#!/bin/sh
set -eu
cd "$(dirname "$0")"
: "${MEMORY_GAME_DB_PASSWORD:?Set MEMORY_GAME_DB_PASSWORD for the runtime account; see docs/database.md}"
if [ ! -f lib/mysql-connector-j-26.7.0.jar ]; then
    printf '%s\n' 'Missing lib/mysql-connector-j-26.7.0.jar; complete database setup and migration first.' >&2
    exit 1
fi
sh compile.sh
export MEMORY_GAME_STORAGE=mysql MEMORY_GAME_DB_MIGRATE=false
exec java -cp 'out:lib/*' memorygame.server.ServerMain "$@"
