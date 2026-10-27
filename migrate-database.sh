#!/bin/sh
set -eu
cd "$(dirname "$0")"
: "${MEMORY_GAME_DB_PASSWORD:?Set MEMORY_GAME_DB_PASSWORD for the migration account}"
sh compile.sh
java -cp 'out:lib/*' memorygame.server.MigrateMain
