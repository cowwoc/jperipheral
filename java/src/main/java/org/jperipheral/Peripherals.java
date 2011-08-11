package org.jperipheral;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/**
 * Returns peripherals.
 *
 * @author Gili Tzabari
 */
@Singleton
public final class Peripherals
{
	/**
	 * Returns a list of all peripherals.
	 * 
	 * @return a list of all peripherals
	 */
	public List<Peripheral> all()
	{
		List<Peripheral> result = new ArrayList<>();
		for (int i = 1; i <= 256; i++)
			result.add(new SerialPort("COM" + i));
		return result;
	}
}
