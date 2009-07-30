package jperipheral.build;

import jperipheral.build.configurations.Debug;
import jperipheral.build.configurations.Release;
import buildinjava.BuildException;
import buildinjava.Configuration;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
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

	/**
	 * Creates a new Project.
	 */
	public Project()
	{
		// "path" must be initialized ahead of time because Configuration.clean() removes classes
		try
		{
			String name = getClass().getName().replace(".", "/") + ".class";
			int packageDepth = getClass().getPackage().getName().split("\\.").length;
			URL projectURL = getClass().getClassLoader().getResource(name);
			assert (projectURL != null);
			File temp = new File(projectURL.toURI()).getParentFile();
			for (int i = 0; i < packageDepth; ++i)
				temp = temp.getParentFile();
			this.path = temp.getParentFile().getParentFile().getParentFile().getParentFile();
		}
		catch (URISyntaxException e)
		{
			throw new BuildException(e);
		}
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
}
