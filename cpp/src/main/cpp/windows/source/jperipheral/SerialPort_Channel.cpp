#include "jace/peer/org/jperipheral/SerialChannel.h"
using jace::peer::org::jperipheral::SerialChannel;
using jace::JArray;
using jace::proxy::java::lang::Object;
using jace::proxy::java::util::concurrent::Future;
using jace::proxy::types::JLong;
using jace::proxy::types::JInt;
using jace::proxy::types::JByte;

#include "jace/proxy/org/jperipheral/SerialPort_BaudRate.h"
using jace::proxy::org::jperipheral::SerialPort_BaudRate;

#include "jace/proxy/org/jperipheral/SerialPort_DataBits.h"
using jace::proxy::org::jperipheral::SerialPort_DataBits;

#include "jace/proxy/org/jperipheral/SerialPort_StopBits.h"
using jace::proxy::org::jperipheral::SerialPort_StopBits;

#include "jace/proxy/org/jperipheral/SerialPort_Parity.h"
using jace::proxy::org::jperipheral::SerialPort_Parity;

#include "jace/proxy/org/jperipheral/SerialPort_FlowControl.h"
using jace::proxy::org::jperipheral::SerialPort_FlowControl;

#include "jperipheral/Worker.h"

#include "jperipheral/SerialPortHelper.h"
using jperipheral::getContext;
using jperipheral::getErrorMessage;
using jperipheral::OverlappedContainer;
using jperipheral::Task;
using jperipheral::getSourceCodePosition;
using jperipheral::SerialPortContext;

#include "jace/proxy/java/lang/Long.h"
using jace::proxy::java::lang::Long;

#include "jace/proxy/org/jperipheral/PeripheralNotFoundException.h"
using jace::proxy::org::jperipheral::PeripheralNotFoundException;

#include "jace/proxy/org/jperipheral/PeripheralInUseException.h"
using jace::proxy::org::jperipheral::PeripheralInUseException;

#include "jace/proxy/org/jperipheral/PeripheralConfigurationException.h"
using jace::proxy::org::jperipheral::PeripheralConfigurationException;

#include "jace/proxy/java/nio/channels/CompletionHandler.h"
using jace::proxy::java::nio::channels::CompletionHandler;

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

#include "jace/proxy/java/nio/channels/InterruptedByTimeoutException.h"
using jace::proxy::java::nio::channels::InterruptedByTimeoutException;

#include "jace/proxy/java/nio/channels/AsynchronousCloseException.h"
using jace::proxy::java::nio::channels::AsynchronousCloseException;

#include "jace/Jace.h"
using jace::toWString;

#include <assert.h>

#include <string>
using std::wstring;

#include <iostream>
using std::wcerr;
using std::endl;

/**
 * Sets the port read timeout.
 *
 * @param port the comport
 * @param timeout the timeout, where 0 means "return immediately" and MAXDWORD means "wait forever"
 */
void setReadTimeout(HANDLE port, JLong timeout)
{
	COMMTIMEOUTS timeouts = {0};
	if (!GetCommTimeouts(port, &timeouts))
	{
		DWORD lastError = GetLastError();
		throw IOException(jace::java_new<IOException>(L"GetCommTimeouts() failed with error: " +
			getErrorMessage(lastError)));
	}
	timeouts.ReadIntervalTimeout = MAXDWORD;
	timeouts.ReadTotalTimeoutMultiplier = MAXDWORD;
	if (timeout == Long::MAX_VALUE())
	{
		// The Windows API does not provide a way to "wait forever" so instead we wait as long as possible
		// and have onTimeout() repeat the operation as needed.
		timeouts.ReadTotalTimeoutConstant = MAXDWORD - 1;
	}
	else if (timeout == static_cast<jlong>(0))
	{
		// return immediately
		timeouts.ReadTotalTimeoutConstant = 0;
	}
	else if (timeout >= MAXDWORD)
	{
		// Java supports longer timeouts than Windows (long vs int).
		// Wait as long as possible.
		timeouts.ReadTotalTimeoutConstant = MAXDWORD - 1;
	}
	else
	{
		// wait for at least one byte or time out
		timeouts.ReadTotalTimeoutConstant = static_cast<DWORD>(timeout);
	}
	if (!SetCommTimeouts(port, &timeouts))
	{
		DWORD lastError = GetLastError();
		throw IOException(jace::java_new<IOException>(L"SetCommTimeouts() failed with error: " +
			getErrorMessage(lastError)));
	}
}

