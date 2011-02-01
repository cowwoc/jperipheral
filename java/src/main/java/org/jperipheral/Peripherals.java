package org.jperipheral;

import com.google.inject.Singleton;
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
				result.add(new SerialPort("COM" + i));
			}
			catch (PeripheralNotFoundException e)
			{
				// skip
			}
		}
		return result;
	}
}
