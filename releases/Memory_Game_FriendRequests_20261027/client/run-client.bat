@echo off
setlocal
cd /d "%~dp0"
if "%~1"=="--check" (
  if not exist "app\memorygame\client\ClientMain.class" exit /b 1
  if not exist "app\memorygame\client\audio\lobby.wav" exit /b 1
  for %%I in (fruit animals food vehicles) do if not exist "app\memorygame\client\images\%%I.png" exit /b 1
  echo Client package classes and resources are present.
  exit /b 0
)
java -cp "app" memorygame.client.ClientMain %*
