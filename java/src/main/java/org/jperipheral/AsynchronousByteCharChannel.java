package org.jperipheral;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Bytes;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.InterruptedByTimeoutException;
import java.nio.channels.ReadPendingException;
import java.nio.channels.ShutdownChannelGroupException;
import java.nio.channels.WritePendingException;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An adapter that maps an AsynchronousCharChannel on top of an existing AsynchronousByteChannel.
 *
 * @author Gili Tzabari
 */
public final class AsynchronousByteCharChannel implements AsynchronousCharChannel
{
	/**
	 * The underlying AsynchronousByteChannel.
	 */
	private final AsynchronousByteChannel channel;
	/**
	 * The character encoding.
	 */
	private final Charset charset;
	/**
	 * Searches for line delimiters.
	 */
	private final Pattern delimiters = Pattern.compile("\\r|\\n");
	private final ScheduledExecutorService timer = Executors.newScheduledThreadPool(0,
		new ThreadFactoryBuilder().setDaemon(false).setNameFormat(getClass().getName() + "-%d").build());
	// ---- START READ OPERATIONS ----
	private State readState = State.INITIAL;
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
	private boolean consumeNewline;
	// ---- END READ OPERATIONS ----		
	private State writeState = State.INITIAL;
	/**
	 * The bytes to write out.
	 */
	private ByteBuffer bytesToWrite = ByteBuffer.allocate(0);

	/**
	 * Creates a new AsynchronousCharChannel.
	 * 
	 * @param channel
	 *        The underlying AsynchronousByteChannel
	 * @param charset
	 *        The character set
	 */
	private AsynchronousByteCharChannel(AsynchronousByteChannel channel, Charset charset)
	{
		this.channel = channel;
		this.charset = charset;
		this.readDecoder = new DecodingWriteableByteChannel(charset);
	}

	/**
	 * Opens an asynchronous character channel.
	 *
	 * @param channel
	 *        The underlying AsynchronousByteChannel
	 * @param charset
	 *        The character set
	 * @return A new asynchronous socket channel
	 */
	public static AsynchronousByteCharChannel open(AsynchronousByteChannel channel, Charset charset)
	{
		return new AsynchronousByteCharChannel(channel, charset);
	}

	@Override
	public synchronized <A> void read(CharBuffer target, A attachment,
																		CompletionHandler<Integer, ? super A> handler)
		throws IllegalArgumentException, ReadPendingException, ShutdownChannelGroupException
	{
		read(target, Long.MAX_VALUE, TimeUnit.MILLISECONDS, attachment, handler);
	}

	@Override
	public synchronized <A> void read(CharBuffer target,
																		long timeout,
																		TimeUnit unit,
																		A attachment,
																		CompletionHandler<Integer, ? super A> handler)
		throws IllegalArgumentException, ReadPendingException, ShutdownChannelGroupException
	{
		synchronized (this)
		{
			if (target.isReadOnly())
				throw new IllegalArgumentException("target may not be read-only");
			if (readState != State.INITIAL)
				throw new ReadPendingException();
			if (target.remaining() <= 0)
			{
				handler.completed(0, attachment);
				return;
			}
			readState = State.RUNNING;
		}
		Future<Void> timeoutTimer;
		if (timeout == Long.MAX_VALUE)
			timeoutTimer = Futures.immediateFuture(null);
		else
		{
			timeoutTimer = timer.schedule(new Callable<Void>()
			{
				@Override
				public Void call() throws IOException
				{
					synchronized (AsynchronousByteCharChannel.this)
					{
						if (readState == State.RUNNING)
							readState = State.INTERRUPTED;
					}
					close();
					return null;
				}
			}, timeout, unit);
		}
		DoneReading<Integer, Void> doneReading = new DoneReading(handler, timeoutTimer);
		ReadCharacters readCharacters = new ReadCharacters(target, doneReading);
		this.consumeNewline = false;
		bytesRead.clear();
		readCharacters.completed(false, null);
		// ReadCharacters.completed() invokes channel.read() if it needs more bytes
	}

