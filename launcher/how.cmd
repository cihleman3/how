@echo off
rem Startet how in PowerShell und cmd.
rem Grenze: cmd ersetzt %VARIABLE% in der Frage. Fuer solche Fragen how ohne Argumente aufrufen.
setlocal

rem how.jar liegt neben diesem Skript (Installation fuer alle Benutzer) oder in %USERPROFILE%\.local\share\how.
set "HOW_JAR=%~dp0how.jar"
if not exist "%HOW_JAR%" set "HOW_JAR=%USERPROFILE%\.local\share\how\how.jar"

rem Java aus JAVA_HOME, sonst aus dem PATH.
set "HOW_JAVA=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "HOW_JAVA=%JAVA_HOME%\bin\java.exe"
if "%HOW_JAVA%"=="java" where java >nul 2>&1 || goto :nojava

rem Java schreibt umgeleitete Ausgabe sonst in Cp1252, PowerShell liest sie in der Codepage der Konsole.
for /f "tokens=2 delims=:" %%c in ('chcp') do set "HOW_CP=%%c"
set "HOW_CP=%HOW_CP: =%"
set "HOW_CP=%HOW_CP:.=%"
if "%HOW_CP%"=="65001" (set "HOW_ENC=UTF-8") else (set "HOW_ENC=cp%HOW_CP%")

"%HOW_JAVA%" -Dstdout.encoding=%HOW_ENC% -Dstderr.encoding=%HOW_ENC% -jar "%HOW_JAR%" %*
exit /b %ERRORLEVEL%

:nojava
echo how: Java not found. Install Java 17 or newer, or set JAVA_HOME. 1>&2
exit /b 1
