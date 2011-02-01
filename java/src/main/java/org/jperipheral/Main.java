package org.jperipheral;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 *
 * @author Gili
 */
public class Main
{
	private AsynchronousByteCharChannel channel;
	private final Peripherals peripherals;
	private final String portName = "COM5";

	public Main()
	{
		Injector injector = Guice.createInjector(new GuiceModule());
		this.peripherals = injector.getInstance(Peripherals.class);
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
			send("0123456789");
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
		SerialChannel byteChannel = port.newAsynchronousChannel();
		try
		{
			byteChannel.configure(9600, SerialPort.DataBits.EIGHT, SerialPort.Parity.NONE,
				SerialPort.StopBits.ONE, SerialPort.FlowControl.NONE);
			channel = AsynchronousByteCharChannel.open(byteChannel, Charset.forName("UTF-8"));
			System.out.println("Flushing...");
			while (true)
				System.out.println("got: " + channel.readLine().get(0, TimeUnit.SECONDS));
		}
		catch (TimeoutException e)
		{
			// eventually there is no more data to read...
		}
		finally
		{
			byteChannel.close();
		}
	}

	private void send(String... lines) throws Exception
	{
		SerialPort port = new SerialPort(portName);
		SerialChannel byteChannel = port.newAsynchronousChannel();
		try
		{
			byteChannel.configure(9600, SerialPort.DataBits.EIGHT, SerialPort.Parity.NONE,
				SerialPort.StopBits.ONE, SerialPort.FlowControl.NONE);
			channel = AsynchronousByteCharChannel.open(byteChannel, Charset.forName("UTF-8"));
			for (String line: lines)
			{
//				System.out.println("sending: " + line);
				channel.write(CharBuffer.wrap(line + "\r"), false).get(30, TimeUnit.SECONDS);
//				System.out.println("got : " + channel.readLine().get(30, TimeUnit.SECONDS));
			}
		}
		finally
		{
			byteChannel.close();
		}
	}
}
