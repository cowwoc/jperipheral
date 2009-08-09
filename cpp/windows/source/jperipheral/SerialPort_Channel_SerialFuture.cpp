#include "jace/peer/jperipheral/SerialChannel_SerialFuture.h"
using jace::peer::jperipheral::SerialChannel_SerialFuture;
using jace::proxy::types::JBoolean;

#include "jace/proxy/jperipheral/SerialChannel_SerialFuture.h"

#include "jperipheral/SerialPortHelper.h"
using jperipheral::getContext;
using jperipheral::getErrorMessage;
using jperipheral::IoTask;
using jperipheral::CompletionPortContext;
using jperipheral::getCompletionPortContext;
using jperipheral::getSourceCodePosition;
using jperipheral::SerialPortContext;
using jperipheral::FutureContext;

#include "jace/proxy/java/io/IOException.h"
using jace::proxy::java::io::IOException;

#include "jace/proxy/java/nio/ByteBuffer.h"
using jace::proxy::java::nio::ByteBuffer;

#include <string>
using std::string;

#pragma warning(push)
#pragma warning(disable: 4103 4244 4512)
#include <boost/thread/mutex.hpp>
#pragma warning(pop)


class CancelTask: public IoTask
{
public:
	CancelTask(HANDLE port, jace::proxy::jperipheral::SerialChannel_SerialFuture future): 
		IoTask(port, 0, 0, future)
	{}

	virtual void updateJavaBuffer(int)
	{}

	virtual bool run()
	{
		if (!CancelIo(port))
			listener->onFailure(IOException(L"CancelIo() failed with error: " + getErrorMessage(GetLastError())));
		return false;
	}
};

void SerialChannel_SerialFuture::nativeCancel()
{
	FutureContext* context = getContext(getJaceProxy());
	{
		boost::mutex::scoped_lock lock(context->thread.lock);
		CancelTask* task = new CancelTask(context->port, getJaceProxy());
		context->thread.workload = task;
		context->thread.workloadChanged.notify_one();
	}
}

void SerialChannel_SerialFuture::dispose()
{
	FutureContext* context = getContext(getJaceProxy());
	delete context;
}