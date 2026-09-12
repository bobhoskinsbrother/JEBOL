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
or in none. Every number below was checked on 2026-09-12 by running it.

---


## Where the port stands

| Measure | Reads |
| --- | --- |
| `scripts/c-parity.py` | 279 of 279 C functions match R3's surface -- a comparison of two *declaration files*, and read as broader than it is |
| `scripts/runtime-parity.py` | the same question asked of two *running* interpreters. Of 582 functions, **0 absent and 3 differ**, the same 3 in every column: `request-color`, `request-dir` and `request-file`, which JEBOL serves through its own port rather than shelling out to `osascript`. It began at 1 absent, 123, 581 and 430 |
| `PortingBacklogTest` | 0 of R3's 404 functions missing |
| `Interpreter.borrowedLoadFailures()` | empty -- every borrowed file loads whole |
| `system/catalog/datatypes` | 59 against R3's 58, the extra being `java-object!`, though `task!` is a name without an arm |
| `SuiteCoverageTest` | the reader reaches 10,133 of 10,133 assertions |
| `known-gaps.txt` | **182 fail**, and they are goals 1 to 8 below |
| `fails-on-rebol-too.txt` | 146 a real 3.22.5 also fails or never runs |
| `scripts/error-parity.py` | **81 of Rebol's 142 error ids can be raised. 61 cannot** |

`./gradlew check` is 17,750 tests, 0 failed, 0 skipped. An unread suite file
fails the build outright -- no list, no exception. `./gradlew browserCheck` is
the second gate and is not optional; it renders the same paint list in Java2D
and in a real Chrome and compares them pixel for pixel.

The 182 are broken into the first eight goals below. The rest own no entries:
they are equivalence the suite cannot see, the two security goals, and the
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

Goal 18 emptied it and the directory is gone. **A vendored file is a copy of
Rebol's and nothing else** — `everyVendoredFileIsUnchanged` fails on any
difference, and `noTestHasLostItsAssertions` catches the shape a cut assertion
leaves even without Rebol's checkout present. An assertion that should not be
graded goes in one of the two lists above, where the ratchet can see it.

### Every fix needs a JEBOL test

The suite is scaffolding and will be deleted when it goes green. A behaviour
fixed because of a suite assertion gets a test in `src/test/java` that stands
on its own: builds an interpreter, asserts on JEBOL, reads no `.r3` file.
Name it `*FromTheSourceTest`. Quote the C in the javadoc, and go past what the
suite checks — that is how a wrong reading gets caught that the suite would
have let through.

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

### 1. Two Java exceptions escaping to the top -- 7

`issue-test.r3`. These are not wrong answers but crashes, and a Java exception
reaching the interpreter's edge is a defect of a different kind:

    to-hex/size 1  0   -> java.lang.IllegalArgumentException: a word needs a spelling
    to-hex/size 1 -1   -> java.lang.StringIndexOutOfBoundsException: Range [17, 16)

`spec/embed.allium` says nothing a script does may reach the host as a
throwable. Both should be REBOL errors; ask `./r3-head` which.

---

### 2. What is left of the file ports -- 33

`port-test.r3`. The file and directory schemes work now
(`SeekableFilePort.java`); these are the remainder.

**Two stops, re-derived with the fixed `SuiteStops`.** This goal has
now said three different things about them, so here is the measured answer:

    pwd = to-rebol-file get-env "PWD"   -> ../ is outside what this port allows
    open %issue-2447                    -> a port that could not be opened

The first is not an environment problem, which is what it looked like while the
tool had no environment installed. `get-env "PWD"` answers; the working
directory it names sits outside the temporary root the harness confines the run
to, and the sandbox refuses to walk up to it. Decide what a rooted interpreter
should say its working directory is — that is the question, and it is not goal
8's.

The rest are wrong answers, so sweep the file: `scripts/sweep.py port-test.r3`.
23 are in "file port" and 14 in "directory port".

