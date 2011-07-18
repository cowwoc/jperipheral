package org.jperipheral;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.Charset;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Gili Tzabari
 */
public class AsynchronousByteChannelFactory
{
	private static final ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactory()
	{
		private ThreadFactory delegate = Executors.defaultThreadFactory();

		@Override
		public Thread newThread(Runnable r)
		{
			Thread result = delegate.newThread(r);
			result.setDaemon(true);
			return result;
		}
	});

	/**
	 * Creates an AsynchronousByteChannel that reads a predefined string.
	 * 
	 * @param input the String to read
	 * @param output the StringBuilder to write into
	 * @param charset the character set used to encode the text
	 * @return a AsynchronousByteChannel
	 */
	public static AsynchronousByteChannel fromString(String input, final StringBuilder output,
																									 final Charset charset)
	{
		final EncodingReadableByteChannel encoder = new EncodingReadableByteChannel(charset);
		final DecodingWriteableByteChannel decoder = new DecodingWriteableByteChannel(charset);
		encoder.append(input);

		return new AsynchronousByteChannel()
		{
			private boolean closed;

			@Override
			public <A> void read(final ByteBuffer dst, final A attachment,
													 final CompletionHandler<Integer, ? super A> handler)
			{
				executor.execute(new Runnable()
				{
					@Override
					public void run()
					{
						try
						{
							handler.completed(encoder.read(dst), attachment);
						}
						catch (IOException e)
						{
							handler.failed(e, attachment);
						}
					}
				});
			}

			@Override
			public Future<Integer> read(final ByteBuffer dst)
			{
				return executor.submit(new Callable<Integer>()
				{
					@Override
					public Integer call() throws IOException
					{
						return encoder.read(dst);
					}
				});
			}

			@Override
			public <A> void write(final ByteBuffer src, final A attachment,
														final CompletionHandler<Integer, ? super A> handler)
			{
				executor.execute(new Runnable()
				{
					@Override
					public void run()
					{
						try
						{
							int bytesWritten = decoder.write(src);
							output.append(decoder.toStringBuilder());
							decoder.toStringBuilder().setLength(0);
							handler.completed(bytesWritten, attachment);
						}
						catch (IOException e)
						{
							handler.failed(e, attachment);
						}
					}
				});
			}

			@Override
			public Future<Integer> write(final ByteBuffer src)
			{
				return executor.submit(new Callable<Integer>()
				{
					@Override
					public Integer call() throws IOException
					{
						int bytesWritten = decoder.write(src);
						output.append(decoder.toStringBuilder());
						decoder.toStringBuilder().setLength(0);
						return bytesWritten;
					}
				});
			}

			@Override
			public void close() throws IOException
			{
				closed = true;
			}

			@Override
			public boolean isOpen()
			{
				return !closed;
			}
		};
	}

	/**
	 * Creates an AsynchronousByteChannel that limits the number of bytes that may be read.
	 * 
	 * @param channel the channel to wrap
	 * @param limit the number of bytes that may be read before end-of-stream should be returned
	 * @return a AsynchronousByteChannel
	 * @throws IllegalArgumentException if limit is negative
	 */
	public static AsynchronousByteChannel limit(final AsynchronousByteChannel channel,
																							final long limit)
	{
		Preconditions.checkArgument(limit >= 0, "limit must be non-negative");

		return new AsynchronousByteChannel()
		{
			private long position = 0;

			@Override
			public <A> void read(final ByteBuffer dst, final A attachment,
													 final CompletionHandler<Integer, ? super A> handler)
			{
				final long remaining = limit - position;
				if (remaining < dst.remaining())
					dst.limit(dst.position() + (int) remaining);
				channel.read(dst, attachment, new CompletionHandler<Integer, A>()
				{
					@Override
					public void completed(Integer bytesRead, A attachment)
					{
						// keep track of position
						int result;
						if (bytesRead != -1)
						{
							position += bytesRead;
							result = bytesRead;
						}
						else if (remaining == 0)
							result = -1;
						else
							result = bytesRead;
						handler.completed(result, attachment);
					}

					@Override
					public void failed(Throwable t, A attachment)
					{
						handler.failed(t, attachment);
					}
				});
			}

			@Override
			public Future<Integer> read(final ByteBuffer dst)
			{
				final long remaining = limit - position;
				if (remaining < dst.remaining())
					dst.limit(dst.position() + (int) remaining);
				final Future<Integer> delegate = channel.read(dst);
				return new Future<Integer>()
				{
					@Override
					public boolean cancel(boolean mayInterruptIfRunning)
					{
						return delegate.cancel(mayInterruptIfRunning);
					}

					@Override
					public boolean isCancelled()
					{
						return delegate.isCancelled();
					}

					@Override
					public boolean isDone()
					{
						return delegate.isDone();
					}

					@Override
					public Integer get() throws InterruptedException, ExecutionException
					{
						try
						{
							return get(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
						}
						catch (TimeoutException e)
						{
							throw new AssertionError(e);
						}
					}

					@Override
					public Integer get(long timeout, TimeUnit unit) throws InterruptedException,
																																 ExecutionException,
																																 TimeoutException
					{
						Integer result = delegate.get(timeout, unit);
						// keep track of position
						if (result != -1)
							position += result;
						else if (remaining == 0)
							result = -1;
						return result;
					}
				};
			}

			@Override
			public <A> void write(ByteBuffer src, A attachment,
														CompletionHandler<Integer, ? super A> handler)
			{
				throw new UnsupportedOperationException("This channel only supports read operations");
			}

			@Override
			public Future<Integer> write(ByteBuffer src)
			{
				throw new UnsupportedOperationException("This channel only supports read operations");
			}

			@Override
			public void close() throws IOException
			{
				channel.close();
			}

			@Override
			public boolean isOpen()
			{
				return channel.isOpen();
			}
		};
	}

	/**
	 * Creates an AsynchronousByteChannel that interrupts a read operation at a specific position.
	 * Any read operations that span the position will be forced to read up to the position.
	 * Subsequent reads will proceed normally.
	 * 
	 * @param channel the channel to wrap
	 * @param readBarrier the position at which read operations should be interrupted (0 to disable)
	 * @param writeBarrier the position at which write operations should be interrupted (0 to disable)
	 * @return a AsynchronousByteChannel
	 * @throws IllegalArgumentException if {@code readBarrier < 0 || writeBarrier < 0}
	 */
	public static AsynchronousByteChannel interruptAt(final AsynchronousByteChannel channel,
																										final long readBarrier, final long writeBarrier)
	{
		Preconditions.checkArgument(readBarrier >= 0, "readBarrier must be non-negative");
		Preconditions.checkArgument(writeBarrier >= 0, "writeBarrier must be non-negative");

		return new AsynchronousByteChannel()
		{
			private long readPosition = 0;
			private long writePosition = 0;

			@Override
			public <A> void read(final ByteBuffer dst, final A attachment,
													 final CompletionHandler<Integer, ? super A> handler)
			{
				final long remaining = readBarrier - readPosition;
				if (remaining <= 0)
				{
					channel.read(dst, attachment, handler);
					return;
				}
				ByteBuffer truncated = dst.duplicate();
				if (remaining < truncated.remaining())
					truncated.limit(truncated.position() + (int) remaining);
				channel.read(truncated, attachment, new CompletionHandler<Integer, A>()
				{
					@Override
					public void completed(Integer result, A attachment)
					{
						// keep track of position
						if (result != -1)
						{
							readPosition += result;
							dst.position(dst.position() + result);
						}
						handler.completed(result, attachment);
					}

					@Override
					public void failed(Throwable t, A attachment)
					{
						handler.failed(t, attachment);
					}
				});
			}

			@Override
			public Future<Integer> read(final ByteBuffer dst)
			{
				final long remaining = readBarrier - readPosition;
				if (remaining <= 0)
					return channel.read(dst);
				ByteBuffer truncated = dst.duplicate();
				if (remaining < truncated.remaining())
					truncated.limit(truncated.position() + (int) remaining);
				final Future<Integer> delegate = channel.read(truncated);
				return new Future<Integer>()
				{
					@Override
					public boolean cancel(boolean mayInterruptIfRunning)
					{
						return delegate.cancel(mayInterruptIfRunning);
					}

					@Override
					public boolean isCancelled()
					{
						return delegate.isCancelled();
					}

					@Override
					public boolean isDone()
					{
						return delegate.isDone();
					}

					@Override
					public Integer get() throws InterruptedException, ExecutionException
					{
						try
						{
							return get(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
						}
						catch (TimeoutException e)
						{
							throw new AssertionError(e);
						}
					}

					@Override
					public Integer get(long timeout, TimeUnit unit) throws InterruptedException,
																																 ExecutionException,
																																 TimeoutException
					{
						Integer result = delegate.get(timeout, unit);
						// keep track of position
						if (result != -1)
						{
							readPosition += result;
							dst.position(dst.position() + result);
						}
						return result;
					}
				};
			}

			@Override
			public <A> void write(final ByteBuffer src, A attachment,
														final CompletionHandler<Integer, ? super A> handler)
			{
				final long remaining = writeBarrier - writePosition;
				if (remaining <= 0)
				{
					channel.write(src, attachment, handler);
					return;
				}
				ByteBuffer truncated = src.duplicate();
				if (remaining < truncated.remaining())
					truncated.limit(truncated.position() + (int) remaining);
				channel.write(truncated, attachment, new CompletionHandler<Integer, A>()
				{
					@Override
					public void completed(Integer result, A attachment)
					{
						// keep track of position
						if (result != -1)
						{
							writePosition += result;
							src.position(src.position() + result);
						}
						handler.completed(result, attachment);
					}

					@Override
					public void failed(Throwable t, A attachment)
					{
						handler.failed(t, attachment);
					}
				});
			};

			@Override
			public Future<Integer> write(final ByteBuffer src)
			{
				final long remaining = writeBarrier - writePosition;
				if (remaining <= 0)
					return channel.write(src);
				ByteBuffer truncated = src.duplicate();
				if (remaining < truncated.remaining())
					truncated.limit(truncated.position() + (int) remaining);
				final Future<Integer> delegate = channel.write(truncated);
				return new Future<Integer>()
				{
					@Override
					public boolean cancel(boolean mayInterruptIfRunning)
					{
						return delegate.cancel(mayInterruptIfRunning);
					}

					@Override
					public boolean isCancelled()
					{
						return delegate.isCancelled();
					}

					@Override
					public boolean isDone()
					{
						return delegate.isDone();
					}

					@Override
					public Integer get() throws InterruptedException, ExecutionException
					{
						try
						{
							return get(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
						}
						catch (TimeoutException e)
						{
							throw new AssertionError(e);
						}
					}

					@Override
					public Integer get(long timeout, TimeUnit unit) throws InterruptedException,
																																 ExecutionException,
																																 TimeoutException
					{
						Integer result = delegate.get(timeout, unit);
						// keep track of position
						if (result != -1)
						{
							writePosition += result;
							src.position(src.position() + result);
						}
						return result;
					}
				};
			}

			@Override
			public void close() throws IOException
			{
				channel.close();
			}

			@Override
			public boolean isOpen()
			{
				return channel.isOpen();
			}
		};
	}
}
