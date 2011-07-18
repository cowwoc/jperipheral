#include "jace/peer/org/jperipheral/SerialChannel.h"
using jace::peer::org::jperipheral::SerialChannel;
using jace::JArray;
using jace::proxy::java::lang::Object;
using jace::proxy::java::util::concurrent::Future;
using jace::proxy::types::JLong;
using jace::proxy::types::JInt;
using jace::proxy::types::JByte;

#include "jace/proxy/org/jperipheral/SerialPort_StopBits.h"
using jace::proxy::org::jperipheral::SerialPort_StopBits;

#include "jace/proxy/org/jperipheral/SerialPort_Parity.h"
using jace::proxy::org::jperipheral::SerialPort_Parity;

#include "jace/proxy/org/jperipheral/SerialPort_DataBits.h"
using jace::proxy::org::jperipheral::SerialPort_DataBits;

#include "jace/proxy/org/jperipheral/SerialPort_FlowControl.h"
using jace::proxy::org::jperipheral::SerialPort_FlowControl;

#include "jperipheral/Worker.h"

#include "jperipheral/SerialPortHelper.h"
using jperipheral::getContext;
using jperipheral::getErrorMessage;
using jperipheral::OverlappedContainer;
using jperipheral::Task;
using jperipheral::getSourceCodePosition;
using jperipheral::SingleThreadExecutor;
using jperipheral::SerialPortContext;

#include "jace/proxy/java/lang/Long.h"
using jace::proxy::java::lang::Long;

#include "jace/proxy/org/jperipheral/PeripheralNotFoundException.h"
using jace::proxy::org::jperipheral::PeripheralNotFoundException;

#include "jace/proxy/org/jperipheral/PeripheralInUseException.h"
using jace::proxy::org::jperipheral::PeripheralInUseException;

#include "jace/proxy/org/jperipheral/SerialChannel_NativeListener.h"
using jace::proxy::org::jperipheral::SerialChannel_NativeListener;

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

#include "jace/Jace.h"
using jace::toWString;

#include <assert.h>

#include <string>
using std::wstring;
using std::string;

#include <iostream>
using std::cerr;
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
		throw IOException(jace::java_new<IOException>(L"GetCommTimeouts() failed with error: " +
			getErrorMessage(GetLastError())));
	}
	timeouts.ReadIntervalTimeout = MAXDWORD;
	timeouts.ReadTotalTimeoutMultiplier = MAXDWORD;
	if (timeout == Long::MAX_VALUE())
	{
		// The Windows API does not provide a way to "wait forever" so instead we wait as long as possible
		// and have Worker::run() repeat the operation as needed.
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
		throw IOException(jace::java_new<IOException>(L"SetCommTimeouts() failed with error: " +
			getErrorMessage(GetLastError())));
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
		throw IOException(jace::java_new<IOException>(L"GetCommTimeouts() failed with error: " +
			getErrorMessage(GetLastError())));
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
		throw IOException(jace::java_new<IOException>(L"SetCommTimeouts() failed with error: " +
			getErrorMessage(GetLastError())));
	}
}

static void onTimeout(boost::shared_ptr<Task> task)
{
	if (task->getTimeout() == Long::MAX_VALUE())
	{
		// Premature timeout caused by the fact that SerialPort_Channel.setReadTimeout() cannot
		// "wait forever". Repeat the operation.
		task->getWorkerThread().execute(task);
		return;
	}
	else if (task->getTimeout() > MAXDWORD)
	{
		// Java supports longer timeouts than Windows (long vs int).
		// We repeat the operation as many times as necessary to statisfy the Java timeout.
		long timeElapsed = task->getTimeElapsed();
		task->setTimeout(task->getTimeout() - timeElapsed);
		task->getWorkerThread().execute(task);
		return;
	}
	try
	{
		task->getListener()->onFailure(jace::java_new<InterruptedByTimeoutException>());
	}
	catch (Throwable& t)
	{
		cerr << __FILE__ << ":" << __LINE__ << endl;
		t.printStackTrace();
	}
}

class ReadTask: public Task
{
public:
	ReadTask(SingleThreadExecutor& workerThread, HANDLE& _port, ByteBuffer _javaBuffer, JLong _timeout):
			Task(workerThread, _port)
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
			listener->onSuccess(bytesTransferredAsInteger);
		}
		catch (Throwable& t)
		{
			cerr << __FILE__ << ":" << __LINE__ << endl;
			t.printStackTrace();
		}
	}

	virtual void run()
	{
		try
		{
			timer = boost::timer();
			JInt remaining = javaBuffer->remaining();
			if (remaining <= 0)
			{
				listener->onFailure(AssertionError(jace::java_new<AssertionError>(L"ByteBuffer.remaining()==" +
					toWString((jint) remaining))));
				return;
			}
			JNIEnv* env = jace::attach(0, "ReadTask", true);
			char* nativeBuffer = reinterpret_cast<char*>(env->GetDirectBufferAddress(*this->nativeBuffer));
			assert(nativeBuffer!=0);

			setReadTimeout(port, timeout);

			OverlappedContainer<Task>* userData =
				new OverlappedContainer<Task>(shared_from_this());

			DWORD bytesTransferred;
			if (!ReadFile(port, nativeBuffer + this->nativeBuffer->position(), remaining, &bytesTransferred,
				&userData->getOverlapped()))
			{
				DWORD errorCode = GetLastError();
				if (errorCode != ERROR_IO_PENDING)
				{
					listener->onFailure(IOException(jace::java_new<IOException>(L"ReadFile() failed with error: " +
						getErrorMessage(errorCode))));
					return;
				}
			}
			// ReadFile() may return synchronously in spite of the fact that we requested an asynchronous
			// operation. The completion port gets notified in either case.
			//
			// REFERENCE: http://support.microsoft.com/kb/156932
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
		}
	}
};