/**
 * Sets the port write timeout.
 *
 * @param port the comport
 * @param timeout the timeout, where 0 means "return immediately" and MAXDWORD means "wait forever"
 */
void setWriteTimeout(HANDLE port, JLong timeout)
{
	COMMTIMEOUTS timeouts = {0};
	if (!GetCommTimeouts(port, &timeouts))
	{
		DWORD lastError = GetLastError();
		throw IOException(jace::java_new<IOException>(L"GetCommTimeouts() failed with error: " +
			getErrorMessage(lastError)));
	}
	timeouts.WriteTotalTimeoutMultiplier = 0;
	if (timeout == Long::MAX_VALUE())
	{
		// wait forever
		timeouts.WriteTotalTimeoutConstant = 0;
	}
	else if (timeout == static_cast<jlong>(0))
	{
		// return immediately
		timeouts.WriteTotalTimeoutConstant = 1;
	}
	else if (timeout >= MAXDWORD)
	{
		// Java supports longer timeouts than Windows (long vs int).
		// Wait as long as possible.
		timeouts.WriteTotalTimeoutConstant = MAXDWORD - 1;
	}
	else
	{
		// write as many bytes as possible before a timeout
		timeouts.WriteTotalTimeoutConstant = static_cast<DWORD>(timeout);
	}
	if (!SetCommTimeouts(port, &timeouts))
	{
		DWORD lastError = GetLastError();
		throw IOException(jace::java_new<IOException>(L"SetCommTimeouts() failed with error: " +
			getErrorMessage(lastError)));
	}
}

static void onTimeout(boost::shared_ptr<Task> task)
{
	if (task->getTimeout() == Long::MAX_VALUE())
	{
		// Premature timeout caused by the fact that SerialPort_Channel.setReadTimeout() cannot
		// "wait forever". Repeat the operation.
		task->run();
		return;
	}
	else if (task->getTimeout() > MAXDWORD)
	{
		// Java supports longer timeouts than Windows (long vs int).
		// We repeat the operation as many times as necessary to statisfy the Java timeout.
		long timeElapsed = task->getTimeElapsed();
		task->setTimeout(task->getTimeout() - timeElapsed);
		task->run();
		return;
	}
	try
	{
		task->getHandler()->failed(jace::java_new<InterruptedByTimeoutException>(),
			*task->getAttachment());
	}
	catch (Throwable& t)
	{
		wcerr << __FILE__ << ":" << __LINE__ << endl;
		t.printStackTrace();
	}
}

class ReadTask: public Task
{
public:
	ReadTask(SerialPortContext& _port, ByteBuffer _javaBuffer, JLong _timeout,
		::jace::proxy::java::lang::Object _attachment,
		::jace::proxy::java::nio::channels::CompletionHandler _handler):
			Task(_port, _attachment, _handler)
	{
		setJavaBuffer(new ByteBuffer(_javaBuffer));
		if (javaBuffer->isDirect())
			nativeBuffer = javaBuffer;
		else
			nativeBuffer = new ByteBuffer(ByteBuffer::allocateDirect(javaBuffer->remaining()));
		setTimeout(_timeout);
	}

	virtual void onSuccess(int bytesTransfered)
	{
		if (bytesTransfered < 1)
		{
			onTimeout(shared_from_this());
			return;
		}

		// Update the Java read buffer
		if (nativeBuffer==javaBuffer)
			javaBuffer->position(javaBuffer->position() + bytesTransfered);
		else
		{
			nativeBuffer->limit(bytesTransfered);
			javaBuffer->put(*nativeBuffer);
		}

		try
		{
			Integer bytesTransferredAsInteger = Integer::valueOf(bytesTransfered);
			handler->completed(bytesTransferredAsInteger, *attachment);
		}
		catch (Throwable& t)
		{
			wcerr << __FILE__ << ":" << __LINE__ << endl;
			t.printStackTrace();
		}
		boost::mutex::scoped_lock lock(portContext.getMutex());
		portContext.removeTask(shared_from_this());
	}

