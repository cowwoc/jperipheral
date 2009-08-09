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

  options.push_back( ClassPath( "C:/Users/Gili/Documents/jace/trunk/release/lib/jace-runtime.jar;" 
		"C:/Users/Gili/Documents/jperipheral/trunk/java/libraries/joda-time/joda-time-1.6.jar"
		"C:/Users/Gili/Documents/jperipheral/trunk/java/netbeans/dist/JPeripheral.jar;" 
		"C:/Users/Gili/Documents/blueeye/trunk/videoease/java/netbeans/dist/VideoEase.jar;" ) );
	options.push_back( LibraryPath( "C:/Users/Gili/Documents/jperipheral/trunk/dist/i386/debug/native" ) );
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
		JArray<String> args(1);
		args[0] = "test";
		main.main(args);
	}
	catch (std::exception& e)
	{
		cout << e.what() << endl;
	}
	jace::helper::getJavaVM()->DestroyJavaVM();
	return 0;
}
