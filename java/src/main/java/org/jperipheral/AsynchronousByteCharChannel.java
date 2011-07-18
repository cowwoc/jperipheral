package org.jperipheral;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.primitives.Bytes;
import com.google.common.util.concurrent.SettableFuture;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.ReadPendingException;
import java.nio.channels.ShutdownChannelGroupException;
import java.nio.channels.WritePendingException;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.util.Queue;
import java.util.concurrent.Future;
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
	/**
	 * The underlying AsynchronousByteChannel.
	 */
	private final AsynchronousByteChannel channel;
	/**
	 * The character encoding.
	 */
	private final Charset charset;
	// ---- START READ OPERATIONS ----
	private final Queue<Runnable> readQueue = Lists.newLinkedList();
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
	 * Searches for line delimiters.
	 */
	private final Pattern delimiters = Pattern.compile("\\r|\\n");
	/**
	 * Indicates if the subsequent newline character should be disregarded by readLine().
	 */
	private boolean consumeNewline;
	// ---- START WRITE OPERATIONS ----		
	private final Queue<Runnable> writeQueue = Lists.newLinkedList();
	/**
	 * The bytes to write out.
	 */
	private final ByteBuffer bytesToWrite = ByteBuffer.allocate(128);
	private boolean errorState;
	// ---- END WRITE OPERATIONS ----		

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
	public synchronized <A> void read(final CharBuffer target, final A attachment,
																		final CompletionHandler<Integer, ? super A> handler)
		throws IllegalArgumentException, ReadPendingException, ShutdownChannelGroupException
	{
		if (target.isReadOnly())
			throw new IllegalArgumentException("target may not be read-only");
		addOperation(new Runnable()
		{
			@Override
			public void run()
			{
//				if (target.remaining() <= 0)
//				{
//					handler.completed(0, attachment);
//					return;
//				}
				OperationDone<Integer, ? super A> doneReading = new OperationDone<>(handler, readQueue);
				ReadCharacters<? super A> readCharacters = new ReadCharacters<>(target, doneReading);
				consumeNewline = false;

				// Read zero bytes to indicate that the read buffer should be checked before reading from
				// the underlying channel.
				bytesRead.limit(bytesRead.position());
				ByteDecoder<? super A> byteDecoder = new ByteDecoder<>(readCharacters);
				channel.read(bytesRead, attachment, byteDecoder);
			}
		}, readQueue);
	}

	/**
	 * Adds a read operation to the queue. If there is no ongoing read, invoke the first read
	 * operation.
	 * 
	 * @param operation the operation to add
	 * @param queue the operation queue
	 */
	private void addOperation(Runnable operation, Queue<Runnable> queue)
	{
		// Rules that govern the queue.
		//
		// 1. Elements are only removed once the operation completes.
		// 2. Therefore, if the queue is non-empty an operation is in progress
		Runnable workload;
		synchronized (readQueue)
		{
			queue.add(operation);
			if (queue.size() == 1)
			{
				// No operation in progress
				workload = operation;
			}
			else
				workload = null;
		}
		if (workload != null)
			workload.run();
	}

	/**
	 * Invoke the next read operation.
	 * 
	 * @param queue the operation queue
	 */
	private void nextOperation(Queue<Runnable> queue)
	{
		Runnable workload;
		synchronized (queue)
		{
			// Remove the operation that just completed
			queue.remove();

			workload = queue.peek();
		}
		if (workload != null)
			workload.run();
	}

	@Override
	public Future<Integer> read(final CharBuffer target)
		throws IllegalArgumentException, ReadPendingException, ShutdownChannelGroupException
	{
		final SettableFuture<Integer> result = SettableFuture.create();
		addOperation(new Runnable()
		{
			@Override
			public void run()
			{
				if (target.remaining() <= 0)
				{
					result.set(0);
					nextOperation(readQueue);
					return;
				}
				FutureCompletionHandler<Integer> futureToHandler = new FutureCompletionHandler<>(result);
				OperationDone<Integer, Void> doneReading = new OperationDone<>(futureToHandler, readQueue);
				ReadCharacters<Void> readCharacters = new ReadCharacters<>(target, doneReading);
				consumeNewline = false;

				// Read zero bytes to indicate that the read buffer should be checked before reading from
				// the underlying channel.
				bytesRead.limit(bytesRead.position());
				readCharacters.completed(false, null);
				// ReadCharacters.completed() invokes channel.read() if it needs more bytes
			}
		}, readQueue);
		return result;
	}

	@Override
	public <A> void readLine(final A attachment, final CompletionHandler<String, ? super A> handler)
		throws IllegalArgumentException, ReadPendingException, ShutdownChannelGroupException
	{
		addOperation(new Runnable()
		{
			@Override
			public void run()
			{
				OperationDone<String, ? super A> doneReading = new OperationDone<>(handler, readQueue);
				ReadLine<? super A> readLine = new ReadLine<>(attachment, doneReading);

				// Read zero bytes to indicate that the read buffer should be checked before reading from
				// the underlying channel.
				bytesRead.limit(bytesRead.position());
				ByteDecoder<? super A> byteDecoder = new ByteDecoder<>(readLine);
				channel.read(bytesRead, attachment, byteDecoder);
			}
		}, readQueue);
	}

	@Override
	public Future<String> readLine()
		throws IllegalArgumentException, ReadPendingException, ShutdownChannelGroupException
	{
		final SettableFuture<String> result = SettableFuture.create();
		addOperation(new Runnable()
		{
			@Override
			public void run()
			{
				FutureCompletionHandler<String> futureToHandler = new FutureCompletionHandler<>(result);
				OperationDone<String, Void> doneReading = new OperationDone<>(futureToHandler, readQueue);
				ReadLine<Void> readLine = new ReadLine<>(null, doneReading);
				// Read zero bytes to indicate that the read buffer should be checked before reading from
				// the underlying channel.
				bytesRead.limit(bytesRead.position());
				readLine.completed(false, null);
				// ReadLine.completed() invokes channel.read() if it needs more bytes
			}
		}, readQueue);
		return result;
	}

	@Override
	public synchronized <A> void write(final CharBuffer source,
																		 final A attachment,
																		 final CompletionHandler<Integer, ? super A> handler)
		throws WritePendingException, ShutdownChannelGroupException
	{
		addOperation(new Runnable()
		{
			@Override
			public void run()
			{
//				if (source.remaining() <= 0)
//				{
//					handler.completed(0, attachment);
//					nextOperation(writeQueue);
//					return;
//				}
				OperationDone<Integer, ? super A> doneWriting = new OperationDone<>(handler, writeQueue);
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
				WriteCharacters<? super A> writeCharacters = new WriteCharacters<>(source, doneWriting);
				channel.write(bytesToWrite, attachment, writeCharacters);
			}
		}, writeQueue);
	}

	@Override
	public synchronized Future<Integer> write(final CharBuffer source)
		throws WritePendingException, ShutdownChannelGroupException
	{
		final SettableFuture<Integer> result = SettableFuture.create();
		addOperation(new Runnable()
		{
			@Override
			public void run()
			{
//				if (source.remaining() <= 0)
//				{
//					nextOperation(writeQueue);
//					result.set(0);
//					return;
//				}
				FutureCompletionHandler<Integer> futureToHandler = new FutureCompletionHandler<>(result);
				OperationDone<Integer, Void> doneWriting = new OperationDone<>(futureToHandler, writeQueue);

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
					doneWriting.failed(e, null);
					return;
				}
				WriteCharacters<Void> writeCharacters = new WriteCharacters<>(source, doneWriting);
				channel.write(bytesToWrite, null, writeCharacters);
			}
		}, writeQueue);
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
				do
				{
					readDecoder.write(bytesRead);
				}
				while (bytesRead.hasRemaining());
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
			handler.completed(false, attachment);
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
			if (charactersRead.length() == 0)
			{
				if (endOfStream)
					handler.completed(-1, attachment);
				else
				{
					// We don't have any buffered characters and there is more data in the stream, so keep on
					// reading.
					ByteDecoder<A> byteDecoder = new ByteDecoder<>(this);

					assert (bytesRead.position() == 0): bytesRead;
					// The first read sets limit = position
					bytesRead.limit(bytesRead.capacity());
					channel.read(bytesRead, attachment, byteDecoder);
				}
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
						ByteDecoder<A> byteDecoder = new ByteDecoder<>(this);

						assert (bytesRead.position() == 0): bytesRead;
						// The first read sets limit = position
						bytesRead.limit(bytesRead.capacity());
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
			String result = target.toString();
			if (result.isEmpty())
			{
				assert (endOfStream): endOfStream;
				result = null;
			}
			handler.completed(result, attachment);
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
	private class OperationDone<V, A> implements CompletionHandler<V, A>
	{
		private final Queue<Runnable> queue;
		private final CompletionHandler<V, A> delegate;
		private boolean done;

		/**
		 * Creates a new DoneReading.
		 *
		 * @param delegate the handler to delegate to. The current object's attachment is passed to the
		 *   delegate.
		 * through to the delegate.
		 * @param queue the queue to process once the operation completes
		 */
		public OperationDone(CompletionHandler<V, A> delegate, Queue<Runnable> queue)
		{
			Preconditions.checkNotNull(delegate, "delegate may not be null");
			Preconditions.checkNotNull(queue, "queue may not be null");

			this.delegate = delegate;
			this.queue = queue;
		}

		@Override
		public void completed(V value, A attachment)
		{
			done = true;
			try
			{
				delegate.completed(value, attachment);
			}
			catch (RuntimeException e)
			{
				log.warn("", e);
			}
			nextOperation(queue);
		}

		@Override
		public void failed(Throwable t, A attachment)
		{
			done = true;
			try
			{
				delegate.failed(t, attachment);
			}
			catch (RuntimeException e)
			{
				log.warn("", e);
			}
			nextOperation(queue);
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
			assert (numBytesWritten != 0 || !source.hasRemaining()): "numBytesWritten: " + numBytesWritten
																															 + ", remaining: "
																															 + source.remaining();
			ByteBuffer outstandingBytes = updateSourcePosition(numBytesWritten);
			if (outstandingBytes.hasRemaining())
			{
				int bytesWritten = bytesToWrite.position();
				bytesToWrite.put(outstandingBytes);
				bytesToWrite.flip();
				bytesToWrite.position(bytesWritten);
				channel.write(bytesToWrite, attachment, this);
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
