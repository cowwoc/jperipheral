package org.jperipheral;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.InterruptedByTimeoutException;
import java.nio.channels.ReadPendingException;
import java.nio.channels.ShutdownChannelGroupException;
import java.nio.channels.WritePendingException;
import java.util.concurrent.TimeUnit;

/**
 * An AsynchronousByteChannel whose operations may be interrupted.
 *
 * @author Gili Tzabari
 */
public interface InterruptibleByteChannel
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
	 * @param   <A>
	 *          The attachment type
	 * @param   target
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
	 *          If the channel does not allow more than one read to be outstanding
	 *          and a previous read has not completed
	 * @throws  ShutdownChannelGroupException
	 *          If the channel is associated with a {@link AsynchronousChannelGroup
	 *          group} that has terminated
	 * @throws  UnsupportedOperationException
	 *          If the implementation does not support this operation
	 */
	 <A> void read(ByteBuffer target, long timeout, TimeUnit unit, A attachment,
								 CompletionHandler<Integer, ? super A> handler)
		throws IllegalArgumentException, ReadPendingException, ShutdownChannelGroupException;

	/**
	 * Writes a sequence of bytes to this channel from the given buffer. <i>(optional operation)</i>
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
	 * AsynchronousByteChannel#write(ByteBuffer,Object,CompletionHandler,boolean)}
	 * method.
	 *
	 * @param   <A>
	 *          The attachment type
	 * @param   source
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
	 *          If the channel does not allow more than one write to be outstanding
	 *          and a previous write has not completed
	 * @throws  ShutdownChannelGroupException
	 *          If the channel is associated with a {@link AsynchronousChannelGroup
	 *          group} that has terminated
	 * @throws  UnsupportedOperationException
	 *          If the implementation does not support this operation
	 */
	 <A> void write(ByteBuffer source, final long timeout, final TimeUnit unit, A attachment,
									CompletionHandler<Integer, ? super A> handler);

	/**
	 * Closes this channel. The underlying channel might take an arbitrary amount of time to cancel
	 * outstanding operations. As such, this method returns immediately and a completion handler
	 * is notified when the operation completes.
	 *
	 * <p>  This method otherwise behaves exactly as specified by {@link
	 * AsynchronousByteChannel#close()}.
	 *
	 * @param   <A>
	 *          The attachment type
	 * @param   attachment
	 *          The object to attach to the I/O operation; can be {@code null}
	 * @param   handler
	 *          The handler for consuming the result
	 */
	 <A> void close(A attachment, CompletionHandler<Void, ? super A> handler);
}
