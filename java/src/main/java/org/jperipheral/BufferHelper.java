package org.jperipheral;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;

/**
 * Buffer helper classes.
 *
 * @author Gili Tzabari
 */
final class BufferHelper
{
	private static BufferHelper instance;

	/**
	 * Returns a BufferHelper.
	 * 
	 * @return a BufferHelper instance
	 */
	public static synchronized BufferHelper getInstance()
	{
		if (instance == null)
			instance = new BufferHelper();
		return instance;
	}

	/**
	 * Writes a CharSequence into a ByteBuffer.
	 *
	 * @param source the CharSequence to read from
	 * @param charset the source character set
	 * @param target the ByteBuffer to write into
	 * @throws CharacterCodingException if an encoding error occurs
	 */
	public void put(CharSequence source, Charset charset, ByteBuffer target) throws CharacterCodingException
	{
		put(CharBuffer.wrap(source), charset, target);
	}

	/**
	 * Writes a CharBuffer into a ByteBuffer.
	 *
	 * @param source the CharBuffer to read from
	 * @param charset the source character set
	 * @param target the ByteBuffer to write into
	 * @throws CharacterCodingException if an encoding error occurs
	 */
	public void put(CharBuffer source, Charset charset, ByteBuffer target)
		throws CharacterCodingException
	{
		final CoderResult result = charset.newEncoder().encode(source, target, true);
		if (result.isError() || result.isOverflow())
			result.throwException();
	}

	/**
	 * Reads a CharBuffer from a ByteBuffer.
	 *
	 * @param source the ByteBuffer
	 * @param decoder the CharsetDecoder for the target character set
	 * @param target the CharBuffer
	 * @throws CharacterCodingException if an encoding error occurs
	 */
	private void get(ByteBuffer source, CharsetDecoder decoder, CharBuffer target) throws
		CharacterCodingException
	{
		final CoderResult result = decoder.decode(source, target, true);
		if (result.isError() || result.isOverflow())
			result.throwException();
	}

	/**
	 * Reads a CharBuffer from a ByteBuffer.
	 *
	 * @param source the ByteBuffer
	 * @param charset the target character set
	 * @param target the CharBuffer
	 * @throws CharacterCodingException if an encoding error occurs
	 */
	public void get(ByteBuffer source, Charset charset, CharBuffer target) throws CharacterCodingException
	{
		final CharsetDecoder decoder = charset.newDecoder();
		get(source, decoder, target);
	}

	/**
	 * Reads a String from a ByteBuffer, without altering the buffer position.
	 *
	 * @param source the ByteBuffer
	 * @param charset the String charset
	 * @return the String
	 * @throws CharacterCodingException if an encoding error occurs
	 */
	public String toString(ByteBuffer source, Charset charset) throws CharacterCodingException
	{
		int oldPosition = source.position();
		CharsetDecoder decoder = charset.newDecoder();
		CharBuffer target = CharBuffer.allocate((int) Math.ceil(decoder.maxCharsPerByte() *
																														source.remaining()));
		get(source, decoder, target);
		source.position(oldPosition);
		target.flip();
		return target.toString();
	}
}
