package org.jperipheral;

import java.util.NavigableMap;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.Charset;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author Gili Tzabari
 */
public class AsynchronousByteChannelFactory
{
	/*
	 * executor's coreSize must be greater than one because some operations fire an I/O operation
	 * in a separate executor thread and block the main thread using Future.get().
	 */
	private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2,
		new ThreadFactoryBuilder().setDaemon(true).
		setNameFormat(AsynchronousByteChannelFactory.class.getSimpleName() + "-%d").build());

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
						catch (RuntimeException | Error e)
						{
							handler.failed(e, attachment);
							throw e;
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
						catch (RuntimeException | Error e)
						{
							handler.failed(e, attachment);
							throw e;
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
	 * Creates an AsynchronousByteChannel that interrupts a read/write operation at a specific
	 * position. Any read/write operations that span the position will be forced to read/write up to
	 * the position. Reads/writes at a barrier will wait for the time to elapse and then read/write
	 * up to the next barrier.
	 * 
	 * @param channel the channel to wrap
	 * @param readBarriers Map[byteIndex, delayInMilliseconds] that indicates when a byte will become available for
	 *   reading. Bytes not mentioned by the map are available immediately.
	 * @param writeBarriers Map[byteIndex, delayInMilliseconds] that indicates when a byte will become available for
	 *   writing. Bytes not mentioned by the map are available immediately.
	 * @return a AsynchronousByteChannel
	 * @throws IllegalArgumentException if {@code readBarriers < 0 || writeBarriers < 0}
	 */
	public static AsynchronousByteChannel delay(final AsynchronousByteChannel channel,
		final NavigableMap<Long, Long> readBarriers,
		final NavigableMap<Long, Long> writeBarriers)
	{
		Preconditions.checkNotNull(readBarriers, "readBarriers may not be null");
		Preconditions.checkNotNull(writeBarriers, "writeBarriers may not be null");

		return new AsynchronousByteChannel()
		{
			private long readPosition = 0;
			private long writePosition = 0;

			@Override
			public <A> void read(final ByteBuffer dst, final A attachment,
				final CompletionHandler<Integer, ? super A> handler)
			{
				Entry<Long, Long> nextRead = readBarriers.higherEntry(readPosition);
				final long remaining;
				Long timeLeft = readBarriers.get(readPosition);
				if (nextRead == null)
					remaining = dst.remaining();
				else
					remaining = nextRead.getKey() - readPosition;
				if (timeLeft == null)
					timeLeft = 0L;
				executor.schedule(new Runnable()
				{
					@Override
					public void run()
					{
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
				}, timeLeft, TimeUnit.MILLISECONDS);
			}

			@Override
			public Future<Integer> read(final ByteBuffer dst)
			{
				Entry<Long, Long> nextRead = readBarriers.higherEntry(readPosition);
				final long remaining;
				Long timeLeft = readBarriers.get(readPosition);
				if (nextRead == null)
					remaining = dst.remaining();
				else
					remaining = nextRead.getKey() - readPosition;
				if (timeLeft == null)
					timeLeft = 0L;
				return executor.schedule(new Callable<Integer>()
				{
					@Override
					public Integer call() throws Exception
					{
						ByteBuffer truncated = dst.duplicate();
						if (remaining < truncated.remaining())
							truncated.limit(truncated.position() + (int) remaining);
						Integer result = channel.read(truncated).get();

						// keep track of position
						if (result != -1)
						{
							readPosition += result;
							dst.position(dst.position() + result);
						}
						return result;
					}
				}, timeLeft, TimeUnit.MILLISECONDS);
			}

			@Override
			public <A> void write(final ByteBuffer src, final A attachment,
				final CompletionHandler<Integer, ? super A> handler)
			{
				Entry<Long, Long> nextWrite = writeBarriers.higherEntry(writePosition);
				final long remaining;
				Long timeLeft = writeBarriers.get(writePosition);
				if (nextWrite == null)
					remaining = src.remaining();
				else
					remaining = nextWrite.getKey() - writePosition;
				if (timeLeft == null)
					timeLeft = 0L;
				executor.schedule(new Runnable()
				{
					@Override
					public void run()
					{
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
					}
				}, timeLeft, TimeUnit.MILLISECONDS);
			}

			@Override
			public Future<Integer> write(final ByteBuffer src)
			{
				Entry<Long, Long> nextWrite = writeBarriers.higherEntry(writePosition);
				final long remaining;
				Long timeLeft = writeBarriers.get(writePosition);
				if (nextWrite == null)
					remaining = src.remaining();
				else
					remaining = nextWrite.getKey() - writePosition;
				if (timeLeft == null)
					timeLeft = 0L;
				return executor.schedule(new Callable<Integer>()
				{
					@Override
					public Integer call() throws Exception
					{
						ByteBuffer truncated = src.duplicate();
						if (remaining < truncated.remaining())
							truncated.limit(truncated.position() + (int) remaining);
						Integer result = channel.write(truncated).get();

						// keep track of position
						if (result != -1)
						{
							writePosition += result;
							src.position(src.position() + result);
						}
						return result;
					}
				}, timeLeft, TimeUnit.MILLISECONDS);
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
