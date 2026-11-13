# Goals

What is left to do on this port, in one order. A finished goal, or a finished
part of one, is deleted; what it said is in the git history.

Nothing else goes here. What has been decided is in `docs/decisions.md`, where
the port stands and where it differs on purpose is in `docs/standing.md`, and
how to work on any goal is in `docs/porting-guide.md`.

---

## The goals

Ordered by importance. Any Rebol file running the same on a real 3.22.5 and
on JEBOL comes first (goal 1). The two that take JEBOL to Android and iOS
come next, with the defects that would hurt there fixed before each stage.
Agreeing with the C comes before improving on it: a place where JEBOL answers
something a real 3.22.5 does not is a defect.

### 1. Any Rebol file runs as it does on a real 3.22.5, and the loose ends

Four pieces of work, in the order to do them.

**1i to 1l change nothing a script run on both would see**: a process-wide
table, confinement and tasks, and then the five Rebol bugs JEBOL fixes on
purpose (1k), which make the two differ deliberately.

**And a browser's swipe is proven to move the app's history.** The browser's
own swipe moves its history, and JEBOL's history is the browser's (decision
50, `spec/nav.allium`), so it should work today. Prove it with an end-to-end
test, and check a mobile browser's edge swipe takes the same path. How a
desktop window hears a swipe is the open question in `spec/nav.allium`.

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

#### 1k. Fix five Rebol bugs

Owner's decisions, 2026-10-23. Each fix is a place JEBOL answers something a
real 3.22.5 does not, so each gets an entry in `docs/standing.md` saying what
and why.

1. **`find` in a binary finds 255.** `find #{00FF} 255` is none in R3 and
   `#{FF}` here; JEBOL is already right. Write it in `docs/standing.md`, and
   turn the note beside it in `spec/natives.allium` into a rule.
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

**Done when** the five behave as above, each with a rule, tests, and an entry
in `docs/standing.md`, and the suite records the `image-diff` difference.

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
   handler, as a swipe does in a browser (`spec/nav.allium`).
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
   does (`spec/nav.allium`).
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

**Tooling to fix on the way:** `PortingBacklogTest` reads R3's function list
from source files and JEBOL's from what is a function at run time, so
`init-schemes` -- which sets its own name to `'done` once run, in both
interpreters -- reads as missing.

### 6. Debugging and tracing

**Before anything in JEBOL: a debugger on the reference.** Goal 1f's stack
drift (decision 66) was found from about forty black-box probes on
`./r3-head` and a reading of `Push_Func`. A watch on `DSP` across `Do_Next`'s
operator case would have shown it in one run. Have `scripts/build-r3.sh`
build a second `./r3-head-debug` with symbols and no optimisation, and check
in a gdb command file under `scripts/` that runs a one-line script and prints
`DSP`, `DSF` and the top cells at each `Do_Next`, `Push_Func` and `Do_Blk`
exit. A probe is never thrown away, so each question it answers is checked in
as a transcript beside the test that uses it.

**Then make `trace` agree with R3.** For `x: 1 + 2` R3 prints the operator
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
rather than boot one per test.** The gate builds about 16,300 interpreters,
and nearly all of each boot is evaluating the borrowed library to define the
same functions and objects every time. Caching the parsed library
(decision 39) and reading each file once only touch the parsing, which is
not where the time goes. Boot one interpreter per test process and hand each
test a deep copy of it. Measure the copy first and stop if it is not clearly
under about 15ms; if it is, every piece of interpreter state -- contexts,
functions, objects, and ports holding host resources -- must copy, proven by
isolation tests of the whole interpreter like `LibrarySourceIsolationTest`.
Compare the gate's time before and after against `test-history/`.

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
   line's own input stream is the fix.

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

If the drag still reads slow after the paint work, the interpreter's next
tier, from the profile:

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

**An extension jar as SQLite's is (decision 63, `docs/writing-an-extension.md` section 11)**, `jebol-pdf`, built here
over Apache PDFBox, which no script sees by name. JEBOL's own jar never names
PDFBox.

**Without the jar the commands are still there and answer none,** unlike
SQLite's (decision 63). `pdf` in `system/modules` is already the codec's module,
so these commands join that module and the codec stays reachable through it
either way.

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

