package jperipheral.build;

import java.util.Set;
import buildinjava.Configuration;
import com.google.common.collect.Sets;
import java.io.File;
import jperipheral.build.configuration.Debug;
import jperipheral.build.configuration.Release;

/**
 * The main project.
 *
 * @author Gili Tzabari
 */
public class JPeripheralProject implements buildinjava.Project
{
	private final File path;
	private Configuration debug;
	private Configuration release;

	/**
	 * Creates a new JPeripheralProject.
	 *
	 * @param path the project path
	 */
	public JPeripheralProject(File path)
	{
		this.path = path;
	}

	@Override
	public Configuration getConfigurationByName(String name)
	{
		if (name.equals("i386.debug"))
		{
			if (debug == null)
				debug = new Debug(path);
			return debug;
		}
		else if (name.equals("i386.release"))
		{
			if (release == null)
				release = new Release(path);
			return release;
		}
		else
			throw new IllegalArgumentException(name + " is not supported");
	}

	@Override
	public File getDirectory()
	{
		return path;
	}

	@Override
	public Set<String> getConfigurations()
	{
		return Sets.newHashSet("i386.debug", "i386.release");
	}

	/**
	 * Creates a new Project.
	 *
	 * @author Gili Tzabari
	 */
	public static class Builder implements buildinjava.Project.Builder
	{
		@Override
		public buildinjava.Project create(File path)
		{
			return new JPeripheralProject(path);
		}
	}
}
