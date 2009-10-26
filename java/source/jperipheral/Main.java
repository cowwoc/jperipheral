package jperipheral;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
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

	private int unsigned(int signed)
	{
		return signed + 128;
	}

	private void run() throws Exception
	{
		flush();
		send("version");
		send("get 0");
		send("get state", "select 1");
		send("select 1");
		send("select a", "get state");
	}

	private void flush() throws Exception
	{
		SerialPort port = (SerialPort) peripherals.getByName(portName);
		port.configure(9600, SerialPort.DataBits.EIGHT, SerialPort.Parity.NONE, SerialPort.StopBits.ONE,
			SerialPort.FlowControl.NONE);
		channel = AsynchronousByteCharChannel.open(port.getAsynchronousChannel(),
			Charset.forName("UTF-8"));
		System.out.println("Flushing...");
		try
		{
			while (true)
				System.out.println("got : " + channel.readLine().get(1, TimeUnit.SECONDS));
		}
		catch (TimeoutException e)
		{
			// eventually there is no more data to read...
		}
		finally
		{
			//System.out.println("Closing\n\n");
			port.close();
		}
	}

	private void send(String... lines) throws Exception
	{
		SerialPort port = (SerialPort) peripherals.getByName(portName);
		port.configure(9600, SerialPort.DataBits.EIGHT, SerialPort.Parity.NONE, SerialPort.StopBits.ONE,
			SerialPort.FlowControl.NONE);
		channel = AsynchronousByteCharChannel.open(port.getAsynchronousChannel(),
			Charset.forName("UTF-8"));
		try
		{
			for (String line: lines)
			{
				System.out.println("sending: " + line);
				channel.write(CharBuffer.wrap(line + "\r"), false).get(1, TimeUnit.SECONDS);
				System.out.println("got : " + channel.readLine().get(1000, TimeUnit.SECONDS));
			}
		}
		finally
		{
//			System.out.println("Closing\n\n");
			port.close();
		}
	}
}
