# The SQLite extension

Rebol-SQLite 3.46.0.0, served by `jebol-sqlite.jar` as decision 63 says. This
note carries what the code cannot: what the C does, how JEBOL keeps a database
in a file, and every place JEBOL answers differently. How to use it is
`extensions/sqlite/README.md`; its examples run in a test.

## The sources

Rebol-SQLite at tag `3.46.0.0`, MIT licence:
https://github.com/Siskin-framework/Rebol-SQLite.

| File | What it is |
| --- | --- |
| `src/sqlite-rebol-extension.r3` | The 14 commands' specs, and the module header (`Name: sqlite Type: module Exports: []`, version 3.46.0.0). It writes `sqlite-rebol-extension.h`; the copy checked in beside it is stale (3.44.2.0, Apache-2.0) |
| `src/sqlite-command-*.c` | One file per command |
| `src/sqlite-rebol-extension.c` | Registers the handles `sqlite-db` and `sqlite-stmt`, and calls `sqlite3_initialize` |
| `sqlite/sqlite3.c` | The SQLite 3.46.0 amalgamation, public domain, which the reference build compiles |
| `ci-test.r3` | The extension's own test script |
| `sqlite-scheme.reb` | The `sqlite:` scheme |

`extensions/sqlite/src/functionalTest/resources/reference/` holds the C's
answers: `generate.sh` fetches the tag, regenerates the header as the
extension's own build does, compiles it with its amalgamation into a `.rebx`,
and runs seven scripts in `./r3-head`. Its README maps every boundary to the
line that covers it. `TheSqliteModuleFromTheSourceTest` runs the same scripts on
the JVM and `TheSqliteExtensionInTheBrowserEndToEndTest` runs them in Chrome;
both must print the transcripts line for line.

## How the commands are built

`SqliteCommands` is the C, ported function by function. Below it,
`SqliteEngine` is SQLite's own C API -- `prepare_v2`, `step`, `bind_*`,
`column_*`, `stmt_readonly`, `expanded_sql`, `get_autocommit`, `serialize`,
`deserialize` -- so the port makes the calls the C makes, in the order it
makes them.

- **On the JVM**, `Sqlite4jEngine` calls the exports of sqlite4j's WebAssembly
  module directly, below its JDBC layer, which hides most of what the commands
  need. Each open database has an instance of its own, so the handles of two
  interpreters on two threads share nothing. The first instance in a JVM costs
  about a second, later ones 30 to 75 ms and 32 MB.
- **In the browser**, `BrowserSqliteEngine` calls the exports of sqlite.org's
  `sqlite3.wasm` through `jebol-sqlite.js`, which the worker loads the first
  time a database opens. 64-bit integers cross as text. That build has no
  `sqlite3_close`, so the engine calls `sqlite3_close_v2`, which is the same
  thing when, as here, no statement is left.

Both engines are SQLite 3.54.1, the newest sqlite4j and sqlite.org share on
2026-11-07. sqlite.org's zip is pinned by SHA-256
`85ebce90b6d189442b29e11d193337b5e63203aae9b6590dbe89a601b9891da2`; its
published SHA3-256, `87e24f6a...0050`, was checked against the same file once
by hand.

**`exec` is SQLite's own loop.** `sqlite3_exec` is prepare, step and finalize
in turn, skipping the whitespace after each statement. JEBOL runs that loop
itself, so `trace` sees each statement start, each row and each end as the
callbacks would have.

**`trace` is told by the commands, not by SQLite.** Neither engine lets a
callback into the JVM's or the worker's code, so the shared code prints what
SQLite's callbacks print, where SQLite would print it: `STMT` with the expanded
SQL when a statement starts a run, `ROW` for each row, `PROFILE` when a run
ends or is reset partway, and `CLOSE` whenever `close` is asked for --
`sqlite3Close` calls the callback before it looks for live statements, so the
C prints it even when the connection stays open for one.

## How a database lives in a file

Both engines hold the database in memory, and every file goes through the
host's `FilePort`, so SECURE, the files grant and `what-dir` apply as they do
to READ and WRITE.

- **`open`** reads the file. A missing file is made empty at once, as
  `sqlite3_open` makes it. A directory, a missing directory, or a file the port
  cannot read is `[SQLITE] unable to open database file`. A relative path counts
  from `what-dir` when the database is opened, and keeps naming that file after
  `change-dir`. `%:memory:` and `%""` never touch a file.
- **A new database gets 4096-byte pages**, SQLite's default, set explicitly,
  because sqlite.org's browser build defaults to 8192 and a file would then be
  a different size depending on which engine wrote it.
- **After each command that ran a statement that writes**, and only when no
  transaction is open, the database is serialized and written back. So a script
  that never closes still leaves its data on disk, and an uncommitted
  transaction is never written.
- **Before each command**, outside a transaction and with no statement
  mid-run, a database whose file changed since it was read or written is read
  again. A process-wide generation for each file, bumped on every write-back,
  tells two handles in one JVM apart from a file's size and modification time,
  which catch a writer in another process. This is what lets a second handle
  see the first one's write before either closes, as SQLite's file locking lets
  it in the C.

## Where JEBOL answers differently

| Case | The C | JEBOL | Why |
| --- | --- | --- | --- |
| a path SECURE refuses | ignores SECURE: `sqlite3_open` gets the path | refuses as READ would, `access security` | Decision 63: every file goes through the host's port |
| no files grant | opens the file | refuses as READ would | The same |
| a zero-length blob, first column or after an integer | reads a NULL or an integer as a series pointer and crashes | `#{}` | The C never answers |
| a zero-length blob after a text or blob column | answers that column's series again (`select 'abc', x''` is `["abc" #{616263}]`) | `#{}` | A defect, not a behaviour a script could want |
| `last-insert-id` on a closed database | crashes | 0, what SQLite's API armour answers for no connection | The C never answers |
| `info`'s `SQLite-version` | 3.46.0 | 3.54.1 | The engine's own version; the module keeps 3.46.0.0 |
| `info`'s memory figures | `sqlite3_memory_used` and its high-water mark | the same on the JVM; 0 in the browser, whose build does not export them | Both are 0 in the C's own build too |
| `info/of`'s pointers | real addresses | the handle's number and the engine's connection number, as hex | Pointers vary from run to run in the C as well |
| two handles writing one file at once | SQLite's locks serialize them | the last write-back wins | Each handle holds its own copy; reads see the other's writes, as above |
| a file that is not a database | refuses only statements that read its pages | the same, decided by EXPLAINing the statement for a `Transaction` opcode | sqlite4j's in-memory database reads the header on every prepare; the EXPLAIN restores the C's laziness |

`eval` of a query that answers no rows answers `sqlite3_changes`, which is the
count the last write left, not 0. That is the C's answer too; the transcript's
0 is there only because a failed insert had just reset the count.

## The scheme

`sqlite-scheme.reb` is carried as the module's script, so `import 'sqlite`
also makes the `sqlite:` scheme, where Rebol needs it imported apart. Two edits
make it work from inside the module, and nothing else in it changes:

- Its `Rebol [...]` header is removed: the file runs as the module's script, not
  as a module of its own.
- `sqlite: import sqlite` in the scheme's spec, which would import the module
  from inside itself, is `sqlite: none`, and the script ends
  `system/schemes/sqlite/sqlite: self`, handing the scheme the module once it
  exists.

`take`, `read` and `pick` answer the port's one data block, which the next read
empties and fills again, as in the C.
