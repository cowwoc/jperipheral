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

#include "jace/proxy/java/io/IOException.h"
using jace::proxy::java::io::IOException;

#include "jace/proxy/java/nio/ByteBuffer.h"
using jace::proxy::java::nio::ByteBuffer;

#include <string>
using std::string;


class CancelTask: public IoTask
{
public:
	CancelTask(HANDLE port, jace::proxy::jperipheral::SerialChannel_SerialFuture future): 
		IoTask(port, 0, 0, future)
	{}

	virtual void updateJavaBuffer(int)
	{}

	virtual void run()
	{
		if (!CancelIo(port))
			listener->onFailure(IOException(L"CancelIo() failed with error: " + getErrorMessage(GetLastError())));
	}
};

void SerialChannel_SerialFuture::nativeCancel()
{
	HANDLE port = getContext(getJaceProxy());
	CancelTask* task = new CancelTask(port, getJaceProxy());

	CompletionPortContext* windowsContext = getCompletionPortContext();
	if (!PostQueuedCompletionStatus(windowsContext->completionPort, 0, IoTask::RUN, 
		&task->overlapped))
	{
		delete task;
		throw IOException(getSourceCodePosition(L__FILE__, __LINE__) + 
			L"PostQueuedCompletionStatus() failed with error: " + getErrorMessage(GetLastError()));
	}
}