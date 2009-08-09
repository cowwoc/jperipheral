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
using jperipheral::getCompletionPortContext;
using jperipheral::getErrorMessage;
using jperipheral::CompletionPortContext;
using jperipheral::IoTask;
using jperipheral::getSourceCodePosition;
using jperipheral::SerialPortContext;
using jperipheral::FutureContext;

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

#include "jace/JNIHelper.h"
using jace::helper::toWString;

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
		throw IOException(L"GetCommTimeouts() failed with error: " + getErrorMessage(GetLastError()));
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
		throw IOException(L"SetCommTimeouts() failed with error: " + getErrorMessage(GetLastError()));
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
		throw IOException(L"GetCommTimeouts() failed with error: " + getErrorMessage(GetLastError()));
	timeouts.WriteTotalTimeoutMultiplier = 0;
	timeouts.WriteTotalTimeoutConstant = timeout;
	if (!SetCommTimeouts(port, &timeouts))
		throw IOException(L"SetCommTimeouts() failed with error: " + getErrorMessage(GetLastError()));
}

class ReadTask: public IoTask
{
public:
	ReadTask(HANDLE _port, ByteBuffer _javaBuffer, DWORD _timeout, SerialChannel_NativeListener _listener):
		IoTask(_port, _javaBuffer, _timeout, _listener)
	{
		javaBuffer = new ByteBuffer(_javaBuffer);
		if (javaBuffer->isDirect())
			nativeBuffer = javaBuffer;
		else
			nativeBuffer = new ByteBuffer(ByteBuffer::allocateDirect(javaBuffer->remaining()));
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
				listener->onFailure(AssertionError(L"ByteBuffer.remaining()==" + toWString((jint) remaining)));
				return false;
			}
			JNIEnv* env = jace::helper::attach();
			char* nativeBuffer = reinterpret_cast<char*>(env->GetDirectBufferAddress(this->nativeBuffer->getJavaJniObject()));
			assert(nativeBuffer!=0);

			setReadTimeout(port, timeout);

			DWORD bytesTransferred;
			if (!ReadFile(port, nativeBuffer + this->nativeBuffer->position(), remaining, &bytesTransferred, 
				&overlapped))
			{
				DWORD errorCode = GetLastError();
				if (errorCode != ERROR_IO_PENDING)
				{
					listener->onFailure(IOException(L"ReadFile() failed with error: " + getErrorMessage(errorCode)));
					return false;
				}
			}
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
	WriteTask(HANDLE _port, ByteBuffer _javaBuffer, DWORD _timeout, SerialChannel_NativeListener _listener): 
		IoTask(_port, _javaBuffer, _timeout, _listener)
	{
		javaBuffer = new ByteBuffer(_javaBuffer);
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
				listener->onFailure(AssertionError(L"ByteBuffer.remaining()==" + toWString((jint) remaining)));
				return false;
			}
			JNIEnv* env = jace::helper::attach();
			char* nativeBuffer = reinterpret_cast<char*>(env->GetDirectBufferAddress(this->nativeBuffer->getJavaJniObject()));
			assert(nativeBuffer!=0);

			setWriteTimeout(port, timeout);

			DWORD bytesTransferred;
			if (!WriteFile(port, nativeBuffer + this->nativeBuffer->position(), remaining, &bytesTransferred, 
				&overlapped))
			{
				DWORD errorCode = GetLastError();
				if (errorCode != ERROR_IO_PENDING)
				{
					listener->onFailure(IOException(L"WriteFile() failed with error: " + getErrorMessage(errorCode)));
					return false;
				}
			}
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
	FutureContext* futureContext = new FutureContext(context->port, context->readThread);
	listener.setNativeContext(reinterpret_cast<intptr_t>(futureContext));
	{
		boost::mutex::scoped_lock lock(context->readThread.lock);
		try
		{
			ReadTask* task = new ReadTask(context->port, target, (DWORD) min(timeout, MAXDWORD), listener);
			context->readThread.workload = task;
			context->readThread.workloadChanged.notify_one();
		}
		catch (...)
		{
			delete futureContext;
			throw;
		}
	}
}

void SerialChannel::nativeWrite(ByteBuffer source, JLong timeout, SerialChannel_NativeListener listener)
{
	SerialPortContext* context = getContext(getJaceProxy());
	FutureContext* futureContext = new FutureContext(context->port, context->readThread);
	listener.setNativeContext(reinterpret_cast<intptr_t>(futureContext));
	{
		boost::mutex::scoped_lock lock(context->writeThread.lock);
		try
		{
			WriteTask* task = new WriteTask(context->port, source, (DWORD) min(timeout, MAXDWORD), listener);
			context->writeThread.workload = task;
			context->writeThread.workloadChanged.notify_one();
		}
		catch (...)
		{
			delete futureContext;
			throw;
		}
	}
}