	virtual void onFailure(DWORD errorCode)
	{		
		bool isOpen;
		{
			boost::mutex::scoped_lock lock(portContext.getMutex());
			isOpen = portContext.isOpen();
		}
		if (isOpen)
		{
			// Get and clear current errors on the port
			DWORD errors;
			if (!ClearCommError(portContext.getPort(), &errors, 0))
			{
				DWORD lastError = GetLastError();
				handler->failed(jace::java_new<IOException>(
					L"ClearCommError() failed with error: " + getErrorMessage(lastError)),
					*attachment);
				boost::mutex::scoped_lock lock(portContext.getMutex());
				portContext.removeTask(shared_from_this());
				return;
			}

			// See http://en.wikipedia.org/wiki/Universal_asynchronous_receiver/transmitter#Special_receiver_conditions
			// for an explanation of the different receiver conditions.
			wstring errorMessage;
			if (errors & CE_BREAK)
				errorMessage += L"The hardware detected a break condition.\n";
			if (errors & CE_FRAME)
				errorMessage += L"The hardware detected a framing error.\n";
			if (errors & CE_OVERRUN)
				errorMessage += L"A character-buffer overrun has occurred. The next character is lost..\n";
			if (errors & CE_RXOVER)
			{
				errorMessage += L"An input buffer overflow has occurred. There is either no room in "
					L"the input buffer, or a character was received after the end-of-file (EOF) "
					L"character.\n";
			}
			if (errors & CE_RXPARITY)
				errorMessage += L"The hardware detected a parity error.\n";
			// Erase the last \n character
			if (!errorMessage.empty())
				errorMessage.erase(errorMessage.end() - 1);
			if (!errorMessage.empty())
			{
				handler->failed(IOException(jace::java_new<IOException>(errorMessage)), *attachment);
				boost::mutex::scoped_lock lock(portContext.getMutex());
				portContext.removeTask(shared_from_this());
				return;
			}
		}
		switch (errorCode)
		{
			case ERROR_OPERATION_ABORTED:
			{
				if (isOpen)
				{
					handler->failed(AsynchronousCloseException(jace::java_new<AsynchronousCloseException>()), 
						*attachment);
					break;
				}
				else
				{
					// Unexpected, leak through to the default case
				}
			}
			default:
			{
				handler->failed(IOException(jace::java_new<IOException>(
					L"GetQueuedCompletionStatus() failed with error: " + getErrorMessage(errorCode))),
					*attachment);
				break;
			}
		}
		boost::mutex::scoped_lock lock(portContext.getMutex());
		portContext.removeTask(shared_from_this());
	}

	virtual void run()
	{
		try
		{
			timer = boost::timer();
			JInt remaining = javaBuffer->remaining();
			if (remaining <= 0)
			{
				handler->failed(AssertionError(jace::java_new<AssertionError>(L"ByteBuffer.remaining()==" +
					toWString((jint) remaining))), *attachment);
				return;
			}
			JNIEnv* env = jace::attach(0, "ReadTask", true);
			char* nativeBuffer = reinterpret_cast<char*>(env->GetDirectBufferAddress(*this->nativeBuffer));
			assert (nativeBuffer != 0);

			// Clear errors set by the previous operation
			DWORD errors;
			boost::mutex::scoped_lock lock(portContext.getMutex());
			HANDLE port = portContext.getPort();
			if (!ClearCommError(port, &errors, 0))
			{
				DWORD lastError = GetLastError();
				handler->failed(jace::java_new<IOException>(L"ClearCommError() failed with error: " + 
					getErrorMessage(lastError)), *attachment);
				return;
			}

			setReadTimeout(port, timeout);

			OverlappedContainer<Task>* userData = new OverlappedContainer<Task>(shared_from_this());

			DWORD bytesTransferred;
			if (!ReadFile(port, nativeBuffer + this->nativeBuffer->position(),
				remaining, &bytesTransferred, &userData->getOverlapped()))
			{
				DWORD lastError = GetLastError();
				if (lastError != ERROR_IO_PENDING)
				{
					handler->failed(IOException(jace::java_new<IOException>(L"ReadFile() failed with error: " +
						getErrorMessage(lastError))), *attachment);
					return;
				}
			}
			// ReadFile() may return synchronously in spite of the fact that we requested an asynchronous
			// operation. The completion port gets notified in either case.
			// REFERENCE: http://support.microsoft.com/kb/156932
			// 
			// The completion port gets notified in either case.
			// REFERENCE: http://msdn.microsoft.com/en-us/library/windows/desktop/aa365683%28v=vs.85%29.aspx
			// "However, if I/O completion ports are being used with this asynchronous handle, a
			//  completion packet will also be sent even though the I/O operation completed immediately."

			portContext.addTask(shared_from_this());
		}
		catch (Throwable& t)
		{
			try
			{
				handler->failed(t, *attachment);
			}
			catch (Throwable& t)
			{
				t.printStackTrace();
			}
		}
	}
};

