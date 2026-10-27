#!/bin/sh
set -eu
cd "$(dirname "$0")"
: "${MEMORY_GAME_TEST_DB_PASSWORD:?Set the local test MySQL root password}"
sh compile.sh
javac -encoding UTF-8 -cp 'out:lib/*' -d out test/*.java
java -Djava.awt.headless=true -cp out memorygame.server.PasswordHashesTest
if [ "$#" -eq 0 ]; then
    set -- memorygame.server.GameDatabaseTest \
        memorygame.server.PersistenceExtensionsTest \
        memorygame.test.PasswordMigrationTest \
        memorygame.server.RuntimePrivilegesTest \
        memorygame.server.SchemaParityTest \
        memorygame.server.CommitDisconnectTest \
        memorygame.test.CrashPhasesTest \
        memorygame.test.WhisperSyncTest \
        memorygame.test.GamePersistenceProtocolTest
fi
for test_class do
    printf '\nRunning %s\n' "$test_class"
    java -Djava.awt.headless=true -cp 'out:lib/*' memorygame.test.DatabaseFlowsTest "$test_class"
done
