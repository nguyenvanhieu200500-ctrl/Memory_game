#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$ROOT"
if [ "$#" -gt 1 ]; then
    printf 'Usage: sh package-release.sh [release-directory]\n' >&2
    exit 2
fi
if [ "$#" -eq 1 ]; then
    RELEASE=$1
    case "$RELEASE" in /*) ;; *) RELEASE="$ROOT/$RELEASE" ;; esac
else
    RELEASE="$ROOT/releases/Memory_Game_$(date -u +%Y%m%d_%H%M%S)"
fi
case "$RELEASE" in
    "$ROOT"|"$ROOT/"|"$ROOT/out"|"$ROOT/dist"|"$ROOT/out/"*|"$ROOT/dist/"*)
        printf 'Refusing unsafe release destination: %s\n' "$RELEASE" >&2; exit 2 ;;
esac
[ ! -e "$RELEASE" ] || { printf 'Release destination already exists: %s\n' "$RELEASE" >&2; exit 2; }
command -v javac >/dev/null 2>&1 || { printf 'javac is required (Java 17 or later).\n' >&2; exit 1; }
command -v zip >/dev/null 2>&1 || { printf 'zip is required to create release archives.\n' >&2; exit 1; }

BUILD=$(mktemp -d "${TMPDIR:-/tmp}/memory-game-package.XXXXXX")
cleanup() { rm -rf "$BUILD"; }
trap cleanup EXIT HUP INT TERM
mkdir -p "$BUILD/classes"
find src -type f -name '*.java' -print > "$BUILD/sources.list"
javac --release 17 -encoding UTF-8 -d "$BUILD/classes" @"$BUILD/sources.list"

mkdir -p "$RELEASE/client/app/memorygame" "$RELEASE/server/app/memorygame" \
    "$RELEASE/server/lib" "$RELEASE/server/sql/migrations"
cp -R "$BUILD/classes/memorygame/client" "$RELEASE/client/app/memorygame/"
cp -R "$BUILD/classes/memorygame/common" "$RELEASE/client/app/memorygame/"
cp -R src/memorygame/client/audio "$RELEASE/client/app/memorygame/client/"
cp -R src/memorygame/client/images "$RELEASE/client/app/memorygame/client/"
cp -R "$BUILD/classes/memorygame/server" "$RELEASE/server/app/memorygame/"
mkdir -p "$RELEASE/server/app/memorygame/client"
cp "$BUILD/classes/memorygame/client/CardTheme.class" "$RELEASE/server/app/memorygame/client/"
cp -R "$BUILD/classes/memorygame/common" "$RELEASE/server/app/memorygame/"
cp -R src/memorygame/client/images "$RELEASE/server/app/memorygame/client/"
cp lib/mysql-connector-j-26.7.0.jar "$RELEASE/server/lib/"
cp docker/mysql/init.sql "$RELEASE/server/sql/"
cp docker/mysql/migrations/*.sql "$RELEASE/server/sql/migrations/"

cat > "$RELEASE/client/run-client.command" <<'CLIENT_SH'
#!/bin/sh
set -eu
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$BASE_DIR"
if [ "${1:-}" = "--check" ]; then
    test -f app/memorygame/client/ClientMain.class
    test -f app/memorygame/client/audio/lobby.wav
    for name in fruit animals food vehicles; do test -f "app/memorygame/client/images/$name.png"; done
    printf 'Client package classes and resources are present.\n'
    exit 0
fi
exec java -cp app memorygame.client.ClientMain "$@"
CLIENT_SH
cat > "$RELEASE/client/run-client.bat" <<'CLIENT_BAT'
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
CLIENT_BAT
cat > "$RELEASE/client/README.txt" <<'CLIENT_README'
MEMORY GAME — CLIENT RELEASE

Requirements: Java 17 or later. This package does not include a Java runtime.

macOS/Linux: run ./run-client.command
Windows: run run-client.bat
The launcher uses only the app directory beside it. It is safe to place this
folder in a path containing spaces. Use --check to verify packaged classes,
images, and music without opening the game.

On the login screen, enter the server address and port (default 12345).
Each player needs a separate account and network access to the server.
The client package contains no player data, database driver, or credentials.
CLIENT_README

cat > "$RELEASE/server/run-server.command" <<'SERVER_SH'
#!/bin/sh
set -eu
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$BASE_DIR"
if [ "${1:-}" = "--check" ]; then
    test -f app/memorygame/server/ServerMain.class
    test -f lib/mysql-connector-j-26.7.0.jar
    test -f app/memorygame/client/CardTheme.class
    for name in fruit animals food vehicles; do test -f "app/memorygame/client/images/$name.png"; done
    test -f sql/init.sql
    test -f sql/migrations/20261027-persistence.sql
    test -f sql/migrations/20261027-passwords.sql
    printf 'Server package classes, JDBC driver, monitor art, and SQL are present.\n'
    exit 0
fi
export MEMORY_GAME_STORAGE=${MEMORY_GAME_STORAGE:-mysql}
export MEMORY_GAME_DB_MIGRATE=false
export MEMORY_GAME_DATA_DIR=${MEMORY_GAME_DATA_DIR:-"$BASE_DIR/data"}
exec java -cp "app:lib/mysql-connector-j-26.7.0.jar" memorygame.server.ServerMain "$@"
SERVER_SH
cat > "$RELEASE/server/run-server.bat" <<'SERVER_BAT'
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
SERVER_BAT
cat > "$RELEASE/server/run-migrations.command" <<'MIGRATE_SH'
#!/bin/sh
set -eu
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$BASE_DIR"
exec java -cp "app:lib/mysql-connector-j-26.7.0.jar" memorygame.server.MigrateMain "$@"
MIGRATE_SH
cat > "$RELEASE/server/run-migrations.bat" <<'MIGRATE_BAT'
@echo off
setlocal
cd /d "%~dp0"
java -cp "app;lib\mysql-connector-j-26.7.0.jar" memorygame.server.MigrateMain %*
MIGRATE_BAT
cat > "$RELEASE/server/database-config.example" <<'CONFIG'
# Replace sample values in the server terminal before running a launcher.
# Java does not automatically read this file.
MEMORY_GAME_DB_URL=jdbc:mysql://127.0.0.1:3307/memory_game?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
MEMORY_GAME_DB_USER=REPLACE_WITH_RUNTIME_OR_MIGRATION_USER
MEMORY_GAME_DB_PASSWORD=REPLACE_WITH_SECRET
CONFIG
cat > "$RELEASE/server/README.txt" <<'SERVER_README'
MEMORY GAME — SERVER RELEASE

Requirements: Java 17 or later and a reachable MySQL database. This package
does not include a Java runtime or database server. Keep database credentials
out of this folder and do not distribute them. database-config.example is a
template only; Java does not load it automatically.

Configure MEMORY_GAME_DB_URL, MEMORY_GAME_DB_USER, and MEMORY_GAME_DB_PASSWORD
in the server terminal. Use a migration account for a deliberate schema update,
then a runtime account with the documented application permissions.

macOS/Linux shell example (replace each value):
  export MEMORY_GAME_DB_URL='jdbc:mysql://127.0.0.1:3307/memory_game?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
  export MEMORY_GAME_DB_USER='REPLACE_WITH_USER'
  export MEMORY_GAME_DB_PASSWORD='REPLACE_WITH_SECRET'
Windows CMD example:
  set "MEMORY_GAME_DB_URL=jdbc:mysql://127.0.0.1:3307/memory_game?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
  set "MEMORY_GAME_DB_USER=REPLACE_WITH_USER"
  set "MEMORY_GAME_DB_PASSWORD=REPLACE_WITH_SECRET"
Set the migration account first for a schema update, then set the runtime
account before starting the game server. Do not put real values in this folder.

macOS/Linux:
  ./run-migrations.command   (only when installing/upgrading the schema)
  ./run-server.command       (default game port 12345)
Windows:
  run-migrations.bat         (only when installing/upgrading the schema)
  run-server.bat             (default game port 12345)

The server launcher defaults to MySQL and never runs migrations automatically.
It writes legacy/local server files under this package's data/ directory;
MySQL remains authoritative when MySQL storage is selected. Use --headless and
an optional port number when needed. The monitor is local to the server host.

The sql/ directory contains installation SQL and upgrade SQL files. Use them
according to the project's database instructions; launching the game server
does not apply these SQL files automatically.
Use run-server.command --check (or run-server.bat --check) to check files
without starting a server.
SERVER_README
chmod +x "$RELEASE/client/run-client.command" "$RELEASE/server/run-server.command" \
    "$RELEASE/server/run-migrations.command"
(cd "$RELEASE/client" && zip -qr ../client.zip .)
(cd "$RELEASE/server" && zip -qr ../server.zip .)
printf 'Release created: %s\n' "$RELEASE"
