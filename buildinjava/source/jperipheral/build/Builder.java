package jperipheral.build;

import buildinjava.Configuration;
import com.google.inject.Guice;
import com.google.inject.Injector;

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
		Configuration configuration = injector.getInstance(project.getDefaultConfiguration());
		configuration.clean();
		configuration.build();
	}
}
