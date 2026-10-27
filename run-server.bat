@echo off
setlocal
cd /d "%~dp0"
if not defined MEMORY_GAME_DB_PASSWORD (
  echo Can cau hinh MEMORY_GAME_DB_PASSWORD cho tai khoan runtime. Xem docs\database.md.
  exit /b 1
)
if not exist lib\mysql-connector-j-26.7.0.jar (
  echo Chay setup-database.bat, sau do migrate-database.bat truoc khi khoi dong server.
  exit /b 1
)
call "%~dp0compile.bat"
if errorlevel 1 exit /b 1
rem Docker is started during setup, using its own credentials. Do not reconfigure it with the runtime password.
set MEMORY_GAME_STORAGE=mysql
set MEMORY_GAME_DB_MIGRATE=false
java -cp "out;lib\mysql-connector-j-26.7.0.jar" memorygame.server.ServerMain %*
