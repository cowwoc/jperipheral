package jperipheral.build.configurations;

import buildinjava.AbstractConfiguration;
import buildinjava.BuildException;
import buildinjava.cpp.VisualStudio;
import buildinjava.io.Copy;
import buildinjava.io.Delete;
import buildinjava.io.FileFilterBuilder;
import buildinjava.io.FileFilters;
import buildinjava.io.WildcardFileFilter;
import buildinjava.java.Jar;
import buildinjava.java.JavaCompiler;
import jace.parser.ClassFile;
import jace.peer.PeerEnhancer;
import jace.peer.PeerGenerator;
import jace.proxy.AutoProxy;
import jace.proxy.ClassPath;
import jace.proxy.ProxyGenerator.AccessibilityType;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Logic common to all configuration.
 *
 * @author Gili Tzabari
 */
public abstract class Common extends AbstractConfiguration
{
	private final File projectPath;
	private final String javaOutputPath = "java/build";
	private final FileFilter directoryFilter = new FileFilterBuilder().addDirectory("*").removeDirectory(".svn").
		build();

	/**
	 * Creates a Common configuration.
	 *
	 * @param name the configuration name
	 * @param projectPath the project path
	 */
	public Common(String name, File projectPath)
	{
		super(name);
		this.projectPath = projectPath;
	}

	@Override
	public void clean()
	{
		new Delete().file(new File(projectPath, javaOutputPath));
		new Delete().file(new File(projectPath, "cpp/build/" + getPlatform()));
		new Delete().file(new File(projectPath, "dist/" + getPlatform()));
	}

	/**
	 * Returns the platform being built.
	 *
	 * @return the platform being built
	 */
	private String getPlatform()
	{
		return "i386/" + getName();
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
	private void compileJavaClasses() throws BuildException
	{
		File source = new File(projectPath, "java/source");
		File target = new File(projectPath, javaOutputPath);

		if (!target.exists() && !target.mkdirs())
			throw new BuildException("Cannot create " + target.getAbsolutePath());
		List<File> classPath = new ArrayList<File>();
		classPath.add(new File(projectPath, "java/libraries/joda-time/joda-time-1.6.jar"));
		classPath.add(new File(projectPath, "java/libraries/slf4j/slf4j-api-1.5.6.jar"));
		classPath.add(new File(projectPath, "java/libraries/guice/guice-2.0.jar"));
		classPath.add(new File(projectPath, "java/libraries/google-collections/google-collect-1.0-rc2.jar"));
		classPath.add(target);
		new JavaCompiler().filter(directoryFilter, new WildcardFileFilter("*.java")).classPath(classPath).
			apply(source, target);
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
			new PeerEnhancer.Builder(file, file).library("JPeripheral").enhance();

			file = new File(projectPath, javaOutputPath + "/jperipheral/CanonicalSerialPort.class");
			new PeerEnhancer.Builder(file, file).library("JPeripheral").deallocationMethod("close").enhance();

			file = new File(projectPath, javaOutputPath + "/jperipheral/SerialChannel.class");
			new PeerEnhancer.Builder(file, file).library("JPeripheral").deallocationMethod("close").enhance();

			file = new File(projectPath,
				javaOutputPath + "/jperipheral/SerialChannel$SerialFuture.class");
			new PeerEnhancer.Builder(file, file).library("JPeripheral").enhance();
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
	private void packageJavaClasses() throws BuildException
	{
		File sourcePath = new File(projectPath, javaOutputPath);
		File target = new File(projectPath, "dist/" + getPlatform() + "/java/jperipheral.jar");
		File targetDirectory = target.getParentFile();
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
			File includeDir = new File(projectPath, "cpp/build/" + getPlatform() + "/include");
			File sourceDir = new File(projectPath, "cpp/build/" + getPlatform() + "/source");

			File classFile = new File(projectPath, javaOutputPath + "/jperipheral/WindowsOS.class");
			new PeerGenerator(new ClassFile(classFile), classFile.lastModified(), includeDir, sourceDir, false).
				generate();

			classFile = new File(projectPath, javaOutputPath + "/jperipheral/CanonicalSerialPort.class");
			new PeerGenerator(new ClassFile(classFile), classFile.lastModified(), includeDir, sourceDir, false).
				generate();

			classFile = new File(projectPath, javaOutputPath + "/jperipheral/SerialChannel.class");
			new PeerGenerator(new ClassFile(classFile), classFile.lastModified(), includeDir, sourceDir, false).
				generate();

			classFile = new File(projectPath, javaOutputPath + "/jperipheral/SerialChannel$SerialFuture.class");
			new PeerGenerator(new ClassFile(classFile), classFile.lastModified(), includeDir, sourceDir, false).
				generate();
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
	private void copyCppSourceToBuild() throws BuildException
	{
		File source = new File(projectPath, "cpp/windows");
		File target = new File(projectPath, "cpp/build/" + getPlatform());
		if (!target.exists() && !target.mkdirs())
			throw new BuildException("Cannot create " + target);
		new Copy().filter(directoryFilter, new FileFilters().acceptAll()).fromDirectory(source).toDirectory(target);
	}

	/**
	 * Generates the C++ proxies.
	 *
	 * @throws BuildException if an expected build error occurs
	 */
	private void generateCppProxies() throws BuildException
	{
		File inputHeaders = new File(projectPath, "cpp/build/" + getPlatform() + "/include");
		File inputSources = new File(projectPath, "cpp/build/" + getPlatform() + "/source");
		File outputHeaders = new File(projectPath, "cpp/build/" + getPlatform() + "/include");
		File outputSources = new File(projectPath, "cpp/build/" + getPlatform() + "/source");
		List<File> classPath = new ArrayList<File>();
		classPath.add(new File(System.getenv("JAVA_HOME"), "jre/lib/rt.jar"));
		classPath.add(new File(projectPath, "java/libraries/slf4j/slf4j-api-1.5.6.jar"));
		classPath.add(new File(projectPath, javaOutputPath));
		try
		{
			new AutoProxy.Builder(Collections.singleton(inputHeaders), Collections.singleton(inputSources),
				outputHeaders, outputSources, new ClassPath(classPath)).accessibility(AccessibilityType.PRIVATE).
				exportSymbols(true).generateProxies();
		}
		catch (IOException e)
		{
			throw new BuildException(e);
		}
	}

	/**
	 * Compiles the C++ classes.
	 *
	 * @throws BuildException if an expected build error occurs
	 */
	private void compileCppClasses() throws BuildException
	{
		new VisualStudio().solution(new File(projectPath, "cpp/build/" + getPlatform() + "/msvc/JPeripheral.sln")).
			configuration(getName()).platform("Win32").build();
	}

	/**
	 * Copy the C++ binaries into the output directory.
	 *
	 * @throws BuildException if an expected build error occurs
	 */
	private void copyCppBinaries()
	{
		File source = new File(projectPath, "cpp/build/" + getPlatform() + "/msvc/" + getPlatform());
		File target = new File(projectPath, "dist/" + getPlatform() + "/native");
		if (!target.exists() && !target.mkdirs())
			throw new BuildException("Cannot create " + target);
		FileFilter directories = new FileFilterBuilder().addDirectory("*").removeDirectory(".svn").build();
		new Copy().filter(directories, new FileFilterBuilder().addFile("*.dll").addFile("*.pdb").build()).
			fromDirectory(source).toDirectory(target);
	}
}
