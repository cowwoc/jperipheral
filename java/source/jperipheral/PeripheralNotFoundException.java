package jperipheral;

import java.io.IOException;

/**
 * An attempt was made to access a nonexistent peripheral.
 *
 * @author Gili Tzbari
 */
public class PeripheralNotFoundException extends IOException
{
	private static final long serialVersionUID = 0L;
	private final String name;

	/**
	 * Creates a new PeripheralNotFoundException.
	 *
	 * @param name the peripheral name
	 * @param cause the underlying cause
	 */
	public PeripheralNotFoundException(String name, Throwable cause)
	{
		super("Peripheral not found: " + name, cause);
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
