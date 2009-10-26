package jperipheral;

/**
 * Encapsulates OS-specific data.
 *
 * @author Gili Tzabari
 */
public interface OperatingSystem
{
	/**
	 * Converts a port name to its OS-specific path.
	 * 
	 * @param name the cross-platform comport name
	 * @return the port path
	 */
	String getPortPath(String name);
}
