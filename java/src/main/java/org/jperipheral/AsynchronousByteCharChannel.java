package org.jperipheral;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Bytes;
import com.google.common.util.concurrent.Futures;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.nio.channels.ReadPendingException;
import java.nio.channels.ShutdownChannelGroupException;
import java.nio.channels.WritePendingException;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An adapter that maps an AsynchronousCharChannel on top of an existing AsynchronousByteChannel.
 *
 * @author Gili Tzabari
 */
public final class AsynchronousByteCharChannel implements AsynchronousCharChannel
{
	private final Logger log = LoggerFactory.getLogger(AsynchronousByteCharChannel.class);
	private final PeripheralChannelGroup group;
	/**
	 * Line delimiters.
	 */
	private final Pattern lineDelimiters = Pattern.compile("\\r|\\n");
	/**
	 * The underlying AsynchronousByteChannel.
	 */
	private final AsynchronousByteChannel channel;
	/**
	 * The character encoding.
	 */
	private final Charset charset;
	private AtomicBoolean closed = new AtomicBoolean();
	private AtomicBoolean reading = new AtomicBoolean();
	/**
	 * Retains malformed byte sequence in the hope of more data coming in.
	 */
	private final DecodingWriteableByteChannel readDecoder;
	/**
	 * Bytes read from the AsynchronousByteChannel, later decoded into <code>charactersRead</code>.
	 * Any bytes that cannot be decoded (some characters span multiple bytes) remain in the buffer
	 * for subsequent read operations.
	 */
	private final ByteBuffer bytesRead = ByteBuffer.allocate(1024);
	/**
	 * Characters decoded from <code>bytesRead</code>.
	 */
	private final StringBuilder charactersRead = new StringBuilder();
	/**
	 * Indicates if the subsequent newline character should be disregarded by readLine().
	 */
	private boolean skipNextNewline;
	private AtomicBoolean writing = new AtomicBoolean();
	/**
	 * The bytes to write out.
	 */
	private final ByteBuffer bytesToWrite = ByteBuffer.allocate(128);

	/**
	 * Creates a new AsynchronousByteCharChannel.
	 * 
	 * @param channel the underlying AsynchronousByteChannel
	 * @param charset the character set
	 * @param group the group associated with the channel. Providing an executor with an insufficient
	 *   number of threads may lead to deadlocks. This class requires an additional thread per
	 *   operation. For example, if reading from the underlying channel requires a dedicated thread,
	 *   the executor should contain at least two threads.
	 * in a separate executor thread and block the main thread using Future.get().

	 * @throws NullPointerException if channel, charset or group are null
	 * @throws ShutdownChannelGroupException if the channel group is shut down
	 */
	private AsynchronousByteCharChannel(AsynchronousByteChannel channel, Charset charset,
		PeripheralChannelGroup group)
	{
		Preconditions.checkNotNull(channel, "channel may not be null");
		Preconditions.checkNotNull(charset, "charset may not be null");
		Preconditions.checkNotNull(group, "group may not be null");
		if (group.isShutdown())
			throw new ShutdownChannelGroupException();

		this.channel = channel;
		this.charset = charset;
		this.group = group;
		this.readDecoder = new DecodingWriteableByteChannel(charset);
	}

	/**
	 * Opens an asynchronous character channel.
	 *
	 * @param channel the underlying AsynchronousByteChannel
	 * @param charset the character set
	 * @param group the group associated with the channel
	 * @return A new asynchronous socket channel
	 * @throws NullPointerException if channel, charset or group are null
	 */
	public static AsynchronousByteCharChannel open(AsynchronousByteChannel channel, Charset charset,
		PeripheralChannelGroup group)
	{
		return new AsynchronousByteCharChannel(channel, charset, group);
	}