	@Override
	public Future<Integer> read(CharBuffer target)
		throws IllegalArgumentException, ReadPendingException, ShutdownChannelGroupException
	{
		synchronized (this)
		{
			if (target.isReadOnly())
				throw new IllegalArgumentException("target may not be read-only");
			if (readState != State.INITIAL)
				throw new ReadPendingException();
			if (target.remaining() <= 0)
				return Futures.immediateFuture(0);
			readState = State.RUNNING;
		}
		SettableFuture<Integer> result = SettableFuture.create();
		FutureCompletionHandler<Integer> futureToHandler = new FutureCompletionHandler<>(result);
		Future<Void> timeoutTimer = Futures.immediateFuture(null);
		DoneReading<Integer, Void> doneReading = new DoneReading(futureToHandler, timeoutTimer);
		ReadCharacters readCharacters = new ReadCharacters(target, doneReading);
		this.consumeNewline = false;
		bytesRead.clear();
		readCharacters.completed(false, null);
		// ReadCharacters.completed() invokes channel.read() if it needs more bytes
		return result;
	}

	@Override
	public <A> void readLine(A attachment, CompletionHandler<String, ? super A> handler)
		throws IllegalArgumentException, ReadPendingException, ShutdownChannelGroupException
	{
		readLine(Long.MAX_VALUE, TimeUnit.MILLISECONDS, attachment, handler);
	}

	@Override
	public <A> void readLine(long timeout, TimeUnit unit, A attachment,
													 CompletionHandler<String, ? super A> handler)
		throws IllegalArgumentException, ReadPendingException, ShutdownChannelGroupException
	{
		synchronized (this)
		{
			if (readState != State.INITIAL)
				throw new ReadPendingException();
			readState = State.RUNNING;
		}
		Future<Void> timeoutTimer;
		if (timeout == Long.MAX_VALUE)
			timeoutTimer = Futures.immediateFuture(null);
		else
		{
			timeoutTimer = timer.schedule(new Callable<Void>()
			{
				@Override
				public Void call() throws IOException
				{
					synchronized (AsynchronousByteCharChannel.this)
					{
						if (readState == State.RUNNING)
							readState = State.INTERRUPTED;
					}
					close();
					return null;
				}
			}, timeout, unit);
		}
		DoneReading<Integer, Void> doneReading = new DoneReading(handler, timeoutTimer);
		ReadLine readLine = new ReadLine(attachment, doneReading);
		bytesRead.clear();
		readLine.completed(false, null);
		// ReadCharacters.completed() invokes channel.read() if it needs more bytes
	}

	@Override
	public Future<String> readLine()
		throws IllegalArgumentException, ReadPendingException, ShutdownChannelGroupException
	{
		synchronized (this)
		{
			if (readState != State.INITIAL)
				throw new ReadPendingException();
			readState = State.RUNNING;
		}
		SettableFuture<String> result = SettableFuture.create();
		FutureCompletionHandler<String> futureToHandler = new FutureCompletionHandler<>(result);
		DoneReading<Integer, Void> doneReading = new DoneReading(futureToHandler,
			Futures.immediateFuture(null));
		ReadLine readLine = new ReadLine(null, doneReading);
		bytesRead.clear();
		readLine.completed(false, null);
		// ReadLine.completed() invokes channel.read() if it needs more bytes
		return result;
	}

	@Override
	public synchronized <A> void write(CharBuffer source,
																		 A attachment, CompletionHandler<Integer, ? super A> handler,
																		 boolean endOfInput)
		throws WritePendingException, ShutdownChannelGroupException
	{
		write(source, Long.MAX_VALUE, TimeUnit.MILLISECONDS, attachment, handler, endOfInput);
	}

