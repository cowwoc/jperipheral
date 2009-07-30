package jperipheral;

/**
 * An attempt was made to access a comport that is locked by another application.
 *
 * @author Gili Tzbari
 */
public class PortInUseException extends Exception
{
	private static final long serialVersionUID = 0L;

	/**
	 * Creates a new PortInUseException.
	 *
	 * @param message the detailed message
	 * @param cause the underlying cause
	 */
	public PortInUseException(String message, Throwable cause)
	{
		super(message, cause);
	}
}
