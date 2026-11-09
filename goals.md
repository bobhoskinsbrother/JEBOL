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

Eight pieces of work, in the order to do them.

**1e to 1g come first: each makes the same script answer differently on a
real 3.22.5 and on JEBOL.** The aim is that any Rebol file runs the same on
either. They are all what a script sees when it fails.

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

#### 1e. An error carries its `near` and `where` however its call was finished

A script that stops on one of these prints a different `** Near:` and
`** Where:` from R3's, and one that reads `e/near` or `e/where` gets none. It
was found as SMTP's `if parse response [...] log-trace ...` (finding 238).

**Measured on 2026-11-09**, `./r3-head` and the jar at 9b7eacdd, from a file
and at the console; `f` is `func [x [block!]] [x]`:

| Source                                  | R3 `near` / `where`                           | JEBOL now                |
|-----------------------------------------|-----------------------------------------------|--------------------------|
| `try [compress "a" 'brotli]`            | `[compress "a" 'brotli]` / `[compress try ...]` | agrees                 |
| `try [compress "a" (quote brotli)]`     | `[compress "a" (quote brotli)]` / `[compress try ...]` | none / none     |
| `try [compress "a" quote brotli]`       | `[compress "a" quote brotli]` / `[compress try ...]` | none / none       |
| `try [f 6]`, `try [1 + "a"]`, `try [append/only 6 7]` | `[try [f 6] ...]` / `[try ...]`  | agrees                   |
| `try [f (6)]`, `try [1 + ("a")]`, `try [if false () 6]` | `[try [f (6)] ...]` / `[try ...]` | none / none        |
| `try [f h]`, `h` a function answering 6 | `[try [f h] ...]` / `[try ...]`               | none / none              |
| `try [q 6]`, `q: func ['x [block!]]`    | `[try [q 6] ...]` / `[try ...]`               | none / none              |
| `try [u]`, `u: func ['x [integer!]]`    | `[try [u] ...]` / `[try ...]` (`expect-arg`)  | none / none              |
| `try [compress]`, `try [f]` (`no-arg`)  | `[try [compress] ...]` / `[try ...]`          | none / none              |
| `try [do (quote (1 / 0))]`              | `[/ 0]`, a `block!` / `[/ do try ...]`        | `(/ 0)`, a `paren!`      |
| `compress "a..." (quote brotli)`, uncaught, from a file | `** Where: compress do either either if -apply-`, `** Near: compress {aaa...` | `** Where: either either if -apply-`, `** Near:` a line of `sys-start` |

Already agreeing, and to stay so: `near`'s type, index and head; an error
inside a function (`[/ f try ...]`); inside REPEAT, FOREACH, REDUCE, PARSE,
MAKE OBJECT! and EITHER's condition; CAUSE-ERROR and `do make error!` (none
for both, in both); `copy/part "abc" "x"`; APPLY; nested DO; a get-word
argument; a refusal at the top of a console line (none, in both).

**The cause is one, and it is not the line the old reading drew.** It is not
"before the native runs" against "inside it": COMPRESS's own raise is lost
through a paren too, and a refused literal is already right. Nor is the
console a path of its own: the original case was measured as `quote brotli`.
What decides it is how the call's last argument arrived:

- **R3 locates an error when it is made.** `Make_Error` (`c-error.c:458`) sets
  `where` from every frame on the stack and `near` from `DSF_BACK(DSF)`, the
  block position the innermost running call started at. `Do_Next` pushes a
  frame (`c-do.c:953`) but makes it the running one only after its arguments
  are gathered and checked (`c-do.c:966`), which is why a refusal or a missing
  argument names the caller and never the refusing function.
- **JEBOL locates an error when the walk catches it**:
  `Evaluator.sayWhereItCameFrom` runs only around `takeOneStep`. Four
  statements of the same loop raise outside it: delivering the value of a
  frame that has just ended (a paren, a function body), delivering an
  unevaluated argument, delivering unset at the end of a block, and raising
  `no-arg` there. An error from any of them leaves the walk unlocated, and the
  next walk out that catches it -- the boot's own, when a script stops --
  stamps its own location on it. That is the last row.
