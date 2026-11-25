@echo off
setlocal
set REPO=%~dp0..
if "%TEMP%"=="" set TEMP=%TMP%
java -cp "%REPO%\buildtools\BuildTools.jar;%REPO%\buildtools\deps\*" net.lax1dude.eaglercraft.v1_8.buildtools.task.init.RegenerateVendored "%REPO%" "%TEMP%\eaglercraft-vendored-regen"
endlocal
