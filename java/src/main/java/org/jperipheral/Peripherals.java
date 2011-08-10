package org.jperipheral;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
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
	 * Returns a list of all peripherals.
	 * 
	 * @return a list of all peripherals
	 */
	public List<Peripheral> all()
	{
		List<Peripheral> result = new ArrayList<>();
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
