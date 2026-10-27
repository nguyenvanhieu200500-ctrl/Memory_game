@echo off
setlocal
cd /d "%~dp0"
java -cp "app;lib\mysql-connector-j-26.7.0.jar" memorygame.server.MigrateMain %*
