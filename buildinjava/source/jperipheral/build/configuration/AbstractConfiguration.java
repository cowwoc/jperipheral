package jperipheral.build.configuration;

import buildinjava.BuildException;
import buildinjava.cpp.VisualStudio;
import buildinjava.io.Copy;
import buildinjava.io.Delete;
import buildinjava.io.FileFilterBuilder;
import buildinjava.io.FileFilters;
import buildinjava.io.Files;
import buildinjava.io.WildcardFileFilter;
import buildinjava.java.Jar;
import buildinjava.java.JavaCompiler;
import com.google.common.base.Function;
import jace.parser.ClassFile;
import jace.peer.PeerEnhancer;
import jace.peer.PeerGenerator;
import jace.proxy.AutoProxy;
import jace.proxy.ClassPath;
import jace.proxy.ProxyGenerator.AccessibilityType;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logic common to all configurations.
 *
 * @author Gili Tzabari
 */
abstract class AbstractConfiguration extends buildinjava.AbstractConfiguration
{
	private final Logger log = LoggerFactory.getLogger(AbstractConfiguration.class);
	private final File projectPath;
	private static final String javaOutputPath = "java/build";
	private static final String cppOutputPath = "cpp/build";
	private static final String nativeLibrary = "JPeripheral";
	private final FileFilter directoryFilter = new FileFilterBuilder(FileFilters.acceptAll()).
		removeDirectory(".svn").build();

	/**
	 * Compiles the C++ classes.
	 *
	 * @throws BuildException if an expected build error occurs
	 */
	private void compileCppClasses() throws BuildException
	{
		log.trace("compileCppClasses");

		// debug, release, etc
		String configurationType = getName().split("\\.")[1];

		new VisualStudio().solution(new File(getProjectPath(),
			cppOutputPath + "/" + getRelativePath() + "/msvc/JPeripheral.sln")).configuration(
			configurationType).platform("Win32").build();
	}

	/**
	 * Creates a AbstractConfiguration configuration.
	 *
	 * @param name the configuration name
	 * @param projectPath the project path
	 */
	public AbstractConfiguration(final String name, final File projectPath)
	{
		super(name);
		this.projectPath = projectPath;
	}

	@Override
	public void clean()
	{
		new Delete().file(new File(projectPath, javaOutputPath));
		new Delete().file(new File(projectPath, cppOutputPath + "/" + getRelativePath()));
		new Delete().file(new File(projectPath, "dist/" + getRelativePath()));
	}

	@Override
	public void build() throws BuildException
	{
		compileJavaClasses();
		enhanceJavaPeers();
		packageJavaClasses();

		copyCppSourceToBuild();
		generateCppPeers();
		generateCppProxies();
		compileCppClasses();
		copyCppBinaries();
	}

	/**
	 * Compiles the Java classes.
	 *
	 * @throws BuildException if an excepted build error occurs
	 */
	@SuppressWarnings(
	{
		"PMD.AvoidDuplicateLiterals", "PMD.DataflowAnomalyAnalysis"
	})
	private void compileJavaClasses() throws BuildException
	{
		final File source = new File(projectPath, "java/source");
		final File target = new File(projectPath, javaOutputPath);

		if (!target.exists() && !target.mkdirs())
			throw new BuildException("Cannot create " + target.getAbsolutePath());
		final List<URL> classPath = new ArrayList<URL>();
		final Function<File, URL> fileToURL = Files.toURL();
		classPath.add(fileToURL.apply(
			new File(projectPath, "java/libraries/joda-time/joda-time-1.6.jar")));
		classPath.add(fileToURL.apply(new File(projectPath, "java/libraries/slf4j/slf4j-api-1.5.6.jar")));
		classPath.add(fileToURL.apply(new File(projectPath, "java/libraries/guice/guice-2.0.jar")));
		classPath.add(fileToURL.apply(new File(projectPath,
			"java/libraries/google-collections/google-collect-1.0-rc2.jar")));
		classPath.add(fileToURL.apply(target));

		List<File> sourceFiles = Files.list(source, directoryFilter, new WildcardFileFilter("*.java"));
		new JavaCompiler().classPath(classPath).apply(sourceFiles, target, Collections.singleton(source));
	}

	/**
	 * Enhances the Java peers.
	 *
	 * @throws BuildException if an expected build error occurs
	 */
	private void enhanceJavaPeers() throws BuildException
	{
		try
		{
			File file = new File(projectPath, javaOutputPath + "/jperipheral/WindowsOS.class");
			new PeerEnhancer.Builder(file, file).library(nativeLibrary).enhance();

			file = new File(projectPath, javaOutputPath + "/jperipheral/CanonicalSerialPort.class");
			new PeerEnhancer.Builder(file, file).library(nativeLibrary).deallocationMethod("close").
				enhance();

			file = new File(projectPath, javaOutputPath + "/jperipheral/SerialChannel.class");
			new PeerEnhancer.Builder(file, file).library(nativeLibrary).deallocationMethod("close").
				enhance();

			file = new File(projectPath,
				javaOutputPath + "/jperipheral/SerialChannel$SerialFuture.class");
			new PeerEnhancer.Builder(file, file).library(nativeLibrary).enhance();
		}
		catch (IOException e)
		{
			throw new BuildException(e);
		}
	}

