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

BEGIN_NAMESPACE_1( jperipheral )

#define WIDEN2(x) L ## x
#define WIDEN(x) WIDEN2(x)
#define L__FILE__ WIDEN(__FILE__)

/**
 * Returns the SerialPort handle.
 */
HANDLE getContext(::jace::proxy::jperipheral::SerialPort port);
/**
 * Returns the SerialPort handle.
 */
HANDLE getContext(::jace::proxy::jperipheral::SerialChannel channel);
/**
 * Returns the SerialPort handle.
 */
HANDLE getContext(::jace::proxy::jperipheral::SerialChannel_SerialFuture future);

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
		 * Run the operation.
		 */
		RUN,
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
	 * @param port the comport
	 * @param javaBuffer the java buffer associated with the operation
	 * @param timeout the maximum number of milliseconds to wait before throwing InterruptedByTimeoutException
	 * @param nativeListener the NativeListener associated with the operation.
	 */
	IoTask(HANDLE port, ::jace::proxy::java::nio::ByteBuffer javaBuffer, DWORD timeout, 
		::jace::proxy::jperipheral::SerialChannel_NativeListener listener);
	/**
	 * Invokes an I/O operation.
	 */
	virtual void run() = 0;
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
};

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