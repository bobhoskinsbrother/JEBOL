# Goals

What is left to do on this port, in one order. A finished goal, or a finished
part of one, is deleted; what it said is in the git history.

The counts live here and nowhere else. `README.md`, `docs/porting-guide.md`
and `using-jebol.md` point here for them.

---

## Where the port stands

Measured on 2026-10-12, the jar and web build rows on 2026-11-06,
and the timing rows on 2026-10-13. The timing rows were taken on an arm64 Mac; the same two figures on
an Intel one read about 122ms median and 187ms p95, so compare a change against
a baseline from the same machine rather than against the row.

| Measure                       | Reads                                                                                                                                                                                                             |
|-------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `fails-on-rebol-too.txt`      | 160 a real 3.22.5 also fails or never runs                                                                                                                                                                        |
| `scripts/c-parity.py`         | 279 of 279 C functions match R3's surface                                                                                                                                                                         |
| `scripts/runtime-parity.py`   | 0 of 582 absent; 3 differ, all deliberate -- see Standing                                                                                                                                                         |
| `scripts/error-parity.py`     | 120 of Rebol's 142 error ids can be raised                                                                                                                                                                        |
| `PortingBacklogTest`          | reports `init-schemes` missing, wrongly -- see goal 5                                                                                                                                                             |
| `corpus/`                     | 1,140 of 1,140 entries pass                                                                                                                                                                                       |
| the shipped jar               | 3,939 KB, of which the 2010 GUI is 75 KB before compression, and no dependencies                                                                                                                                  |
| the web build                 | 16.9 MB of WebAssembly (5.1 MB gzipped) beside a 38 KB loader, a 9.6 KB worker and a 556 KB font; the whole site 17.6 MB, 5.4 MB gzipped                                                                          |
| a warm `Interpreter.create()` | 44 to 57ms median across runs (45.5ms on 2026-10-22, with the GUI carried and not loaded), 66 to 73ms p95, the first call 424 to 427ms; 16,300 of them per `./gradlew functionalTests` (`scripts/timing.py boot`) |
| the interpreter's speed       | 8x to 14x slower than a real 3.22.5 on `scripts/timing.py speed`; about 2x more again in the browser                                                                                                              |

---

## How to work on any of them

Read `CLAUDE.md` first.

- **`./r3-head`** is a real Rebol 3.22.5 and the authority. It takes a script
  file with a `Rebol []` header. Ask it rather than guessing. It is not
  checked in: `scripts/build-r3.sh` builds it from `rebol3-source/`, and says
  how to get the one Rebol it needs to do that.
- **`rebol3-source/`** is the C, searched through the IntelliJ MCP.
- **`scripts/sweep.py <file.r3>`** shows which suite assertions answer
  differently; **`org.jebol.suite.SuiteStops`** shows which raise. Fix a
  raise first: everything after it never runs.
- **`org.jebol.adapter.cli.Repl`** reads a script from standard input, one
  expression per line, so the same cases can be put to both interpreters and
  diffed.
- **`scripts/serve-example.sh <app directory> [port]`** builds the web module,
  assembles an app such as `examples/blend2d` as a site, and serves it to the
  network, printing the addresses to open.
- To learn whether a line of C can be reached, copy `rebol3-source/` to the
  scratchpad, add a print, build with `scripts/build-r3.sh` and watch. Never
  instrument `rebol3-source/` itself.
- An assertion a real Rebol does not pass either goes in
  `fails-on-rebol-too.txt` with the `./r3-head` session that settles it. Never
  because it is hard.
- Every fix gets a JEBOL test in `src/functionalTest/java` (or `src/unitTest/java`
  where no interpreter is needed) that stands on its own, with
  expectations read off `./r3-head`.
- Commit to `main`, never branch, no attribution trailers.

---

## The goals

Ordered by importance. Any Rebol file running the same on a real 3.22.5 and
on JEBOL comes first (goal 1). The two that take JEBOL to Android and iOS
come next, with the defects that would hurt there fixed before each stage.
Agreeing with the C comes before improving on it: a place where JEBOL answers
something a real 3.22.5 does not is a defect.

### 1. Any Rebol file runs as it does on a real 3.22.5, and the loose ends

Twelve pieces of work, in the order to do them.

**1a to 1g come first: each makes the same script answer differently on a
real 3.22.5 and on JEBOL.** The aim is that any Rebol file runs the same on
either. Ordered by how much ordinary code they break: the modules a script
can ask for (1a, 1b), the console and
https (1c, 1d), then what a script sees when it fails (1e to 1g).

**1h to 1l change nothing a script run on both would see**: the spec, a
process-wide table, confinement and tasks, and then the five Rebol bugs
JEBOL fixes on purpose (1k), which make the two differ deliberately.

**And one Rebol file that does not run here is this project's own**:
`scripts/resolve-nest.r3`, the pre-make step `scripts/build-r3.sh` needs, so
JEBOL cannot bootstrap `./r3-head` and a release has to be fetched to get the
first one (the script says how). It stops at `foreach key listed [spec/:key:
copy []]` with `invalid-path`, naming `:key`. Measured on 2026-10-25: the same
write works on a fresh map, in a `foreach`, after `secure [file allow]`, and
with `scan-names` run over a small literal block -- it fails only once
`scan-names` has walked the real `rebol3-source/make/rebol3.nest`, which is
1,200 lines of `set-word!` and `to word!`. Shrink that input until the
smallest failing case is one line before reading any code.

#### 1a. The `bzip2` module, then SQLite and Postgres as extension jars

**JEBOL never fetches an extension** (decision 58). One it serves is ported to
Java and carried in the build as a delayed entry, which IMPORT makes into a
module the first time a script asks for it. Eight are served that way: easing,
blurhash, deflate, zlib-ng, blend2d, mathpresso, zstd and bzip2. Brotli is 1b and webp
is goal 10. What R3 names and JEBOL leaves out is decision 61.

**What each exports is read from its source, never from running it.** Each
repository's `src/*-rebol-extension.r3`, at the tag R3's address names, declares
the commands; read it as text. No release binary is run. **A probe is never
thrown away:** whatever is compiled or scripted to learn an answer is checked
in as a generator with the tables it writes, or becomes a test, and that test
is what stays.

Three pieces, in this order, each with its failing tests written first:

1. **`import 'bzip2`.** Done: `docs/bzip2-port.md`. Where the C never answers,
   `write/flush` above all, it says what JEBOL does instead.
2. **SQLite, as one extension jar with two setups.** The full plan is below,
   under "The SQLite plan". It was agreed on 2026-11-07.
3. **Postgres, next, on the same pattern.** The same kind of jar with only a
   `jvm` setup, over pgjdbc. Browsers cannot open a TCP socket, so a site build
   that names it is refused. R3 has no Postgres extension, so no C decides its
   answers: settle its commands in `spec/` first, shaped like SQLite's (`open`,
   `exec`, `eval`, `prepare`, `step /rows /with`, `columns`, `finalize`, `close`,
   `info`), with a `postgres://` scheme like SQLite's, so that a script reads the
   same against either. pgjdbc opens its own sockets, so its `SocketFactory` is
   JEBOL's and checks the NETWORK grant and SECURE before connecting. The tests
   start a Postgres installed from apt by `scripts/setup-machine.sh`, as they
   start Chrome; no Docker image.

