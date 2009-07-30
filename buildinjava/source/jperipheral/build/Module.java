package jperipheral.build;

import jperipheral.build.configurations.Debug;
import jperipheral.build.configurations.Release;
import buildinjava.Configuration;
import com.google.inject.Binder;
import com.google.inject.name.Names;

/**
 * Guice module.
 *
 * @author Gili Tzabari
 */
public class Module implements com.google.inject.Module
{
	@Override
	public void configure(Binder binder)
	{
		binder.bind(buildinjava.Project.class).to(Project.class);
		binder.bind(Configuration.class).annotatedWith(Names.named("debug")).to(Debug.class);
		binder.bind(Configuration.class).annotatedWith(Names.named("release")).to(Release.class);
	}
}
