package org.jperipheral;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jperipheral.nio.channels.AsynchronousByteChannel;
import org.jperipheral.nio.channels.CompletionHandler;
import org.jperipheral.nio.channels.ReadPendingException;
import org.jperipheral.nio.channels.ShutdownChannelGroupException;
import org.jperipheral.nio.channels.WritePendingException;

/**
 * An adapter that maps an AsynchronousCharChannel on top of an existing AsynchronousByteChannel.
 *
 * @author Gili Tzabari
 */
public class AsynchronousByteCharChannel implements AsynchronousCharChannel
{
	/**
	 * The underlying AsynchronousByteChannel.
	 */
	private final AsynchronousByteChannel channel;
	/**
	 * The underlying AsynchronousByteChannelTimeouts or null if timeouts are not supported.
	 */
	private final AsynchronousByteChannelTimeouts channelTimeouts;
	/**
	 * The character encoding.
	 */
	private final Charset charset;
	/**
	 * Used to encode characters being written.
	 */
	private final CharsetEncoder encoder;
	/**
	 * Used to decode characters being read.
	 */
	private final CharsetDecoder decoder;
	/**
	 * Searches for line delimiters.
	 */
	private final Pattern delimiters = Pattern.compile("\\r|\\n");
	/**
	 * Indicates if there is an ongoing read operation.
	 */
	private boolean readPending;
	/**
	 * Indicates if there is an ongoing write operation.
	 */
	private boolean writePending;
	/**
	 * Indicates if the subsequent newline character should be disregarded by readLine().
	 */
	private boolean consumeNewline;
	/**
	 * The buffer containing characters decoded from <code>readBytes</code>.
	 */
	private StringBuilder readString = new StringBuilder();
	/**
	 * The buffer that the underlying AsynchronousByteChannel writes into.
	 */
	private ByteBuffer readBytes = ByteBuffer.allocate(1024);

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
		if (channel instanceof AsynchronousByteChannelTimeouts)
			this.channelTimeouts = (AsynchronousByteChannelTimeouts) channel;
		else
			this.channelTimeouts = null;
		this.charset = charset;
		this.encoder = charset.newEncoder();
		this.decoder = charset.newDecoder();
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
		if (target.isReadOnly())
			throw new IllegalArgumentException("target may not be read-only");
		if (readPending)
			throw new ReadPendingException();
		readPending = true;
		if (target.remaining() <= 0)
		{
			readPending = false;
			handler.completed(0, attachment);
			return;
		}
		try
		{
			read(new CharBufferWriter(target), attachment, handler);
		}
		catch (ReadPendingException e)
		{
			readPending = false;
			throw new AssertionError(e);
		}
		catch (ShutdownChannelGroupException e)
		{
			readPending = false;
			throw e;
		}
		catch (RuntimeException e)
		{
			readPending = false;
			throw e;
		}
	}

	@Override
	public synchronized <A> void read(CharBuffer target,
																		long timeout,
																		TimeUnit unit,
																		A attachment,
																		CompletionHandler<Integer, ? super A> handler)
		throws IllegalArgumentException, ReadPendingException, ShutdownChannelGroupException
	{
		if (target.isReadOnly())
			throw new IllegalArgumentException("target may not be read-only");
		if (readPending)
			throw new ReadPendingException();
		readPending = true;
		if (target.remaining() <= 0)
		{
			readPending = false;
			handler.completed(0, attachment);
			return;
		}
		try
		{
			read(new CharBufferWriter(target), timeout, unit, attachment, handler);
		}
		catch (ReadPendingException e)
		{
			readPending = false;
			throw new AssertionError(e);
		}
		catch (ShutdownChannelGroupException e)
		{
			readPending = false;
			throw e;
		}
		catch (UnsupportedOperationException e)
		{
			readPending = false;
			throw e;
		}
		catch (RuntimeException e)
		{
			readPending = false;
			throw e;
		}
	}

	/**
	 * @see #read(CharBuffer,Object,CompletionHandler)
	 */
	private synchronized <A> void read(CharSequenceWriter target, A attachment,
																		 CompletionHandler<Integer, ? super A> handler)
		throws IllegalArgumentException, ReadPendingException, ShutdownChannelGroupException
	{
		CompletionHandler<Integer, ByteBuffer> readHandler = getReadHandler(target, attachment, handler);
		if (readHandler == null)
		{
			readPending = false;
			return;
		}
		channel.read(readBytes, readBytes, readHandler);
	}

	/**
	 * @see #read(CharBuffer,long,TimeUnit,Object,CompletionHandler)
	 */
	private synchronized <A> void read(CharSequenceWriter target, long timeout, TimeUnit unit,
																		 A attachment, CompletionHandler<Integer, ? super A> handler)
		throws IllegalArgumentException, ReadPendingException, ShutdownChannelGroupException,
					 UnsupportedOperationException
	{
		if (channelTimeouts == null)
			throw new UnsupportedOperationException();
		CompletionHandler<Integer, ByteBuffer> readHandler = getReadHandler(target, attachment, handler);
		if (readHandler == null)
		{
			readPending = false;
			return;
		}
		channelTimeouts.read(readBytes, timeout, unit, readBytes, readHandler);
	}

	/**
	 * Returns the CompletionHandler to use with the read operation.
	 *
	 * @param   <A>
	 *          The attachment type
	 * @param   target
	 *          The buffer into which characters are to be transferred
	 * @param   attachment
	 *          The object to attach to the I/O operation; can be {@code null}
	 * @param   handler
	 *          The completion handler
	 * @return  null if the operation completed without needing to read from the underlying channel
	 */
	private <A> CompletionHandler<Integer, ByteBuffer> getReadHandler(CharSequenceWriter target,
																																		A attachment,
																																		CompletionHandler<Integer, ? super A> handler)
	{
		if (readString.length() > 0)
		{
			// Try satisfying the request using the buffer
			try
			{
				int charactersTransferred = target.write(readString);
				if (charactersTransferred > 0)
				{
					readString.delete(0, charactersTransferred);
					handler.completed(charactersTransferred, attachment);
				}
			}
			catch (IOException e)
			{
				handler.failed(e, attachment);
			}
			return null;
		}
		readBytes.clear();
		return new ReadHandler<A>(attachment, handler, target);
	}

	@Override
	public Future<Integer> read(CharBuffer target)
		throws IllegalArgumentException, ReadPendingException, ShutdownChannelGroupException
	{
		if (target.isReadOnly())
			throw new IllegalArgumentException("target may not be read-only");
		if (target.remaining() <= 0)
			return new CompletedFuture(0);
		return read(new CharBufferWriter(target));
	}

	/**
	 * @see #read(CharBuffer)
	 */
	private synchronized Future<Integer> read(CharSequenceWriter target)
		throws IllegalArgumentException, ReadPendingException, ShutdownChannelGroupException
	{
		if (readPending)
			throw new ReadPendingException();
		readPending = true;
		if (readString.length() > 0)
		{
			// Try satisfying the request using the buffer
			try
			{
				int charactersTransferred = target.write(readString);
				if (charactersTransferred > 0)
				{
					readString.delete(0, charactersTransferred);
					readPending = false;
					return new CompletedFuture(charactersTransferred);
				}
			}
			catch (IOException e)
			{
				return new CompletedFuture(e);
			}
		}
		readBytes.clear();
		try
		{
			return new ReadFuture(channel.read(readBytes), readBytes, target);
		}
		catch (RuntimeException e)
		{
			readPending = false;
			throw e;
		}
	}

	@Override
	public <A> void readLine(A attachment, CompletionHandler<String, ? super A> handler)
		throws IllegalArgumentException, ReadPendingException, ShutdownChannelGroupException
	{
		StringBuilder target = new StringBuilder();
		read(new LineWriter(target), attachment, new ReadLineHandler<A>(handler, target));
	}

	@Override
	public Future<String> readLine()
		throws IllegalArgumentException, ReadPendingException, ShutdownChannelGroupException
	{
		StringBuilder target = new StringBuilder();
		return new ReadLineFuture(read(new LineWriter(target)), target);
	}

	@Override
	public synchronized <A> void write(CharBuffer source,
																		 A attachment, CompletionHandler<Integer, ? super A> handler,
																		 boolean endOfInput)
		throws WritePendingException, ShutdownChannelGroupException
	{
		if (writePending)
			throw new WritePendingException();
		writePending = true;
		ByteBuffer sourceBytes = getBytes(source, attachment, handler, endOfInput);
		if (sourceBytes == null)
		{
			writePending = false;
			return;
		}
		CompletionHandler<Integer, ByteBuffer> writeHandler = new WriteHandler<A>(attachment, handler,
			source,
			endOfInput);
		try
		{
			channel.write(sourceBytes, sourceBytes, writeHandler);
		}
		catch (RuntimeException e)
		{
			writePending = false;
			throw e;
		}
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
		if (channelTimeouts == null)
			throw new UnsupportedOperationException();
		if (writePending)
			throw new WritePendingException();
		writePending = true;
		ByteBuffer sourceBytes = getBytes(source, attachment, handler, endOfInput);
		if (sourceBytes == null)
		{
			writePending = false;
			return;
		}
		CompletionHandler<Integer, ByteBuffer> writeHandler = new WriteHandler<A>(attachment, handler,
			source,
			endOfInput);
		try
		{
			channelTimeouts.write(sourceBytes, timeout, unit, sourceBytes, writeHandler);
		}
		catch (WritePendingException e)
		{
			writePending = false;
			throw new AssertionError(e);
		}
		catch (ShutdownChannelGroupException e)
		{
			writePending = false;
			throw e;
		}
		catch (RuntimeException e)
		{
			writePending = false;
			throw e;
		}
	}

	@Override
	public synchronized Future<Integer> write(CharBuffer source, boolean endOfInput)
		throws WritePendingException, ShutdownChannelGroupException
	{
		if (writePending)
			throw new WritePendingException();
		writePending = true;
		PollableCompletionHandler handler = new PollableCompletionHandler();
		ByteBuffer sourceBytes = getBytes(source, null, handler, endOfInput);
		if (sourceBytes == null)
		{
			writePending = false;
			return new CompletedFuture(handler.throwable);
		}
		try
		{
			return new WriteFuture(channel.write(sourceBytes), source, sourceBytes, endOfInput);
		}
		catch (RuntimeException e)
		{
			writePending = false;
			throw e;
		}
	}

	/**
	 * Returns the buffer from which bytes are to be retrieved.
	 *
	 * @param <A>
	 *        The attachment type
	 * @param source
	 *        The buffer from which characters are to be retrieved
	 * @param attachment
	 *        The object to attach to the I/O operation; can be {@code null}
	 * @param handler
	 *        The completion handler object
	 * @param endOfInput
	 *        <code>true</code> if, and only if, the invoker can provide no additional input characters beyond
	 *        those in the given buffer.
	 *
	 * @throws WritePendingException
	 *         If the channel does not allow more than one write to be outstanding
	 *         and a previous write has not completed
	 * @throws ShutdownChannelGroupException
	 *         If the channel is associated with a {@link AsynchronousChannelGroup
	 *         group} that has terminated
	 * @return The buffer from which bytes are to be retrieved, or null if an error occured and was delegated
	 *         to the <code>CompletionHandler</code>
	 */
	private <A> ByteBuffer getBytes(CharBuffer source,
																	A attachment, CompletionHandler<Integer, ? super A> handler,
																	boolean endOfInput)
	{
		ByteBuffer sourceBytes = ByteBuffer.allocate((int) Math.ceil(encoder.maxBytesPerChar()
																																 * source.remaining()));
		// duplicate source to avoid modifying its position
		CharBuffer sourceCopy = source.duplicate();
		CoderResult encodingResult = encoder.encode(sourceCopy, sourceBytes, endOfInput);
		if (encodingResult.isError())
		{
			delegateError(encodingResult, attachment, handler);
			return null;
		}
		if (endOfInput)
		{
			encodingResult = encoder.flush(sourceBytes);
			if (encodingResult.isError())
			{
				delegateError(encodingResult, attachment, handler);
				return null;
			}
		}
		sourceBytes.flip();
		return sourceBytes;
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

	/**
	 * A Future for an operation that has already completed.
	 *
	 * @author Gili Tzabari
	 */
	private static class CompletedFuture implements Future<Integer>
	{
		private final int charactersTransfered;
		private final Throwable throwable;

		/**
		 * Creates a new CompletedFuture.
		 *
		 * @param charactersTransfered
		 *        The number of characters transfered
		 */
		public CompletedFuture(int charactersTransfered)
		{
			this.charactersTransfered = charactersTransfered;
			this.throwable = null;
		}

		/**
		 * Creates a new CompletedFuture.
		 *
		 * @param throwable the Throwable thrown by the operation
		 */
		public CompletedFuture(Throwable throwable)
		{
			this.throwable = throwable;
			this.charactersTransfered = 0;
		}

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
			if (throwable != null)
				throw new ExecutionException(throwable);
			return charactersTransfered;
		}

		@Override
		public Integer get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException,
																													 TimeoutException
		{
			if (throwable != null)
				throw new ExecutionException(throwable);
			return charactersTransfered;
		}
	}

	/**
	 * Invoked when a byte reading operation completes.
	 *
	 * @param <A>
	 *        The attachment type
	 * @param bytesRead
	 *        The buffer from which bytes are to be retrieved
	 * @param endOfInput
	 *        true if the end of stream has been reached
	 * @param target
	 *        The buffer into which characters are to be transferred
	 * @param attachment
	 *        The object to attach to the I/O operation; can be null
	 * @param handler
	 *        The CompletionHandler to delegate to
	 * @return true if the read operation completed
	 */
	private <A> boolean onBytesRead(ByteBuffer bytesRead, boolean endOfInput,
																	CharSequenceWriter target,
																	A attachment, CompletionHandler<Integer, ? super A> handler)
	{
		try
		{
			decodeBytesForRead(decoder, bytesRead, endOfInput, readString);
			int charactersTransferred = target.write(readString);
			if (endOfInput && charactersTransferred == 0)
				charactersTransferred = -1;
			if (charactersTransferred != 0)
			{
				if (charactersTransferred > 0)
					readString.delete(0, charactersTransferred);
				handler.completed(charactersTransferred, attachment);
				return true;
			}
		}
		catch (IOException e)
		{
			handler.failed(e, attachment);
			return true;
		}
		return false;
	}

	/**
	 * Decodes a ByteBuffer.
	 *
	 * @param decoder
	 *        The Charset decoder
	 * @param source
	 *        The ByteBuffer to decode
	 * @param endOfInput
	 *        true if the end of the stream has been reached
	 * @param target
	 *        The buffer into which characters are to be transferred
	 * @throws CharacterCodingException
	 *         If a decoding error occured
	 */
	private static void decodeBytesForRead(CharsetDecoder decoder, ByteBuffer source,
																				 boolean endOfInput,
																				 StringBuilder target)
		throws CharacterCodingException
	{
		final CharBuffer charBuffer = CharBuffer.allocate((int) Math.ceil(decoder.maxCharsPerByte() * source.
			remaining()));
		CoderResult decodingResult = decoder.decode(source, charBuffer, endOfInput);
		if (decodingResult.isError())
			decodingResult.throwException();
		charBuffer.flip();
		target.append(charBuffer.toString());
		if (endOfInput)
			flushDecoderForRead(decoder, charBuffer, target);
	}

	/**
	 * Flush the CharsetDecoder.
	 *
	 * @param decoder
	 *        The Charset decoder
	 * @param temp
	 *        A temporary buffer
	 * @param target
	 *        The buffer into which characters are to be transferred
	 */
	private static void flushDecoderForRead(CharsetDecoder decoder, CharBuffer temp,
																					StringBuilder target)
	{
		CoderResult decodingResult;
		do
		{
			temp.clear();
			decodingResult = decoder.flush(temp);
			temp.flip();
			target.append(temp.toString());
		}
		while (!decodingResult.isUnderflow());
	}

	@Override
	public String toString()
	{
		return getClass().getName() + "[" + channel + "]";
	}

	/**
	 * A handler for consuming the result of an asynchronous character reading operation.
	 *
	 * @author Gili Tzabari
	 */
	private class ReadHandler<A> implements CompletionHandler<Integer, ByteBuffer>
	{
		private final CompletionHandler<Integer, ? super A> handler;
		private final A attachment;
		private final CharSequenceWriter target;

		/**
		 * Creates a new ReadHandler.
		 *
		 * @param attachment
		 *        The object to attach to the I/O operation; can be null
		 * @param handler
		 *        The CompletionHandler to delegate to
		 * @param target
		 *        The buffer into which characters are to be transferred
		 */
		public ReadHandler(A attachment, CompletionHandler<Integer, ? super A> handler,
											 CharSequenceWriter target)
		{
			this.handler = handler;
			this.attachment = attachment;
			this.target = target;
		}

		@Override
		public void completed(Integer numBytesRead, ByteBuffer bytesRead)
		{
			if (onBytesRead(bytesRead, numBytesRead == -1, target, attachment, handler))
			{
				synchronized (AsynchronousByteCharChannel.this)
				{
					readPending = false;
				}
				return;
			}

			// ask for more bytes
			bytesRead.compact();
			channel.read(bytesRead, bytesRead, this);
		}

		@Override
		public void failed(Throwable throwable, ByteBuffer source)
		{
			handler.failed(throwable, attachment);
			synchronized (AsynchronousByteCharChannel.this)
			{
				readPending = false;
			}
		}
	}

	/**
	 * A CompletionHandler that retains its results for future examination.
	 *
	 * @author Gili Tzabari
	 */
	private static class PollableCompletionHandler implements CompletionHandler<Integer, Void>
	{
		public Integer result;
		public Throwable throwable;

		@Override
		public void completed(Integer result, Void attachment)
		{
			this.result = result;
		}

		@Override
		public void failed(Throwable throwable, Void attachment)
		{
			this.throwable = throwable;
		}
	}

	/**
	 * The result of an asynchronous character reading operation.
	 *
	 * @author Gili Tzabari
	 */
	private class ReadFuture implements Future<Integer>
	{
		private final ByteBuffer source;
		private final CharSequenceWriter target;
		private Future<Integer> future;
		private ExecutionException exception;

		/**
		 * Creates a new ReadFuture.
		 *
		 * @param future
		 *        The result of an asynchronous byte reading operation
		 * @param source
		 *        The buffer from which bytes are to be retrieved
		 * @param target
		 *        The buffer into which characters are to be transferred
		 */
		private ReadFuture(Future<Integer> future, ByteBuffer source, CharSequenceWriter target)
		{
			this.future = future;
			this.source = source;
			this.target = target;
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning)
		{
			return future.cancel(mayInterruptIfRunning);
		}

		@Override
		public boolean isCancelled()
		{
			return future.isCancelled();
		}

		@Override
		public boolean isDone()
		{
			return future.isDone();
		}

		@Override
		public Integer get() throws CancellationException, InterruptedException, ExecutionException
		{
			if (exception != null)
			{
				// Prevent CharsetDecoder from being invoked again
				throw exception;
			}
			while (true)
			{
				boolean endOfInput = future.get() == -1;
				PollableCompletionHandler handler = new PollableCompletionHandler();
				source.flip();
				if (onBytesRead(source, endOfInput, target, null, handler))
				{
					synchronized (AsynchronousByteCharChannel.this)
					{
						readPending = false;
					}
					if (handler.throwable != null)
					{
						exception = new ExecutionException(handler.throwable);
						throw exception;
					}
					return handler.result;
				}

				// ask for more bytes
				source.compact();
				future = channel.read(source);
			}
		}

		@Override
		public Integer get(long timeout, TimeUnit unit) throws CancellationException,
																													 InterruptedException,
																													 ExecutionException, TimeoutException
		{
			if (exception != null)
			{
				// Prevent CharsetDecoder from being invoked again
				throw exception;
			}
			while (true)
			{
				boolean endOfInput;
				try
				{
					endOfInput = future.get(timeout, unit) == -1;
				}
				catch (CancellationException e)
				{
					synchronized (AsynchronousByteCharChannel.this)
					{
						readPending = false;
					}
					throw e;
				}
				catch (InterruptedException e)
				{
					synchronized (AsynchronousByteCharChannel.this)
					{
						readPending = false;
					}
					throw e;
				}
				catch (ExecutionException e)
				{
					synchronized (AsynchronousByteCharChannel.this)
					{
						readPending = false;
					}
					throw e;
				}
				catch (TimeoutException e)
				{
					synchronized (AsynchronousByteCharChannel.this)
					{
						readPending = false;
					}
					throw e;
				}
				PollableCompletionHandler handler = new PollableCompletionHandler();
				source.flip();
				if (onBytesRead(source, endOfInput, target, null, handler))
				{
					synchronized (AsynchronousByteCharChannel.this)
					{
						readPending = false;
					}
					if (handler.throwable != null)
					{
						exception = new ExecutionException(handler.throwable);
						throw exception;
					}
					return handler.result;
				}

				// ask for more bytes
				source.compact();
				future = channel.read(source);
			}
		}
	}

	/**
	 * A handler for consuming the result of an asynchronous line reading operation.
	 *
	 * @author Gili Tzabari
	 */
	private static class ReadLineHandler<A> implements CompletionHandler<Integer, A>
	{
		private final CompletionHandler<String, ? super A> handler;
		private final StringBuilder target;

		/**
		 * Creates a new ReadLineHandler.
		 *
		 * @param attachment
		 *        The object to attach to the I/O operation; can be null
		 * @param handler
		 *        The CompletionHandler to delegate to
		 * @param target
		 *        The buffer into which characters are to be transferred
		 */
		public ReadLineHandler(CompletionHandler<String, ? super A> handler, StringBuilder target)
		{
			this.handler = handler;
			this.target = target;
		}

		@Override
		public void completed(Integer numBytesRead, A attachment)
		{
			if (numBytesRead == -1)
				handler.completed(null, attachment);
			else
				handler.completed(target.toString(), attachment);
		}

		@Override
		public void failed(Throwable throwable, A attachment)
		{
			handler.failed(throwable, attachment);
		}
	}

	/**
	 * The result of an asynchronous line reading operation.
	 *
	 * @author Gili Tzabari
	 */
	private static class ReadLineFuture implements Future<String>
	{
		private Future<Integer> future;
		private final StringBuilder target;

		/**
		 * Creates a new ReadLineFuture.
		 *
		 * @param future
		 *        The result of an asynchronous byte reading operation
		 * @param target
		 *        The buffer into which characters are to be transferred
		 */
		private ReadLineFuture(Future<Integer> future, StringBuilder target)
		{
			this.future = future;
			this.target = target;
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning)
		{
			return future.cancel(mayInterruptIfRunning);
		}

		@Override
		public boolean isCancelled()
		{
			return future.isCancelled();
		}

		@Override
		public boolean isDone()
		{
			return future.isDone();
		}

		@Override
		public String get() throws CancellationException, InterruptedException, ExecutionException
		{
			Integer result = future.get();
			if (result == -1)
				return null;
			return target.toString();
		}

		@Override
		public String get(long timeout, TimeUnit unit) throws CancellationException,
																													InterruptedException,
																													ExecutionException, TimeoutException
		{
			Integer result = future.get(timeout, unit);
			if (result == -1)
				return null;
			return target.toString();
		}
	}

	/**
	 * Invoked when a byte reading operation completes.
	 *
	 * @param <A>
	 *        The attachment type
	 * @param source
	 *        The buffer from which characters are to be retrieved
	 * @param sourceOffset
	 *        Source's initial position when the write operation began
	 * @param bytesWritten
	 *        The bytes that were written
	 * @param bytesRead
	 *        The buffer from which bytes are to be retrieved
	 * @param target
	 *        The buffer into which characters are to be transferred
	 * @param attachment
	 *        The object to attach to the I/O operation; can be null
	 * @param handler
	 *        The CompletionHandler to delegate to
	 * @param endOfInput
	 *        true if the end of the stream has been reached
	 * @return true if the write operation completed
	 */
	private <A> boolean onBytesWritten(CharBuffer source, int sourceOffset, ByteBuffer bytesWritten,
																		 A attachment, CompletionHandler<Integer, ? super A> handler,
																		 boolean endOfInput)
	{
		// Decode the bytes we sent out
		CharsetDecoder decoderForWrite = charset.newDecoder();
		final CharBuffer charBuffer = CharBuffer.allocate((int) Math.ceil(decoderForWrite.
			maxCharsPerByte()
																																			* bytesWritten.remaining()));
		CoderResult decodingResult = decoderForWrite.decode(bytesWritten, charBuffer, endOfInput);
		if (decodingResult.isUnmappable())
		{
			delegateError(decodingResult, attachment, handler);
			return true;
		}
		if (decodingResult.isMalformed())
		{
			// The write operation stopped in the middle of a codepoint
			skipWellFormedBytes(source, bytesWritten, decoderForWrite);

			bytesWritten.clear();
			putCodepoint(bytesWritten, source.toString().codePointAt(0), encoder);
			if (bytesWritten.position() > decodingResult.length())
			{
				// Send the rest of the codepoint
				bytesWritten.position(decodingResult.length());
				return false;
			}
			delegateError(decodingResult, attachment, handler);
			return true;
		}
		// We sent a whole number of codepoints
		source.position(source.position() + charBuffer.position());
		if (endOfInput)
			flushDecoderForWrite(decoderForWrite, charBuffer, source);

		handler.completed(source.position() - sourceOffset, attachment);
		return true;
	}

	/**
	 * Skips any well-formed bytes that were written.
	 *
	 * @param source
	 *        The buffer from which characters are to be retrieved
	 * @param malformedBytes
	 *        The buffer containing the malformed bytes
	 */
	private static void skipWellFormedBytes(CharBuffer source, ByteBuffer malformedBytes,
																					CharsetDecoder decoder)
	{
		malformedBytes.flip();
		CharBuffer charBuffer = CharBuffer.allocate((int) Math.ceil(decoder.maxCharsPerByte() * malformedBytes.
			remaining()));
		CoderResult decodingResult = decoder.decode(malformedBytes, charBuffer, true);
		assertCoderResult(decodingResult);
		decodingResult = decoder.flush(charBuffer);
		assertCoderResult(decodingResult);
		source.position(source.position() + charBuffer.position());
	}

	/**
	 * Puts a codepoint into a ByteBuffer.
	 *
	 * @param target
	 *        The buffer to write into
	 * @param codepoint
	 *        The codepoint
	 */
	private static void putCodepoint(ByteBuffer target, int codepoint, CharsetEncoder encoder)
	{
		// Encode one codepoint
		int charCount = Character.charCount(codepoint);
		CharBuffer codepointBuffer = CharBuffer.allocate(charCount);
		codepointBuffer.put(Character.toChars(codepoint));
		codepointBuffer.flip();
		CoderResult result = encoder.encode(codepointBuffer, target, true);
		assertCoderResult(result);
		result = encoder.flush(target);
		assertCoderResult(result);
	}

	/**
	 * Throws AssertionError if an CoderResult error has occured.
	 *
	 * @param coderResult
	 *        The result of an coding operation
	 * @throws AssertionError
	 *         If coderResult.isError()
	 */
	private static void assertCoderResult(CoderResult coderResult) throws AssertionError
	{
		if (coderResult.isError())
		{
			try
			{
				coderResult.throwException();
			}
			catch (CharacterCodingException e)
			{
				throw new AssertionError(e);
			}
		}
	}

	/**
	 * Delegate a decoding error if necessary.
	 *
	 * @param <A>
	 *        The attachment type
	 * @param coderResult
	 *        The CoderResult
	 * @param attachment
	 *        The object to attach to the I/O operation; can be null
	 * @param handler
	 *        The CompletionHandler to delegate to
	 */
	private static <A> void delegateError(CoderResult coderResult, A attachment,
																				CompletionHandler<?, ? super A> handler)
	{
		try
		{
			coderResult.throwException();
		}
		catch (CharacterCodingException e)
		{
			handler.failed(e, attachment);
		}
	}

	/**
	 * Flush the CharsetDecoder.
	 *
	 * @param decoder
	 *        The Charset decoder
	 * @param temp
	 *        A temporary buffer
	 * @param source
	 *        The buffer from which bytes are to be retrieved
	 */
	private static void flushDecoderForWrite(CharsetDecoder decoder, CharBuffer charBuffer,
																					 CharBuffer source)
	{
		CoderResult decodingResult;
		do
		{
			charBuffer.clear();
			decodingResult = decoder.flush(charBuffer);
			source.position(source.position() + charBuffer.position());
		}
		while (!decodingResult.isUnderflow());
	}

	/**
	 * A handler for consuming the result of an asynchronous character writing operation.
	 *
	 * @author Gili Tzabari
	 */
	private class WriteHandler<A> implements CompletionHandler<Integer, ByteBuffer>
	{
		private final CompletionHandler<Integer, ? super A> handler;
		private final A attachment;
		private final CharBuffer source;
		/**
		 * Source's initial position.
		 */
		private final int sourceOffset;
		private final boolean endOfInput;

		/**
		 * Creates a new WriteHandler.
		 *
		 * @param attachment
		 *        The object to attach to the I/O operation; can be null
		 * @param handler
		 *        The CompletionHandler to delegate to
		 * @param source
		 *        The buffer from which bytes are to be retrieved
		 * @param endOfInput
		 *        true if the end of stream has been reached
		 */
		public WriteHandler(A attachment, CompletionHandler<Integer, ? super A> handler,
												CharBuffer source,
												boolean endOfInput)
		{
			this.handler = handler;
			this.attachment = attachment;
			this.source = source;
			this.sourceOffset = source.position();
			this.endOfInput = endOfInput;
		}

		@Override
		public void completed(Integer unused, ByteBuffer bytesWritten)
		{
			// Ensure we wrote a whole number of characters
			bytesWritten.flip();
			if (onBytesWritten(source, sourceOffset, bytesWritten, attachment, handler, endOfInput))
			{
				synchronized (AsynchronousByteCharChannel.this)
				{
					writePending = false;
				}
				return;
			}

			// Write the remaining bytes
			channel.write(bytesWritten, bytesWritten, this);
		}

		@Override
		public void failed(Throwable throwable, ByteBuffer source)
		{
			handler.failed(throwable, attachment);
			synchronized (AsynchronousByteCharChannel.this)
			{
				readPending = false;
			}
		}
	}

	/**
	 * The result of an asynchronous character writing operation.
	 *
	 * @author Gili Tzabari
	 */
	private class WriteFuture implements Future<Integer>
	{
		private final CharBuffer source;
		private final ByteBuffer bytesWritten;
		/**
		 * Source's initial position.
		 */
		private final int sourceOffset;
		private final boolean endOfInput;
		private Future<Integer> future;
		private ExecutionException throwable;

		/**
		 * Creates a new WriteFuture.
		 *
		 * @param future
		 *        The result of an asynchronous byte writing operation
		 * @param source
		 *        The buffer from which characters are to be retrieved
		 * @param bytesWritten
		 *        The buffer from which bytes are to be retrieved
		 * @param endOfInput
		 *        true if the end of stream has been reached
		 */
		private WriteFuture(Future<Integer> future, CharBuffer source, ByteBuffer bytesWritten,
												boolean endOfInput)
		{
			this.future = future;
			this.source = source;
			this.bytesWritten = bytesWritten;
			this.sourceOffset = source.position();
			this.endOfInput = endOfInput;
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning)
		{
			return future.cancel(mayInterruptIfRunning);
		}

		@Override
		public boolean isCancelled()
		{
			return future.isCancelled();
		}

		@Override
		public boolean isDone()
		{
			return future.isDone();
		}

		@Override
		public Integer get() throws CancellationException, InterruptedException, ExecutionException
		{
			if (throwable != null)
			{
				// Prevent CharsetDecoder from being invoked again
				throw throwable;
			}
			while (true)
			{
				future.get();

				// Ensure we wrote a whole number of characters
				bytesWritten.flip();
				PollableCompletionHandler handler = new PollableCompletionHandler();
				if (onBytesWritten(source, sourceOffset, bytesWritten, null, handler, endOfInput))
				{
					synchronized (AsynchronousByteCharChannel.this)
					{
						writePending = false;
					}
					if (handler.throwable != null)
					{
						throwable = new ExecutionException(handler.throwable);

						throw throwable;
					}
					return handler.result;
				}

				// Write the remaining bytes
				future = channel.write(bytesWritten);
			}
		}

		@Override
		public Integer get(long timeout, TimeUnit unit) throws CancellationException,
																													 InterruptedException,
																													 ExecutionException, TimeoutException
		{
			if (throwable != null)
			{
				// Prevent CharsetDecoder from being invoked again
				throw throwable;
			}
			while (true)
			{
				try
				{
					future.get(timeout, unit);
				}
				catch (CancellationException e)
				{
					synchronized (AsynchronousByteCharChannel.this)
					{
						writePending = false;
					}
					throw e;
				}
				catch (InterruptedException e)
				{
					synchronized (AsynchronousByteCharChannel.this)
					{
						writePending = false;
					}
					throw e;
				}
				catch (ExecutionException e)
				{
					synchronized (AsynchronousByteCharChannel.this)
					{
						writePending = false;
					}
					throw e;
				}
				catch (TimeoutException e)
				{
					synchronized (AsynchronousByteCharChannel.this)
					{
						writePending = false;
					}
					throw e;
				}

				// Ensure we wrote a whole number of characters
				bytesWritten.flip();
				PollableCompletionHandler handler = new PollableCompletionHandler();
				if (onBytesWritten(source, sourceOffset, bytesWritten, null, handler, endOfInput))
				{
					synchronized (AsynchronousByteCharChannel.this)
					{
						writePending = false;
					}
					if (handler.throwable != null)
					{
						throwable = new ExecutionException(handler.throwable);

						throw throwable;
					}
					return handler.result;
				}

				// Write the remaining bytes
				future = channel.write(bytesWritten);
			}
		}
	}

	/**
	 * An interface for writing CharSequences.
	 *
	 * @author Gili Tzabari
	 */
	private interface CharSequenceWriter
	{
		/**
		 * Writes a sequence of characters.
		 *
		 * @param source
		 *        The character sequence to write
		 * @return The number of characters that were consumed,
		 *         <tt>0</tt>&nbsp;<tt>&lt;=</tt>&nbsp;<tt>n</tt>&nbsp;<tt>&lt;</tt>&nbsp;<tt>source.length()</tt>.
		 *         Note that there is no implied correlation between the number of characters that are consumed
		 *         and the number of characters that are written. Writers may choose to skip an arbitrary number
		 *         of bytes.
		 * @throws IOException
		 *         If an I/O error occurs
		 */
		int write(CharSequence source) throws IOException;
	}

	/**
	 * A CharSequenceWriter for writing into CharBuffers.
	 *
	 * @author Gili Tzabari
	 */
	private static class CharBufferWriter implements CharSequenceWriter
	{
		private final CharBuffer target;

		/**
		 * Creates a new CharBufferWriter.
		 *
		 * @param target
		 *        The buffer into which characters are to be transferred
		 */
		public CharBufferWriter(CharBuffer target)
		{
			this.target = target;
		}

		@Override
		public int write(CharSequence source)
		{
			int result = Math.min(source.length(), target.remaining());

			// WORKAROUND: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4860681
			StringBuilder sourceString = (StringBuilder) source;

			target.put(sourceString.substring(0, result));
			return result;
		}
	}

	/**
	 * A CharSequenceWriter for writing lines of text.
	 *
	 * @author Gili Tzabari
	 */
	private class LineWriter implements CharSequenceWriter
	{
		private final StringBuilder target;

		/**
		 * Creates a new StringBuilderWriter.
		 *
		 * @param target
		 *        The buffer into which characters are to be transferred
		 */
		public LineWriter(StringBuilder target)
		{
			this.target = target;
		}

		@Override
		public int write(CharSequence source) throws IOException
		{
			Matcher matcher = delimiters.matcher(source);
			int startIndex = 0;
			while (true)
			{
				if (!matcher.find())
					return 0;
				if (matcher.group().equals("\r"))
					consumeNewline = true;
				else if (matcher.group().equals("\n"))
				{
					if (consumeNewline && matcher.start() == 0)
					{
						// "consumeNewline" gets reset if we find a real line terminator, otherwise we need to retain
						// its value for the next method invocation.
						startIndex = "\n".length();
						continue;
					}
					consumeNewline = false;
				}

				target.append(source.subSequence(startIndex, matcher.start()));

				// +1 to strip out the line delimiter
				return matcher.start() + 1;
			}
		}
	}

}