**Done when** every `bzip2` command answers as the extension's C does, SQLite's
"Done when" below holds, and Postgres's does once its table is settled.

##### The SQLite plan

**What was decided, and why.** Agreed with the user on 2026-11-07, after
weighing the alternatives below.

- **One jar, `jebol-sqlite.jar`, with two setups: `jvm` and `web`.** It also
  carries a descriptor, its configuration, that says what each setup is and
  what it needs. The command line and the desktop app take the `jvm` setup. The
  site build takes the `web` setup. JEBOL's own jar keeps its zero dependencies
  and never names SQLite.
- **`jvm` runs sqlite4j** (`io.roastedroot:sqlite4j`, Apache-2.0): Xerial's
  sqlite-jdbc with SQLite compiled to WebAssembly, then translated to plain Java
  bytecode at build time by Endive. It is pure Java, with no native library to
  unpack. Xerial's own `sqlite-jdbc` was the first plan. It was dropped because
  it unpacks a native library and could never run on the web.
- **`web` runs sqlite.org's official WebAssembly build** (`sqlite3.wasm` and its
  JS API) in the worker, through TeaVM's JS interop, the way `BrowserStore` calls
  the page's store. Both files are carried inside the jar.
- **The commands are written once, against a `SqliteEngine` interface.** Each
  setup supplies its own engine. Rejected: one hand-written driver calling one
  `sqlite3.wasm` through Endive on the JVM and the browser's engine on the web
  ("stupidly complicated"). Also rejected: a pure-Java database TeaVM compiles,
  because none exists that we know of; H2 and HSQLDB rest on `java.sql`, threads
  and file channels.
- **Every file goes through the host's `FilePort`.** No setup reads a path on its
  own. Both engines hold the database in memory: on `open` its octets are read
  through the port, and after a change, and on `close`, they are written back
  through it. So SECURE, the grants and `what-dir` apply as they do to any file.
  `:memory:` never touches the port.
- **Extensions, unlike the reference, may use prebuilt libraries.** "No release
  binary is run" is about where the reference answers come from. The
  user, 2026-11-07: core is one jar, and an extension can do whatever it needs,
  since core does not rely on it.
- **A bad handle answers as the C does,** with `command-fail` carrying
  `"Invalid SQLite DB handle!"` or `"Invalid SQLite STMT handle!"`, not with
  `invalid-handle`.
- **`info` and `trace` are tested by their shape.** Their pointers, memory figures
  and nanoseconds differ from run to run, and sqlite4j cannot report SQLite's
  memory at all.
- **`sqlite-scheme.reb` is included,** run as the extension's own script.
- **The version.** Pin both engines to the newest SQLite that both sqlite4j
  (3.48.0.0 onwards on Maven Central) and sqlite.org's WebAssembly downloads
  have, and check that the pair really exists before pinning. The reference is
  3.46.0. The module itself keeps the header's `Version: 3.46.0.0`, and only
  `info`'s `SQLite-version` line reports the engine's version.
  `docs/sqlite-port.md` records every place the newer SQLite answers
  differently from the transcripts.

**The sources.** Rebol-SQLite at tag `3.46.0.0`, MIT licence:
https://github.com/Siskin-framework/Rebol-SQLite. These files matter:

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

**What each command does, read from the C.** The reference transcripts settle
the exact answers.

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

The scheme's actions are `open /new`, `open?`, `close`, `write` (exec), `insert`
(prepare, caching statements by their text), `take /part`, `read /part`, `pick`
(read what was just inserted) and `modify 'trace-level`. A missing file without
`/new` raises `access cannot-open`. The scheme starts with `sqlite: import
sqlite`, which cannot run inside the module it imports: change it to use the
module's own words, and record the change as a borrowed-file edit.

**What the core lacks today.** Found by reading it on 2026-11-07:

- **The command line adds no extension.** `Repl.grantEveryHostService` builds
  `Bounds.standard()` and nothing more. Check how the desktop app builds its
  bounds: it must get the same extensions.
- **There is no public way to keep commands in the module.**
  `ExtensionInstallation.install` puts every command into `lib`, so an `open` or a
  `close` is refused as `already-used`. `installDelayed` keeps them in the module
  but is package-private. It ignores `handleTypes` and `startWith`, and it reports
  nothing.
- **A command cannot reach the files.** `Command` offers no route to the host's
  `FilePort` that applies SECURE and resolves against `what-dir`.
- **A command cannot ask a handle's kind without raising.** `call.unwrap` raises
  `invalid-handle`, and the C answers its own `command-fail` string instead.
- **`java -jar` ignores `-cp`.** So the command line is run as `java -cp
  jebol.jar:jebol-sqlite.jar org.jebol.adapter.cli.JebolLauncher`, with the
  library jars beside `jebol-sqlite.jar`, named by its manifest's `Class-Path`.

**The jar.** It is built by the Gradle subproject `extensions/sqlite`:

- **The shared part:** the setup providers, the commands, the scheme, the handle
  kinds, `SqliteEngine`, and the file round-trip. It never names either engine.
- **`Sqlite4jEngine`.** It is reached only from the `jvm` provider. Finding how
  octets get in and out of sqlite4j's in-memory database is the first spike.
  Candidates: `SQLiteConnection`'s `serialize` and `deserialize`, or a path in
  its in-memory filesystem (ZeroFs).
- **`BrowserSqliteEngine`.** It is reached only from the `web` provider, and loads
  octets with `sqlite3_deserialize` and saves them with `sqlite3_js_db_export`.
  TeaVM's JS-interop types are needed when compiling the jar but are not shipped
  in it.
- **`sqlite3.wasm` and its JS,** about 1 MB, as resources. Fetched by Gradle,
  pinned by version and SHA-256.
- **The descriptor, `META-INF/jebol/extension.reb`,** written in REBOL:

  ```rebol
  name: sqlite
  version: 3.46.0.0
  setups: [
      jvm [
          provider: org.jebol.extension.sqlite.SqliteOnTheJvm
          needs:    [%sqlite4j-<v>.jar ...]
      ]
      web [
          provider: org.jebol.extension.sqlite.SqliteInTheBrowser
          assets:   [%sqlite3.wasm %sqlite3.js]
      ]
  ]
  ```

  A provider builds the `Extension`, so the core never knows about engines. The
  manifest's `Class-Path` is generated from `jvm`'s `needs`, so the two cannot
  drift apart. Check what sqlite4j itself needs at runtime: Xerial's driver needs
  `slf4j-api`, which warns on stderr when it finds no logging provider.

**The pattern, so that Postgres and PDF repeat it.** A `buildSrc` convention
plugin, `jebol-extension`, builds such a jar from a short declaration of the name,
the version, and each setup's provider, libraries and assets. From that one
declaration it writes the descriptor and the manifest. It also wires the
extension's tests into `allTests`. The core and the site build only ever read
the descriptor. PDF (goal 12) needs the same jar shape, plus its rule that its
commands answer none when the jar is absent. Revisit goal 12 when this lands.

