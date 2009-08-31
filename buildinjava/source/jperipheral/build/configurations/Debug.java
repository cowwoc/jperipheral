package jperipheral.build.configurations;

import java.io.File;

/**
 * The debug configuration.
 *
 * @author Gili Tzabari
 */
public class Debug extends Common
{
	/**
	 * Creates a Debug configuration.
	 * 
	 * @param projectPath the project path
	 */
	public Debug(File projectPath)
	{
		super("debug", projectPath);
	}
}
