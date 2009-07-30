package jperipheral;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import jperipheral.nio.channels.CompletionHandler;
import jperipheral.nio.channels.InterruptedByTimeoutException;
import jperipheral.nio.channels.ReadPendingException;
import jperipheral.nio.channels.ShutdownChannelGroupException;
import jperipheral.nio.channels.WritePendingException;
import org.joda.time.DateTime;
import org.joda.time.Duration;

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
public class SerialChannel extends AsynchronousSerialChannel
{
	private final long nativeContext;
	private boolean closed;
	private boolean readPending;
	private boolean writePending;

	/**
	 * Creates a new SerialChannel.
	 *
	 * @param nativeContext a pointer to the native context
	 */
	public SerialChannel(long nativeContext)
	{
		this.nativeContext = nativeContext;
	}

	@Override
	public synchronized Future<Integer> read(ByteBuffer target)
		throws IllegalArgumentException, ReadPendingException
	{
		if (target.isReadOnly())
			throw new IllegalArgumentException("target may not be read-only");
		if (closed)
			return new ClosedFuture();
		if (readPending)
			throw new ReadPendingException();
		if (target.remaining() <= 0)
			return new NoOpFuture();
		SerialFuture<Integer> result = new SerialFuture<Integer>(nativeContext, new Runnable()
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
		nativeRead(target, 0L, result);
		readPending = true;
		return result;
	}

	@Override
	public synchronized <A> void read(ByteBuffer target, long timeout, TimeUnit unit, A attachment,
																		CompletionHandler<Integer, ? super A> handler)
		throws IllegalArgumentException, ReadPendingException, ShutdownChannelGroupException
	{
		if (target.isReadOnly())
			throw new IllegalArgumentException("target may not be read-only");
		if (closed)
		{
			handler.failed(new ClosedChannelException(), attachment);
			return;
		}
		if (readPending)
			throw new ReadPendingException();
		if (target.remaining() <= 0)
		{
			handler.completed(0, attachment);
			return;
		}
		nativeRead(target, unit.convert(timeout, TimeUnit.MILLISECONDS),
			new NativeListenerToCompletionHandler<A>(handler, attachment, new Runnable()
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
		readPending = true;
	}

	@Override
	public synchronized Future<Integer> write(ByteBuffer source) throws WritePendingException
	{
		if (closed)
			return new ClosedFuture();
		if (writePending)
			throw new WritePendingException();
		if (source.remaining() <= 0)
			return new NoOpFuture();
		SerialFuture<Integer> result = new SerialFuture<Integer>(nativeContext, new Runnable()
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
		nativeWrite(source, 0L, result);
		writePending = true;
		return result;
	}

	@Override
	public synchronized <A> void write(ByteBuffer source, long timeout, TimeUnit unit, A attachment,
																		 CompletionHandler<Integer, ? super A> handler)
		throws IllegalArgumentException, WritePendingException, ShutdownChannelGroupException
	{
		if (closed)
		{
			handler.failed(new ClosedChannelException(), attachment);
			return;
		}
		if (writePending)
			throw new WritePendingException();
		if (source.remaining() <= 0)
		{
			handler.completed(0, attachment);
			return;
		}
		nativeWrite(source, unit.convert(timeout, TimeUnit.MILLISECONDS),
			new NativeListenerToCompletionHandler<A>(handler, attachment, new Runnable()
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
		writePending = true;
	}

	@Override
	public synchronized void close() throws IOException
	{
		closed = true;
	}

	@Override
	public synchronized boolean isOpen()
	{
		return !closed;
	}

	/**
	 * Reads data from the port.
	 *
	 * @param <A> the attachment type
	 * @param target the buffer to write into
	 * @param timeout the number of milliseconds to wait before throwing InterruptedByTimeoutException
	 * @param listener listens for native events
	 */
	private native <A> void nativeRead(ByteBuffer target, long timeout, NativeListener listener);

	/**
	 * Writes data to the port.
	 *
	 * @param <A> the attachment type
	 * @param source the buffer to read from
	 * @param timeout the number of milliseconds to wait before throwing InterruptedByTimeoutException
	 * @param listener listens for native events
	 */
	private native <A> void nativeWrite(ByteBuffer source, long timeout, NativeListener listener);

	/**
	 * Listens for native events.
	 *
	 * @author Gili Tzabari
	 */
	private static interface NativeListener
	{
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
		public boolean cancel(boolean mayInterruptIfRunning)
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
		public Integer get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException,
																													 TimeoutException
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
	private class NativeListenerToCompletionHandler<A> implements NativeListener
	{
		private final CompletionHandler<Integer, ? super A> handler;
		private final A attachment;
		private final Runnable onDone;

		/**
		 * Creates a new CompletionHandlerAdapter.
		 *
		 * @param handler the CompletionHandler to wrap
		 * @param attachment the attachment associated with the CompletionHandler
		 * @param onDone the Runnable to invoke when the operation completes
		 */
		public NativeListenerToCompletionHandler(CompletionHandler<Integer, ? super A> handler, A attachment,
																						 Runnable onDone)
		{
			this.handler = handler;
			this.attachment = attachment;
			this.onDone = onDone;
		}

		@Override
		public void onSuccess(Integer value)
		{
			handler.completed(value, attachment);
			onDone.run();
		}

		@Override
		public void onFailure(Throwable throwable)
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
	}

	/**
	 * A Future for serial-port operations.
	 *
	 * @param <A> the attachment type
	 * @author Gili Tzabari
	 */
	private static class SerialFuture<A> implements Future<Integer>, NativeListener
	{
		private final long nativeContext;
		private Integer value;
		private Throwable throwable;
		private boolean done;
		private boolean cancelled;
		private boolean cancelRequested;
		private final Runnable onDone;

		/**
		 * Creates a new SerialFuture.
		 *
		 * @param nativeContext a pointer to the native context
		 * @param onDone the Runnable to invoke when the operation completes
		 */
		public SerialFuture(long nativeContext, Runnable onDone)
		{
			this.nativeContext = nativeContext;
			this.onDone = onDone;
		}

		@Override
		public synchronized boolean cancel(boolean mayInterruptIfRunning)
		{
			if (!mayInterruptIfRunning)
			{
				done = true;
				return false;
			}
			if (done)
				return false;
			try
			{
				nativeCancel();
			}
			catch (IOException e)
			{
				e.printStackTrace();
				return false;
			}
			while (!done)
			{
				try
				{
					// wait for onSuccess(), onFailure() or onCancellation() to get invoked
					cancelRequested = true;
					wait();
				}
				catch (InterruptedException e)
				{
					throw new RuntimeException(e);
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
		public synchronized Integer get() throws CancellationException, InterruptedException, ExecutionException
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
		public Integer get(long timeout, TimeUnit unit) throws CancellationException, InterruptedException,
																													 ExecutionException, TimeoutException
		{
			DateTime endTime = new DateTime().plus(new Duration(unit.convert(timeout, TimeUnit.MILLISECONDS)));
			while (!done)
			{
				long timeLeft = new Duration(new DateTime(), endTime).getMillis();
				if (timeLeft < 0)
				{
					cancel(true);
					break;
				}
				wait(timeLeft);
			}
			if (cancelled)
				throw new CancellationException();
			if (throwable != null)
				throw new ExecutionException(throwable);
			return value;
		}

		@Override
		public synchronized void onSuccess(Integer value)
		{
			this.value = value;
			done = true;
			notifyAll();
			onDone.run();
		}

		@Override
		public synchronized void onFailure(Throwable throwable)
		{
			this.throwable = throwable;
			done = true;
			notifyAll();
			onDone.run();
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
				// Caused by AsynchronousChannel.close()
				this.throwable = new AsynchronousCloseException();
			}
			done = true;
			notifyAll();
			onDone.run();
		}

		/**
		 * Cancels an ongoing operation.
		 *
		 * @throws IOException if the operation fails
		 */
		private native void nativeCancel() throws IOException;
	}

	/**
	 * A Future for reading or writing zero bytes.
	 *
	 * @author Gili Tzbari
	 */
	private class NoOpFuture implements Future<Integer>
	{
		@Override
		public boolean cancel(boolean mayInterruptIfRunning)
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
		public Integer get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException,
																													 TimeoutException
		{
			return get();
		}
	}
}
