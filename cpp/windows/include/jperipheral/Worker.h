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

BEGIN_NAMESPACE_3(jace, peer, jperipheral)

class SerialChannel;

END_NAMESPACE_3(jace, peer, jperipheral)


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
	 * Prevent copying.
	 */
	Worker& operator=(const Worker&);

	friend void RunTasks(Worker&);
	friend class ::jace::peer::jperipheral::SerialChannel;

	boost::thread* thread;
	boost::mutex lock;
	boost::condition running;
	HANDLE completionPort;
};

/**
 * The singleton worker.
 */
extern Worker* worker;

END_NAMESPACE_1(jperipheral)

#endif