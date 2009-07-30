package jperipheral.build.configurations;

import jperipheral.build.Project;
import com.google.inject.Inject;

/**
 * The debug configuration.
 *
 * @author Gili Tzabari
 */
public class Debug extends Common
{
	/**
	 * Creates a Debug.
	 * 
	 * @param wiring an instance of <code>Wiring</code>
	 * @param project the parent project
	 */
	@Inject
	public Debug(Wiring wiring, Project project)
	{
		super(wiring, project, "debug");
	}
}
