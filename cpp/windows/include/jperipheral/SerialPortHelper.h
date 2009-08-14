#ifndef JPERIPHERAL_SERIALPORTHELPER_H
#define JPERIPHERAL_SERIALPORTHELPER_H

#ifndef JACE_NAMESPACE_H
#include "jace/namespace.h"
#endif

#ifndef JACE_PEER_JPERIPHERAL_SERIALPORT_H
#include "jace/peer/jperipheral/SerialPort.h"
#endif

#ifndef JACE_PEER_JPERIPHERAL_SERIALCHANNEL_H
#include "jace/peer/jperipheral/SerialChannel.h"
#endif

#ifndef JACE_PEER_JPERIPHERAL_SERIALPORT_WINDOWSOS_H
#include "jace/peer/jperipheral/WindowsOS.h"
#endif

#ifndef JACE_PROXY_JPERIPHERAL_SERIALCHANNEL_NATIVELISTENER_H
#include "jace/proxy/jperipheral/SerialChannel_NativeListener.h"
#endif

#ifndef JACE_PROXY_JPERIPHERAL_SERIALCHANNEL_SERIALFUTURE_H
#include "jace/proxy/jperipheral/SerialChannel_SerialFuture.h"
#endif

#define WIN32_LEAN_AND_MEAN
#include <windows.h>

#pragma warning(push)
#pragma warning(disable: 4103 4244 4512)
#include <boost/thread/thread.hpp>
#include <boost/thread/mutex.hpp>
#include <boost/thread/condition.hpp>
#pragma warning(pop)

BEGIN_NAMESPACE_1( jperipheral )

#define WIDEN2(x) L ## x
#define WIDEN(x) WIDEN2(x)
#define L__FILE__ WIDEN(__FILE__)


class WorkerThread;

/**
 * A task that updates a Future.
 */
class IoTask
{
public:
	/**
	 * Possible completion keys.
	 */
	enum CompletionKey
	{
		/**
		 * Handle the completion of an existing operation.
		 */
		COMPLETION,
		/**
		 * Shutdown the worker thread.
		 */
		SHUTDOWN
	};

	/**
	 * Creates a new IoTask.
	 *
	 * @param workerThread the thread that will execute the task
	 * @param port the comport
	 * @param javaBuffer the java buffer associated with the operation
	 * @param timeout the maximum number of milliseconds to wait before throwing InterruptedByTimeoutException
	 * @param nativeListener the NativeListener associated with the operation.
	 */
	IoTask(WorkerThread& workerThread, HANDLE port, ::jace::proxy::java::nio::ByteBuffer javaBuffer, DWORD timeout, 
		::jace::proxy::jperipheral::SerialChannel_NativeListener listener);
	/**
	 * Invokes an I/O operation.
	 *
	 * @return false if the task should be deleted
	 */
	virtual bool run() = 0;
	/**
	 * Copies changes from nativeBuffer to javaBuffer.
	 *
	 * @param size the number of bytes to update
	 */
	virtual void updateJavaBuffer(int size) = 0;
	virtual ~IoTask();

	/**
	 * Converts from a pointer to the overlapped member to the enclosing IoTask object.
	 *
	 * @param overlapped a pointer to the overlapped member
	 * @return a pointer to the enclosing IoTask object.
	 */
	static IoTask* fromOverlapped(OVERLAPPED* overlapped);

	/**
	 * The OVERLAPPED structure associated with the operation.
	 */
	OVERLAPPED overlapped;
	/**
	 * The comport.
	 */
	HANDLE port;
	/** 
	 * The native buffer associated with the operation.
	 */
	::jace::proxy::java::nio::ByteBuffer* nativeBuffer;
	/** 
	 * The Java ByteBuffer associated with the operation.
	 */
	::jace::proxy::java::nio::ByteBuffer* javaBuffer;
	/**
	 * The maximum number of milliseconds to wait before throwing InterruptedByTimeoutException,
	 * where 0 means "wait forever"
	 */
	DWORD timeout;
	/**
	 * The NativeListener associated with the operation.
	 */
	::jace::proxy::jperipheral::SerialChannel_NativeListener* listener;
	/**
	 * The worker thread associated with the operation.
	 */
	WorkerThread& thread;
};

/**
 * A thread that only executes one workload at a time.
 */
class WorkerThread
{
public:
	WorkerThread();
	~WorkerThread();

	boost::thread* thread;
	bool shutdownRequested;
	IoTask* workload;
	boost::mutex lock;
	boost::condition workloadChanged;
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

	HANDLE port;
	WorkerThread readThread;
	WorkerThread writeThread;
};

/**
 * Returns the SerialPort handle.
 */
SerialPortContext* getContext(::jace::proxy::jperipheral::SerialPort port);
/**
 * Returns the SerialPort handle.
 */
SerialPortContext* getContext(::jace::proxy::jperipheral::SerialChannel channel);
/**
 * Returns the SerialPort handle.
 */
IoTask* getContext(::jace::proxy::jperipheral::SerialChannel_SerialFuture future);

/**
 * Data associated with the completion port.
 */
class CompletionPortContext
{
public:
	/**
	 * Creates a new CompletionPortContext.
	 */
	CompletionPortContext();

	/**
	 * Creates a new ~CompletionPortContext.
	 */
	~CompletionPortContext();


	HANDLE workerThread;
	HANDLE completionPort;
};

/**
 * Returns the CompletionPort handle.
 */
CompletionPortContext* getCompletionPortContext();

/**
 * Returns the String representation of the current source-code position.
 */
std::wstring getSourceCodePosition(wchar_t* file, int line);

/**
 * Returns the String representation of GetLastError().
 */
std::wstring getErrorMessage(DWORD errorCode);

END_NAMESPACE_1( jperipheral )

#endif