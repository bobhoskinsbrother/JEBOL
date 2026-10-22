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
| `PortingBacklogTest` | reports `init-schemes` missing, wrongly -- see goal 6 |
| `./gradlew allTests` | 28,850 tests on 2026-10-22: `unitTests` 2,798, `functionalTests` 24,088, `endToEndTests` 1,964 |
| `./gradlew networkTests` | 15 tests on 2026-10-20: the 12 suite assertions that need the network, all graded, and a github script at the command line |
| `corpus/` | 1,140 of 1,140 entries pass |
| the shipped jar | about 3,090 KB at its last build (the carried font, 556 KB, is most of what the last two batches added), and no dependencies |
| the web build | 13.7 MB of WebAssembly (4.1 MB gzipped) beside a 38 KB loader, a 5.9 KB worker and a 556 KB font; the whole site 14.3 MB, 4.4 MB gzipped |
| a warm `Interpreter.create()` | 44 to 57ms median across runs, 66ms p95, the first call 427ms; 16,300 of them per `./gradlew functionalTests` (`scripts/timing.py boot`) |
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

Ordered by importance. Navigation on the web comes first, and then the two
that take JEBOL to Android and iOS, with the
defects that would hurt there fixed before each stage. Agreeing with the C
comes before improving on it: a place where JEBOL answers something a real
3.22.5 does not is a defect.

### 1. Navigation: back, forward, and a history stack

An app has places, and the operator has a way back to the last one. On a page
those are the browser's own Back and Forward buttons and its history stack; in
a window there are no buttons, only the same stack. Today there is neither:
pressing Back leaves or kills the app, the address never changes, a reload
starts the app over, the worker never learns where the page is, and a frame's
title is read once when it opens. The same model is the groundwork for the
phone goals, where a link that opens a view is a deep link and Back is the
operator's own gesture too.

What an app needs is one model on every screen: moving to a place adds an
entry, Back and Forward arrive as events the app answers, the address names
the current state so a reload or a shared link lands in the same place, and
leaving with unsaved work can ask first.

1. **The port, on every screen.** `NavigationPort` (`org.jebol.domain.host`),
   beside `WindowPort`: push a state, replace the current one, go back, go
   forward, and answer the current state. Every screen supplies it -- a
   desktop window, the shipped page and `--screen=web` now, the phones later
   -- and a run with no screen refuses it, as decision 48 arranges the
   dialogs. Navigation is a service of its own in the grants, granted
   wherever there is a screen. There is one history per app, as a phone
   keeps, however many windows are open, and the title that follows it is
   the first window the app opened. The web adapter is the browser's own
   `pushState`, `replaceState` and `popstate`, with `document.title`
   following the state; the desktop adapter is a stack. On `--screen=web` the
   page's half rides whatever transport carries its events.

2. **The history's rules.** Going somewhere new after going back drops the
   forward entries, as a browser does. Moving to the place already current
   adds no entry, because it is not a move -- Android calls this single top;
   the same place with different data is a different state and is a move.
   Back at the first entry does nothing, and so does Forward at the last. A state is a word, string, number or
   logic, or a block of them, and blocks may hold blocks. None is not a state
   and is refused before anything is pushed; so is any other type.

3. **The limits are the most restrictive host's, on every host.** At most
   100 history changes in 30 seconds, which is Safari's. A state's size and
   its address's length are capped at the smallest any target allows --
   Firefox's state size, the shortest address a target browser keeps,
   Android's saved-state limit -- measured and written into the spec as
   numbers. Past any limit a move is a catchable error, never a silent drop.

4. **The address is the state, written as data.** A hash first, because it
   reads in any host with no server asked to serve a path it does not know;
   the port hides the shape, so paths can come later. Every state the stack
   accepts has an address, so a link can set an app up at any one place in
   it, and a reload or a shared link lands there.

5. **A link is untrusted input.** Anyone can write one. The address is read
   as data and never loaded or evaluated. A server never sees a hash, so
   there is no 404 from one; the two cases are the app's:
   - an address that does not read as a state -- malformed, the wrong type,
     too long -- starts the app at its start, and the first event says the
     address was refused;
   - an address that reads but names a place the app does not know is the
     app's to answer with its own "not found", which is the 404.

6. **A navigation event, an extension of the C.** Rebol's event types are a
   fixed list and none of them is navigation. Both the phones and the web
   need one, so JEBOL adds it: an event naming the move, back or forward,
   with the state now current. It arrives on `system/ports/event` with every
   other screen event, so one handler block answers clicks and Back alike. A
   fresh load is the app's first such event. It goes in Standing as a
   deliberate difference, with a decision entry.

