package jperipheral;

import java.io.IOException;

/**
 * An attempt was made to access a peripheral that is locked by another application.
 *
 * @author Gili Tzbari
 */
public class PeripheralInUseException extends IOException
{
	private static final long serialVersionUID = 0L;
	private final String name;

	/**
	 * Creates a new PeripheralInUseException.
	 *
	 * @param name the peripheral name
	 * @param cause the underlying cause
	 */
	public PeripheralInUseException(String name, Throwable cause)
	{
		super("Peripheral in use: " + name, cause);
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