class WriteTask: public Task
{
public:
	WriteTask(SingleThreadExecutor& workerThread, HANDLE& _port, ByteBuffer _javaBuffer, JLong _timeout):
			Task(workerThread, _port)
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
			listener->onSuccess(bytesTransferredAsInteger);
		}
		catch (Throwable& t)
		{
			cerr << __FILE__ << ":" << __LINE__ << endl;
			t.printStackTrace();
		}
	}

	virtual void run()
	{
		try
		{
			timer = boost::timer();
			JInt remaining = nativeBuffer->remaining();
			if (remaining <= 0)
			{
				listener->onFailure(AssertionError(jace::java_new<AssertionError>(L"ByteBuffer.remaining()==" +
					toWString((jint) remaining))));
				return;
			}
			JNIEnv* env = jace::attach(0, "WriteTask", true);
			char* nativeBuffer = reinterpret_cast<char*>(env->GetDirectBufferAddress(*this->nativeBuffer));
			assert(nativeBuffer!=0);

			setWriteTimeout(port, timeout);

			OverlappedContainer<Task>* userData =
				new OverlappedContainer<Task>(shared_from_this());
			DWORD bytesTransferred;
			if (!WriteFile(port, nativeBuffer + this->nativeBuffer->position(), remaining, &bytesTransferred,
				&userData->getOverlapped()))
			{
				DWORD errorCode = GetLastError();
				if (errorCode != ERROR_IO_PENDING)
				{
					listener->onFailure(IOException(jace::java_new<IOException>(L"WriteFile() failed with error: " +
						getErrorMessage(errorCode))));
					return;
				}
			}
			// WriteFile() may return synchronously in spite of the fact that we requested an asynchronous
			// operation. The completion port gets notified in either case.
			//
			// REFERENCE: http://support.microsoft.com/kb/156932
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
		}
	}
};

JLong SerialChannel::nativeOpen(String name)
{
	wstring nameWstring = name;
	HANDLE port = CreateFile((L"\\\\.\\" + nameWstring).c_str(),
		GENERIC_READ | GENERIC_WRITE,
		0,											// must be opened with exclusive-access
		0,											// default security attributes
		OPEN_EXISTING,					// must use OPEN_EXISTING
		FILE_FLAG_OVERLAPPED,		// overlapped I/O
		0);											// hTemplate must be NULL for comm devices
	if (port == INVALID_HANDLE_VALUE)
	{
		DWORD errorCode = GetLastError();

		switch (errorCode)
		{
			case ERROR_FILE_NOT_FOUND:
				throw PeripheralNotFoundException(jace::java_new<PeripheralNotFoundException>(name, Throwable()));
			case ERROR_ACCESS_DENIED:
				throw PeripheralInUseException(jace::java_new<PeripheralInUseException>(name, Throwable()));
			default:
			{
				throw IOException(jace::java_new<IOException>(L"CreateFile() failed with error: " +
					getErrorMessage(GetLastError())));
			}
		}
	}

	// Associate the file handle with the existing completion port
	HANDLE completionPort = CreateIoCompletionPort(port, ::jperipheral::worker->completionPort, Task::COMPLETION, 0);
	if (completionPort==0)
	{
		throw AssertionError(jace::java_new<AssertionError>(L"CreateIoCompletionPort() failed with error: " +
			getErrorMessage(GetLastError())));
	}

	// Bind the native serial port to Java serial port
	SerialPortContext* result = new SerialPortContext(port);
	return reinterpret_cast<intptr_t>(result);
}

