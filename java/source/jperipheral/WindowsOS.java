package jperipheral;

import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import java.io.File;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Windows operating system.
 *
 * @author Gili Tzabari
 */
final class WindowsOS implements OperatingSystem
{
	/**
	 * Referenced by native code.
	 */
	private final Logger log = LoggerFactory.getLogger(WindowsOS.class);
	private final File peripheralPath = new File("//./");
	/**
	 * A pointer to the native object.
	 */
	private long nativeObject;

	/**
	 * Creates a new WindowsOS object.
	 */
	@SuppressWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
	WindowsOS()
	{
		try
		{
			this.nativeObject = nativeInitialize();
		}
		catch (IOException e)
		{
			// Unrecoverable error
			throw new AssertionError(e);
		}
	}

	@Override
	public String getPortPath(String name)
	{
		return new File(peripheralPath, name).getAbsolutePath();
	}

	@Override
	protected void finalize() throws Throwable
	{
		try
		{
			nativeDispose();
			nativeObject = 0;
		}
		catch (IOException e)
		{
			// Unrecoverable error
			throw new AssertionError(e);
		}
	}

	/**
	 * Initializes shared I/O structures.
	 *
	 * @return a pointer to the native WindowsOS object
	 * @throws IOException if an I/O error occurs
	 */
	private native long nativeInitialize() throws IOException;

	/**
	 * Releases the native context.
	 *
	 * @throws IOException if an I/O error occurs
	 */
	private native void nativeDispose() throws IOException;
}
