# Goals

Everything that is left to do on this port, in one file and in one order.

Rebol's own test suite is the measure: what still fails is listed one per line
in `src/test/resources/rebol-suite/known-gaps.txt`. It is not the whole
measure, and the goals below that own no entries are the ones it cannot see.

Take one, finish it, commit it, stop.

**A finished goal is deleted rather than kept with DONE beside it.** Many have
been; what each said is in the git history under the commit that finished it. A
list of things nobody has to do any more is a list nobody reads.

**The counts live here and nowhere else.** `README.md`,
`docs/porting-guide.md` and `using-jebol.md` describe what each measure asks and
point here for what it answered, on purpose: the same figures were once written
into four files and drifted until two of them disagreed about how many error ids
can be raised, with nothing to say which was right. A count belongs in one place
or in none. Every number below was checked on 2026-09-13 by running it.

---


## Where the port stands

| Measure | Reads |
| --- | --- |
| `scripts/c-parity.py` | 279 of 279 C functions match R3's surface -- a comparison of two *declaration files*, and read as broader than it is |
| `scripts/runtime-parity.py` | the same question asked of two *running* interpreters. Of 582 functions, **0 absent and 3 differ**, the same 3 in every column: `request-color`, `request-dir` and `request-file`, which JEBOL serves through its own port rather than shelling out to `osascript`. It began at 1 absent, 123, 581 and 430 |
| `PortingBacklogTest` | 0 of R3's 404 functions missing |
| `Interpreter.borrowedLoadFailures()` | empty -- every borrowed file loads whole |
| `system/catalog/datatypes` | 59 against R3's 58, the extra being `java-object!`, and every one of them has an arm |
| `SuiteCoverageTest` | the reader reaches 10,133 of 10,133 assertions |
| `known-gaps.txt` | **1 fails**, and no work will retire it -- goal 1 below |
| `fails-on-rebol-too.txt` | 162 a real 3.22.5 also fails or never runs |
| `scripts/error-parity.py` | **114 of Rebol's 142 error ids can be raised, and every one of the 28 that cannot has a written reason.** It also reports the 4 ids JEBOL raises that Rebol does not name -- all four are the host-grant system and the browser view, which R3 has no equivalent of -- and that no id is filed under a category the catalogue disagrees with |

`./gradlew check` is 18,438 tests, 0 failed, 0 skipped. An unread suite file
fails the build outright -- no list, no exception. `./gradlew browserCheck` is
the second gate and is not optional; it renders the same paint list in Java2D
and in a real Chrome and compares them pixel for pixel.

**`port-test.r3` owns none of it.** It had 29 when the file ports were
picked up: eighteen were real defects and have been fixed, and eleven turned
out to be assertions a real 3.22.5 does not pass here either -- nine guarded on
Windows or on Linux's `/proc`, and two with stale expected checksums. Those
eleven moved to `fails-on-rebol-too.txt` with the measurement beside each.

The one that is left is goal 1 below: `checksum-test.r3` asks to read
`system/options/boot`, which is the launcher a script runs to start a confined
child interpreter and therefore has to sit outside whatever root the script can
see. Retiring it would mean giving up confinement propagation. The reason is
written at the top of `known-gaps.txt`. Every other goal owns no entries at
all: they are equivalence the suite cannot see, the two security goals, and the
engineering and tooling work.

---

## How to work on any of them

Read `CLAUDE.md` first — this section does not repeat it, and several of its
rules are ones that are easy to break without noticing.

### The two authorities

1. **`./r3-head`** is a built Rebol 3.22.5 in the repo root. It answers in a
   second. Anything you are unsure about, ask it, and believe it over your
   reading of the C. It takes a script file, and the file needs a
   `Rebol []` header:

       printf 'Rebol []\nprobe compress "test" %s\n' "'crush" > /tmp/p.r3
       ./r3-head /tmp/p.r3

   Process substitution (`./r3-head <(...)`) works for reading but a script
   that writes files will fail on the path. Copy to a real directory first.

2. **`rebol3-source/`** is a symlink to the Rebol checkout, gitignored, and
   the IDE indexes it. It is where every port is read from. Search it with the
   IntelliJ MCP, never with grep — see `CLAUDE.md`.

### Finding what is wrong

Two tools, and they answer different questions.

**`scripts/sweep.py`** — which assertions answer *wrongly*. It rewrites each
`--assert X` as `probe try [X]`, runs the file through both interpreters and
prints the pairs that differ.

    ./gradlew compileTestJava
    python3 scripts/sweep.py image-test.r3

It stops being useful once the probe script itself raises: everything after
the first raise reads as a difference. When a file reports "N assertions, N
differ", fix the first one and sweep again.

**Two things it cannot see, both found by using it on `enbase-test.r3`.** It
counts `--assert` *source lines*, so an assert inside a `foreach` is one entry
to the sweep and one per iteration to the gate -- which is why a file can show
"121 assertions, 9 differ" while owing 29 gaps. And a probe wraps every
assertion in `try`, so a raise that stops the real file stops nothing here:
where the gate scores everything after a stop as failing, the sweep happily
reports those same assertions agreeing. Use `SuiteStops` first, always.

`r3-head` sets its current directory to the *script's* directory rather than
the process's, so the probe carries a `change-dir` to the suite root. Without
it every suite file that reads a fixture died on the first `read`, and the
sweep reported one difference for a file that had dozens.

**`org.jebol.suite.SuiteStops`** — which assertions *raise*, and a raise is
worth more than a wrong answer, because everything after it in the file never
runs. A file with one stop near the top can owe a hundred entries and need one
fix.

    ./gradlew compileTestJava
    ~/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.4+7/Contents/Home/bin/java \
      -cp build/classes/java/main:build/classes/java/test:build/resources/main:build/resources/test \
      org.jebol.suite.SuiteStops 1

(The system `java` is too old for the class files; use the JDK Gradle uses,
above. The argument is the smallest gap count worth reporting, and it is worth
passing 1: a run at 7 silently hides every file owing fewer than seven, which
is how eighteen files went unlooked-at while this file claimed to account for
all of them.)

Both tools build their interpreter through `SuiteHost`, which is the one the
gate uses. They did not always, and both reported blockers the gate never sees;
the commit that fixed the measuring tools has what that cost.

For a one-off comparison, `org.jebol.suite.SweepRunner <script.r3>` runs a
script through JEBOL with everything granted, which is what the harness gives
each suite file. Running the same script through `./r3-head` and diffing the
two is the whole technique:

    diff <(./r3-head /tmp/probe-with-header.r3) \
         <(java ... org.jebol.suite.SweepRunner /tmp/probe.r3)

**When the question is "can a script reach this line of C at all", stop
guessing inputs and watch the line instead.** Copy `rebol3-source/` into the
scratchpad, add a print where you want to know, build it with the same
compiler line `scripts/build-r3.sh` uses, and run Rebol's own suite under it.
Never instrument `rebol3-source/` itself -- it is the authority and it is
somebody else's checkout.

