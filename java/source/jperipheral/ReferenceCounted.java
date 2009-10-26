package jperipheral;

/**
 * Notified when references to the object are added or removed.
 *
 * @author Gili Tzabari
 */
interface ReferenceCounted<E extends Exception>
{
	/**
	 * Invoked before a reference to this object is added.
	 *
	 * @throws IllegalStateException if the object cannot accept any more references
	 */
	void beforeReferenceAdded();

	/**
	 * Invoked after a reference to this object is removed.
	 *
	 * @throws IllegalStateException if the last reference to the object has already been removed
	 */
	void afterReferenceRemoved() throws E;
}
