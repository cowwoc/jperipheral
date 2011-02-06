#############
# FUNCTIONS #
#############

# Returns all header files in the current directory, recursing into sub-directories.
function (get_headers DIRECTORY RESULT)
  file (GLOB_RECURSE HEADER_FILES_TMP "${DIRECTORY}/*.h" "${DIRECTORY}/*.hpp" "${DIRECTORY}/*.inl" "${DIRECTORY}/*.pch" "${DIRECTORY}/*.tsd" "${DIRECTORY}/*.tsp")
  set (${RESULT} ${HEADER_FILES_TMP} PARENT_SCOPE)
endfunction ()

# Returns all source files in the current directory, recursing into sub-directories
function (get_sources DIRECTORY RESULT)
  file (GLOB_RECURSE SOURCE_FILES_TMP "${DIRECTORY}/*.c" "${DIRECTORY}/*.cpp")
  set (${RESULT} ${SOURCE_FILES_TMP} PARENT_SCOPE)
endfunction ()

################
# Build script #
################

cmake_minimum_required (VERSION 2.8)
project (jperipheral)
set (jperipheral_VERSION_MAJOR 1)
set (jperipheral_VERSION_MINOR 0)
add_definitions (-D_UNICODE -DUNICODE -DJPERIPHERAL_EXPORTS -DJACE_PROXY_EXPORTS)

file (TO_NATIVE_PATH ${PROJECT_BINARY_DIR} NATIVE_PROJECT_BINARY_DIR)
file (TO_NATIVE_PATH "$ENV{JACE_HOME}/lib" JACE_LIBRARY)
set (BOOST_INCLUDE "$ENV{BOOST_HOME}")
set (JACE_INCLUDE "$ENV{JACE_HOME}/include")
set (CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} /MP")

include_directories (target/generated-sources/include windows/include ${JACE_INCLUDE} ${BOOST_INCLUDE} $ENV{JAVA_HOME}/include $ENV{JAVA_HOME}/include/win32) 
link_directories (${JACE_LIBRARY})

get_headers ("${CMAKE_CURRENT_SOURCE_DIR}/windows/include" TEMP)
set (HEADER_FILES ${TEMP})
get_headers ("${CMAKE_CURRENT_SOURCE_DIR}/target/generated-sources/include" TEMP)
set (HEADER_FILES ${HEADER_FILES} ${TEMP})

get_sources ("${CMAKE_CURRENT_SOURCE_DIR}/windows/source" TEMP)
set (SOURCE_FILES ${TEMP})
get_sources ("${CMAKE_CURRENT_SOURCE_DIR}/target/generated-sources/source" TEMP)
set (SOURCE_FILES ${SOURCE_FILES} ${TEMP})

add_library (jperipheral SHARED ${SOURCE_FILES} ${HEADER_FILES})
target_link_libraries (jperipheral jace.lib)

add_executable (main windows/source/jperipheral/Main.cpp)
target_link_libraries (main jperipheral)

if (${CMAKE_GENERATOR} MATCHES "Visual Studio")
  add_custom_command (TARGET jperipheral POST_BUILD COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/post-build \"${CMAKE_CFG_INTDIR}\" \"${JACE_LIBRARY}\" \"${NATIVE_PROJECT_BINARY_DIR}\")
endif ()