	/**
	 * Packages the Java classes into a JAR file.
	 *
	 * @throws BuildException if an expected build error occurs
	 */
	@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
	private void packageJavaClasses() throws BuildException
	{
		final File sourcePath = new File(projectPath, javaOutputPath);
		final File target = new File(projectPath, "dist/" + getRelativePath() + "/java/jperipheral.jar");
		final File targetDirectory = target.getParentFile();
		if (!targetDirectory.exists() && !targetDirectory.mkdirs())
			throw new BuildException("Cannot create " + targetDirectory);
		new Jar().create(sourcePath, target);
	}

	/**
	 * Generates the C++ peers.
	 *
	 * @throws BuildException if an expected build error occurs
	 */
	private void generateCppPeers() throws BuildException
	{
		try
		{
			final File includeDir = new File(projectPath, cppOutputPath + "/" + getRelativePath()
																										+ "/include");
			final File sourceDir = new File(projectPath, cppOutputPath + "/" + getRelativePath()
																									 + "/source");

			File classFile = new File(projectPath, javaOutputPath + "/jperipheral/WindowsOS.class");
			new PeerGenerator(new ClassFile(classFile), classFile.lastModified(), includeDir, sourceDir,
				false).generate();

			classFile = new File(projectPath, javaOutputPath + "/jperipheral/CanonicalSerialPort.class");
			new PeerGenerator(new ClassFile(classFile), classFile.lastModified(), includeDir, sourceDir,
				false).generate();

			classFile = new File(projectPath, javaOutputPath + "/jperipheral/SerialChannel.class");
			new PeerGenerator(new ClassFile(classFile), classFile.lastModified(), includeDir, sourceDir,
				false).generate();

			classFile = new File(projectPath, javaOutputPath
																				+ "/jperipheral/SerialChannel$SerialFuture.class");
			new PeerGenerator(new ClassFile(classFile), classFile.lastModified(), includeDir, sourceDir,
				false).generate();
		}
		catch (IOException e)
		{
			throw new BuildException(e);
		}
	}

	/**
	 * Copies the C++ classes into the build directory.
	 *
	 * @throws BuildException if an expected build error occurs
	 */
	@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
	private void copyCppSourceToBuild() throws BuildException
	{
		final File source = new File(projectPath, "cpp/windows");
		final File target = new File(projectPath, cppOutputPath + "/" + getRelativePath());
		if (!target.exists() && !target.mkdirs())
			throw new BuildException("Cannot create " + target);
		new Copy().filter(directoryFilter, FileFilters.isFile()).fromDirectory(source).
			toDirectory(target);
	}

	/**
	 * Generates the C++ proxies.
	 *
	 * @throws BuildException if an expected build error occurs
	 */
	private void generateCppProxies() throws BuildException
	{
		final File inputHeaders = new File(projectPath, cppOutputPath + "/" + getRelativePath()
																										+ "/include");
		final File inputSources = new File(projectPath, cppOutputPath + "/" + getRelativePath()
																										+ "/source");
		final File outputHeaders = new File(projectPath, cppOutputPath + "/" + getRelativePath()
																										 + "/include");
		final File outputSources = new File(projectPath, cppOutputPath + "/" + getRelativePath()
																										 + "/source");
		final List<File> classPath = new ArrayList<File>();
		classPath.add(new File(System.getenv("JAVA_HOME"), "jre/lib/rt.jar"));
		classPath.add(new File(projectPath, "java/libraries/slf4j/slf4j-api-1.5.6.jar"));
		classPath.add(new File(projectPath, javaOutputPath));
		try
		{
			new AutoProxy.Builder(Collections.singleton(inputHeaders), Collections.singleton(inputSources),
				outputHeaders, outputSources, new ClassPath(classPath)).accessibility(
				AccessibilityType.PRIVATE).
				exportSymbols(true).generateProxies();
		}
		catch (IOException e)
		{
			throw new BuildException(e);
		}
	}

	/**
	 * Copy the C++ binaries into the output directory.
	 *
	 * @throws BuildException if an expected build error occurs
	 */
	@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
	private void copyCppBinaries()
	{
		final File source = new File(projectPath, cppOutputPath + "/" + getRelativePath() + "/msvc/"
																							+ getRelativePath());
		final File target = new File(projectPath, "dist/" + getRelativePath() + "/native");
		if (!target.exists() && !target.mkdirs())
			throw new BuildException("Cannot create " + target);
		new Copy().filter(directoryFilter, new FileFilterBuilder(FileFilters.rejectAll()).addFile(
			"*.dll").addFile("*.pdb").build()).fromDirectory(source).toDirectory(target);
	}

	/**
	 * Returns the project path.
	 *
	 * @return the project path
	 */
	protected final File getProjectPath()
	{
		return projectPath;
	}

	/**
	 * Returns the relative path used to denote the configuration.
	 *
	 * @return the relative path used to denote the configuration
	 */
	private String getRelativePath()
	{
		return getName().replace(".", "/");
	}
}
