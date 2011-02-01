#include "jperipheral/SerialPortHelper.h"
using jperipheral::Task;
using jperipheral::SerialPortContext;
using jperipheral::SingleThreadExecutor;
using jperipheral::getErrorMessage;

#include "jace/proxy/org/jperipheral/SerialPort.h"
using jace::proxy::org::jperipheral::SerialPort;

#include "jace/proxy/org/jperipheral/SerialChannel.h"
using jace::proxy::org::jperipheral::SerialChannel;

#include "jace/proxy/org/jperipheral/SerialChannel_NativeListener.h"
using jace::proxy::org::jperipheral::SerialChannel_NativeListener;

#include "jace/proxy/org/jperipheral/SerialChannel_SerialFuture.h"
using jace::proxy::org::jperipheral::SerialChannel_SerialFuture;

#include "jace/proxy/java/lang/AssertionError.h"
using jace::proxy::java::lang::AssertionError;

#include "jace/proxy/java/lang/RuntimeException.h"
using jace::proxy::java::lang::RuntimeException;

#include "jace/proxy/java/lang/IllegalStateException.h"
using jace::proxy::java::lang::IllegalStateException;

#include "jace/proxy/types/JLong.h"
using jace::proxy::types::JLong;

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
#include "boost/timer.hpp"
#pragma warning(pop)


SerialPortContext* jperipheral::getContext(SerialChannel channel)
{
	return reinterpret_cast<SerialPortContext*>(static_cast<intptr_t>(channel.nativeObject()));
}

boost::shared_ptr<Task>* jperipheral::getContext(SerialChannel_SerialFuture future)
{
	return reinterpret_cast<boost::shared_ptr<Task>*>(static_cast<intptr_t>(
		future.getUserObject()));
}

Task::Task(SingleThreadExecutor& _workerThread, HANDLE& _port):
  workerThread(_workerThread), port(_port), timeout(0), nativeBuffer(0), 
	javaBuffer(0), listener(0)
{}

Task::Task(const Task& other):
  workerThread(other.workerThread), port(other.port), timeout(other.timeout), nativeBuffer(other.nativeBuffer), 
	javaBuffer(other.javaBuffer), listener(other.listener)
{
}

void Task::setJavaBuffer(::jace::proxy::java::nio::ByteBuffer* _javaBuffer)
{
	javaBuffer = _javaBuffer;
}

void Task::setTimeout(JLong _timeout)
{
	timeout = _timeout;
}

long Task::getTimeElapsed() const
{
	return static_cast<long>(timer.elapsed() * 1000);
}

void Task::setListener(::jace::proxy::org::jperipheral::SerialChannel_NativeListener _listener)
{
	if (listener != 0)
	{
		// Clean up existing listener
		boost::shared_ptr<Task>* context(reinterpret_cast<boost::shared_ptr<Task>*>(static_cast<intptr_t>(
			listener->getUserObject())));
		delete context;
		listener->setUserObject(0);
	}
	listener = new SerialChannel_NativeListener(_listener);
	listener->setUserObject(reinterpret_cast<intptr_t>(new boost::shared_ptr<Task>(shared_from_this())));
}

::jace::proxy::org::jperipheral::SerialChannel_NativeListener* Task::getListener()
{
	return listener;
}

JLong Task::getTimeout() const
{
	return timeout;
}

HANDLE& Task::getPort()
{
	return port;
}

SingleThreadExecutor& Task::getWorkerThread()
{
	return workerThread;
}

Task::~Task()
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

void SingleThreadExecutor::run()
{
	{
		boost::mutex::scoped_lock lock(mutex);
		onRunning.notify_all();
		running = true;
		onStateChanged.wait(lock);
	}
	try
	{
		boost::shared_ptr<Task> task;
		while (true)
		{
			{
				boost::mutex::scoped_lock lock(mutex);
				if (shutdownRequested && tasks.empty())
					break;
				task = tasks.front();
				tasks.pop_front();
			}
			// DESIGN: We must ensure that the listener is never used while the SingleThreadExecutor
			//         mutex is locked, otherwise we risk the following deadlock:
			//
			//         1) Lock SingleThreadExecutor mutex -> ReadTask -> error occurs ->
			//            listener->onFailure() -> Waiting on Future mutex
			//         2) Lock Future mutex -> Future.cancel() -> CancelTask ->
			//            Waiting on SingleThreadExecutor mutex
			task->run();
			{
				boost::mutex::scoped_lock lock(mutex);
				if (!shutdownRequested && tasks.empty())
					onStateChanged.wait(lock);
			}
		}
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
	{
		boost::mutex::scoped_lock lock(mutex);
		running = false;
		jace::detach();
	}
};

SingleThreadExecutor::SingleThreadExecutor():
	thread(0), shutdownRequested(false), running(false)
{
	boost::mutex::scoped_lock lock(mutex);
	thread = new boost::thread(boost::bind(&SingleThreadExecutor::run, 
		this));
	// If we don't wait, the worker thread may miss conditions notified past this point
	onRunning.wait(lock);
}

SingleThreadExecutor::~SingleThreadExecutor()
{
	shutdown();
	awaitTermination();
	delete thread;
}

void SingleThreadExecutor::execute(boost::shared_ptr<Task> task)
{
	boost::mutex::scoped_lock lock(mutex);
	if (!running)
	{
		throw IllegalStateException(jace::java_new<IllegalStateException>(
			std::wstring(L"SingleThreadExecutor has shut down")));
	}
	if (shutdownRequested)
	{
		throw IllegalStateException(jace::java_new<IllegalStateException>(
	  	std::wstring(L"SingleThreadExecutor is in the process of shutting "
			L"down and cannot accept any new tasks")));
	}
	tasks.push_back(task);
	onStateChanged.notify_all();
}

void SingleThreadExecutor::shutdown()
{
	boost::mutex::scoped_lock lock(mutex);
	shutdownRequested = true;
	onStateChanged.notify_all();
}

void SingleThreadExecutor::awaitTermination()
{
	{
		boost::mutex::scoped_lock lock(mutex);
		if (!running)
			return;
	}
	thread->join();
}

SerialPortContext::SerialPortContext(HANDLE _port):
	port(_port)
{}

SerialPortContext::~SerialPortContext()
{
	readThread.shutdown();
	readThread.awaitTermination();
	writeThread.shutdown();
	writeThread.awaitTermination();
	if (!CloseHandle(port))
	{
		throw IOException(jace::java_new<IOException>(L"CloseHandle() failed with error: " + 
		  getErrorMessage(GetLastError())));
	}
}

HANDLE& SerialPortContext::getPort()
{
	return port;
}

SingleThreadExecutor& SerialPortContext::getReadThread()
{
	return readThread;
}

SingleThreadExecutor& SerialPortContext::getWriteThread()
{
	return writeThread;
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
