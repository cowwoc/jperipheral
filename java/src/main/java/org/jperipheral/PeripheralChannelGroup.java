package org.jperipheral;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.AsynchronousChannelGroup;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * An AsynchronousChannelGroup for peripherals.
 * 
 * @author Gili Tzabari
 */
public class PeripheralChannelGroup extends AsynchronousChannelGroup
{
	private final ExecutorService executor;
	private final List<Closeable> channels = Lists.newArrayList();

	/**
	 * Creates a new PeripheralChannelGroup.
	 * 
	 * @param executor the executor used to schedule tasks
	 */
	public PeripheralChannelGroup(ExecutorService executor)
	{
		super(null);
		this.executor = executor;
	}

	@Override
	public boolean isShutdown()
	{
		return executor.isShutdown();
	}

	@Override
	public boolean isTerminated()
	{
		return executor.isTerminated();
	}

	@Override
	public void shutdown()
	{
		executor.shutdown();
	}

	@Override
	public void shutdownNow() throws IOException
	{
		executor.shutdownNow();
		for (Closeable channel: channels)
			channel.close();
	}

	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException
	{
		return executor.awaitTermination(timeout, unit);
	}

	/**
	 * Adds a channel to the group.
	 * 
	 * @param channel the channel
	 * @throws NullPointerException if channel is null
	 */
	public void addChannel(Closeable channel)
	{
		Preconditions.checkNotNull(channel, "channel may not be null");

		channels.add(channel);
	}
	
	/**
	 * Returns the executor associated with the group.
	 * 
	 * @return the executor associated with the group
	 */
	public ExecutorService executor()
	{
		return executor;
	}
}
