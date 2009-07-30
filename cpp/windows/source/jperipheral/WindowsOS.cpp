#include "jace/peer/jperipheral/WindowsOS.h"
using jace::peer::jperipheral::WindowsOS;
using jace::proxy::types::JLong;
using jace::helper::toWString;

#include "jace/proxy/java/lang/AssertionError.h"
using jace::proxy::java::lang::AssertionError;

#include <string>
using std::wstring;

#include "jperipheral/SerialPortHelper.h"
using jperipheral::getCompletionPortContext;
using jperipheral::getErrorMessage;
using jperipheral::CompletionPortContext;
using jperipheral::IoTask;
using jperipheral::getSourceCodePosition;

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


/**
 * Thread used carry out all I/O operations.
 */
DWORD WINAPI WorkerThread(LPVOID _completionPort)
{
	HANDLE completionPort = (HANDLE) _completionPort;
	DWORD bytesTransfered;
	ULONG_PTR completionKey;
	OVERLAPPED* overlapped;

	while (true)
	{
		if (!GetQueuedCompletionStatus(completionPort, &bytesTransfered, &completionKey,
			&overlapped, INFINITE))
		{
			DWORD errorCode = GetLastError();
			IoTask* task = IoTask::fromOverlapped(overlapped);
			Integer bytesTransferredAsInteger = Integer::valueOf(bytesTransfered);

			switch (errorCode)
			{
				case ERROR_HANDLE_EOF:
				{
					bytesTransferredAsInteger = Integer::valueOf(-1);
					break;
				}
				case ERROR_OPERATION_ABORTED:
				{
					task->listener->onCancellation();
					delete task;
					break;
				}
				default:
				{
					task->listener->onFailure(IOException(L"GetQueuedCompletionStatus() failed with error: " + 
						getErrorMessage(errorCode)));
					delete task;
					break;
				}
			}
		}
		else
		{
			IoTask* task = IoTask::fromOverlapped(overlapped);
			switch (completionKey)
			{
				case IoTask::RUN:
				{
					task->run();
					break;
				}
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
						delete task;
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
					delete task;
					break;
				}
				case IoTask::SHUTDOWN:
				{
					//
					// Someone used PostQueuedCompletionStatus() to post an I/O packet with a shutdown CompletionKey.
					//
					jace::helper::detach();
					return 0;
				}
				default:
				{
					task->listener->onFailure(AssertionError(String(wstring(L"completionKey==") + toWString(completionKey))));
					delete task;
					break;
				}
			}
		}
	}
	return 0;
}

CompletionPortContext::CompletionPortContext()
{
	// Empirical tests show that handling I/O completion costs 0.3 milliseconds, making it hard to justify
	// the use of multiple threads.
	completionPort = CreateIoCompletionPort(INVALID_HANDLE_VALUE, 0, 0, 0);
	if (completionPort==0)
		throw AssertionError(String(L"CreateIoCompletionPort() failed with error: " + getErrorMessage(GetLastError())));

	DWORD threadId;
	workerThread = CreateThread(0, 0, WorkerThread, completionPort, 0, &threadId);
	if (!workerThread)
	{
		CloseHandle(completionPort);
		throw AssertionError(String(L"CreateThread() failed with error: " + getErrorMessage(GetLastError())));
	}
}

CompletionPortContext::~CompletionPortContext()
{
	if (!CloseHandle(workerThread))
		throw IOException(L"CloseHandle(workerThread) failed with error: " + getErrorMessage(GetLastError()));
	if (!CloseHandle(completionPort))
		throw IOException(L"CloseHandle(completionPort) failed with error: " + getErrorMessage(GetLastError()));
}

JLong WindowsOS::nativeInitialize()
{
	return reinterpret_cast<intptr_t>(new CompletionPortContext());
}

void WindowsOS::nativeDispose()
{
	CompletionPortContext* context = getCompletionPortContext();
	if (!PostQueuedCompletionStatus(context->completionPort, 0, IoTask::SHUTDOWN, 0))
	{
		throw IOException(getSourceCodePosition(WIDEN(__FILE__), __LINE__) + 
			L" PostQueuedCompletionStatus() failed with error: " + getErrorMessage(GetLastError()));
	}

	// Wait for thread to shut down
	switch (WaitForSingleObject(context->workerThread, INFINITE))
	{
		case WAIT_ABANDONED_0:
			throw IOException("WaitForSingleObject() returned WAIT_ABANDONED_0");
		case WAIT_OBJECT_0:
			break;
		default:
			throw IOException(L"WaitForSingleObject() failed with error: " + getErrorMessage(GetLastError()));
	}
	delete context;
}