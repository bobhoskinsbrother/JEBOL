# Goals

What is left to do on this port, in one order. A finished goal, or a finished
part of one, is deleted; what it said is in the git history.

The counts live here and nowhere else. `README.md`, `docs/porting-guide.md`
and `using-jebol.md` point here for them.

---

## Where the port stands

Measured on 2026-10-12, and the test count and timing rows on 2026-10-13.

| Measure | Reads |
| --- | --- |
| `fails-on-rebol-too.txt` | 169 a real 3.22.5 also fails or never runs |
| `scripts/c-parity.py` | 279 of 279 C functions match R3's surface |
| `scripts/runtime-parity.py` | 0 of 582 absent; 3 differ, all deliberate -- see Standing |
| `scripts/error-parity.py` | 120 of Rebol's 142 error ids can be raised |
| `PortingBacklogTest` | reports `init-schemes` missing, wrongly -- see goal 11 |
| `./gradlew check` | 27,487 tests |
| `corpus/` | 1,140 of 1,140 entries pass |
| the shipped jar | about 3,090 KB at its last build (the carried font, 556 KB, is most of what the last two batches added), and no dependencies |
| the web build | 13.7 MB of WebAssembly (4.1 MB gzipped) beside a 38 KB loader, a 5.9 KB worker and a 556 KB font; the whole site 14.3 MB, 4.4 MB gzipped |
| a warm `Interpreter.create()` | 44 to 57ms median across runs, 66ms p95, the first call 427ms; 16,300 of them per `./gradlew test` (`scripts/timing.py boot`) |
| the interpreter's speed | 8x to 14x slower than a real 3.22.5 on `scripts/timing.py speed`; about 2x more again in the browser |

---

## How to work on any of them

Read `CLAUDE.md` first.

- **`./r3-head`** is a real Rebol 3.22.5 and the authority. It takes a script
  file with a `Rebol []` header. Ask it rather than guessing.
- **`rebol3-source/`** is the C, searched through the IntelliJ MCP.
- **`scripts/sweep.py <file.r3>`** shows which suite assertions answer
  differently; **`org.jebol.suite.SuiteStops`** shows which raise. Fix a
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

Ordered by importance. The first two take JEBOL to Android, then to iOS, with
the defects that would hurt there fixed before each stage. Agreeing with the C
comes before improving on it: a place where JEBOL answers something a real
3.22.5 does not is a defect.

### 5. Android, with the interpreter running on Android's own Java

1. **Check JEBOL's Java against Android's.** JEBOL is written for Java 25.
   Find the language features and library calls Android's compiler and
   runtime do not have, and replace them or choose the oldest Android version
   that does.
2. **The app.** An Android project of its own, so the jar keeps no
   dependencies. One screen draws the domain's surface calls on Android's own
   canvas and turns touches and the on-screen keyboard into JEBOL's events.
   Pausing and resuming the app are handled.
3. **Android's adapters.** Files in the app's own storage, real sockets, a
   console view. CALL is refused.
4. **Touch in the 2010 GUI.** A tap is a click; a drag scrolls; focusing a
   field brings up the keyboard; layouts scale with the screen's size and
   density. This is done once, in the GUI's REBOL, and iOS and the web on a
   phone use it too.
5. **Android's own features**, one extension each (using-jebol.md, section
   4): the camera, location, notifications. Where a feature can come to the
   web or the desktop too -- the camera, location -- it comes there as well.
6. **A gate on an emulator.** The examples are driven on an Android emulator
   the way the browser gate drives Chrome: a gate of its own, never a skipped
   test.

### 6. iOS, with the interpreter compiled ahead of time by GraalVM

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
   on Android, using goal 5 step 4's work in the 2010 GUI.
4. **iOS's adapters.** Files in the app's own sandbox and real sockets. CALL is
   refused: Apple does not allow an app to start another program.