class WriteTask: public Task
{
public:
	WriteTask(SerialPortContext& _port, ByteBuffer _javaBuffer, JLong _timeout,
		::jace::proxy::java::lang::Object _attachment,
		::jace::proxy::java::nio::channels::CompletionHandler _handler):
			Task(_port, _attachment, _handler)
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
	}


	virtual void onSuccess(int bytesTransfered)
	{
		if (bytesTransfered < 1)
		{
			onTimeout(shared_from_this());
			return;
		}

		// Update the Java write buffer
		javaBuffer->position(javaBuffer->position() + bytesTransfered);

		try
		{
			Integer bytesTransferredAsInteger = Integer::valueOf(bytesTransfered);
			handler->completed(bytesTransferredAsInteger, *attachment);
		}
		catch (Throwable& t)
		{
			wcerr << __FILE__ << ":" << __LINE__ << endl;
			t.printStackTrace();
		}
		boost::mutex::scoped_lock lock(portContext.getMutex());
		portContext.removeTask(shared_from_this());
	}

	virtual void onFailure(DWORD errorCode)
	{
		switch (errorCode)
		{
			case ERROR_OPERATION_ABORTED:
			{
				bool isOpen;
				{
					boost::mutex::scoped_lock lock(portContext.getMutex());
					isOpen = portContext.isOpen();
				}
				if (isOpen)
				{
					handler->failed(AsynchronousCloseException(jace::java_new<AsynchronousCloseException>()), 
						*attachment);
					break;
				}
				else
				{
					// Unexpected, leak through to the default case
				}
			}
			default:
			{
				handler->failed(IOException(jace::java_new<IOException>(
					L"GetQueuedCompletionStatus() failed with error: " + getErrorMessage(errorCode))),
					*attachment);
				break;
			}
		}
		boost::mutex::scoped_lock lock(portContext.getMutex());
		portContext.removeTask(shared_from_this());
	}

	virtual void run()
	{
		try
		{
			timer = boost::timer();
			JInt remaining = nativeBuffer->remaining();
			if (remaining <= 0)
			{
				handler->failed(AssertionError(jace::java_new<AssertionError>(L"ByteBuffer.remaining()==" +
					toWString((jint) remaining))), *attachment);
				return;
			}
			JNIEnv* env = jace::attach(0, "WriteTask", true);
			char* nativeBuffer = reinterpret_cast<char*>(env->GetDirectBufferAddress(*this->nativeBuffer));
			assert(nativeBuffer!=0);

			boost::mutex::scoped_lock lock(portContext.getMutex());
			HANDLE port = portContext.getPort();
			setWriteTimeout(port, timeout);

			OverlappedContainer<Task>* userData = new OverlappedContainer<Task>(shared_from_this());
			DWORD bytesTransferred;
			if (!WriteFile(port, nativeBuffer + this->nativeBuffer->position(),
				remaining, &bytesTransferred, &userData->getOverlapped()))
			{
				DWORD lastError = GetLastError();
				if (lastError != ERROR_IO_PENDING)
				{
					handler->failed(IOException(jace::java_new<IOException>(L"WriteFile() failed with error: " +
						getErrorMessage(lastError))), *attachment);
					return;
				}
			}
			// WriteFile() may return synchronously in spite of the fact that we requested an asynchronous
			// operation. REFERENCE: http://support.microsoft.com/kb/156932
			// 
			// The completion port gets notified in either case.
			// REFERENCE: http://msdn.microsoft.com/en-us/library/windows/desktop/aa365683%28v=vs.85%29.aspx
			// "However, if I/O completion ports are being used with this asynchronous handle, a
			//  completion packet will also be sent even though the I/O operation completed immediately."

			portContext.addTask(shared_from_this());
		}
		catch (Throwable& t)
		{
			try
			{
				handler->failed(t, *attachment);
			}
			catch (Throwable& t)
			{
				t.printStackTrace();
			}
		}
	}
};

