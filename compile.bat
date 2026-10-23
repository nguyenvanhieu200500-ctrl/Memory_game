@echo off
setlocal
cd /d "%~dp0"
if not exist out mkdir out
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -d out @sources.txt
set RESULT=%ERRORLEVEL%
del sources.txt
if not "%RESULT%"=="0" exit /b %RESULT%
if not exist out\memorygame\client\audio mkdir out\memorygame\client\audio
copy /Y src\memorygame\client\audio\lobby.wav out\memorygame\client\audio\lobby.wav >nul
if errorlevel 1 exit /b 1
if not exist out\memorygame\client\images mkdir out\memorygame\client\images
copy /Y src\memorygame\client\images\*.png out\memorygame\client\images\ >nul
if errorlevel 1 exit /b 1
echo Bien dich thanh cong.
