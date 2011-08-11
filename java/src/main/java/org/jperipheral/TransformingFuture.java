package org.jperipheral;

import com.google.common.base.Preconditions;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Forwards calls to another Future.
 * 
 * <b>Thread safety</b>: The class is thread-safe if delegate is only modified by the constructor
 * or if the subclass synchronizes the {@code get(long, TimeUnit)} method.
 * 
 * @param <I> the return value of the delegate
 * @param <O> the return value of this Future
 * @author Gili Tzabari
 */
public abstract class TransformingFuture<I, O> implements Future<O>
{
	protected Future<I> delegate;

	/**
	 * Creates a new ForwardingFuture.
	 * 
	 * @param delegate the Future to forward calls to
	 * @throws NullPointerException if input is null
	 */
	public TransformingFuture(Future<I> delegate)
	{
		Preconditions.checkNotNull(delegate, "delegate may not be null");

		this.delegate = delegate;
	}

	@Override
	public synchronized boolean cancel(boolean mayInterruptIfRunning)
	{
		return delegate.cancel(mayInterruptIfRunning);
	}

	@Override
	public synchronized boolean isCancelled()
	{
		return delegate.isCancelled();
	}

	@Override
	public synchronized boolean isDone()
	{
		return delegate.isDone();
	}

	@Override
	public O get() throws InterruptedException, ExecutionException
	{
		try
		{
			return get(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException e)
		{
			// Violates specification of Future.get()
			throw new AssertionError("get(long, TimeUnit) implementation violates the method "
				+ "specification", e);
		}
	}

	@Override
	public abstract O get(long timeout, TimeUnit unit)
		throws InterruptedException, ExecutionException, TimeoutException;
}