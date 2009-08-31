package jperipheral.build;

import jperipheral.build.configurations.Debug;
import jperipheral.build.configurations.Release;
import buildinjava.BuildException;
import buildinjava.Configuration;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

/**
 * The main project.
 *
 * @author Gili Tzabari
 */
public class Project implements buildinjava.Project
{
	private final File path;
	private Configuration debug;
	private Configuration release;

	/**
	 * Creates a new Project.
	 *
	 * @param path the project path
	 */
	public Project(File path)
	{
		this.path = path;
	}

	@Override
	public Collection<Class<? extends Configuration>> getConfigurations()
	{
		Collection<Class<? extends Configuration>> result = new ArrayList<Class<? extends Configuration>>();
		result.add(Debug.class);
		result.add(Release.class);
		return result;
	}

	@Override
	public Class<? extends Configuration> getDefaultConfiguration()
	{
		return Debug.class;
	}

	@Override
	public File getPath() throws BuildException
	{
		return path;
	}

	@Override
	public Configuration getConfigurationByName(String name)
	{
		if (name.equals("debug"))
		{
			if (debug == null)
				debug = new Debug(path);
			return debug;
		}
		else if (name.equals("release"))
		{
			if (release == null)
				release = new Release(path);
			return release;
		}
		else
			throw new IllegalArgumentException(name + " is not supported");
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
			return new Project(path);
		}
	}
}
