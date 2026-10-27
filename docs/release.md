# Creating and running a release

From the project root, run `sh package-release.sh`. The script compiles the
current `src/` with `javac --release 17` into a fresh system temporary directory,
then creates a timestamped folder under `releases/` with separate `client/` and
`server/` folders and ZIP archives. To choose a destination, pass a new
directory, for example `sh package-release.sh "releases/Memory Game test"`.
The destination must not already exist. The script does not read `out/`, old
`dist/`, player data, logs, test data, or local environment files.

Give players `client.zip`. They need Java 17 or later; the archive does not
contain a Java runtime. Start `client/run-client.command` on macOS/Linux or
`client/run-client.bat` on Windows. Use the server address and port shown by
the server operator (default port 12345).

For a server, extract `server.zip` on the host and configure the database
environment variables in that terminal using `server/database-config.example`
as a template; example `export` and Windows `set` commands are in
`server/README.txt`. Run the migration launcher only for an intentional schema
installation or upgrade, then set the runtime account and run the server
launcher. macOS/Linux launchers end in `.command`; Windows launchers end in
`.bat`. The server package needs
Java 17 or later and a separately managed MySQL server; it does not bundle a
Java runtime or MySQL. Its local `data/` directory is created as needed and
contains no preloaded player data.

Both package launchers accept `--check` to check required package files without
starting the application. The client and server launchers resolve paths beside
themselves and support installation paths containing spaces.
