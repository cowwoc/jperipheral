#include "jperipheral/Worker.h"
using jperipheral::Worker;

#include "jace/proxy/java/lang/AssertionError.h"
using jace::proxy::java::lang::AssertionError;

#include <string>
using std::wstring;

#include "jperipheral/SerialPortHelper.h"
using jperipheral::getErrorMessage;
using jperipheral::Task;
using jperipheral::getSourceCodePosition;

#include "jace/Jace.h"
using jace::toWString;

#include "jace/proxy/java/lang/Long.h"
using jace::proxy::java::lang::Long;

#include "jace/proxy/java/lang/Integer.h"
using jace::proxy::java::lang::Integer;


#include "jace/proxy/java/lang/String.h"
using jace::proxy::java::lang::String;

#include "jace/proxy/java/io/IOException.h"
using jace::proxy::java::io::IOException;

#include "jace/proxy/java/lang/Throwable.h"
using jace::proxy::java::lang::Throwable;

#include "jace/proxy/java/nio/ByteBuffer.h"
using jace::proxy::java::nio::ByteBuffer;

#include "jace/proxy/java/nio/channels/AsynchronousCloseException.h"
using jace::proxy::java::nio::channels::AsynchronousCloseException;

#include <iostream>
using std::cerr;
using std::endl;

#pragma warning(push)
#pragma warning(disable: 4103 4244 4512)
#include "boost/timer.hpp"
#pragma warning(pop)


Worker* jperipheral::worker;

/**
 * Executes any pending tasks.
 */
void Worker::run()
{
	DWORD bytesTransfered;
	ULONG_PTR completionKey;
	OVERLAPPED* overlapped;

	{
		boost::mutex::scoped_lock lock(mutex);
		running.notify_all();
	}
	bool shutdownRequested = false;
	do
	{
		// Task destructors invoke jace::attach()
		jace::detach();

		if (!GetQueuedCompletionStatus(completionPort, &bytesTransfered, &completionKey,
			&overlapped, INFINITE))
		{
			DWORD lastError = GetLastError();
			OverlappedContainer<Task>* overlappedContainer = OverlappedContainer<Task>::fromOverlapped(overlapped);
			boost::shared_ptr<Task> task(overlappedContainer->getData());

			// Get and clear current errors on the port.
			DWORD errors;
			if (!ClearCommError(completionPort, &errors, 0))
			{
				DWORD lastError2 = GetLastError();
				if (lastError2 == ERROR_INVALID_HANDLE)
				{
					// The operation failed because the port was closed, not because of an error flag
					errors = 0;
				}
				else
				{
					task->getHandler()->failed(jace::java_new<IOException>(
						L"ClearCommError() failed with error: " + getErrorMessage(lastError2)),
						*task->getAttachment());
					delete overlappedContainer;
					continue;
				}
			}

			if (errors & CE_BREAK)
			{
			  // Seems to be related to SetCommBreak().
			  // 
			  // REFERENCE: http://msdn.microsoft.com/en-us/library/windows/desktop/aa363433%28v=vs.85%29.aspx
				task->getHandler()->failed(IOException(jace::java_new<IOException>(
					wstring(L"The hardware detected a break condition"))), *task->getAttachment());
				delete overlappedContainer;
				continue;
			}
			else if (errors & CE_FRAME)
			{
				task->getHandler()->failed(IOException(jace::java_new<IOException>(
					wstring(L"The hardware detected a framing error"))), *task->getAttachment());
				delete overlappedContainer;
				continue;
			}
			else if (errors & CE_OVERRUN)
			{
				task->getHandler()->failed(IOException(jace::java_new<IOException>(
					wstring(L"A character-buffer overrun has occurred. The next character is lost."))), 
					*task->getAttachment());
				delete overlappedContainer;
				continue;
			}
			else if (errors & CE_RXOVER)
			{
				task->getHandler()->failed(IOException(jace::java_new<IOException>(
					wstring(L"An input buffer overflow has occurred. There is either no room in the input buffer, "
					L"or a character was received after the end-of-file (EOF) character."))), 
					*task->getAttachment());
				delete overlappedContainer;
				continue;
			}
			else if (errors & CE_RXPARITY)
			{
				task->getHandler()->failed(IOException(jace::java_new<IOException>(
					wstring(L"The hardware detected a parity error."))), *task->getAttachment());
				delete overlappedContainer;
				continue;
			}
			switch (lastError)
			{
				case ERROR_HANDLE_EOF:
					break;
				case ERROR_OPERATION_ABORTED:
				{
					// The port was closed
					task->getHandler()->failed(AsynchronousCloseException(
						jace::java_new<AsynchronousCloseException>()), *task->getAttachment());
					delete overlappedContainer;
					break;
				}
				default:
				{
					task->getHandler()->failed(IOException(jace::java_new<IOException>(
						L"GetQueuedCompletionStatus() failed with error: " + getErrorMessage(lastError))), *task->getAttachment());
					delete overlappedContainer;
					break;
				}
			}
		}
		else
		{
			OverlappedContainer<Task>* overlappedContainer = OverlappedContainer<Task>::fromOverlapped(overlapped);
			boost::shared_ptr<Task> task(overlappedContainer->getData());
			switch (completionKey)
			{
				case Task::COMPLETION:
				{
					task->onSuccess(bytesTransfered);
					delete overlappedContainer;
					break;
				}
				case Task::SHUTDOWN:
				{
					//
					// Someone used PostQueuedCompletionStatus() to post an I/O packet with a shutdown CompletionKey.
					//
					shutdownRequested = true;
					break;
				}
				default:
				{
					task->getHandler()->failed(AssertionError(jace::java_new<AssertionError>(
						wstring(L"completionKey==") + toWString(completionKey))), *task->getAttachment());
					delete overlappedContainer;
					break;
				}
			}
		}
	} while (!shutdownRequested);

	// Task destructors invoke jace::attach()
	jace::detach();
}

