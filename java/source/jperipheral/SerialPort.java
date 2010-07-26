package jperipheral;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jperipheral.nio.channels.CompletionHandler;

/**
 * A serial port.
 * 
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
	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	/**
	 * Creates a new SerialPort.
	 *
	 * @param name the port name
	 * @throws PeripheralNotFoundException if the comport does not exist
	 * @throws IllegalArgumentException if name is null
	 */
	public SerialPort(String name)
		throws PeripheralNotFoundException
	{
		if (name == null)
			throw new IllegalArgumentException("name may not be null");
		this.name = name;
		try
		{
			SerialChannel channel = new SerialChannel(this);
			channel.close();
		}
		catch (PeripheralInUseException e)
		{
			// the serial port exists, but is in use
		}
		catch (PeripheralNotFoundException e)
		{
			throw e;
		}
		catch (IOException e)
		{
			// We didn't write anything so close() shouldn't fail
			throw new AssertionError(e);
		}
	}

	@Override
	public SerialChannel newAsynchronousChannel()
		throws PeripheralNotFoundException, PeripheralInUseException
	{
		return new SerialChannel(this);
	}

	@Override
	public String getName()
	{
		return name;
	}

	/**
	 * Invokes a task on the comport dispatch thread.
	 *
	 * @param <V> The result type returned by the completion handler
	 * @param <A> The user object to pass to the completion handler
	 * @param callable the operation to execute
	 * @param attachment The object to attach to the I/O operation; can be {@code null}
	 * @param handler The completion handler
	 */
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
	public String toString()
	{
		return name;
	}
}