void SerialChannel::nativeConfigure(JInt baudRate,
																		SerialPort_DataBits dataBits,
																		SerialPort_Parity parity,
																		SerialPort_StopBits stopBits,
																		SerialPort_FlowControl flowControl)
{
	SerialPortContext* context = getContext(getJaceProxy());
	DCB dcb = {0};

	if (!GetCommState(context->getPort(), &dcb))
	{
		throw IOException(jace::java_new<IOException>(L"GetCommState() failed with error: " +
			getErrorMessage(GetLastError())));
	}
	dcb.BaudRate = baudRate;

	switch (dataBits.ordinal())
	{
		case SerialPort_DataBits::Ordinals::FIVE:
		{
			dcb.ByteSize = 5;
			break;
		}
		case SerialPort_DataBits::Ordinals::SIX:
		{
		  dcb.ByteSize = 6;
			break;
		}
		case SerialPort_DataBits::Ordinals::SEVEN:
		{
			dcb.ByteSize = 7;
			break;
		}
		case SerialPort_DataBits::Ordinals::EIGHT:
		{
			dcb.ByteSize = 8;
			break;
		}
		default:
			throw AssertionError(jace::java_new<AssertionError>(dataBits));
	}

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

	dcb.fOutxDsrFlow = 0;
	dcb.fDtrControl = DTR_CONTROL_ENABLE;
	dcb.fRtsControl = RTS_CONTROL_ENABLE;
	dcb.fTXContinueOnXoff = false;
	dcb.fErrorChar = false;
	dcb.fNull = false;
	dcb.fAbortOnError = false;
	dcb.fDsrSensitivity = false;
	dcb.fOutxCtsFlow = false;
	dcb.fOutX = false;
	dcb.fInX = false;

	switch (flowControl.ordinal())
	{
		case SerialPort_FlowControl::Ordinals::RTS_CTS:
		{
			dcb.fOutxCtsFlow = true;
			dcb.fRtsControl = RTS_CONTROL_HANDSHAKE;
			break;
		}
		case SerialPort_FlowControl::Ordinals::XON_XOFF:
		{
			dcb.fOutX = true;
			dcb.fInX = true;
			break;
		}
		case SerialPort_FlowControl::Ordinals::NONE:
		{
			// do nothing
			break;
		}
		default:
			throw AssertionError(jace::java_new<AssertionError>(flowControl));
	}

	if (!SetCommState(context->getPort(), &dcb))
	{
		throw IOException(jace::java_new<IOException>(L"SetCommState() failed with error: " +
			getErrorMessage(GetLastError())));
	}
}

//void SerialChannel::printStatus()
//{
//	COMSTAT comStat;
//  DWORD errors;
//  bool fOOP, fOVERRUN, fPTO, fRXOVER, fRXPARITY, fTXFULL, fBREAK, fDNS, fFRAME, fIOE, fMODE;
//
//  // Get and clear current errors on the port.
//	SerialPortContext* context = getContext(getJaceProxy());
//  if (!ClearCommError(context->port, &errors, &comStat))
//	{
//		throw IOException(jace::java_new<IOException>(L"ClearCommError() failed with error: " +
//			getErrorMessage(GetLastError())));
//	}
//
//  // Get error flags.
//  fDNS = errors & CE_DNS;
//  fIOE = errors & CE_IOE;
//  fOOP = errors & CE_OOP;
//  fPTO = errors & CE_PTO;
//  fMODE = errors & CE_MODE;
//  fBREAK = errors & CE_BREAK;
//  fFRAME = errors & CE_FRAME;
//  fRXOVER = errors & CE_RXOVER;
//  fTXFULL = errors & CE_TXFULL;
//  fOVERRUN = errors & CE_OVERRUN;
//  fRXPARITY = errors & CE_RXPARITY;
//
//  // COMSTAT structure contains information regarding
//  // communications status.
//  if (comStat.fCtsHold)
//		cerr << "Tx waiting for CTS signal" << endl;
//
//  if (comStat.fDsrHold)
//		cerr << "Tx waiting for DSR signal" << endl;
//
//  if (comStat.fRlsdHold)
//    cerr << "Tx waiting for RLSD signal" << endl;
//
//  if (comStat.fXoffHold)
//    cerr << "Tx waiting, XOFF char rec'd" << endl;
//
//  if (comStat.fXoffSent)
//    cerr << "Tx waiting, XOFF char sent" << endl;
//
//  if (comStat.fEof)
//    cerr << "EOF character received" << endl;
//
//  if (comStat.fTxim)
//    cerr << "Character waiting for Tx; char queued with TransmitCommChar" << endl;
//
//  if (comStat.cbInQue)
//    cerr << comStat.cbInQue << " bytes have been received, but not read" << endl;
//
//  if (comStat.cbOutQue)
//    cerr << comStat.cbOutQue << " bytes are awaiting transfer" << endl;
//}

void SerialChannel::nativeClose()
{
	SerialPortContext* context = getContext(getJaceProxy());
	delete context;
}

void SerialChannel::nativeRead(ByteBuffer target, JLong timeout, SerialChannel_NativeListener listener)
{
	SerialPortContext* context = getContext(getJaceProxy());
	SingleThreadExecutor& readThread = context->getReadThread();

	boost::shared_ptr<Task> task(new ReadTask(readThread, context->getPort(), target,
		timeout));
	task->setListener(listener);
	readThread.execute(task);
}

void SerialChannel::nativeWrite(ByteBuffer source, JLong timeout, SerialChannel_NativeListener listener)
{
	SerialPortContext* context = getContext(getJaceProxy());
	SingleThreadExecutor& writeThread = context->getWriteThread();

	boost::shared_ptr<Task> task(new WriteTask(writeThread, context->getPort(), source,
		timeout));
	task->setListener(listener);
	writeThread.execute(task);
}
