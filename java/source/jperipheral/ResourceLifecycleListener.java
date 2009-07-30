package jperipheral;

/**
 * Listens for resource lifecycle events.
 *
 * @author Gili Tzabari
 */
public interface ResourceLifecycleListener
{
	/**
	 * Invoked before a resource is created.
	 */
	void beforeResourceCreated();

	/**
	 * Invoked after a resource is destroyed.
	 */
	void afterResourceDestroyed();
}
