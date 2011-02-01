#include "jace/Jace.h"

#include "jace/OptionList.h"
using jace::OptionList;
using jace::Option;
using jace::ClassPath;
using jace::LibraryPath;
using jace::Verbose;
using jace::CustomOption;

#include "jace/Win32VmLoader.h"
using jace::Win32VmLoader;

#include <iostream>
using std::cerr;
using std::endl;

#include <vector>
using std::vector;

#include "jace/proxy/org/jperipheral/Main.h"
using jace::proxy::org::jperipheral::Main;

#include "jace/JArray.h"
using jace::JArray;

#include "jace/proxy/java/lang/String.h"
using jace::proxy::java::lang::String;

#include "jace/proxy/java/lang/Throwable.h"
using jace::proxy::java::lang::Throwable;


int main(int argc, char* argv[])
{
	Win32VmLoader loader(Win32VmLoader::JVMV_SUN, Win32VmLoader::JVMT_DEFAULT, "", JNI_VERSION_1_6);
	argc = 0;
	argv = 0;

	OptionList options;

	options.push_back(ClassPath("jperipheral.jar;"
		"joda-time-1.6.jar;"
		"slf4j-api-1.6.1.jar;"
		"google-collect-1.0-rc2.jar;"
		"aopalliance.jar;"
		"guice-2.0.jar;"
		"guice-assistedinject-2.0;"
		"jace-runtime.jar;"
		"logback-classic-0.9.24.jar;"
		"logback-core-0.9.24.jar;"
		));
	//options.push_back(Verbose(Verbose::JNI));
	//options.push_back(Verbose(Verbose::CLASS));
	options.push_back(CustomOption("-ea"));
	try
	{
    jace::createVm(loader, options, false);
  }
  catch (std::exception& e)
	{
    cerr << "Unable to create the virtual machine: " << endl;
    cerr << e.what() << endl;
    return -2;
  }
	try
	{
		Main main;
		JArray<String> args(0);
		main.main(args);
	}
	catch (Throwable& t)
	{
		t.printStackTrace();
	}
	catch (std::exception& e)
	{
		cerr << e.what() << endl;
	}
	jace::destroyVm();
	return 0;
}