---

### 3. The checksum port -- 34

`checksum-test.r3` has one stop, and it is environment-dependent:
`file-checksum system/options/boot` — the boot path is outside the sandbox
root, so opening it fails. Decide what a sandboxed interpreter should say
about its own boot file; the honest answer may be that this assertion belongs
in `fails-on-rebol-too.txt` reasoning, or that `system/options/boot` should
name something inside the root.

**The other 28 are not "the Checksum port failing on its merits", which is what
this goal used to say.** They are the `xxh3` and `xxh128` blocks, which JEBOL
never enters because it has not got those algorithms — the same shape the
compression work had, and subject to the same correction: declaring them absent
retires nothing,
because an unreached assertion is scored as a failure.

**Do not settle a checksum-port question on a single `./r3-head` run.** The
canonical reference is not deterministic on this file. Running `checksum-test.r3` through
Rebol's own runner fails 13 assertions in roughly one run in eight, reproduced
from cold in two unrelated directories with byte-identical inputs; the failing
one is `--assert not open? close port`, so a real Rebol sometimes reports a
closed checksum port as still open. Nothing on `known-gaps.txt` depends on it
today, but two audit passes disagreed with each other because one hit a bad run.
Run it three times.

---

### 4. ENBASE, DEBASE and their parts -- 29

`enbase-test.r3`. One stop — `load` of bytes that are not valid UTF-8,
`#{B7D3}` — and then wrong answers.

One is already isolated and small: **`enbase/url` must not pad.**

    enbase/url "a" 64     ; r3 "YQ"    JEBOL "YQ=="
    enbase/url "ab" 64    ; r3 "YWI"   JEBOL "YWI="

The URL-safe alphabet leaves the padding off; the plain one keeps it. The
decoder already knows this (see `octetsOfBase64` in `Encodings.java`, which
lets a URL-safe group end short); the encoder does not.

"debase/part" is 21 of the 29 and is worth a look as one piece.

---

### 5. The elliptic curves -- 27

`dh-test.r3` stops at

    foreach ecurve system/catalog/elliptic-curves [...]

with "ecdh does not allow none! for its public-key argument". The catalogue is
not the problem — JEBOL lists all thirteen curves a real Rebol does, secp192r1
through curve448 — so `ecdh/init` is not answering a key for at least one of
them. `EllipticCurveKey.java` is the JEBOL side; the C is `n-crypt.c`.

**Two of the thirteen were added on 12 September 2026** because the TLS work
needed them: curve25519 and curve448, which are the first two entries of
the TLS scheme's `supported-groups` and so the ones a client hello reaches for.
They are a different shape from the rest and that is the part worth knowing —
they publish one coordinate on its own, 32 bytes and 56, with no lead byte
saying the point is uncompressed, because their arithmetic never needs the
second coordinate and there is no other way to write it. The JDK serves them
through `XDH` rather than `EC`, and the wire wants the coordinate
least-significant byte first where the JDK hands over a plain number.

Five are served now and eight are not. Measured against `./r3-head`, which
answers for all thirteen:

| served | not served |
| --- | --- |
| secp256r1, secp384r1, secp521r1, curve25519, curve448 | secp192r1, secp224r1, secp192k1, secp224k1, secp256k1, bp256r1, bp384r1, bp512r1 |

The Brainpool three were never in the JDK's default provider. The narrower NIST
and Koblitz curves were withdrawn from it — a JDK 16 change, not something
JEBOL chose — so serving them means carrying the curve parameters and the
arithmetic, or none of them answering, which is what the C already does for a
build without a curve.

---

### 6. Modules and IMPORT -- 12

`module-test.r3` stopped nine times, and every one came back to
`system/options/modules` being none.

**Both halves of that are fixed, on 12 September 2026, because the last of the
sweepable files needed them.** IMPORT looks in three places in order -- what is already loaded, a file
in the modules directory, and the address in `system/modules`, which it
downloads and saves -- and two of the three were unreachable here:

