package org.jperipheral;

import com.google.common.base.Preconditions;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.Executor;

/**
 * Invokes a delegate CompletionHandler using a specific Executor.
 *
 * @param <V> the result type of the I/O operation
 * @param <A> the type of the object attached to the I/O operation
 * @author Gili Tzabari
 */
class CompletionHandlerExecutor<V, A> implements CompletionHandler<V, A>
{
	private final CompletionHandler<V, ? super A> delegate;
	private final Executor executor;

	/**
	 * Creates a CompletionHandlerExecutor.
	 * 
	 * @param delegate the CompletionHandler to forward calls to
	 * @param executor the Executor used to invoke the delegate
	 * @throws NullPointerException if executor or delegate are null
	 */
	public CompletionHandlerExecutor(CompletionHandler<V, ? super A> delegate, Executor executor)
	{
		Preconditions.checkNotNull(delegate, "delegate may not be null");
		Preconditions.checkNotNull(executor, "executor may not be null");

		this.delegate = delegate;
		this.executor = executor;
	}

	@Override
	public void completed(final V result, final A attachment)
	{
		executor.execute(new Runnable()
		{
			@Override
			public void run()
			{
				delegate.completed(result, attachment);
			}
		});
	}

	@Override
	public void failed(final Throwable t, final A attachment)
	{
		executor.execute(new Runnable()
		{
			@Override
			public void run()
			{
				delegate.failed(t, attachment);
			}
		});
	}
}
