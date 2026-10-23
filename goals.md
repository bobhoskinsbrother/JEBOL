# Goals

What is left to do on this port, in one order. A finished goal, or a finished
part of one, is deleted; what it said is in the git history.

The counts live here and nowhere else. `README.md`, `docs/porting-guide.md`
and `using-jebol.md` point here for them.

---

## Where the port stands

Measured on 2026-10-12, the test count row on 2026-10-22 and the timing rows on
2026-10-13.

| Measure | Reads |
| --- | --- |
| `fails-on-rebol-too.txt` | 160 a real 3.22.5 also fails or never runs |
| `scripts/c-parity.py` | 279 of 279 C functions match R3's surface |
| `scripts/runtime-parity.py` | 0 of 582 absent; 3 differ, all deliberate -- see Standing |
| `scripts/error-parity.py` | 120 of Rebol's 142 error ids can be raised |
| `PortingBacklogTest` | reports `init-schemes` missing, wrongly -- see goal 4 |
| `./gradlew allTests` | 29,137 tests on 2026-10-22: `unitTests` 2,976, `functionalTests` 24,161, `endToEndTests` 2,000 |
| `./gradlew networkTests` | 15 tests on 2026-10-20: the 12 suite assertions that need the network, all graded, and a github script at the command line |
| `corpus/` | 1,140 of 1,140 entries pass |
| the shipped jar | 3,291 KB on 2026-10-22, of which the 2010 GUI is 75 KB before compression, and no dependencies |
| the web build | 13.7 MB of WebAssembly (4.1 MB gzipped) beside a 38 KB loader, a 5.9 KB worker and a 556 KB font; the whole site 14.3 MB, 4.4 MB gzipped |
| a warm `Interpreter.create()` | 44 to 57ms median across runs (45.5ms on 2026-10-22, with the GUI carried and not loaded), 66 to 73ms p95, the first call 424 to 427ms; 16,300 of them per `./gradlew functionalTests` (`scripts/timing.py boot`) |
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
- Every fix gets a JEBOL test in `src/functionalTest/java` (or `src/unitTest/java`
  where no interpreter is needed) that stands on its own, with
  expectations read off `./r3-head`.
- Commit to `main`, never branch, no attribution trailers.

---

## The goals

Ordered by importance. The two that take JEBOL to Android and iOS come
first, with the defects that would hurt there fixed before each stage. Agreeing with the C
comes before improving on it: a place where JEBOL answers something a real
3.22.5 does not is a defect.

### 1. Android, with the interpreter running on Android's own Java

1. **Check JEBOL's Java against Android's.** JEBOL is text for Java 25.
   Find the language features and library calls Android's compiler and
   runtime do not have, and replace them or choose the oldest Android version
   that does.
2. **The app.** An Android project of its own, so the jar keeps no
   dependencies. One screen draws the domain's surface calls on Android's own
   canvas and turns touches and the on-screen keyboard into JEBOL's events.
   Pausing and resuming the app are handled.
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

### 2. iOS, with the interpreter compiled ahead of time by GraalVM

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
   on Android, using goal 1 step 4's work in the 2010 GUI.
4. **iOS's adapters.** Files in the app's own sandbox and real sockets. CALL is
   refused: Apple does not allow an app to start another program.
5. **A way to call iOS itself.** JEBOL reaches iOS's own features -- the
   camera, location, notifications, background work -- through GraalVM's
   direct calls into native code, one extension per feature.
   Whether the same compiled JEBOL fits inside a widget or an extension,
   within the memory iOS allows them, is measured here.
6. **A gate on the simulator**, as on Android: the examples driven in the app,
   a gate of its own.

### 3. A real MIDI device through `javax.sound.midi`

The `midi` scheme is registered and its actor is `MidiSchemeActor`, behind
the `MidiDevices` host port; every interpreter starts with `NoMidiDevices`,
which lists nothing. What is left is an adapter behind `MidiDevices` over
`javax.sound.midi`, wired by the command line as the clipboard is. Input
arrives on another thread and reaches the port's `awake` through the
interpreter's postbox, as a `read` event; the C sends one for each message
that arrives. Whether opening an open port should do what the C does is an
open question in `spec/natives.allium`.

### 4. The type-major refactor: what is left

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

### 5. Loose ends

- **A task's body runs nowhere by default.** DO of a task answers the task,
  and the body runs only where a host supplies a way to run it: on a thread of
  its own in a fresh interpreter on the JVM, which the console wires. The rule
  is `TheFateOfATasksBodyIsTheHostsToDecide` in `spec/natives.allium`; a
  browser runs none, decision 30.
- **Fourteen compiled modules are left out of `system/modules`** -- sqlite,
  zstd, webp and the rest -- because importing them fetches native code. Each
  can come back as an extension text in Java, one at a time, when one is
  wanted. The `serial` scheme is the same: an extension can provide it now.
- **`access-os` answers `not-here` for `uid`, `euid`, `gid` and `egid`.** The
  JVM has no portable way to ask.
- **An error's `near` and `where` differ from R3's in form.** R3 writes `near`
  as the whole block positioned at the failing value,
  `#(block! [o: make object! [self: 3] o/self] 2)`, where JEBOL writes the
  block from that value on; and R3's `where` names `catch` between a native
  and the TRY that caught it, which JEBOL's leaves out. Found by the sweeps on
  2026-10-15; no suite assertion asks.
- **67 open questions across the spec files** on 2026-10-22, 27 of them in
  `natives.allium`. Unowned by any goal: the three in `parse.allium`, the eight
  in `embed.allium`, the one in `nav.allium` (whether a window should hear the
  trackpad's swipe, which Java gives no public way to), and the design
  questions in `natives.allium` (whether a
  `struct!` is more than its layout, which verbs reach a port's actor, whether
  values may cross between interpreters).

### 6. A debugger

**First, make `trace` agree with R3.** For `x: 1 + 2` R3 prints the operator
(`+ : op! [value1 value2]`), its call (`--> +`) and its result
(`<-- + == 3`); JEBOL prints none of the three and numbers the lines
differently. `trace/back`, `trace/function` and `stack` with its refinements are
unchecked.

**Then stepping and breakpoints**, which R3 does not have. Three things to
decide first: what drives it (the Debug Adapter Protocol, text here since
the jar takes no dependencies); what a paused script does to the deadline and
grants it runs under; and whether a script may debug itself.

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
The gate is 5m22s on 2026-10-23, with one test process per core.

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
- **No Java PDF implementation.** The borrowed codec is what Rebol has; more
  than that is a library the caller adds.
