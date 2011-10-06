package org.jperipheral;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;

/**
 * Converts bytes written to a String.
 * 
 * @author Gili Tzabari
 */
class DecodingWriteableByteChannel implements WritableByteChannel
{
	private final CharsetDecoder decoder;
	private boolean closed;
	private final StringBuilder result = new StringBuilder();
	/**
	 * Used to decode bytes to characters.
	 */
	private final CharBuffer charBuffer;
	/**
	 * Holds a byte sequence that is malformed for this charset unless subsequent bytes are supplied.
	 */
	private final ByteBuffer byteBuffer;

	/**
	 * Creates a new DecodingWriteableByteChannel.
	 * 
	 * @param charset the character set used to encode the text
	 */
	public DecodingWriteableByteChannel(Charset charset)
	{
		this.decoder = charset.newDecoder();
		this.charBuffer = CharBuffer.allocate((int) Math.ceil(decoder.maxCharsPerByte() * 1024));
		this.byteBuffer = ByteBuffer.allocate((int) Math.ceil(decoder.maxCharsPerByte() * 1024));
	}

	@Override
	public int write(ByteBuffer src) throws IOException
	{
		if (closed)
			throw new ClosedChannelException();
		CoderResult decodingResult;
		assert (byteBuffer.hasRemaining()): byteBuffer;

		// Ensure byteBuffer contains at least one character's worth of data
		int bytesRead = Math.min(byteBuffer.remaining(), src.remaining());
		ByteBuffer truncated = src.duplicate();
		truncated.limit(truncated.position() + bytesRead);
		byteBuffer.put(truncated);
		src.position(src.position() + bytesRead);
		
		do
		{
			byteBuffer.flip();
			decodingResult = decoder.decode(byteBuffer, charBuffer, false);
			if (decodingResult.isError())
				decodingResult.throwException();
			charBuffer.flip();
			result.append(charBuffer.toString());
			charBuffer.clear();
		}
		while (decodingResult.isOverflow());
		byteBuffer.compact();
		return bytesRead;
	}

	@Override
	public boolean isOpen()
	{
		return !closed;
	}

	@Override
	public void close() throws IOException
	{
		if (!closed)
		{
			CoderResult decodingResult;
			do
			{
				charBuffer.clear();
				byteBuffer.flip();
				do
				{
					decodingResult = decoder.decode(byteBuffer, charBuffer, true);
					if (decodingResult.isError())
						decodingResult.throwException();
					charBuffer.flip();
					result.append(charBuffer.toString());
					charBuffer.clear();
				}
				while (decodingResult.isOverflow());

				do
				{
					decodingResult = decoder.flush(charBuffer);
					charBuffer.flip();
					result.append(charBuffer.toString());
					charBuffer.clear();
				}
				while (decodingResult.isOverflow());
			}
			while (!decodingResult.isUnderflow());
			closed = true;
		}
	}

	/**
	 * Returns the decoded String.
	 * 
	 * @return the decoded String
	 */
	public StringBuilder toStringBuilder()
	{
		return result;
	}
}
