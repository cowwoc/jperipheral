#include "jace/peer/jperipheral/SerialPort.h"
using jace::peer::jperipheral::SerialPort;
using jace::proxy::types::JInt;
using jace::proxy::types::JLong;
using jace::proxy::java::lang::String;
using jace::proxy::jperipheral::SerialPort_StopBits;
using jace::proxy::jperipheral::SerialPort_Parity;
using jace::proxy::jperipheral::SerialPort_DataBits;
using jace::proxy::jperipheral::SerialPort_FlowControl;

#include "jace/proxy/jperipheral/PortNotFoundException.h"
using jace::proxy::jperipheral::PortNotFoundException;

#include "jace/proxy/jperipheral/PortInUseException.h"
using jace::proxy::jperipheral::PortInUseException;

#include "jace/proxy/java/lang/AssertionError.h"
using jace::proxy::java::lang::AssertionError;

#include "jace/proxy/java/io/IOException.h"
using jace::proxy::java::io::IOException;

#include "jace/proxy/java/nio/ByteBuffer.h"
using jace::proxy::java::nio::ByteBuffer;

#include <string>
using std::wstring;

#include "jperipheral/SerialPortHelper.h"
using jperipheral::getContext;
using jperipheral::getErrorMessage;
using jperipheral::CompletionPortContext;
using jperipheral::getCompletionPortContext;
using jperipheral::getSourceCodePosition;
using jperipheral::IoTask;

#include <iostream>
using std::cout;
using std::endl;


JLong SerialPort::nativeOpen(String name, String path)
{
	wstring pathAsString = path;
	HANDLE result = CreateFile(pathAsString.c_str(),
		GENERIC_READ | GENERIC_WRITE,
		0,											// must be opened with exclusive-access
		0,											// default security attributes
		OPEN_EXISTING,					// must use OPEN_EXISTING
		FILE_FLAG_OVERLAPPED,		// overlapped I/O
		0);											// hTemplate must be NULL for comm devices
	if (result == INVALID_HANDLE_VALUE)
	{
		DWORD errorCode = GetLastError();

		switch (errorCode)
		{
			case ERROR_FILE_NOT_FOUND:
				throw PortNotFoundException(name, 0);
			case ERROR_ACCESS_DENIED:
				throw PortInUseException(name, 0);
			default:
				throw IOException(L"CreateFile() failed with error: " + getErrorMessage(GetLastError()));
		}
	}

	// Associate the file handle with the completion port
	CompletionPortContext* context = getCompletionPortContext();
	HANDLE completionPort = CreateIoCompletionPort(result, context->completionPort, IoTask::COMPLETION, 0);
	if (completionPort==0)
		throw AssertionError(L"CreateIoCompletionPort() failed with error: " + getErrorMessage(GetLastError()));
	return reinterpret_cast<intptr_t>(result);
}

void SerialPort::nativeConfigure(JInt baudRate,
																 SerialPort_DataBits dataBits,
																 SerialPort_Parity parity,
																 SerialPort_StopBits stopBits,
																 SerialPort_FlowControl flowControl)
{
	HANDLE port = getContext(getJaceProxy());
	DCB dcb = {0};
	
	if (!GetCommState(port, &dcb))
		throw IOException(L"GetCommState() failed with error: " + getErrorMessage(GetLastError()));
	dcb.BaudRate = baudRate;

	if (dataBits.equals(dataBits.FIVE()))
		dcb.ByteSize = 5;
	else if (dataBits.equals(dataBits.SIX()))
		dcb.ByteSize = 6;
	else if (dataBits.equals(dataBits.SEVEN()))
		dcb.ByteSize = 7;
	else if (dataBits.equals(dataBits.EIGHT()))
		dcb.ByteSize = 8;
	else throw AssertionError(dataBits);

	if (parity.equals(SerialPort_Parity::EVEN()))
	{
		dcb.fParity = true;
		dcb.Parity = EVENPARITY;
	}
	else if (parity.equals(SerialPort_Parity::MARK()))
	{
		dcb.fParity = true;
		dcb.Parity = MARKPARITY;
	}
	else if (parity.equals(SerialPort_Parity::NONE()))
	{
		dcb.fParity = false;
		dcb.Parity = NOPARITY;
	}
	else if (parity.equals(SerialPort_Parity::ODD()))
	{
		dcb.fParity = true;
		dcb.Parity = ODDPARITY;
	}
	else if (parity.equals(SerialPort_Parity::SPACE()))
	{
		dcb.fParity = true;
		dcb.Parity = SPACEPARITY;
	}
	else throw AssertionError(parity);

	if (stopBits.equals(SerialPort_StopBits::ONE()))
		dcb.StopBits = ONESTOPBIT;
	else if (stopBits.equals(SerialPort_StopBits::ONE_POINT_FIVE()))
		dcb.StopBits = ONE5STOPBITS;
	else if (stopBits.equals(SerialPort_StopBits::TWO()))
		dcb.StopBits = TWOSTOPBITS;
	else
		throw AssertionError(stopBits);

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
	if (flowControl.equals(SerialPort_FlowControl::RTS_CTS()))
	{
		dcb.fOutxCtsFlow = true;
		dcb.fRtsControl = RTS_CONTROL_HANDSHAKE;
	}
	else if (flowControl.equals(SerialPort_FlowControl::XON_XOFF()))
	{
		dcb.fOutX = true;
		dcb.fInX = true;
	}
	else if (flowControl.equals(SerialPort_FlowControl::NONE()))
	{
		// do nothing
	}
	else
		throw AssertionError(flowControl);

	if (!SetCommState(port, &dcb))
		throw IOException(L"SetCommState() failed with error: " + getErrorMessage(GetLastError()));
}

