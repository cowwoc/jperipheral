#include "jperipheral/Worker.h"
using jperipheral::Worker;
using jperipheral::CancelIoExType;

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

#include <iostream>
using std::wcerr;
using std::endl;

#pragma warning(push)
#pragma warning(disable: 4103 4244 4512)
#include "boost/timer.hpp"
#pragma warning(pop)


Worker* jperipheral::worker;
CancelIoExType jperipheral::cancelIoEx;

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
			task->onFailure(lastError);
			delete overlappedContainer;
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

	HMODULE hKernel32 = LoadLibrary(L"kernel32.dll");
	if (hKernel32 == 0)
		jperipheral::cancelIoEx = 0;
	else
		jperipheral::cancelIoEx = (jperipheral::CancelIoExType) GetProcAddress(hKernel32, "CancelIoEx");
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
