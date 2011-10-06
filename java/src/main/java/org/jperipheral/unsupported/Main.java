package org.jperipheral.unsupported;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jperipheral.AsynchronousByteCharChannel;
import org.jperipheral.GuiceModule;
import org.jperipheral.PeripheralChannelGroup;
import org.jperipheral.SerialChannel;
import org.jperipheral.SerialPort;
import org.jperipheral.SerialPort.BaudRate;
import org.jperipheral.SerialPort.DataBits;
import org.jperipheral.SerialPort.FlowControl;
import org.jperipheral.SerialPort.Parity;
import org.jperipheral.SerialPort.StopBits;

/**
 * @author Gili Tzabari
 */
public class Main
{
	private AsynchronousByteCharChannel channel;
	private final ExecutorService executor =
		Executors.newFixedThreadPool(2, new ThreadFactoryBuilder().setDaemon(true).
		setNameFormat(Main.class.getSimpleName() + "-%d").build());
	private final PeripheralChannelGroup channelGroup = new PeripheralChannelGroup(executor);
	private final String portName = "COM1";

	public Main()
	{
		Injector injector = Guice.createInjector(new GuiceModule());
	}

	public static void main(String[] args) throws Exception
	{
		Main main = new Main();
		main.run();
	}

	private void run() throws Exception
	{
		long first = System.currentTimeMillis();
		for (int i = 0; i < 1000; ++i)
		{
			send(i + ": 0123456789");
			System.out.println("got: " + receive());
		}

		System.out.println("Time Elapsed: " + ((System.currentTimeMillis() - first) / 10000 - 0.83));
//		flush();
//		send("version");
//		flush();
//		send("get 0");
//		flush();
//		send("get state", "select 1");
//		flush();
//		send("select 1");
//		flush();
//		send("select a", "get state");
//		flush();
//		while (true)
//		{
//			flush();
//			try
//			{
//				send("request" + Math.random());
//			}
//			catch (TimeoutException e)
//			{
//				// do nothing
//			}
//		}
	}

	private void flush() throws Exception
	{
		SerialPort port = new SerialPort(portName);
		try (SerialChannel byteChannel = port.newAsynchronousChannel(channelGroup))
		{
			byteChannel.configure(BaudRate._115200, DataBits.EIGHT, Parity.NONE, StopBits.ONE,
				FlowControl.NONE);
			channel = AsynchronousByteCharChannel.open(byteChannel, Charset.forName("UTF-8"),
				channelGroup);
			System.out.println("Flushing...");
			while (true)
				System.out.println("got: " + channel.readLine().get(0, TimeUnit.SECONDS));
		}
		catch (TimeoutException e)
		{
			// eventually there is no more data to read...
		}
	}

	private void send(String... lines) throws Exception
	{
		SerialPort port = new SerialPort(portName);
		try (SerialChannel byteChannel = port.newAsynchronousChannel(channelGroup))
		{
			byteChannel.configure(BaudRate._115200, DataBits.EIGHT, Parity.NONE,
				StopBits.ONE, FlowControl.NONE);
			channel = AsynchronousByteCharChannel.open(byteChannel, Charset.forName("UTF-8"),
				channelGroup);
			for (String line: lines)
			{
//				System.out.println("sending: " + line);
				channel.write(CharBuffer.wrap(line + "\r")).get(30, TimeUnit.SECONDS);
//				System.out.println("got : " + channel.readLine().get(30, TimeUnit.SECONDS));
			}
		}
	}

	private String receive() throws Exception
	{
		SerialPort port = new SerialPort(portName);

		try (SerialChannel byteChannel = port.newAsynchronousChannel(channelGroup))
		{
			byteChannel.configure(BaudRate._115200, DataBits.EIGHT, Parity.NONE,
				StopBits.ONE, FlowControl.NONE);
			channel = AsynchronousByteCharChannel.open(byteChannel, Charset.forName("UTF-8"),
				channelGroup);
//				System.out.println("sending: " + line);
			return channel.readLine().get(30, TimeUnit.SECONDS);
//				System.out.println("got : " + channel.readLine().get(30, TimeUnit.SECONDS));
		}
	}
}
