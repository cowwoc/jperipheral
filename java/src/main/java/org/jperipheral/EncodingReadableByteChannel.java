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
	private final CharBuffer charBuffer;
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
		this.charBuffer = CharBuffer.allocate(1024);
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

	/**
	 * Returns the maximum number of bytes that a read may return.
	 * 
	 * @return the maximum number of bytes that a read may return
	 */
	public int maxReadCount()
	{
		return (int) Math.ceil(encoder.maxBytesPerChar() * charBuffer.length());
	}

	@Override
	public int read(ByteBuffer dst) throws IOException
	{
		if (closed)
			throw new ClosedChannelException();
		if (endOfStream)
			return -1;

		if (!endOfInput)
		{
			// Fill the String buffer
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
		int result = 0;
		if (!flushing)
		{
			int positionBeforeWriting = dst.position();
			CoderResult encodingResult = encoder.encode(charBuffer, dst, endOfInput);
			if (encodingResult.isError())
				encodingResult.throwException();
			flushing = endOfInput && !encodingResult.isOverflow();
			result = dst.position() - positionBeforeWriting;
		}
		if (flushing)
		{
			flushing = true;
			// Encode the final bytes
			int positionBeforeWriting = dst.position();
			CoderResult encodingResult = encoder.flush(dst);
			if (encodingResult.isError())
				encodingResult.throwException();
			result += dst.position() - positionBeforeWriting;
			endOfStream = encodingResult.isUnderflow();
			if (result == 0)
				return -1;
		}
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
