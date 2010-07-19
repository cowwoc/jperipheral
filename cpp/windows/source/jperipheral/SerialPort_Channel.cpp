#include "jace/peer/jperipheral/SerialChannel.h"
using jace::peer::jperipheral::SerialChannel;
using jace::JArray;
using jace::proxy::java::lang::Object;
using jace::proxy::java::util::concurrent::Future;
using jace::proxy::types::JLong;
using jace::proxy::types::JInt;
using jace::proxy::types::JByte;

#include "jperipheral/SerialPortHelper.h"
using jperipheral::getContext;
using jperipheral::getErrorMessage;
using jperipheral::CompletionPortContext;
using jperipheral::IoTask;
using jperipheral::getSourceCodePosition;
using jperipheral::SerialPortContext;
using jperipheral::WorkerThread;

#include "jace/proxy/jperipheral/SerialChannel_NativeListener.h"
using jace::proxy::jperipheral::SerialChannel_NativeListener;

#include "jace/proxy/java/io/IOException.h"
using jace::proxy::java::io::IOException;

#include "jace/proxy/java/nio/ByteBuffer.h"
using jace::proxy::java::nio::ByteBuffer;

#include "jace/proxy/java/lang/AssertionError.h"
using jace::proxy::java::lang::AssertionError;

#include "jace/proxy/java/lang/Throwable.h"
using jace::proxy::java::lang::Throwable;

#include "jace/proxy/java/lang/Integer.h"
using jace::proxy::java::lang::Integer;

#include "jace/proxy/java/lang/String.h"
using jace::proxy::java::lang::String;

#include "jace/Jace.h"
using jace::toWString;

#include <assert.h>

#include <string>
using std::string;

#include <iostream>
using std::cerr;
using std::endl;

#pragma warning(push)
#pragma warning(disable: 4103 4244 4512)
#include <boost/thread/thread.hpp>
#include <boost/thread/mutex.hpp>
#include <boost/thread/condition.hpp>
#include <boost/function.hpp>
#pragma warning(pop)

/**
 * Sets the port read timeout.
 *
 * @param port the comport
 * @param timeout the timeout, where 0 means "wait forever"
 */
void setReadTimeout(HANDLE port, DWORD timeout)
{
	COMMTIMEOUTS timeouts = {0};
	if (!GetCommTimeouts(port, &timeouts))
	{
		throw IOException(jace::java_new<IOException>(L"GetCommTimeouts() failed with error: " + 
			getErrorMessage(GetLastError())));
	}
	timeouts.ReadIntervalTimeout = MAXDWORD;
	timeouts.ReadTotalTimeoutMultiplier = MAXDWORD;
	if (timeout == 0)
	{
		// The Windows API does not provide a way to "wait forever" so instead we wait as long as possible
		// and have WindowsOS.WorkerThread() repeat the operation if needed.
		timeouts.ReadTotalTimeoutConstant = MAXDWORD - 1;
	}
	else
	{
		// wait for at least one byte or time out
		timeouts.ReadTotalTimeoutConstant = timeout;
	}
	if (!SetCommTimeouts(port, &timeouts))
	{
		throw IOException(jace::java_new<IOException>(L"SetCommTimeouts() failed with error: " + 
			getErrorMessage(GetLastError())));
	}
}

/**
 * Sets the port write timeout.
 *
 * @param port the comport
 * @param timeout the timeout, where 0 means "wait forever"
 */
void setWriteTimeout(HANDLE port, DWORD timeout)
{
	COMMTIMEOUTS timeouts = {0};
	if (!GetCommTimeouts(port, &timeouts))
	{
		throw IOException(jace::java_new<IOException>(L"GetCommTimeouts() failed with error: " + 
			getErrorMessage(GetLastError())));
	}
	timeouts.WriteTotalTimeoutMultiplier = 0;
	timeouts.WriteTotalTimeoutConstant = timeout;
	if (!SetCommTimeouts(port, &timeouts))
	{
		throw IOException(jace::java_new<IOException>(L"SetCommTimeouts() failed with error: " + 
			getErrorMessage(GetLastError())));
	}
}

class ReadTask: public IoTask
{
public:
	ReadTask(WorkerThread& workerThread, HANDLE _port, ByteBuffer _javaBuffer, DWORD _timeout,
		SerialChannel_NativeListener _listener):
			IoTask(workerThread, _port)
	{
		setJavaBuffer(new ByteBuffer(_javaBuffer));
		if (javaBuffer->isDirect())
			nativeBuffer = javaBuffer;
		else
			nativeBuffer = new ByteBuffer(ByteBuffer::allocateDirect(javaBuffer->remaining()));
		setTimeout(_timeout);
		setListener(new SerialChannel_NativeListener(_listener));
	}

