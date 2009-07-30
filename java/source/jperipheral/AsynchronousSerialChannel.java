package jperipheral;

import java.nio.ByteBuffer;
import jperipheral.nio.channels.AsynchronousByteChannel;
import jperipheral.nio.channels.CompletionHandler;
import jperipheral.nio.channels.InterruptedByTimeoutException;
import jperipheral.nio.channels.ReadPendingException;
import jperipheral.nio.channels.ShutdownChannelGroupException;
import jperipheral.nio.channels.WritePendingException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * An asynchronous channel for serial ports.
 *
 * <h4>Timeouts</h4>
 *
 * <p> The {@link #read(ByteBuffer,long,TimeUnit,Object,CompletionHandler) read}
 * and {@link #write(ByteBuffer,long,TimeUnit,Object,CompletionHandler) write}
 * methods defined by this class allow a timeout to be specified when initiating
 * a read or write operation. If the timeout elapses before an operation completes
 * then the operation completes with the exception {@link
 * InterruptedByTimeoutException}. A timeout may leave the channel, or the
 * underlying connection, in an inconsistent state. Where the implementation
 * cannot guarantee that bytes have not been read from the channel then it puts
 * the channel into an implementation specific <em>error state</em>. A subsequent
 * attempt to initiate a {@code read} operation causes an unspecified runtime
 * exception to be thrown. Similarly if a {@code write} operation times out and
 * the implementation cannot guarantee bytes have not been written to the
 * channel then further attempts to {@code write} to the channel cause an
 * unspecified runtime exception to be thrown. When a timeout elapses then the
 * state of the {@link ByteBuffer}, or the sequence of buffers, for the I/O
 * operation is not defined. Buffers should be discarded or at least care must
 * be taken to ensure that the buffers are not accessed while the channel remains
 * open.
 *
 * @author Gili Tzabari
 */
public abstract class AsynchronousSerialChannel implements AsynchronousByteChannel
{
	/**
	 * Reads a sequence of bytes from this channel into the given buffer.
	 *
	 * <p> This method initiates an asynchronous read operation to read a
	 * sequence of bytes from this channel into the given buffer. The {@code
	 * handler} parameter is a completion handler that is invoked when the read
	 * operation completes (or fails). The result passed to the completion
	 * handler is the number of bytes read or {@code -1} if no bytes could be
	 * read because the channel has reached end-of-stream.
	 *
	 * <p> If a timeout is specified and the timeout elapses before the operation
	 * completes then the operation completes with the exception {@link
	 * InterruptedByTimeoutException}. Where a timeout occurs, and the
	 * implementation cannot guarantee that bytes have not been read, or will not
	 * be read from the channel into the given buffer, then further attempts to
	 * read from the channel will cause an unspecific runtime exception to be
	 * thrown.
	 *
	 * <p> Otherwise this method works in the same manner as the {@link
	 * AsynchronousByteChannel#read(ByteBuffer,Object,CompletionHandler)}
	 * method.
	 *
	 * @param   dst
	 *          The buffer into which bytes are to be transferred
	 * @param   timeout
	 *          The timeout, or {@code 0L} for no timeout
	 * @param   unit
	 *          The time unit of the {@code timeout} argument
	 * @param   attachment
	 *          The object to attach to the I/O operation; can be {@code null}
	 * @param   handler
	 *          The handler for consuming the result
	 *
	 * @throws  IllegalArgumentException
	 *          If the {@code timeout} parameter is negative or the buffer is
	 *          read-only
	 * @throws  ReadPendingException
	 *          If a read operation is already in progress on this channel
	 * @throws  ShutdownChannelGroupException
	 *          If the channel group is shutdown
	 */
	public abstract <A> void read(ByteBuffer dst,
																long timeout,
																TimeUnit unit,
																A attachment,
																CompletionHandler<Integer, ? super A> handler);

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
	 * Writes a sequence of bytes to this channel from the given buffer.
	 *
	 * <p> This method initiates an asynchronous write operation to write a
	 * sequence of bytes to this channel from the given buffer. The {@code
	 * handler} parameter is a completion handler that is invoked when the write
	 * operation completes (or fails). The result passed to the completion
	 * handler is the number of bytes written.
	 *
	 * <p> If a timeout is specified and the timeout elapses before the operation
	 * completes then it completes with the exception {@link
	 * InterruptedByTimeoutException}. Where a timeout occurs, and the
	 * implementation cannot guarantee that bytes have not been written, or will
	 * not be written to the channel from the given buffer, then further attempts
	 * to write to the channel will cause an unspecific runtime exception to be
	 * thrown.
	 *
	 * <p> Otherwise this method works in the same manner as the {@link
	 * AsynchronousByteChannel#write(ByteBuffer,Object,CompletionHandler)}
	 * method.
	 *
	 * @param   src
	 *          The buffer from which bytes are to be retrieved
	 * @param   timeout
	 *          The timeout, or {@code 0L} for no timeout
	 * @param   unit
	 *          The time unit of the {@code timeout} argument
	 * @param   attachment
	 *          The object to attach to the I/O operation; can be {@code null}
	 * @param   handler
	 *          The handler for consuming the result
	 *
	 * @throws  IllegalArgumentException
	 *          If the {@code timeout} parameter is negative
	 * @throws  WritePendingException
	 *          If a write operation is already in progress on this channel
	 * @throws  ShutdownChannelGroupException
	 *          If the channel group is shutdown
	 */
	public abstract <A> void write(ByteBuffer src,
																 long timeout,
																 TimeUnit unit,
																 A attachment,
																 CompletionHandler<Integer, ? super A> handler);

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
