# Goals

What is left to do on this port, in one order. A finished goal, or a finished
part of one, is deleted; what it said is in the git history.

The counts live here and nowhere else. `README.md`, `docs/porting-guide.md`
and `using-jebol.md` point here for them.

---

## Where the port stands

Measured on 2026-10-09.

| Measure | Reads |
| --- | --- |
| `known-gaps.txt` | empty |
| `fails-on-rebol-too.txt` | 169 a real 3.22.5 also fails or never runs |
| `scripts/c-parity.py` | 277 of 279 C functions match R3's surface; the 2 are in goal 3 |
| `scripts/runtime-parity.py` | 0 of 582 absent; 3 differ, all deliberate -- see Standing |
| `scripts/error-parity.py` | 114 of Rebol's 142 error ids can be raised |
| `PortingBacklogTest` | reports `init-schemes` missing, wrongly -- see goal 6 |
| `./gradlew check` | 26,823 tests |
| `corpus/` | 1,140 of 1,140 entries pass |
| the shipped jar | about 2,560 KB at its last build, and no dependencies |

---

## How to work on any of them

Read `CLAUDE.md` first.

- **`./r3-head`** is a real Rebol 3.22.5 and the authority. It takes a script
  file with a `Rebol []` header. Ask it rather than guessing.
- **`rebol3-source/`** is the C, searched through the IntelliJ MCP.
- **`scripts/sweep.py <file.r3>`** shows which suite assertions answer
  differently; **`org.jebol.suite.SuiteStops 1`** shows which raise. Fix a
  raise first: everything after it never runs.
- **`org.jebol.adapter.cli.Repl`** reads a script from standard input, one
  expression per line, so the same cases can be put to both interpreters and
  diffed.
- To learn whether a line of C can be reached, copy `rebol3-source/` to the
  scratchpad, add a print, build with `scripts/build-r3.sh` and watch. Never
  instrument `rebol3-source/` itself.
- An assertion a real Rebol does not pass either goes in
  `fails-on-rebol-too.txt` with the `./r3-head` session that settles it. Never
  because it is hard.
- Every fix gets a JEBOL test in `src/test/java` that stands on its own, with
  expectations read off `./r3-head`.
- Commit to `main`, never branch, no attribution trailers.

---

## The goals

Ordered by importance. Agreeing with the C comes before improving on it: a
place where JEBOL answers something a real 3.22.5 does not is a defect.

### 1. One way to plug in extra functions, and know what is plugged in

Today a host can add a Java function to JEBOL with
`Interpreter.defineFunction`. But JEBOL only gets the function, not what it
is. So nothing can list what has been added, allow one addition while
blocking another, or report that an addition failed to start.

The aim: something a library adds should look and behave exactly like
something JEBOL ships with. A script should not be able to tell the
difference.

1. **Move the shared helpers somewhere sensible.** `DefaultNative` holds
   `acceptsAllNumbers`, a helper only arithmetic needs. Because everything
   builds on `DefaultNative`, even a network scheme inherits it. Move it to a
   class only the arithmetic functions use.
2. **Stop an extension taking a built-in's name.** If a library tries to add
   a function called `add`, refuse it with the `already-used` error. Lock the
   built-in names once start-up finishes, and look in libraries only after
   the built-ins. (A script can already define its own `add` without
   touching the built-in one. That works and is tested.)
3. **Describe the other kinds of plug-in.** A function is not the only thing
   a library might add. A scheme (like `http`) needs a spec, a start-up step,
   a wake-up handler and an actor. A codec (like `png`) needs a name, the
   file suffixes it handles and three functions. Decide what each must
   provide before writing the first one.
4. **Decide how a library gets loaded.** Three choices: JEBOL finds it on the
   classpath by itself; the host hands it over; or JEBOL finds it but a
   script still needs permission before using it. The third is the default
   unless there is a reason against it.

What this makes possible:
- the `serial` scheme, and the `callback` scheme, which nothing can send to
  yet;
- six extension errors JEBOL can never raise today: `bad-extension`,
  `extension-init`, `no-extension`, `command-fail`, `bad-command` and
  `handle-exists`;
- fourteen compiled modules that are left out of `system/modules`, because
  loading them means fetching a native library.

### 2. Register the MIDI scheme

