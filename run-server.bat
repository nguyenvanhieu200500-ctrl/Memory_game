@echo off
setlocal
cd /d "%~dp0"
if not exist out\memorygame\server\ServerMain.class call "%~dp0compile.bat"
if errorlevel 1 exit /b 1
if not exist lib\mysql-connector-j-26.7.0.jar call "%~dp0setup-database.bat"
if errorlevel 1 exit /b 1
docker compose up -d mysql adminer
if errorlevel 1 exit /b 1
set MEMORY_GAME_STORAGE=mysql
java -cp "out;lib\mysql-connector-j-26.7.0.jar" memorygame.server.ServerMain 12345
