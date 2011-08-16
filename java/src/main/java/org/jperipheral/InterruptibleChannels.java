package org.jperipheral;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.InterruptedByTimeoutException;
import java.nio.channels.ReadPendingException;
import java.nio.channels.ShutdownChannelGroupException;
import java.nio.channels.WritePendingException;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Gili Tzabari
 */
public class InterruptibleChannels
{
	/**
	 * Schedules timeout events.
	 */
	private static final ScheduledExecutorService timer = Executors.newScheduledThreadPool(0,
		new ThreadFactoryBuilder().setDaemon(false).setNameFormat(InterruptibleChannels.class.getName()
		+ "-%d").build());

	/**
	 * Opens a new InterruptibleByteChannel.
	 * 
	 * @param delegate the InterruptibleByteChannel to wrap
	 * @return a new InterruptibleByteChannel
	 */
	public static InterruptibleByteChannel open(AsynchronousByteChannel delegate)
	{
		return new InterruptibleByteChannelAdapter(delegate);
	}

	/**
	 * Opens a new InterruptibleCharChannel.
	 * 
	 * @param delegate the InterruptibleCharChannel to wrap
	 * @return a new InterruptibleCharChannel
	 */
	public static InterruptibleCharChannel open(AsynchronousCharChannel delegate)
	{
		return new InterruptibleCharChannelAdapter(delegate);
	}

	/**
	 * A InterruptibleByteChannel that wraps a AsynchronousByteChannel.
	 * 
	 * @author Gili Tzabari
	 */
	private static class InterruptibleByteChannelAdapter implements InterruptibleByteChannel
	{
		private final AsynchronousByteChannel delegate;

		/**
		 * Creates a new InterruptibleByteChannelAdapter.
		 * 
		 * @param delegate the AsynchronousByteChannel to wrap
		 */
		public InterruptibleByteChannelAdapter(AsynchronousByteChannel delegate)
		{
			this.delegate = delegate;
		}

		@Override
		public <A> void read(ByteBuffer target, long timeout, TimeUnit unit, A attachment,
			CompletionHandler<Integer, ? super A> handler)
			throws IllegalArgumentException, ReadPendingException, ShutdownChannelGroupException
		{
			if (target.isReadOnly())
				throw new IllegalArgumentException("target may not be read-only");
			// TODO: interrupted should persist across read/write operations
			final AtomicBoolean interrupted = new AtomicBoolean();
			Future<Void> timeoutTimer;
			if (timeout == Long.MAX_VALUE)
				timeoutTimer = Futures.immediateFuture(null);
			else
				timeoutTimer = timer.schedule(new CloseChannel(delegate, interrupted), timeout, unit);
			DoneReading<Integer, ? super A> doneReading = new DoneReading<>(attachment, handler,
				timeoutTimer);
			delegate.read(target, interrupted, doneReading);
		}

		@Override
		public <A> void write(ByteBuffer source, long timeout, TimeUnit unit, A attachment,
			CompletionHandler<Integer, ? super A> handler)
			throws IllegalArgumentException, WritePendingException, ShutdownChannelGroupException,
			UnsupportedOperationException
		{
			final AtomicBoolean interrupted = new AtomicBoolean();
			Future<Void> timeoutTimer;
			if (timeout == Long.MAX_VALUE)
				timeoutTimer = Futures.immediateFuture(null);
			else
				timeoutTimer = timer.schedule(new CloseChannel(delegate, interrupted), timeout, unit);
			DoneWriting<Integer, ? super A> doneWriting = new DoneWriting<>(attachment, handler,
				timeoutTimer);
			delegate.write(source, interrupted, doneWriting);
		}

		@Override
		public <A> void close(final A attachment,
			final CompletionHandler<Void, ? super A> handler)
		{
			timer.execute(new Runnable()
			{
				@Override
				public void run()
				{
					try
					{
						delegate.close();
						handler.completed(null, attachment);
					}
					catch (Throwable t)
					{
						handler.failed(t, attachment);
					}
				}
			});
		}
	}