### 13. Postgres as an extension jar

A real 3.22.5 has no Postgres, so like PDF this goal improves on the C rather
than agreeing with it. `jebol-postgres.jar` is built on the pattern `jebol-sqlite.jar` set,
with only a `jvm` setup, over pgjdbc. Decision 63 says why it has no `web` setup
and how its sockets are checked.

1. **Settle the commands in `spec/` first,** shaped like SQLite's (`open`,
   `exec`, `eval`, `prepare`, `step /rows /with`, `columns`, `finalize`,
   `close`, `info`), with a `postgres://` scheme like SQLite's, so that a script
   reads the same against either.
2. **A Postgres for the tests:** installed from apt by
   `scripts/setup-machine.sh` and started by the tests themselves, as Chrome
   is.

**Done when** every command in the settled table has a standalone test, a site
build that names it is refused, and an end-to-end test runs the command line
with and without the jar against a real Postgres.

### 14. The `miniaudio` module, as a port

`import 'miniaudio` answering as Oldes/Rebol-MiniAudio 0.11.23.0 does: its
devices, playback, fades and effect nodes, ported rather than bound to a native
library. Last on purpose. Sound cannot be compared byte for byte the way a codec
can, so working out what its tests compare against is the first step.

### 15. Find where JEBOL's answers differ from R3's by mutating real code

The sweeps so far are a grid per feature, answered by `./r3-head`:
`make-to-sweep.py` (every MAKE and TO), `near-and-where-sweep.py` (every
native refusing every argument), and `sweep.py` (each suite assertion as a
probe). They are boundary analysis feature by feature, and they work. But
they cannot find a difference that only appears when two features combine.
Goal 1f is the proof: `print + 1` raises `cannot-use` in R3 and
`missing-arg` in JEBOL, and no grid over `print` or over `+` would ever
write it. A one-token mutation of `print 1 + 1` does.

So this is differential testing: real Rebol code, mutated a token at a time,
run through both interpreters, with every disagreement made into a test.
The case that matters most is the one where R3 answers a value and JEBOL
raises, or the reverse. That is a difference in logic, not in reporting.

**The seeds are real code, not invented code:**

- the `--assert` expressions of Rebol's own suite (10,144 of them;
  `sweep.py` already turns each into a probe), read from
  `rebol3-source/src/tests/units/`;
- the `code` entries of `corpus/*.corpus`;
- the top-level expressions of the library JEBOL borrows,
  `rebol3-source/src/mezz/`, run only where they define nothing and have no
  side effects (the filter comes first, see below).

**The mutations work on values, not text.** Rebol code is a block, so a
seed is loaded, one value is changed, and the block is molded back. One
mutation per mutant, so that a difference has one cause. The operators:

- delete a value; duplicate a value (`1 + 1` becomes `1 + + 1`); swap two
  neighbours;
- replace a value with one of a fixed set chosen to sit on boundaries:
  `none`, `true`, `false`, `0`, `-1`, `1`, `""`, `[]`, `#{}`, `0.0`,
  `1x1`, `'a`, `#"a"`, `%f`, and the word of an operator;
- wrap a value in a paren, or in a block; unwrap a paren or block of one
  value;
- turn a word into its set-word, get-word or lit-word, and back;
- insert an operator word before or after a value.

**Each case's outcome is put on a ladder,** and a difference is ranked by
the highest rung where the two disagree:

1. **value against error.** One answers, the other raises. Always a defect
   unless the allowlist names it.
2. **different values.** Compared by `mold/all`, after the normalisation
   below.
3. **different error id.**
4. **same id, different arg1, arg2 or arg3.** The datatype is compared as
   well as the spelling, so `add:` is not `add`.
5. **same id and arguments, different `near`.**

