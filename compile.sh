#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p out
build_sources=$(mktemp "${TMPDIR:-/tmp}/memory-game-sources.XXXXXX")
trap 'rm -f "$build_sources"' EXIT HUP INT TERM
find src -name '*.java' -print > "$build_sources"
javac -encoding UTF-8 -d out @"$build_sources"
mkdir -p out/memorygame/client/audio out/memorygame/client/images
cp src/memorygame/client/audio/*.wav out/memorygame/client/audio/
cp src/memorygame/client/images/*.png out/memorygame/client/images/
printf 'Đã biên dịch bản mới.\n'
