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
| `scripts/c-parity.py` | 277 of 279 C functions match R3's surface; the 2 are in goal 11 |
| `scripts/runtime-parity.py` | 0 of 582 absent; 3 differ, all deliberate -- see Standing |
| `scripts/error-parity.py` | 114 of Rebol's 142 error ids can be raised |
| `PortingBacklogTest` | reports `init-schemes` missing, wrongly -- see goal 14 |
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

Ordered by importance. The first nine take JEBOL to the web, then to Android,
then to iOS, with the defects that would hurt there fixed before each stage.
Agreeing with the C comes before improving on it: a place where JEBOL answers
something a real 3.22.5 does not is a defect.

### 2. The web, with the interpreter compiled to WebAssembly

The first of three goals -- this one, 8 and 9 -- that give JEBOL one front end
everywhere. Today a page is drawn by JavaScript that repeats the desktop's
layout, and every event crosses to a JVM that runs the logic. The aim is one
interpreter and one drawing engine on every platform: the interpreter runs
where the screen is, and each platform only copies pixels to the screen, sends
back taps and keys, and supplies a font. That is how R3 itself was ported: AGG
drew into a pixel buffer everywhere.

The domain and application layers already use no networking, no AWT and no
reflection. The only blocking code is WAIT's sleep loop and the event queue's
locks. Files, the network and processes sit behind ports, so each platform
needs new adapters rather than a changed core.

1. **A one-day trial.** Compile the domain and application with TeaVM, or
   GraalVM Web Image if TeaVM fails. Run `print 1 + 2` in a page, then the
   Brotli tests. Write down what the compiler refused, how big the download
   is and how long a boot takes. This decides whether the rest goes ahead.
2. **Make the core compile, and keep it compiling.** Replace whatever the
   compiler refuses; WAIT's sleep becomes a port. `ScreenEventQueue` is
   synchronised and sits in the domain; the hand-off between threads moves
   out to the adapters, as `BrowserScreen` already does for a page's events,
   and the domain keeps a plain queue. Compiling the core for the web becomes
   part of the gate, so a change that breaks it fails the build.
3. **Run the interpreter in a worker.** A page's main thread cannot block, and
   WAIT does. The interpreter runs in a Web Worker and draws to a canvas it
   owns. Events reach it through shared memory, which needs the page served
   with cross-origin isolation, or through WebAssembly's pause-and-resume.
   At first it draws with the existing JavaScript renderer, so the first
   running page comes before the drawing engine. Whether a task ever runs on
   a thread of its own (goal 16) is decided here, with the worker. The page
   resizing is not yet an event: `BrowserScreen.theBrowserMeasures` writes the
   root gob's size from the server's thread while the script may be reading
   it. It becomes something the interpreter takes on its own thread, like a
   click, and the spec says when a script sees the new size.
4. **The browser's adapters, and what it refuses.** The console becomes an
   element on the page. Files live in the browser's private storage for the
   site, or in memory. HTTP and HTTPS go through `fetch`. TCP, UDP, DNS and
   CALL are refused with an error that says the browser does not allow them.
   The spec says which services a browser grants before any of it is built.
5. **The drawing engine.** The paint list is drawn into a grid of pixels in
   the domain: fills, strokes, curves, smooth edges, opacity, gradients,
   images and clipping, then text, with a font reader and one bundled font.
   The desktop and the page both copy the grid to the screen. Java2D's
   renderer and the JavaScript renderer are deleted, and the browser gate
   expects identical pixels with no tolerance.
6. **The examples with no server.** The sliders and the survey run wholly in
   a page served as static files, and the browser gate drives them there.
7. **A build that ships it.** A task produces the static files; their size goes
   in the table at the top of this file beside the jar's.

### 3. Boot time

About 50ms per warm `Interpreter.create()` when last measured. The evaluation
of the system object's declaration runs on every boot although the answer is
the same each time; caching the built object is the next move. To be done as a
measured investigation. Compiled to WebAssembly or running on a phone, boot
may be several times slower, so goal 3's trial measures it there too.

### 4. Check the TLS certificate

Every certificate a browser would refuse is accepted, by R3 and by JEBOL:
expired, self-signed, wrong host and untrusted root all read. The web build
does not reach this -- `fetch` checks certificates itself -- but an Android or
iOS app uses JEBOL's own TLS, so until this is done any app's traffic can be
intercepted. Decided: a host port, refusing by default, checked in the domain,
hooked in through the `CRT` codec rather than by editing `prot-tls.reb`.