**The order.** Each step's failing tests are written before its code.

0. **The reference.** `extensions/sqlite/src/test/resources/reference/generate.sh`
   fetches Rebol-SQLite at its tag. It compiles the extension's C with its
   `sqlite3.c` and its own `rebol-extension.h` into a `.rebx` with `gcc -shared
   -fPIC`, and runs `module-*.r3` scripts in `./r3-head` with the `.rebx` as their
   argument, as `bzip2/README.md` describes. The scripts and their `.txt` output are
   checked in. They cover every boundary in the list below.
1. **The core, in `src/functionalTest`:**
   1. Pin today's answer to `import 'sqlite` first. It is what a host without the
      jar keeps answering.
   2. **A public extension whose commands stay in its module.** Only the names it
      exports reach `lib`, the seal applies only to those names, handle types and
      `startWith` are honoured, and every refusal is reported in
      `interpreter.extensions()`. Then move the eight bundled extensions onto this
      route, so there is one route, and delete `installDelayed`. A new group in
      `ExtensionsFromTheSourceTest`.
   3. **Reading descriptors.** Read every `META-INF/jebol/extension.reb` with
      `ClassLoader.getResources`, take the `jvm` setup, and add its provider's
      extension to the command line's and the desktop app's `Bounds`. A jar with
      no `jvm` setup, or with a broken descriptor, is reported as refused and
      stops nothing else. A host embedding JEBOL is unchanged: adding an extension
      is the grant.
   4. **On `Command`:** read and write a file through the host's `FilePort`,
      resolved against `what-dir` and checked by SECURE, and ask a handle's kind
      without raising.
2. **The `jebol-extension` plugin and the `extensions/sqlite` subproject**, with an
   empty provider, proving the jar, its descriptor, its manifest and its tests in
   `allTests`.
3. **The `jvm` setup.** The spike on getting octets in and out first. Then the
   commands, then the scheme. `TheSqliteModuleFromTheSourceTest` checks the
   transcripts, and separate `*FromTheSourceTest`s check the same behaviour
   without reading any `.r3` file.
4. **The `web` setup.** `assembleSite -Pextensions=<jar>` reads the descriptor
   and puts the jar on TeaVM's classpath. It generates a class that registers the
   `web` provider, because TeaVM cannot look classes up by name, and adds it to
   `ShippedPageBounds`. It copies the assets beside `jebol-web.wasm`. A jar with no
   `web` setup fails the build, saying so.
5. **End to end.**
   - The shipped jar with and without `jebol-sqlite.jar` on `-cp`, checking each
     answer and that stderr is clean.
   - A site built with and without the extension, driven in Chrome, running the
     same scripts as step 3, so any difference between the two engines fails.
6. **The docs.**
   - Decision 62: an extension jar with setups and a descriptor.
   - `spec/embed.allium`.
   - A section in `writing-an-extension.md` on writing an extension in its own jar.
   - `docs/sqlite-port.md`: where JEBOL differs from the C, and how the file
     round-trip works.
   - `using-jebol.md`: how to run with the jar.
   - These goals.

**The boundaries.** Each is covered by the reference and by a JEBOL test:

- **SQL:** empty SQL; several statements in one `exec`; a syntax error; a
  constraint error (`ci-test`'s NOT NULL).
- **`/rows`:** 0, -1, 1, more than there are rows, and over 1000, where the C
  stops preallocating more.
- **`/with`:** fewer values than placeholders, more, and none at all; each
  bindable type; `binary!`, `block!` and an unset word.
- **Strings:** a string not at its head, in `step/with` and in `eval`; a wide
  string, both bound and read back; `eval` given a string query not at its head.
- **Values:** NULL, a zero-length blob, a large blob, and a 64-bit integer at
  each extreme.
- **Statements:** stepping again after DONE, for a read-only statement and for a
  write; `reset`, then stepping again; a finalized statement passed to `step`,
  `columns`, `eval` and `finalize`.
- **Databases:** a closed database passed to each command; one handle kind
  where the other is expected; a handle from another extension.
- **`eval`:** with nested blocks of rows, a short row, and a finalized
  statement inside its block.
- **`open`:** `:memory:`; a missing directory; whether `open` creates the file
  before any write; a path relative to `what-dir`; a path SECURE refuses.
- **The library:** `shutdown` twice, then `initialize`.
- **The scheme:** `open` without `/new` on a missing file; `read /part`, `take`,
  `pick`; `insert` of the same text twice, which reuses the cached statement; an
  action on a closed port (`not-open`).

**Done when** every command and scheme action answers as the transcripts do, with
each exception recorded in `docs/sqlite-port.md`, on both setups. Every
boundary above has a standalone JEBOL test. And the end-to-end tests run the
command line and a built site, each with and without the extension, and get each
answer.

#### 1b. The `brotli` module, and the streaming encoder it needs

`compress` and `decompress` with `'br` already run on JEBOL's Brotli and
write R3's bytes. `system/modules/brotli` is something else: a link to
Oldes/Rebol-Brotli 0.1.0, a native extension R3 downloads on `import brotli`.
Its source is at https://github.com/Oldes/Rebol-Brotli (`src/brotli-rebol-extension.r3`
declares the commands, `src/brotli-commands.c` implements them). A real
3.22.5 here cannot import it: the download meets the TLS inspector (Standing).

What `import brotli` gives a script, against what JEBOL's port has:

| Command                                                      | What it does                                                  | In the Java port            |
|--------------------------------------------------------------|---------------------------------------------------------------|-----------------------------|
| `version`                                                    | the Brotli library's version                                  | no; the vendored C is 1.2.0 |
| `compress` `/part` `/level`                                  | compress in one call                                          | yes, `Brotli.compress`      |
| `decompress` `/part` `/size`                                 | decompress in one call                                        | yes, `Brotli.decompress`    |
| `make-encoder` `/level`, `make-decoder`                      | a handle holding a stream's state                             | no                          |
| `write` `/flush` `/finish`, `read`                           | feed a stream in chunks and take its output                   | no                          |
| an encoder's `/mode` and `/size-hint`, a codec's `/finished` | tune the encoder; ask whether a stream ended                  | no                          |
| on import                                                    | appends `br` to `system/schemes/http/headers/Accept-Encoding` | trivial                     |

The port compresses and decompresses a whole input in one call. It has no
encoder that keeps its state between calls, never flushes a block partway
through, and takes no mode and no size hint. None of that is cosmetic: the
extension sets `BROTLI_PARAM_MODE` and `BROTLI_PARAM_SIZE_HINT`, and
`docs/brotli-port.md` records that the size hint changes which of the four
hashers runs.

Two stages, each specified, tested and built in that order:

1. **The module over the port as it is.** `version`, `compress`,
   `decompress`, and the `Accept-Encoding` edit, served by an extension in
   Java. `system/modules/brotli` then answers what an imported module
   answers.
