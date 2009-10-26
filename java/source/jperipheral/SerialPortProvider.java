package jperipheral;

/**
 * Returns serial ports.
 * 
 * @author Gili Tzabari
 */
public interface SerialPortProvider
{
	/**
	 * Looks up a SerialPort by its name.
	 *
	 * If the port is already opened the existing instance will be returned.
	 *
	 * @param name the name
	 * @return the opened serial port
	 * @throws IllegalArgumentException if name is null
	 * @throws PeripheralNotFoundException if the serial port does not exist
	 * @throws PeripheralInUseException if the serial port is being used by another application
	 */
	SerialPort getByName(String name)
		throws IllegalArgumentException, PeripheralNotFoundException, PeripheralInUseException;
}
