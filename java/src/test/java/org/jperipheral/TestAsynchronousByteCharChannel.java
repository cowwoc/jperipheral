package org.jperipheral;

import com.google.common.base.Charsets;
import java.nio.CharBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p/>
 * <p/>
 * @author Gili Tzabari
 */
public class TestAsynchronousByteCharChannel
{
	private Logger log = LoggerFactory.getLogger(TestAsynchronousByteCharChannel.class);

	@Test
	public void readCharFuture() throws InterruptedException, ExecutionException
	{
		log.trace("start");
		String input = "1\r2\n3\r\n4";
		StringBuilder output = new StringBuilder();

		AsynchronousByteChannel byteChannel = AsynchronousByteChannelFactory.fromString(input,
			output, Charsets.UTF_8);
		AsynchronousCharChannel charChannel = AsynchronousByteCharChannel.open(byteChannel,
			Charsets.UTF_8);

		CharBuffer buffer = CharBuffer.allocate(1);
		Integer result = charChannel.read(buffer).get();
		assert (result == 1): result;

		buffer.flip();
		assert (buffer.toString().equals(input.substring(0, 1))): buffer.toString();
		log.trace("stop");
	}

	@Test
	public void writeCharFuture() throws InterruptedException, ExecutionException
	{
		log.trace("start");
		String input = "1\r2\n3\r\n4";
		StringBuilder output = new StringBuilder();

		AsynchronousByteChannel byteChannel = AsynchronousByteChannelFactory.fromString(input,
			output, Charsets.UTF_8);
		AsynchronousCharChannel charChannel = AsynchronousByteCharChannel.open(byteChannel,
			Charsets.UTF_8);

		CharBuffer buffer = CharBuffer.wrap(input);
		buffer.limit(1);
		Integer result = charChannel.write(buffer).get();
		assert (result == 1): result;

		assert (output.toString().equals(input.substring(0, 1))): output.toString();
		log.trace("stop");
	}

	@Test
	public void readCharHandler() throws InterruptedException, ExecutionException
	{
		log.trace("start");
		String input = "1\r2\n3\r\n4";
		StringBuilder output = new StringBuilder();

		AsynchronousByteChannel byteChannel = AsynchronousByteChannelFactory.fromString(input,
			output, Charsets.UTF_8);
		AsynchronousCharChannel charChannel = AsynchronousByteCharChannel.open(byteChannel,
			Charsets.UTF_8);

		CharBuffer buffer = CharBuffer.allocate(1);
		PollableCompletionHandler<Integer> handler = new PollableCompletionHandler<>();
		synchronized (handler)
		{
			charChannel.read(buffer, null, handler);
			handler.wait();
		}
		assert (handler.value == 1): handler.value;

		buffer.flip();
		assert (buffer.toString().equals(input.substring(0, 1))): buffer.toString();
		log.trace("stop");
	}

	@Test
	public void writeCharHandler() throws InterruptedException, ExecutionException
	{
		log.trace("start");
		String input = "1\r2\n3\r\n4";
		StringBuilder output = new StringBuilder();

		AsynchronousByteChannel byteChannel = AsynchronousByteChannelFactory.fromString(input,
			output, Charsets.UTF_8);
		AsynchronousCharChannel charChannel = AsynchronousByteCharChannel.open(byteChannel,
			Charsets.UTF_8);

		CharBuffer buffer = CharBuffer.wrap(input);
		buffer.limit(1);
		PollableCompletionHandler<Integer> handler = new PollableCompletionHandler<>();
		synchronized (handler)
		{
			charChannel.write(buffer, null, handler);
			handler.wait();
		}
		assert (handler.value == 1): handler.value;

		assert (output.toString().equals(input.substring(0, 1))): output.toString();
		log.trace("stop");
	}

	@Test
	public void readCharactersFuture() throws InterruptedException, ExecutionException
	{
		log.trace("start");
		String input = "1\r2\n3\r\n4";
		StringBuilder output = new StringBuilder();

		AsynchronousByteChannel byteChannel = AsynchronousByteChannelFactory.fromString(input,
			output, Charsets.UTF_8);
		AsynchronousCharChannel charChannel = AsynchronousByteCharChannel.open(byteChannel,
			Charsets.UTF_8);

		CharBuffer buffer = CharBuffer.allocate(5);
		Integer result = charChannel.read(buffer).get();
		assert (result == 5): result;

		buffer.flip();
		assert (buffer.toString().equals(input.substring(0, 5))): buffer.toString();
		log.trace("stop");
	}

