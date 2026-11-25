@echo off
setlocal
set REPO=%~dp0..
if "%OUT_DIR%"=="" set OUT_DIR=%REPO%\dist
if "%MAVEN_LOCAL%"=="" set MAVEN_LOCAL=%REPO%\buildtools\maven
if "%BUILD_TEMP_DIR%"=="" set BUILD_TEMP_DIR=%TEMP%\eaglercraft-vendored-build
if "%JAVA%"=="" set JAVA=java

if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"
if not exist "%MAVEN_LOCAL%" mkdir "%MAVEN_LOCAL%"
if not exist "%BUILD_TEMP_DIR%" mkdir "%BUILD_TEMP_DIR%"

set CONFIG=%TEMP%\eaglercraft-vendored-build-config.json
> "%CONFIG%" echo {
>> "%CONFIG%" echo  "repositoryFolder": "%REPO:\=/%",
>> "%CONFIG%" echo  "minecraftSourceDirectory": "%REPO:\=/%/sources/minecraft/java",
>> "%CONFIG%" echo  "minecraftAssetsDirectory": "%REPO:\=/%/sources/minecraft/assets",
>> "%CONFIG%" echo  "minecraftLanguagesDirectory": "%REPO:\=/%/sources/minecraft/lang",
>> "%CONFIG%" echo  "outputDirectory": "%OUT_DIR:\=/%",
>> "%CONFIG%" echo  "temporaryDirectory": "%BUILD_TEMP_DIR:\=/%",
>> "%CONFIG%" echo  "mavenLocal": "%MAVEN_LOCAL:\=/%",
>> "%CONFIG%" echo  "productionIndex": "%REPO:\=/%/buildtools/production-index.html",
>> "%CONFIG%" echo  "productionFavicon": "%REPO:\=/%/buildtools/production-favicon.png",
>> "%CONFIG%" echo  "generateOfflineDownload": true,
>> "%CONFIG%" echo  "offlineDownloadTemplate": "%REPO:\=/%/sources/setup/workspace_template/javascript/OfflineDownloadTemplate.txt",
>> "%CONFIG%" echo  "minifying": true,
>> "%CONFIG%" echo  "writeSourceMap": false,
>> "%CONFIG%" echo  "keepTemporaryFiles": false
>> "%CONFIG%" echo }

"%JAVA%" -Deaglercraft.TeaVMBridge="%REPO%\buildtools\TeaVMBridge.jar" -cp "%REPO%\buildtools\BuildTools.jar;%REPO%\buildtools\deps\*" net.lax1dude.eaglercraft.v1_8.buildtools.gui.headless.CompileLatestClientHeadless -y "%CONFIG%"
endlocal
