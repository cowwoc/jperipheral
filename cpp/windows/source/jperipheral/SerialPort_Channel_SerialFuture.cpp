#include "jace/peer/org/jperipheral/SerialChannel_SerialFuture.h"
using jace::peer::org::jperipheral::SerialChannel_SerialFuture;
using jace::proxy::types::JBoolean;

#include "jace/proxy/org/jperipheral/SerialChannel_SerialFuture.h"

#include "jace/proxy/org/jperipheral/SerialChannel_NativeListener.h"
using jace::proxy::org::jperipheral::SerialChannel_NativeListener;

#include "jperipheral/SerialPortHelper.h"
using jperipheral::getContext;
using jperipheral::getErrorMessage;
using jperipheral::Task;
using jperipheral::getSourceCodePosition;
using jperipheral::SerialPortContext;
using jperipheral::SingleThreadExecutor;

#include "jace/proxy/java/io/IOException.h"
using jace::proxy::java::io::IOException;

#include "jace/proxy/java/nio/ByteBuffer.h"
using jace::proxy::java::nio::ByteBuffer;

#include <string>
using std::string;

#include <iostream>
using std::cerr;
using std::endl;


class CancelTask: public Task
{
public:
	CancelTask(SingleThreadExecutor& workerThread, HANDLE& port): 
		Task(workerThread, port)
	{}

	virtual void onSuccess(int)
	{}

	virtual void run()
	{
		timer = boost::timer();
		if (!CancelIo(port))
		{
			listener->onFailure(jace::java_new<IOException>(L"CancelIo() failed with error: " +
			  getErrorMessage(GetLastError())));
		}
	}
};

void SerialChannel_SerialFuture::nativeCancel()
{
	// Java peer ensures that nativeCancel() and nativeDispose() are not invoked at the same time
	boost::shared_ptr<Task>* context(getContext(getJaceProxy()));
	if (!context)
	{
		// onSuccess() or onFailure() must have invoked nativeDispose()
		return;
	}
	boost::shared_ptr<Task> task(*context);
	boost::shared_ptr<Task> cancelTask(new CancelTask(task->getWorkerThread(), task->getPort()));
	cancelTask->getWorkerThread().execute(cancelTask);
}

void SerialChannel_SerialFuture::nativeDispose()
{
	// Java peer ensures that nativeCancel() and nativeDispose() are not invoked at the same time
	boost::shared_ptr<Task>* context(getContext(getJaceProxy()));
	assert(context);
	boost::shared_ptr<Task> task(*context);

	// Prevent future calls to cancel() in case this method was invoked by onSuccess() or onFuture()
	setUserObject(0);

	delete context;
}