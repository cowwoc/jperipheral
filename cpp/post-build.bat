@echo off
rem *******************************
rem * %1 = build configuration    *
rem * %2 = jace library directory *
rem * %3 = output directory       *
rem *******************************

echo Copying dependencies to output directory...
copy "%2\*.dll" "%3\%1" /y
copy "%2\*.pdb" "%3\%1" /y