#include "jperipheral/SerialPortHelper.h"
using jperipheral::IoTask;
using jperipheral::SerialPortContext;
using jperipheral::WorkerThread;
using jperipheral::getErrorMessage;

#include "jace/proxy/jperipheral/SerialPort.h"
using jace::proxy::jperipheral::SerialPort;

#include "jace/proxy/jperipheral/SerialChannel.h"
using jace::proxy::jperipheral::SerialChannel;

#include "jace/proxy/jperipheral/SerialChannel_NativeListener.h"
using jace::proxy::jperipheral::SerialChannel_NativeListener;

#include "jace/proxy/jperipheral/SerialChannel_SerialFuture.h"
using jace::proxy::jperipheral::SerialChannel_SerialFuture;

#include "jace/proxy/java/lang/AssertionError.h"
using jace::proxy::java::lang::AssertionError;

#include "jace/proxy/java/lang/RuntimeException.h"
using jace::proxy::java::lang::RuntimeException;

#include "jace/proxy/java/lang/String.h"
using jace::proxy::java::lang::String;

#include "jace/proxy/java/io/IOException.h"
using jace::proxy::java::io::IOException;

#include "jace/proxy/java/nio/ByteBuffer.h"
using jace::proxy::java::nio::ByteBuffer;

#include "jace/Jace.h"
using jace::toWString;

#include <string>
using std::wstring;

#include <sstream>
using std::stringstream;

#include <iostream>
using std::cerr;
using std::endl;

#pragma warning(push)
#pragma warning(disable: 4103 4244 4512)
#include <boost/bind.hpp>
#include <boost/thread/thread.hpp>
#include <boost/thread/mutex.hpp>
#pragma warning(pop)


SerialPortContext* jperipheral::getContext(SerialChannel channel)
{
	return reinterpret_cast<SerialPortContext*>(static_cast<intptr_t>(channel.nativeObject()));
}

IoTask* jperipheral::getContext(SerialChannel_SerialFuture future)
{
	return reinterpret_cast<IoTask*>(static_cast<intptr_t>(future.userObject()));
}

IoTask::IoTask(WorkerThread& _workerThread, HANDLE _port):
  workerThread(_workerThread), port(_port), timeout(0), nativeBuffer(0), javaBuffer(0), listener(0)
{
	memset(&overlapped, 0, sizeof(OVERLAPPED));
}

void IoTask::setJavaBuffer(::jace::proxy::java::nio::ByteBuffer* _javaBuffer)
{
	javaBuffer = _javaBuffer;
}

void IoTask::setTimeout(DWORD _timeout)
{
	timeout = _timeout;
}

void IoTask::setListener(::jace::proxy::jperipheral::SerialChannel_NativeListener* _listener)
{
	listener = _listener;
	listener->setUserObject(reinterpret_cast<intptr_t>(this));
}

IoTask::~IoTask()
{
	delete listener;
	if (nativeBuffer!=javaBuffer)
	{
		delete nativeBuffer;
		delete javaBuffer;
	}
	else
		delete nativeBuffer;
}

IoTask* IoTask::fromOverlapped(OVERLAPPED* overlapped)
{
	return reinterpret_cast<IoTask*>((char*) overlapped - offsetof(IoTask, overlapped));
}

static void ScheduleTasks(WorkerThread& context)
{
	boost::mutex::scoped_lock lock(context.mutex);
	context.running.notify_one();
	try
	{
		while (!context.shutdownRequested)
		{
			context.taskChanged.wait(lock);
			if (!context.task->run())
			{
				context.taskDone.notify_one();
				context.pendingTasks.remove(context.task);
				delete context.task;
			}
			context.task = 0;
		}
		assert (context.task == 0);
	}
  catch (jace::proxy::java::lang::Throwable& t)
  {
		JNIEnv* env = jace::attach();
    env->Throw(static_cast<jthrowable>(env->NewLocalRef(t)));
  }
  catch (std::exception& e)
  {
    std::string msg = std::string("An unexpected JNI error has occurred: ") + e.what();
    RuntimeException ex(jace::java_new<RuntimeException>(msg));
		JNIEnv* env = jace::attach();
    env->Throw(static_cast<jthrowable>(env->NewLocalRef(ex)));
  }
	jace::detach();
};

WorkerThread::WorkerThread():
	task(0), shutdownRequested(false)
{
	boost::mutex::scoped_lock lock(mutex);
	thread = new boost::thread(boost::bind(&ScheduleTasks, boost::ref(*this)));
	// If we don't wait, the worker thread may miss condition notification
	running.wait(lock);
}

WorkerThread::~WorkerThread()
{
	thread->join();
	delete thread;
	delete task;
}

void WorkerThread::deleteTask(IoTask* task)
{
	boost::mutex::scoped_lock lock(mutex);
	taskDone.notify_one();
	pendingTasks.remove(task);
	delete task;
}

SerialPortContext::SerialPortContext(HANDLE _port):
	port(_port)
{}

class ShutdownTask: public IoTask
{
public:
	ShutdownTask(WorkerThread& workerThread, HANDLE _port): 
		IoTask(workerThread, _port)
	{}

	virtual void updateJavaBuffer(int)
	{}

	virtual bool run()
	{
		// Wait for ongoing tasks to complete
		if (!workerThread.pendingTasks.empty())
		{
			if (!CancelIo(port))
			{
				throw IOException(jace::java_new<IOException>(L"CancelIo() failed with error: " + 
				  getErrorMessage(GetLastError())));
			}
			while (!workerThread.pendingTasks.empty())
				workerThread.taskDone.wait(workerThread.mutex);
		}
		workerThread.shutdownRequested = true;
		return false;
	}
};

SerialPortContext::~SerialPortContext()
{
	{
		boost::mutex::scoped_lock lock(readThread.mutex);
		ShutdownTask* task = new ShutdownTask(readThread, port);
		task->workerThread.task = task;
		task->workerThread.taskChanged.notify_one();
	}
	{
		boost::mutex::scoped_lock lock(writeThread.mutex);
		ShutdownTask* task = new ShutdownTask(writeThread, port);
		task->workerThread.task = task;
		task->workerThread.taskChanged.notify_one();
	}
	readThread.thread->join();
	writeThread.thread->join();
	if (!CloseHandle(port))
		throw IOException(jace::java_new<IOException>(L"CloseHandle() failed with error: " + getErrorMessage(GetLastError())));
}

/**
 * Returns the String representation of the current source-code position.
 */
wstring jperipheral::getSourceCodePosition(wchar_t* file, int line)
{
	return L"[" + toWString(file) + L":" + toWString(line) + L"]";
}

/**
 * Returns the String representation of GetLastError().
 */
wstring jperipheral::getErrorMessage(DWORD errorCode)
{
	LPWSTR buffer;

	// REFERENCE: http://stackoverflow.com/questions/455434/how-should-i-use-formatmessage-properly-in-c
	if (!FormatMessage(FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS, 
		0, errorCode, 0, (LPWSTR) &buffer, 0, 0))
	{
		throw AssertionError(jace::java_new<AssertionError>(L"FormatMessage() failed with error: " + 
			toWString(GetLastError())));
	}
	wstring result = buffer;
	if (LocalFree(buffer))
	{
		throw AssertionError(jace::java_new<AssertionError>(L"LocalFree() failed with error: " + 
			toWString(GetLastError())));
	}
	return result;
}