	/**
	 * A InterruptibleByteChannel that wraps a AsynchronousByteChannel.
	 * 
	 * @author Gili Tzabari
	 */
	private static class InterruptibleCharChannelAdapter implements InterruptibleCharChannel
	{
		private final AsynchronousCharChannel delegate;

		/**
		 * Creates a new InterruptibleCharChannelAdapter.
		 * 
		 * @param delegate the AsynchronousCharChannel to wrap
		 */
		public InterruptibleCharChannelAdapter(AsynchronousCharChannel delegate)
		{
			this.delegate = delegate;
		}

		@Override
		public <A> void read(CharBuffer target, long timeout, TimeUnit unit, A attachment,
			CompletionHandler<Integer, ? super A> handler)
			throws IllegalArgumentException, ReadPendingException, ShutdownChannelGroupException
		{
			if (target.isReadOnly())
				throw new IllegalArgumentException("target may not be read-only");
			final AtomicBoolean interrupted = new AtomicBoolean();
			Future<Void> timeoutTimer;
			if (timeout == Long.MAX_VALUE)
				timeoutTimer = Futures.immediateFuture(null);
			else
				timeoutTimer = timer.schedule(new CloseChannel(delegate, interrupted), timeout, unit);
			DoneReading<Integer, ? super A> doneReading = new DoneReading<>(attachment, handler,
				timeoutTimer);
			delegate.read(target, interrupted, doneReading);
		}

		@Override
		public <A> void readLine(long timeout, TimeUnit unit, A attachment,
			CompletionHandler<String, ? super A> handler)
			throws IllegalArgumentException, ReadPendingException, ShutdownChannelGroupException
		{
			final AtomicBoolean interrupted = new AtomicBoolean();
			Future<Void> timeoutTimer;
			if (timeout == Long.MAX_VALUE)
				timeoutTimer = Futures.immediateFuture(null);
			else
				timeoutTimer = timer.schedule(new CloseChannel(delegate, interrupted), timeout, unit);
			DoneReading<String, ? super A> doneReading = new DoneReading<>(attachment, handler,
				timeoutTimer);
			delegate.readLine(interrupted, doneReading);
		}

		@Override
		public <A> void write(CharBuffer source, long timeout, TimeUnit unit, A attachment,
			CompletionHandler<Integer, ? super A> handler)
			throws IllegalArgumentException, WritePendingException, ShutdownChannelGroupException,
			UnsupportedOperationException
		{
			final AtomicBoolean interrupted = new AtomicBoolean();
			Future<Void> timeoutTimer;
			if (timeout == Long.MAX_VALUE)
				timeoutTimer = Futures.immediateFuture(null);
			else
				timeoutTimer = timer.schedule(new CloseChannel(delegate, interrupted), timeout, unit);
			DoneWriting<Integer, ? super A> doneWriting = new DoneWriting<>(attachment, handler,
				timeoutTimer);
			delegate.write(source, interrupted, doneWriting);
		}

		@Override
		public <A> void close(final A attachment,
			final CompletionHandler<Void, ? super A> handler)
		{
			timer.execute(new Runnable()
			{
				@Override
				public void run()
				{
					try
					{
						delegate.close();
						handler.completed(null, attachment);
					}
					catch (Throwable t)
					{
						handler.failed(t, attachment);
					}
				}
			});
		}
	}

	/**
	 * Notified when a read operation completes.
	 *
	 * @param <V> the result type of the I/O operation
	 * @param <A> the type of the object attached to the I/O operation
	 * @author Gili Tzabari
	 */
	private static class DoneReading<V, A> implements CompletionHandler<V, AtomicBoolean>
	{
		private final A attachment;
		private final CompletionHandler<V, A> handler;
		private final Future<?> timer;
		private boolean done;