`Throw_Error` in `c-error.c` is the funnel every `Trap` goes through, so four
lines there report every error the interpreter raises:

    if (getenv("R3_TRACE_ERRNUM")) {
        fprintf(stderr, "ERRNUM %d\n", (int)ERR_NUM(err));
        fflush(stderr);
    }

`e/code` is the category base plus the id's position within its category in
`errors.reb` -- Throw 0, Note 100, Syntax 200, Script 300, Math 400, Access
500, Command 600, User 800, Internal 900 -- so the numbers map straight back to
ids. Rebol's whole suite run that way raises 67 distinct ids, which is the
measurement that settled which of the catalogue's 142 are reachable at all.

The other half of the same question is who calls the function the line is in.
Three of the ids that looked unreachable turned out to be in code the build
disables -- `c-do.c` holds three definitions of `Do_Path` and only one of them
is live, the others marked `x*/` and `xx*/` where the live one has `*/`. That
marker is how Rebol's build tool is told to skip a function, and it is worth
knowing before spending an afternoon looking for an input.

### The gate, and the ratchet

`./gradlew check` is the gate. About four minutes, ~16,000 tests. Never commit
without it green.

`known-gaps.txt` is a ratchet and only ever shrinks. Two tests hold it:

- `theAssertionHolds` fails if something on the list was passing and stops.
- `theGapListHasNoPassingEntries` fails if something on the list starts
  passing — that is the signal to delete those lines. It names them exactly.

So the loop is: change something, run the gate, read the list of newly-passing
entries out of the failure message, delete those lines, run the gate again.

**Three things the ratchet does not do, all found by audit and all confirmed
by running it.** Know them before trusting a green gate to mean progress.

1. **It cannot fire for an assertion that never runs.** 350 assertions carry the
   harness's own verdict "never reached", and 268 of those are on
   `known-gaps.txt`. An assertion behind an earlier raise cannot start passing,
   so for something over two fifths of the list the ratchet is inert. Those
   entries come off only when the raise above them is fixed and the whole block
   runs.
2. **It does not cover `fails-on-rebol-too.txt` at all.**
   `theGapListHasNoPassingEntries` reads `knownGaps()` and nothing else. Moving a
   genuine gap into that file drops the published backlog by one and leaves the
   build green. That was proved by mutation, not by reading.
3. **It used to reward two changes that are wrong.** `--red--` is a harness
   word now and `noRedOnlyAssertionIsAGap` fails if a red-marked assertion ever
   appears on the gap list again.

Do not `rm -rf build/test-results` to force a re-run; it makes Gradle fail on
its own binary results directory. `./gradlew cleanTest` instead.

### When an assertion cannot pass

Some assertions a real Rebol does not run either. Those go in
`src/test/resources/rebol-suite/fails-on-rebol-too.txt`, with a comment
recording the `./r3-head` session that settles it. They are not gaps and are not
run. Never move an assertion there because it is hard. The build now holds the
other half of that rule: `theFindingsListHasNoPassingEntries` fails if anything
on the list starts passing here, so a gap parked there to shrink the backlog
comes back the moment it works.

The stated typical case used to be one arm of an `either error? try [...]`.
Measured across all 84 entries, that describes 2 of them. 80 are whole files
guarded on a native this build has not got, where a real 3.22.5 reports
`Number of Assertions Performed: 0`. Two more it runs and fails.

**There used to be a second ledger that worked differently, and there is a rule
left over from it.** `src/test/resources/rebol-suite-excluded/` held 33
assertions cut out of nine vendored files, each with its identity and a reason.
Because they were not in the vendored text at all, no count saw them and no
ratchet could reach them, and the reasons went stale without anything to
notice: they had been settled against a 3.22.1 binary, and 24 of the 33 pass
here now.

The suite-selection work emptied it and the directory is gone. **A vendored file is a copy of
Rebol's and nothing else** — `everyVendoredFileIsUnchanged` fails on any
difference, and `noTestHasLostItsAssertions` catches the shape a cut assertion
leaves even without Rebol's checkout present. An assertion that should not be
graded goes in one of the two lists above, where the ratchet can see it.

### Every fix needs a JEBOL test

The suite is scaffolding and will be deleted when it goes green. A behaviour
fixed because of a suite assertion gets a test in `src/test/java` that stands
on its own: builds an interpreter, asserts on JEBOL, reads no `.r3` file.
Name it `*FromTheSourceTest`. Put the C it was read from in `docs/`, not above
the class — a test carries no javadoc and no comment — and go past what the
suite checks, because that is how a wrong reading gets caught that the suite
would have let through.

Every figure in such a test should have been read off `./r3-head` first. When
a test of mine disagreed with a real Rebol it was the test that was wrong,
every time.

### Committing

Commit to `main`. Never branch. No attribution trailers. One goal per commit,
gate green.

---

---

## A note that is still true: the gate reaches the internet

`thru-cache-test.r3`'s assertions read `raw.githubusercontent.com` and
`httpbin.org` by name, so grading them makes `./gradlew check` depend on two
third-party services. That is accepted for now because the alternative is ten
assertions that never run, and a file whose guard is false measures nothing at
all.

**It is not the end state.** Once the differences between the C and this port
are dealt with and the suite is the ratchet it is meant to be, come back and
take the external calls out. What replaces them has to be decided then rather
than now: a recorded exchange the suite replays, a local server the run starts,
or the whole file moved to a second gate that is allowed a network. Whichever
it is, `./gradlew check` should end up needing nothing but a JDK again.

Until then, a failure in that file that names a host rather than a behaviour is
the network and not the port, and it still counts as a failure -- a flake is a
fail, and this one has a cause anybody can point at.

---

## The goals

**Ordered by importance, not by size, and the first rule is that agreeing with
the C comes before improving on it.** This is a port. A place where JEBOL
answers something a real 3.22.5 does not is a defect; a place where both are
weak is a decision, and it waits. So the suite backlog leads, then the
equivalence the suite cannot see, then the two security goals -- which are
divergences from the C rather than gaps against it, because Rebol does not
authenticate a server or check a fetched module either -- and the engineering
and tooling last.

Several goals own no `known-gaps.txt` entries, which is not the same as being
small: no assertion in Rebol's suite asks whether an error id can be raised or
whether a certificate was checked.

**Whether a size is a floor or a ceiling is unsettled, and it matters.** This
file used to say floor, reasoning that fixing a stop frees the assertions
standing behind it. Two independent audit passes disagreed with each other: one
confirmed the reasoning, the other found that the harness already scores an
unreached assertion as a failure, which would make the size a ceiling and mean
the count can only fall. Nobody has settled it. Until someone does, treat a size
as an estimate and expect surprises in both directions -- and if you fix a stop
and the count goes up, that is the answer, so write it down here.

---

### And no Java PDF implementation, now or later

**Decided, and the hang that prompted it is fixed.** Making the borrowed codec
work is equivalence work and is in scope. Writing a Java PDF implementation is
not, and will not be, however much of PDF the borrowed codec turns out not to
do.

