@echo off
rem Starts how in PowerShell and cmd.
rem Limitation: cmd replaces %VARIABLE% in the question. For such questions, run how without arguments.
setlocal

rem how.jar is next to this script (installation for all users) or in %USERPROFILE%\.local\share\how.
set "HOW_JAR=%~dp0how.jar"
if not exist "%HOW_JAR%" set "HOW_JAR=%USERPROFILE%\.local\share\how\how.jar"

rem Java from JAVA_HOME, otherwise from the PATH.
set "HOW_JAVA=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "HOW_JAVA=%JAVA_HOME%\bin\java.exe"
if "%HOW_JAVA%"=="java" where java >nul 2>&1 || goto :nojava

rem Otherwise Java writes redirected output in Cp1252, while PowerShell reads it in the console's code page.
for /f "tokens=2 delims=:" %%c in ('chcp') do set "HOW_CP=%%c"
set "HOW_CP=%HOW_CP: =%"
set "HOW_CP=%HOW_CP:.=%"
if "%HOW_CP%"=="65001" (set "HOW_ENC=UTF-8") else (set "HOW_ENC=cp%HOW_CP%")

"%HOW_JAVA%" -Dstdout.encoding=%HOW_ENC% -Dstderr.encoding=%HOW_ENC% -jar "%HOW_JAR%" %*
exit /b %ERRORLEVEL%

:nojava
echo how: Java not found. Install Java 17 or newer, or set JAVA_HOME. 1>&2
exit /b 1
