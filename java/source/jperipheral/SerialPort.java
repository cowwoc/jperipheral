package jperipheral;

import java.io.IOException;

/**
 * A serial port.
 * 
 * @author Gili Tzabari
 * @see http://www.lammertbies.nl/comm/info/RS-232_specs.html
 */
public class SerialPort extends ComPort
{
	private final String name;
	/**
	 * The native context associated with the object.
	 */
	private final long nativeContext;
	private int baudRate;
	private DataBits dataBits;
	private StopBits stopBits;
	private Parity parity;
	private FlowControl flowControl;
	private final SerialChannel channel;
	private boolean closed;

	/**
	 * Returns a SerialPort by its name.
	 *
	 * @param name the name
	 * @return the serial port
	 * @throws IllegalArgumentException if name is null
	 * @throws PortNotFoundException if the comport does not exist
	 * @throws PortInUseException if the comport is locked by another application
	 */
	public static SerialPort getByName(String name)
		throws IllegalArgumentException, PortNotFoundException, PortInUseException
	{
		if (name == null)
			throw new IllegalArgumentException("name may not be null");
		return new SerialPort(name);
	}

	/**
	 * Creates a new SerialPort.
	 * 
	 * @param name the port name
	 * @throws PortNotFoundException if the comport does not exist
	 * @throws PortInUseException if the comport is locked by another application
	 * @throws IllegalArgumentException if name is null
	 */
	private SerialPort(String name) throws PortNotFoundException, PortInUseException
	{
		if (name == null)
			throw new IllegalArgumentException("name may not be null");
		OperatingSystem os = OperatingSystem.getCurrent();
		ResourceLifecycleListener listener = (ResourceLifecycleListener) os;
		listener.beforeResourceCreated();

		this.name = name;
		try
		{
			this.nativeContext = nativeOpen(name, os.getPortPath(name));
			this.channel = new SerialChannel(nativeContext);
		}
		catch (PortNotFoundException e)
		{
			listener.afterResourceDestroyed();
			throw e;
		}
		catch (PortInUseException e)
		{
			listener.afterResourceDestroyed();
			throw e;
		}
		catch (RuntimeException e)
		{
			listener.afterResourceDestroyed();
			throw e;
		}
	}

	/**
	 * Returns the baud rate being used.
	 *
	 * @return the baud rate being used
	 */
	public int getBaudRate()
	{
		return baudRate;
	}

	/**
	 * Returns the number of data bits being used.
	 *
	 * @return the number of data bits being used
	 */
	public DataBits getDataBits()
	{
		return dataBits;
	}

	/**
	 * Returns the number of stop bits being used.
	 *
	 * @return the number of stop bits being used
	 */
	public StopBits getStopBits()
	{
		return stopBits;
	}

	/**
	 * Returns the parity type being used.
	 *
	 * @return the parity type being used
	 */
	public Parity getParity()
	{
		return parity;
	}

	/**
	 * Returns the flow control mechanism being used.
	 * 
	 * @return the flow control mechanism being used
	 */
	public FlowControl getFlowControl()
	{
		return flowControl;
	}

	@Override
	public SerialChannel getAsynchronousChannel()
	{
		return channel;
	}

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

	@Override
	public String getName()
	{
		return name;
	}

	@Override
	public synchronized void close() throws IOException
	{
		if (closed)
			return;
		channel.close();
		closed = true;
		nativeClose();
		ResourceLifecycleListener listener = (ResourceLifecycleListener) OperatingSystem.getCurrent();
		listener.afterResourceDestroyed();
	}

	/**
	 * Opens the port.
	 *
	 * @param name the port name
	 * @param path the port path
	 * @return the native context associated with the port
	 * @throws PortNotFoundException if the comport does not exist
	 * @throws PortInUseException if the comport is locked by another application
	 */
	public native long nativeOpen(String name, String path) throws PortNotFoundException, PortInUseException;

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
		return name + "[" + baudRate + " " + dataBits + "-" + parity + "-" + stopBits + " " + flowControl + "]";
	}

	/**
	 * The number of data bits in a word.
	 */
	public enum DataBits
	{
		/**
		 * 5 data bits.
		 */
		FIVE
		{
			@Override
			public String toString()
			{
				return "5";
			}
		},
		/**
		 * 6 data bits.
		 */
		SIX
		{
			@Override
			public String toString()
			{
				return "6";
			}
		},
		/**
		 * 7 data bits.
		 */
		SEVEN
		{
			@Override
			public String toString()
			{
				return "7";
			}
		},
		/**
		 * 8 data bits.
		 */
		EIGHT
		{
			@Override
			public String toString()
			{
				return "8";
			}
		}
	}

	/**
	 * The flow control used by the port.
	 */
	public enum FlowControl
	{
		/**
		 * Hardware flow control.
		 */
		RTS_CTS
		{
			@Override
			public String toString()
			{
				return "RTS/CTS";
			}
		},
		/**
		 * Software flow control.
		 */
		XON_XOFF
		{
			@Override
			public String toString()
			{
				return "XON/XOFF";
			}
		},
		/**
		 * No flow control.
		 */
		NONE
		{
			@Override
			public String toString()
			{
				return "NONE";
			}
		}
	}

	/**
	 * The minimum period of time the line must be idle at the end of each word.
	 */
	public enum StopBits
	{
		/**
		 * 1 bit.
		 */
		ONE
		{
			@Override
			public String toString()
			{
				return "1";
			}
		},
		/**
		 * 1.5 bits.
		 */
		ONE_POINT_FIVE
		{
			@Override
			public String toString()
			{
				return "1.5";
			}
		},
		/**
		 * 2 bits.
		 */
		TWO
		{
			@Override
			public String toString()
			{
				return "2";
			}
		}
	}

	/**
	 * The parity mode used to detect errors.
	 */
	public enum Parity
	{
		/**
		 * Indicates that the number of bits in a word must be even.
		 */
		EVEN
		{
			@Override
			public String toString()
			{
				return "E";
			}
		},
		/**
		 * Indicates that the parity bit is always 1.
		 */
		MARK
		{
			@Override
			public String toString()
			{
				return "M";
			}
		},
		/**
		 * Indicates that parity checking is disabled.
		 */
		NONE
		{
			@Override
			public String toString()
			{
				return "N";
			}
		},
		/**
		 * Indicates that the number of bits in a word must be odd.
		 */
		ODD
		{
			@Override
			public String toString()
			{
				return "O";
			}
		},
		/**
		 * Indicates that the parity bit is always 0.
		 */
		SPACE
		{
			@Override
			public String toString()
			{
				return "S";
			}
		}
	}
}
