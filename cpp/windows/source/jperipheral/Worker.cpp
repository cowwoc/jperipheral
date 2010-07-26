#include "jperipheral/Worker.h"
using jperipheral::Worker;

#include "jace/proxy/java/lang/AssertionError.h"
using jace::proxy::java::lang::AssertionError;

#include <string>
using std::wstring;

#include "jperipheral/SerialPortHelper.h"
using jperipheral::getErrorMessage;
using jperipheral::IoTask;
using jperipheral::getSourceCodePosition;

#include "jace/Jace.h"
using jace::toWString;

#include "jace/proxy/types/JLong.h"
using jace::proxy::types::JLong;

#include "jace/proxy/java/lang/Integer.h"
using jace::proxy::java::lang::Integer;


#include "jace/proxy/java/lang/String.h"
using jace::proxy::java::lang::String;

#include "jace/proxy/java/io/IOException.h"
using jace::proxy::java::io::IOException;

#include "jace/proxy/java/lang/Throwable.h"
using jace::proxy::java::lang::Throwable;

#include "jace/proxy/jperipheral/nio/channels/InterruptedByTimeoutException.h"
using jace::proxy::jperipheral::nio::channels::InterruptedByTimeoutException;

#include "jace/proxy/java/nio/ByteBuffer.h"
using jace::proxy::java::nio::ByteBuffer;

#include <iostream>
using std::cerr;
using std::endl;


Worker* jperipheral::worker;

/**
 * Executes any pending tasks.
 */
void jperipheral::RunTasks(Worker& worker)
{
	HANDLE completionPort = worker.completionPort;
	DWORD bytesTransfered;
	ULONG_PTR completionKey;
	OVERLAPPED* overlapped;

	{
		boost::mutex::scoped_lock lock(worker.lock);
		worker.running.notify_one();
	}
	while (true)
	{
		if (!GetQueuedCompletionStatus(completionPort, &bytesTransfered, &completionKey,
			&overlapped, INFINITE))
		{
			DWORD errorCode = GetLastError();
			IoTask* task = IoTask::fromOverlapped(overlapped);

			switch (errorCode)
			{
				case ERROR_HANDLE_EOF:
					break;
				case ERROR_OPERATION_ABORTED:
				{
					// Triggered by CancelIo()
					task->listener->onCancellation();
					task->workerThread.deleteTask(task);
					jace::detach();
					break;
				}
				default:
				{
					task->listener->onFailure(IOException(jace::java_new<IOException>(
						L"GetQueuedCompletionStatus() failed with error: " + getErrorMessage(errorCode))));
					task->workerThread.deleteTask(task);
					jace::detach();
					break;
				}
			}
		}
		else
		{
			IoTask* task = IoTask::fromOverlapped(overlapped);
			switch (completionKey)
			{
				case IoTask::COMPLETION:
				{
					if (bytesTransfered < 1)
					{
						// timeout occurred
						if (task->timeout == 0)
						{
							// Premature timeout caused by the fact that SerialPort_Channel.setReadTimeout() cannot
							// "wait forever". Repeat the operation.
							task->run();
							continue;
						}
						try
						{
							task->updateJavaBuffer(bytesTransfered);
							task->listener->onFailure(InterruptedByTimeoutException());
						}
						catch (Throwable& t)
						{
							cerr << __FILE__ << ":" << __LINE__ << endl;
							t.printStackTrace();
						}
						task->workerThread.deleteTask(task);
						jace::detach();
						break;
					}
					try
					{
						task->updateJavaBuffer(bytesTransfered);
						Integer bytesTransferredAsInteger = Integer::valueOf(bytesTransfered);
						task->listener->onSuccess(bytesTransferredAsInteger);
					}
					catch (Throwable& t)
					{
						cerr << __FILE__ << ":" << __LINE__ << endl;
						t.printStackTrace();
					}
					task->workerThread.deleteTask(task);
					jace::detach();
					break;
				}
				case IoTask::SHUTDOWN:
				{
					//
					// Someone used PostQueuedCompletionStatus() to post an I/O packet with a shutdown CompletionKey.
					//
					return;
				}
				default:
				{
					task->listener->onFailure(AssertionError(jace::java_new<AssertionError>(
						wstring(L"completionKey==") + toWString(completionKey))));
					task->workerThread.deleteTask(task);
					jace::detach();
					break;
				}
			}
		}
	}
}

Worker::Worker()
{
	// Empirical tests show that handling I/O completion costs 0.3 milliseconds, making it hard to justify
	// the use of multiple threads.
	completionPort = CreateIoCompletionPort(INVALID_HANDLE_VALUE, 0, 0, 0);
	if (completionPort==0)
		throw AssertionError(jace::java_new<AssertionError>(L"CreateIoCompletionPort() failed with error: " + getErrorMessage(GetLastError())));
	
	boost::mutex::scoped_lock lock(this->lock);
	thread = new boost::thread(boost::bind(&RunTasks, boost::ref(*this)));
	running.wait(lock);
}

Worker::~Worker()
{
	if (!PostQueuedCompletionStatus(completionPort, 0, IoTask::SHUTDOWN, 0))
	{
		throw IOException(jace::java_new<IOException>(getSourceCodePosition(WIDEN(__FILE__), __LINE__) + 
			L" PostQueuedCompletionStatus() failed with error: " + getErrorMessage(GetLastError())));
	}
	thread->join();
	delete thread;
	if (!CloseHandle(completionPort))
	{
		throw IOException(jace::java_new<IOException>(L"CloseHandle(completionPort) failed with error: " + 
			getErrorMessage(GetLastError())));
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
