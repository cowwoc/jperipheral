package jperipheral;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import jperipheral.ComPort;

/**
 * Windows operating system.
 *
 * @author Gili Tzabari
 */
public class LinuxOS extends OperatingSystem
{
	/**
	 * Returns the path of the device directory.
	 *
	 * @return the path of the device directory
	 */
	private File getDeviceDirectory()
	{
		return new File("/dev/");
	}

	/**
	 * Returns a list of the possible serial port name prefixes.
	 *
	 * @return a list of the possible serial port name prefixes
	 */
	protected Set<String> getSerialPrefixes()
	{
		Set<String> result = new HashSet<String>();
		result.add("ttyS"); // normal serial devices
		result.add("ttySA"); // iPaq devices
		result.add("ttyUSB"); // USB devices
		result.add("rfcomm"); // bluetooth devices
		result.add("ttyircomm"); // IrDA devices
		return result;
	}

	@Override
	public Set<ComPort> getComPorts()
	{
		Set<String> prefixes = getSerialPrefixes();
		Set<ComPort> result = new HashSet<ComPort>();

		nextCandidate:
		for (String candidate: getDeviceDirectory().list())
		{
			for (String prefix: prefixes)
				if (!candidate.startsWith(prefix))
					break nextCandidate;
			try
			{
				result.add(SerialPort.getByName(candidate));
			}
			catch (PortNotFoundException e)
			{
				// skip
			}
			catch (PortInUseException e)
			{
				// skip
			}
		}
		return result;
	}

	@Override
	public String getPortPath(String name)
	{
		return getDeviceDirectory() + name;
	}
}