JLong SerialChannel::nativeOpen(String name, JLong timeout)
{
	wstring nameWstring = name;

	// WORKAROUND: http://stackoverflow.com/a/8896887/14731
	boost::timer timer;
	HANDLE port;
	const int SLEEP_TIME = 100;
	
	while (true)
	{
		port = CreateFile((L"\\\\.\\" + nameWstring).c_str(),
			GENERIC_READ | GENERIC_WRITE,
			0,											// must be opened with exclusive-access
			0,											// default security attributes
			OPEN_EXISTING,					// must use OPEN_EXISTING
			FILE_FLAG_OVERLAPPED,		// overlapped I/O
			0);											// hTemplate must be NULL for comm devices
		if (port == INVALID_HANDLE_VALUE)
		{
			DWORD lastError = GetLastError();

			switch (lastError)
			{
				case ERROR_FILE_NOT_FOUND:
					throw PeripheralNotFoundException(jace::java_new<PeripheralNotFoundException>(name, Throwable()));
				case ERROR_ACCESS_DENIED:
				{
					if (timer.elapsed() < (timeout / 1000.0))
					{
						if (timer.elapsed() + (SLEEP_TIME / 1000.0) > timer.elapsed_max())
						{
							// We're about to surpass timer.elapsed_max()
							timeout = timeout - (jlong) (timer.elapsed() * 1000.0);
							timer.restart();
						}
						boost::this_thread::sleep(boost::posix_time::milliseconds(SLEEP_TIME));
						continue;
					}
					throw PeripheralInUseException(jace::java_new<PeripheralInUseException>(name, Throwable()));
				}
				default:
				{
					throw IOException(jace::java_new<IOException>(L"CreateFile() failed with error: " +
						getErrorMessage(lastError)));
				}
			}
		}
		break;
	}

	// Associate the file handle with the existing completion port
	HANDLE completionPort = CreateIoCompletionPort(port, ::jperipheral::worker->completionPort, Task::COMPLETION, 0);
	if (completionPort==0)
	{
		DWORD lastError = GetLastError();
		throw AssertionError(jace::java_new<AssertionError>(L"CreateIoCompletionPort() failed with error: " +
			getErrorMessage(lastError)));
	}

	// Bind the native serial port to Java serial port
	SerialPortContext* result = new SerialPortContext(port);
	return reinterpret_cast<intptr_t>(result);
}

void SerialChannel::nativeConfigure(SerialPort_BaudRate baudRate,
																		SerialPort_DataBits dataBits,
																		SerialPort_Parity parity,
																		SerialPort_StopBits stopBits,
																		SerialPort_FlowControl flowControl)
{
	SerialPortContext* context = getContext(getJaceProxy());
	DCB dcb = {0};

	if (!GetCommState(context->getPort(), &dcb))
	{
		DWORD lastError = GetLastError();
		throw PeripheralConfigurationException(jace::java_new<PeripheralConfigurationException>(
			L"GetCommState() failed with error: " + getErrorMessage(lastError), Throwable()));
	}
	dcb.BaudRate = baudRate.toInt();
	dcb.ByteSize = (BYTE) dataBits.toInt();
	dcb.fBinary = true;

	switch (parity.ordinal())
	{
		case SerialPort_Parity::Ordinals::EVEN:
		{
			dcb.fParity = true;
			dcb.Parity = EVENPARITY;
			break;
		}
		case SerialPort_Parity::Ordinals::MARK:
		{
			dcb.fParity = true;
			dcb.Parity = MARKPARITY;
			break;
		}
		case SerialPort_Parity::Ordinals::NONE:
		{
			dcb.fParity = false;
			dcb.Parity = NOPARITY;
			break;
		}
		case SerialPort_Parity::Ordinals::ODD:
		{
			dcb.fParity = true;
			dcb.Parity = ODDPARITY;
			break;
		}
		case SerialPort_Parity::Ordinals::SPACE:
		{
			dcb.fParity = true;
			dcb.Parity = SPACEPARITY;
			break;
		}
		default:
			throw AssertionError(jace::java_new<AssertionError>(parity));
	}

	dcb.fOutxDsrFlow = false;
	dcb.fDtrControl = DTR_CONTROL_ENABLE;
	dcb.fDsrSensitivity = false;
	dcb.fTXContinueOnXoff = false;
	dcb.fErrorChar = false;
	dcb.fNull = false;
	dcb.fAbortOnError = false;
	dcb.wReserved = 0;
	// Leave default values for XonLim, XoffLim, XonChar, XoffChar, ErrorChar, EofChar, EvtChar.

	switch (stopBits.ordinal())
	{
		case SerialPort_StopBits::Ordinals::ONE:
		{
			dcb.StopBits = ONESTOPBIT;
			break;
		}
		case SerialPort_StopBits::Ordinals::ONE_POINT_FIVE:
		{
			dcb.StopBits = ONE5STOPBITS;
			break;
		}
		case SerialPort_StopBits::Ordinals::TWO:
		{
			dcb.StopBits = TWOSTOPBITS;
			break;
		}
		default:
			throw AssertionError(jace::java_new<AssertionError>(stopBits));
	}

	switch (flowControl.ordinal())
	{
		case SerialPort_FlowControl::Ordinals::RTS_CTS:
		{
			dcb.fOutX = false;
			dcb.fInX = false;
			dcb.fOutxCtsFlow = true;
			dcb.fRtsControl = RTS_CONTROL_TOGGLE;
			break;
		}
		case SerialPort_FlowControl::Ordinals::XON_XOFF:
		{
			dcb.fOutX = true;
			dcb.fInX = true;
			dcb.fOutxCtsFlow = false;
			dcb.fRtsControl = RTS_CONTROL_ENABLE;
			break;
		}
		case SerialPort_FlowControl::Ordinals::NONE:
		{
			dcb.fOutX = false;
			dcb.fInX = false;
			dcb.fOutxCtsFlow = false;
			dcb.fRtsControl = RTS_CONTROL_ENABLE;
			break;
		}
		default:
			throw AssertionError(jace::java_new<AssertionError>(flowControl));
	}

	if (!SetCommState(context->getPort(), &dcb))
	{
		DWORD lastError = GetLastError();
		throw PeripheralConfigurationException(jace::java_new<PeripheralConfigurationException>(
			L"SetCommState() failed with error: " + getErrorMessage(lastError), Throwable()));
	}
}