The borrowed codec is what Rebol has, so matching it is the whole obligation.
Its own header is honest about the ceiling -- it decodes the object structure
"for examination" and is "not yet much useful for document creation" -- and
JEBOL is not going to exceed that by writing a second implementation in Java.
Doing so would be the thing this port does not do: reimplementing in Java what
Rebol writes in REBOL.

**Anything beyond it is an optional extension and a dependency the caller
chooses.** A real PDF capability means a real PDF library, and the shipped jar
has no dependencies at all -- about 1,700 KB of which the borrowed library and
the bundled modules are most of it, and nothing on the classpath that the JVM
does not bring. That stays true. Somebody who wants more than the borrowed
codec gives adds the library and a bridge to it themselves, and with neither
present nothing registers and nothing is attempted.

### 1. One entry, and no work will retire it

    checksum-test.r3   binary? file-checksum system/options/boot 'md5

`known-gaps.txt` is down to this single line, and it is the one that stays. The
reason is at the top of the file and has not changed: `system/options/boot` is
the launcher a script runs to start a confined child interpreter, so it has to
sit outside whatever root the script can see, and this assertion asks to read
it. With `--root /` it passes here exactly as it does on ./r3-head; under the
suite host it cannot, and making the field name something inside the root would
give up confinement propagation to retire one assertion.

**So the suite has nothing left to say about JEBOL, and the goals below are the
ones it could never see.** Read them in order: what the error catalogue work
uncovered, then what reaching zero would not prove, then what the suite does
not ask.

Two things are worth keeping from the way the last of it was cleared.

**`org.jebol.suite.ShowFailures` prints why each listed gap fails** and the
first line of its source, which is the report this file used to ask people to
assemble by hand. `ShowAssertion` prints the source behind a gap's name. Both
boot an interpreter before reading the suite, and that matters: the reader
cannot read a datatype written in construction syntax until one exists, and
quietly answers a truncated file if none does -- which made `datatype-test.r3`
look like twenty-eight assertions instead of fifty-one.

**Four of the last thirty-one were never work at all** and are now in
`fails-on-rebol-too.txt` with the guard's answer beside them: one arm of an
`either 'Windows = system/platform`, and three guarded on
`system/version < 3.19.1`. The arm not taken is not a gap, and the file already
had a section for exactly that shape.

---

### 2. Five divergences the error catalogue work uncovered

The catalogue goal that stood here is finished and what it found is not. These
five were each measured against `./r3-head`, none of them is about which ids
exist, and all five are still here.

1. **The reader takes a lone `:`, `'` and `\` for words, and reads a control
   character as one too.** `load ":"` answers a word here and `** Syntax error:
   invalid` on a real 3.22.5; `load "^(01)"` answers that character as a word
   where R3 answers an empty block, because the lexer's table calls every
   control character a space. This is the sharpest of the four: every script
   that loads a stray colon gets a word instead of an error, quietly.

   `to word! #":"` went the same way until it stopped asking the reader.
   `Natives.theWordASingleCharacterSpells` now reads the C's lexical table
   directly, which is why the conversion agrees with `./r3-head` on all 300
   code points and the reader still does not.

2. **The reader's errors carry no arguments.** R3 names the token it was
   building and the text that would not read -- `transcode to binary!
   "1.2.3.4.5.6.7.8.9.10.11.12.13"` gives `arg1` "tuple" and `arg2` the text --
   and every reader failure here gives none at all. `TranscodeResult` already
   carries `tokenKind` and `offendingText` and `failureReading` already passes
   them, so the shape exists; about sixty of the raise sites in `Transcoder`
   call the plain `failure(...)` instead and lose them. It matters more since
   the ids were made faithful: an unterminated string and a bad escape both
   report `invalid` now, as they do in R3, and the arguments are what tells
   them apart.

3. **WORDS-OF cannot tell a field named SELF from an object's own SELF.**
   JEBOL marks the pointer by the slot's name, so about twenty places filter on
   `canonical().equals("self")`; R3 marks it by position, slot zero. A context
   made by USE has no such slot, so `append that 'self` adds an ordinary field
   and R3 lists it -- `[x self]` -- where JEBOL adds it and hides it. Making
   the slot say what it is, rather than the name saying it, is the fix.

4. **A series action on something that is not a series answers the wrong id.**
   `head 5` is `cannot-use` here and `expect-arg` on a real 3.22.5. Found in
   passing while looking for `no-such-action`; not chased, and one line of
   measurement is all there is on it.

5. **A host exception can still escape as a host exception.** `make string!
   2000000000` threw a `java.lang.OutOfMemoryError` out of the interpreter
   and killed the process -- a count a real 3.22.5 accepts, because its cap
   is in bytes and a JVM's is in what the heap holds. That breaks
   `ErrorsAreValuesNotHostExceptions` in `eval.allium`, which says no failure
   leaves as a host-language exception.

   MAKE is mended: it refuses a count past the C's own cap without trying,
   and turns an allocation the heap cannot serve into `no-memory`, which is
   both of the arms the C has. **Every other way a script can ask for memory
   is not.** Appending to a series in a loop, reading a large file, joining
   strings -- none of them has the guard, and the guarantee is only as good
   as the thinnest of them. Finding the rest means asking where JEBOL
   allocates on a script's say-so, which is a sweep nobody has done.

**What the catalogue goal ended at**, so that a later reading of this file does
not have to recover it: `errors.reb` names 142 ids, JEBOL raises 114, and each
of the 28 it does not is accounted for.

**Twenty-four cannot be raised by a real 3.22.5 either**, and the reason is
written in the C rather than inferred from probing:

```
11  in gen-errnums.h and in no .c file at all -- no-buffer, security-level,
    resv700, globals-full, exited, no-load, bad-decode, block-lines,
    invalid-op, parse-into-bad, wrong-denom
 3  commented out -- limit-hit (the parse depth check in u-parse.c),
    expect-type (the only caller of Trap_Expect), bad-path (which lives in
    an `xx*/`-marked duplicate of Do_Path, one of three in c-do.c and not
    the live one)
 6  extensions, which this build has not got -- bad-extension,
    extension-init, no-extension, command-fail, bad-command, handle-exists
 2  compiled out or unexported -- positive is behind #ifdef USE_NO_INFINITY,
    deprecated belongs to as-binary and as-string which 3.22.5 does not
    export
 2  throw markers rather than errors -- halt and quit go through Halt_Code,
    never Trap, and `catch/quit [quit]` answers unset
```

**The last four are live C that a script has not been shown to reach**:
`max-natives` fires only while booting; `cannot-close` needs the operating
system to fail a socket close; `bad-file-path` needs `To_Local_Path` to return
null, which happens on a null byte pointer rather than on anything a file value
carries; and `throw-usage` needs a reflect action to be handed a cell still
marked thrown, which nine shapes could not arrange because THROW unwinds first.