2. **The streaming encoder and decoder.** A port, not wiring, done the way
   the one-shot port was:
    1. The reference probe first: the extension's `brotli-commands.c` compiled
       against `rebol3-source/src/core/brotli/`, driven by a script of
       `write`, `/flush`, `/finish` and `read` calls, writing each chunk.
       Check which Brotli the extension is built against (its `.nest` names
       upstream `@brotli`); if not 1.2.0, say what differs.
    2. A boundary list from the C: chunk sizes either side of the input
       block and the window, a flush on an empty stream, a flush twice, a
       `write` after `/finish`, each mode, a size hint under and over the
       real length, and every level.
    3. The streaming state in Java, byte-exact against the probe; the
       decoder fed in chunks split at every place a chunk can split.
    4. The handles and their getters and setters, with R3's errors for a
       wrong handle or a write after the end.

**Done when** every command in the table answers as the extension does in
a real 3.22.5, the stream's bytes match the probe at every boundary on the
list, and Brotli's own corpus round-trips through the streaming API in
chunks.


#### 1c. The console honours echo, and `call/console` runs without one

- **Echo.** `modify system/ports/output 'echo false` is accepted and changes
  nothing: what is typed is still shown, so a script asking for a password
  shows it here and not in R3. JEBOL also writes the setting into a field
  R3's port has not got (`system/ports/output/echo` answers false here and
  is `invalid-path` in R3). The console adapter holds the three modes --
  echo, line and error -- and honours them; the field goes. First say, in
  the spec, what each mode means for a host that is not a terminal.
- **`call/console` on a host with no console** runs the program as plain
  inheritance would. One rule in `embed.allium`.

**Done when** echo off hides what is typed at the REPL (an end-to-end test
through the command line), the port has no extra field, and `call/console`
runs under a host with no console.

#### 1d. TLS: an out-of-order chain, and a Unicode host name

R3 trusts every server, so a script reading a server like these works there
and fails here.

- **A chain sent out of order is put in order** before it is checked, as
  browsers do. A chain missing an intermediate is still refused: fetching
  it would be a network call per handshake.
- **A Unicode host name is converted to its ASCII form** (`java.net.IDN`)
  before it is matched against the certificate, so `https://bücher.de` can
  be read.

Both need servers the end-to-end suite spins up itself, like the expired and
wrongly named ones it already has.

**Done when** both are rules, and the command line reads a local server that
sends its chain backwards and one named in Unicode.

#### 1e. An error a native raises carries its `near` and `where`

For `try [compress "a" 'brotli]` R3 gives `near: [compress "a" quote brotli]`
and `where: [compress try ...]`; JEBOL gives none for both. Errors from
evaluated code, paths, PARSE and CAUSE-ERROR already agree with R3. A script
that stops on such an error prints a different `** Near:` and `** Where:`.

1. Sweep: raise an error from every native that can raise one, in both
   interpreters, and diff `near` and `where`. COMPRESS is the one found so
   far; how many others share it is not known.
2. Fix the path they share, not each native.

**Done when** the sweep shows no difference, and is a test.

#### 1f. An operator with nothing on its left raises what R3 raises

Measured on 2026-10-23:

| Source         | R3                                   | JEBOL                                                                   |
|----------------|--------------------------------------|-------------------------------------------------------------------------|
| `do [+ 1]`     | `no-op-arg`, arg1 `+`                | `no-op-arg`, arg1 the sentence "the operator + has nothing on its left" |
| `do [1 + + 3]` | `cannot-use`, arg1 `add:`, arg2 none | `missing-arg`, arg1 the same sentence                                   |

`AnOperatorWithNothingOnItsLeftFails` in `spec/eval.allium` names
`missing-arg` for the second case, which R3 does not raise there: the rule is
wrong, so it is fixed first.

