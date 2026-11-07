# The SQLite extension

Rebol-SQLite 3.46.0.0, served by `jebol-sqlite.jar` as decision 63 says. This
note carries what the code cannot: what the C does, and where JEBOL differs.
The work is goal 1a. Until it is built, everything here was read from the C on
2026-11-07, and the reference transcripts settle the exact answers.

## The sources

Rebol-SQLite at tag `3.46.0.0`, MIT licence:
https://github.com/Siskin-framework/Rebol-SQLite.

| File | What it is |
| --- | --- |
| `src/sqlite-rebol-extension.r3` | The 14 commands' specs, and the module header (`Name: sqlite Type: module Exports: []`, version 3.46.0.0) |
| `src/sqlite-command-*.c` | One file per command |
| `src/sqlite-rebol-extension.c` | Registers the handles `sqlite-db` and `sqlite-stmt`, and calls `sqlite3_initialize` |
| `sqlite/sqlite3.c` | The SQLite 3.46.0 amalgamation, public domain, which the reference build compiles |
| `ci-test.r3` | The extension's own test script, the larger source of cases |
| `sqlite-scheme.reb` | The `sqlite:` scheme |

`rebol3-source/src/tests/test-extension-sqlite.r3` is a third source of cases.
`f-extension.c:543` turns a command's error string into an error, which JEBOL
already has as `command-fail` carrying the message.

## What each command does

| Command | Answers | Notes from the C |
| --- | --- | --- |
| `info /of handle` | a string | Without `/of`: needed and current Rebol version, `SQLite-version`, `SQLite-memory`, `SQLite-mem-top`. With `/of` on a db: the context and db pointers, and `sqlite-buffer-size`. With `/of` on a statement: two pointers, `last-result-code`, `bind-parameters`, `data-count`. Any other handle: an empty string |
| `open file` | a `sqlite-db` handle | The file's text is handed to SQLite as it is. Failure: `"[SQLITE] <errstr>"` |
| `exec db sql` | unset | Runs every statement, separated by semicolons. Prints each row as `name = value` lines, `NULL` for null, then a blank line. Failure: `"[SQLITE] <errstr> <errmsg>"` |
| `eval db query` | a block of rows laid out flat, or the count of rows changed | `query` is a string, a statement handle, or a block. A block holds a string or a statement handle, then the parameters, either flat or as one block per row. It binds a string from the head of its series, ignoring the index. Failure: `"[SQLITE] <errstr>"` |
| `last-insert-id db` | an integer | |
| `prepare db sql` | a `sqlite-stmt` handle | Sets `last_result_code` to `SQLITE_ROW`. Failure: `"[SQLITE] <errstr> <errmsg>"` |
| `step stmt /rows count /with parameters` | a block of rows laid out flat, or none | Without `/rows`, one row. A count below 1 means every row. `/with` binds by position: integer, decimal, string, none and logic, with words, get-words and get-paths resolved first. Any other type fails with `"[SQLITE] Unsupported value type!"`, and `binary!` is one of them. A string binds from the head of its series, ignoring the index (`ci-test`'s `next "xA_di"` exercises this). A read-only statement that has already answered `DONE` answers none without stepping. Other failures: `"[SQLITE] Statement is busy!"`, `"[SQLITE] Statement misuse!"`, or the bare errstr, with no prefix |
| `columns stmt` | a block of strings, or none for no columns | |
| `reset stmt` | unset | Sets `last_result_code` back to `SQLITE_ROW` |
| `finalize stmt` | unset | Every later use is `"Invalid SQLite STMT handle!"` |
| `close db` | unset | The handle still passes its check afterwards, and SQLite itself refuses what follows |
| `trace db mask` | unset | `mask and 15`. Prints `TRACE[n] STMT: <sql>`, `PROFILE: <n>ns`, `ROW` or `CLOSE` with a raw `printf`. JEBOL prints through `call.writeLine` |
| `initialize`, `shutdown` | `#(true)` | |

Columns map INTEGER to `integer!`, FLOAT to `decimal!`, TEXT to `string!`, BLOB
to `binary!` and NULL to `none`. A zero-length blob reaches no `memcpy`, so what
the C answers for one is for the reference to say.

## The scheme

Its actions are `open /new`, `open?`, `close`, `write` (exec), `insert`
(prepare, caching statements by their text), `take /part`, `read /part`, `pick`
(read what was just inserted) and `modify 'trace-level`. A missing file without
`/new` raises `access cannot-open`. The scheme starts with `sqlite: import
sqlite`, which cannot run inside the module it imports, so JEBOL's copy uses the
module's own words: a borrowed-file edit.
