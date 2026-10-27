@echo off
setlocal
cd /d "%~dp0"

where docker >nul 2>&1
if errorlevel 1 (
  echo Khong tim thay Docker. Hay cai va mo Docker Desktop.
  pause
  exit /b 1
)

if not exist lib mkdir lib
if not exist lib\mysql-connector-j-26.7.0.jar (
  echo Dang tai MySQL Connector/J 26.7.0...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing 'https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/26.7.0/mysql-connector-j-26.7.0.jar' -OutFile 'lib\mysql-connector-j-26.7.0.jar'"
  if errorlevel 1 (
    echo Khong tai duoc MySQL Connector/J.
    pause
    exit /b 1
  )
)

docker compose up -d mysql adminer
if errorlevel 1 (
  echo Khong khoi dong duoc MySQL. Hay dam bao Docker Desktop dang chay.
  pause
  exit /b 1
)

echo Dang cho MySQL san sang...
for /L %%i in (1,1,60) do (
  for /f %%s in ('docker inspect --format="{{.State.Health.Status}}" memory-game-mysql 2^>nul') do (
    if "%%s"=="healthy" goto ready
  )
  timeout /t 1 /nobreak >nul
)
echo MySQL khoi dong qua lau. Hay xem: docker logs memory-game-mysql
exit /b 1

:ready
echo MySQL da san sang tai localhost:3307.
echo Adminer: http://localhost:8080
echo Cau hinh MEMORY_GAME_DB_PASSWORD va chay MigrateMain bang tai khoan migration.