**And the whole list was checked by running the reference rather than reading
it.** `Throw_Error` in `c-error.c` is the one funnel every `Trap` passes
through, so a copy of the tree with four lines added there prints the number of
every error raised. Rebol's own full suite under that binary raises **67
distinct ids and not one of these 28** -- which is also why none of them has a
suite assertion to port. `scripts/build-r3.sh` builds from a copy in the
scratchpad; never instrument `rebol3-source/` itself.

**Two ids were on the unreachable list and should not have been, and both were
found by being pushed on rather than by the probing that put them there.**
`no-memory` is one line away -- `make block! 500000000` -- and had been written
off after two probes that hit an argument range check before any allocation.
`bad-sys-func` needed the C's callers read rather than inputs guessed: it fires
when one of the four functions the interpreter calls by name is not a function,
and the four are MAKE-PORT*, MAKE-MODULE*, DO* and START, not the neighbours
with similar names that four earlier probes had tried. `system/contexts/sys` is
an ordinary object, so `system/contexts/sys/make-port*: 5` followed by `make
port! [...]` reaches it.

The lesson is the same both times and it is worth more than the two arms:
**a probe that fails early has measured the guard in front of the thing, and
"I could not reach it" is not "it cannot be reached".** Read the callers, or
instrument the reference and watch. `error-parity.py`
prints the count, the ids JEBOL raises that Rebol does not name, and whether any
id is filed under a category the catalogue disagrees with.

---

### 3. What reaching zero would not prove

None of this is on `known-gaps.txt` and none of it can be, because the suite
tests what functions **return** and these are all about what functions **say
about themselves**. `action?` appears zero times in all 67 vendored files and
`no-refine` appears zero times, so no assertion in the suite could ever have
caught any of it.

`python3 scripts/runtime-parity.py` asks both interpreters about every function
Rebol's `lib` holds. When it was first written it said:

    582 asked, 1 absent
    123 report a different datatype
    581 answer words-of differently
    430 answer a different spec-of length

It now says 0 absent, 3, 4 and 4, and the same handful accounts for all eleven.
Run it before and after a change here; it is the only thing that will tell you
whether the change worked.

**What was wrong, and what it took.** Each of these has a
`*FromTheSourceTest` beside it and every expectation was read off `./r3-head`:

- **An action said it was a native.** Two things and only the first is a list:
  `actions.reb` declares sixty, and the other fifty-seven are the type-tests
  that `types.reb` generates, one per datatype.
- **`words-of` answered none for 581 of 582 functions**, and `spec-of` rebuilt
  a block from the registry that had no refinements, no order and no prose.
  Both now read Rebol's own declarations, which is what a declaration is for.
  `actions.reb`, `natives.reb` and `generated/gen-natives.reb` are vendored
  beside `errors.reb`; missing that third file left 45 functions still wrong
  and looked finished without it.
- **`APPLY` dropped every refinement**, so `apply :copy [[1 2 3 4 5] true 3]`
  answered the whole series. It reads the block against the function's words
  now, which is why it had to wait for `words-of` to work.
- **An unknown refinement was accepted on any REBOL-defined function** —
  `f/nope 1` answering 1, `pad/left "ab" 5` padding on the right. That was
  every function in the borrowed library and every function a script writes.
- **`parse 1 [end]` answered false** where R3 refuses a non-series.
- **`make block! -1`, `round/to 1 0` and `o/self: 2`** all answered where R3
  raises.

**What is left, and why each is left.**

1. ~~`near` and `where` are blank on every raised error.~~ **Done, with a
   stated limit.** Both are attached as the error passes back out through the
   evaluator, which is the one place holding the frames — a native raising
   `zero-divide` has no idea what block it is in. `near` matches R3 exactly in
   the shapes checked: `[/ 0]` for `1 / 0`, and the innermost block rather than
   the caller's when the failure is inside a function.

   **`where` matches only as far as the script goes.** R3 builds it from its own
   data stack, so its chain runs on into the console's frames —
   `[/ f try all print do either either if -apply-]` where this answers
   `[/ f]`. The head is about the script and matches; the tail is the
   interpreter talking about itself, and this is a different interpreter.
   Asserting the whole list would be asserting the shape of the C's evaluator,
   so the test asserts the head and says why.

   One shape is known to differ: where R3's argument checking fails before the
   callee gets a frame, its `near` points at the caller's block —
   `try [append 1 2]` reports the whole outer block. JEBOL reports the call. R3's
   answer there is a consequence of when it pushes a frame, not of the
   language.
2. **The deflate family does not produce Rebol's bytes.** Narrower than it was:
   the compression work made the headers agree, because a level nobody asked
   for is now the
   slowest here as it is there, and gzip's extra-flags and operating-system
   bytes are written the way `gzip_compress.c` writes them. What is left is the
   body. 3.22.5 compresses with **libdeflate**, not zlib, and chooses a stored
   block where `java.util.zip` emits a fixed-Huffman one; matching it byte for
   byte means porting libdeflate. LZMA and Brotli are no longer examples of
   this -- both are byte-exact where they claim to be.
3. **`request-color`, `request-dir` and `request-file` answer `native!` where
   R3 answers `function!`.** Deliberate and documented in
   `mezz/ORDER.txt`: R3 defines them in `mezz-osx-dialogs.reb`, which shells
   out to `osascript` on macOS only, and JEBOL serves all three through the
   WINDOWS service and its port, which works anywhere and asks for a grant
   first. Not a defect; do not "fix" it without reading that note.
4. ~~`set-cookies` reports more `/local` words than R3 does.~~ **Done.**
   COLLECT-WORDS deduplicated on the spelling where a REBOL word is
   case-insensitive, so `Domain` and `domain` were two words and FUNCTION gave
   that one three locals R3 does not list. The first spelling seen is kept.

---

### 4. What the suite does not ask

**The suite is the measure, and it is not the whole surface.** Running all 930
combinations of MAKE and TO against fifteen target types and thirty-one source
values, through JEBOL and through `./r3-head` side by side, found 140 answers
that differ. Rebol's own suite asserts most of those families and names them in
`known-gaps.txt`, so they were already counted. Some it never asks at all, and
those had no name until the sweep: `to integer! #FF` answered a refusal where a
real Rebol reads the digits as hex and says 255.

**A hundred and thirty-nine of the hundred and forty are fixed.** What is left
is one answer, and it is a quirk rather than a rule:

```
to paren!  1   a typeset converted to a paren comes back a *block*, because
               the C's `Set_Block` writes REB_BLOCK whatever type was asked
               for. JEBOL returns the paren that was asked for, which is
               arguably the better answer, and no assertion covers it
```

The decimal, percent and binary columns went by porting the make-and-to switch
of `T_Decimal`, of `make_binary` and `Scan_Decimal` with them. The path column
went by fixing molding rules rather than any conversion. The string column went
by porting `make_string`, which turned out to be about FORM: an any-string is
copied as it stands so a tag loses the brackets FORM keeps, a path keeps its
slashes, and `Form_Object` writes one field to a line with no `make object!`
around them -- while *molding* each field's value, which is the part of it that
cannot be guessed. And the last seven went with one line: **a nought byte ends
the source**, so `make block! #{31 00 32}` is `[1]` and an empty one is `[]`
rather than a block holding a nought.

