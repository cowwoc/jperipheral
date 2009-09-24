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
		"C:/Users/Gili/Documents/jperipheral/trunk/java/libraries/joda-time/joda-time-1.6.jar;"
		"C:/Users/Gili/Documents/jperipheral/trunk/java/libraries/slf4j/slf4j-api-1.5.6.jar;"
		"C:/Users/Gili/Documents/jperipheral/trunk/java/libraries/logback/logback-classic-0.9.15.jar;"
		"C:/Users/Gili/Documents/jperipheral/trunk/java/libraries/logback/logback-core-0.9.15.jar;"
		"C:/Users/Gili/Documents/jperipheral/trunk/dist/i386/release/java/jperipheral.jar;" ) );
#ifdef _DEBUG
	std::string libPath = "C:/Users/Gili/Documents/jperipheral/trunk/cpp/build/i386/release/msvc/i386/debug";
#else
	std::string libPath = "C:/Users/Gili/Documents/jperipheral/trunk/cpp/build/i386/release/msvc/i386/release";
#endif
	options.push_back( LibraryPath( libPath ) );
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
		std::wstring libPathW(libPath.length(), L'');
		std::copy(libPath.begin(), libPath.end(), libPathW.begin());
		LoadLibrary(std::wstring(libPathW + L"\\JPeripheral.dll").c_str());
		Main main;
		JArray<String> args(0);
		main.main(args);
	}
	catch (std::exception& e)
	{
		cout << e.what() << endl;
	}
	jace::helper::getJavaVM()->DestroyJavaVM();
	return 0;
}
