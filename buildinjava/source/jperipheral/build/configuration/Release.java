package jperipheral.build.configuration;

import java.io.File;

/**
 * The debug configuration.
 *
 * @author Gili Tzabari
 */
public class Release extends AbstractConfiguration
{
	/**
	 * Creates a Release configuration.
	 * 
	 * @param projectPath the project path
	 */
	public Release(final File projectPath)
	{
		super("i386.release", projectPath);
	}
}