**Both ported columns turned out to be a list and not a rule**, and reading
them as a rule is what had gone wrong. Anything with bytes underneath looks
convertible to a binary, and a percent, a paren, a path and an issue all have
bytes and are all refused, where the decimal, block, string and word they
resemble are taken.

**The path column was not a conversion problem at all.** Thirty-nine of the
forty-six were one molding rule read too loosely -- `isAnyWord()` where the C
says `IS_WORD` -- which wrote `a:/b`, `/a/b` and `#a/b` in a form that does not
read back. Worth remembering when a column of the sweep looks large: count the
causes, not the cases.

**A question never asked reads as a wrong answer.** Twice more, and both in
bitsets. `pick` on one accepted a char and returned false for everything else,
so `pick charset "a" 97` said the set does not hold `a` -- where the C shares
one arm between `A_PICK` and `A_FIND` and `Check_Bits` takes a char, an
integer, a string, a binary or a block of ranges. And `zero?` refused a bitset
outright although the C declares its argument as a bare `value`: for a bitset
it is not a comparison but `Is_Zero_Bitset`, which asks whether every byte is
what an empty set would hold -- nought, or `0xFF` where the set is written as a
complement. Reading that arm also turned up a range test over the datatype
table, so a zero pair and a zero tuple are zero too.

**COPY of a map returned the same map.** Not a suite gap -- a data-corruption
bug: `c: copy m` then `c/a: 99` changed `m/a` too, because the `copied` switch
had a case for every container except a map and a map fell to
`default -> original`. The same shape as the bitset COPY bug already recorded in
`BitsetValue.duplicate`, where a shallow copy let the url parser scribble on the
catalogue's own charset.

Chasing it turned up two more. **`same?` compared maps and bitsets by content**,
so a map was the same value as its own copy -- everything holding its contents
somewhere answers by *where*, which is the whole of the C's mode three. And
**`/types` and `/deep` were one question when they are two**: `/types` says
which datatypes are duplicated rather than shared, `/deep` says whether to keep
doing it inside what was duplicated. Without `/deep` a copied member is copied
and its own contents stay shared, which is what
`if ((types & CP_DEEP) != 0)` guards. Sixty-three of `copy-test.r3`'s
sixty-four assertions came off together, and it is now down to one.

**MOLD/ALL is a flag every value reads, not a choice made once.** The last
eight went with it. `MOPT_MOLD_ALL` sits on the C's mold state, so a date below
it writes ISO and a typeset writes its construct form, and a path that has to
fall back to a construct sets the flag for its own contents whether or not the
caller asked for it. JEBOL had no molder object to hang a field on, so the flag
is a thread-local beside the two the file already keeps for nesting depth and
recursion.

**The datatype enum was not in `types.reb` order and now is.** Forty-three of
the fifty-eight sat somewhere else, which is invisible until a typeset molds
itself: a typeset writes its members by walking the table, so
`mold any-string!` was `[string! file! url! email! tag! ref!]` where a real
Rebol writes `[string! file! email! ref! url! tag!]`. Reordering the enum fixed
every typeset at once and moved nothing else -- the gate was clean and
`system/catalog/datatypes` still reads in order. The enum's own doc now says
the order is not free to change, and why.

**Twice now, one JEBOL helper stood for two different C functions.** MAKE and
TO were one `convertedTo`; FORM and the arm TO STRING! uses were one
`runTogether`. Both looked like tidy sharing and both gave wrong answers,
because the C has two functions for a reason and the difference is exactly what
gets lost. The second one was worse: taking a tag's brackets off in the shared
helper broke AJOIN, AJOIN/with and COMBINE in the same commit that fixed
TO STRING!. When the C has two entry points, JEBOL wants two as well, even when
they agree on nearly everything.

**And the sweep does not replace the suite.** A sweep builds values and asks
about them, so every value in it stands at its head. `mold next 'a/b` is a path
standing at its second of two, and that is what caught the same rule reading
the remaining count where the C reads the whole series length. All 930 cases
agreed and mold-test.r3 still said no.

The sweep itself is worth repeating on other datatypes. It is cheap, and it
found two things that four separate readings of the C had not. See
`docs/rebol-findings.md` entries 21 and 22, both of which came out of one run.

---

### 5. The prelude is a bootstrap, not 32 forks -- audited 2026-09-14

**Run, and the answer is that there were no forks.** Every one of the 36 words
`prelude.reb` defines was put to both interpreters -- `type?`, `spec-of` and
`body-of` on each, the two outputs diffed exactly -- and 35 matched to the
character. The one that did not was `empty?`, and it turned out not to be a
fork either.

**The prelude's copies are replaced as Rebol's library loads.** 32 of the 36
are also defined in a vendored `mezz` file, and the definition standing at
runtime is the library's: `collect`'s body at runtime is
`mezz-series.reb`'s, not the prelude's, and the two differ. The prelude's
versions exist so that the prelude and the earliest library files can run at
all -- deleting `empty?` from it makes the prelude fail to load, because the
prelude uses it before `mezz-series.reb` has defined it.

The four the library does not define -- `to-block`, `to-decimal`, `to-string`
and `funct` -- answer what a real 3.22.5 answers anyway.

**What the audit did find was a defect one layer down.** `make :tail?
[[{Doc} series [series! none!]]]` is how Rebol's library widens a built-in's
declared types without rewriting it, and it is how `empty?` is defined. JEBOL
took the derived parameters for calling -- a narrowed derivation refused
arguments correctly -- and then reported the *original's* specification from
`spec-of`, because a built-in's declaration is looked up by name and a derived
one carries the original's name. `empty?` therefore advertised `tail?`'s
narrow type list. Fixed: a derived built-in carries its own specification.

**And it found why nothing had caught that.** `runtime-parity.py` compared
`spec-of` **length**, so a widened type list and a rewritten docstring of the
same item count both read as agreement. It compares the text now. Re-run
after the change, the count is unmoved -- 3 of 582, the same three
`request-*` functions JEBOL serves through its own port -- so the loose
measure had been hiding exactly one thing, and this was it.

The lesson is the one this file keeps learning: **a measure that counts
instead of comparing will agree with anything.**

---

### 6. Loose ends

**A task is made and read and never run.** The datatype is whole -- `make
task!` builds the five-field header, the fields are read and written through a
path, it molds and forms as its header, and DO answers it. The one thing
missing is the thread: `Do_Task` is `OS_Create_Thread`, and the body is bound
to the contexts the parent is using, none of which is safe to touch from two
threads. Running it on the calling thread instead would be worse than not
running it -- `do make task! [1 / 0]` would raise where a real Rebol answers a
task. The open question beside `DoOfATaskAnswersTheTask` in spec/natives.allium
says what it would take, and that a host service the caller has to grant is the
shape the answer probably wants.

**Five scheme names R3 registers and JEBOL does not.**

```
callback  clipboard  midi  serial  udp
```