		/**
		 * Creates a new DoneReading.
		 *
		 * @param attachment the object to attach to the I/O operation; can be {@code null}
		 * @param handler the completion handler
		 * @param timer the timeout timer to cancel when the operation completes
		 * through to the delegate.
		 */
		public DoneReading(A attachment, CompletionHandler<V, A> handler, Future<?> timer)
		{
			Preconditions.checkNotNull(handler, "delegate may not be null");
			Preconditions.checkNotNull(timer, "timer may not be null");

			this.attachment = attachment;
			this.handler = handler;
			this.timer = timer;
		}

		@Override
		public void completed(V value, AtomicBoolean interrupted)
		{
			done = true;
			timer.cancel(false);
			if (interrupted.get())
				handler.failed(new InterruptedByTimeoutException(), attachment);
			else
				handler.completed(value, attachment);
		}

		@Override
		public void failed(Throwable t, AtomicBoolean interrupted)
		{
			done = true;
			timer.cancel(false);
			if (interrupted.get())
			{
				InterruptedByTimeoutException interruptedException = new InterruptedByTimeoutException();
				interruptedException.addSuppressed(t);
				handler.failed(interruptedException, attachment);
			}
			else
				handler.failed(t, attachment);
		}

		/**
		 * Indicates if the operation has completed.
		 * 
		 * @return true if the operation has completed
		 */
		public boolean isDone()
		{
			return done;
		}
	}

	/**
	 * Notified when a write operation completes.
	 *
	 * @param <V> the result type of the I/O operation
	 * @param <A> the type of the object attached to the I/O operation
	 * @author Gili Tzabari
	 */
	private static class DoneWriting<V, A> implements CompletionHandler<V, AtomicBoolean>
	{
		private final A attachment;
		private final CompletionHandler<V, A> delegate;
		private final Future<?> timer;
		private boolean done;

		/**
		 * Creates a new DoneWriting.
		 *
		 * @param attachment the object to attach to the I/O operation; can be {@code null}
		 * @param handler the completion handler
		 * @param timer the timeout timer to cancel when the operation completes
		 * through to the delegate.
		 */
		public DoneWriting(A attachment, CompletionHandler<V, A> handler, Future<?> timer)
		{
			Preconditions.checkNotNull(handler, "delegate may not be null");
			Preconditions.checkNotNull(timer, "timer may not be null");

			this.attachment = attachment;
			this.delegate = handler;
			this.timer = timer;
		}

		@Override
		public void completed(V value, AtomicBoolean interrupted)
		{
			done = true;
			timer.cancel(false);
			if (interrupted.get())
				delegate.failed(new InterruptedByTimeoutException(), attachment);
			else
				delegate.completed(value, attachment);
		}

		@Override
		public void failed(Throwable t, AtomicBoolean interrupted)
		{
			done = true;
			timer.cancel(false);
			if (interrupted.get())
			{
				InterruptedByTimeoutException interruptedException = new InterruptedByTimeoutException();
				interruptedException.addSuppressed(t);
				delegate.failed(interruptedException, attachment);
			}
			else
				delegate.failed(t, attachment);
		}

		/**
		 * Indicates if the operation has completed.
		 * 
		 * @return true if the operation has completed
		 */
		public boolean isDone()
		{
			return done;
		}
	}

	/**
	 * Interrupts an ongoing operation by closing the channel.
	 */
	private static class CloseChannel implements Callable<Void>
	{
		private final Closeable channel;
		private final AtomicBoolean interrupted;

		/**
		 * Creates a new CloseChannel.
		 * 
		 * @param channel the channel to close
		 * @param interrupted indicates if the operation was interrupted
		 */
		public CloseChannel(Closeable channel, AtomicBoolean interrupted)
		{
			this.channel = channel;
			this.interrupted = interrupted;
		}

		@Override
		public Void call() throws IOException
		{
			interrupted.set(true);
			channel.close();
			return null;
		}
	}
}