1. A `CertificateAuthority` port: given a chain and a hostname, trusted or why
   not. One adapter over `CertPathValidator` and the JDK trust store; one that
   refuses everything for an ungranted host.
2. A grant beside the others, refused by `Bounds.standard()`.
3. The hostname check against the subject alternative names, wildcards
   included.
4. A failure ends the handshake rather than logging.
5. A test against badssl.com for each refusal, one good host, and the same put
   to `./r3-head`. These reach the network -- see goal 21.

Until then, `read https://` should be described as unauthenticated wherever it
matters.

### 5. Running out of memory escapes as a host exception

MAKE turns an allocation the heap cannot serve into `no-memory`. Appending in a
loop, reading a large file and joining strings do not, and an
`OutOfMemoryError` there breaks `ErrorsAreValuesNotHostExceptions` in
`eval.allium`. On a phone, with far less memory, that ends the app. Needs a
sweep of everywhere JEBOL allocates on a script's say-so.

### 6. What a script may touch

Apps will run scripts with the phone's files and features in reach, so how
much a script is allowed is settled before goal 8.

1. **A script reads and writes outside its own folder.** `read %../outside.txt`,
   `read %/etc/hosts` and `write %../written.txt "x"` all work here; R3 refuses
   each with `Access security`, naming the folder or file. Checked against
   `./r3-head` on 2026-10-10.
2. **SECURE is not enforced.** `+s script.r3` runs here where r3 stops with a
   security violation, and SECURE's settings are not consulted. Where SECURE's
   boundary sits is an open question in `natives.allium`, and it sits beside
   JEBOL's own grants in `Bounds`: decide how the two combine first.

### 7. One way to plug in extra functions, and know what is plugged in

Today a host can add a Java function to JEBOL with
`Interpreter.defineFunction`. But JEBOL only gets the function, not what it
is. So nothing can list what has been added, allow one addition while
blocking another, or report that an addition failed to start. Goals 8 and 9
reach the phones' own features -- the camera, location, notifications -- one
port per feature, and those are plug-ins of exactly this kind, so this comes
first.

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

### 8. Android, with the interpreter running on Android's own Java

1. **Check JEBOL's Java against Android's.** JEBOL is written for Java 25.
   Find the language features and library calls Android's compiler and
   runtime do not have, and replace them or choose the oldest Android version
   that does.
2. **The app.** An Android project of its own, so the jar keeps no
   dependencies. One screen copies the pixel grid from goal 3 step 5 to the
   display and turns touches and the on-screen keyboard into JEBOL's events.
   Pausing and resuming the app are handled.
3. **Android's adapters.** Files in the app's own storage, real sockets, a
   console view. CALL is refused.
4. **Touch in the 2010 GUI.** A tap is a click; a drag scrolls; focusing a
   field brings up the keyboard; layouts scale with the screen's size and
   density. This is done once, in the GUI's REBOL, and iOS and the web on a
   phone use it too.
5. **Android's own features**, one plug-in each in goal 7's shape.
6. **A gate on an emulator.** The examples are driven on an Android emulator
   the way the browser gate drives Chrome: a gate of its own, never a skipped
   test.

### 9. iOS, with the interpreter compiled ahead of time by GraalVM

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
3. **The app.** One view copies the pixel grid from goal 3 step 5 to the
   screen; touches, the keyboard, and pausing and resuming are handled as on
   Android, using goal 8 step 4's work in the 2010 GUI.
4. **iOS's adapters.** Files in the app's own sandbox and real sockets. CALL is
   refused: Apple does not allow an app to start another program.
5. **A way to call iOS itself.** JEBOL reaches iOS's own features -- the
   camera, location, notifications, background work -- through GraalVM's
   direct calls into native code, one plug-in per feature in goal 7's shape.
   Whether the same compiled JEBOL fits inside a widget or an extension,
   within the memory iOS allows them, is measured here.
6. **A gate on the simulator**, as on Android: the examples driven in the app,
   a gate of its own.

### 10. Register the MIDI scheme

`open midi://` is `cannot-open` in R3 (no device on that build) and
`no-scheme` here. Its declaration is `sys-ports-midi.reb`, in `rebol3-source`
and not among the files this build loads. It works by `append/only
system/schemes [...]` while SCHEMES is still a block, so it has to load in the
window `INIT-SCHEMES` leaves: after `sys-ports.reb` defines `MAKE-SCHEME`,
before the first borrowed protocol file calls it (`prot-mysql.reb`), and
before `mezz-tail.reb` protects `system/schemes`. Making MIDI actually work
through `javax.sound.midi` is a further step and a separate goal.

