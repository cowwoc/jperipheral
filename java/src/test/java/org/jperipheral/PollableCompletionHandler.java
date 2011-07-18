package org.jperipheral;

import java.nio.channels.CompletionHandler;

/**
 * @author Gili Tzabari
 */
public class PollableCompletionHandler<V> implements CompletionHandler<V, Void>
{
	public V value;
	public Throwable throwable;

	@Override
	public synchronized void completed(V result, Void attachment)
	{
		this.value = result;
		notify();
	}

	@Override
	public synchronized void failed(Throwable t, Void attachment)
	{
		this.throwable = t;
		notify();
	}
}
