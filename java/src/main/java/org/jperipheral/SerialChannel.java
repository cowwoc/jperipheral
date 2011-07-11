package org.jperipheral;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.nio.channels.InterruptedByTimeoutException;
import java.nio.channels.ReadPendingException;
import java.nio.channels.ShutdownChannelGroupException;
import java.nio.channels.WritePendingException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jperipheral.SerialPort.DataBits;
import org.jperipheral.SerialPort.FlowControl;
import org.jperipheral.SerialPort.Parity;
import org.jperipheral.SerialPort.StopBits;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An asynchronous channel for serial ports.
 *
 * <h4>Timeouts</h4>
 *
 * <p> The {@link #read(ByteBuffer,long,TimeUnit,Object,CompletionHandler) read}
 * and {@link #write(ByteBuffer,long,TimeUnit,Object,CompletionHandler) write}
 * methods defined by this class allow a timeout to be specified when initiating
 * a read or write operation. If the timeout elapses before an operation completes
 * then the operation completes with the exception {@link
 * InterruptedByTimeoutException}. A timeout may leave the channel, or the
 * underlying connection, in an inconsistent state. Where the implementation
 * cannot guarantee that bytes have not been read from the channel then it puts
 * the channel into an implementation specific <em>error state</em>. A subsequent
 * attempt to initiate a {@code read} operation causes an unspecified runtime
 * exception to be thrown. Similarly if a {@code write} operation times out and
 * the implementation cannot guarantee bytes have not been written to the
 * channel then further attempts to {@code write} to the channel cause an
 * unspecified runtime exception to be thrown. When a timeout elapses then the
 * state of the {@link ByteBuffer}, or the sequence of buffers, for the I/O
 * operation is not defined. Buffers should be discarded or at least care must
 * be taken to ensure that the buffers are not accessed while the channel remains
 * open.
 * 
 * @author Gili Tzabari
 */
public class SerialChannel implements AsynchronousByteChannel
{
	/**
	 * A pointer to the native object, accessed by the native code.
	 */
	@SuppressWarnings(
	{
		"PMD.UnusedPrivateField",
		"PMD.SingularField"
	})
	private transient final long nativeObject;
	private transient final SerialPort port;
	private transient int baudRate;
	private transient DataBits dataBits;
	private transient StopBits stopBits;
	private transient Parity parity;
	private transient FlowControl flowControl;
	private transient boolean closed;
	private transient boolean readPending;
	private transient boolean writePending;

	/**
	 * Creates a new SerialChannel.
	 *
	 * @param port the serial port
	 * @throws PeripheralNotFoundException if the comport does not exist
	 * @throws PeripheralInUseException if the peripheral is locked by another application
	 */
	public SerialChannel(final SerialPort port)
		throws PeripheralNotFoundException, PeripheralInUseException
	{
		this.port = port;
		this.nativeObject = nativeOpen(port.getName());
	}

	/**
	 * Returns the baud rate being used.
	 *
	 * @return the baud rate being used
	 */
	public int getBaudRate()
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
	public synchronized Future<Integer> read(final ByteBuffer target)
		throws IllegalArgumentException, ReadPendingException
	{
		if (target.isReadOnly())
			throw new IllegalArgumentException("target may not be read-only");
		synchronized (this)
		{
			if (closed)
				return new ClosedFuture();
			if (readPending)
				throw new ReadPendingException();
			readPending = true;
		}
		if (target.remaining() <= 0)
			return new NoOpFuture();
		final SerialFuture<Integer> result = new SerialFuture<>(new Runnable()
		{
			@Override
			public void run()
			{
				synchronized (SerialChannel.this)
				{
					readPending = false;
				}
			}
		});
		try
		{
			nativeRead(target, Long.MAX_VALUE, result);
			return result;
		}
		catch (RuntimeException e)
		{
			readPending = false;
			throw e;
		}
	}

	@Override
	public synchronized <A> void read(final ByteBuffer target, final A attachment,
																		final CompletionHandler<Integer, ? super A> handler)
		throws IllegalArgumentException, ReadPendingException, ShutdownChannelGroupException
	{
		if (target.isReadOnly())
			throw new IllegalArgumentException("target may not be read-only");
		synchronized (this)
		{
			if (closed)
			{
				handler.failed(new ClosedChannelException(), attachment);
				return;
			}
			if (readPending)
				throw new ReadPendingException();
			readPending = true;
		}
		if (target.remaining() <= 0)
		{
			handler.completed(0, attachment);
			return;
		}
		try
		{
			nativeRead(target, Long.MAX_VALUE,
				new NativeListenerToCompletionHandler<>(handler, attachment, new Runnable()
			{
				@Override
				public void run()
				{
					synchronized (SerialChannel.this)
					{
						readPending = false;
					}
				}
			}));
		}
		catch (RuntimeException e)
		{
			readPending = false;
			throw e;
		}
	}

	@Override
	public synchronized Future<Integer> write(final ByteBuffer source)
		throws WritePendingException
	{
		synchronized (this)
		{
			if (closed)
				return new ClosedFuture();
			if (writePending)
				throw new WritePendingException();
			writePending = true;
		}
		if (source.remaining() <= 0)
			return new NoOpFuture();
		final SerialFuture<Integer> result = new SerialFuture<>(new Runnable()
		{
			@Override
			public void run()
			{
				synchronized (SerialChannel.this)
				{
					writePending = false;
				}
			}
		});
		try
		{
			nativeWrite(source, Long.MAX_VALUE, result);
			return result;
		}
		catch (RuntimeException e)
		{
			writePending = false;
			throw e;
		}
	}

	@Override
	public synchronized <A> void write(final ByteBuffer source, final A attachment,
																		 final CompletionHandler<Integer, ? super A> handler)
		throws IllegalArgumentException, WritePendingException, ShutdownChannelGroupException
	{
		synchronized (this)
		{
			if (closed)
			{
				handler.failed(new ClosedChannelException(), attachment);
				return;
			}
			if (writePending)
				throw new WritePendingException();
			writePending = true;
		}
		if (source.remaining() <= 0)
		{
			handler.completed(0, attachment);
			return;
		}
		try
		{
			nativeWrite(source, Long.MAX_VALUE,
				new NativeListenerToCompletionHandler<>(handler, attachment, new Runnable()
			{
				@Override
				public void run()
				{
					synchronized (SerialChannel.this)
					{
						writePending = false;
					}
				}
			}));
		}
		catch (RuntimeException e)
		{
			readPending = false;
			throw e;
		}
	}

	/**
	 * Configures the serial port channel.
	 *
	 * @param baudRate the baud rate
	 * @param dataBits the number of data bits per word
	 * @param parity the parity mechanism to use
	 * @param stopBits the number of stop bits to use
	 * @param flowControl the flow control to use
	 * @throws IOException if an I/O error occurs while configuring the channel
	 */
	public void configure(final int baudRate, final DataBits dataBits, final Parity parity,
												final StopBits stopBits, final FlowControl flowControl)
		throws IOException
	{
		nativeConfigure(baudRate, dataBits, parity, stopBits, flowControl);
		this.baudRate = baudRate;
		this.dataBits = dataBits;
		this.parity = parity;
		this.stopBits = stopBits;
		this.flowControl = flowControl;
	}

	@Override
	public synchronized void close() throws IOException
	{
		if (closed)
			return;
		nativeClose();
		closed = true;
	}

	@Override
	public synchronized boolean isOpen()
	{
		return !closed;
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
	 * @return the native context associated with the port
	 * @throws PeripheralNotFoundException if the comport does not exist
	 * @throws PeripheralInUseException if the comport is locked by another application
	 */
	private native long nativeOpen(String name) throws PeripheralNotFoundException,
																										 PeripheralInUseException;

	/**
	 * Sets the port configuration.
	 *
	 * @param baudRate the baud rate
	 * @param dataBits the number of data bits per word
	 * @param parity the parity mechanism to use
	 * @param stopBits the number of stop bits to use
	 * @param flowControl the flow control to use
	 * @throws IOException if an I/O error occurs while configuring the channel
	 */
	private native void nativeConfigure(int baudRate, DataBits dataBits, Parity parity,
																			StopBits stopBits, FlowControl flowControl) throws IOException;

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
	 *                InterruptedByTimeoutException. 0 means "return right away".
	 *                Long.MAX_VALUE means "wait forever"
	 * @param listener listens for native events
	 */
	private native <A> void nativeRead(ByteBuffer target, long timeout, NativeListener listener);

	/**
	 * Writes data to the port.
	 *
	 * @param <A> the attachment type
	 * @param source the buffer to read from
	 * @param timeout the number of milliseconds to wait before throwing
	 *                InterruptedByTimeoutException. 0 means "return right away".
	 *                Long.MAX_VALUE means "wait forever"
	 * @param listener listens for native events
	 */
	private native <A> void nativeWrite(ByteBuffer source, long timeout, NativeListener listener);

	/**
	 * Listens for native events.
	 *
	 * @author Gili Tzabari
	 */
	private interface NativeListener
	{
		/**
		 * Sets the the user object associated with the listener.
		 *
		 * @param userObject a pointer to the user object
		 */
		void setUserObject(long userObject);

		/**
		 * Returns the user object associated with the listener.
		 *
		 * @return the pointer to the user object
		 */
		long getUserObject();

		/**
		 * Invoked if the operation completed successfully.
		 *
		 * @param value the operation return-value
		 */
		void onSuccess(Integer value);

		/**
		 * Invoked if the operation failed.
		 *
		 * @param throwable the Throwable associated with the failure
		 */
		void onFailure(Throwable throwable);

		/**
		 * Invokes if the operation was canceled.
		 */
		void onCancellation();
	}

	/**
	 * A Future that always throws ClosedChannelException.
	 *
	 * @author Gili Tzabari
	 */
	private static class ClosedFuture implements Future<Integer>
	{
		@Override
		public boolean cancel(final boolean mayInterruptIfRunning)
		{
			return false;
		}

		@Override
		public boolean isCancelled()
		{
			return false;
		}

		@Override
		public boolean isDone()
		{
			return true;
		}

		@Override
		public Integer get() throws InterruptedException, ExecutionException
		{
			throw new ExecutionException(new ClosedChannelException());
		}

		@Override
		public Integer get(final long timeout, final TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException
		{
			throw new ExecutionException(new ClosedChannelException());
		}
	}

	/**
	 * Converts NativeListener events to CompletionHandler events.
	 *
	 * @type <A> the attachment type
	 * @author Gili Tzabari
	 */
	private static class NativeListenerToCompletionHandler<A> implements NativeListener
	{
		private transient final CompletionHandler<Integer, ? super A> handler;
		private transient final A attachment;
		private transient final Runnable onDone;

		/**
		 * Creates a new CompletionHandlerAdapter.
		 *
		 * @param handler the CompletionHandler to wrap
		 * @param attachment the attachment associated with the CompletionHandler
		 * @param onDone the Runnable to invoke when the operation completes
		 */
		public NativeListenerToCompletionHandler(final CompletionHandler<Integer, ? super A> handler,
																						 final A attachment,
																						 final Runnable onDone)
		{
			this.handler = handler;
			this.attachment = attachment;
			this.onDone = onDone;
		}

		@Override
		public void onSuccess(final Integer value)
		{
			handler.completed(value, attachment);
			onDone.run();
		}

		@Override
		public void onFailure(final Throwable throwable)
		{
			handler.failed(throwable, attachment);
			onDone.run();
		}

		@Override
		public void onCancellation()
		{
			handler.failed(new AsynchronousCloseException(), attachment);
			onDone.run();
		}

		@Override
		public void setUserObject(final long userObject)
		{
			throw new AssertionError("Never used by native code");
		}

		@Override
		public long getUserObject()
		{
			throw new AssertionError("Never used by native code");
		}
	}

	/**
	 * A Future for serial-port operations.
	 *
	 * @param <A> the attachment type
	 * @author Gili Tzabari
	 */
	private static class SerialFuture<A> implements Future<Integer>, NativeListener
	{
		private transient long userObject;
		private transient Integer value;
		private transient Throwable throwable;
		private transient boolean done;
		private transient boolean cancelled;
		private transient boolean cancelRequested;
		private transient final Runnable onDone;
		private transient final Logger log = LoggerFactory.getLogger(SerialFuture.class);

		/**
		 * Creates a new SerialFuture.
		 *
		 * @param onDone the Runnable to invoke when the operation completes
		 */
		public SerialFuture(final Runnable onDone)
		{
			this.onDone = onDone;
		}

		@Override
		public synchronized boolean cancel(final boolean mayInterruptIfRunning)
		{
			if (!mayInterruptIfRunning)
			{
				done = true;
				return false;
			}
			if (done)
				return false;
			cancelRequested = true;
			try
			{
				// NOTE: Canceling an operation may result in data loss.
				// REFERENCE: http://stackoverflow.com/questions/1238905/what-does-cancelio-do-with-bytes-that-have-already-been-read
				nativeCancel();
			}
			catch (IOException e)
			{
				log.error("", e);
				return false;
			}
			while (!done)
			{
				try
				{
					// wait for onSuccess(), onFailure() or onCancellation() to get invoked
					wait();
				}
				catch (InterruptedException e)
				{
					return false;
				}
			}
			return cancelled;
		}

		@Override
		public synchronized boolean isCancelled()
		{
			return cancelled;
		}

		@Override
		public synchronized boolean isDone()
		{
			return done;
		}

		@Override
		public synchronized Integer get()
			throws CancellationException, InterruptedException, ExecutionException
		{
			while (!done)
				wait();
			if (cancelled)
				throw new CancellationException();
			if (throwable != null)
				throw new ExecutionException(throwable);
			return value;
		}

		@Override
		public synchronized Integer get(final long timeout, final TimeUnit unit)
			throws CancellationException, InterruptedException, ExecutionException, TimeoutException
		{
			DateTime endTime = new DateTime().plus(new Duration(TimeUnit.MILLISECONDS.convert(timeout,
				unit)));
			boolean timeoutOccured = false;
			while (!done)
			{
				long timeLeft = new Duration(new DateTime(), endTime).getMillis();
				if (timeLeft <= 0)
				{
					timeoutOccured = true;
					cancel(true);
					break;
				}
				wait(timeLeft);
			}
			if (cancelled)
			{
				if (timeoutOccured)
					throw new TimeoutException();
				throw new CancellationException();
			}
			if (throwable != null)
				throw new ExecutionException(throwable);
			return value;
		}

		@Override
		public synchronized void onSuccess(final Integer value)
		{
			this.value = value;
			onDone();
		}

		@Override
		public synchronized void onFailure(final Throwable throwable)
		{
			this.throwable = throwable;
			onDone();
		}

		@Override
		public synchronized void onCancellation()
		{
			if (cancelRequested)
			{
				// Caused by Future.cancel()
				cancelled = true;
			}
			else
			{
				// Otherwise, assume it was caused by AsynchronousChannel.close()
				this.throwable = new AsynchronousCloseException();
			}
			onDone();
		}

		/**
		 * Invoked when the operation completes.
		 */
		private synchronized void onDone()
		{
			done = true;
			notifyAll();
			onDone.run();
			nativeDispose();
		}

		@Override
		public synchronized void setUserObject(final long userObject)
		{
			// invoked by native code
			this.userObject = userObject;
		}

		@Override
		public synchronized long getUserObject()
		{
			// invoked by native code
			return userObject;
		}

		/**
		 * Cancels an ongoing operation.
		 *
		 * Implementation must ensure that nativeDispose() is not invoked at the same time.
		 *
		 * @throws IOException if the operation fails
		 */
		private native void nativeCancel() throws IOException;

		/**
		 * Disposes the resources associated with the native listener.
		 *
		 * Implementation must ensure that nativeCancel() is not invoked at the same time.
		 */
		private native void nativeDispose();
	}

	/**
	 * A Future for reading or writing zero bytes.
	 *
	 * @author Gili Tzbari
	 */
	private static class NoOpFuture implements Future<Integer>
	{
		@Override
		public boolean cancel(final boolean mayInterruptIfRunning)
		{
			return false;
		}

		@Override
		public boolean isCancelled()
		{
			return false;
		}

		@Override
		public boolean isDone()
		{
			return true;
		}

		@Override
		public Integer get() throws InterruptedException, ExecutionException
		{
			return Integer.valueOf(0);
		}

		@Override
		public Integer get(final long timeout, final TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException
		{
			return get();
		}
	}
}