	@Override
	public synchronized <A> void read(final CharBuffer target, final A attachment,
		final CompletionHandler<Integer, ? super A> handler)
		throws IllegalArgumentException, ReadPendingException, ShutdownChannelGroupException
	{
		if (target.isReadOnly())
			throw new IllegalArgumentException("target may not be read-only");
		try
		{
			group.executor().execute(new Runnable()
			{
				@Override
				public void run()
				{
					try
					{
						if (closed.get())
						{
							handler.failed(new ClosedChannelException(), attachment);
							return;
						}
						if (!reading.compareAndSet(false, true))
							throw new ReadPendingException();
						OperationDone<Integer, ? super A> operationDone = new OperationDone<>(handler, reading);
						if (target.remaining() <= 0)
						{
							operationDone.completed(0, attachment);
							return;
						}
						skipNextNewline = false;
						// Read zero bytes to indicate that the read buffer should be checked before reading from
						// the underlying channel.
						new ReadCharacters<>(null, target, operationDone).completed(false, attachment);
					}
					catch (Error e)
					{
						handler.failed(e, attachment);
						throw e;
					}
				}
			});
		}
		catch (RejectedExecutionException e)
		{
			if (group.isTerminated())
				throw new ShutdownChannelGroupException();
			throw e;
		}
	}