Worker::Worker()
{
	// Empirical tests show that handling I/O completion costs 0.3 milliseconds, making it hard to justify
	// the use of multiple threads.
	completionPort = CreateIoCompletionPort(INVALID_HANDLE_VALUE, 0, 0, 0);
	if (completionPort==0)
	{
		DWORD lastError = GetLastError();
		throw AssertionError(jace::java_new<AssertionError>(L"CreateIoCompletionPort() failed with error: " + getErrorMessage(lastError)));
	}
	
	boost::mutex::scoped_lock lock(mutex);
	thread = new boost::thread(boost::bind(&Worker::run, this));
	running.wait(lock);
}

Worker::~Worker()
{
	if (!PostQueuedCompletionStatus(completionPort, 0, Task::SHUTDOWN, 0))
	{
		DWORD lastError = GetLastError();
		throw IOException(jace::java_new<IOException>(getSourceCodePosition(WIDEN(__FILE__), __LINE__) + 
			L" PostQueuedCompletionStatus() failed with error: " + getErrorMessage(lastError)));
	}
	thread->join();
	delete thread;
	if (!CloseHandle(completionPort))
	{
		DWORD lastError = GetLastError();
		throw IOException(jace::java_new<IOException>(L"CloseHandle(completionPort) failed with error: " + 
			getErrorMessage(lastError)));
	}
}

/**
 * @see http://java.sun.com/javase/6/docs/technotes/guides/jni/spec/invocation.html#library_version
 */
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM*, void*)
{
	jperipheral::worker = new Worker();
	return JNI_VERSION_1_6;
}

/**
 * @see http://java.sun.com/javase/6/docs/technotes/guides/jni/spec/invocation.html#library_version
 */
extern "C" JNIEXPORT void JNICALL JNI_OnUnload(JavaVM*, void*)
{
	delete jperipheral::worker;
	jperipheral::worker = 0;
}