	@Test
	public void writeCharactersFuture() throws InterruptedException, ExecutionException
	{
		log.trace("start");
		String input = "1\r2\n3\r\n4";
		StringBuilder output = new StringBuilder();

		AsynchronousByteChannel byteChannel = AsynchronousByteChannelFactory.fromString(input,
			output, Charsets.UTF_8);
		AsynchronousCharChannel charChannel = AsynchronousByteCharChannel.open(byteChannel,
			Charsets.UTF_8);

		CharBuffer buffer = CharBuffer.wrap(input);
		buffer.limit(5);
		Integer result = charChannel.write(buffer).get();
		assert (result == 5): result;

		assert (output.toString().equals(input.substring(0, 5))): output.toString();
		log.trace("stop");
	}

	@Test
	public void readCharactersHandler() throws InterruptedException, ExecutionException
	{
		log.trace("start");
		String input = "1\r2\n3\r\n4";
		StringBuilder output = new StringBuilder();


		AsynchronousByteChannel byteChannel = AsynchronousByteChannelFactory.fromString(input,
			output, Charsets.UTF_8);
		AsynchronousCharChannel charChannel = AsynchronousByteCharChannel.open(byteChannel,
			Charsets.UTF_8);

		CharBuffer buffer = CharBuffer.allocate(5);
		PollableCompletionHandler<Integer> handler = new PollableCompletionHandler<>();
		synchronized (handler)
		{
			charChannel.read(buffer, null, handler);
			handler.wait();
		}
		assert (handler.value == 5): handler.value;
		buffer.flip();
		assert (buffer.toString().equals(input.substring(0, 5))): buffer.toString();

		buffer.clear();
		handler = new PollableCompletionHandler<>();
		synchronized (handler)
		{
			charChannel.read(buffer, null, handler);
			handler.wait();
		}
		assert (handler.value == 3): handler.value;
		buffer.flip();
		assert (buffer.toString().equals(input.substring(5, 8))): buffer.toString();

		buffer.clear();
		handler = new PollableCompletionHandler<>();
		synchronized (handler)
		{
			charChannel.read(buffer, null, handler);
			handler.wait();
		}
		assert (handler.value == -1): handler.value;
		log.trace("stop");
	}

	@Test
	public void writeCharactersHandler() throws InterruptedException, ExecutionException
	{
		log.trace("start");
		String input = "1\r2\n3\r\n4";
		StringBuilder output = new StringBuilder();


		AsynchronousByteChannel byteChannel = AsynchronousByteChannelFactory.fromString(input,
			output, Charsets.UTF_8);
		AsynchronousCharChannel charChannel = AsynchronousByteCharChannel.open(byteChannel,
			Charsets.UTF_8);

		CharBuffer buffer = CharBuffer.wrap(input);
		buffer.limit(5);
		PollableCompletionHandler<Integer> handler = new PollableCompletionHandler<>();
		synchronized (handler)
		{
			charChannel.write(buffer, null, handler);
			handler.wait();
		}
		assert (handler.value == 5): handler.value;
		
		assert (output.toString().equals(input.substring(0, 5))): output.toString();
		output.setLength(0);

		buffer.limit(buffer.position() + 3);
		handler = new PollableCompletionHandler<>();
		synchronized (handler)
		{
			charChannel.write(buffer, null, handler);
			handler.wait();
		}
		assert (handler.value == 3): handler.value;
		
		assert (output.toString().equals(input.substring(5, 8))): output.toString();

		handler = new PollableCompletionHandler<>();
		synchronized (handler)
		{
			charChannel.write(buffer, null, handler);
			handler.wait();
		}
		assert (handler.value == 0): handler.value;
		log.trace("stop");
	}

	@Test
	public void readSingleLineFuture() throws InterruptedException, ExecutionException
	{
		log.trace("start");
		String input = "1\r2\n3\r\n4";
		StringBuilder output = new StringBuilder();

		AsynchronousByteChannel byteChannel = AsynchronousByteChannelFactory.fromString(input,
			output, Charsets.UTF_8);
		AsynchronousCharChannel charChannel = AsynchronousByteCharChannel.open(byteChannel,
			Charsets.UTF_8);

		String result = charChannel.readLine().get();
		assert (result.equals(input.substring(0, 1))): result;
		log.trace("stop");
	}

	@Test
	public void readSingleLineHandler() throws InterruptedException, ExecutionException
	{
		log.trace("start");
		String input = "1\r2\n3\r\n4";
		StringBuilder output = new StringBuilder();

		AsynchronousByteChannel byteChannel = AsynchronousByteChannelFactory.fromString(input,
			output, Charsets.UTF_8);
		AsynchronousCharChannel charChannel = AsynchronousByteCharChannel.open(byteChannel,
			Charsets.UTF_8);

		PollableCompletionHandler<String> handler = new PollableCompletionHandler<>();
		synchronized (handler)
		{
			charChannel.readLine(null, handler);
			handler.wait();
		}
		assert (handler.value.equals(input.substring(0, 1))): handler.value;
		log.trace("stop");
	}