`file`, `dir`, `checksum`, `crypt`, `system` and `bundled` are served now.
`callback`, `clipboard`, `midi`, `serial` and `udp` are the ones left.

Still unsettled, and older than the schemes: the command-line REPL grants only
WINDOWS, so `read %README.md` answers `no-service` there. Whether that is the
design or a gap in the CLI has never been decided.

**Four fields of `access-os` answer `not-here`** -- `uid`, `euid`, `gid`,
`egid` -- where a real Rebol answers a number. The JVM has no portable way to
ask. `pid` works.

**61 open questions across nine spec files**, the heaviest being
`natives.allium` with 26. It read 55 and 19 until 2026-09-13, and the drift is
the point rather than a slip: **finishing every goal above would not empty this
list, and the list grows as the goals are worked.** Each goal that ports a
subsystem properly tends to leave a question behind, because reading the C
closely is what turns "we never thought about it" into "here are two readings
and neither is obviously right".

Roughly a third are owned by a goal above and would be forced by doing it: the
four in `draw.allium` and two of the six in `screen.allium` by the graphics
goal, two of the seven in `load.allium` by modules and IMPORT, and a handful in
`natives.allium` by the error catalogue and the surface sweeps.

The rest are owned by nothing, and three groups stand out. **All three in
`parse.allium`** -- what THEN commits to, what a word holding a foreign position
names, and how far backtracking goes -- because no goal covers PARSE. **All five
in `embed.allium`**: how often the evaluator checks whether to stop, whether
there is a memory bound as well as a time bound, what a host sees of a script's
progress, and the two about a started program's streams. And the standing design
questions in `natives.allium`: where SECURE's boundary sits, whether a `struct!`
is ever more than its layout, which verbs reach a port's actor, and whether
values may cross between interpreter instances.

Those want a pass of their own rather than a line here, and it is not one of the
goals above.

---

### 7. Graphics -- DRAW is done; what is left is VID and the old markup path

**Every command the dialect table declares is painted**, measured by
extracting both lists on 2026-09-13: `dial-draw.reb` declares 35 drawing
commands and the seven SHAPE sub-commands, and JEBOL handles all of them bar
`effect`. It was 22 that morning.

The fourteen that went in: `transform`, `invert-matrix`, `clip`, `triangle`
with its Gouraud shading, `spline`, `arrow`, `line-pattern`, `grad-pen` in all
six kinds, `image`, `image-filter`, `gamma` and `text`.

**The rule that made the awkward ones affordable** is the one worth keeping:
*anything neither toolkit has is worked out in the domain and handed to both
as something they do have.* Gouraud shading becomes a mesh of 256 flat
triangles clipped to the triangle; a conic gradient becomes a fan of 240
wedges clipped to the shape it fills; a diamond one becomes rings; a
two-colour dash becomes two paths, one for the dashes and one for the gaps; a
keyed image becomes a copy with that colour made see-through; a warped image
becomes a resampled copy; a scaled image is resampled here so that two
rasterisers cannot disagree about it. A renderer still executes and decides
nothing.

The two renderers grew one primitive between them: clipping to a path rather
than only to a rectangle. Both toolkits have it natively.

**What is genuinely excluded, and why:**

- `effect` is a dialect of its own -- blurs and tints over a whole image --
  that happens to be listed in the draw table. It belongs with the codecs.
- `image-options` and `image-pattern` are declared in `draw.reb` and absent
  from `dial-draw.reb`, so DELECT cannot read either of them in any Rebol.
  The same trap catches `opened` and `resize`: both are documented in
  `draw.reb`, neither is in the table, and writing the documented word makes
  the whole command fail to read.

**There is no reference for any of it and that is a fact about Rebol, not a
gap here.** `./r3-head` has no `draw` at all -- `value? 'draw` is false --
because `n-draw.c` does not exist in the checkout and `n-graphics.c` is
excluded from the build as `;old source`. The only dispatcher in the tree is
`src/os/win32/host-draw.c`, Windows-only, calling an AGG that is not
vendored, and no assertion in Rebol's 10,133 mentions the dialect. So the
argument handling was read off that C and the pixels were checked the only
way available: the same paint list executed by Java2D and by a real Chrome,
compared.

**That comparison is now tolerant where it has to be and exact everywhere
else**, which is what made curves, gradients and text checkable at all. A
pixel in the flat inside of a shape must match exactly; an edge pixel may
differ by up to 24 of 255, and at most a fortieth of the picture may be edge
that uses it. The numbers are in `spec/screen.allium` so that widening one is
a change to the specification. A deliberate 12-by-12 patch differing by 7 was
caught by it, so the allowance has not made it blind.

`./gradlew browserCheck` draws 28 pictures in both renderers and writes them
to `build/renderer-pictures/` beside a difference map, which is the quickest
way to see what a change did.

**Text is the one thing the two renderers cannot be held to pixel for pixel.**
They measure and hint glyphs differently. The words, the place, the size, the
weight and the colour are all decided in the domain; the shapes of the letters
are the toolkit's, and the raster comparison leaves them alone.

Still here: the stroked-curve comparison problem, the 522 lines of old markup
path, VID, Android, and the events-name-the-wrong-window one.

---

### 8. Code from outside is not authenticated -- the TLS client

**Found on 12 September 2026, by reading `prot-tls.reb` rather than by a test
failing.** It owns no `known-gaps.txt` entries, because no assertion in Rebol's
suite asks whether a certificate was checked.

`read https://` works here now, and `import` fetches over it. What that
transport gives is confidentiality against somebody listening and **nothing
against somebody in the middle.** Three holes, each read off the source:

1. **The chain is never checked.** `decode-certificates` reads the list, takes
   the first certificate's public key, and stops. No issuer, no chain building,
   no expiry, no hostname match.
2. **There is no trust anchor to check it against.** No trust store anywhere in
   the source. mbedTLS is vendored but only the primitives -- `oid.c`,
   `ecp.c`, `md.c`, `asn1parse.c`. `x509.c` is not among them; the handshake is
   written in REBOL.
3. **The one signature check there is does not stop anything.**
   `decode-certificate-verify` acts only for signature type 2052 and ends:

        unless rsa/verify/pss :key :to-sign :signature [
            log-error "Certificate validation failed!"
        ]

   `log-error` is `sys/log/error`, which prints. A failed verification is a log
   line and the handshake carries on. And it verifies against the key from the
   certificate the server itself sent, so even aborting would prove only that
   the server holds its own private key -- not who it is.

So a proxy presenting a certificate of its own is accepted, and a module
fetched through one is evaluated. That is the other half of the note on IMPORT
above: a checksum answers "is this the code I expected", and this answers "am I
even talking to who I think" -- and neither is being asked.

#### The decision this needs first

**JEBOL's rule is to be faithful to the C, and here faithfulness reproduces the
hole.** `prot-tls.reb` is Rebol's file, loaded rather than rewritten, and that
rule has earned its keep everywhere else. It cannot hold here: a port that
silently accepts any certificate is not a faithful port of a security decision,
because Rebol did not decide this -- hand-rolled TLS is hard and this is what it
looks like when it is not finished.

