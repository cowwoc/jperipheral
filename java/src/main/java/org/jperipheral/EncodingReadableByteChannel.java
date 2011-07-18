package org.jperipheral;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;

/**
 * Converts bytes read to a String.
 * 
 * @author Gili Tzabari
 */
public class EncodingReadableByteChannel implements ReadableByteChannel
{
	private final StringBuilder input = new StringBuilder();
	private boolean endOfInput;
	private boolean flushing;
	private boolean endOfStream;
	/**
	 * We copy data from input to charBuffer.
	 */
	private final CharBuffer charBuffer;
	/**
	 * We decode characters from charBuffer to byteBuffer.
	 */
	private final ByteBuffer byteBuffer;
	private final CharsetEncoder encoder;
	private boolean closed;

	/**
	 * Creates a new EncodingReadableByteChannel.
	 * 
	 * @param charset the character set used to encode the text
	 */
	public EncodingReadableByteChannel(Charset charset)
	{
		this.encoder = charset.newEncoder();
		this.charBuffer = CharBuffer.allocate(128);
		this.byteBuffer = ByteBuffer.allocate((int) Math.ceil(encoder.maxBytesPerChar() * charBuffer.
			length()));
		this.byteBuffer.flip();
		this.charBuffer.flip();
	}

	/**
	 * Appends to the text stream to be encoded.
	 * 
	 * @param text the text to be encoded, null to terminate the stream
	 * @throws NullPointerException if text is null
	 * @throws IllegalStateException if the input stream is terminated
	 */
	public void append(CharSequence text)
	{
		Preconditions.checkNotNull(text, "text may not be null");

		if (endOfInput)
			throw new IllegalStateException("Cannot append to a terminated stream");
		input.append(text);
	}

	@Override
	public int read(ByteBuffer dst) throws IOException
	{
		if (closed)
			throw new ClosedChannelException();
		if (endOfStream)
			return -1;
		if (!byteBuffer.hasRemaining())
		{
			if (!endOfInput)
			{
				// Fill the char buffer
				charBuffer.compact();
				if (charBuffer.length() > 0 && input.length() == 0)
					endOfInput = true;
				else
				{
					int count = Math.min(input.length(), charBuffer.length());
					charBuffer.put(input.substring(0, count));
					input.delete(0, count);
				}
				charBuffer.flip();
			}
			if (!flushing)
			{
				// Fill the byte buffer
				byteBuffer.compact();
				CoderResult encodingResult = encoder.encode(charBuffer, byteBuffer, endOfInput);
				if (encodingResult.isError())
					encodingResult.throwException();
				flushing = endOfInput && !encodingResult.isOverflow();
				byteBuffer.flip();
			}
			if (flushing)
			{
				flushing = true;
				// Encode the final bytes
				CoderResult encodingResult = encoder.flush(dst);
				if (encodingResult.isError())
					encodingResult.throwException();
				endOfStream = encodingResult.isUnderflow();
			}
		}
		ByteBuffer truncated = byteBuffer.duplicate();
		if (byteBuffer.remaining() > dst.remaining())
			truncated.limit(truncated.position() + dst.remaining());
		dst.put(truncated);
		int result = truncated.position() - byteBuffer.position();
		byteBuffer.position(truncated.position());
		if (result == 0 && flushing)
			return -1;
		return result;
	}

	@Override
	public boolean isOpen()
	{
		return !closed;
	}

	@Override
	public void close() throws IOException
	{
		closed = true;
	}
}
