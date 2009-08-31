package jperipheral.build.configurations;

import buildinjava.AbstractConfiguration;
import buildinjava.BuildException;
import buildinjava.io.Copy;
import buildinjava.io.Delete;
import buildinjava.io.FileFilterBuilder;
import buildinjava.java.Jar;
import buildinjava.java.JavaCompiler;
import jace.parser.ClassFile;
import jace.peer.PeerEnhancer;
import jace.peer.PeerGenerator;
import jace.proxy.AutoProxy;
import jace.proxy.ProxyGenerator.AccessibilityType;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStreamReader;
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
		//copyJavaClasses();
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
		FileFilter filter = new FileFilterBuilder().addDirectory("*").removeDirectory(".svn").addFile("*.java").
			build();

		if (!target.exists() && !target.mkdirs())
			throw new BuildException("Cannot create " + target.getAbsolutePath());
		List<File> classPath = new ArrayList<File>();
		classPath.add(new File(projectPath, "java/libraries/joda-time/joda-time-1.6.jar"));
		new JavaCompiler().filter(filter).classPath(classPath).apply(source, target);
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

			file = new File(projectPath, javaOutputPath + "/jperipheral/SerialPort.class");
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
	 * Copies the Java classes into the build directory.
	 *
	 * @throws BuildException if an expected build error occurs
	 */
	private void copyJavaClasses() throws BuildException
	{
		File netbeansPath = new File(projectPath, javaOutputPath);
		File buildPath = new File(projectPath, "dist/" + getPlatform() + "/java");
		if (!buildPath.exists() && !buildPath.mkdirs())
			throw new BuildException("Cannot create " + buildPath);
		new Copy().fromDirectory(netbeansPath).toDirectory(buildPath);
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

			classFile = new File(projectPath, javaOutputPath + "/jperipheral/SerialPort.class");
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
		new Copy().filter(new FileFilterBuilder().acceptAll().removeDirectory(".svn").build()).
			fromDirectory(source).toDirectory(target);
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
		classPath.add(new File(projectPath, javaOutputPath));
		try
		{
			new AutoProxy.Builder(Collections.singleton(inputHeaders), Collections.singleton(inputSources),
				outputHeaders, outputSources, classPath).accessibility(AccessibilityType.PRIVATE).exportSymbols(true).
				generateProxies();
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
		ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c",
			System.getenv("VS90COMNTOOLS") + "..\\IDE\\devenv.com", "JPeripheral.sln",
			"/build", getName() + "^|Win32");

		builder.directory(new File(projectPath, "cpp/build/" + getPlatform() + "/msvc"));
		builder.redirectErrorStream(true);
		try
		{
			Process process = builder.start();
			BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
			while (true)
			{
				String line = in.readLine();
				if (line == null)
					break;
				System.out.println(line);
			}
			if (process.exitValue() != 0)
				throw new BuildException("Return code: " + process.exitValue());
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
	private void copyCppBinaries()
	{
		File source = new File(projectPath, "cpp/build/" + getPlatform() + "/msvc/" + getPlatform());
		File target = new File(projectPath, "dist/" + getPlatform() + "/native");
		if (!target.exists() && !target.mkdirs())
			throw new BuildException("Cannot create " + target);
		new Copy().filter(new FileFilterBuilder().addDirectory("*").addFile("*.dll").addFile("*.pdb").build()).
			fromDirectory(source).toDirectory(target);
	}
}
