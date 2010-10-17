#ifndef JPERIPHERAL_SERIALPORTHELPER_H
#define JPERIPHERAL_SERIALPORTHELPER_H

#include "jace/namespace.h"

#include "jace/peer/jperipheral/SerialChannel.h"
#include "jace/proxy/jperipheral/SerialPort.h"
#include "jace/proxy/jperipheral/SerialChannel_NativeListener.h"
#include "jace/proxy/jperipheral/SerialChannel_SerialFuture.h"

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
	class SingleThreadExecutor;
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
	 * Returns the NativeListener associated with the operation.
	 *
	 * @return the NativeListener associated with the operation.
	 */
	::jace::proxy::jperipheral::SerialChannel_NativeListener* getListener();
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
	 * Returns the worker thread associated with the operation.
	 *
	 * @return the worker thread associated with the operation
	 */
	SingleThreadExecutor& getWorkerThread();
	/**
	 * Returns the serial port associated with the operation.
	 *
	 * @return the serial port thread associated with the operation
	 */
	HANDLE& getPort();
	/**
	 * Sets the NativeListener associated with the operation.
	 *
	 * @param listener the NativeListener associated with the operation.
	 */
	void setListener(::jace::proxy::jperipheral::SerialChannel_NativeListener listener);
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
	 * @param workerThread the thread that will execute the task
	 * @param port the comport
	 */
	Task(SingleThreadExecutor& workerThread, HANDLE& port);
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
	 * The NativeListener associated with the operation.
	 */
	::jace::proxy::jperipheral::SerialChannel_NativeListener* listener;
	/**
	 * The serial port.
	 */
	HANDLE& port;
	/**
	 * The worker thread associated with the operation.
	 */
	SingleThreadExecutor& workerThread;
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
 * A worker thread operating off an unbounded queue that only executes
 * one workload at a time.
 */
class SingleThreadExecutor
{
public:
	/**
	 * Creates a new SingleThreadExecutor.
	 */
	SingleThreadExecutor();
	~SingleThreadExecutor();

	/**
	 * Executes a task at some time in the future.
	 *
	 * @param task the task
	 */
	void execute(boost::shared_ptr<Task> task);
	/**
	 * Initiates an orderly shutdown in which previously
	 * submitted tasks are executed, but no new tasks will be accepted.
	 * Invocation has no additional effect if already shut down.
	 */
	void shutdown();
	/**
	 * Blocks until all tasks have completed execution after a shutdown request.
	 */
	void awaitTermination();

private:
	/**
	 * The function executed by the thread.
	 */
	void run();

	boost::thread* thread;
	boost::mutex mutex;
	boost::condition onRunning;
	boost::condition onShutdown;
	boost::condition onStateChanged;
	bool running;
	bool shutdownRequested;
	/**
	 * A list of Tasks to execute.
	 */
	std::list<boost::shared_ptr<Task>> tasks;
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

	/**
	 * Returns the worker thread that executes read operations.
	 *
	 * @return the worker thread that executes read operations
	 */
	SingleThreadExecutor& getReadThread();

	/**
	 * Returns the worker thread that executes write operations.
	 *
	 * @return the worker thread that executes write operations
	 */
	SingleThreadExecutor& getWriteThread();
private:
	/**
	 * Prevent assignment.
	 */
	SerialPortContext& operator=(const SerialPortContext&);

	HANDLE port;

	// DESIGN: We need one thread per cancelable operation because CancelIo()
	//         cancels all operations associated with a thread.
	SingleThreadExecutor readThread;
	SingleThreadExecutor writeThread;
};

/**
 * Returns the serial port context.
 */
SerialPortContext* getContext(::jace::proxy::jperipheral::SerialPort port);
/**
 * Returns the serial port context.
 */
SerialPortContext* getContext(::jace::proxy::jperipheral::SerialChannel channel);
/**
 * Returns the Task associated with a Future.
 */
boost::shared_ptr<Task>* getContext(::jace::proxy::jperipheral::SerialChannel_SerialFuture future);

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