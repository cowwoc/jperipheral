package jperipheral;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jperipheral.nio.channels.CompletionHandler;

/**
 * A canonical serial port object.
 * 
 * @author Gili Tzabari
 * @see http://www.lammertbies.nl/comm/info/RS-232_specs.html
 */
final class CanonicalSerialPort implements SerialPort, ReferenceCounted<IOException>
{
	private final String name;
	private final OperatingSystem os;
	/**
	 * A pointer to the native object.
	 */
	private final long nativeObject;
	private final SerialChannel channel;
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private int baudRate;
	private DataBits dataBits;
	private StopBits stopBits;
	private Parity parity;
	private FlowControl flowControl;
	private boolean closed;
	private boolean configured;
	private int references;
	private List<ReferenceCounted<?>> listeners = new CopyOnWriteArrayList<ReferenceCounted<?>>();

	/**
	 * Creates a new SerialPort.
	 *
	 * @param name the port name
	 * @param os the operating system
	 * @throws PeripheralNotFoundException if the comport does not exist
	 * @throws PeripheralInUseException if the comport is locked by another application
	 * @throws IllegalArgumentException if name is null
	 */
	CanonicalSerialPort(String name, OperatingSystem os)
		throws PeripheralNotFoundException, PeripheralInUseException
	{
		if (name == null)
			throw new IllegalArgumentException("name may not be null");
		this.name = name;
		this.os = os;
		try
		{
			this.nativeObject = nativeOpen(name, os.getPortPath(name));
			this.channel = new SerialChannel(nativeObject);
		}
		catch (PeripheralNotFoundException e)
		{
			throw e;
		}
		catch (PeripheralInUseException e)
		{
			throw e;
		}
	}

	/**
	 * Adds a reference listener.
	 *
	 * @param listener listens for references to this object. Any thrown exceptions are ignored.
	 */
	public void addReferenceListeners(ReferenceCounted<?> listener)
	{
		listeners.add(listener);
	}

	/**
	 * Removes a reference listener.
	 *
	 * @param listener listens for references to this object. Any thrown exceptions are ignored.
	 */
	public void removeReferenceListeners(ReferenceCounted<?> listener)
	{
		listeners.remove(listener);
	}

	@Override
	public int getBaudRate()
	{
		return baudRate;
	}

	@Override
	public DataBits getDataBits()
	{
		return dataBits;
	}

	@Override
	public StopBits getStopBits()
	{
		return stopBits;
	}

	@Override
	public Parity getParity()
	{
		return parity;
	}

	@Override
	public FlowControl getFlowControl()
	{
		return flowControl;
	}

	@Override
	public SerialChannel getAsynchronousChannel()
	{
		return channel;
	}

	@Override
	public void configure(int baudRate, DataBits dataBits, Parity parity, StopBits stopBits,
												FlowControl flowControl) throws IOException
	{
		this.baudRate = baudRate;
		this.dataBits = dataBits;
		this.parity = parity;
		this.stopBits = stopBits;
		this.flowControl = flowControl;
		nativeConfigure(baudRate, dataBits, parity, stopBits, flowControl);
	}

	/**
	 * Indicates if the device has been configured before.
	 *
	 * @return true if the device has been configured before
	 */
	public boolean isConfigured()
	{
		return configured;
	}

	@Override
	public String getName()
	{
		return name;
	}

	@Override
	public <V, A> void execute(final Callable<V> callable, final A attachment,
														 final CompletionHandler<V, A> handler)
	{
		executor.execute(new Runnable()
		{
			@Override
			public void run()
			{
				try
				{
					V result = callable.call();
					handler.completed(result, attachment);
				}
				catch (RuntimeException e)
				{
					handler.failed(e, attachment);
				}
				catch (Exception e)
				{
					handler.failed(e, attachment);
				}
				catch (Error e)
				{
					handler.failed(e, attachment);
				}
			}
		});
	}

	@Override
	public synchronized void beforeReferenceAdded()
	{
		if (references == Integer.MAX_VALUE)
			throw new IllegalStateException("The comport cannot accept any more references");
		++references;
		for (ReferenceCounted<?> listener: listeners)
		{
			try
			{
				listener.beforeReferenceAdded();
			}
			catch (Exception ignored)
			{
			}
		}
	}

	@Override
	public synchronized void afterReferenceRemoved() throws IOException
	{
		if (closed)
			throw new IllegalStateException("SerialPort already closed");
		--references;
		if (references <= 0)
			close();
		for (ReferenceCounted<?> listener: listeners)
		{
			try
			{
				listener.afterReferenceRemoved();
			}
			catch (Exception ignored)
			{
			}
		}
	}

	@Override
	public synchronized boolean isClosed()
	{
		return closed;
	}

	/**
	 * Removes all references and closes the serial port.
	 *
	 * @throws IOException if an I/O error occurs
	 */
	@Override
	public synchronized void close() throws IOException
	{
		if (closed)
			return;
		assert (references == 0): references;
		channel.close();
		closed = true;
		nativeClose();
	}

	/**
	 * Opens the port.
	 *
	 * @param name the port name
	 * @param path the port path
	 * @return the native context associated with the port
	 * @throws PeripheralNotFoundException if the comport does not exist
	 * @throws PeripheralInUseException if the comport is locked by another application
	 */
	private native long nativeOpen(String name, String path) throws PeripheralNotFoundException,
																																	PeripheralInUseException;

	/**
	 * DESIGN: We can't expose this method until we verify whether it is available on other platforms.
	 */
	private native void printStatus();

	/**
	 * Sets the port configuration.
	 *
	 * @param baudRate the baud rate
	 * @param dataBits the number of data bits per word
	 * @param parity the parity mechanism to use
	 * @param stopBits the number of stop bits to use
	 * @param flowControl the flow control to use
	 * @throws IOException if an I/O error occurs
	 */
	private native void nativeConfigure(int baudRate, DataBits dataBits, Parity parity,
																			StopBits stopBits, FlowControl flowControl) throws IOException;

	/**
	 * Closes the port.
	 *
	 * @throws IOException if an I/O error occurs
	 */
	private native void nativeClose() throws IOException;

	@Override
	public String toString()
	{
		return name + "[" + baudRate + " " + dataBits + "-" + parity + "-" + stopBits + " "
					 + flowControl + "]";
	}
}
