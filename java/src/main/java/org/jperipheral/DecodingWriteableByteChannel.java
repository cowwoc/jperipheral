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
public class DecodingWriteableByteChannel implements WritableByteChannel
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
	private final ByteBuffer malformedBytes;

	/**
	 * Creates a new DecodingWriteableByteChannel.
	 * 
	 * @param charset the character set used to encode the text
	 */
	public DecodingWriteableByteChannel(Charset charset)
	{
		this.decoder = charset.newDecoder();
		this.charBuffer = CharBuffer.allocate((int) Math.ceil(decoder.maxCharsPerByte() * 1024));
		this.malformedBytes = ByteBuffer.allocate((int) Math.ceil(decoder.maxCharsPerByte()));
		this.malformedBytes.flip();
	}

	@Override
	public int write(ByteBuffer src) throws IOException
	{
		if (closed)
			throw new ClosedChannelException();
		int bytesRead = 0;
		CoderResult decodingResult;
		if (malformedBytes.remaining() > 0)
		{
			// Try to fill malformedBytes so it contains at least one character's worth of data
			int bytesToTransfer = Math.min(malformedBytes.remaining(), src.remaining());
			ByteBuffer truncated = src.duplicate();
			truncated.limit(truncated.position() + bytesToTransfer);
			malformedBytes.put(truncated);
			src.position(src.position() + bytesToTransfer);
			decodingResult = decoder.decode(malformedBytes, charBuffer, false);
			assert (!decodingResult.isOverflow()): "charBuffer should be able to hold one character";
			if (decodingResult.isError())
				decodingResult.throwException();
			if (decodingResult.isUnderflow())
			{
				// Transfer the remaining bytes back into src
				src.position(src.position() - malformedBytes.remaining());
			}
			malformedBytes.clear();
		}
		do
		{
			int positionBeforeReading = src.position();
			decodingResult = decoder.decode(src, charBuffer, false);
			if (decodingResult.isError())
				decodingResult.throwException();
			bytesRead += src.position() - positionBeforeReading;
			charBuffer.flip();
			result.append(charBuffer.toString());
			charBuffer.clear();
		}
		while (decodingResult.isOverflow());

		// The remaining bytes denote a malformed byte sequence for this charset unless subsequent bytes
		// are supplied.
		malformedBytes.put(src);
		malformedBytes.flip();
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
				ByteBuffer empty = ByteBuffer.allocate(0);
				do
				{
					decodingResult = decoder.decode(empty, charBuffer, true);
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
