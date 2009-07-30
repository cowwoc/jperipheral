package jperipheral;

import java.util.Set;

/**
 * Encapsulates OS-specific data.
 *
 * @author Gili Tzabari
 */
public abstract class OperatingSystem
{
	private static OperatingSystem instance;

	/**
	 * Returns the current operating system.
	 *
	 * @return the current operating system
	 */
	public static synchronized OperatingSystem getCurrent()
	{
		if (instance == null)
		{
			String osName = System.getProperty("os.name").toLowerCase();
			if (osName.startsWith("windows"))
				instance = new WindowsOS();
			else if (osName.startsWith("linux"))
				instance = new LinuxOS();
			else
				throw new AssertionError("Unsupported operating system");
		}
		return instance;
	}

	/**
	 * Returns all comports.
	 *
	 * @return all comports
	 */
	public abstract Set<ComPort> getComPorts();

	/**
	 * Converts a port name to its OS-specific path.
	 * 
	 * @param name the cross-platform comport name
	 * @return the port path
	 */
	public abstract String getPortPath(String name);
}
