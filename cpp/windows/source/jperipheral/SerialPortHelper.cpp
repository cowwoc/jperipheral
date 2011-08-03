#include "jperipheral/SerialPortHelper.h"
using jperipheral::Task;
using jperipheral::SerialPortContext;
using jperipheral::getErrorMessage;

#include "jace/proxy/org/jperipheral/SerialPort.h"
using jace::proxy::org::jperipheral::SerialPort;

#include "jace/proxy/org/jperipheral/SerialChannel.h"
using jace::proxy::org::jperipheral::SerialChannel;

#include "jace/proxy/java/nio/channels/CompletionHandler.h"
using jace::proxy::java::nio::channels::CompletionHandler;

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

Task::Task(HANDLE& _port, ::jace::proxy::java::lang::Object _attachment, ::jace::proxy::java::nio::channels::CompletionHandler _handler):
  port(_port), timeout(0), nativeBuffer(0), javaBuffer(0), attachment(0), handler(0)
{
	attachment = new ::jace::proxy::java::lang::Object(_attachment);
	handler = new CompletionHandler(_handler);
}

Task::Task(const Task& other):
  port(other.port), timeout(other.timeout), nativeBuffer(other.nativeBuffer), 
	javaBuffer(other.javaBuffer), attachment(other.attachment), handler(other.handler)
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

::jace::proxy::java::lang::Object* Task::getAttachment()
{
	return attachment;
}

::jace::proxy::java::nio::channels::CompletionHandler* Task::getHandler()
{
	return handler;
}

JLong Task::getTimeout() const
{
	return timeout;
}

HANDLE& Task::getPort()
{
	return port;
}

Task::~Task()
{
	delete attachment;
	delete handler;
	if (nativeBuffer!=javaBuffer)
	{
		delete nativeBuffer;
		delete javaBuffer;
	}
	else
		delete nativeBuffer;
}

SerialPortContext::SerialPortContext(HANDLE _port):
	port(_port)
{}

SerialPortContext::~SerialPortContext()
{
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
