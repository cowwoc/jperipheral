package jperipheral;

import java.io.IOException;

/**
 * An attempt was made to access a nonexistent comport.
 *
 * @author Gili Tzbari
 */
public class PortNotFoundException extends IOException
{
	private static final long serialVersionUID = 0L;
	private final String port;

	/**
	 * Creates a new PortNotFoundException.
	 *
	 * @param port the port name
	 * @param cause the underlying cause
	 */
	public PortNotFoundException(String port, Throwable cause)
	{
		super("Port not found: " + port, cause);
		this.port = port;
	}

	/**
	 * Returns the port name.
	 *
	 * @return the port name
	 */
	public String getPort()
	{
		return port;
	}
}