void SerialPort::printStatus()
{
	COMSTAT comStat;
  DWORD errors;
  bool fOOP, fOVERRUN, fPTO, fRXOVER, fRXPARITY, fTXFULL, fBREAK, fDNS, fFRAME, fIOE, fMODE;

  // Get and clear current errors on the port.
	HANDLE port = getContext(getJaceProxy());
  if (!ClearCommError(port, &errors, &comStat))
		throw IOException(L"ClearCommError() failed with error: " + getErrorMessage(GetLastError()));

  // Get error flags.
  fDNS = errors & CE_DNS;
  fIOE = errors & CE_IOE;
  fOOP = errors & CE_OOP;
  fPTO = errors & CE_PTO;
  fMODE = errors & CE_MODE;
  fBREAK = errors & CE_BREAK;
  fFRAME = errors & CE_FRAME;
  fRXOVER = errors & CE_RXOVER;
  fTXFULL = errors & CE_TXFULL;
  fOVERRUN = errors & CE_OVERRUN;
  fRXPARITY = errors & CE_RXPARITY;

  // COMSTAT structure contains information regarding
  // communications status.
  if (comStat.fCtsHold)
		cout << "Tx waiting for CTS signal" << endl;

  if (comStat.fDsrHold)
		cout << "Tx waiting for DSR signal" << endl;

  if (comStat.fRlsdHold)
    cout << "Tx waiting for RLSD signal" << endl;

  if (comStat.fXoffHold)
    cout << "Tx waiting, XOFF char rec'd" << endl;

  if (comStat.fXoffSent)
    cout << "Tx waiting, XOFF char sent" << endl;
  
  if (comStat.fEof)
    cout << "EOF character received" << endl;
  
  if (comStat.fTxim)
    cout << "Character waiting for Tx; char queued with TransmitCommChar" << endl;

  if (comStat.cbInQue)
    cout << comStat.cbInQue << " bytes have been received, but not read" << endl;

  if (comStat.cbOutQue)
    cout << comStat.cbOutQue << " bytes are awaiting transfer" << endl;
}

class CancelTask: public IoTask
{
public:
	CancelTask(HANDLE port): 
		IoTask(port, 0, 0, 0), errorCode(ERROR_SUCCESS)
	{
		taskDone = CreateEvent(0, false, false, 0);
		if (taskDone == 0)
			throw IOException(L"CreateEvent() failed with error: " + getErrorMessage(GetLastError()));
	}

	virtual void updateJavaBuffer(int)
	{}

	virtual void run()
	{
		if (!CancelIo(port))
			errorCode = GetLastError();
		if (!SetEvent(taskDone))
			throw IOException(L"SetEvent() failed with error: " + getErrorMessage(GetLastError()));
	}

	virtual ~CancelTask()
	{
		if (!CloseHandle(taskDone))
			throw IOException(L"CloseHandle() failed with error: " + getErrorMessage(GetLastError()));
	}

	DWORD errorCode;
	HANDLE taskDone;
};

void SerialPort::nativeClose()
{
	HANDLE port = getContext(getJaceProxy());

	// block until cancel() completes
	CancelTask* task = new CancelTask(port);

	CompletionPortContext* windowsContext = getCompletionPortContext();
	if (!PostQueuedCompletionStatus(windowsContext->completionPort, 0, IoTask::RUN, 
		&task->overlapped))
	{
		delete task;
		throw IOException(getSourceCodePosition(L__FILE__, __LINE__) + 
			L"PostQueuedCompletionStatus() failed with error: " + getErrorMessage(GetLastError()));
	}
	switch (WaitForSingleObject(task->taskDone, INFINITE))
	{
		case WAIT_OBJECT_0:
		{
			if (task->errorCode != ERROR_SUCCESS)
				throw IOException(L"WaitForSingleObject() failed with error: " + getErrorMessage(GetLastError()));
			break;
		}
		default:
			throw IOException(L"WaitForSingleObject() failed with error: " + getErrorMessage(GetLastError()));
	}

	delete task;
	if (!CloseHandle(port))
		throw IOException(L"CloseHandle() failed with error: " + getErrorMessage(GetLastError()));
	jace::helper::detach();
}