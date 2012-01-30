#ifndef JPERIPHERAL_WORKER_H
#define JPERIPHERAL_WORKER_H

#include "jace/Jace.h"

#define WIN32_LEAN_AND_MEAN
#include <windows.h>

#pragma warning(push)
#pragma warning(disable: 4103 4244 4512)
#include <boost/thread/thread.hpp>
#include <boost/thread/mutex.hpp>
#include <boost/thread/condition.hpp>
#pragma warning(pop)

BEGIN_NAMESPACE_4(jace, peer, org, jperipheral)

class SerialChannel;

END_NAMESPACE_4(jace, peer, org, jperipheral)


BEGIN_NAMESPACE_1(jperipheral)

/**
 * Executes asynchronous tasks.
 */
class Worker
{
public:
	/**
	 * Creates a Worker.
	 */
	Worker();

	/**
	 * Destroys the Worker.
	 */
	~Worker();

private:
	/**
	 * The function executed by the thread.
	 */
	void run();
	/**
	 * Prevent copying.
	 */
	Worker& operator=(const Worker&);

	friend class ::jace::peer::org::jperipheral::SerialChannel;

	boost::thread* thread;
	boost::mutex mutex;
	boost::condition running;
	HANDLE completionPort;
};

/**
 * The singleton worker.
 */
extern Worker* worker;

/**
 * Pointer to CancelIoEx() if the feature is available (Windows Vista and newer), null otherwise.
 */
typedef BOOL (WINAPI *CancelIoExType)(HANDLE hFile, LPOVERLAPPED lpOverlapped);
extern CancelIoExType cancelIoEx;


END_NAMESPACE_1(jperipheral)

#endif