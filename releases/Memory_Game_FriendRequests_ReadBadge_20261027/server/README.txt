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
