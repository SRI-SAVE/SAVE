@echo off
SET CURDIR="%cd%"
set VWF_DIR="%CURDIR%\..\VWF"

cd %VWF_DIR%
for %%X in (node.exe) do (set NODEJS_FOUND=%%~$PATH:X)
if defined NODEJS_FOUND (
	node node-server -a public
) else (
	echo It appears that Nodejs is not installed.
	echo You can find the Nodejs installer in the installer subdirectory
	pause
)
cd %CURDIR%