`open midi://` is `cannot-open` in R3 (no device on that build) and
`no-scheme` here. Its declaration is `sys-ports-midi.reb`, in `rebol3-source`
and not among the files this build loads. It works by `append/only
system/schemes [...]` while SCHEMES is still a block, so it has to load in the
window `INIT-SCHEMES` leaves: after `sys-ports.reb` defines `MAKE-SCHEME`,
before the first borrowed protocol file calls it (`prot-mysql.reb`), and
before `mezz-tail.reb` protects `system/schemes`. Making MIDI actually work
through `javax.sound.midi` is a further step and a separate goal.

### 3. Three divergences

1. **WORDS-OF cannot tell a field named SELF from an object's own SELF.**
   `c: use [x] [x: 1 context? 'x] append c 'self words-of c` is `[x self]` in R3
   and `[x]` here. JEBOL marks the pointer by the slot's name, so about twenty
   places filter on `canonical().equals("self")`; R3 marks it by position. The
   slot should say what it is.
2. **`to-degrees` and `to-radians` take a percent.** R3 declares both
   `[integer! decimal!]` and refuses `10%` with `expect-arg`.
3. **A host exception can escape where a script asks for memory.** MAKE turns
   an allocation the heap cannot serve into `no-memory`. Appending in a loop,
   reading a large file and joining strings do not, and an `OutOfMemoryError`
   there breaks `ErrorsAreValuesNotHostExceptions` in `eval.allium`. Needs a
   sweep of everywhere JEBOL allocates on a script's say-so.

### 4. MAKE and TO answers no assertion asks

- **`to paren! any-string!`** is a block in R3, because `Set_Block` writes
  `REB_BLOCK` whatever was asked for, and a paren here. A decision rather than
  a fix.
- **79 other answers differed at the sweep of 2026-10-08**, mostly wording:
  an error's `arg1` written as a sentence where R3 writes `_`; R3 molding `10%`
  as `10.000000000000001%`; `hash!` under MOLD/ALL; a pair, tuple or date given
  `"a b"` refusing as `bad-make-arg` where R3 says `invalid-chars`. Two are R3
  oddities (`to url! []` naming `email!`, `make percent!` errors naming
  `decimal!`).
- **`make utype! [[x] [x]]` and `make command! [[x] [x]]`** -- R3 builds
  something for the first and answers `bad-func-def` for the second. Both are
  open questions in `spec/natives.allium`.

### 5. Two found in Rebol's own declarations

1. **`system/standard/stats` starts at zero; R3 starts every field at none.**
   `STATS/PROFILE` and `DELTA-PROFILE` subtract the fields, so changing them is
   a behaviour change with its own spec rule and tests.
2. **The `??` parse keyword is missing.** `parse [a b] [?? skip skip]` prints
   `skip: [a b]` and answers true in R3, and raises `parse-rule` here. The C is
   three lines at `u-parse.c:1109`; the decision is how the parse walk gets to
   write a line. `TheDialectsWordsMatchRebolsOwnTest` will turn red when it
   lands, and its list of absences wants updating then.

### 6. The type-major refactor: what is left

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

### 7. Graphics: what is left

- **VID.**
- **The old markup path** -- 522 lines.
- **`effect`**, the one DRAW command not painted; it is a dialect of its own
  and belongs with the codecs.
- **The stroked-curve comparison** between the two renderers.
- **Events that name the wrong window.**
- **Android.**

### 8. Loose ends

- **A task is never run.** `make task!` builds one and DO answers it, but
  nothing runs the body on a thread of its own. The open question beside
  `DoOfATaskAnswersTheTask` in `spec/natives.allium` says what it would take.
- **The command-line console grants only WINDOWS**, so `read %README.md`
  answers `no-service` there. Decide whether that is the design.
- **`access-os` answers `not-here` for `uid`, `euid`, `gid` and `egid`.** The
  JVM has no portable way to ask.
