package jperipheral;

import java.io.IOException;
import jperipheral.nio.channels.AsynchronousByteChannel;
import java.util.Set;

/**
 * A peripheral port.
 * 
 * @author Gili Tzabari
 */
public abstract class ComPort
{
	/**
	 * Returns all comports.
	 * 
	 * @return all comports
	 */
	public static Set<ComPort> all()
	{
		return OperatingSystem.getCurrent().getComPorts();
	}

	/**
	 * Returns the port name.
	 *
	 * @return the port name
	 */
	public abstract String getName();

	/**
	 * Returns the AsynchronousChannel used to communicate with the comport. This method will always return the
	 * same AsynchronousChannel for the same port.
	 *
	 * @return an AsynchronousChannel used to communicate with the comport
	 */
	public abstract AsynchronousByteChannel getAsynchronousChannel();

	/**
	 * Closes the comport.
	 *
	 * @throws IOException if an I/O error occurs
	 */
	public abstract void close() throws IOException;
}
