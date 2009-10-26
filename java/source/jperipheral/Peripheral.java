package jperipheral;

import java.io.IOException;
import jperipheral.nio.channels.AsynchronousByteChannel;

/**
 * A peripheral.
 *
 * @author Gili Tzabari
 * @see #submit(java.util.concurrent.Callable)
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
	 * Returns the AsynchronousChannel used to communicate with the peripheral. This method will always
	 * return the same AsynchronousChannel for the same peripheral.
	 *
	 * @return an AsynchronousChannel used to communicate with the peripheral
	 */
	AsynchronousByteChannel getAsynchronousChannel();

	/**
	 * Closes the peripheral.
	 *
	 * @throws IOException if an I/O error occurs
	 */
	void close() throws IOException;
}
