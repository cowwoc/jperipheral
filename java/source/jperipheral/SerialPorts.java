package jperipheral;

import com.google.common.base.Function;
import com.google.common.collect.ComputationException;
import com.google.common.collect.MapMaker;
import java.util.Map;

/**
 * Returns serial ports.
 * 
 * @author Gili Tzabari
 */
public class SerialPorts implements SerialPortProvider
{
	private final OperatingSystem os;
	private final Map<String, CanonicalSerialPort> ports = new MapMaker().concurrencyLevel(1).weakValues().
		makeComputingMap(new Function<String, CanonicalSerialPort>()
	{
		@Override
		public CanonicalSerialPort apply(String name)
		{
			try
			{
				return new CanonicalSerialPort(name, os);
			}
			catch (PeripheralNotFoundException e)
			{
				throw new ComputationException(e);
			}
			catch (PeripheralInUseException e)
			{
				throw new ComputationException(e);
			}
		}
	});

	/**
	 * Creates a new SerialPorts object.
	 *
	 * @param os an instance of {@code OperatingSystem}
	 */
	SerialPorts(OperatingSystem os)
	{
		this.os = os;
	}

	public SerialPort getByName(final String name)
		throws IllegalArgumentException, PeripheralNotFoundException, PeripheralInUseException
	{
		if (name == null)
			throw new IllegalArgumentException("name may not be null");
		try
		{
			final CanonicalSerialPort canonicalInstance = ports.get(name);
			canonicalInstance.addReferenceListeners(new ReferenceCounted<RuntimeException>()
			{
				@Override
				public void beforeReferenceAdded()
				{
				}

				@Override
				public void afterReferenceRemoved() throws RuntimeException
				{
					if (canonicalInstance.isClosed())
						ports.remove(name);
				}
			});
			return new SerialPortReference(canonicalInstance);
		}
		catch (ComputationException e)
		{
			Throwable cause = e.getCause();
			if (cause instanceof PeripheralNotFoundException)
				throw (PeripheralNotFoundException) cause;
			if (cause instanceof PeripheralInUseException)
				throw (PeripheralInUseException) cause;
			if (cause instanceof RuntimeException)
				throw (RuntimeException) cause;
			if (cause instanceof Error)
				throw (Error) cause;
			throw new AssertionError(e);
		}
	}
}