5. **A way to call iOS itself.** JEBOL reaches iOS's own features -- the
   camera, location, notifications, background work -- through GraalVM's
   direct calls into native code, one extension per feature.
   Whether the same compiled JEBOL fits inside a widget or an extension,
   within the memory iOS allows them, is measured here.
6. **A gate on the simulator**, as on Android: the examples driven in the app,
   a gate of its own.

### 7. Register the MIDI scheme

`open midi://` is `cannot-open` in R3 (no device on that build) and
`no-scheme` here. Its declaration is `sys-ports-midi.reb`, in `rebol3-source`
and not among the files this build loads. It works by `append/only
system/schemes [...]` while SCHEMES is still a block, so it has to load in the
window `INIT-SCHEMES` leaves: after `sys-ports.reb` defines `MAKE-SCHEME`,
before the first borrowed protocol file calls it (`prot-mysql.reb`), and
before `mezz-tail.reb` protects `system/schemes`. Making MIDI actually work
through `javax.sound.midi` is a further step and a separate goal.

### 9. MAKE and TO answers no assertion asks

- **`to paren! any-string!`** is a block in R3, because `Set_Block` writes
  `REB_BLOCK` whatever was asked for, and a paren here. A decision rather than
  a fix.
- **79 other answers differed at the sweep of 2026-10-08**, mostly wording:
  an error's `arg1` written as a sentence where R3 writes `_`; R3 molding `10%`
  as `10.000000000000001%`; `hash!` under MOLD/ALL; a pair, tuple or date given
  `"a b"` refusing as `bad-make-arg` where R3 says `invalid-chars`. Two are R3
  oddities (`to url! []` naming `email!`, `make percent!` errors naming
  `decimal!`).
- **`make utype! [[x] [x]]`** -- R3 builds something, and what it builds is
  an open question in `spec/natives.allium`.

### 10. Two found in Rebol's own declarations

1. **`system/standard/stats` starts at zero; R3 starts every field at none.**
   `STATS/PROFILE` and `DELTA-PROFILE` subtract the fields, so changing them is
   a behaviour change with its own spec rule and tests.
2. **The `??` parse keyword is missing.** `parse [a b] [?? skip skip]` prints
   `skip: [a b]` and answers true in R3, and raises `parse-rule` here. The C is
   three lines at `u-parse.c:1109`; the decision is how the parse walk gets to
   write a line. `TheDialectsWordsMatchRebolsOwnTest` will turn red when it
   lands, and its list of absences wants updating then.

### 11. The type-major refactor: what is left

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

### 12. Graphics: what is left

- **VID.**
- **The old markup path** -- 522 lines.
- **`effect`**, the one DRAW command not painted; it is a dialect of its own
  and belongs with the codecs.
- **The stroked-curve comparison** between the two adapters. The surface of
  decision 35 makes the walk one and leaves only the toolkit's coverage
  arithmetic, which the spec's tolerance already bounds.

### 13. Loose ends

- **A task's body runs nowhere by default.** DO of a task answers the task,
  and the body runs only where a host supplies a way to run it: on a thread of
  its own in a fresh interpreter on the JVM, which the console wires, and
  never in a browser. Decided with the web worker; the rule is
  `TheFateOfATasksBodyIsTheHostsToDecide` in `spec/natives.allium`.
- **Fourteen compiled modules are left out of `system/modules`** -- sqlite,
  zstd, webp and the rest -- because importing them fetches native code. Each
  can come back as an extension written in Java, one at a time, when one is
  wanted. The `serial` scheme is the same: an extension can provide it now.
- **`access-os` answers `not-here` for `uid`, `euid`, `gid` and `egid`.** The
  JVM has no portable way to ask.
- **An error's `near` and `where` differ from R3's in form.** R3 writes `near`
  as the whole block positioned at the failing value,
  `#(block! [o: make object! [self: 3] o/self] 2)`, where JEBOL writes the
  block from that value on; and R3's `where` names `catch` between a native
  and the TRY that caught it, which JEBOL's leaves out. Found by the goal 8
  sweeps on 2026-10-15; no suite assertion asks.