**The allowlist is what keeps the output readable.** It is a checked-in
file under `src/testFixtures/resources/org/jebol/`. Each line pairs a
pattern (an error id, a native's name, or the shape of the source) with
the entry in `docs/standing.md` ("Where JEBOL differs from a real 3.22.5
on purpose") or the decision number it is the consequence of. The five
fixes JEBOL makes to Rebol on purpose (goal 1k) are the first entries. A
difference the allowlist names is counted, not reported. A line the
allowlist has that no case matches anymore is reported too, so the list
cannot go stale silently.

**Shrink every difference before reporting it.** Delete values from the
mutant, from the outside in and from both ends, as long as the two
interpreters still disagree on the same rung. Stop when one more deletion
would make them agree. What is reported is that smallest source, its seed
and the mutation applied to it, and both answers. CLAUDE.md asks for this
of a codec ("shrink the input until the smallest failing case is one
line"), and it is what makes a difference fixable rather than just
counted.

**A probe is never thrown away.** Every shrunken difference becomes a row in
a checked-in transcript, the same shape as `make-and-to-as-r3-answers.tsv`:
source, R3's answer, rung. Once it is fixed, the row moves into a
`*FromTheSourceTest`, standing on its own as CLAUDE.md requires, and the
transcript row stays as the regression. The generator script is checked in
under `scripts/` beside the other sweeps and follows their conventions:
find the repository and the JDK from where the script stands, run
`./r3-head` (never a release binary), and write the TSV that the test
reads.

**What has to be settled before the first run:**

- **Answers that change from run to run.** `now`, `random`, `checksum` of
  anything holding a time, `stats`, molded handles, ports and events, and
  anything that prints an address are either normalised (a date becomes
  `<date>`) or excluded by name. A list of excluded natives is checked in,
  and each entry gives its reason.
- **Side effects.** Each batch runs in a fresh, empty working directory
  under `build/`, with a timeout per case. Mutants that would reach `quit`,
  `halt`, `q`, `wait`, `call`, `browse`, `write`, `delete`, `rename`,
  `make-dir`, `secure`, or a network scheme are dropped before they run, by
  checking the mutant's words against that list. Network scheme means
  `read` or `open` of a url other than a `file:` one. Never test against an
  external site (see the memory on this).
- **R3 crashing.** A few cases segfault a real Rebol (`make event! [[x]
  [x]]` is one; `make-to-sweep.py` says so). The existing sweeps run one
  process per case, which is too slow for tens of thousands of mutants.
  Run cases in batches, each case under `try`, with one line of output per
  case. When a process dies, rerun what is left of the batch from the case
  after the last line written, and record the crash itself as a finding
  for `docs/rebol-findings.md`.
- **JEBOL's boot.** At about 54ms an interpreter, give each batch one
  interpreter, as `SweepRunner` does. A case runs inside its own
  `do`-of-a-copy, so a mutant that redefines a word cannot leak into the
  next. Redefining a word from the library (`print: ...`, `+: ...`) is a
  mutation in its own right, but it is excluded at first, because it
  poisons the rest of the batch on both sides.

**Then measure what the cases reach in the C.** Build a second
`./r3-head` with gcov (`--coverage` on the compile; add it as an option of
`scripts/build-r3.sh` rather than a copy of the script), run the seeds and
the mutants through it, and list the branches of
`rebol3-source/src/core/` that nothing reached. Those branches are the
boundaries that per-feature boundary analysis wants, read from the C
instead of guessed. CLAUDE.md says the same for compressors: the
boundaries that matter are where the implementation changes what it does.
Keep any mutant that reaches a branch nothing before it reached as a seed
for the next round. That is coverage-guided fuzzing, the simplest version
of it, and the uncovered list becomes the stopping rule: a round that adds
no new branch and no new difference ends the search for that seed set.

**The order:**

1. The value-against-error rung only. Suite seeds, one mutation each, the
   allowlist, the side-effect filter, batching. That is the cheapest
   stage, and it catches the worst class of difference.
2. Shrinking, and the transcript plus its test.
3. The other four rungs, rung by rung, triaged as they come in. Each new
   kind of difference is either fixed under the zero-bug rule or, if it is
   deliberate, given an allowlist line that names its decision.
4. The corpus and the library as seeds.
5. gcov on `r3-head`, the list of unreached branches, and coverage-guided
   seeding.

**Done when** a full round over every seed set has no unexplained
difference on any rung, every difference found is either fixed with its
test or named in the allowlist with its reason, the transcript is checked
in and read by a test in `allTests`, and the list of unreached C branches
is in `docs/standing.md` beside the other measures.