### 11. Two divergences

1. **WORDS-OF cannot tell a field named SELF from an object's own SELF.**
   `c: use [x] [x: 1 context? 'x] append c 'self words-of c` is `[x self]` in R3
   and `[x]` here. JEBOL marks the pointer by the slot's name, so about twenty
   places filter on `canonical().equals("self")`; R3 marks it by position. The
   slot should say what it is.
2. **`to-degrees` and `to-radians` take a percent.** R3 declares both
   `[integer! decimal!]` and refuses `10%` with `expect-arg`.

### 12. MAKE and TO answers no assertion asks

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

### 13. Two found in Rebol's own declarations

1. **`system/standard/stats` starts at zero; R3 starts every field at none.**
   `STATS/PROFILE` and `DELTA-PROFILE` subtract the fields, so changing them is
   a behaviour change with its own spec rule and tests.
2. **The `??` parse keyword is missing.** `parse [a b] [?? skip skip]` prints
   `skip: [a b]` and answers true in R3, and raises `parse-rule` here. The C is
   three lines at `u-parse.c:1109`; the decision is how the parse walk gets to
   write a line. `TheDialectsWordsMatchRebolsOwnTest` will turn red when it
   lands, and its list of absences wants updating then.

### 14. The type-major refactor: what is left

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

### 15. Graphics: what is left

- **VID.**
- **The old markup path** -- 522 lines.
- **`effect`**, the one DRAW command not painted; it is a dialect of its own
  and belongs with the codecs.
- **The stroked-curve comparison** between the two renderers. Goal 3 step 5
  replaces both renderers with one engine, after which there is nothing to
  compare.

### 16. Loose ends

- **A task is never run.** `make task!` builds one and DO answers it, but
  nothing runs the body on a thread of its own. The open question beside
  `DoOfATaskAnswersTheTask` in `spec/natives.allium` says what it would take.
  Decided with goal 3 step 3.
- **The command-line console grants only WINDOWS**, so `read %README.md`
  answers `no-service` there. Decide whether that is the design.
- **`access-os` answers `not-here` for `uid`, `euid`, `gid` and `egid`.** The
  JVM has no portable way to ask.
- **65 open questions across the spec files**, 30 of them in
  `natives.allium`. Unowned by any goal: the three in `parse.allium`, the five
  in `embed.allium`, and the design questions in `natives.allium` (whether a
  `struct!` is more than its layout, which verbs reach a port's actor, whether
  values may cross between interpreters). Where SECURE's boundary sits belongs
  to goal 6.

### 17. Verify a fetched module

Nothing is fetched today: every module this build needs is bundled. Before an
address points outward again:

1. `system/modules` carries an expected digest beside each remote address.
2. The bytes are verified before they are written to disk and before anything
   is evaluated. `download-extension` currently reads and writes with no check.
3. A mismatch fails the import.
4. No digest recorded means the address is not usable.

Still to decide: whether a host can refuse fetching outright.

### 18. A debugger

**First, make `trace` agree with R3.** For `x: 1 + 2` R3 prints the operator
(`+ : op! [value1 value2]`), its call (`--> +`) and its result
(`<-- + == 3`); JEBOL prints none of the three and numbers the lines
differently. `trace/back`, `trace/function` and `stack` with its refinements are
unchecked.

**Then stepping and breakpoints**, which R3 does not have. Three things to
decide first: what drives it (the Debug Adapter Protocol, written here since
the jar takes no dependencies); what a paused script does to the deadline and
grants it runs under; and whether a script may debug itself.

### 19. Tools for a model writing REBOL

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

### 20. What the command line does differently from r3

1. **The bare console does not run `start`**: its own banner, sandboxed
   bounds, no colours. Deciding it means deciding whether the plain console
   keeps its sandbox.
2. **`--trace` does not trace the boot.** Do with goal 18.
3. **The version and build differ.** `Rebol/core 3.22.5` here against
   `Rebol/Bulk 3.22.5 (...)` and its copyright lines, and `system/build` is none
   in every field. What JEBOL should claim is a decision.

SECURE not being enforced is part of goal 6.

### 21. Take the network out of the gate

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
