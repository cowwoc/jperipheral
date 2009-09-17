package jperipheral;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Windows operating system.
 *
 * @author Gili Tzabari
 */
public class WindowsOS extends OperatingSystem implements ResourceLifecycleListener
{
	/**
	 * The native context associated with the object.
	 */
	private long nativeContext;
	/**
	 * The number of open resources.
	 */
	private long resourceCount;

	/**
	 * Returns the path of the device directory.
	 *
	 * @return the path of the device directory
	 */
	private File getDeviceDirectory()
	{
		return new File("//./");
	}

	@Override
	public Set<ComPort> getComPorts()
	{
		Set<ComPort> result = new HashSet<ComPort>();
		for (int i = 1; i <= 256; i++)
		{
			try
			{
				result.add(SerialPort.getByName("COM" + i));
			}
			catch (PortNotFoundException e)
			{
				// skip
			}
			catch (PortInUseException e)
			{
				// skip
			}
		}
		return result;
	}

	@Override
	public String getPortPath(String name)
	{
		return new File(getDeviceDirectory(), name).getAbsolutePath();
	}

	@Override
	public synchronized void beforeResourceCreated()
	{
		if (resourceCount == 0)
		{
			try
			{
				this.nativeContext = nativeInitialize();
			}
			catch (IOException e)
			{
				// Unrecoverable error
				throw new AssertionError(e);
			}
		}
		assert (resourceCount < Integer.MAX_VALUE): resourceCount;
		++resourceCount;
	}

	@Override
	public synchronized void afterResourceDestroyed()
	{
		assert (resourceCount > 0): resourceCount;
		--resourceCount;
		if (resourceCount <= 0)
		{
			try
			{
				nativeDispose();
				this.nativeContext = 0;
			}
			catch (IOException e)
			{
				// Unrecoverable error
				throw new AssertionError(e);
			}
		}
	}

	/**
	 * Initializes shared I/O structures.
	 *
	 * @return a pointer to the native context
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