	@Override
	public synchronized <A> void write(CharBuffer source,
																		 final long timeout,
																		 final TimeUnit unit,
																		 A attachment,
																		 CompletionHandler<Integer, ? super A> handler,
																		 boolean endOfInput)
		throws IllegalArgumentException, WritePendingException, ShutdownChannelGroupException,
					 UnsupportedOperationException
	{
		synchronized (this)
		{
			if (writeState != State.INITIAL)
				throw new WritePendingException();
			if (source.remaining() <= 0)
			{
				handler.completed(0, attachment);
				return;
			}
			writeState = State.RUNNING;
		}
		Future<Void> timeoutTimer;
		if (timeout == Long.MAX_VALUE)
			timeoutTimer = Futures.immediateFuture(null);
		else
		{
			timeoutTimer = timer.schedule(new Callable<Void>()
			{
				@Override
				public Void call() throws IOException
				{
					synchronized (AsynchronousByteCharChannel.this)
					{
						if (writeState == State.RUNNING)
							writeState = State.INTERRUPTED;
					}
					close();
					return null;
				}
			}, timeout, unit);
		}
		DoneWriting<Integer, Void> doneWriting = new DoneWriting(handler, timeoutTimer);
		EncodingReadableByteChannel encoder = new EncodingReadableByteChannel(charset);
		encoder.append(source);
		this.bytesToWrite = ByteBuffer.allocate(encoder.maxReadCount());
		try
		{
			encoder.read(bytesToWrite);
		}
		catch (IOException e)
		{
			handler.failed(e, attachment);
			return;
		}
		WriteCharacters writeCharacters = new WriteCharacters(source, doneWriting);
		channel.write(bytesToWrite, attachment, writeCharacters);
	}