- **A paren DO evaluates is its own code**, and `near` is cut from it as it
  stands; R3's is always a `block!` over the same series.

The plan, in order:

1. **Check the probe in.** The three probe files behind the table go into
   `src/testFixtures/resources/errors/near-and-where.r3` with the transcript
   beside it regenerated from both, replacing the three cases it holds now.
2. **The spec.** `ARefusedArgumentIsLocatedAtTheCallAlreadyRunning`
   (`spec/natives.allium`) is right and stays. Beside it:
   - `AMissingArgumentIsLocatedAtTheCallAlreadyRunning`: `no-arg` takes the
     same location as a refusal.
   - `AnErrorIsLocatedWhenItIsRaised`: `where` is every call open, innermost
     first, the raising one included; `near` is the block at the position the
     innermost one started, always a `block!`, over the same series.
   - An invariant: the location does not depend on how an argument was
     produced -- written, from a paren, from a function, from a get-word or an
     operator, taken unevaluated, or unset at the end of a block.
   The C reading above goes in the rules' comments, and `allium check` runs
   clean.
3. **Failing tests**, in `NearAndWhereFromTheSourceTest`: every row of the
   table and every "already agreeing" case, as a grid of argument source
   (written, paren, function, get-word, operator, unevaluated, end of block)
   by failure (a refusal, a raise from the body, `no-arg`), through
   `Interpreter.run`; a paren DO evaluates; and the three routes -- a file at
   the command line that stops prints R3's `** Where:` and `** Near:`, and the
   console prints them for `compress "a" quote brotli`.
4. **The fix is in the walk, not in any native.** Every statement of the
   loop's iteration raises inside the one catch that locates, so no error
   leaves a walk unlocated; `near` is made a `block!` over the code's series.
   Then check the other walks that evaluate (DO, REDUCE, PARSE's parens, a
   port's `awake`) by the same test, rather than assuming they agree.
5. **The sweep, generated, as `make-to-sweep.py` does MAKE and TO.**
   `scripts/near-and-where-sweep.py` asks `./r3-head`, one process a case, for
   every native, action and op in `lib`, each typed argument in turn given a
   type it refuses, written and from a paren, with valid values before it; and
   writes `id`, `near` molded flat and `where` up to its `try` to
   `src/testFixtures/resources/org/jebol/near-and-where-as-r3-answers.tsv`.
   `NearAndWhereAnswerAsR3DoesTest` runs each through JEBOL. A refusal never
   runs the native, so no case writes, waits or quits; a raise from a body is
   not generated, because its path is the walk's and the grid in step 3 covers
   it. `sweep.py` and `make-to-sweep.py` name a macOS repository and JDK that
   no longer exist; the new script finds both from where it stands, and the two
   old ones are fixed the same way.
6. **The record.** `ErrorValue.traceThrough`'s javadoc says the tail of
   `where` differs from R3's; measured, it is the same
   (`try do either either if -apply-` in both), so it goes.

**Done when** every row of the table answers R3's, the grid and the generated
sweep are green, and a script that stops on any of them prints what R3
prints.

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

Every open question in the spec has an answer. The work that follows from the
answers is 1j, 1k and 1l; this sub-goal is the spec's side of it.
Every edit runs `allium check` clean.

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
     exception;
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
   together: home and values between interpreters (1j), the Rebol bugs (1k), `effect` in DRAW and
   GUI-METRIC/SET (1l).
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
real 3.22.5 does not, so each gets an entry in `docs/standing.md` saying what
and why.

1. **`find` in a binary finds 255.** `find #{00FF} 255` is none in R3 and
   `#{FF}` here; JEBOL is already right. Write it in `docs/standing.md`, and
   turn the open question into a rule.
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
6. **Copied, not fixed:** `change/part` on an image with a count changes
   nothing. The question becomes a finding in `docs/rebol-findings.md`.

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