- **`system/options/modules` was none.** `sys-start.reb` writes it in one line,
  `modules: attempt [make-dir/deep join data %modules/]`, and nothing else
  decides it. It is set when a host installs a filesystem *and* the data
  directory is there. The second condition is JEBOL's own: the line above it in
  `sys-start.reb` makes the data directory, and JEBOL does not, because the path
  it defaults to is the operator's own and a confined filesystem reads that path
  as somewhere else entirely -- making it eagerly put a folder named after the
  operator's home inside every sandbox, which three tests of what a fresh
  directory contains noticed at once.
- **`system/modules` started empty.** `sysobj.reb` builds it as a table of
  addresses, and a module that loads replaces its own -- `repend system/modules
  [name module]` is the last thing LOAD-MODULE does, so the table is both the
  list of what may be fetched and the record of what has been. The table is put
  down before the library loads, for that reason.

Three of `module-test.r3`'s entries came off with it, and `import 'thru-cache`
now downloads, saves and imports.

**Two things were left out of the table on purpose**, and the reasoning is in
"A standing note: IMPORT fetches and evaluates code over the network" above.
The fourteen compiled extensions are gone because nothing here can load a
shared library -- each was a round trip to github ending in failure, and several
named things this build has anyway. The nineteen addresses for modules JEBOL
already vendors should go the same way and have not yet; that is the next piece
of this goal.

#### What is left

Twelve entries in `module-test.r3`, and they are about IMPORT itself rather than
about where a module comes from. Read them with `SuiteStops` before planning:
the nine stops this goal was written around are gone, so the list is a different
list now and has not been re-derived.

Two more defects turned up on the way and are fixed, both found by an IMPORT
that got further than before:

- **UPPERCASE and LOWERCASE took only a quoted string**, where the declaration
  says `string [any-string! char!]`. The module loader names a downloaded file
  with `lowercase second split-path source`, and SPLIT-PATH of a url answers a
  file.
- **TO-REAL-FILE dropped the trailing slash from a directory.** `OS_Real_Path`
  stats what it resolved and appends one -- the comment beside the line is the
  rule. Rebol's cache module opens with `join to-real-file any [get-env "TEMP"
  so/data] %thru-cache/`, and without the slash its whole cache went to a
  directory named by running two names together.

---

### 7. The PDF codec times out -- 9

`codecs-test-pdf.r3` runs past the harness's 5000ms limit on its first step.
Either the PDF codec is doing something quadratic or it is looping. Worth
finding out which before deciding what to do about it.

---

### 8. The scattered singles and pairs -- 31 across fourteen files

What is left when the others above are taken out:

    6  date-test.r3          3  mold-test.r3         1  bbcode-test.r3
    4  task-test.r3          2  bitset-test.r3       1  copy-test.r3
    3  error-test.r3         2  gob-test.r3          1  csv-test.r3
    3  struct-test.r3        2  percent-test.r3      1  datatype-test.r3
                                                     1  evaluation-test.r3
                                                     1  series-test.r3

Thirteen of the fourteen have no stop at all, so every entry is a wrong answer
and `scripts/sweep.py` will put the two answers side by side. That makes this
the cheapest goal per assertion on the list and a reasonable place to start
cold, because each one is small enough to hold in your head whole.

**The one stop**, re-derived with the fixed `SuiteStops`:

    task-test.r3   to task! [...]   -> cannot use to task! on block!

`task!` is a datatype word here without a datatype behind it, which is recorded
in goal 18 below and is a larger decision than four assertions warrant on its own.

---

### 9. The error catalogue: 61 ids cannot be raised

`too-long` is one of Rebol's error ids and JEBOL simply did not have it. That
was found by needing it, which is no way to find things, so the whole catalogue
was compared: **`src/boot/errors.reb` names 142 ids and JEBOL can raise 81.**

```
Access    25   ports, files, network, security -- areas JEBOL reaches through
               the host-grant system instead, so most of these have no arm
