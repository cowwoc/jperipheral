package jperipheral.build;

import buildinjava.BuildInJava;
import buildinjava.Project;
import buildinjava.io.FileFilters;
import buildinjava.io.Files;
import buildinjava.io.WildcardFileFilter;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.FileFilter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Returns the project classpath.
 * 
 * @author Gili Tzabari
 */
public class ProjectClassPath implements Project.ClassPath
{
	@Override
	public List<URL> getCompileClasspath(final BuildInJava buildInJava, final File projectDirectory)
	{
		final List<URL> result = new ArrayList<URL>(buildInJava.getClassPath());
		final File classDirectory = new File(projectDirectory, "buildinjava");
		final File jaceDirectory = new File(projectDirectory, "java/libraries/jace");
		final FileFilter isDirectory = FileFilters.isDirectory();
		final FileFilter jarFiles = new WildcardFileFilter("*.jar");
		result.addAll(buildInJava.getClassPath());
		result.add(Files.toURL().apply(classDirectory));
		result.addAll(Lists.transform(Files.list(jaceDirectory, isDirectory, jarFiles),
			Files.toURL()));
		result.add(Files.toURL().apply(new File(classDirectory, "target/project")));
		return result;
	}

	@Override
	public List<URL> getRunClasspath(final BuildInJava buildInJava, final File projectDirectory)
	{
		return getCompileClasspath(buildInJava, projectDirectory);
	}
}
