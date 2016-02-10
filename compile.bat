@echo off
setlocal enabledelayedexpansion

cd c:/serverroot/

REM the compilation string to run
set compile=javac -cp .;javax.mail.jar *.java

echo %compile%
call %compile%
echo success!