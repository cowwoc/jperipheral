package org.jperipheral;

import java.nio.CharBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutionException;
import org.junit.Test;

/**
 * @author Gili Tzabari
 */
public class TestAsynchronousByteCharChannel
{
	private final Charset utf8 = Charset.forName("UTF-8");

	@Test
	public void readChar() throws InterruptedException, ExecutionException
	{
		String input = "1\r2\n3\r\n4";

		AsynchronousByteChannel byteChannel = AsynchronousByteChannelFactory.fromString(input, utf8);
		AsynchronousCharChannel charChannel = AsynchronousByteCharChannel.open(byteChannel, utf8);

		CharBuffer buffer = CharBuffer.allocate(1);
		Integer result = charChannel.read(buffer).get();
		assert (result == 1): result;

		buffer.flip();
		assert (buffer.toString().equals(input.substring(0, 1))): buffer.toString();
	}

	@Test
	public void readCharacters() throws InterruptedException, ExecutionException
	{
		String input = "1\r2\n3\r\n4";

		AsynchronousByteChannel byteChannel = AsynchronousByteChannelFactory.fromString(input, utf8);
		AsynchronousCharChannel charChannel = AsynchronousByteCharChannel.open(byteChannel, utf8);

		CharBuffer buffer = CharBuffer.allocate(5);
		Integer result = charChannel.read(buffer).get();
		assert (result == 5): result;

		buffer.flip();
		assert (buffer.toString().equals(input.substring(0, 5))): buffer.toString();
	}

	@Test
	public void readSingleLine() throws InterruptedException, ExecutionException
	{
		String input = "1\r2\n3\r\n4";

		AsynchronousByteChannel byteChannel = AsynchronousByteChannelFactory.fromString(input, utf8);
		AsynchronousCharChannel charChannel = AsynchronousByteCharChannel.open(byteChannel, utf8);
		String result = charChannel.readLine().get();
		assert (result.equals(input.substring(0, 1))): result;
	}

	@Test
	public void readMultipleLines() throws InterruptedException, ExecutionException
	{
		String input = "1\r2\n3\r\n4";

		AsynchronousByteChannel byteChannel = AsynchronousByteChannelFactory.fromString(input, utf8);
		AsynchronousCharChannel charChannel = AsynchronousByteCharChannel.open(byteChannel, utf8);
		String result = charChannel.readLine().get();
		assert (result.equals("1")): result;

		result = charChannel.readLine().get();
		assert (result.equals("2")): result;

		result = charChannel.readLine().get();
		assert (result.equals("3")): result;

		result = charChannel.readLine().get();
		assert (result.equals("4")): result;
	}
}
