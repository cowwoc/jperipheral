package org.jperipheral;

import java.nio.CharBuffer;
import java.nio.channels.AsynchronousChannel;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.CompletionHandler;
import java.nio.channels.InterruptedByTimeoutException;
import java.nio.channels.ReadPendingException;
import java.nio.channels.ShutdownChannelGroupException;
import java.nio.channels.WritePendingException;
import java.util.concurrent.Future;

/**
 * An asynchronous channel that can read and write characters.
 *
 * <p> Some channels may not allow more than one read or write to be outstanding at any given time.
 * If a thread invokes a read method before a previous read operation has completed then a {@link ReadPendingException}
 * might be thrown. Similarly, if a write method is invoked before a previous write has completed
 * then {@link WritePendingException} might be thrown. Whether or not other kinds of I/O operations
 * may proceed concurrently with a read operation depends upon the type of the channel.
 *
 * <p> Note that {@link java.nio.CharBuffer CharBuffers} are not safe for use by multiple concurrent
 * threads. When a read or write operation is initiated then care must be taken to ensure that the
 * buffer is not accessed until the operation completes.
 *
 * <h4>Optional methods</h4>
 *
 * <p> Some implementations may not perform one or more of these operations, throwing a runtime
 * exception (
 * <code>UnsupportedOperationException</code>) if they are attempted. Implementations must specify
 * in their documentation which optional operations they support. </p>
 *
 * <h4>Timeouts</h4>
 *
 * <p> The {@link
 * #read(java.nio.CharBuffer, java.lang.Object, java.nio.channels.CompletionHandler) read} and
 * {@link #write(java.nio.CharBuffer, java.lang.Object, java.nio.channels.CompletionHandler) write}
 * methods defined by this class allow a timeout to be specified when initiating a read or write
 * operation. If the timeout elapses before an operation completes then the operation completes with
 * the exception {@link
 * InterruptedByTimeoutException}. A timeout may leave the channel, or the underlying connection, in
 * an inconsistent state. Where the implementation cannot guarantee that bytes have not been read
 * from the channel then it puts the channel into an implementation specific <em>error state</em>. A
 * subsequent attempt to initiate a {@code read} operation causes an unspecified runtime exception to
 * be thrown. Similarly if a {@code write} operation times out and the implementation cannot
 * guarantee bytes have not been written to the channel then further attempts to {@code write} to the
 * channel cause an unspecified runtime exception to be thrown. When a timeout elapses then the state
 * of the {@link CharBuffer}, or the sequence of buffers, for the I/O operation is not defined.
 * Buffers should be discarded or at least care must be taken to ensure that the buffers are not
 * accessed while the channel remains open. All methods that accept timeout parameters treat values
 * less than or equal to zero to mean that the I/O operation times out immediately, and a value of
 * <code>Long.MAX_VALUE</code> to mean that the I/O operation does not timeout.
 *
 * @author Gili Tzabari
 */
