#ifndef JPERIPHERAL_SERIALPORTHELPER_H
#define JPERIPHERAL_SERIALPORTHELPER_H

#include "jace/namespace.h"

#include "jace/peer/org/jperipheral/SerialChannel.h"
#include "jace/proxy/org/jperipheral/SerialPort.h"
#include "jace/proxy/java/nio/channels/CompletionHandler.h"

#define WIN32_LEAN_AND_MEAN
#include <windows.h>

#pragma warning(push)
#pragma warning(disable: 4103 4244 4512)
#include <boost/thread/thread.hpp>
#include <boost/thread/mutex.hpp>
#include <boost/thread/condition.hpp>
#include <boost/shared_ptr.hpp>
#include <boost/enable_shared_from_this.hpp>
#include <boost/timer.hpp>
#pragma warning(pop)

#include <list>


namespace jperipheral
{
	class SerialPortContext;
}

BEGIN_NAMESPACE_1(jperipheral)

#define WIDEN2(x) L ## x
#define WIDEN(x) WIDEN2(x)
#define L__FILE__ WIDEN(__FILE__)

/**
 * Links an OVERLAPPED structure to user data.
 */
template <typename T>
class OverlappedContainer
{
public:
	/**
	 * Creates a new OverlappedContainer.
	 *
	 * @param data the user data
	 */
	OverlappedContainer(boost::shared_ptr<T> _data):
		data(_data), overlapped()
	{}

	/**
	 * Converts from a pointer to the overlapped member to the enclosing object.
	 *
	 * @param overlapped a pointer to the overlapped member
	 * @return a pointer to the enclosing object
	 */
	static OverlappedContainer<T>* fromOverlapped(const OVERLAPPED* overlapped)
	{
		return reinterpret_cast<OverlappedContainer<T>*>((char*) overlapped - offsetof(OverlappedContainer, overlapped));
	}

	/**
	 * Returns the overlapped structure.
	 *
	 * @return the overlapped structure
	 */
	OVERLAPPED& getOverlapped()
	{
		return overlapped;
	}

	/**
	 * Returns the user data.
	 */
	boost::shared_ptr<T> getData() const
	{
		return data;
	}
private:
	/**
	 * Prevent assignment.
	 */
	OverlappedContainer& operator=(const OverlappedContainer&);

	/**
	 * The OVERLAPPED structure associated with the operation.
	 */
	OVERLAPPED overlapped;
	/**
	 * The user data.
	 */
	const boost::shared_ptr<T> data;
};

/**
 * A task that updates a Future.
 */
class Task: public boost::enable_shared_from_this<Task>
{
public:
	/**
	 * Possible completion keys.
	 */
	enum CompletionKey
	{
		/**
		 * Handle the completion of an existing operation. Task is valid.
		 */
		COMPLETION,
		/**
		 * Shutdown the worker thread. Task is invalid.
		 */
		SHUTDOWN
	};

	virtual ~Task();
	/**
	 * Copy constructor.
	 */
	Task(const Task&);
	/**
	 * Invokes an I/O operation.
	 */
	virtual void run() = 0;
	/**
	 * Invoked after the operation completes successfully.
	 *
	 * @param bytesTransferred the number of bytes transferred
	 */
	virtual void onSuccess(int bytesTransferred) = 0;
	/**
	 * Returns the attachment associated with the completion handler.
	 *
	 * @return the attachment associated with the completion handler
	 */
	::jace::proxy::java::lang::Object* getAttachment();
	/**
	 * Returns the CompletionHandler associated with the operation.
	 *
	 * @return the CompletionHandler associated with the operation.
	 */
	::jace::proxy::java::nio::channels::CompletionHandler* getHandler();
	/**
	 * Returns the maximum number of milliseconds to wait before throwing InterruptedByTimeoutException.
	 *
	 * @return the maximum number of milliseconds to wait before throwing InterruptedByTimeoutException.
	 *         0 means "return immediately". JLong::MAX_VALUE means "wait forever"
	 */
	::jace::proxy::types::JLong getTimeout() const;
	/**
	 * Returns the number of milliseconds that have elapsed since the Task was run.
	 *
	 * @return the elapsed time in milliseconds
	 */
	long getTimeElapsed() const;
	/**
	 * Returns the serial port associated with the operation.
	 *
	 * @return the serial port thread associated with the operation
	 */
	HANDLE& getPort();
	/**
	 * Sets the maximum number of milliseconds to wait before throwing InterruptedByTimeoutException.
	 *
	 * @param timeout the maximum number of milliseconds to wait before throwing InterruptedByTimeoutException
	 */
	void setTimeout(::jace::proxy::types::JLong timeout);
protected:
	/**
	 * Creates a new Task.
	 *
	 * @param port the comport
	 * @param attachment the attachment to pass into handler
	 * @param handler the CompletionHandler associated with the operation.
	 */
	Task(HANDLE& port, ::jace::proxy::java::lang::Object attachment, ::jace::proxy::java::nio::channels::CompletionHandler handler);
	/**
	 * Sets the java buffer associated with the operation.
	 *
	 * @param javaBuffer the java buffer associated with the operation
	 */
	void setJavaBuffer(::jace::proxy::java::nio::ByteBuffer* javaBuffer);

	/** 
	 * The native buffer associated with the operation.
	 */
	::jace::proxy::java::nio::ByteBuffer* nativeBuffer;
	/** 
	 * The Java ByteBuffer associated with the operation.
	 */
	::jace::proxy::java::nio::ByteBuffer* javaBuffer;
	/**
	 * The maximum number of milliseconds to wait before throwing InterruptedByTimeoutException.
	 * 0 means "wait forever" and Long::MAX_VALUE() means wait forever.
	 */
	::jace::proxy::types::JLong timeout;
	/**
	 * The attachment associated with the CompletionHandler.
	 */
	::jace::proxy::java::lang::Object* attachment;
	/**
	 * The CompletionHandler associated with the operation.
	 */
	::jace::proxy::java::nio::channels::CompletionHandler* handler;
	/**
	 * The serial port.
	 */
	HANDLE& port;
	/**
	 * Measures how long a task has been running.
	 */
	boost::timer timer;

private:
	/**
	 * Prevent assignment.
	 */
	Task& operator=(const Task&);
};

/**
 * Data associated with the serial port.
 */
class SerialPortContext
{
public:
	/**
	 * Creates a new SerialPortContext.
	 *
	 * @param port the serial port
	 */
	SerialPortContext(HANDLE port);

	/**
	 * Destroys the SerialPortContext.
	 */
	~SerialPortContext();

	/**
	 * Returns the serial port.
	 */
	HANDLE& getPort();
private:
	/**
	 * Prevent assignment.
	 */
	SerialPortContext& operator=(const SerialPortContext&);

	HANDLE port;
};

/**
 * Returns the serial port context.
 */
SerialPortContext* getContext(::jace::proxy::org::jperipheral::SerialChannel channel);

/**
 * Returns the String representation of the current source-code position.
 */
std::wstring getSourceCodePosition(wchar_t* file, int line);

/**
 * Returns the String representation of GetLastError().
 */
std::wstring getErrorMessage(DWORD errorCode);

END_NAMESPACE_1(jperipheral)

#endif