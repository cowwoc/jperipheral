package jperipheral.build;

import buildinjava.Project;
import buildinjava.io.FileFilters;
import buildinjava.io.Files;
import buildinjava.io.WildcardFileFilter;
import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;

/**
 * Information for compiling the project.
 * 
 * @author Gili Tzabari
 */
public class ProjectCompiler implements Project.Compiler
{
	@Override
	public List<File> getClasspath(List<File> buildInJavaClassPath, File projectDirectory)
	{
		List<File> result = new ArrayList<File>(buildInJavaClassPath);
		File jaceDirectory = new File(projectDirectory, "java/libraries/jace");
		FileFilter isDirectory = new FileFilters().isDirectory();
		FileFilter jarFiles = new WildcardFileFilter("*.jar");

		for (File file: new Files().list(jaceDirectory, isDirectory, jarFiles))
			result.add(file);
		return result;
	}
}