public interface AsynchronousCharChannel extends AsynchronousChannel
{
	/**
	 * Reads a sequence of characters from this channel into the given buffer.
	 *
	 * <p> This method initiates an asynchronous read operation to read a sequence of characters from
	 * this channel into the given buffer. The {@code
	 * handler} parameter is a completion handler that is invoked when the read operation completes
	 * (or fails). The result passed to the completion handler is the number of characters read or {@code -1}
	 * if no characters could be read because the channel has reached end-of-stream.
	 *
	 * <p> The read operation may read up to <i>r</i> characters from the channel, where <i>r</i> is
	 * the number of characters remaining in the buffer, that is,
	 * {@code target.remaining()} at the time that the read is attempted. Where <i>r</i> is 0, the
	 * read operation completes immediately with a result of
	 * {@code 0} without initiating an I/O operation.
	 *
	 * <p> Suppose that a character sequence of length <i>n</i> is read, where
	 * <tt>0</tt>&nbsp;<tt>&lt;</tt>&nbsp;<i>n</i>&nbsp;<tt>&lt;=</tt>&nbsp;<i>r</i>. This character
	 * sequence will be transferred into the buffer so that the first character in the sequence is at
	 * index <i>p</i> and the last character is at index
	 * <i>p</i>&nbsp;<tt>+</tt>&nbsp;<i>n</i>&nbsp;<tt>-</tt>&nbsp;<tt>1</tt>, where <i>p</i> is the
	 * buffer's position at the moment the read is performed. Upon completion the buffer's position
	 * will be equal to <i>p</i>&nbsp;<tt>+</tt>&nbsp;<i>n</i>; its limit will not have changed.
	 *
	 * <p> Buffers are not safe for use by multiple concurrent threads so care should be taken to not
	 * to access the buffer until the operation has completed.
	 *
	 * <p> This method may be invoked at any time. Some channel types may not allow more than one read
	 * to be outstanding at any given time. If a thread initiates a read operation before a previous
	 * read operation has completed then a {@link ReadPendingException} might be thrown.
	 *
	 * @param <A> The attachment type
	 * @param target The buffer into which characters are to be transferred
	 * @param attachment The object to attach to the I/O operation; can be {@code null}
	 * @param handler The completion handler
	 *
	 * @throws IllegalArgumentException If the buffer is read-only
	 * @throws ReadPendingException If the channel does not allow more than one read to be outstanding
	 * and a previous read has not completed
	 * @throws ShutdownChannelGroupException If the channel is associated with a {@link AsynchronousChannelGroup
	 *          group} that has terminated
	 */
	 <A> void read(CharBuffer target, A attachment,
		CompletionHandler<Integer, ? super A> handler)
		throws IllegalArgumentException, ReadPendingException, ShutdownChannelGroupException;

	/**
	 * Reads a sequence of characters from this channel into the given buffer.
	 *
	 * <p> This method initiates an asynchronous read operation to read a
	 * sequence of characters from this channel into the given buffer. The method
	 * behaves in exactly the same manner as the {@link
	 * #read(CharBuffer,Object,CompletionHandler)
	 * read(CharBuffer,Object,CompletionHandler)} method except that instead
	 * of a completion handler, this method returns a {@code Future}
	 * representing the pending result. The {@link Future#get() get} method
	 * returns the number of characters read or {@code -1} if no characters could be
	 * read because the channel has reached end-of-stream.
	 *
	 * @param   target
	 *          The buffer into which characters are to be transferred
	 *
	 * @return  A Future representing the result of the operation
	 *
	 * @throws  IllegalArgumentException
	 *          If the buffer is read-only
	 * @throws  ReadPendingException
	 *          If the channel does not allow more than one read to be outstanding
	 *          and a previous read has not completed
	 */
	Future<Integer> read(CharBuffer target)
		throws IllegalArgumentException, ReadPendingException;

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
	 <A> void readLine(A attachment, CompletionHandler<String, ? super A> handler)
		throws IllegalArgumentException, ReadPendingException, ShutdownChannelGroupException;

	/**
	 * Reads a line of characters from this channel into the given buffer.
	 *
	 * <p> This method initiates an asynchronous read operation to read a
	 * line of characters from this channel into the given buffer. A line
	 * is considered to be terminated by any one of a line feed <code>'\n'</code>,
	 * a carriage return <code>'\r'</code>, or a carriage return followed
	 * immediately by a linefeed. The method behaves in exactly the same manner
	 * as the {@link #readLine(java.lang.Object, java.nio.channels.CompletionHandler)
	 * readLine(Object, CompletionHandler)} method except that instead
	 * of a completion handler, this method returns a {@code Future}
	 * representing the pending result. The {@link Future#get() get} method
	 * returns the line of characters that were read (excluding termination characters)
	 * or {@code null} if no characters could be read because the channel has reached end-of-stream.
	 *
	 * @return A Future representing the result of the operation
	 *
	 * @throws IllegalArgumentException
	 *         If the buffer is read-only
	 * @throws ReadPendingException
	 *         If the channel does not allow more than one read to be outstanding
	 *         and a previous read has not completed
	 */
	Future<String> readLine()
		throws IllegalArgumentException, ReadPendingException;

