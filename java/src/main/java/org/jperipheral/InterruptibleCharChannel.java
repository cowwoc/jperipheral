package org.jperipheral;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.CompletionHandler;
import java.nio.channels.InterruptedByTimeoutException;
import java.nio.channels.ReadPendingException;
import java.nio.channels.ShutdownChannelGroupException;
import java.nio.channels.WritePendingException;
import java.util.concurrent.TimeUnit;

/**
 * An AsynchronousCharChannel whose operations may be interrupted.
 *
 * @author Gili Tzabari
 */
public interface InterruptibleCharChannel
{
	/**
	 * Reads a sequence of characters from this channel into the given buffer.
	 *
	 * <p> This method initiates an asynchronous read operation to read a
	 * sequence of characters from this channel into the given buffer. The {@code
	 * handler} parameter is a completion handler that is invoked when the read
	 * operation completes (or fails). The result passed to the completion
	 * handler is the number of characters read or {@code -1} if no characters could be
	 * read because the channel has reached end-of-stream.
	 *
	 * <p> If a timeout is specified and the timeout elapses before the operation
	 * completes then the operation completes with the exception {@link
	 * InterruptedByTimeoutException}. Where a timeout occurs, and the
	 * implementation cannot guarantee that characters have not been read, or will not
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
	 *          The buffer into which characters are to be transferred
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
	 *          If the channel is associated with a {@link AsynchronousChannelGroup
	 *          group} that has terminated
	 * @throws  UnsupportedOperationException
	 *          If the implementation does not support this operation
	 */
	 <A> void read(CharBuffer target, long timeout, TimeUnit unit, A attachment,
								 CompletionHandler<Integer, ? super A> handler)
		throws IllegalArgumentException, ReadPendingException, ShutdownChannelGroupException;

	/**
	 * Reads a line of characters from this channel into the given buffer.
	 *
	 * <p> This method initiates an asynchronous read operation to read a
	 * line of characters from this channel into the given buffer. A line
	 * is delimited by any one of a line feed <code>'\n'</code>,
	 * a carriage return <code>'\r'</code>, or a carriage return followed
	 * immediately by a linefeed. The {@code handler} parameter is a completion
	 * handler that is invoked when the read operation completes (or fails).
	 * The result passed to the completion handler is the line of characters
	 * that was read (excluding termination characters) or {@code null} if no
	 * characters could be read because the channel has reached end-of-stream.
	 *
	 * <p> Suppose that a character sequence of length <i>n</i> is read, where
	 * <i>n</i>&nbsp;<tt>&gt;</tt>&nbsp;<tt>0</tt>. This character sequence
	 * will be transferred into the buffer so that the first character in the
	 * sequence is at index <i>p</i> and the last character is at index
	 * <i>p</i>&nbsp;<tt>+</tt>&nbsp;<i>n</i>&nbsp;<tt>-</tt>&nbsp;<tt>1</tt>&nbsp;<tt>-</tt>&nbsp;<i>d</i>,
	 * where <i>p</i> is the buffer's position at the moment the read is
	 * performed and <i>d</i> is the length of the line delimiter. Upon completion
	 * the buffer's position will be equal to
	 * <i>p</i>&nbsp;<tt>+</tt>&nbsp;<i>n</i>&nbsp;<tt>-</tt>&nbsp;<i>d</i>; its limit
	 * will not have changed.
	 *
	 * <p> Buffers are not safe for use by multiple concurrent threads so care
	 * should be taken to not to access the buffer until the operation has
	 * completed.
	 *
	 * <p> This method may be invoked at any time. Some channel types may not
	 * allow more than one read to be outstanding at any given time. If a thread
	 * initiates a read operation before a previous read operation has
	 * completed then a {@link ReadPendingException} might be thrown.
	 *
	 * @param   <A>
	 *          The attachment type
	 * @param   timeout
	 *          The timeout, or {@code 0L} for no timeout
	 * @param   unit
	 *          The time unit of the {@code timeout} argument
	 * @param   attachment
	 *          The object to attach to the I/O operation; can be {@code null}
	 * @param   handler
	 *          The completion handler
	 *
	 * @throws  IllegalArgumentException
	 *          If the buffer is read-only
	 * @throws  ReadPendingException
	 *          If the channel does not allow more than one read to be outstanding
	 *          and a previous read has not completed
	 * @throws  ShutdownChannelGroupException
	 *          If the channel is associated with a {@link AsynchronousChannelGroup
	 *          group} that has terminated
	 */
	 <A> void readLine(long timeout, TimeUnit unit, A attachment,
										 CompletionHandler<String, ? super A> handler)
		throws IllegalArgumentException, ReadPendingException, ShutdownChannelGroupException;

	/**
	 * Writes a sequence of characters to this channel from the given buffer. <i>(optional operation)</i>
	 *
	 * <p> This method initiates an asynchronous write operation to write a
	 * sequence of characters to this channel from the given buffer. The {@code
	 * handler} parameter is a completion handler that is invoked when the write
	 * operation completes (or fails). The result passed to the completion
	 * handler is the number of characters written.
	 *
	 * <p> If a timeout is specified and the timeout elapses before the operation
	 * completes then it completes with the exception {@link
	 * InterruptedByTimeoutException}. Where a timeout occurs, and the
	 * implementation cannot guarantee that characters have not been written, or will
	 * not be written to the channel from the given buffer, then further attempts
	 * to write to the channel will cause an unspecific runtime exception to be
	 * thrown.
	 *
	 * <p> Otherwise this method works in the same manner as the {@link
	 * AsynchronousByteChannel#write(ByteBuffer,Object,CompletionHandler,boolean)}
	 * method.
	 *
	 * The handler fails with <code>MalformedInputException</code> if source is not a legal
	 * sixteen-bit Unicode sequence or <code>UnmappableCharacterException</code> if source is valid
	 * but cannot be mapped to an output byte sequence.
	 *
	 * @param   <A>
	 *          The attachment type
	 * @param   source
	 *          The buffer from which characters are to be retrieved
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
	 <A> void write(CharBuffer source, final long timeout, final TimeUnit unit, A attachment,
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
