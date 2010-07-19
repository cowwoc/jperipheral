package jperipheral;

import com.google.inject.Singleton;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Returns peripherals.
 *
 * @author Gili Tzabari
 */
@Singleton
public final class Peripherals
{
	private final Logger log = LoggerFactory.getLogger(Peripherals.class);
	private final OperatingSystem os;
	private final SerialPortProvider serialPorts;

	/**
	 * Creates a new Peripherals object.
	 */
	Peripherals()
	{
		String osName = System.getProperty("os.name").toLowerCase();
		if (osName.startsWith("windows"))
			os = new WindowsOS();
		else
			throw new AssertionError("Unsupported operating system: " + osName);
		serialPorts = new SerialPorts(os);
	}

	/**
	 * Returns all available peripherals.
	 * 
	 * @return a set of opened peripherals
	 */
	public Set<Peripheral> all()
	{
		Set<Peripheral> result = new HashSet<Peripheral>();
		for (int i = 1; i <= 256; i++)
		{
			try
			{
				result.add(serialPorts.getByName("COM" + i));
			}
			catch (PeripheralNotFoundException e)
			{
				// skip
			}
			catch (PeripheralInUseException e)
			{
				if (log.isTraceEnabled())
					log.trace("Peripheral in use: " + e.getName());
			}
		}
		return result;
	}

	/**
	 * Looks up a peripheral by its name.
	 *
	 * If the peripheral is already opened the existing instance will be returned.
	 *
	 * @param name the name
	 * @return the opened peripheral
	 * @throws IllegalArgumentException if name is null
	 * @throws PeripheralNotFoundException if the peripheral does not exist
	 * @throws PeripheralInUseException if the peripheral is being used by another application
	 */
	public Peripheral getByName(String name)
		throws IllegalArgumentException, PeripheralNotFoundException, PeripheralInUseException
	{
		return serialPorts.getByName(name);
	}
}