Which leaves where the divergence goes, and that is the design question:

- **A host port, like `FilePort` and `NetworkPort`.** Validation is a host
  concern -- the trust anchors belong to the machine, and the JDK already has
  them in `cacerts`. A `CertificateAuthority` port the domain owns and the
  adapter implements would fit the architecture exactly, and a host that
  installs none gets the refusal rather than the hole.
- **Where the protocol would call it.** Not by editing the vendored file. The
  candidates are the `CRT` codec, which JEBOL could serve as a native that
  validates as it decodes, or the crypto natives `rsa/verify` and
  `ecdsa/verify` that the protocol already calls -- though neither is given the
  hostname, which is half of what has to be checked.
- **What a failure does.** Refusing the connection is the only answer that
  helps. Anything that logs and continues is what is there now.

What is already built to work with: the `CRT` codec parses `issuer`,
`valid-from`, `valid-to`, `subject`, `public-key` and `signature` out of a
certificate, so the fields are in hand; `rsa/verify` and `ecdsa/verify` work;
and `Check_Security(SYM_NET, POL_EXEC, ...)` in `p-net.c` means a host can
already refuse the socket outright, which is the blunt mitigation until this is
done.

**Until it is done, say so where it matters.** `read https://` reads as a
secure operation and is not one. A host embedding this and reaching anything it
does not control should know that before it does.

---

### 9. Code from outside is not verified -- no checksum on a fetched module

**Nothing crosses the wire today**, which is why this is a goal rather than a
live hole: the thirteen modules this build has no other way to reach are bundled
with it and read through the `bundled:` scheme, and `system/modules` names
nothing remote. The moment an address points outward again -- a host adds one, a
module this build does not carry is wanted, an extension becomes loadable -- the
fetch has to be verified, and **a checksum must be implemented on that path
before it is used.**

What is already there, and what is not:

- **`import/check hash`** exists and is the right mechanism. `load-module`
  declares `hash [binary!]` and takes it from the caller, so the expected digest
  comes from outside the thing being checked.
- **A script's own `checksum:` header field is verified** by `load-header`,
  which answers `bad-checksum` on a mismatch. **That is not the same thing and
  must not be mistaken for it.** The file asserts its own hash, so a file that
  was tampered with carries a tampered hash and passes. It catches corruption in
  transit and nothing else.
- **`download-extension` verifies nothing.** It is `content: read source` then
  `write file content`, with no digest between them. That is the hole.

So the requirement, when the path is next used:

1. `system/modules` carries an expected digest beside each remote address, not
   only the address.
2. The fetch verifies the bytes against it **before** they are written to the
   modules directory and **before** anything is evaluated. Writing an unverified
   module to disk is most of the damage already done, because the next run finds
   it as a local file and never fetches again.
3. A mismatch fails the import. It is not a warning and not a log line.
4. No digest recorded for an address means the address is not usable.

A bundled module is a different and much weaker case: it cannot change under a
running system, so a digest there guards a corrupted build rather than an
attacker. Worth having, not urgent.

**The TLS client goal is the other half of this.** A checksum answers "is this the code I
expected"; authenticating the server answers "am I even talking to who I think".
Neither is being asked, and a fetch wants both.

Still to decide: whether a host should be able to refuse the fetch outright, the
way it refuses the filesystem and the network. A host serving untrusted scripts
wants that more than it wants either check.

---

### 10. The type-major refactor

**The original complaint, and much the largest piece left.** One `t-*.c` per
increment, bitset as the pilot.

The graphics work left a hint about the shape: `PaintInstruction` is sealed, so
adding a kind broke every renderer's switch at compile time. `VectorValue`
proved it again -- adding it to `SeriesValue permits` made the compiler
enumerate every arm that needed work. That is what the action seam wants.

---

### 11. The boot -- 343ms cold, 72ms warm

**343ms for the first interpreter, 72ms once the JVM has settled.** A
7900-test run pays the 72ms per class, and that is the floor rather than the
machine.

Pool first, then library caching. The series byte accounting behind STATS is
already in that allocation path, and it costs about 2ms of the 72.

---

### 12. A debugger

**Two halves, and the file has learned to say which is which.** One is parity
work with a reference standing behind it. The other is a feature nothing can
be checked against, and pretending otherwise is what went wrong with DRAW.

**The half with a reference: R3's own debug natives.** All six words are
defined here -- `trace`, `stack`, `ds`, `dump`, `dp`, `check` -- and at least
one of them answers differently. `trace on` over `x: 1 + 2`, measured on
2026-09-13:

```
R3                              JEBOL
 4: x:                          3 : x:
 5: 1                           4 : 1
 6: + : op! [value1 value2]     (missing)
 7: 2                           6 : 2
   --> +                        (missing)
   <-- + == 3                   (missing)
```

So JEBOL names no operator, reports no call into one and no value out of one,
and counts lines differently. `trace/back` and `trace/function` are untested
here, and `stack` with its eight refinements -- `/block /word /func /args
/size /depth /limit` -- has had nothing said about it at all. Every one of
those is checkable against `./r3-head` in a second, which makes this the
cheap half and the half to do first.

**The half with no reference: stopping, stepping and looking.** R3 has no
breakpoints, no stepping and no way to inspect a paused frame, so there is
nothing to port and nothing to diff. It is a feature, on its own merits, and
the merits are real: this interpreter is meant to be embedded in a server,
and a dialect that misbehaves in production is currently debugged by printing.

What it would be built on is already there and was built for something else.
The evaluator walks explicit frames on the heap rather than recursing -- that
is what makes `where` and `near` work on a raised error, and what makes the
depth limit a policy rather than the host stack. A stepper wants exactly that
seam: a frame you can stop at, read and resume.

**Three things to decide before any of it, and none is technical:**

1. **What drives it.** The Debug Adapter Protocol is what an editor speaks,
   and speaking it means an ordinary editor debugs a REBOL script with no
   plugin. It is also a wire protocol in a jar that has no dependencies, so it
   would be written here or not at all.
2. **What a paused script does to its host.** A run carries a deadline and a
   grant, and a debugger that parks a request thread is the same problem as
   the blocking VIEW in `screen.allium`. A paused script has to be visible to
   the bounds that were set for it, or the bounds are a lie.
3. **Whether a script may debug itself.** `trace` already lets one, which
   makes a breakpoint native the obvious next step and the security question
   immediate: a script that can pause and inspect frames can inspect frames it
   was not given.

**Do the parity half first regardless.** It is measurable today, it costs
little, and a `trace` that agrees with R3 is the thing anybody reaches for
before they reach for a debugger.

---

### 13. LLM-friendly MCP tools

**The reader will only ever be an LLM, and that decides the design.** A model
does not misunderstand, it infers confidently from training data that is mostly
REBOL 2. It has no faculty for caution, so a warning it is asked to heed is a
warning that gets ignored under task pressure. **Every warning has to become a
mechanism or it is not there.** And it reads exactly one channel without being
asked to: the text of the error it just caused.

