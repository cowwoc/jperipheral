package jperipheral.build.configurations;

import jperipheral.build.*;
import com.google.inject.Inject;

/**
 * The debug configuration.
 *
 * @author Gili Tzabari
 */
public class Release extends Common
{
	/**
	 * Creates a DebugConfiguration.
	 * 
	 * @param wiring an instance of <code>Wiring</code>
	 * @param project the parent project
	 */
	@Inject
	public Release(Wiring wiring, Project project)
	{
		super(wiring, project, "debug");
	}
}