Script    16   the interesting column: behaviour JEBOL does implement and
               reports under a different id or not at all
Internal   8   memory and stack limits the JVM does not let us ask about
Syntax     4   bad-char, bad-checksum, bad-header, no-header
the rest   8   Note 3, Command 2, Throw 2, Math 1
```

The Script and Syntax columns are twenty ids naming behaviour that is already
here. `parse-series` is the one already known to matter: a get-word in a string
parse whose value is not a series should raise it, and JEBOL answers no-match.
`expect-type`, `bad-refine`, `no-return`, `type-limit` and `self-protected` are
the others worth reading the C for.

An id JEBOL cannot raise is not automatically a gap -- some of these are
raised nowhere in R3 either -- but the count is a measure that did not exist
before. `scripts/error-parity.py` prints it, so it is run rather than
remembered.

---

### 10. What reaching zero would not prove

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

### 11. What the suite does not ask

**The suite is the measure, and it is not the whole surface.** Running all 930
combinations of MAKE and TO against fifteen target types and thirty-one source
values, through JEBOL and through `./r3` side by side, found 140 answers that
differ. Rebol's own suite asserts most of those families and names them in
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

### 12. The 32 prelude forks

`prelude.reb` defines 36 words and Rebol defines 32 of them in `src/mezz` too:

```
all-of  any-of  body-of  cause-error  clean-path  clos  closure  collect
default  dirize  does  empty?  enum  funco  has  join  keys-of  map  max  min
rejoin  script?  spec-of  split-path  suffix?  title-of  to-word  types-of
undirize  values-of  words-of  wrap
```

Audit by identity rather than by datatype: for each one, is JEBOL's version
the same function, and if not, why was it forked?

---

### 13. Loose ends

**`task!` is a datatype word and not yet a datatype.** `task!` answers
`#(datatype!)` on both, but `make task! [1 + 1]` gives `#(task!)` on a real
Rebol and `cannot-use` here. It is the last one that is not really there.

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

**55 open questions across nine spec files**, the heaviest being
`natives.allium` with 19.

---

### 14. Graphics -- fourteen DRAW commands

**DRAW renders 22 of R3's 36 commands.** The fourteen it does not:

```
arrow  clip  gamma  grad-pen  image  image-filter  image-options
image-pattern  invert-matrix  line-pattern  spline  text  transform  triangle
```

`image` and `text` are the two that make pages look wrong rather than plain.

Also here: the stroked-curve comparison problem, the 522 lines of old markup
path, VID, Android, and the events-name-the-wrong-window one.

---

### 15. Code from outside is not authenticated -- the TLS client

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

### 16. Code from outside is not verified -- no checksum on a fetched module

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

**Goal 1 is the other half of this.** A checksum answers "is this the code I
expected"; authenticating the server answers "am I even talking to who I think".
Neither is being asked, and a fetch wants both.

Still to decide: whether a host should be able to refuse the fetch outright, the
way it refuses the filesystem and the network. A host serving untrusted scripts
wants that more than it wants either check.

---

### 17. The type-major refactor

**The original complaint, and much the largest piece left.** One `t-*.c` per
increment, bitset as the pilot.

The graphics work left a hint about the shape: `PaintInstruction` is sealed, so
adding a kind broke every renderer's switch at compile time. `VectorValue`
proved it again -- adding it to `SeriesValue permits` made the compiler
enumerate every arm that needed work. That is what the action seam wants.

---

### 18. The boot -- 343ms cold, 72ms warm

**343ms for the first interpreter, 72ms once the JVM has settled.** A
7900-test run pays the 72ms per class, and that is the floor rather than the
machine.

Pool first, then library caching. The series byte accounting behind STATS is
already in that allocation path, and it costs about 2ms of the 72.

---

### 19. LLM-friendly MCP tools

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