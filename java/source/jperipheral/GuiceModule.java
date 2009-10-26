package jperipheral;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;

/**
 * The guice module.
 * 
 * @author Gili Tzabari
 */
public class GuiceModule implements Module
{
	@Override
	public void configure(Binder binder)
	{
	}

	@Singleton
	@Provides
	private Peripherals getPeripherals()
	{
		return new Peripherals();
	}
}