7. **The keys and gestures browsers use for Back and Forward** move the
   history on every screen: Alt+Left and Alt+Right on Windows and Linux;
   Cmd+[, Cmd+], Cmd+Left and Cmd+Right on macOS; the mouse's back and
   forward buttons; the macOS trackpad swipe. They still reach the app as
   key presses too, on a page as in a window, so an app that reads them
   keeps working.

8. **Unsaved work is a flag the app sets and clears in advance.** Leaving
   cannot ask the interpreter and wait -- `beforeunload` is synchronous and
   the interpreter is in a worker, and Android's back gesture has the same
   rule -- so the page holds the flag. With it set, a reload, closing the
   tab and leaving the app ask first through `beforeunload`. A window's
   close already reaches the script: `DesktopScreen` posts it and does not
   close, and `view-funcs.reb`'s default handler is what calls `unview`. So
   on the desktop the flag is read by that handler's path, not by a second
   veto in the port. Back between two of the app's own places is not
   guarded; an app that wants to ask there asks with its own form.

9. **To do: move the page's close from `beforeunload` to `pagehide`.**
   Today `site/runner.html:333` posts `close` from `beforeunload`, so once a
   leave can be cancelled, choosing "Stay" would leave a live page whose app
   has closed every window. The defect only shows once the flag exists, so
   the move lands in the same change as the flag, with the end-to-end test
   that chooses "Stay" and finds the app still running.

10. **A page brought back from the browser's back-forward cache restarts the
    app**, from its address, as a fresh load does.

11. **A small layer for app code.** `go` moves -- `go 'survey`, `go/back`,
    `go/forward`, `go/replace` -- because an app should not have to hold the
    port. `back` and `forward` are series actions and cannot be the words;
    `go` is defined nowhere in Rebol's library, the GUI or the examples.

Tests are the browser gate in Chrome and Firefox, driving the real Back and
Forward: a deep link that lands, one that is malformed, one the app does not
know; "Stay" on a guarded reload keeping the app running; Back between two
places with the flag set not asking; a restore from the back-forward cache.
The history's rules, the limits and the desktop's keys are tested at the
port's boundary on the JVM, and a real window is checked by hand, as decision
48's chooser is.

First steps, in the repo's order. The spec before any code: a new
`spec/nav.allium` holds every rule above, `spec/embed.allium`'s shipped-page
grants and `spec/repl.allium`'s command-line grants gain navigation, and
`ThePageClosingClosesEveryWindowOnIt` in `spec/screen.allium` moves to
`pagehide`. Then the tests that fail without a port, on the JVM and in both
browsers. Then the port, its two adapters and the `go` layer, with a decision
entry and `docs/web-port.md` beside them.

### 2. Android, with the interpreter running on Android's own Java

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
   density. This is done once, in the GUI's REBOL, and iOS and the web on a
   phone use it too.
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
   on Android, using goal 2 step 4's work in the 2010 GUI.
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
that arrives. Whether opening an open port should do what the C does is an
open question in `spec/natives.allium`.

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
- **The stroke-curve comparison** between the two adapters. The surface of
  decision 35 makes the walk one and leaves only the toolkit's coverage
  arithmetic, which the spec's tolerance already bounds.

### 8. Loose ends

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
- **65 open questions across the spec files**, 28 of them in
  `natives.allium`. Unowned by any goal: the three in `parse.allium`, the five
  in `embed.allium`, and the design questions in `natives.allium` (whether a
  `struct!` is more than its layout, which verbs reach a port's actor, whether
  values may cross between interpreters).

### 9. Verify a fetched module

Nothing is fetched today: every module this build needs is bundled. Before an
address points outward again:

1. `system/modules` carries an expected digest beside each remote address.
2. The bytes are verified before they are text to disk and before anything
   is evaluated. `download-extension` currently reads and writes with no check.
3. A mismatch fails the import.
4. No digest recorded means the address is not usable.

Still to decide: whether a host can refuse fetching outright.

### 10. A debugger

**First, make `trace` agree with R3.** For `x: 1 + 2` R3 prints the operator
(`+ : op! [value1 value2]`), its call (`--> +`) and its result
(`<-- + == 3`); JEBOL prints none of the three and numbers the lines
differently. `trace/back`, `trace/function` and `stack` with its refinements are
unchecked.

**Then stepping and breakpoints**, which R3 does not have. Three things to
decide first: what drives it (the Debug Adapter Protocol, text here since
the jar takes no dependencies); what a paused script does to the deadline and
grants it runs under; and whether a script may debug itself.

### 11. Dev tools

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


### 12. What the command line does differently from r3

1. **`--trace` does not trace the boot.** Do with goal 10.
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

### 13. A drag still lags, and the interpreter has a next tier

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

### 14. WebP as an image format

A real 3.22.5 has no WebP codec, so this is the one goal that improves on the
C rather than agreeing with it, and it comes last for that reason. Every
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