- **64 open questions across the spec files**, 27 of them in
  `natives.allium`. Unowned by any goal: the three in `parse.allium`, the five
  in `embed.allium`, and the design questions in `natives.allium` (whether a
  `struct!` is more than its layout, which verbs reach a port's actor, whether
  values may cross between interpreters).

### 14. Verify a fetched module

Nothing is fetched today: every module this build needs is bundled. Before an
address points outward again:

1. `system/modules` carries an expected digest beside each remote address.
2. The bytes are verified before they are written to disk and before anything
   is evaluated. `download-extension` currently reads and writes with no check.
3. A mismatch fails the import.
4. No digest recorded means the address is not usable.

Still to decide: whether a host can refuse fetching outright.

### 15. A debugger

**First, make `trace` agree with R3.** For `x: 1 + 2` R3 prints the operator
(`+ : op! [value1 value2]`), its call (`--> +`) and its result
(`<-- + == 3`); JEBOL prints none of the three and numbers the lines
differently. `trace/back`, `trace/function` and `stack` with its refinements are
unchecked.

**Then stepping and breakpoints**, which R3 does not have. Three things to
decide first: what drives it (the Debug Adapter Protocol, written here since
the jar takes no dependencies); what a paused script does to the deadline and
grants it runs under; and whether a script may debug itself.

### 16. Tools for a model writing REBOL

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

### 17. What the command line does differently from r3

1. **`--trace` does not trace the boot.** Do with goal 15.
2. **The version and build differ.** `Rebol/core 3.22.5` here against
   `Rebol/Bulk 3.22.5 (...)` and its copyright lines, and `system/build` is none
   in every field. The banner's title line is decided -- it says "(JEBOL
   port)" -- and the rest of what JEBOL claims is still a decision.
3. **A `quit/return 42` typed at the console leaves with nought.** R3 leaves
   with 42, because its console carries what QUIT held out to the process. The
   console loop here returns without it.

### 18. Take the network out of the gate

`thru-cache-test.r3` reads `raw.githubusercontent.com` and `httpbin.org`, so
`./gradlew check` depends on two outside services. Replace them -- a recorded
exchange, a local server, or `./gradlew networkCheck`, the gate that is
allowed a network and already holds the badssl.com checks -- so the gate
needs nothing but a JDK.

### 19. A drag still lags, and the interpreter has a next tier

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
boot, 5m16s to 4m19s on the test run. The profile's next tier, if the drag
still reads slow after the paint work:

1. **The per-call frame machinery.** Every function call makes a fresh
   `Context` and registers each parameter into its `LinkedHashMap`
   (`HashMap.putVal` is the top frame in the call workload), then tears it
   down. An array-backed call frame is the next measurable thing.
2. **`BlockStorage.at(int)`**, 7 to 8 per cent of some workloads: every value
   read from a block pays bounds arithmetic and an indirection.
3. **The wasm worker's cold start**, 459ms, which is the JVM's first create
   by another route; the per-boot parse of the borrowed library is its
   biggest single piece and the candidate for a build-time form of it.

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
- **A file is judged by where it really leads, where R3 judges the path as
  written, on purpose.** A symbolic link in a script's own folder pointing out
  of it reads in a real 3.22.5 and is a security violation here, naming where
  it leads; a link leaving a host's `--root` is refused even with every policy
  at allow. Links that stay inside are followed. `AFileIsJudgedByWhereItReallyLeads`
  in `spec/natives.allium` and `ALinkLeavingTheRootIsRefused` in
  `spec/embed.allium`.
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
- **`thru-cache-test.r3`** failed once under a full gate on 2026-10-12 -- three
  assertions "never reached" because the script died before them, and the
  whole suite passed alone straight after. The reads to
  `raw.githubusercontent.com` are goal 18's dependency on two outside
  services, and under a parallel gate one of them stalled past the run's
  deadline. Goal 18 is the fix; until then a full gate can fail here without
  anything having changed.
