package jperipheral.build.configurations;

import buildinjava.AbstractConfiguration;
import buildinjava.BuildException;
import buildinjava.Project;
import buildinjava.io.Copy;
import buildinjava.io.Delete;
import buildinjava.io.FileFilterBuilder;
import buildinjava.java.Jar;
import buildinjava.java.JavaCompiler;
import com.google.inject.Inject;
import com.google.inject.Provider;
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
	private final Project project;
	private final Provider<Delete> delete;
	private final Provider<Copy> copy;
	private final FileFilterBuilder.Builder fileFilter;
	private final Provider<JavaCompiler> javaCompiler;
	private final Provider<Jar> jar;
	private final String javaOutputPath = "java/build";

	/**
	 * Insulates subclasses from a growing list of wiring objects.
	 *
	 * @author Gili Tzabari
	 */
	protected static class Wiring
	{
		private final Provider<Delete> delete;
		private final Provider<Copy> copy;
		private final FileFilterBuilder.Builder filterBuilder;
		private final Provider<JavaCompiler> javaCompiler;
		private final Provider<Jar> jar;

		/**
		 * Creates a new Wiring.
		 *
		 * @param delete an instance of <code>Provider<Delete></code>
		 * @param copy an instance of <code>Provider<Copy></code>
		 * @param filterBuilder an instance of <code>FileFilterBuilder.Builder</code>
		 * @param javaCompiler an instance of <code>Provider<JavaCompiler></code>
		 * @param jar an instance of <code>Provider<Jar></code>
		 */
		@Inject
		public Wiring(Provider<Delete> delete, Provider<Copy> copy,
									FileFilterBuilder.Builder filterBuilder,
									Provider<JavaCompiler> javaCompiler, Provider<Jar> jar)
		{
			this.delete = delete;
			this.copy = copy;
			this.filterBuilder = filterBuilder;
			this.javaCompiler = javaCompiler;
			this.jar = jar;
		}
	}

	/**
	 * Creates a Common.
	 *
	 * @param wiring an instance of <code>Wiring</code>
	 * @param project the parent project
	 * @param name the configuration name
	 */
	public Common(Wiring wiring, Project project, String name)
	{
		super(name);
		this.delete = wiring.delete;
		this.copy = wiring.copy;
		this.fileFilter = wiring.filterBuilder;
		this.javaCompiler = wiring.javaCompiler;
		this.jar = wiring.jar;
		this.project = project;
	}

	/**
	 * Returns the project.
	 *
	 * @return the project
	 */
	protected Project getProject()
	{
		return project;
	}

	@Override
	public void clean()
	{
		delete.get().file(new File(project.getPath(), javaOutputPath));
		delete.get().file(new File(project.getPath(), "cpp/build/" + getPlatform()));
		delete.get().file(new File(project.getPath(), "dist/" + getPlatform()));
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

		copyCppClasses();
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
		File source = new File(project.getPath(), "java/source");
		File target = new File(project.getPath(), javaOutputPath);
		FileFilter filter = fileFilter.rejectAll().addDirectory("*").removeDirectory(".svn").addFile("*.java").
			build();
		List<File> classPath = new ArrayList<File>();
		classPath.add(new File(project.getPath(), "java/libraries/joda-time/joda-time-1.6.jar"));

		if (!target.exists() && !target.mkdirs())
			throw new BuildException("Cannot create " + target.getAbsolutePath());
		javaCompiler.get().filter(filter).classPath(classPath).apply(source, target);
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
			File file = new File(project.getPath(), javaOutputPath + "/jperipheral/WindowsOS.class");
			new PeerEnhancer.Builder(file, file).library("JPeripheral").enhance();

			file = new File(project.getPath(), javaOutputPath + "/jperipheral/SerialPort.class");
			new PeerEnhancer.Builder(file, file).library("JPeripheral").deallocationMethod("close").enhance();

			file = new File(project.getPath(), javaOutputPath + "/jperipheral/SerialChannel.class");
			new PeerEnhancer.Builder(file, file).library("JPeripheral").deallocationMethod("close").enhance();

			file = new File(project.getPath(),
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
		File netbeansPath = new File(project.getPath(), javaOutputPath);
		File buildPath = new File(project.getPath(), "dist/" + getPlatform() + "/java");
		if (!buildPath.exists() && !buildPath.mkdirs())
			throw new BuildException("Cannot create " + buildPath);
		copy.get().apply(netbeansPath, buildPath);
	}

	/**
	 * Packages the Java classes into a JAR file.
	 *
	 * @throws BuildException if an expected build error occurs
	 */
	private void packageJavaClasses() throws BuildException
	{
		File sourcePath = new File(project.getPath(), javaOutputPath);
		File target = new File(project.getPath(), "dist/" + getPlatform() + "/java/jperipheral.jar");
		File targetDirectory = target.getParentFile();
		if (!targetDirectory.exists() && !targetDirectory.mkdirs())
			throw new BuildException("Cannot create " + targetDirectory);
		jar.get().create(sourcePath, target);
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
			File includeDir = new File(project.getPath(), "cpp/build/" + getPlatform() + "/include");
			File sourceDir = new File(project.getPath(), "cpp/build/" + getPlatform() + "/source");

			File classFile = new File(project.getPath(), javaOutputPath + "/jperipheral/WindowsOS.class");
			new PeerGenerator(new ClassFile(classFile), includeDir, sourceDir, false).generate();

			classFile = new File(project.getPath(), javaOutputPath + "/jperipheral/SerialPort.class");
			new PeerGenerator(new ClassFile(classFile), includeDir, sourceDir, false).generate();

			classFile = new File(project.getPath(), javaOutputPath + "/jperipheral/SerialChannel.class");
			new PeerGenerator(new ClassFile(classFile), includeDir, sourceDir, false).generate();

			classFile = new File(project.getPath(),
				javaOutputPath + "/jperipheral/SerialChannel$SerialFuture.class");
			new PeerGenerator(new ClassFile(classFile), includeDir, sourceDir, false).generate();
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
	private void copyCppClasses() throws BuildException
	{
		File source = new File(project.getPath(), "cpp/windows");
		File target = new File(project.getPath(), "cpp/build/" + getPlatform());
		if (!target.exists() && !target.mkdirs())
			throw new BuildException("Cannot create " + target);
		copy.get().filter(fileFilter.acceptAll().removeDirectory(".svn").build()).apply(source, target);
	}

	/**
	 * Generates the C++ proxies.
	 *
	 * @throws BuildException if an expected build error occurs
	 */
	private void generateCppProxies() throws BuildException
	{
		File inputHeaders = new File(getProject().getPath(), "cpp/build/" + getPlatform() + "/include");
		File inputSources = new File(getProject().getPath(), "cpp/build/" + getPlatform() + "/source");
		File outputHeaders = new File(getProject().getPath(), "cpp/build/" + getPlatform() + "/include");
		File outputSources = new File(getProject().getPath(), "cpp/build/" + getPlatform() + "/source");
		List<File> classPath = new ArrayList<File>();
		classPath.add(new File(System.getenv("JAVA_HOME"), "jre/lib/rt.jar"));
		classPath.add(new File(getProject().getPath(), javaOutputPath));
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

		builder.directory(new File(project.getPath(), "cpp/build/" + getPlatform() + "/msvc"));
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
		File source = new File(project.getPath(), "cpp/build/" + getPlatform() + "/msvc/" + getPlatform());
		File target = new File(project.getPath(), "dist/" + getPlatform() + "/native");
		if (!target.exists() && !target.mkdirs())
			throw new BuildException("Cannot create " + target);
		copy.get().filter(fileFilter.rejectAll().addDirectory("*").addFile("*.dll").addFile("*.pdb").build()).
			apply(source, target);
	}
}
