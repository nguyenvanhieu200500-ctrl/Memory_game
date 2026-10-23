@echo off
setlocal
cd /d "%~dp0"
docker compose up -d mysql adminer
if errorlevel 1 (
  echo Khong mo duoc database. Hay khoi dong Docker Desktop truoc.
  pause
  exit /b 1
)
start "Memory Game Database" "http://localhost:8080"