	@Test
	public void readMultipleLinesFuture() throws InterruptedException, ExecutionException
	{
		log.trace("start");
		String input = "1\r2\n3\r\n4";
		StringBuilder output = new StringBuilder();

		AsynchronousByteChannel byteChannel = AsynchronousByteChannelFactory.fromString(input,
			output, Charsets.UTF_8);
		AsynchronousCharChannel charChannel = AsynchronousByteCharChannel.open(byteChannel,
			Charsets.UTF_8);

		String result = charChannel.readLine().get();
		assert (result.equals("1")): result;

		result = charChannel.readLine().get();
		assert (result.equals("2")): result;

		result = charChannel.readLine().get();
		assert (result.equals("3")): result;

		result = charChannel.readLine().get();
		assert (result.equals("4")): result;

		result = charChannel.readLine().get();
		assert (result == null): result;
		log.trace("stop");
	}

	@Test
	public void readMultipleLinesHandler() throws InterruptedException, ExecutionException
	{
		log.trace("start");
		String input = "1\r2\n3\r\n4";
		StringBuilder output = new StringBuilder();

		AsynchronousByteChannel byteChannel = AsynchronousByteChannelFactory.fromString(input,
			output, Charsets.UTF_8);
		AsynchronousCharChannel charChannel = AsynchronousByteCharChannel.open(byteChannel,
			Charsets.UTF_8);

		PollableCompletionHandler<String> handler = new PollableCompletionHandler<>();
		synchronized (handler)
		{
			charChannel.readLine(null, handler);
			handler.wait();
		}
		assert (handler.value.equals("1")): handler.value;

		handler = new PollableCompletionHandler<>();
		synchronized (handler)
		{
			charChannel.readLine(null, handler);
			handler.wait();
		}
		assert (handler.value.equals("2")): handler.value;

		handler = new PollableCompletionHandler<>();
		synchronized (handler)
		{
			charChannel.readLine(null, handler);
			handler.wait();
		}
		assert (handler.value.equals("3")): handler.value;

		handler = new PollableCompletionHandler<>();
		synchronized (handler)
		{
			charChannel.readLine(null, handler);
			handler.wait();
		}
		assert (handler.value.equals("4")): handler.value;

		handler = new PollableCompletionHandler<>();
		synchronized (handler)
		{
			charChannel.readLine(null, handler);
			handler.wait();
		}
		assert (handler.value == null): handler.value;
		log.trace("stop");
	}

	@Test
	public void readCharactersInterruptMidCharacter() throws InterruptedException, ExecutionException
	{
		// Interrupts the read operation in the middle of a character.
		log.trace("start");
		String input = "\uD840\uDC00123\uD840\uDC00";
		StringBuilder output = new StringBuilder();

		AsynchronousByteChannel byteChannel = AsynchronousByteChannelFactory.fromString(input,
			output, Charsets.UTF_8);
		byteChannel = AsynchronousByteChannelFactory.interruptAt(byteChannel, 1, 0);
		AsynchronousCharChannel charChannel = AsynchronousByteCharChannel.open(byteChannel,
			Charsets.UTF_8);

		CharBuffer buffer = CharBuffer.allocate(5);
		Integer result = charChannel.read(buffer).get();
		assert (result == 5): result;
		buffer.flip();
		assert (buffer.toString().equals(input.substring(0, 5))): buffer.toString();
		buffer.clear();

		result = charChannel.read(buffer).get();
		assert (result == 2): result;
		buffer.flip();
		assert (buffer.toString().equals(input.substring(5, 7))): buffer.toString();
		buffer.clear();

		result = charChannel.read(buffer).get();
		assert (result == -1): result;
		log.trace("stop");
	}
	
	@Test
	public void writeCharactersInterruptMidCharacter() throws InterruptedException, ExecutionException
	{
		// Interrupts the write operation in the middle of a character.
		log.trace("start");
		String input = "\uD840\uDC00123\uD840\uDC00";
		StringBuilder output = new StringBuilder();

		AsynchronousByteChannel byteChannel = AsynchronousByteChannelFactory.fromString(input,
			output, Charsets.UTF_8);
		byteChannel = AsynchronousByteChannelFactory.interruptAt(byteChannel, 0, 1);
		AsynchronousCharChannel charChannel = AsynchronousByteCharChannel.open(byteChannel,
			Charsets.UTF_8);

		CharBuffer buffer = CharBuffer.wrap(input);
		buffer.limit(5);
		Integer result = charChannel.write(buffer).get();
		assert (result == 2): result;
		assert (output.toString().equals(input.substring(0, 2))): output.toString();
		output.setLength(0);

		buffer.limit(buffer.position() + 5);
		result = charChannel.write(buffer).get();
		assert (result == 5): result;
		
		assert (output.toString().equals(input.substring(2, 7))): output.toString();
		output.setLength(0);

		result = charChannel.write(buffer).get();
		assert (result == 0): result;
		log.trace("stop");
	}
}
