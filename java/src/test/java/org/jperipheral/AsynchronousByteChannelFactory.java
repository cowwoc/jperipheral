package org.jperipheral;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

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
	 * @param text the String to read
	 * @param charset the character set used to encode the text
	 */
	public static AsynchronousByteChannel fromString(String text, final Charset charset)
	{
		final EncodingReadableByteChannel in = new EncodingReadableByteChannel(charset);
		in.append(text);

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
							handler.completed(in.read(dst), attachment);
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
						return in.read(dst);
					}
				});
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
				closed = true;
			}

			@Override
			public boolean isOpen()
			{
				return !closed;
			}
		};
	}
}