	@Override
	public synchronized Future<Integer> write(CharBuffer source, boolean endOfInput)
		throws WritePendingException, ShutdownChannelGroupException
	{
		synchronized (this)
		{
			if (writeState != State.INITIAL)
				throw new WritePendingException();
			if (source.remaining() <= 0)
				return Futures.immediateFuture(0);
			writeState = State.RUNNING;
		}
		SettableFuture<Integer> result = SettableFuture.create();
		FutureCompletionHandler<Integer> futureToHandler = new FutureCompletionHandler<>(result);
		Future<Void> timeoutTimer = Futures.immediateFuture(null);
		DoneWriting<Integer, Void> doneWriting = new DoneWriting(futureToHandler, timeoutTimer);

		EncodingReadableByteChannel encoder = new EncodingReadableByteChannel(charset);
		encoder.append(source);
		this.bytesToWrite = ByteBuffer.allocate(encoder.maxReadCount());
		try
		{
			encoder.read(bytesToWrite);
		}
		catch (IOException e)
		{
			futureToHandler.failed(e, null);
			return result;
		}
		WriteCharacters writeCharacters = new WriteCharacters(source, doneWriting);
		channel.write(bytesToWrite, null, writeCharacters);
		return result;
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

	@Override
	public String toString()
	{
		return getClass().getName() + "[" + channel + "]";
	}

	/**
	 * The operation state.
	 */
	public static enum State
	{
		/**
		 * The operation hasn't started.
		 */
		INITIAL,
		/**
		 * The operation is running.
		 */
		RUNNING,
		/**
		 * The operation was interrupted.
		 */
		INTERRUPTED
	}

	/**
	 * Decodes bytesRead into charactersRead.
	 *
	 * @param <A> the type of the object attached to the I/O operation
	 * @author Gili Tzabari
	 */
	private class ByteDecoder<A> implements CompletionHandler<Integer, A>
	{
		private CompletionHandler<Boolean, A> handler;

		/**
		 * Creates a new ByteDecoder.
		 *
		 * @param handler a handler for consuming the result of an asynchronous I/O operation
		 */
		public ByteDecoder(CompletionHandler<Boolean, A> handler)
		{
			this.handler = handler;
		}

		@Override
		public void completed(Integer numBytesRead, A attachment)
		{
			assert (numBytesRead != 0);
			if (numBytesRead == -1)
			{
				handler.completed(true, attachment);
				return;
			}
			bytesRead.flip();
			assert (bytesRead.remaining() == numBytesRead): "remaining: " + bytesRead.remaining()
																											+ ", numBytesRead: " + numBytesRead;
			try
			{
				readDecoder.write(bytesRead);
				bytesRead.compact();
			}
			catch (IOException e)
			{
				handler.failed(e, attachment);
				return;
			}
			StringBuilder decodedCharacters = readDecoder.toStringBuilder();
			charactersRead.append(decodedCharacters);
			int numCharactersRead = decodedCharacters.length();
			decodedCharacters.delete(0, numCharactersRead);
			handler.completed(numCharactersRead == 0, attachment);
		}

		@Override
		public void failed(Throwable t, A attachment)
		{
			handler.failed(t, attachment);
		}
	}

	/**
	 * Attempts to read one character.
	 *
	 * @param <A> the type of the object attached to the I/O operation
	 * @author Gili Tzabari
	 */
	private class ReadCharacters<A> implements CompletionHandler<Boolean, A>
	{
		private final CharBuffer target;
		private final CompletionHandler<Integer, A> handler;

		/**
		 * Creates a new ReadCharacters.
		 *
		 * @param target the buffer into which characters are to be transferred
		 * @param handler a handler for consuming the result of an asynchronous I/O operation.
		 *   The value denotes the number of characters read by the operation. The current object's
		 *   attachment is passed to the handler.
		 * @throws NullPointerException if target or handler are null
		 */
		public ReadCharacters(CharBuffer target, CompletionHandler<Integer, A> handler)
		{
			Preconditions.checkNotNull(target, "target may not be null");
			Preconditions.checkNotNull(handler, "handler may not be null");

			this.target = target;
			this.handler = handler;
		}

		@Override
		public void completed(Boolean endOfStream, A attachment)
		{
			if (charactersRead.length() == 0 && !endOfStream)
			{
				// We don't have any buffered characters and there is more data in the stream, so keep on
				// reading.
				ByteDecoder<Void> byteDecoder = new ByteDecoder(this);
				channel.read(bytesRead, null, byteDecoder);
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
			handler.failed(t, attachment);
		}
	}

	/**
	 * Attempts to read one line of text.
	 *
	 * @param <A> the type of the object attached to the I/O operation
	 * @author Gili Tzabari
	 */
	private class ReadLine<A> implements CompletionHandler<Boolean, A>
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
		public ReadLine(A attachment, CompletionHandler<String, A> handler)
		{
			this.handler = handler;
		}

		@Override
		public void completed(Boolean endOfStream, A attachment)
		{
			while (true)
			{
				Matcher matcher = delimiters.matcher(charactersRead);
				if (!matcher.find())
				{
					target.append(charactersRead);
					charactersRead.setLength(0);
					if (!endOfStream)
					{
						// delimiters not found and there is more data in the stream, so keep on reading.
						ByteDecoder<Void> byteDecoder = new ByteDecoder(this);
						channel.read(bytesRead, null, byteDecoder);
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
						consumeNewline = true;
						break;
					}
					case "\n":
					{
						if (consumeNewline && matcher.start() == 0)
						{
							// Handle the case where \r\n is split across separate reads
							consumeNewline = false;
							continue;
						}
						break;
					}
					default:
						throw new AssertionError("Unexpected delimiter: '" + toHex(match) + "'");
				}
				break;
			}
			handler.completed(target.toString(), attachment);
		}

		@Override
		public void failed(Throwable t, A attachment)
		{
			handler.failed(t, attachment);
		}
	}

	/**
	 * Notified when a read operation completes.
	 *
	 * @param <V> the result type of the I/O operation
	 * @param <A> the type of the object attached to the I/O operation
	 * @author Gili Tzabari
	 */
	private class DoneReading<V, A> implements CompletionHandler<V, A>
	{
		private final CompletionHandler<V, A> delegate;
		private final Future<?> timer;
		private boolean done;

		/**
		 * Creates a new DoneReading.
		 *
		 * @param delegate the handler to delegate to. The current object's attachment is passed to the
		 *   delegate.
		 * @param timer the timeout timer to cancel when the operation completes
		 * through to the delegate.
		 */
		public DoneReading(CompletionHandler<V, A> delegate, Future<?> timer)
		{
			Preconditions.checkNotNull(delegate, "delegate may not be null");
			Preconditions.checkNotNull(timer, "timer may not be null");

			this.delegate = delegate;
			this.timer = timer;
		}

		@Override
		public void completed(V value, A attachment)
		{
			done = true;
			timer.cancel(false);
			boolean interrupted;
			synchronized (AsynchronousByteCharChannel.this)
			{
				switch (readState)
				{
					case INTERRUPTED:
					{
						readState = State.INITIAL;
						interrupted = true;
						break;
					}
					case RUNNING:
					{
						readState = State.INITIAL;
						interrupted = false;
						break;
					}
					default:
						throw new AssertionError("Unexpected state: " + readState);
				}
			}
			if (interrupted)
				delegate.failed(new InterruptedByTimeoutException(), attachment);
			else
				delegate.completed(value, attachment);
		}

		@Override
		public void failed(Throwable t, A attachment)
		{
			done = true;
			timer.cancel(false);
			boolean interrupted;
			synchronized (AsynchronousByteCharChannel.this)
			{
				interrupted = readState == State.INTERRUPTED;
				readState = State.INITIAL;
			}
			if (interrupted)
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
	 * Attempts to write characters.
	 *
	 * @param <A> the type of the object attached to the I/O operation
	 * @author Gili Tzabari
	 */
	private class WriteCharacters<A> implements CompletionHandler<Integer, A>
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
		public WriteCharacters(CharBuffer source, CompletionHandler<Integer, A> handler)
		{
			Preconditions.checkNotNull(source, "source may not be null");
			Preconditions.checkNotNull(handler, "handler may not be null");

			this.source = source;
			this.handler = handler;
			this.initialPosition = source.position();
		}

		@Override
		public void completed(Integer numBytesWritten, A attachment)
		{
			assert (numBytesWritten != 0);
			ByteBuffer outstandingBytes = updateSourcePosition(numBytesWritten);
			if (outstandingBytes.hasRemaining())
			{
				channel.write(outstandingBytes, attachment, this);
				return;
			}
			handler.completed(source.position() - initialPosition, attachment);
		}

		@Override
		public void failed(Throwable t, A attachment)
		{
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
			ByteBuffer actualBytesWritten = bytesToWrite.duplicate();
			actualBytesWritten.limit(numBytesWritten);
			try
			{
				try
				{
					decoder.write(actualBytesWritten);
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
				source.position(wholeCharsWritten.limit());

				// Calculate how many bytes make up the whole characters written
				EncodingReadableByteChannel encoder = new EncodingReadableByteChannel(charset);
				encoder.append(wholeCharsWritten);
				bytesToWrite.position(0);
				encoder.read(bytesToWrite);
				int wholeBytesWritten = bytesToWrite.position();
				int malformedBytesWritten = numBytesWritten - wholeBytesWritten;

				// Encode the partially-written character
				encoder.append(String.valueOf(source.get()));
				ByteBuffer result = ByteBuffer.allocate(encoder.maxReadCount());
				encoder.read(result);
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
	}

	/**
	 * Notified when a write operation completes.
	 *
	 * @param <V> the result type of the I/O operation
	 * @param <A> the type of the object attached to the I/O operation
	 * @author Gili Tzabari
	 */
	private class DoneWriting<V, A> implements CompletionHandler<V, A>
	{
		private final CompletionHandler<V, A> delegate;
		private final Future<?> timer;
		private boolean done;

		/**
		 * Creates a new DoneWriting.
		 *
		 * @param delegate the handler to delegate to. The current object's attachment is passed to the
		 *   delegate.
		 * @param timer the timeout timer to cancel when the operation completes
		 * through to the delegate.
		 */
		public DoneWriting(CompletionHandler<V, A> delegate, Future<?> timer)
		{
			Preconditions.checkNotNull(delegate, "delegate may not be null");
			Preconditions.checkNotNull(timer, "timer may not be null");

			this.delegate = delegate;
			this.timer = timer;
		}

		@Override
		public void completed(V value, A attachment)
		{
			done = true;
			timer.cancel(false);
			boolean interrupted;
			synchronized (AsynchronousByteCharChannel.this)
			{
				switch (writeState)
				{
					case INTERRUPTED:
					{
						writeState = State.INITIAL;
						interrupted = true;
						break;
					}
					case RUNNING:
					{
						writeState = State.INITIAL;
						interrupted = false;
						break;
					}
					default:
						throw new AssertionError("Unexpected state: " + readState);
				}
			}
			if (interrupted)
				delegate.failed(new InterruptedByTimeoutException(), attachment);
			else
				delegate.completed(value, attachment);
		}

		@Override
		public void failed(Throwable t, A attachment)
		{
			done = true;
			timer.cancel(false);
			boolean interrupted;
			synchronized (AsynchronousByteCharChannel.this)
			{
				interrupted = writeState == State.INTERRUPTED;
				writeState = State.INITIAL;
			}
			if (interrupted)
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
	 * Returns a String's bytes in hexidecimal format.
	 * 
	 * @param text the String to convert to hex
	 * @return the String bytes in hexidecimal format
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
