package org.jperipheral;

import org.jperipheral.nio.channels.AsynchronousByteChannel;

/**
 * A peripheral.
 *
 * @author Gili Tzabari
 */
public interface Peripheral
{
	/**
	 * Returns the peripheral name.
	 *
	 * @return the peripheral name
	 */
	String getName();

	/**
	 * Returns a new AsynchronousChannel for communicating with the peripheral.
	 *
	 * @return a AsynchronousChannel for communicating with the peripheral
	 * @throws PeripheralNotFoundException if the comport does not exist
	 * @throws PeripheralInUseException if the peripheral is locked by another application
	 */
	AsynchronousByteChannel newAsynchronousChannel()
		throws PeripheralNotFoundException, PeripheralInUseException;
}
