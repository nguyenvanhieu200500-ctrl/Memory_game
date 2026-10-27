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
