package jperipheral;

/**
 * An attempt was made to access a nonexistent comport.
 *
 * @author Gili Tzbari
 */
public class PortNotFoundException extends Exception
{
	private static final long serialVersionUID = 0L;

	/**
	 * Creates a new PortNotFoundException.
	 *
	 * @param message the detailed message
	 * @param cause the underlying cause
	 */
	public PortNotFoundException(String message, Throwable cause)
	{
		super(message, cause);
	}
}