	@Override
	public Future<Integer> read(final CharBuffer target)
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
				try
				{
					if (target.remaining() <= 0)
						return 0;
					// Read zero bytes to indicate that the read buffer should be checked before reading from
					// the underlying channel.
					ReadCharacters<Void> readCharacters = new ReadCharacters<>(Futures.immediateFuture(false),
						target, null);

					try
					{
						return readCharacters.get();
					}
					catch (ExecutionException e)
					{
						Throwable cause = e.getCause();
						if (cause instanceof Exception)
							throw (Exception) cause;
						// ExecutionException is only supposed to propogate Exception, not Error
						throw new AssertionError(cause);
					}
				}
				finally
				{
					reading.set(false);
				}
			}
		});
	}

	@Override
	public <A> void readLine(final A attachment, final CompletionHandler<String, ? super A> handler)
		throws IllegalArgumentException, ReadPendingException, ShutdownChannelGroupException
	{
		try
		{
			group.executor().execute(new Runnable()
			{
				@Override
				public void run()
				{
					try
					{
						if (closed.get())
						{
							handler.failed(new ClosedChannelException(), attachment);
							return;
						}
						if (!reading.compareAndSet(false, true))
							throw new ReadPendingException();
						OperationDone<String, ? super A> doneReading = new OperationDone<>(handler, reading);
						// Read zero bytes to indicate that the read buffer should be checked before reading from
						// the underlying channel.
						new ReadLine<>(null, doneReading).completed(false, attachment);
					}
					catch (Error e)
					{
						handler.failed(e, attachment);
						throw e;
					}
				}
			});
		}
		catch (RejectedExecutionException e)
		{
			if (group.isTerminated())
				throw new ShutdownChannelGroupException();
			throw e;
		}
	}

	@Override
	public Future<String> readLine()
		throws IllegalArgumentException, ReadPendingException
	{
		return group.executor().submit(new Callable<String>()
		{
			@Override
			public String call() throws Exception
			{
				if (closed.get())
					throw new ClosedChannelException();
				if (!reading.compareAndSet(false, true))
					throw new ReadPendingException();
				try
				{
					// Read zero bytes to indicate that the read buffer should be checked before reading from
					// the underlying channel.
					ReadLine<Void> readLine = new ReadLine<>(Futures.immediateFuture(false), null);

					try
					{
						return readLine.get();
					}
					catch (ExecutionException e)
					{
						Throwable cause = e.getCause();
						if (cause instanceof Exception)
							throw (Exception) cause;
						// ExecutionException is only supposed to propogate Exception, not Error
						throw new AssertionError(cause);
					}
				}
				finally
				{
					reading.set(false);
				}
			}
		});
	}

	@Override
	public synchronized <A> void write(final CharBuffer source,
		final A attachment, final CompletionHandler<Integer, ? super A> handler)
		throws WritePendingException, ShutdownChannelGroupException
	{
		try
		{
			group.executor().execute(new Runnable()
			{
				@Override
				public void run()
				{
					try
					{
						if (closed.get())
						{
							handler.failed(new ClosedChannelException(), attachment);
							return;
						}
						if (!writing.compareAndSet(false, true))
							throw new WritePendingException();
						OperationDone<Integer, ? super A> operationDone = new OperationDone<>(handler, writing);
						if (source.remaining() <= 0)
						{
							operationDone.completed(0, attachment);
							return;
						}
						EncodingReadableByteChannel encoder = new EncodingReadableByteChannel(charset);
						encoder.append(source);
						bytesToWrite.clear();
						try
						{
							encoder.read(bytesToWrite);
							bytesToWrite.flip();
						}
						catch (IOException e)
						{
							handler.failed(e, attachment);
							return;
						}
						WriteCharacters<? super A> writeCharacters =
							new WriteCharacters<>(null, source, operationDone);
						channel.write(bytesToWrite, attachment, writeCharacters);
					}
					catch (Error e)
					{
						handler.failed(e, attachment);
						throw e;
					}
				}
			});
		}
		catch (RejectedExecutionException e)
		{
			if (group.isTerminated())
				throw new ShutdownChannelGroupException();
			throw e;
		}
	}

	@Override
	public synchronized Future<Integer> write(final CharBuffer source)
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
				try
				{
					if (source.remaining() <= 0)
						return 0;
					EncodingReadableByteChannel encoder = new EncodingReadableByteChannel(charset);
					encoder.append(source);
					bytesToWrite.clear();
					encoder.read(bytesToWrite);
					bytesToWrite.flip();
					WriteCharacters<Void> writeCharacters = new WriteCharacters<>(Futures.immediateFuture(0),
						source, null);

					try
					{
						return writeCharacters.get();
					}
					catch (ExecutionException e)
					{
						Throwable cause = e.getCause();
						if (cause instanceof Exception)
							throw (Exception) cause;
						// ExecutionException is only supposed to propogate Exception, not Error
						throw new AssertionError(cause);
					}
				}
				finally
				{
					writing.set(false);
				}
			}
		});
	}

	@Override
	public void close() throws IOException
	{
		if (closed.compareAndSet(false, true))
			channel.close();
	}

	@Override
	public boolean isOpen()
	{
		return channel.isOpen();
	}

	@Override
	public String toString()
	{
		return getClass().getName() + "[" + channel + "]";
	}

	/**
	 * Returns the future parameter if it is not null. Otherwise, returns a Future that throws
	 * {@code UnsupportedOperationException}.
	 * 
	 * @param <V> the return type of the future
	 * @param future the future to evaluate
	 * @return a non-null Future
	 */
	private static <V> Future<V> notNull(Future<V> future)
	{
		if (future != null)
			return future;
		return Futures.immediateFailedFuture(new UnsupportedOperationException());
	}

	/**
	 * Decodes bytesRead into charactersRead.
	 *
	 * @param <A> the type of the object attached to the I/O operation
	 * @author Gili Tzabari
	 */
	private class ByteDecoder<A> extends TransformingFuture<Integer, Boolean>
		implements CompletionHandler<Integer, A>
	{
		private final CompletionHandler<Boolean, A> handler;

		/**
		 * Creates a new ByteDecoder.
		 *
		 * @param input the Future to read the bytes from. Ignored if set to null.
		 * @param handler a handler for consuming the result of an asynchronous I/O operation.
		 *   Ignored if set to null.
		 * @throws IllegalArgumentException if both input and handler are null
		 */
		public ByteDecoder(Future<Integer> input, CompletionHandler<Boolean, A> handler)
		{
			super(notNull(input));
			Preconditions.checkArgument(input != null || handler != null, "Either input or handler must "
				+ "be non-null");

			this.handler = handler;
		}

		@Override
		public void completed(Integer numBytesRead, A attachment)
		{
			if (handler == null)
				throw new UnsupportedOperationException("handler is null");
			try
			{
				handler.completed(process(numBytesRead), attachment);
			}
			catch (IOException e)
			{
				handler.failed(e, attachment);
			}
		}

		/**
		 * Process incoming values.
		 * 
		 * @param numBytesRead the number of bytes that were read, -1 on end of stream
		 * @return true if the end of stream has been reached
		 * @throws IOException if an error occurs while decoding the bytes
		 */
		private boolean process(Integer numBytesRead) throws IOException
		{
			if (numBytesRead == -1)
				return true;
			bytesRead.flip();
			assert (bytesRead.remaining() == numBytesRead): "remaining: " + bytesRead.remaining()
				+ ", numBytesRead: " + numBytesRead;
			do
			{
				readDecoder.write(bytesRead);
			}
			while (bytesRead.hasRemaining());
			bytesRead.compact();
			StringBuilder decodedCharacters = readDecoder.toStringBuilder();
			charactersRead.append(decodedCharacters);
			int numCharactersRead = decodedCharacters.length();
			decodedCharacters.delete(0, numCharactersRead);
			return false;
		}

		@Override
		public void failed(Throwable t, A attachment)
		{
			if (handler == null)
				throw new UnsupportedOperationException("handler is null");
			handler.failed(t, attachment);
		}

		@Override
		public Boolean get(long timeout, TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException
		{
			Integer result = delegate.get(timeout, unit);
			try
			{
				return process(result);
			}
			catch (IOException | RuntimeException e)
			{
				throw new ExecutionException(e);
			}
		}
	}

	/**
	 * Attempts to read one or more characters.
	 *
	 * @param <A> the type of the object attached to the I/O operation
	 * @author Gili Tzabari
	 */
	private class ReadCharacters<A> extends TransformingFuture<Boolean, Integer>
		implements CompletionHandler<Boolean, A>
	{
		private final CharBuffer target;
		private final CompletionHandler<Integer, A> handler;

		/**
		 * Creates a new ReadCharacters.
		 *
		 * @param input the Future to read the bytes from. Ignored if set to null.
		 * @param target the buffer into which characters are to be transferred
		 * @param handler a handler for consuming the result of an asynchronous I/O operation.
		 *   The value denotes the number of characters read by the operation. The current object's
		 *   attachment is passed to the handler. Ignored if set to null
		 * @throws NullPointerException if target is null
		 * @throws IllegalArgumentException if both input and handler are null
		 */
		public ReadCharacters(Future<Boolean> input, CharBuffer target,
			CompletionHandler<Integer, A> handler)
		{
			super(notNull(input));
			Preconditions.checkNotNull(target, "target may not be null");
			Preconditions.checkArgument(input != null || handler != null, "Either input or handler must "
				+ "be non-null");

			this.target = target;
			this.handler = handler;
		}

		@Override
		public void completed(Boolean endOfStream, A attachment)
		{
			if (handler == null)
				throw new UnsupportedOperationException("handler is null");
			if (charactersRead.length() == 0)
			{
				if (endOfStream)
					handler.completed(-1, attachment);
				// We don't have any buffered characters and there is more data in the stream, so keep on
				// reading.
				ByteDecoder<A> byteDecoder = new ByteDecoder<>(null, this);

				assert (bytesRead.position() == 0): bytesRead;
				channel.read(bytesRead, attachment, byteDecoder);
				return;
			}

			int numCharactersRead = Math.min(charactersRead.length(), target.remaining());

			target.put(charactersRead.substring(0, numCharactersRead));
			charactersRead.delete(0, numCharactersRead);
			handler.completed(numCharactersRead, attachment);
		}

		@Override
		public void failed(Throwable t, A attachment)
		{
			if (handler == null)
				throw new UnsupportedOperationException("handler is null");
			handler.failed(t, attachment);
		}

		@Override
		public synchronized Integer get(long timeout, TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException
		{
			long before = System.nanoTime();
			long timeLeft = timeout;
			while (true)
			{
				boolean endOfStream = delegate.get(timeLeft, unit);

				if (charactersRead.length() == 0)
				{
					if (endOfStream)
						return -1;
					// We don't have any buffered characters and there is more data in the stream, so keep on
					// reading.
					this.delegate = new ByteDecoder<>(channel.read(bytesRead), null);
					long after = System.nanoTime();
					timeLeft = timeout - unit.convert(after - before, TimeUnit.NANOSECONDS);
					before = after;
					continue;
				}

				int numCharactersRead = Math.min(charactersRead.length(), target.remaining());

				target.put(charactersRead.substring(0, numCharactersRead));
				charactersRead.delete(0, numCharactersRead);
				return numCharactersRead;
			}
		}
	}

	/**
	 * Attempts to read one line of characters.
	 *
	 * @param <A> the type of the object attached to the I/O operation
	 * @author Gili Tzabari
	 */
	private class ReadLine<A> extends TransformingFuture<Boolean, String>
		implements CompletionHandler<Boolean, A>
	{
		private final StringBuilder target = new StringBuilder();
		private final CompletionHandler<String, A> handler;

		/**
		 * Creates a new LineListener.
		 *
		 * @param handler a handler for consuming the result of an asynchronous I/O operation. The
		 *   value denotes the line read. The current object's attachment is passed to the handler.
		 * @throws NullPointerException if handler is null
		 */
		public ReadLine(Future<Boolean> future, CompletionHandler<String, A> handler)
		{
			super(notNull(future));

			this.handler = handler;
		}

		@Override
		public void completed(Boolean endOfStream, A attachment)
		{
			if (handler == null)
				throw new UnsupportedOperationException("handler is null");
			while (true)
			{
				Matcher matcher = lineDelimiters.matcher(charactersRead);
				if (!matcher.find())
				{
					target.append(charactersRead);
					charactersRead.setLength(0);
					if (!endOfStream)
					{
						// delimiters not found and there is more data in the stream, so keep on reading.
						ByteDecoder<A> byteDecoder = new ByteDecoder<>(null, this);

						assert (bytesRead.position() == 0): bytesRead;
						channel.read(bytesRead, attachment, byteDecoder);
						return;
					}
					break;
				}
				// Matcher.group() references the original String so we need to backup the result before
				// modifying the String.
				String match = matcher.group();
				target.append(charactersRead, 0, matcher.start());
				charactersRead.delete(0, matcher.end());
				switch (match)
				{
					case "\r":
					{
						skipNextNewline = true;
						break;
					}
					case "\n":
					{
						if (skipNextNewline && matcher.start() == 0)
						{
							// Handle the case where \r\n is split across separate reads
							skipNextNewline = false;
							continue;
						}
						break;
					}
					default:
						throw new AssertionError("Unexpected delimiter: '" + toHex(match) + "'");
				}
				break;
			}
			String result = target.toString();
			if (endOfStream && result.isEmpty())
				result = null;
			handler.completed(result, attachment);
		}

		@Override
		public void failed(Throwable t, A attachment)
		{
			if (handler == null)
				throw new UnsupportedOperationException("handler is null");
			handler.failed(t, attachment);
		}

		@Override
		public String get(long timeout, TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException
		{
			long before = System.nanoTime();
			long timeLeft = timeout;
			boolean endOfStream;
			while (true)
			{
				endOfStream = delegate.get(timeLeft, unit);
				Matcher matcher = lineDelimiters.matcher(charactersRead);
				if (!matcher.find())
				{
					target.append(charactersRead);
					charactersRead.setLength(0);
					if (!endOfStream)
					{
						// We don't have any buffered characters and there is more data in the stream, so keep on
						// reading.
						long after = System.nanoTime();
						timeLeft = timeout - unit.convert(after - before, TimeUnit.NANOSECONDS);
						before = after;
						this.delegate = new ByteDecoder<>(channel.read(bytesRead), null);
						continue;
					}
					break;
				}
				// Matcher.group() references the original String so we need to backup the result before
				// modifying the String.
				String match = matcher.group();
				target.append(charactersRead, 0, matcher.start());
				charactersRead.delete(0, matcher.end());
				switch (match)
				{
					case "\r":
					{
						skipNextNewline = true;
						break;
					}
					case "\n":
					{
						if (skipNextNewline && matcher.start() == 0)
						{
							// Handle the case where \r\n is split across separate reads
							skipNextNewline = false;
							continue;
						}
						break;
					}
					default:
						throw new AssertionError("Unexpected delimiter: '" + toHex(match) + "'");
				}
				break;
			}
			String result = target.toString();
			if (endOfStream && result.isEmpty())
				result = null;
			return result;
		}
	}

	/**
	 * Notified when a read operation completes.
	 *
	 * @param <V> the result type of the I/O operation
	 * @param <A> the type of the object attached to the I/O operation
	 * @author Gili Tzabari
	 */
	private class OperationDone<V, A> implements CompletionHandler<V, A>
	{
		private final CompletionHandler<V, A> handler;
		private final AtomicBoolean running;

		/**
		 * Creates a new DoneReading.
		 *
		 * @param handler the handler to delegate to. The current object's attachment is passed to the
		 *   delegate.
		 * @param running the boolean to set to false when the operation completes
		 * @throws NullPointerException if handler or running are null
		 */
		public OperationDone(CompletionHandler<V, A> handler, AtomicBoolean running)
		{
			Preconditions.checkNotNull(handler, "handler may not be null");
			Preconditions.checkNotNull(running, "running may not be null");

			this.handler = handler;
			this.running = running;
		}

		@Override
		public void completed(V value, A attachment)
		{
			running.set(false);
			try
			{
				handler.completed(value, attachment);
			}
			catch (RuntimeException e)
			{
				log.warn("", e);
			}
		}

		@Override
		public void failed(Throwable t, A attachment)
		{
			running.set(false);
			try
			{
				handler.failed(t, attachment);
			}
			catch (RuntimeException e)
			{
				log.warn("", e);
			}
		}
	}

	/**
	 * Attempts to write characters.
	 *
	 * @param <A> the type of the object attached to the I/O operation
	 * @author Gili Tzabari
	 */
	private class WriteCharacters<A> extends TransformingFuture<Integer, Integer>
		implements CompletionHandler<Integer, A>
	{
		private final CharBuffer source;
		private final CompletionHandler<Integer, A> handler;
		private final int initialPosition;

		/**
		 * Creates a new WriteCharacters.
		 *
		 * @param source the buffer from which characters are to be transferred
		 * @param handler a handler for consuming the result of an asynchronous I/O operation.
		 *   The value denotes the number of characters read by the operation. The current object's
		 *   attachment is passed to the handler.
		 * @throws NullPointerException if target or handler are null
		 */
		public WriteCharacters(Future<Integer> input, CharBuffer source,
			CompletionHandler<Integer, A> handler)
		{
			super(notNull(input));
			Preconditions.checkNotNull(source, "source may not be null");
			Preconditions.checkArgument(input != null || handler != null, "Either input or handler must "
				+ "be non-null");

			this.source = source;
			this.handler = handler;
			this.initialPosition = source.position();
		}

		@Override
		public void completed(Integer numBytesWritten, A attachment)
		{
			if (handler == null)
				throw new UnsupportedOperationException("handler is null");
			ByteBuffer outstandingBytes;
			if (numBytesWritten > 0)
				outstandingBytes = updateSourcePosition(numBytesWritten);
			else
				outstandingBytes = bytesToWrite.duplicate();
			if (outstandingBytes.hasRemaining())
			{
				// bytesToWrite updated by updateSourcePosition()
				numBytesWritten = bytesToWrite.position();
				bytesToWrite.put(outstandingBytes);
				bytesToWrite.flip();
				bytesToWrite.position(numBytesWritten);
				channel.write(bytesToWrite, attachment, this);
				return;
			}
			handler.completed(source.position() - initialPosition, attachment);
		}

		@Override
		public void failed(Throwable t, A attachment)
		{
			if (handler == null)
				throw new UnsupportedOperationException("handler is null");
			handler.failed(t, attachment);
		}

		/**
		 * Updates the position of the CharBuffer that was written out.
		 * If the write operation stopped in the middle of a multi-byte character, returns the
		 * bytes that must be written to complete the character.
		 * 
		 * @param numBytesWritten the number of bytes that were written
		 * @return empty buffer if the last character written was whole
		 */
		private ByteBuffer updateSourcePosition(int numBytesWritten)
		{
			// Convert the bytes that were sent back into characters
			DecodingWriteableByteChannel decoder = new DecodingWriteableByteChannel(charset);
			bytesToWrite.flip();
			try
			{
				try
				{
					decoder.write(bytesToWrite);
					decoder.close();

					// Move source forward by the number of whole characters written
					source.position(source.position() + decoder.toStringBuilder().length());
					return ByteBuffer.allocate(0);
				}
				catch (MalformedInputException unused)
				{
					// continue processing below
				}
				// Move source forward by the number of whole characters written
				CharBuffer wholeCharsWritten = source.duplicate();
				wholeCharsWritten.limit(wholeCharsWritten.position() + decoder.toStringBuilder().length());
				source.position(source.position() + wholeCharsWritten.limit());

				// Calculate how many bytes make up the whole characters written
				EncodingReadableByteChannel encoder = new EncodingReadableByteChannel(charset);
				encoder.append(wholeCharsWritten);
				bytesToWrite.position(0);
				encoder.read(bytesToWrite);
				int wholeBytesWritten = bytesToWrite.position();
				bytesToWrite.compact();
				int malformedBytesWritten = numBytesWritten - wholeBytesWritten;

				// Encode the partially-written character
				encoder = new EncodingReadableByteChannel(charset);
				String codepoint = new String(new int[]
					{
						source.toString().codePointAt(0)
					}, 0, 1);
				encoder.append(codepoint);
				ByteBuffer result = ByteBuffer.allocate((int) Math.ceil(charset.newEncoder().
					maxBytesPerChar() * codepoint.length()));
				while (true)
				{
					if (encoder.read(result) == -1)
						break;
				}
				result.flip();
				result.position(malformedBytesWritten);
				return result;
			}
			catch (IOException e)
			{
				// we've already encoded/decoded this String without running into unmappable
				// characters so CharCodingException should not occur. Furthermore, all operations take
				// place on memory buffers so IOException should not occur.
				throw new AssertionError(e);
			}
		}

		@Override
		public Integer get(long timeout, TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException
		{
			try
			{
				long before = System.nanoTime();
				long timeLeft = timeout;
				while (true)
				{
					int numBytesWritten = delegate.get(timeLeft, unit);
					ByteBuffer outstandingBytes;
					if (numBytesWritten > 0)
						outstandingBytes = updateSourcePosition(numBytesWritten);
					else
						outstandingBytes = bytesToWrite.duplicate();
					if (outstandingBytes.hasRemaining())
					{
						// bytesToWrite updated by updateSourcePosition()
						numBytesWritten = bytesToWrite.position();
						bytesToWrite.put(outstandingBytes);
						bytesToWrite.flip();
						bytesToWrite.position(numBytesWritten);
						this.delegate = channel.write(bytesToWrite);
						long after = System.nanoTime();
						timeLeft = timeout - unit.convert(after - before, TimeUnit.NANOSECONDS);
						before = after;
						continue;
					}
					return source.position() - initialPosition;
				}
			}
			catch (RuntimeException e)
			{
				throw new ExecutionException(e);
			}
		}
	}

	/**
	 * Returns a String's bytes in hexadecimal format.
	 * 
	 * @param text the String to convert to hex
	 * @return the String bytes in hexadecimal format
	 */
	private String toHex(String text)
	{
		return Joiner.on(", ").join(Iterables.transform(Bytes.asList(text.getBytes(Charsets.UTF_8)),
			new Function<Byte, String>()
			{
				@Override
				public String apply(Byte input)
				{
					String result = Integer.toHexString(input).toUpperCase();
					if (input < 16)
						return "0x0" + result;
					return "0x" + result;
				}
			}));
	}
}
