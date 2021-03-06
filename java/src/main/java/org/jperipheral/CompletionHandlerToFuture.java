package org.jperipheral;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.SettableFuture;
import java.nio.channels.CompletionHandler;

/**
 * Wraps a Future in a CompletionHandler.
 * 
 * @param <V> the result type of the I/O operation
 * @author Gili Tzabari
 */
class CompletionHandlerToFuture<V> implements CompletionHandler<V, Void>
{
	private final SettableFuture<V> future;

	/**
	 * Creates a new CompletionHandlerToFuture.
	 * 
	 * @param future the future to wrap
	 * @throws NullPointerException if future is null
	 */
	public CompletionHandlerToFuture(SettableFuture<V> future)
	{
		Preconditions.checkNotNull(future, "future may not be null");

		this.future = future;
	}

	@Override
	public void completed(V result, Void attachment)
	{
		future.set(result);
	}

	@Override
	public void failed(Throwable t, Void attachment)
	{
		future.setException(t);
	}
}
