package jperipheral;

import java.io.IOException;

/**
 * An attempt was made to access a comport that is locked by another application.
 *
 * @author Gili Tzbari
 */
public class PortInUseException extends IOException
{
	private static final long serialVersionUID = 0L;
	private final String port;

	/**
	 * Creates a new PortInUseException.
	 *
	 * @param port the port name
	 * @param cause the underlying cause
	 */
	public PortInUseException(String port, Throwable cause)
	{
		super("Port in use: " + port, cause);
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
