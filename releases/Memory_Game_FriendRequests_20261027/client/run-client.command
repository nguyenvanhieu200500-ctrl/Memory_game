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
