package jperipheral;

import java.nio.ByteBuffer;
import jperipheral.nio.channels.AsynchronousByteChannel;
import jperipheral.nio.channels.CompletionHandler;
import jperipheral.nio.channels.ReadPendingException;
import jperipheral.nio.channels.ShutdownChannelGroupException;
import jperipheral.nio.channels.WritePendingException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * An asynchronous channel for serial ports.
 *
 * @author Gili Tzabari
 */
public abstract class AsynchronousSerialChannel implements AsynchronousByteChannel,
																													 AsynchronousByteChannelTimeouts
{
	/**
	 * @throws  IllegalArgumentException        {@inheritDoc}
	 * @throws  ReadPendingException            {@inheritDoc}
	 * @throws  ShutdownChannelGroupException
	 *          If the channel group is shutdown
	 */
	@Override
	public final <A> void read(ByteBuffer dst,
														 A attachment,
														 CompletionHandler<Integer, ? super A> handler)
	{
		read(dst, 0L, TimeUnit.MILLISECONDS, attachment, handler);
	}

	/**
	 * @throws  IllegalArgumentException        {@inheritDoc}
	 * @throws  ReadPendingException            {@inheritDoc}
	 */
	@Override
	public abstract Future<Integer> read(ByteBuffer dst);

	/**
	 * @throws  WritePendingException          {@inheritDoc}
	 * @throws  ShutdownChannelGroupException
	 *          If the channel group is shutdown
	 */
	@Override
	public final <A> void write(ByteBuffer src,
															A attachment,
															CompletionHandler<Integer, ? super A> handler)
	{
		write(src, 0L, TimeUnit.MILLISECONDS, attachment, handler);
	}

	/**
	 * @throws  WritePendingException       {@inheritDoc}
	 */
	@Override
	public abstract Future<Integer> write(ByteBuffer src);
}
