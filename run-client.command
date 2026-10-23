#!/bin/sh
set -eu
cd "$(dirname "$0")"
sh ./compile.sh
exec java -cp out memorygame.client.ClientMain
