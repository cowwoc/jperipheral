package org.jperipheral;

import com.google.common.base.Preconditions;

/**
 * A serial port.
 * <p/>
 * @author Gili Tzabari
 * @see http://www.lammertbies.nl/comm/info/RS-232_specs.html
 */
public final class SerialPort implements Peripheral
{
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
	private final String name;

	/**
	 * Creates a new SerialPort.
	 *
	 * @param name the port name
	 * @throws NullPointerException if name is null
	 * @throws IllegalStateException if name.trim().isEmpty()
	 */
	public SerialPort(String name)
		throws PeripheralNotFoundException
	{
		Preconditions.checkNotNull(name, "name may not be null");
		Preconditions.checkArgument(!name.trim().isEmpty(), "name may not be an empty string");

		this.name = name;
	}

	@Override
	public SerialChannel newAsynchronousChannel(PeripheralChannelGroup group)
		throws PeripheralNotFoundException, PeripheralInUseException
	{
		SerialChannel result = new SerialChannel(this, group);
		group.addChannel(result);
		return result;
	}

	@Override
	public String getName()
	{
		return name;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
