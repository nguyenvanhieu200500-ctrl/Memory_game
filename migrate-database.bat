@echo off
setlocal
cd /d "%~dp0"
if not defined MEMORY_GAME_DB_PASSWORD (
  echo Can cau hinh MEMORY_GAME_DB_PASSWORD cho tai khoan migration.
  exit /b 1
)
call "%~dp0compile.bat"
if errorlevel 1 exit /b 1
java -cp "out;lib/*" memorygame.server.MigrateMain
