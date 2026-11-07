# What Rebol-SQLite answers in its own C

Transcripts of Rebol-SQLite 3.46.0.0
(https://github.com/Siskin-framework/Rebol-SQLite, tag `3.46.0.0`, MIT), its C
and its SQLite 3.46.0 amalgamation compiled here from source and loaded into
`./r3-head`. No release binary is run.

    generate.sh      fetches the tag, compiles it, runs every script, writes every .txt

The extension's checked-in `sqlite-rebol-extension.h` is stale (it says 3.44.2.0
and Apache-2.0), so the generator first runs `src/sqlite-rebol-extension.r3` in
`./r3-head`, as the extension's own build does, and compiles against what that
writes. Each script runs in a fresh folder of its own, and paths under it are
printed as `%./`.

Every script imports its first argument when given one and `import 'sqlite`
otherwise, so JEBOL runs the same file. A line reads `[code] => answer`, where an
error answers its type, id and first argument.

| Script | What it covers |
| --- | --- |
| `module-commands.r3` | the module's header and words; every command, its errors and its boundaries |
| `module-one-case.r3` | cases that crash the C, each run in a process of its own: a closed database, and zero-length blobs |
| `module-files.r3` | files: creation on `open`, reading back, two handles on one file, a transaction, relative paths, a file that is not a database, `:memory:` and `%""` |
| `module-secure.r3` | `secure [file throw]`, which the C never consults |
| `module-trace.r3` | what `trace` prints for each mask, with nanoseconds shown as `<n>` |
| `module-scheme.r3` | the `sqlite:` scheme, with the extension's `sqlite-scheme.reb` as the second argument |

`info` answers pointers and memory figures that vary from run to run, so
`module-commands.r3` prints pointers as `<pointer>`, and SQLite's version and
memory as `?`.

## Where the C never answers

| Case | What the C does |
| --- | --- |
| a zero-length blob in a result's first column | reads a NULL series and crashes |
| a zero-length blob after an integer | reads the integer as a series pointer and crashes |
| a zero-length blob after a text or blob column | answers that column's series again: `select 'abc', x''` is `["abc" #{616263}]` |
| `last-insert-id` on a closed database | crashes |

## The boundaries, and what covers each

| Boundary | Covered by |
| --- | --- |
| empty SQL | `exec db ""`, `exec db "   "`, `eval db ""`, `prepare db ""` |
| several statements in one `exec` | `exec db "select * from t; select count(*) as n from t"`, and one failing in the middle |
| several statements in one `eval` | `eval db "select a from t order by a; select 99"` |
| a syntax error | `exec`, `eval` and `prepare` given `"nonsense"` |
| a constraint error | `exec` into `nn`, `eval` into `q`, `step` of `bad` and of `dup` |
| `/rows` 0, -1, 1, past the rows | `step/rows st 0`, `-1`, `1`, `100` |
| `/rows` over 1000 | `step/rows many 1001` twice over 1,500 rows |
| `/with` fewer values, more, none | `step/with w [1]`, `[1 2 3]`, `[]` |
| `/with` each bindable type | integer, decimal, string, none, both logics |
| `/with` refusals | `binary!`, `block!`, an unset word, a lit-word, a `date!`, a path, a paren |
| `/with` resolving | a word, a get-word, a get-path |
| strings not at their head | `step/with` and `eval` given `next "xA_di"`; `eval` and `exec` given `next "Xselect ..."` |
| a wide string | `"ěšč"`, `"Océane"`, `"😀"` bound and read back |
| NULL, a large blob, 64-bit extremes | `module-commands.r3`'s values section |
| a zero-length blob | `module-one-case.r3` |
| stepping after DONE | `ro` (read-only) and `wr` (a write) |
| `reset`, then stepping again | `st` and `ro` |
| a finalized statement | `step`, `columns`, `reset`, `finalize`, `eval` and `eval` in a block |
| a closed database | `module-one-case.r3` cases 1 to 10 |
| one handle kind for the other | `exec`, `eval`, `prepare`, `last-insert-id`, `trace`, `close` given a statement; `step`, `columns`, `reset`, `finalize`, `eval` given a database |
| another extension's handle | `rc4/key`, to `exec`, `step`, `eval` and `info/of` |
| `eval` with nested rows, a short row, a long row | the `p` table |
| `eval` with a finalized statement in its block | `eval db reduce [st 1]` |
| `open` of `:memory:` | `module-files.r3` |
| `open` of a missing directory, of a directory | `module-files.r3` |
| `open` creating the file before any write | `module-files.r3`, `size?` after `open` |
| a path relative to `what-dir` | `change-dir %sub/` in `module-files.r3` |
| a path SECURE refuses | `module-secure.r3` |
| `shutdown` twice, then `initialize` | `module-commands.r3`'s library section |
| scheme `open` without `/new` on a missing file | `module-scheme.r3` |
| scheme `read/part`, `take`, `pick` | `module-scheme.r3` |
| scheme `insert` of the same text twice | `length? db/state/statements` stays 1 |
| a scheme action on a closed port | every action after `close` |