#### Before any of the tooling

Nothing below pays until a dialect can be written, run, and diagnosed in one
command. All of this is hours.

- **A dialect that fails loudly.** A rule that does not match answers false, and
  the natural shape of the calling code drops it. `parse [add 200 grams of flour
  wibble]` against a four-command grammar returns `[200 grams flour]` with no
  error. A person notices the answer looks short. A model accepts it, and there
  is no second line of defence.
- **One worked dialect, thirty lines, with a test.** DELECT matches by type and
  ignores order; PARSE matches by position. Nothing states that choice anywhere,
  so the first move is a guess, and the guess is PARSE because that is what the
  training data talks about.
- **Ship `using-jebol.md` inside the jar, printed by `--manual`.** Written to
  standard output, exit 0. The distribution now carries the file, `LICENSE` and
  `NOTICE`; the flag does not exist yet.

#### The dialect schema, generated

A DELECT command is not a production rule. It is a record with typed optional
slots that arrive in any order, so what falls out of `system/dialects` is a
schema rather than a grammar -- and a schema is the shape a model has seen ten
thousand times in tool definitions.

Generate it by walking a booted interpreter, never by parsing `dial-*.reb` out
of the mezz. That is the trap `c-surface.py` fell into: it read only the boot
files, could not see the 54 natives declared in C comments, and reported
MISSING: 0 while `binary` was absent.

Make it a test rather than a script, in the shape of `SurfaceReportTest`, so a
change to the table fails the build instead of shipping a binary that describes
a dialect it no longer has. The types come from R3's table and stay faithful.
The descriptions -- which R3's objects do not carry, and without which a model
produces acceptable nonsense -- live in a file beside it, and the same test
fails when a command has none.

Its first customer is the error message, not the server: with the schema, a
failure can name the acceptable set at the position it stopped.

Two things to read before writing it: what a command's value looks like inside
a `system/dialects` object, and whether the table marks a slot that is itself a
sub-dialect. `ShapeSubDialect` says DRAW nests. `Delect.read` and
`u-dialect.c` say whether the table knows.

#### Three tools

| Tool | Answers |
| --- | --- |
| `run` | evaluate under a declared fixture -- conclusion, value or error, services asked for, port calls made |
| `match` | this block against this grammar: matched, or where it stopped and what was acceptable there |
| `word` | what this word takes, what it does, whether it is reliable in this build |

`run` replaces a validator returning a boolean, which is the trap: a model reads
"valid" as "correct" and stops. Validation is not separable from execution here
-- a word means nothing until it is looked up, PARSE rules live in words, and
blocks are built by `compose` at runtime -- but it does not need to be. It is
execution with the effects removed, and the ports are already that seam. The
fixture defaults to nothing existing and nothing granted, so the laziest
possible call still answers "it asked for FILES and you granted none".

A run covers a path, not a program, because a stub has to answer and the answer
picks the branch. Say so in the result rather than implying otherwise. Forking
both branches is not viable: `if` is a function here, so are `either`, `case`
and any user word that wraps them, and finding the branch points is the problem
being solved.

**The descriptions carry the doctrine, because they are the one text a model
cannot skip.** `run`'s carries the capability model and the six conclusions.
`match`'s carries DELECT against PARSE in one sentence. `word`'s carries the
warning that a clean run is not a correctness proof.

**The results carry more than the descriptions.** "Asked for FILES, not
granted" and "used a word with failing assertions in this build" arrive unbidden
at the moment they matter. The description sets the frame once; the results
correct the model every call.

No embed API in any of it. A caller reaching an MCP server is writing REBOL, not
Java, and sections 3 and 6 of `using-jebol.md` would be context spent on the
wrong reader.

#### What drops, given the audience

`help` inside the console, the banner, and most of the manual's prose. `help
parse` is a thing a person types mid-session. The banner tells someone stuck in
a REPL how to leave. And anything a model would infer correctly is wasted
tokens -- what earns space is only what contradicts the prior: `if 0` is true,
`none` and a Java null stay apart, there are six conclusions rather than two.

One thing to price before starting: error text becomes an interface. Reword it
later and whatever was built on the old wording breaks.

---

## Three loose ends, all flakes

**A flake is a fail** (see `CLAUDE.md`). None of these is closed.

### A memory test that measures the whole JVM

`SeriesMemoryFromTheSourceTest / Rebol's own assertion, run whole` failed once
under a full gate and has passed every time since -- alone, beside the suite
three times running, and in the gate after. It is not closed, and unlike the
two below it comes with a mechanism.

`stats` answers `SeriesMemory.bytesHeld()`, and that is a `static AtomicLong`
shared by every interpreter in the process. The assertion is

    all [
        stats >= (before + 5000000)
        none? held: none
        recycle
        (stats - before) < 2000
    ]

so it asks a process-wide counter to come back to within two thousand bytes of
where it started, across a window in which every other test in the same JVM is
allocating and freeing series of its own. Nothing about that is under the
test's control, and the tolerance is four hundred parts per million of the
figure it is checking.

It surfaced when the QOI tests arrived, which build 256x256 images -- a quarter
of a megabyte apiece, several times over. That is the occasion rather than the
cause: the fragility was already there and any test that allocates enough can
tip it.

Not narrowed and not skipped. Fixing it means either a per-interpreter figure,
which `SeriesMemory` has no notion of because it is a phantom-reference ledger
for the whole process, or running this one class in a JVM of its own. Both are
real decisions and neither is a line of code.

    ./gradlew cleanTest && ./gradlew check    # once in some number of runs

### The canonical reference is not deterministic on one file

`./r3-head` fails 13 assertions in `checksum-test.r3` in roughly one run in
eight, reproduced from cold in two unrelated directories with byte-identical
inputs and ruled out as a working-directory or concurrency effect. The failing
assertion is `--assert not open? close port`, so a real Rebol sometimes reports a
closed checksum port as still open.

    cd rebol3-source/src/tests && for i in 1 2 3 4 5 6 7 8; do \
      ../../../r3-head run-tests.r3 2>&1 | grep -c FAIL; done

This is a flake in the instrument every other answer here is settled with.
Nothing on `known-gaps.txt` depends on it today, and two audit passes still
disagreed with each other because one of them hit a bad run and did not re-run.
Nobody has read the C to find out why. Until someone does, ask `./r3-head` three
times whenever the answer surprises you.

### A server test that failed once

`WebScreenServerFromTheSourceTest` failed once, under a full parallel gate
run, with 404 where it wanted 204, and has passed every time since — twelve
isolated runs, 400 serial rounds, 960 concurrent rounds, and every gate run
after. It is an open failure and not a curiosity.

What has been done: the server now binds the loopback and names the address it
actually bound, rather than calling itself `localhost`, which resolves to two
addresses on a dual-stack machine and left the client to pick. That was the
one ambiguity findable by reading. The failing assertion now carries the whole
response instead of a bare status, so the next occurrence will say which
server answered and what it said.

It is not called fixed. If it recurs, the diagnostics are there.