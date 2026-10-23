@echo off
setlocal
cd /d "%~dp0"
where javaw >nul 2>&1
if errorlevel 1 (
  echo Khong tim thay Java. Hay cai JDK 17 tro len.
  pause
  exit /b 1
)
start "Memory Game Client" javaw -cp app memorygame.client.ClientMain
