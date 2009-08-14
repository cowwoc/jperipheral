#include "jperipheral/SerialPortHelper.h"
using jperipheral::IoTask;
using jperipheral::CompletionPortContext;
using jperipheral::SerialPortContext;
using jperipheral::WorkerThread;

#include "jace/proxy/jperipheral/SerialPort.h"
using jace::proxy::jperipheral::SerialPort;

#include "jace/proxy/jperipheral/SerialChannel.h"
using jace::proxy::jperipheral::SerialChannel;

#include "jace/proxy/jperipheral/WindowsOS.h"
using jace::proxy::jperipheral::WindowsOS;

#include "jace/proxy/jperipheral/SerialChannel_NativeListener.h"
using jace::proxy::jperipheral::SerialChannel_NativeListener;

#include "jace/proxy/jperipheral/SerialChannel_SerialFuture.h"
using jace::proxy::jperipheral::SerialChannel_SerialFuture;

#include "jace/proxy/java/lang/AssertionError.h"
using jace::proxy::java::lang::AssertionError;

#include "jace/proxy/java/lang/String.h"
using jace::proxy::java::lang::String;

#include "jace/proxy/java/io/IOException.h"
using jace::proxy::java::io::IOException;

#include "jace/proxy/java/nio/ByteBuffer.h"
using jace::proxy::java::nio::ByteBuffer;

#include "jace/proxy/jperipheral/OperatingSystem.h"
using ::jace::proxy::jperipheral::OperatingSystem;

#include "jace/peer/jperipheral/WindowsOS.h"

#include "jace/javacast.h"
using jace::java_cast;

#include "jace/JNIHelper.h"
using jace::helper::toWString;

#include <string>
using std::wstring;

#include <sstream>
using std::stringstream;

#pragma warning(push)
#pragma warning(disable: 4103 4244 4512)
#include <boost/bind.hpp>
#include <boost/thread/thread.hpp>
#include <boost/thread/mutex.hpp>
#pragma warning(pop)


SerialPortContext* jperipheral::getContext(SerialPort serialPort)
{
	return reinterpret_cast<SerialPortContext*>(static_cast<intptr_t>(serialPort.nativeContext()));
}

SerialPortContext* jperipheral::getContext(SerialChannel channel)
{
	return reinterpret_cast<SerialPortContext*>(static_cast<intptr_t>(channel.nativeContext()));
}

IoTask* jperipheral::getContext(SerialChannel_SerialFuture future)
{
	return reinterpret_cast<IoTask*>(static_cast<intptr_t>(future.getNativeContext()));
}

CompletionPortContext* jperipheral::getCompletionPortContext()
{
	::jace::proxy::jperipheral::WindowsOS windows = java_cast<::jace::proxy::jperipheral::WindowsOS>(OperatingSystem::getCurrent());
	return reinterpret_cast<CompletionPortContext*>(static_cast<intptr_t>(windows.nativeContext()));
}

IoTask::IoTask(WorkerThread& _thread, HANDLE _port, ByteBuffer _javaBuffer, DWORD _timeout, SerialChannel_NativeListener _listener):
  thread(_thread), port(_port), timeout(_timeout), nativeBuffer(0), javaBuffer(0)
{
	memset(&overlapped, 0, sizeof(OVERLAPPED));
	listener = new SerialChannel_NativeListener(_listener);
	listener->setNativeContext(reinterpret_cast<intptr_t>(this));
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

void WorkHandler(WorkerThread& context)
{
	boost::mutex::scoped_lock lock(context.lock);
	context.workloadChanged.wait(lock);
	while (!context.shutdownRequested)
	{
		if (!context.workload->run())
			delete context.workload;
		context.workload = 0;
		context.workloadChanged.wait(lock);
	}
	jace::helper::detach();
};

WorkerThread::WorkerThread():
	workload(0), shutdownRequested(false)
{
	thread = new boost::thread(boost::bind(&WorkHandler, boost::ref(*this)));
}

WorkerThread::~WorkerThread()
{
	{
		boost::mutex::scoped_lock lock(this->lock);
		shutdownRequested = true;
		workloadChanged.notify_one();
	}
	thread->join();
	delete thread;
	delete workload;
}

SerialPortContext::SerialPortContext(HANDLE _port):
	port(_port)
{}

SerialPortContext::~SerialPortContext()
{
	if (!CloseHandle(port))
		throw IOException(L"CloseHandle() failed with error: " + getErrorMessage(GetLastError()));
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
		throw AssertionError(String(L"FormatMessage() failed with error: " + toWString(GetLastError())));
	}
	wstring result = buffer;
	if (LocalFree(buffer))
		throw AssertionError(String(L"LocalFree() failed with error: " + toWString(GetLastError())));
	return result;
}