- **65 open questions across the spec files**, 30 of them in
  `natives.allium`. Unowned by any goal: the three in `parse.allium`, the five
  in `embed.allium`, and the design questions in `natives.allium` (where
  SECURE's boundary sits, whether a `struct!` is more than its layout, which
  verbs reach a port's actor, whether values may cross between interpreters).

### 9. Check the TLS certificate

Every certificate a browser would refuse is accepted, by R3 and by JEBOL:
expired, self-signed, wrong host and untrusted root all read. Decided: a host
port, refusing by default, checked in the domain, hooked in through the `CRT`
codec rather than by editing `prot-tls.reb`.

1. A `CertificateAuthority` port: given a chain and a hostname, trusted or why
   not. One adapter over `CertPathValidator` and the JDK trust store; one that
   refuses everything for an ungranted host.
2. A grant beside the others, refused by `Bounds.standard()`.
3. The hostname check against the subject alternative names, wildcards
   included.
4. A failure ends the handshake rather than logging.
5. A test against badssl.com for each refusal, one good host, and the same put
   to `./r3-head`. These reach the network -- see goal 15.

Until then, `read https://` should be described as unauthenticated wherever it
matters.

### 10. Verify a fetched module

Nothing is fetched today: every module this build needs is bundled. Before an
address points outward again:

1. `system/modules` carries an expected digest beside each remote address.
2. The bytes are verified before they are written to disk and before anything
   is evaluated. `download-extension` currently reads and writes with no check.
3. A mismatch fails the import.
4. No digest recorded means the address is not usable.

Still to decide: whether a host can refuse fetching outright.

### 11. Boot time

About 50ms per warm `Interpreter.create()` when last measured. The evaluation
of the system object's declaration runs on every boot although the answer is
the same each time; caching the built object is the next move. To be done as a
measured investigation.

### 12. A debugger

**First, make `trace` agree with R3.** For `x: 1 + 2` R3 prints the operator
(`+ : op! [value1 value2]`), its call (`--> +`) and its result
(`<-- + == 3`); JEBOL prints none of the three and numbers the lines
differently. `trace/back`, `trace/function` and `stack` with its refinements are
unchecked.

**Then stepping and breakpoints**, which R3 does not have. Three things to
decide first: what drives it (the Debug Adapter Protocol, written here since
the jar takes no dependencies); what a paused script does to the deadline and
grants it runs under; and whether a script may debug itself.

### 13. Tools for a model writing REBOL

- **A dialect that fails loudly.** A rule that does not match answers false
  and the calling code drops it.
- **One worked dialect with a test**, stating that DELECT matches by type in
  any order and PARSE by position.
- **`--manual`**, printing `using-jebol.md` to standard output.
- **A dialect schema generated from a booted interpreter**, as a test in the
  shape of `SurfaceReportTest`, with descriptions in a file beside it.
- **An MCP server with three tools:** `run` (evaluate under a declared fixture,
  reporting services asked for), `match` (where a block stopped against a
  grammar) and `word` (what a word takes and whether it is reliable here).

### 14. What the command line does differently from r3

1. **The bare console does not run `start`**: its own banner, sandboxed
   bounds, no colours. Deciding it means deciding whether the plain console
   keeps its sandbox.
2. **SECURE is not enforced.** `+s script.r3` runs here where r3 stops with a
   security violation.
3. **`--trace` does not trace the boot.** Do with goal 12.
4. **The version and build differ.** `Rebol/core 3.22.5` here against
   `Rebol/Bulk 3.22.5 (...)` and its copyright lines, and `system/build` is none
   in every field. What JEBOL should claim is a decision.

### 15. Take the network out of the gate

`thru-cache-test.r3` reads `raw.githubusercontent.com` and `httpbin.org`, so
`./gradlew check` depends on two outside services. Replace them -- a recorded
exchange, a local server, or a second gate allowed a network -- so the gate
needs nothing but a JDK.

---

## Standing

- **`request-color`, `request-dir` and `request-file` answer `native!` where R3
  answers `function!`, on purpose.** R3 shells out to `osascript` on macOS
  only; JEBOL serves them through its own port. See `mezz/ORDER.txt` before
  changing it.
- **No Java PDF implementation.** The borrowed codec is what Rebol has; more
  than that is a library the caller adds.

---

## Open flakes

A flake is a fail. None of these is closed.

- **`SeriesMemoryFromTheSourceTest / Rebol's own assertion, run whole`** failed
  once under a full gate. It asks a process-wide counter
  (`SeriesMemory.bytesHeld()`, a static shared by every interpreter) to come
  back within 2,000 bytes while other tests allocate. Fixing it means a
  per-interpreter figure or running the class in a JVM of its own.
- **`./r3-head` fails 13 assertions of `checksum-test.r3` about one run in
  eight** -- a closed checksum port sometimes reports itself open. Nobody has
  read the C. Ask `./r3-head` three times when its answer surprises you.
- **`WebScreenServerFromTheSourceTest`** failed once with 404 where it wanted
  204, and has passed every time since. The assertion now carries the whole
  response for next time.
