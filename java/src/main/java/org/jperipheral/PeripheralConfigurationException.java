package org.jperipheral;

import java.io.IOException;

/**
 * Thrown if an error occurs while configuring a peripheral.
 *
 * @author Gili Tzbari
 */
public class PeripheralConfigurationException extends IOException
{
	private static final long serialVersionUID = 0L;
	private final String name;

	/**
	 * Creates a new PeripheralInUseException.
	 *
	 * @param name the peripheral name
	 * @param cause the underlying cause
	 */
	public PeripheralConfigurationException(String name, Throwable cause)
	{
		super("Failed to configure: " + name, cause);
		this.name = name;
	}

	/**
	 * Returns the peripheral name.
	 *
	 * @return the peripheral name
	 */
	public String getName()
	{
		return name;
	}
}