	virtual void updateJavaBuffer(int bytesTransfered)
	{
		if (nativeBuffer==javaBuffer)
			javaBuffer->position(javaBuffer->position() + bytesTransfered);
		else
		{
			nativeBuffer->limit(bytesTransfered);
			javaBuffer->put(*nativeBuffer);
		}
	}

	virtual bool run()
	{
		try
		{
			JInt remaining = javaBuffer->remaining();
			if (remaining <= 0)
			{
				listener->onFailure(AssertionError(jace::java_new<AssertionError>(L"ByteBuffer.remaining()==" + 
					toWString((jint) remaining))));
				return false;
			}
			JNIEnv* env = jace::attach(0, "ReadTask", true);
			char* nativeBuffer = reinterpret_cast<char*>(env->GetDirectBufferAddress(*this->nativeBuffer));
			assert(nativeBuffer!=0);

			setReadTimeout(port, timeout);

			DWORD bytesTransferred;
			if (!ReadFile(port, nativeBuffer + this->nativeBuffer->position(), remaining, &bytesTransferred, 
				&overlapped))
			{
				DWORD errorCode = GetLastError();
				if (errorCode != ERROR_IO_PENDING)
				{
					listener->onFailure(IOException(jace::java_new<IOException>(L"ReadFile() failed with error: " + 
						getErrorMessage(errorCode))));
					return false;
				}
			}
			workerThread.pendingTasks.push_back(this);
			return true;
		}
		catch (Throwable& t)
		{
			try
			{
				listener->onFailure(t);
			}
			catch (Throwable& t)
			{
				t.printStackTrace();
			}
			return false;
		}
	}
};

class WriteTask: public IoTask
{
public:
	WriteTask(WorkerThread& workerThread, HANDLE _port, ByteBuffer _javaBuffer, DWORD _timeout,
		SerialChannel_NativeListener _listener): 
			IoTask(workerThread, _port)
	{
		setJavaBuffer(new ByteBuffer(_javaBuffer));
		if (javaBuffer->isDirect())
			nativeBuffer = javaBuffer;
		else
		{
			nativeBuffer = new ByteBuffer(ByteBuffer::allocateDirect(javaBuffer->remaining()));
			JInt oldPosition = javaBuffer->position();
			nativeBuffer->put(*javaBuffer);
			nativeBuffer->flip();
			javaBuffer->position(oldPosition);
		}
		setTimeout(_timeout);
		setListener(new SerialChannel_NativeListener(_listener));
	}

	virtual void updateJavaBuffer(int bytesTransfered)
	{
		javaBuffer->position(javaBuffer->position() + bytesTransfered);
	}

	virtual bool run()
	{
		try
		{
			JInt remaining = nativeBuffer->remaining();
			if (remaining <= 0)
			{
				listener->onFailure(AssertionError(jace::java_new<AssertionError>(L"ByteBuffer.remaining()==" + 
					toWString((jint) remaining))));
				return false;
			}
			JNIEnv* env = jace::attach(0, "WriteTask", true);
			char* nativeBuffer = reinterpret_cast<char*>(env->GetDirectBufferAddress(*this->nativeBuffer));
			assert(nativeBuffer!=0);

			setWriteTimeout(port, timeout);

			DWORD bytesTransferred;
			if (!WriteFile(port, nativeBuffer + this->nativeBuffer->position(), remaining, &bytesTransferred, 
				&overlapped))
			{
				DWORD errorCode = GetLastError();
				if (errorCode != ERROR_IO_PENDING)
				{
					listener->onFailure(IOException(jace::java_new<IOException>(L"WriteFile() failed with error: " +
						getErrorMessage(errorCode))));
					return false;
				}
			}
			workerThread.pendingTasks.push_back(this);
			return true;
		}
		catch (Throwable& t)
		{
			try
			{
				listener->onFailure(t);
			}
			catch (Throwable& t)
			{
				t.printStackTrace();
			}
			return false;
		}
	}
};

void SerialChannel::nativeRead(ByteBuffer target, JLong timeout, SerialChannel_NativeListener listener)
{
	SerialPortContext* context = getContext(getJaceProxy());
	{
		boost::mutex::scoped_lock lock(context->readThread.mutex);
		ReadTask* task = new ReadTask(context->readThread, context->port, target, (DWORD) min(timeout, MAXDWORD), 
			listener);
		task->workerThread.task = task;
		task->workerThread.taskChanged.notify_one();
	}
}

void SerialChannel::nativeWrite(ByteBuffer source, JLong timeout, SerialChannel_NativeListener listener)
{
	SerialPortContext* context = getContext(getJaceProxy());
	{
		boost::mutex::scoped_lock lock(context->writeThread.mutex);
		WriteTask* task = new WriteTask(context->writeThread, context->port, source, (DWORD) min(timeout, MAXDWORD),
			listener);
		task->workerThread.task = task;
		task->workerThread.taskChanged.notify_one();
	}
}
