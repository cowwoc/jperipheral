package org.jperipheral;

import com.google.common.base.Objects;
import com.google.common.base.Objects.ToStringHelper;
import com.google.common.base.Throwables;
import java.nio.channels.CompletionHandler;

/**
 * @author Gili Tzabari
 */
public class PollableCompletionHandler<V> implements CompletionHandler<V, Void>
{
	public boolean done;
	public V value;
	public Throwable throwable;

	@Override
	public synchronized void completed(V result, Void attachment)
	{
		this.done = true;
		this.value = result;
		notify();
	}

	@Override
	public synchronized void failed(Throwable t, Void attachment)
	{
		this.done = true;
		this.throwable = t;
		notify();
	}

	@Override
	public String toString()
	{
		ToStringHelper result = Objects.toStringHelper(getClass().getName()).add("done", done).
			add("value", value);
		if (throwable == null)
			result.add("throwable", "null");
		else
			result.add("throwable", Throwables.getStackTraceAsString(throwable));
		return result.toString();
	}
}
