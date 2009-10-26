package jperipheral;

import java.io.IOException;
import java.util.concurrent.Callable;
import jperipheral.nio.channels.CompletionHandler;

/**
 * A serial port.
 * 
 * @author Gili Tzabari
 * @see http://www.lammertbies.nl/comm/info/RS-232_specs.html
 */
public interface SerialPort extends Peripheral
{
	/**
	 * Returns the baud rate being used.
	 *
	 * @return the baud rate being used
	 */
	int getBaudRate();

	/**
	 * Returns the number of data bits being used.
	 *
	 * @return the number of data bits being used
	 */
	DataBits getDataBits();

	/**
	 * Returns the number of stop bits being used.
	 *
	 * @return the number of stop bits being used
	 */
	StopBits getStopBits();

	/**
	 * Returns the parity type being used.
	 *
	 * @return the parity type being used
	 */
	Parity getParity();

	/**
	 * Returns the flow control mechanism being used.
	 * 
	 * @return the flow control mechanism being used
	 */
	FlowControl getFlowControl();

	/**
	 * Configures the serial port connection.
	 *
	 * @param baudRate the baud rate
	 * @param dataBits the number of data bits per word
	 * @param parity the parity mechanism to use
	 * @param stopBits the number of stop bits to use
	 * @param flowControl the flow control to use
	 * @throws IOException if an I/O error occurs
	 */
	void configure(int baudRate, DataBits dataBits, Parity parity, StopBits stopBits, FlowControl flowControl)
		throws IOException;

	/**
	 * Invokes a task on the comport dispatch thread.
	 *
	 * @param <V> The result type returned by the completion handler
	 * @param <A> The user object to pass to the completion handler
	 * @param callable the operation to execute
	 * @param attachment The object to attach to the I/O operation; can be {@code null}
	 * @param handler The completion handler
	 */
	<V, A> void execute(Callable<V> callable, A attachment, CompletionHandler<V, A> handler);

	/**
	 * Indicates if the peripheral is closed.
	 *
	 * @return true if the peripheral is closed
	 */
	boolean isClosed();

	/**
	 * Closes the serial port.
	 *
	 * @throws IOException if an I/O error occurs
	 */
	void close() throws IOException;

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