1. Read the C for what the second case pushes and why it surfaces as
   `cannot-use` on `add:`; probe a few more positions (after a set-word,
   inside a paren, after a function's last argument).
2. Correct the rule, then the tests, then the code.

**Done when** every position probed answers R3's id and arguments.

#### 1g. An error's arguments are values, not sentences

Twice now JEBOL has put an English sentence where R3 puts a value: arg1
of the operator error (1f), and arg1 of `no-value`, which for `get first
[zork]` is a `word!` spelt `zork has no value` where R3's is `zork`. A script
that reads `e/arg1` gets prose.

1. Sweep: raise every error JEBOL can raise, read arg1, arg2 and arg3, and
   list every one that is a sentence. `scripts/error-parity.py` is the place
   to start.
2. For each, find what R3 puts there. Where R3 cannot raise it, the
   argument is the value the error is about, and the wording goes in the
   catalogue's message.

**Done when** no error argument is a sentence, proven by the sweep as a
test.

#### 1h. Clear the spec's open questions

All 63 were read against the code and `./r3-head` on 2026-10-23 and each has
an answer; 60 are left, the three about a port's actor having become rules
with the dispatch they describe. The work that follows from the answers is
1c, 1d, 1j, 1k and 1l; this sub-goal is the spec's side of it. Every edit
runs `allium check` clean.

1. **Already answered: the question becomes a rule, or goes.** Each of
   these now matches R3 or is built. Where the behaviour is not already a
   rule, write it as one, and point it at the test that covers it (write the
   test if none does):
   - `parse.allium`: THEN commits the alternative (`parse "ab" [["a" then
     "x" | "a" "b"]]` is false in both); a mark into another series is read
     as a rule, as R3 reads it.
   - `eval.allium`: a get-word refinement is checked whatever it holds
     (`append/:bogus` is `no-refine` with BOGUS false, in both).
   - `values.allium`: `#[none]` is `invalid-arg` in both; `struct!` matches
     R3 as a binary layout tool (typed fields, arrays, nesting, `rebval!`
     fields, `change s #{...}` to lay bytes over it, `to binary!`) -- that
     is all R3 does with one, so the question goes.
   - `load.allium`: `a%b`, `a#b`, `a$b` and `a\b` are `invalid` in both; an
     export never defined says nothing, in both; HELP, SOURCE, WHAT, ABOUT
     and DUMP-OBJ exist; the unbalanced-delimiter spellings match R3.
     Delete the paren-led path question outright: R3 reads
     `(make object! [q: 7])/q` as a paren and a refinement too.
   - `natives.allium`: the two error ids for giving a native a body match
     R3 on all three spellings; `read %*.r3` lists matches; REMOVE on a
     complemented bitset matches; PROTECT/HIDE hides; REPEAT takes a
     series and a decimal; the six natives waiting on datatypes exist; a
     handle holds a value and stays opaque;
     `CompressedBytesAreTheOnesARealRebolWrites` now holds for all nine
     methods (zlib, gzip and deflate write R3's bytes at levels 0, 1, 6 and
     9; LZ4 and LZAV were ported byte for byte), so the libdeflate question goes and the invariant loses its
     exception; a wait on a connection with no timeout answers none until
     connections are read on their own thread (a rule, not a question);
     `system/options/boot` serves children only, which is enough (owner's
     answer); a byte-exact CHECK over a vector is dropped.
   - `screen.allium`: VIEW waits until a deadline ends it, built; a draw
     block on a gob paints; a missing screen's metrics are zero, as R3's
     host answers; the window's size is settled by the sliders example
     matching its reference screenshot.
   - `draw.allium`: SPLINE is built; a block's defaults read the gob's
     size, as the C does.
   - `dialect.allium`: no dialect has a slot after a repeating slot (every
     `*` in `dial-draw.reb` is last); swapped slots of one type get no
     warning (owner's answer); a near-miss count stays debugging output
     (owner's answer).
   - `embed.allium`: the evaluator checks its bounds every `checkEvery`
     steps, 1,000 by default; a file feeding a program's input stays a path
     for the host, since no script can tell; a host sees nothing of a run
     until it ends (owner's answer).
   - `web.allium`: an empty folder chosen in a browser answers the empty
     folder; no browser can name it.
2. **Answered by the owner, with work to do: the question becomes a rule
   when its sub-goal starts**, not before, so the rule and its tests arrive
   together: console echo and `call/console` (1c), TLS chains and Unicode
   hosts (1d), home and values between interpreters (1j), the Rebol bugs
   (1k), `effect` in DRAW and GUI-METRIC/SET (1l). The `call/console`
   question is in `embed.allium` and `natives.allium` both; the rule goes
   in `embed.allium` and the copy in `natives.allium` is deleted.
3. **Not a question for JEBOL:** move the `open/new` question -- a real
   3.22.5 answers two different ways on two identical runs -- to
   `docs/rebol-findings.md`. `change/part` on an image with a count copies
   R3 (owner's answer): the question becomes a finding beside it.
4. **Questions about the spec itself**, decided while editing:
   - Write down how far PARSE backs up when an alternative fails, and
     whether a repeat is retried at a lower count -- read off the C and
     `./r3-head`.
   - `TranscodeRequest` holds the text twice (`source_text` and `source`);
     keep `source` and derive the text.
   - State the mold property once (whatever would not read back is written
     as a construct) rather than listing members, with the round-trip
     invariant enforcing it.
   - `mold_indent_width`, `longest_quoted_string` and
     `newlines_forcing_braces` become named constants, not configuration.
5. **Moved:** whether a running script can be paused and resumed goes to
   goal 6, where stepping needs it.
6. **Still open, rewritten in plain English:**
   - Should a host be able to cap a script's memory? (Later.)
   - Should JEBOL check whether a certificate was revoked, and if it cannot
     find out, allow or refuse? (Later.)
   - Does a partly transparent pixel decoded in the browser come out
     visibly wrong? Look at one first, then decide whether a second decoder
     is worth it.
   - A swipe moves an app back and forward on every platform: decided. How
     it arrives differs on each, and the desktop is the one undecided:
     - **Web, desktop and mobile browsers:** the browser's own swipe moves
       its history, and JEBOL's history is the browser's (decision 50,
       `spec/nav.allium`), so it works today. Prove it with an end-to-end
       test, and check a mobile browser's edge swipe takes the same path.
     - **Desktop window:** macOS sends a swipe to
       `com.apple.eawt.event.SwipeListener`, which JDK 25 still ships but
       does not export (it needs `Add-Exports` in the jar's manifest, and
       works on macOS only); the public alternative is reading a sideways
       scroll as back and forward, which works on every system and cannot
       tell a swipe from scrolling sideways. Try both on a real trackpad,
       then choose.
     - **Android and iOS (goals 2 and 3):** Android's system back gesture
       arrives through the app's back handler; iOS has no system back, so
       an edge swipe has to be recognised by the app itself. Each goes in
       its platform's goal.

**Done when** the spec holds only the four questions in step 6, each in
plain English, and every answered one is a rule or gone.

#### 1i. The table of word spellings belongs to an interpreter

`Context.ONE_NAME_PER_SPELLING` keeps one lower-cased copy of every spelling
ever read, in a static table every interpreter in the process shares and
nothing empties. A long-running process whose scripts make words in a loop
(`to word! join "k" i`) grows it without limit. It is per-interpreter state
held in a static, and it goes.

**Done when** the table lives with the interpreter and is gone when the
interpreter is, proven by a test that makes words in one interpreter and
finds none of them in the next.

#### 1j. A confined script's home, and values between interpreters

The command line confines a script to its folder as R3 does, and both report
the machine's home, so neither changes what a script run on both answers.

- **Home inside the confinement.** `system/options/home` reports the
  machine's home even when the script may only see one folder, so REBOL's
  own user storage cannot write there. Ask the filesystem port where home
  is, so every host gives an answer inside what it lets the script see.
- **Values cross between interpreters by deep copy.** Owner's decision. Say
  in `values.allium` what crosses (every datatype, or which not: ports,
  handles, functions bound to the sender's contexts), and that the receiver
  gets a copy it owns. A task's body is the first place it is used; goal 6's
  copied interpreter is the second.

**Done when** both are rules with tests, and a confined interpreter can make
REBOL's user file at home.

#### 1k. Fix five Rebol bugs, and copy one

Owner's decisions, 2026-10-23. Each fix is a place JEBOL answers something a
real 3.22.5 does not, so each gets a Standing entry saying what and why.

1. **`find` in a binary finds 255.** `find #{00FF} 255` is none in R3 and
   `#{FF}` here; JEBOL is already right. Write it in Standing, and turn the
   open question into a rule.
2. **`make image!` takes a block of tuples.** `make image! [2x1 [1.1.1
   2.2.2]]` is a malconstruct in R3, because the reader that consumes the
   block never steps past it. Read the C and decide what the block should
   mean (one tuple per pixel, row by row) before writing the rule.
3. **`image-diff/part` clips an area that runs past the edge.** The C
   subtracts the size twice; `image-diff/part i1 i2 0x0 1x3` on two two by
   two images answers 0% where the pixels differ. Rebol's own
   `image-test.r3` asserts that 0% (line 402), so the suite assertion must
   be recorded as a deliberate difference: decide how the suite harness
   records one -- it has `fails-on-rebol-too.txt` for the other direction
   and nothing for this -- before the fix lands.
4. **A cipher that would swallow a message raises.** The open question named
   one cipher; a probe on 2026-10-23 that writes 32 bytes without a header,
   then UPDATE and READ, answers none for all nine GCM ciphers and only 16
   bytes for chacha20-poly1305, in both. Find which of those are the bug
   (a GCM stream may simply need its tag read differently) and raise on
   those.
5. **Opening a port that is already open raises `already-open`**, as the
   crypt port does, instead of connecting again and leaking the first
   connection. MIDI is the one that leaks; the sweep of every built-in
   scheme on 2026-10-25 found three more places where a port's state should
   gate a verb and does not, each measured against `./r3-head`:
   - `open p` on an open file port is `already-open %held.txt` in R3 and
     answers the port here; `create p` on an open directory port the same.
   - `delete p` on an *open* file port is `no-delete %held.txt` in R3, and
     here it deletes the file.
   - `rename p %zz` refuses in both, as `no-rename`, but arg1 is the file in
     R3 and the whole port object here.
   These are the only differences left on any built-in scheme after the
   port-actor work:
   every one of the thirty-seven verbs otherwise answers what R3 answers,
   on each of file, dir, tcp, udp, dns, console, event, system, callback,
   checksum, crypt and midi.
6. **Copied, not fixed:** `change/part` on an image with a count changes
   nothing. The question becomes a finding in `docs/rebol-findings.md`.

**Done when** the five behave as above, each with a rule, tests, and a
Standing entry, and the suite records the `image-diff` difference.

#### 1l. DRAW refuses `effect`, and GUI-METRIC/SET moves the window

- **`effect`** is the one DRAW command (of the 34 in `dial-draw.reb`) JEBOL
  does not paint, and it is skipped without a word. It raises until it is
  built. Read the C for the id.
- **`gui-metric/set`** with `'restore`, `'minimize`, `'maximize` and
  `'activate` controls the window, on the desktop. Say what each does in a
  browser, where a page cannot minimise itself. This goes past the C, where
  neither host handles those words and each raises `command-fail`, which is
  what JEBOL answers until then.

**Done when** both are rules, with an end-to-end test that minimises,
restores and maximises a real window.

### 2. Android, with the interpreter running on Android's own Java

1. **Check JEBOL's Java against Android's.** JEBOL is text for Java 25.
   Find the language features and library calls Android's compiler and
   runtime do not have, and replace them or choose the oldest Android version
   that does.
2. **The app.** An Android project of its own, so the jar keeps no
   dependencies. One screen draws the domain's surface calls on Android's own
   canvas and turns touches and the on-screen keyboard into JEBOL's events.
   Pausing and resuming the app are handled. The system back gesture (and
   the back button) moves the app's history back, through Android's back
   handler, as a swipe does in a browser (1h, `spec/nav.allium`).
3. **Android's adapters.** Files in the app's own storage, real sockets, a
   console view. CALL is refused. And a task's body must run on the host's
   ports too: `ThreadTasks`
   (`src/main/java/org/jebol/adapter/host/ThreadTasks.java:32`) builds its
   child interpreter with `Interpreter.createWithOutput(...)`, which is
   `Interpreter.standardPorts()`, so a task body on Android would answer
   `system/platform` with `Linux` and reach nothing the host supplied, where
   `TheFateOfATasksBodyIsTheHostsToDecide` already says the child inherits
   the parent's output and pause. `ThreadTasks` wants the parent's
   `HostPorts`, which also settles what a task body may reach, since today it
   has no files at all and a file port carries the directory it is standing
   in, so the parent's own cannot simply be shared.
4. **Touch in the 2010 GUI.** A tap is a click; a drag scrolls; focusing a
   field brings up the keyboard; layouts scale with the screen's size and
   density. This is done once, in the GUI's REBOL --
   `src/main/resources/org/jebol/modules/r3-gui.reb`, with each edit listed
   in ORDER.txt -- and iOS and the web on a phone use it too.
5. **Android's own features**, one extension each (using-jebol.md, section
   4): the camera, location, notifications. Where a feature can come to the
   web or the desktop too -- the camera, location -- it comes there as well.
6. **A gate on an emulator.** The examples are driven on an Android emulator
   the way the browser gate drives Chrome: a gate of its own, never a skipped
   test.

### 3. iOS, with the interpreter compiled ahead of time by GraalVM

Not a web view: one would pause JEBOL whenever the app is in the background,
keep it out of widgets, the Watch and app extensions, and make every call to
iOS an asynchronous message with its data copied. Compiled to native code,
JEBOL runs under the same rules as any iOS app and calls iOS directly.

1. **A native image on the desktop first.** Build JEBOL with GraalVM Native
   Image on macOS and run the gate's examples. This proves ahead-of-time
   compiling works: the borrowed REBOL library files must be bundled as
   resources, and nothing may depend on code being loaded at run time.
2. **A minimal iOS app.** Gluon's tools for GraalVM on iOS: an app that
   prints `1 + 2` on a simulator, then on a phone. Signing and Xcode are part
   of this step.
3. **The app.** One view draws the domain's surface calls with iOS's own
   drawing; touches, the keyboard, and pausing and resuming are handled as
   on Android, using goal 2 step 4's work in the 2010 GUI. iOS has no system
   back gesture, so the app recognises a swipe from the left edge as back
   and from the right edge as forward, the way a browser on the phone
   does (1h, `spec/nav.allium`).
4. **iOS's adapters.** Files in the app's own sandbox and real sockets. CALL is
   refused: Apple does not allow an app to start another program.
5. **A way to call iOS itself.** JEBOL reaches iOS's own features -- the
   camera, location, notifications, background work -- through GraalVM's
   direct calls into native code, one extension per feature.
   Whether the same compiled JEBOL fits inside a widget or an extension,
   within the memory iOS allows them, is measured here.
6. **A gate on the simulator**, as on Android: the examples driven in the app,
   a gate of its own.

### 4. A real MIDI device through `javax.sound.midi`

The `midi` scheme is registered and its actor is `MidiSchemeActor`, behind
the `MidiDevices` host port; every interpreter starts with `NoMidiDevices`,
which lists nothing. What is left is an adapter behind `MidiDevices` over
`javax.sound.midi`, wired by the command line as the clipboard is. Input
arrives on another thread and reaches the port's `awake` through the
interpreter's postbox, as a `read` event; the C sends one for each message
that arrives. Opening a port that is already open raises `already-open`
rather than connecting twice (1k).

### 5. The type-major refactor: what is left

Each datatype's behaviour belongs in its own class, not in a switch elsewhere.
What still switches on the datatype, by `scripts/complexity.py`:

- **`Actions.of`** -- a switch over nine datatypes wrapping each value in a
  separate `*Actions` class (`BitsetActions`, `MapActions`, `BinaryActions`,
  `StringActions`, `BlockActions`, `ObjectActions`, `GobActions`,
  `ImageActions`, `VectorActions`). The behaviour moves onto the value classes
  and the switch goes.
- **The rest of the path seam.** `Evaluator.writeThroughTheLastSegment`
  (cc 46) and `Evaluator.selectWith` (cc 41) still chain through time, bitset,
  error, string, gob, struct, image, vector and block, and `BlockPath`,
  `GobPath`, `ImagePath`, `EventPath`, `StructPath` and `VectorPath` are statics
  taking the value as argument one. Each value should answer through
  `PathTarget`, as objects, maps, dates, pairs and tuples already do.
- **`Molder.renderOne`** (cc 47) -- per-datatype writing, which should become
  each value molding itself.

The `BrotliDictionaryMatches`, `LzmaEncoder` and `SymmetryPartitionSort`
methods are high on purpose: their shape is the C's.

**Tooling to fix on the way:** `PortingBacklogTest` reads R3's function list
from source files and JEBOL's from what is a function at run time, so
`init-schemes` -- which sets its own name to `'done` once run, in both
interpreters -- reads as missing.

### 6. Debugging and tracing

**First, make `trace` agree with R3.** For `x: 1 + 2` R3 prints the operator
(`+ : op! [value1 value2]`), its call (`--> +`) and its result
(`<-- + == 3`); JEBOL prints none of the three and numbers the lines
differently. `trace/back`, `trace/function` and `stack` with its refinements are
unchecked.

**Then stepping and breakpoints**, which R3 does not have. Three things to
decide first: what drives it (the Debug Adapter Protocol, text here since
the jar takes no dependencies); what a paused script does to the deadline and
grants it runs under; and whether a script may debug itself. The question
moved here from `spec/eval.allium`: evaluation state lives on the heap, so a
running evaluation could be paused, resumed, or moved to another thread --
say which of those JEBOL promises, and at what step a script can be stopped.

**Next, with tracing to see what a boot builds: copy a booted interpreter
rather than boot one per test.** The gate builds about 16,300 interpreters at
about 41ms each, and nearly all of that is evaluating the borrowed library to
define the same functions and objects every time. Caching the parsed library
(decision 39) and reading each file once only touch the parsing, which is
not where the time goes. Boot one interpreter per test process and hand each
test a deep copy of it. Measure the copy first and stop if it is not clearly
under about 15ms; if it is, every piece of interpreter state -- contexts,
functions, objects, and ports holding host resources -- must copy, proven by
isolation tests of the whole interpreter like `LibrarySourceIsolationTest`.
The gate read 5m21s to 5m22s on 2026-10-23, with one test process per core,
in three of four runs; the fourth read 4m46s and was not explained. Runs are
kept in `test-history/`.

### 7. Dev tools

Two homes: the flag and the example go in JEBOL; the server and its tools are a
second Gradle build in this repo: one executable jar taking JEBOL's jar as its
one dependency, with a gate of its own rather than `allTests`.

1. **`--manual` prints `using-jebol.md` to standard output.** The file is
   copied into the jar's resources at build time. The same pass gives `--help`
   and `-V` their missing output: both are parsed today and neither is read.
2. **One worked dialect with a test**, stating that DELECT matches by type in
   any order where PARSE matches by position, and written so a rule that does
   not match raises rather than answering false and being dropped. This is the
   example an AI author copies.
3. **The dev-tools project.** An executable jar of its own, hand-rolled
   JSON-RPC over stdio, one long-lived interpreter session, so an editor or an
   AI tool starts it once and keeps talking to it.
4. **`run`.** Evaluate a script under a scope the call declares - granted
   services, file root, limits, nothing granted by default - and report every
   service the script asked for, so the caller learns everything it wanted
   rather than where it first failed.
5. **Every dialect's schema, generated from a booted interpreter.**
   `system/dialects` holds draw, effect and text, and each is written out with
   a checked-in file of descriptions beside it. Written for the AI to read: the
   types say what fits, the descriptions say what it does. The generator is a
   test in this project in `SurfaceReportTest`'s shape.
6. **`match`.** Where a block stopped against a dialect's grammar, built on the
   schema.
7. **`word`.** What a word takes and whether it is reliable here, meaning it
   answers as the C does; the declaration parity and the runtime parity's three
   deliberate differences are where reliable comes from.
8. **Hot reload.** The dev server serves and watches a script's files, the
   checksum is the change detector, and a changed file is re-parsed into the
   live session. The same reload serves the session, the browser page and the
   desktop app, since all three run one interpreter; nothing survives a reload
   at first.


### 8. What the command line does differently from r3

1. **`--trace` does not trace the boot.** Do with goal 6, the debugger.
2. **The version and build differ.** `Rebol/core 3.22.5` here against
   `Rebol/Bulk 3.22.5 (...)` and its copyright lines, and `system/build` is none
   in every field. The banner's title line is decided -- it says "(JEBOL
   port)" -- and the rest of what JEBOL claims is still a decision.
3. **A `quit/return 42` typed at the console leaves with nought.** R3 leaves
   with 42, because its console carries what QUIT held out to the process. The
   console loop here returns without it.
4. **`input` in a script run by the command line refuses.** `Repl.portsFor`
   grants CONSOLE and hands no `ConsolePort` over, so a script's read of a
   line meets `ConsolePort.none()`'s "this script was given no console to
   read", where a real r3 reads the terminal. One adapter over the command
   line's own input stream is the fix, and it is the same shape of gap the
   windows service had until decision 48.

### 9. A drag still lags, and the interpreter has a next tier

The picture reaches the page 10 to 22ms after each pointer move, and a fast
drag settles at about 35 frames a second, which still reads as laggy. The
measurements are decision 36's. Three costs, in the order they bite: the
wait polls every ten milliseconds and that sets the floor; each paint walks
the whole surface and writes its JSON afresh -- 98KB for the two examples --
where only what has changed is ever looked at; and the page clears and
replays the whole canvas for each. The three actions for that, unchanged:
patch the paint list rather than rebuilding it, paint at most once a frame,
and drop a pointer move a later one has superseded.

The interpreter's first tier is done. Decision 38 took the streams off the
per-call path, kept the parameter sequence per call instead of rescanning,
made the argument list one copy instead of up to four, and interned
canonical names: about 2x on the timing workloads, 75ms to 54ms on a warm
boot, 5m16s to 4m19s on the test run. Decision 53 made a call's frame an
array: calls about 15% faster, recursion unchanged. The profile's next tier,
if the drag still reads slow after the paint work:

1. **`BlockStorage.at(int)`**, 6 to 8 per cent of some workloads: every value
   read from a block pays bounds arithmetic and an indirection.
2. **The wasm worker's cold start**, about 460ms, which is the JVM's first
   create by another route. Not the parse: decision 39's pre-parsed library
   image left it unchanged, because building the values costs what scanning
   them did. What is left is constructing and evaluating the library on every
   boot, which only a boot image in the strong sense would skip.

### 10. WebP as an image format

A real 3.22.5 has no WebP codec, so this goal improves on the C rather than
agreeing with it, and it comes after the agreement work for that reason. Every
browser reads and writes WebP, phones take pictures in it, and a script that
loads a picture from the web meets it often.

What it needs:

1. **The codec.** `codec-image.reb` is borrowed and stays as it is, so the
   `webp` codec -- suffix `.webp`, identified by `RIFF`, four bytes of length
   and `WEBP` -- is registered by a file of JEBOL's own, loaded after the
   borrowed one, decoding and encoding through `lib/image/load/as` and
   `lib/image/save/as` with `'WEBP` like the six beside it.
2. **The page.** Nothing beyond the page's `BrowserImages` (decision 49): `createImageBitmap`
   reads it and `OffscreenCanvas.convertToBlob` writes it.
3. **The desktop.** Java's own image library has no WebP, and the shipped jar
   takes no dependency, so the desktop needs a port: VP8L, the lossless form,
   and VP8, the lossy one, decoding first. It is byte-exact work and the rules
   in `CLAUDE.md` for a codec apply in full -- libwebp compiled into a probe
   before the port, the boundaries read from its C, and libwebp's own test
   images as the corpus. Whether the desktop also encodes, and in which form,
   is to be settled before the spec: the browser's encoder is lossy, and a
   lossless encoder alone is a much smaller port.
4. **Android and iOS**, when they land, use the system's decoder. Android's
   `BitmapFactory` reads it and `Bitmap.compress` writes it; iOS reads it from
   14 and has no encoder.

A host that cannot do one side refuses it the way a page refuses dds: a
catchable error naming the format and the host. The spec first, as everywhere:
the codec and each host's answer go in `spec/web.allium` and its desktop
counterpart before any test.

### 11. A url script's own files come from its url, and it cannot leave its root

A script given by url runs today in the directory the command was given in, so
`do %my-alt-vid.rb` inside `https://raw.github.com/myresources/main.rb` reads
a local `./my-alt-vid.rb` or nothing, and never the file beside the script.
That is the current contract and not an accident: `AScriptMayComeFromAUrl` in
`spec/repl.allium` says relative paths count from the command's directory, and
`do*`'s change-dir guard (`sys-base.reb:117`) fires only for a file.

What it should do instead: a relative path inside the script resolves against
the script's own url. `%my-alt-vid.rb` is
`https://raw.github.com/myresources/my-alt-vid.rb`, and every way a script
reaches another file takes it - `do`, `load`, `read`, `import` and a `needs:`
header naming a file.

**The root is fixed and may not be left.** The directory the first script was
fetched from is the root, and every fetch resolves under it: a relative path
may descend into the root, and one that climbs above it (`%../x.rb`) is
refused with a catchable error naming the url, the way a link leaving a host's
`--root` is refused (`ALinkLeavingTheRootIsRefused`, `spec/embed.allium`). A
script fetched under the root resolves its own relative paths against its own
url, still under the same root, so nothing a run fetches widens what it can
reach. An explicit url - `read https://other/x.r3` - is the script choosing,
as DO of a url is, and is left alone.

The spec changes first: `AScriptMayComeFromAUrl` gains the new answer, with
the root rule beside it.
`AScriptGivenByUrlAtTheCommandLineEndToEndTest` grows two cases through the
local TLS server it already builds: a relative include fetched from the
served tree, and a `%../` refused with nothing requested. `ORDER.txt`'s note
on `sys-start.reb` says a url has no directory to run in, and stops being true
the day this lands.

### 12. PDF as an optional extension

**The borrowed `codec-pdf.reb` stays as it is.** It reads a file into its
objects and writes them back, which is all a real 3.22.5 does, and `load` and
`save` answer the same with or without this goal's jars. What the codec cannot
do is say what a document contains: its text, or what a page looks like.

**It is an extension exactly as SQLite is (1a, piece 2), written to
`docs/writing-an-extension.md`.** A separate
jar, `jebol-pdf`, built here. Apache PDFBox is its implementation, and no script
sees that name. A script gets the commands below from `import 'pdf` by adding that
jar and its library jars to the classpath and nothing else: no flag, no
configuration. JEBOL's own jar keeps its zero dependencies and never names PDFBox.

**Without the jars the commands are still there and do nothing, unlike SQLite's.**
SQLite was never in the core language, so its import fails. PDF is a core function
that needs an extra library, so each command answers none and raises nothing. `pdf` in
`system/modules` is already the codec's module, so these commands join that
module and the codec stays reachable through it either way.

R3 has no such extension, so no C decides these answers and this goal does:

| Command                       | What it does                                        |
|-------------------------------|-----------------------------------------------------|
| `version`                     | the implementation's version                        |
| `open` `/password`            | a handle on a document, from a file or a binary     |
| `close`                       | releases the handle                                 |
| `page-count`                  | how many pages                                      |
| `read-text` `/pages`          | the document's text, or that of a range of pages    |
| `render-page` `/dpi`          | one page as an `image!`                             |
| `merge`                       | several documents as one binary                     |
| `extract-pages`               | a range of pages as one binary                      |

Settle this table in `spec/` before building: it is a proposal until then.

**What the tests compare against.** Rebol's own six fixtures, under
`src/tests/units/files/`, each checked by length and digest before it is trusted.
Text is compared with what each file is known to say, read independently of
PDFBox. A rendered page is compared by size and by chosen pixels, not byte for
byte, because PDFBox's output depends on the JVM's rasteriser and fonts. The
boundaries include an empty document, a damaged and a truncated file, a wrong and
a missing password, a page number either side of the range, and a closed handle.

**Done when** every command in the settled table has a standalone test, the
codec's answers are shown not to change, and an end-to-end test runs the command
line with and without the jars on the classpath and gets each answer.

### 13. The `miniaudio` module, as a port

`import 'miniaudio` answering as Oldes/Rebol-MiniAudio 0.11.23.0 does: its
devices, playback, fades and effect nodes, ported rather than bound to a native
library. Last on purpose. Sound cannot be compared byte for byte the way a codec
can, so working out what its tests compare against is the first step.

---

## Standing

- **`request-color`, `request-dir` and `request-file` answer `native!` where R3
  answers `function!`, on purpose.** R3 shells out to `osascript` on macOS
  only; JEBOL serves them through its own port. See `mezz/ORDER.txt` before
  changing it.
- **A TLS server must be trusted, where R3 trusts every one, on purpose.** An
  expired, self-signed, wrong-host or untrusted-root certificate reads in a
  real 3.22.5 and is refused here, and a TLS 1.2 elliptic curve server reads
  here and fails there. The vendored `prot-tls.reb` carries four edits for
  it. Decision 43 and finding 222 say what and why.
- **The command line trusts the machine's authorities, and a certificate with
  a GeneralizedTime validity reads here, on purpose.** The keychain, the
  Windows root store and the Linux bundle are added to the JDK's, and
  `--cafile` adds a bundle of its own; the verdicts above still hold. The
  borrowed DER/CRT codecs now decode GeneralizedTime, which X.509 permits, so
  a server behind a company's TLS inspector reads here and stops a real
  3.22.5 at "Missing public key in certifiate". The nine github assertions
  that came off `fails-on-rebol-too.txt` for that reason are this divergence.
  Decision 47.
- **A file is judged by where it really leads, where R3 judges the path as
  text, on purpose.** A symbolic link in a script's own folder pointing out
  of it reads in a real 3.22.5 and is a security violation here, naming where
  it leads; a link leaving a host's `--root` is refused even with every policy
  at allow. Links that stay inside are followed. `AFileIsJudgedByWhereItReallyLeads`
  in `spec/natives.allium` and `ALinkLeavingTheRootIsRefused` in
  `spec/embed.allium`.
- **An app has a history, and `system/catalog/event-types` has 49 types where
  R3 has 47, on purpose.** `back` and `forward` come after the C's last, so no
  index the C fixed moves; `go`, `go-back`, `go-forward`, `current-place` and
  `unsaved-work` are JEBOL's own words. Decision 50 and `spec/nav.allium`.
- **A task's body runs, where R3 never runs it, on purpose.** R3 makes a
  task and DO answers it, but nothing ever starts the body: an obvious bug.
  JEBOL runs it where a host supplies a way to: on a thread of its own in a
  fresh interpreter on the JVM, which the console and the command line wire.
  A browser runs none. `TheFateOfATasksBodyIsTheHostsToDecide` in
  `spec/natives.allium`, decision 30.
- **No Java PDF implementation in the interpreter jar.** The borrowed codec is
  what Rebol has; more than that is a library the caller adds, which is goal 12.
