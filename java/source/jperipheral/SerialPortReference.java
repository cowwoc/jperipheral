package jperipheral;

import java.io.IOException;
import java.util.concurrent.Callable;
import jperipheral.nio.channels.CompletionHandler;

/**
 * A SerialPort decorator that ensures that the port is only closed once per reference.
 * 
 * @author Gili Tzabari
 */
final class SerialPortReference implements SerialPort
{
	private boolean closed;
	private final CanonicalSerialPort delegate;

	/**
	 * Creates a new SerialPortReference.
	 * 
	 * @param delegate the canonical serial port
	 */
	public SerialPortReference(CanonicalSerialPort delegate)
	{
		this.delegate = delegate;
		this.delegate.beforeReferenceAdded();
	}

	public synchronized void close() throws IOException
	{
		if (closed)
			return;
		closed = true;
		delegate.afterReferenceRemoved();
	}

	public StopBits getStopBits()
	{
		return delegate.getStopBits();
	}

	public Parity getParity()
	{
		return delegate.getParity();
	}

	public String getName()
	{
		return delegate.getName();
	}

	public FlowControl getFlowControl()
	{
		return delegate.getFlowControl();
	}

	public DataBits getDataBits()
	{
		return delegate.getDataBits();
	}

	public int getBaudRate()
	{
		return delegate.getBaudRate();
	}

	public SerialChannel getAsynchronousChannel()
	{
		return delegate.getAsynchronousChannel();
	}

	public <V, A> void execute(Callable<V> callable, A attachment,
														 CompletionHandler<V, A> handler)
	{
		delegate.execute(callable, attachment, handler);
	}

	public void configure(int baudRate, DataBits dataBits, Parity parity, StopBits stopBits,
												FlowControl flowControl) throws IOException
	{
		delegate.configure(baudRate, dataBits, parity, stopBits, flowControl);
	}

	public String toString()
	{
		return delegate.toString();
	}

	public boolean isClosed()
	{
		return delegate.isClosed();
	}
}
