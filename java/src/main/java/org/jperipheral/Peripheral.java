package org.jperipheral;

import java.nio.channels.AsynchronousByteChannel;

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
	 * @param group the channel group
	 * @return a AsynchronousChannel for communicating with the peripheral
	 * @throws PeripheralNotFoundException if the comport does not exist
	 * @throws PeripheralInUseException if the peripheral is locked by another application
	 * @throws NullPointerException if group is null
	 */
	AsynchronousByteChannel newAsynchronousChannel(PeripheralChannelGroup group)
		throws PeripheralNotFoundException, PeripheralInUseException;
}
