@echo off
setlocal
cd /d "%~dp0"
if "%~1"=="--check" (
  if not exist "app\memorygame\server\ServerMain.class" exit /b 1
  if not exist "lib\mysql-connector-j-26.7.0.jar" exit /b 1
  if not exist "app\memorygame\client\CardTheme.class" exit /b 1
  for %%I in (fruit animals food vehicles) do if not exist "app\memorygame\client\images\%%I.png" exit /b 1
  if not exist "sql\init.sql" exit /b 1
  if not exist "sql\migrations\20261027-persistence.sql" exit /b 1
  if not exist "sql\migrations\20261027-passwords.sql" exit /b 1
  echo Server package classes, JDBC driver, monitor art, and SQL are present.
  exit /b 0
)
set "MEMORY_GAME_DATA_DIR=%~dp0data"
set MEMORY_GAME_STORAGE=mysql
set MEMORY_GAME_DB_MIGRATE=false
java -cp "app;lib\mysql-connector-j-26.7.0.jar" memorygame.server.ServerMain %*
