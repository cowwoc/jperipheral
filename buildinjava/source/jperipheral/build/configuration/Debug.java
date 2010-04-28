package jperipheral.build.configuration;

import java.io.File;

/**
 * The debug configuration.
 *
 * @author Gili Tzabari
 */
public class Debug extends AbstractConfiguration
{
	/**
	 * Creates a Debug configuration.
	 * 
	 * @param projectPath the project path
	 */
	public Debug(final File projectPath)
	{
		super("i386.debug", projectPath);
	}
}
