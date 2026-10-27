#!/bin/sh
set -eu
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$BASE_DIR"
exec java -cp "app:lib/mysql-connector-j-26.7.0.jar" memorygame.server.MigrateMain "$@"
