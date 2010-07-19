#include "jace/peer/jperipheral/SerialChannel_SerialFuture.h"
using jace::peer::jperipheral::SerialChannel_SerialFuture;
using jace::proxy::types::JBoolean;

#include "jace/proxy/jperipheral/SerialChannel_SerialFuture.h"

#include "jace/proxy/jperipheral/SerialChannel_NativeListener.h"
using jace::proxy::jperipheral::SerialChannel_NativeListener;

#include "jperipheral/SerialPortHelper.h"
using jperipheral::getContext;
using jperipheral::getErrorMessage;
using jperipheral::IoTask;
using jperipheral::CompletionPortContext;
using jperipheral::getSourceCodePosition;
using jperipheral::SerialPortContext;
using jperipheral::WorkerThread;

#include "jace/proxy/java/io/IOException.h"
using jace::proxy::java::io::IOException;

#include "jace/proxy/java/nio/ByteBuffer.h"
using jace::proxy::java::nio::ByteBuffer;

#include <string>
using std::string;

#include <iostream>
using std::cerr;
using std::endl;

#pragma warning(push)
#pragma warning(disable: 4103 4244 4512)
#include <boost/thread/mutex.hpp>
#pragma warning(pop)


class CancelTask: public IoTask
{
public:
	CancelTask(WorkerThread& workerThread, HANDLE port, SerialChannel_NativeListener future): 
		IoTask(workerThread, port)
	{
		setListener(new SerialChannel_NativeListener(future));
	}

	virtual void updateJavaBuffer(int)
	{}

	virtual bool run()
	{
		if (!CancelIo(port))
		{
			listener->onFailure(IOException(jace::java_new<IOException>(L"CancelIo() failed with error: " +
			  getErrorMessage(GetLastError()))));
		}
		return false;
	}
};

void SerialChannel_SerialFuture::nativeCancel()
{
	IoTask* context = getContext(getJaceProxy());
	{
		boost::mutex::scoped_lock lock(context->workerThread.mutex);
		CancelTask* task = new CancelTask(context->workerThread, context->port, getJaceProxy());
		task->workerThread.task = task;
		task->workerThread.taskChanged.notify_one();
	}
}