//void SerialChannel::printStatus()
//{
//	COMSTAT comStat;
//  DWORD errors;
//  bool fBREAK, fFRAME, fOVERRUN, fRXOVER, fRXPARITY;
//
//  // Get and clear current errors on the port.
//	SerialPortContext* context = getContext(getJaceProxy());
//  if (!ClearCommError(context->port, &errors, &comStat))
//	{
//      DWORD lastError = GetLastError();
//		throw IOException(jace::java_new<IOException>(L"ClearCommError() failed with error: " +
//			getErrorMessage(lastError)));
//	}
//
//  // Get error flags.
//  fBREAK = errors & CE_BREAK;
//  fFRAME = errors & CE_FRAME;
//  fOVERRUN = errors & CE_OVERRUN;
//  fRXOVER = errors & CE_RXOVER;
//  fRXPARITY = errors & CE_RXPARITY;
//
//  // COMSTAT structure contains information regarding communications status.
//  if (comStat.fCtsHold)
//		wcerr << "Tx waiting for CTS signal" << endl;
//
//  if (comStat.fDsrHold)
//		wcerr << "Tx waiting for DSR signal" << endl;
//
//  if (comStat.fRlsdHold)
//    wcerr << "Tx waiting for RLSD signal" << endl;
//
//  if (comStat.fXoffHold)
//    wcerr << "Tx waiting, XOFF char rec'd" << endl;
//
//  if (comStat.fXoffSent)
//    wcerr << "Tx waiting, XOFF char sent" << endl;
//
//  if (comStat.fEof)
//    wcerr << "EOF character received" << endl;
//
//  if (comStat.fTxim)
//    wcerr << "Character waiting for Tx; char queued with TransmitCommChar" << endl;
//
//  if (comStat.cbInQue)
//    wcerr << comStat.cbInQue << " bytes have been received, but not read" << endl;
//
//  if (comStat.cbOutQue)
//    wcerr << comStat.cbOutQue << " bytes are awaiting transfer" << endl;
//}

void SerialChannel::nativeClose()
{
	SerialPortContext* context = getContext(getJaceProxy());
	delete context;
}

void SerialChannel::nativeRead(ByteBuffer target, JLong timeout, Object attachment, CompletionHandler handler)
{
	SerialPortContext* context = getContext(getJaceProxy());

	boost::shared_ptr<Task> task(new ReadTask(*context, target, timeout, attachment, handler));
	task->run();
}

void SerialChannel::nativeWrite(ByteBuffer source, JLong timeout, Object attachment, CompletionHandler handler)
{
	SerialPortContext* context = getContext(getJaceProxy());

	boost::shared_ptr<Task> task(new WriteTask(*context, source, timeout, attachment, handler));
	task->run();
}
