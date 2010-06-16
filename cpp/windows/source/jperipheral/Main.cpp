#include "jace/JNIHelper.h"

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
using std::cout;
using std::endl;

#include <vector>
using std::vector;

#include "jace/proxy/jperipheral/Main.h"
using jace::proxy::jperipheral::Main;

#include "jace/JArray.h"
using jace::JArray;

#include "jace/proxy/java/lang/String.h"
using jace::proxy::java::lang::String;


int main(int argc, char* argv[])
{
	Win32VmLoader loader( Win32VmLoader::JVMV_SUN, Win32VmLoader::JVMT_DEFAULT, "", JNI_VERSION_1_4 );
	argc = 0;
	argv = 0;

	OptionList options;

#ifdef _DEBUG
	#ifndef JACE_AMD64
		std::string platform = "i386/debug";
	#else
		std::string platform = "amd64/debug";
	#endif
#else
	#ifndef JACE_AMD64
		std::string platform = "i386/release";
	#else
		std::string platform = "amd64/release";
	#endif
#endif
	options.push_back(ClassPath("jace-runtime.jar;"
		"joda-time-1.6.jar;"
		"slf4j-api-1.5.6.jar;"
		"google-collect-1.0-rc2.jar;"
		"guice/guice-2.0.jar;"
		"logback-classic-0.9.15.jar;"
		"logback-core-0.9.15.jar;"
		"jperipheral.jar;"));
	//options.push_back( Verbose( Verbose::JNI ) );
	//options.push_back( Verbose( Verbose::CLASS ) );
	options.push_back( CustomOption( "-Xmx256M" ) );
	try
	{
    jace::helper::createVm( loader, options, false );
  }
  catch ( std::exception& e ) {
    cout << "Unable to create the virtual machine: " << endl;
    cout << e.what() << endl;
    return -2;
  }
	try
	{
		Main main;
		JArray<String> args(0);
		main.main(args);
	}
	catch (std::exception& e)
	{
		cout << e.what() << endl;
	}
	jace::helper::destroyVm();
	return 0;
}
