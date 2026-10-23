@echo off
setlocal
cd /d "%~dp0"
call "%~dp0compile.bat"
if errorlevel 1 exit /b 1
javaw -cp out memorygame.client.ClientMain
