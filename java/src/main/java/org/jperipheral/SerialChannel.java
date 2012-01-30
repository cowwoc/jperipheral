package org.jperipheral;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.SettableFuture;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jperipheral.SerialPort.BaudRate;
import org.jperipheral.SerialPort.DataBits;
import org.jperipheral.SerialPort.FlowControl;
import org.jperipheral.SerialPort.Parity;
import org.jperipheral.SerialPort.StopBits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An asynchronous channel for serial ports.
 *
 * <h4>Timeouts</h4>
 *
 * The {@link #read(java.nio.ByteBuffer, java.lang.Object, java.nio.channels.CompletionHandler) read}
 * and {@link #write(java.nio.ByteBuffer, java.lang.Object, java.nio.channels.CompletionHandler) write}
 * methods defined by this class allow a timeout to be specified when initiating a read or write
 * operation. If the timeout elapses before an operation completes then the operation completes with
 * the exception {@link InterruptedByTimeoutException}. A timeout may leave the channel, or the
 * underlying connection, in an inconsistent state. Where the implementation cannot guarantee that
 * bytes have not been read from the channel then it puts the channel into an implementation
 * specific <em>error state</em>. A subsequent attempt to initiate a {
 * @code read} operation causes an unspecified runtime exception to be thrown. Similarly if a {@code write}
 * operation times out and the implementation cannot guarantee bytes have not been written to the
 * channel then further attempts to {@code write} to the channel cause an unspecified runtime
 * exception to be thrown. When a timeout elapses then the state of the {@link ByteBuffer}, or the
 * sequence of buffers, for the I/O operation is not defined. Buffers should be discarded or at
 * least care must be taken to ensure that the buffers are not accessed while the channel remains
 * open.
 *
 * @author Gili Tzabari
 */
public class SerialChannel implements AsynchronousByteChannel
{
	private final Logger log = LoggerFactory.getLogger(SerialChannel.class);
	private final PeripheralChannelGroup group;
	/**
	 * A pointer to the native object, accessed by the native code.
	 */
	@SuppressWarnings(
	{
		"PMD.UnusedPrivateField",
		"PMD.SingularField"
	})
	private final long nativeObject;
	private final SerialPort port;
	private BaudRate baudRate;
	private DataBits dataBits;
	private StopBits stopBits;
	private Parity parity;
	private FlowControl flowControl;
	private final AtomicBoolean closed = new AtomicBoolean();
	private final AtomicBoolean reading = new AtomicBoolean();
	private final AtomicBoolean readInterrupted = new AtomicBoolean();
	private final AtomicBoolean writing = new AtomicBoolean();
	private final AtomicBoolean writeInterrupted = new AtomicBoolean();
	private final Phaser ongoingOperations = new Phaser();

	/**
	 * Creates a new SerialChannel. The caller is responsible for adding the channel into the group.
	 *
	 * @param port the serial port
	 * @param group the group associated with the channel
	 * @param timeout the minimum amount of time to wait if the port is locked by another process
	 * before giving up
	 * @param unit the unit of timeout
	 * @throws PeripheralNotFoundException if the comport does not exist
	 * @throws PeripheralInUseException if the peripheral is locked by another process
	 * @throws NullPointerException if port, unit is null
	 */
	SerialChannel(final SerialPort port, final PeripheralChannelGroup group, long timeout,
		TimeUnit unit) throws PeripheralNotFoundException, PeripheralInUseException
	{
		Preconditions.checkNotNull(port, "port may not be null");
		Preconditions.checkNotNull(group, "group may not be null");

		this.port = port;
		this.group = group;
		this.nativeObject = nativeOpen(port.getName(), TimeUnit.MILLISECONDS.convert(timeout, unit));
	}

	/**
	 * Returns the baud rate being used.
	 *
	 * @return the baud rate being used
	 */
	public BaudRate getBaudRate()
	{
		return baudRate;
	}

	/**
	 * Returns the number of data bits being used.
	 *
	 * @return the number of data bits being used
	 */
	public DataBits getDataBits()
	{
		return dataBits;
	}

	/**
	 * Returns the number of stop bits being used.
	 *
	 * @return the number of stop bits being used
	 */
	public StopBits getStopBits()
	{
		return stopBits;
	}

	/**
	 * Returns the parity type being used.
	 *
	 * @return the parity type being used
	 */
	public Parity getParity()
	{
		return parity;
	}

	/**
	 * Returns the flow control mechanism being used.
	 *
	 * @return the flow control mechanism being used
	 */
	public FlowControl getFlowControl()
	{
		return flowControl;
	}

	@Override
	public <A> void read(final ByteBuffer target, final A attachment,
		final CompletionHandler<Integer, ? super A> handler)
		throws IllegalArgumentException, ReadPendingException, ShutdownChannelGroupException
	{
		if (target.isReadOnly())
			throw new IllegalArgumentException("target may not be read-only");
		group.executor().submit(new Runnable()
		{
			@Override
			public void run()
			{
				if (closed.get())
				{
					handler.failed(new ClosedChannelException(), attachment);
					return;
				}
				if (!reading.compareAndSet(false, true))
					throw new ReadPendingException();
				ongoingOperations.register();
				OperationDone<Integer, ? super A> operationDone = new OperationDone<>(attachment, handler,
					reading, ongoingOperations);
				if (readInterrupted.get())
				{
					operationDone.failed(new IllegalStateException("The previous read cancellation left the channel in an"
						+ "inconsistent state. See java.nio.channels.AsynchronousChannel section Cancellation"
						+ "for more information."), true);
					return;
				}
				if (target.remaining() <= 0)
				{
					operationDone.completed(0, true);
					return;
				}
				try
				{
					nativeRead(target, Long.MAX_VALUE, false, operationDone);
				}
				catch (RuntimeException | Error e)
				{
					operationDone.failed(e, true);
				}
			}
		});
	}

	@Override
	public Future<Integer> read(final ByteBuffer target)
		throws IllegalArgumentException, ReadPendingException
	{
		if (target.isReadOnly())
			throw new IllegalArgumentException("target may not be read-only");
		return group.executor().submit(new Callable<Integer>()
		{
			@Override
			public Integer call() throws Exception
			{
				if (closed.get())
					throw new ClosedChannelException();
				if (!reading.compareAndSet(false, true))
					throw new ReadPendingException();
				ongoingOperations.register();
				try
				{
					if (readInterrupted.get())
					{
						throw new IllegalStateException("The previous read cancellation left the channel in an"
							+ "inconsistent state. See java.nio.channels.AsynchronousChannel section Cancellation"
							+ "for more information.");
					}
					if (target.remaining() <= 0)
						return 0;

					SettableFuture<Integer> result = SettableFuture.create();
					CompletionHandlerToFuture<Integer> handlerToFuture =
						new CompletionHandlerToFuture<>(result);
					try
					{
						nativeRead(target, Long.MAX_VALUE, null, handlerToFuture);
						return result.get();
					}
					catch (InterruptedException e)
					{
						// Exit at the request of Future.cancel(true)
						readInterrupted.set(true);
						close();
						throw e;
					}
				}
				finally
				{
					reading.set(false);
					ongoingOperations.arriveAndDeregister();
				}
			}
		});
	}

	@Override
	public <A> void write(final ByteBuffer source, final A attachment,
		final CompletionHandler<Integer, ? super A> handler)
		throws IllegalArgumentException, WritePendingException, ShutdownChannelGroupException
	{
		group.executor().submit(new Runnable()
		{
			@Override
			public void run()
			{
				if (closed.get())
				{
					handler.failed(new ClosedChannelException(), attachment);
					return;
				}
				if (!writing.compareAndSet(false, true))
					throw new WritePendingException();
				ongoingOperations.register();
				OperationDone<Integer, ? super A> operationDone = new OperationDone<>(attachment, handler,
					writing, ongoingOperations);
				if (writeInterrupted.get())
				{
					operationDone.failed(new IllegalStateException("The previous write cancellation left the channel in an"
						+ "inconsistent state. See java.nio.channels.AsynchronousChannel section Cancellation"
						+ "for more information."), true);
					return;
				}
				if (source.remaining() <= 0)
				{
					operationDone.completed(0, true);
					return;
				}
				try
				{
					nativeWrite(source, Long.MAX_VALUE, false, operationDone);
				}
				catch (RuntimeException | Error e)
				{
					operationDone.failed(e, true);
				}
			}
		});
	}

	@Override
	public Future<Integer> write(final ByteBuffer source)
		throws WritePendingException
	{
		return group.executor().submit(new Callable<Integer>()
		{
			@Override
			public Integer call() throws Exception
			{
				if (closed.get())
					throw new ClosedChannelException();
				if (!writing.compareAndSet(false, true))
					throw new WritePendingException();
				ongoingOperations.register();
				try
				{
					if (writeInterrupted.get())
					{
						throw new IllegalStateException("The previous write cancellation left the channel in an"
							+ "inconsistent state. See java.nio.channels.AsynchronousChannel section Cancellation"
							+ "for more information.");
					}
					if (source.remaining() <= 0)
						return 0;
					SettableFuture<Integer> result = SettableFuture.create();
					CompletionHandlerToFuture<Integer> handlerToFuture =
						new CompletionHandlerToFuture<>(result);
					try
					{
						nativeWrite(source, Long.MAX_VALUE, null, handlerToFuture);
						return result.get();
					}
					catch (InterruptedException e)
					{
						// Exit at the request of Future.cancel(true)
						readInterrupted.set(true);
						close();
						throw e;
					}
				}
				finally
				{
					writing.set(false);
					ongoingOperations.arriveAndDeregister();
				}
			}
		});
	}

	/**
	 * Configures the serial port channel.
	 *
	 * @param baudRate the baud rate
	 * @param dataBits the number of data bits per word
	 * @param parity the parity mechanism to use
	 * @param stopBits the number of stop bits to use
	 * @param flowControl the flow control to use
	 * @throws PeripheralConfigurationException if an I/O error occurs while configuring the channel
	 */
	public void configure(final BaudRate baudRate, final DataBits dataBits, final Parity parity,
		final StopBits stopBits, final FlowControl flowControl)
		throws PeripheralConfigurationException
	{
		nativeConfigure(baudRate, dataBits, parity, stopBits, flowControl);
		this.baudRate = baudRate;
		this.dataBits = dataBits;
		this.parity = parity;
		this.stopBits = stopBits;
		this.flowControl = flowControl;
	}

	@Override
	public void close() throws IOException
	{
		if (closed.compareAndSet(false, true))
		{
			nativeClose();
			boolean ongoingRead = reading.get();
			boolean ongoingWrite = writing.get();
			if (ongoingRead || ongoingWrite)
			{
				if (log.isDebugEnabled())
				{
					StringBuilder cause = new StringBuilder("Waiting for ");
					if (ongoingRead)
						cause.append("read ");
					if (ongoingRead && ongoingWrite)
						cause.append("and ");
					if (ongoingWrite)
						cause.append("write ");
					cause.append("operation");
					if (ongoingRead && ongoingWrite)
						cause.append("s");
					cause.append(" to complete");
					log.debug(cause.toString());
				}
				ongoingOperations.register();
				int phase = ongoingOperations.arriveAndDeregister();
				if (log.isDebugEnabled())
				{
					log.debug("parties: " + ongoingOperations.getRegisteredParties() + ", arrived: "
						+ ongoingOperations.getArrivedParties() + ", phase: " + phase);
				}
				ongoingOperations.awaitAdvance(phase);
				log.debug("Port closed");
			}
		}
	}

	@Override
	public boolean isOpen()
	{
		return !closed.get();
	}

	/**
	 * Returns the serial port associated with the channel.
	 *
	 * @return the serial port associated with the channel
	 */
	public SerialPort getPort()
	{
		return port;
	}

	@Override
	public String toString()
	{
		return port.getName() + "[" + baudRate + " " + dataBits + "-" + parity + "-" + stopBits + " "
			+ flowControl + "]";
	}

	/**
	 * Opens the port.
	 *
	 * @param name the port name
	 * @param timeout the minimum number of milliseconds to wait if the port is locked by another
	 * process before giving up
	 * @return the native context associated with the port
	 * @throws PeripheralNotFoundException if the comport does not exist
	 * @throws PeripheralInUseException if the comport is locked by another process
	 */
	private native long nativeOpen(String name, long timeout)
		throws PeripheralNotFoundException, PeripheralInUseException;

	/**
	 * Sets the port configuration.
	 *
	 * @param baudRate the baud rate
	 * @param dataBits the number of data bits per word
	 * @param parity the parity mechanism to use
	 * @param stopBits the number of stop bits to use
	 * @param flowControl the flow control to use
	 * @throws PeripheralConfigurationException if an I/O error occurs while configuring the channel
	 */
	private native void nativeConfigure(BaudRate baudRate, DataBits dataBits, Parity parity,
		StopBits stopBits, FlowControl flowControl) throws PeripheralConfigurationException;

	/**
	 * Closes the port.
	 *
	 * @throws IOException if an I/O error occurs
	 */
	private native void nativeClose() throws IOException;

	/**
	 * Reads data from the port.
	 *
	 * @param <A> the attachment type
	 * @param target the buffer to write into
	 * @param timeout the number of milliseconds to wait before throwing
	 * InterruptedByTimeoutException. 0 means "return right away". Long.MAX_VALUE means "wait forever"
	 * @param attachment the attachment associated with handler
	 * @param handler a handler for consuming the result of an asynchronous I/O operation. On success,
	 * returns the number of bytes read.
	 */
	private native <A> void nativeRead(ByteBuffer target, long timeout, A attachment,
		CompletionHandler<Integer, A> handler);

	/**
	 * Writes data to the port.
	 *
	 * @param <A> the attachment type
	 * @param source the buffer to read from
	 * @param timeout the number of milliseconds to wait before throwing
	 * InterruptedByTimeoutException. 0 means "return right away". Long.MAX_VALUE means "wait forever"
	 * @param attachment the attachment associated with handler
	 * @param handler a handler for consuming the result of an asynchronous I/O operation. On success,
	 * returns the number of bytes written.
	 */
	private native <A> void nativeWrite(ByteBuffer source, long timeout, A attachment,
		CompletionHandler<Integer, A> handler);

	/**
	 * Notified when a read or write operation completes.
	 *
	 * The attachment indicates whether the delegate completion handler may be invoked directly, or
	 * whether it must be scheduled for asynchronous execution by the AsynchronousChannelGroup.
	 *
	 * @param <V> the result type expected by the delegate completion handler
	 * @param <A> the type of the object attached to the delegate handler
	 * @author Gili Tzabari
	 */
	private class OperationDone<V, A> implements CompletionHandler<V, Boolean>
	{
		private final A attachment;
		private final CompletionHandler<V, A> delegate;
		private final AtomicBoolean running;
		private final Phaser phaser;
		private final Logger log = LoggerFactory.getLogger(OperationDone.class);

		/**
		 * Creates a new OperationDone.
		 *
		 * @param attachment the attachment to pass to
		 * <code>delegate</code>
		 * @param delegate the handler to delegate to
		 * @param running an AtomicBoolean to set to false when the operation completes.
		 * @param phaser a Phaser that should arriveAndDeregister() when the operation completes.
		 * @throws NullPointerException if delegate or running are null
		 */
		public OperationDone(A attachment, CompletionHandler<V, A> delegate, AtomicBoolean running,
			Phaser phaser)
		{
			Preconditions.checkNotNull(delegate, "delegate may not be null");
			Preconditions.checkNotNull(running, "running may not be null");
			Preconditions.checkNotNull(phaser, "phaser may not be null");

			this.attachment = attachment;
			this.delegate = delegate;
			this.running = running;
			this.phaser = phaser;
		}

		@Override
		public void completed(final V value, Boolean groupThread)
		{
			// Set running before delegating in order to allow the delegate to initiate a follow-up
			// operation.
			running.set(false);
			phaser.arriveAndDeregister();
			if (groupThread)
				delegate.completed(value, attachment);
			else
				new CompletionHandlerExecutor<>(delegate, group.executor()).completed(value, attachment);
		}

		@Override
		public void failed(final Throwable t, Boolean groupThread)
		{
			// Set running before delegating in order to allow the delegate to initiate a follow-up
			// operation.
			running.set(false);
			phaser.arriveAndDeregister();
			if (groupThread)
				delegate.failed(t, attachment);
			else
				new CompletionHandlerExecutor<>(delegate, group.executor()).failed(t, attachment);
		}
	}
}
