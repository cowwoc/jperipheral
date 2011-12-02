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
using std::wcerr;
using std::endl;

#include <vector>
using std::vector;

#include <string>
using std::string;

#include "boost/filesystem.hpp"
using namespace boost::filesystem;

#include "jace/proxy/org/jperipheral/unsupported/Main.h"
using jace::proxy::org::jperipheral::unsupported::Main;

#include "jace/JArray.h"
using jace::JArray;

#include "jace/proxy/java/lang/String.h"
using jace::proxy::java::lang::String;

#include "jace/proxy/java/lang/Throwable.h"
using jace::proxy::java::lang::Throwable;

/**
	* Adds all JAR files in a directory to the classpath.
	*/
void addJars(path directory, string& classpath)
{
	directory_iterator end;
	for (directory_iterator i(directory); i != end; ++i)
	{
		if (!is_regular_file(i->status()))
			continue;
		if (i->path().extension() != ".jar")
			continue;
		classpath += i->path().string();
		classpath += ";";
	}
}


int main(int argc, char* argv[])
{
	Win32VmLoader loader(Win32VmLoader::JVMV_SUN, Win32VmLoader::JVMT_DEFAULT, "", JNI_VERSION_1_6);
	argc = 0;
	argv = 0;

	OptionList options;
	string classpath;
	addJars(path("."), classpath);
	options.push_back(ClassPath(classpath));
	//options.push_back(Verbose(Verbose::JNI));
	//options.push_back(Verbose(Verbose::CLASS));
	options.push_back(CustomOption("-ea"));
	try
	{
    jace::createVm(loader, options, false);
  }
  catch (std::exception& e)
	{
    wcerr << L"Unable to create the virtual machine: " << endl;
    wcerr << e.what() << endl;
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
		wcerr << e.what() << endl;
	}
	jace::destroyVm();
	return 0;
}