	/**
	 * Writes a sequence of characters to this channel from the given buffer.
	 *
	 * <p> This method initiates an asynchronous write operation to write a
	 * sequence of characters to this channel from the given buffer. The {@code
	 * handler} parameter is a completion handler that is invoked when the write
	 * operation completes (or fails). The result passed to the completion
	 * handler is the number of characters written.
	 *
	 * <p> The write operation may write up to <i>r</i> characters to the channel,
	 * where <i>r</i> is the number of characters remaining in the buffer, that is,
	 * {@code src.remaining()} at the time that the write is attempted. Where
	 * <i>r</i> is 0, the write operation completes immediately with a result of
	 * {@code 0} without initiating an I/O operation.
	 *
	 * <p> Suppose that a character sequence of length <i>n</i> is written, where
	 * <tt>0</tt>&nbsp;<tt>&lt;</tt>&nbsp;<i>n</i>&nbsp;<tt>&lt;=</tt>&nbsp;<i>r</i>.
	 * This character sequence will be transferred from the buffer starting at index
	 * <i>p</i>, where <i>p</i> is the buffer's position at the moment the
	 * write is performed; the index of the last character written will be
	 * <i>p</i>&nbsp;<tt>+</tt>&nbsp;<i>n</i>&nbsp;<tt>-</tt>&nbsp;<tt>1</tt>.
	 * Upon completion the buffer's position will be equal to
	 * <i>p</i>&nbsp;<tt>+</tt>&nbsp;<i>n</i>; its limit will not have changed.
	 *
	 * <p> Buffers are not safe for use by multiple concurrent threads so care
	 * should be taken to not to access the buffer until the operation has completed.
	 *
	 * <p> This method may be invoked at any time. Some channel types may not
	 * allow more than one write to be outstanding at any given time. If a thread
	 * initiates a write operation before a previous write operation has
	 * completed then a {@link WritePendingException} might be thrown.
	 * 
	 * The handler fails with <code>MalformedInputException</code> if source is not a legal
	 * sixteen-bit Unicode sequence or <code>UnmappableCharacterException</code> if source is valid
	 * but cannot be mapped to an output byte sequence.
	 *
	 * @param <A>
	 *        The attachment type
	 * @param source
	 *        The buffer from which characters are to be retrieved
	 * @param attachment
	 *        The object to attach to the I/O operation; can be {@code null}
	 * @param handler
	 *        The completion handler object
	 *
	 * @throws WritePendingException
	 *         If the channel does not allow more than one write to be outstanding
	 *         and a previous write has not completed
	 * @throws ShutdownChannelGroupException
	 *         If the channel is associated with a {@link AsynchronousChannelGroup
	 *         group} that has terminated
	 */
	 <A> void write(CharBuffer source,
		A attachment,
		CompletionHandler<Integer, ? super A> handler)
		throws WritePendingException, ShutdownChannelGroupException;

	/**
	 * Writes a sequence of characters to this channel from the given buffer.
	 *
	 * <p> This method initiates an asynchronous write operation to write a
	 * sequence of characters to this channel from the given buffer. The method
	 * behaves in exactly the same manner as the {@link
	 * #write(CharBuffer,Object,CompletionHandler)
	 * write(CharBuffer,Object,CompletionHandler)} method except that instead
	 * of a completion handler, this method returns a {@code Future}
	 * representing the pending result. The {@link Future#get() get} method
	 * returns the number of characters written.
	 *
	 * The future fails with <code>MalformedInputException</code> if source is not a legal
	 * sixteen-bit Unicode sequence or <code>UnmappableCharacterException</code> if source is valid
	 * but cannot be mapped to an output byte sequence.
	 * 
	 * @param source
	 *        The buffer from which characters are to be retrieved
	 *
	 * @return A Future representing the result of the operation
	 *
	 * @throws WritePendingException
	 *         If the channel does not allow more than one write to be outstanding
	 *         and a previous write has not completed
	 */
	Future<Integer> write(CharBuffer source)
		throws WritePendingException;
}
