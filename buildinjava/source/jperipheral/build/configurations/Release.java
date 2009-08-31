package jperipheral.build.configurations;

import java.io.File;

/**
 * The debug configuration.
 *
 * @author Gili Tzabari
 */
public class Release extends Common
{
	/**
	 * Creates a Release configuration.
	 * 
	 * @param projectPath the project path
	 */
	public Release(File projectPath)
	{
		super("release", projectPath);
	}
}
