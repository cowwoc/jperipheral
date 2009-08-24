package jperipheral.build;

import buildinjava.Configuration;
import com.google.inject.Guice;
import com.google.inject.Injector;
import jperipheral.build.configurations.Release;

/**
 * Builds the project.
 *
 * @author Gili Tzabari
 */
public class Builder
{
	public static void main(String[] args)
	{
		Injector injector = Guice.createInjector(new buildinjava.Module());
		Project project = injector.getInstance(Project.class);
		Configuration configuration = injector.getInstance(Release.class);//project.getDefaultConfiguration());
		configuration.clean();
		configuration.build();
	}
}
