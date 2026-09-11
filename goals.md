# Goals

Rebol's own test suite is the measure of this port. What still fails is listed
one per line in `src/test/resources/rebol-suite/known-gaps.txt`, and this file
breaks that remaining work into goals that can be taken one at a time.

The sizes below are the only per-goal figures anywhere; the totals live in
`TODO.md` and are not repeated here. Both are snapshots, so check them rather
than believe them:

    wc -l < src/test/resources/rebol-suite/known-gaps.txt          # roughly
    grep -c '^[^#]' src/test/resources/rebol-suite/known-gaps.txt  # exactly

Take one, finish it, commit it, stop.

**Goals 16 to 21 come first.** They are not porting work; they correct faults in
the measure itself, found by an audit on 5 September 2026 that ran three
independent adversarial passes over this target. Until they are done, work
against `known-gaps.txt` can make JEBOL worse and be rewarded for it. Each
carries the command that reproduces the fault.

**16 to 20 are done, and 21 is down to three known differences that are all
the same deliberate decision.** `runtime-parity.py` reports 3 of 582 functions
differing, and they are `request-color`, `request-dir` and `request-file` in
every column. Two larger things are left inside 21 and named there with the
reason each was left: `near`/`where` on errors, and `compress` bytes.

The porting goals are **mostly, not entirely, independent**, and the couplings
are named in the goals themselves. Two that this file used to claim turned out
not to exist: goal 5 and goal 9 were both said to wait on goal 8, and neither
does — that came from a measuring tool inventing environment stops, and goal 20
has the story. The real ones now are goal 15 double-counting the eight crypt-port entries
that belonged to 4a and 4b. The rest are discharged: goal 3 is done, and so
are 4a and 4b, so goal 1's wait on them is over.

Goals 1, 6 and 14 are also one question asked three times: what the capability
catalogues claim is present. `system/codecs` is longer here than in a real
3.22.5, so JEBOL enters blocks a real Rebol skips and then raises inside them.
`system/catalog/ciphers` was the same fault seen from the other side — empty
where a real one holds forty-two — and goals 4a and 4b settled it: it now names
the thirty the port really serves, which is the shape the others should end up
in too.

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
goal 20 has what that cost.

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
3. **It rewards two changes that are wrong.** See goal 16.

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

## The goals

Sizes are the number of `known-gaps.txt` entries the goal is worth, and every
entry belongs to exactly one goal. **Re-derived from the list on 11 September
2026**, because the old figures had drifted: goal 11 was the worst at 135
against a real 132, and goal 15 claimed 40 where the files it owns hold 31.
Do not trust a size here that has not been re-derived since work landed —
`grep -c '^[^#]' src/test/resources/rebol-suite/known-gaps.txt` is the live
total and the table below is how it divides.

    352   the whole list
    ---
    132   11. sweepable files that run clean
     20    2. image
     36    5. the file ports
     34    6. the checksum port
     31   15. the scattered singles and pairs
     29    7. enbase and debase
     27   10. the elliptic curves
     17    9. modules and import
     10    1. what is left of the codecs, which is not codecs
      9   14. the pdf codec
      7   12. Java exceptions escaping to the top

Goals 3, 4a, 4b, 8, 13 and 16 to 21 are done and own nothing. To re-derive the
table, count the list by file and read each goal for which files it names:

    sed 's| /.*||' src/test/resources/rebol-suite/known-gaps.txt | sort | uniq -c | sort -rn

**Whether a size is a floor or a ceiling is unsettled, and it matters.** This
file used to say floor, reasoning that fixing a stop frees the assertions
standing behind it. Two independent audit passes disagreed with each other: one
confirmed the reasoning, the other found that the harness already scores an
unreached assertion as a failure, which would make the size a ceiling and mean
the count can only fall. Nobody has settled it. Until someone does, treat a size
as an estimate and expect surprises in both directions — and if you fix a stop
and the count goes up, that is the answer, so write it down here.

### 16. Stop the ratchet rewarding wrong answers — DONE

`--red--` is a harness word now, the mark reaches the assertion beside it, and
`assertionsExpectedToPass` honours it. `noRedOnlyAssertionIsAGap` fails if a
red-marked assertion ever appears on the gap list again. The eight
`power-test.r3` entries came off; the three that were plain misfilings moved to
`fails-on-rebol-too.txt` with the `r3-head` session that settles each.

What follows is why, kept because the reasoning is the part worth having.

**Eleven lines of `known-gaps.txt` name assertions where JEBOL already answers
exactly what `./r3-head` answers.** Working them means moving JEBOL away from
Rebol, and the ratchet will turn green when you do. This is the one goal that
gets worse the longer it is left, because anybody picking up goal 11 walks into
it.

Eight are `power-test.r3 #11`-`#18`, and they come off with one line:

    --test-- "power-integer-1"  --red-- --assert integer? power 2 16

`power 2 16` is `65536.0` in Rebol, so that assertion is false there too.
`--red--` is Rebol's own marker: in `rebol3-source/src/tests/quick-test-module.r3`
it binds to `as-red-only`, which sets `qt-red-only`, and a failing assertion under
that flag is counted as "not like Red" rather than as a failure. JEBOL's prelude
binds it to `does []` and grades the assertion anyway.

`RebolSuiteTest.java` around line 344 documents the choice and gets it backwards:
it argues that judging strictly "can only ever name a gap that is really there".
It cannot, because the strict reading demands behaviour Rebol has not got. Bind
`--red--` the way Rebol does, and the eight lines go.

The other three are ordinary misfilings, checked one at a time against the
canonical reference:

    make-test.r3 #498, #533   error? try [make map! quote (1 2)]  -> #(false) both sides
    module-test.r3 #18        both sides answer _

Reproduce any of them:

    printf 'Rebol []\nprint mold power 2 16\nprint mold error? try [make map! quote (1 2)]\n' > /tmp/p.r3
    ./r3-head /tmp/p.r3

More are suspected in `port-test.r3` and `codecs-test.r3` and could not be
confirmed, for a structural reason worth knowing: in a file where the canonical
reference performs fewer assertions than the file writes, the ordinals cannot be
lined up, so there is no way to say which listed entry corresponds to which of
its failures.
Five separate counts of "how many are misfiled" came back as 8, 10, 13, 14 and
17 for exactly this reason. **Eleven is the confirmed floor, not the answer.**

### 17. Give the second list a ratchet — DONE

`theFindingsListHasNoPassingEntries` mirrors the gap-list ratchet onto
`fails-on-rebol-too.txt`. It cost nothing to run: those assertions were already
being executed, just filtered out of the expected-to-pass set, so the verdicts
were there unused. Both lists are now held the same way.

What follows is why.

`fails-on-rebol-too.txt` has none. `theGapListHasNoPassingEntries` reads
`knownGaps()` only, `theTwoListsDoNotOverlap` catches a copy but not a move, and
`theGapListNamesRealAssertions` never asks whether an entry has started passing.

Demonstrated rather than reasoned: moving `bitset-test.r3 #139` — which a real
Rebol passes and JEBOL fails — out of `known-gaps.txt` and into
`fails-on-rebol-too.txt` drops the published backlog by one and leaves the build
green.

Mirror the existing test onto the second list. Both files are then held the same
way, and the only route left for the number to fall without work is closed.

### 18. Re-run the exclusion ledger and empty it — DONE

All 33 assertions are back in the files Rebol wrote them in, and **every one of
the 67 vendored files is now byte-identical to `rebol3-source`**. The ledger
directory is gone.

How they landed, each checked against `./r3-head` one at a time:

    24  pass here and cost nothing         distance 9, as-color 5, factorial 6,
                                           compare 2, object 1, load 1
     5  fail on a real 3.22.5 too          -> fails-on-rebol-too.txt with the session
     4  are real gaps                      -> known-gaps.txt

Every reason in the ledger had gone stale, which is the part worth remembering.
`distance`, `as-color` and `factorial` were excluded as "not in this build of R3
at all" and all three are present in 3.22.5. Three more needed files under
`units/` that had "never been vendored" and have been since. The verdicts were
right when written against 3.22.1 and nothing re-asked them.

Restoring shifted the ordinals of everything below each insertion, which broke
ten gap-list entries: `evaluation-test` "do needs" moved by 3, and nine
`parse-test` entries by 3. The ratchet caught both halves of that on its own —
three entries suddenly "passing" and three failures with no entry — which is
what it is for.

Two guards now hold it, in `SuiteSelectionTest`:

- `everyVendoredFileIsUnchanged` compares each file with upstream byte for byte.
  It needs the `rebol3-source` symlink, so it cannot be the only one.
- `noTestHasLostItsAssertions` reads the vendored text alone and fails on a
  `--test--` with the next dialect word straight after it, which is exactly what
  a cut assertion leaves behind. Six tests are empty upstream too, with Rebol's
  own note saying why, and they are named in the test rather than pattern-matched.

### 19. `do %anyfile` is broken — DONE

`DO` of a file, a URL, a string or a binary is not written in C: `n-control.c`
sends all four to `Do_Sys_Func(SYS_CTX_DO_P, ...)`, which is `sys/do*` in the
borrowed `sys-base.reb`. JEBOL matched a file against the Java `StringValue`
case, because a file is one, and evaluated the file's own name as source. File
and URL now go to `do*`; string and binary keep their routes, because a string
is source rather than a script.

Routing them there took three defects with it, each found by the next one
failing:

1. **`unprotect/words` ignored paths.** `natives.reb` says
   `/words "Process list as words (and path words)"`, and the parenthetical was
   the whole of it: an entry that was not a bare word was skipped, silently,
   because the call answers the block it was given either way. Rebol's
   `protect-system` protects every word of SYSTEM and then hands back the few a
   script must write, with `unprotect/words [system/script]`. With that ignored,
   `do*` could not record the script it was about to run.
2. **A sys file could not call a helper defined in a later sys file.** Each was
   bound as it loaded, so `do-needs` in sys-load.reb did not exist when
   sys-base.reb was bound and the word never resolved. R3 has no such ordering
   because its sys context is built from a boot list first. JEBOL now declares
   every sys file's set-words before binding any of them.
3. **`prot-mysql.reb` loaded after the system object was sealed.** Once
   `protect/words` worked, `protect/words/deep [system/catalog]` really did
   protect it, and the file's closing `put system/catalog/errors 'MySQL ...`
   stopped. Rebol never meets this: it imports that file on demand long after
   boot. It now loads before `mezz-tail.reb` rather than after.

Six gap entries came off, including all three `evaluation-test` ones. The
fourth assertion in that group — `error? try [do %units/files/error.r3]` — was
green because the feature was broken, wanting an error and getting the wrong
one; it now passes for the right reason, raising `zero-divide` from inside the
script.

`lexer-test.r3 / Special tests / NULLs inside loaded string #452` is still a
gap. It is in the same file-and-process corner but a different defect: it loads
a 40,000-character string through a subprocess and checks the buffer survives
being extended.

### 20. Fix the measuring tools before trusting them — DONE

`SuiteStops` and `SweepRunner` each granted every `HostService` and installed
only a filesystem, where the gate also installs `useEnvironment` and
`useProcesses`. Granting a service is not providing one, so both reported stops
the gate never sees, every one reading "given no environment to read" or "given
no way to start a program". `SweepRunner`'s own comment claimed it "grants what
the harness grants".

There is now one definition of the interpreter a suite file runs in,
`SuiteHost`, and the gate and both tools call it. Copying it a fourth time is
the mistake that made this, so the arrangement matters more than the two lines:
a capability added there reaches all three at once. `SuiteHostTest` asks the
three things granting alone does not give, and it is not decoration — removing
those two lines again turns three of its five red.

**What the fixed tool then said**, which is not what the old one said and not
what this file said either:

    port-test.r3         2 stops, not three and not none
    module-test.r3       9 stops, none of them the environment or CALL
    os-test.r3           7 stops, all real
    evaluation-test.r3   none at all

Goals 5, 8, 9 and 15 are corrected in place from that, and the corrections are
measurements rather than readings. Goal 5 had said three stops, then none; it is
two, and the first is the sandbox refusing to walk up to a working directory
outside its root rather than anything about the environment.

**`scripts/c-parity.py` could not fail on the defects that matter, and now
there is a measure that can.** It compares two *files*, so a clean report from
it is true and narrow — and it was read as broad. `scripts/runtime-parity.py`
asks two running interpreters the questions a declaration cannot answer:

    ./gradlew compileTestJava
    python3 scripts/runtime-parity.py          # summary
    python3 scripts/runtime-parity.py --all    # every differing function

The function list comes from `./r3-head`, so it is Rebol's list rather than
JEBOL's and a missing function shows as ABSENT instead of dropping out of both
sides. What it says today is goal 21, with numbers rather than examples:

    582 functions asked
      1 absent from JEBOL                         `|`
    123 report a different datatype               120 action! -> native!,
                                                  3 function! -> native!
    581 answer words-of differently               JEBOL gives none for all but one
    430 answer a different spec-of length

`c-parity.py` now says in its own header what it cannot see, and points here.
Quote the two together or neither.

### 21. What reaching zero would not prove — mostly DONE

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
   goal 3 made the headers agree, because a level nobody asked for is now the
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

### 1. The remaining codecs — 109, now 10, and they are not codecs

`codecs-test.r3`. Was the largest single file, and it was not one problem but
about eleven, each an `if find codecs 'name [...]` block that raised and took
its whole group with it.

Almost none of them turned out to be codecs to port. The DER, CRT, PLIST, ZIP
and MIME blocks were all interpreter defects surfacing inside Rebol's own
borrowed REBOL codec files: the binary dialect resolving a get-word too early,
a get-path rebinding what it read, ENHEX answering the wrong datatype, PARSE's
insert, and `enbase/part` counting bytes where it should count characters. The
image blocks needed a port rather than a codec, and QOI needed writing. The SWF
block fell out of the binary dialect's `VINT`, which is goal 13.

The WAV block turned out not to be work at all. Its two checksums are stale and
a real 3.22.5 answers exactly what JEBOL answers, so they are on
`fails-on-rebol-too.txt` with the session that settles it and
`WavCodecFromTheSourceTest` carries the real numbers. Finding 24 in
`docs/rebol-findings.md` explains why nobody noticed: the WAV codec is a
delayed module and the block's `if find codecs 'wav` guard has been false since
the sound data stopped being a raw binary.

**What is left is ten entries that are not codec work and should not be filed
here.** The crypt port cleared four of the thirteen SAFE entries; the rest need
SET-USER, `system/user/data` and a user's storage file — REBOL's own idea of a
logged-in user with an encrypted store, which nothing in this file describes.
They stay under goal 1 only because nobody has written the goal they belong to.

One of the ten is worth a look before it is filed with the other nine.
Assertion #222 answers false inside the suite while the same round trip is
byte-identical to `./r3-head` when run on its own, so it may be a harness or
ordering problem rather than a missing feature.

Note that the group names in `known-gaps.txt` are wrong for this file — the
slicer takes the last top-level `===start-group===`, and this file nests its
groups inside the `if` blocks, so the entries all claim to be in "TEXT codec".
They are not.

### 2. Image, read and written — 51, now 20

`image-test.r3` runs to the end with no stops, so every one of these is a
wrong answer and `scripts/sweep.py image-test.r3` will show them in pairs.
**"Image as a series" is done — all 31.** APPEND, INSERT, CHANGE, FIND and
REPEAT all refused an image, which made it a series that could not be used as
one. `ImageSeries.java` is the one place that knows what counts as a pixel;
the natives gained an arm each.

The part worth carrying forward is the height. It is not stored: it is how
many whole rows the pixels make, so three pixels in an image two wide are a
row and a spare, the size says 2x1 and the length says 3. The spare is really
there and the next pixel appended lifts the height. `ImageStorage` already
worked that way; nothing above it did.

Two things the canonical reference settled that reading would not have.
FIND's /ONLY drops the alpha from the comparison, so a four-part tuple matches
a pixel whose alpha differs — the same "look at the thing, not into it" that
/ONLY means everywhere. And CHANGE does not lengthen an image: pixels past the
end are dropped, because the width is fixed and a longer image would be a
different shape.

What is left, all of it wrong answers rather than refusals:

     8  Image difference
     5  change image
     3  BLUR
     2  RGB - HSV conversions
     1  construct image
     1  Save/load image

The C is `rebol3-source/src/core/t-image.c`. JEBOL's side is
`src/main/java/org/jebol/domain/eval/ImagePath.java` and the image branches of
`Natives.java`.

### 3. LZMA and Brotli — 45 — DONE

`compress-test.r3` no longer has a single line on `known-gaps.txt`. Both
algorithms are ported, and the port is byte-exact against `./r3-head` rather
than merely round-tripping with itself.

**What was actually needed, against what this file predicted.** It said the
honest size was 38 assertions that could not be cleared any other way, and that
the other seven were incidental. Both halves were right, and the seven turned
out to be two faults rather than seven:

- A negative `/part` was clamped to zero instead of reading the span behind the
  position. `Partial1` turns the count round; five of the seven asked for
  `compress/part tail data 'zlib -4`, once per algorithm group.
- GZIP threw the level away and always compressed at the default, so the two
  assertions that quote level-zero bytes could not hold. The header's ninth and
  tenth bytes were wrong too, which nothing asserted.

**LZMA is byte-exact at every level.** `u-lzma.c` is the LZMA SDK of 2017-06-10,
and the parts Rebol can reach are ported whole: the binary-tree and hash-chain
match finders, the priced parse used from level five, the greedy one used below
it, and the range coder. Verified on ten inputs from nothing to eighty-six
kilobytes across all eleven levels, comparing the compressed bytes and the
decompressed content with a real 3.22.5 in both directions. The dictionary is
sized at whichever is smaller of the level's window and the data, which the C
does not do and which changes no answer -- without it, compressing fourteen
bytes at the default level allocates a hundred and thirty-four megabytes.

**Brotli is complete: all twelve levels, byte-exact.** The decoder is RFC 7932
in full. The encoder is every level from zero to eleven, verified against a real
3.22.5 on Brotli's own test corpus -- twenty-four files including a twelve
megabyte one -- plus a four megabyte source tree, three megabytes of noise, and
about three thousand generated inputs across eight data shapes.

Levels two to nine are the generic path: four hash tables, the look-ahead that
puts off a match when the next byte offers a better one, the dictionary search
on the encoder side, the greedy block splitter over three alphabets, the choice
between one, two, three and thirteen literal contexts, and the meta-block writer
in its three forms.

Levels ten and eleven are a different algorithm again. They keep every match at
every position rather than the best one, price each in bits, and choose the
cheapest run of commands through the whole block; eleven then does it a second
time with prices measured from what the first pass wrote. They also choose how
distances are split between code and extra bits per meta-block, divide each
alphabet by pricing rather than greedily, and give every literal block type
sixty-four histograms which are then merged back down.

Three test files cover it, one per group of levels.

**Three surprises, all recorded because the sizes in this file are estimates.**

1. **The count fell by more than 45.** Three assertions in `codecs-test.r3` came
   off with LZMA: `load %units/files/test2-lzma.swf` and the two that compare it
   with the deflate SWF beside it. Nothing predicted that; the SWF codec is
   Rebol's own mezz and it simply started working.
2. **Two more assertions moved to `fails-on-rebol-too.txt`, and they are the
   trap this file warned about in the other direction.** `SAVE/compress` quotes
   a zlib stream opening `78 9C`; a real 3.22.5 answers `78 DA`, because
   libdeflate given no level reads that as its highest. Fixing JEBOL's default
   to the slowest -- which is right, and makes the header match -- broke two
   assertions that had been passing for the wrong reason. Item 2 of goal 21 is
   narrower than it was: the header bytes now agree and only the deflate body
   differs.
3. **Both `feature-na` guards fired, exactly as predicted.** `#62` for Brotli
   and `#83` for LZMA are the "this build has not got it" assertions, and a
   build that has got it never runs them. Both are in
   `fails-on-rebol-too.txt` with the `./r3-head` output that settles it.

**Two faults a round trip would never have found**, both caught by comparing
bytes with the canonical reference and worth repeating wherever the next compressor is
ported:

- LZMA's reversed bit trees are walked one way in the decoder and another in
  the encoder if you transcribe the optimised macro literally. The two agree
  for the first two bits and part company on the third, so short data round
  trips perfectly and anything longer comes back subtly wrong.
- Brotli's two prefix-code builders live in different files and each has a
  `static SortHuffmanTree` of its own. They differ by one line: the one that
  sorts a literal code does not break a tie between equal counts, the one that
  sorts a command code does. Using the wrong one gives a code of the same shape
  with two symbols swapped -- valid Brotli, decodes perfectly, wrong bytes.

**And one the decoder had been carrying all along.** A prefix code over code
lengths may have a single symbol in it, and such a code is read by spending no
bits at all -- there is nothing to tell apart. Read as an ordinary prefix code
it spends a bit, and everything after it is then read at the wrong offset. The
decoder had been reported as checked against 132 real streams at all twelve
levels; every one of them was short, and the first stream that needs the special
case is about fifty kilobytes. Writing levels two to nine produced such a stream
within an hour and the round-trip test caught it. Counting samples is not
coverage, and the rule that came out of it is in `CLAUDE.md`.

**Two more the arithmetic was carrying.** Both were found by the top two levels
and both had been quietly wrong for every level above three.

The C's table of logarithms for the first 256 whole numbers is an array of
double whose every literal is written with an `f` on the end, so the compiler
rounds each to float and only then widens it. Two hundred and forty-seven of the
two hundred and fifty-six therefore differ from the true logarithm by about one
part in ten million. This port had computed them instead. Nothing below level
ten noticed, because the numbers those feed are compared against thresholds
hundreds of bits wide; levels ten and eleven compare costs that differ in their
last bit, and were wrong on any input over about forty kilobytes.

Java has no `log2`, and `log(v) / log(2)` disagrees with the C on twenty-three
of every hundred whole numbers. `BrotliLog2` computes it in pairs of doubles
instead. It agrees with the C on all but a hundred and sixty values in three
million -- and on those hundred and sixty the C is the one that is wrong, being
a last bit away from the correctly rounded answer. Which means byte-exactness
with "a real 3.22.5" is, in principle, platform-dependent on values whose
logarithm falls within a hair of halfway between two doubles. It has never come
up in practice and there is nothing to be done about it if it does.

**And a lesson about whose test data to use.** Levels ten and eleven passed
every input this project could invent -- text, noise, repetition, mixtures,
three thousand fuzz cases -- with a constant wrong that makes level eleven
divide its blocks three times where the C divides them ten. Three files in
Brotli's own corpus catch it. Thirty kilobytes of one of them is now in
`src/test/resources/brotli/`, because nothing generated here does the job. The
twelve megabyte file in the same corpus then caught two more: an array sized to
the C's starting capacity rather than what it grows to, and a distance setting
that must not carry from one meta-block to the next.

### 4a. The crypt port, on the ciphers the JVM already has — 16 — DONE, and it was 28

`crypt-port-test.r3`, `crypt-port-gcm-test.r3` and four lines of `codecs-test.r3`
have come off the list: 435 entries down to 407. Twelve more than the estimate,
because the file's own chunked-input tests sit outside the `foreach` that the
catalogue guards and ran as soon as the port existed.

**Three things were built, not one.** `make port!` now hands its specification
to `sys/make-port*` like `MT_Port` does, so a block, a url, a word, a file, an
object and a port all make one; what is no specification at all answers
`invalid-spec` and a specification whose scheme nobody serves answers
`no-scheme`. `system/catalog/ciphers` holds the seventeen the JVM can be made to carry.
`CryptPort.java` is the port, and `Interpreter.THE_CRYPT_SCHEME` quotes the
scheme's INIT out of `init-schemes` so the three ways of naming an algorithm
stay REBOL's.

**Two tests were asserting something a real Rebol refuses.**
`make port! system/standard/port` used to answer a port here because MAKE
wrapped an object; a real 3.22.5 raises `no-scheme`, because the object names
none. Both now say `to port!`, which is the spelling that wraps.

**Weeding the spec afterwards found three defects fifty passing tests had
missed**, and they are the reason to run it rather than treat it as a
formality. Opening never re-checked the algorithm, so a port closed, given an
unserved algorithm in its specification and reopened would open with no cipher
behind it and take a NullPointerException to the top of the interpreter on the
next write. Galois counter mode put the tag in place of the cipher text instead
of after it, so a TAKE with no READ before it lost the message — invisible
because REBOL's own test always reads first. And a second write to a Galois
port re-enciphered everything gathered so far, handing the first bytes back
twice. All three are fixed, tested, and were confirmed against `./r3-head`
before and after. Two more gap entries came off with them.

**The SAFE block did not clear the way this goal predicted.** The crypt port
unblocked its encryption and four of the thirteen pass, but the ninth assertion
onwards needs SET-USER, `system/user/data` and a user's storage file, which is
neither 4a nor 4b. `codecs-test.r3` owes ten entries and they are a user-account
goal nobody has written down yet. One of them, #222, answers false inside the
suite while the same round trip is byte-identical to `./r3-head` when run on its
own — worth a look before assuming it belongs with the other nine.

### 4a as it was scoped, kept because 4b still leans on it

`crypt-port-test.r3` used to stop at its first line:

    port: open make port! [scheme: 'crypt algorithm: 'AES-128-CBC key: #{...}]

`make port!` of a block was refused, and there was no `crypt` scheme.
Everything after that was `port is unset`. That one blocker stood in front of
all forty assertions in the file, and in front of the eight in the three files
beside it, which is why building the port was the whole of this goal and 4b is
only about the algorithms underneath it.

**Which 16.** The AES vectors, which is everything in the two files that is not
Camellia and not CCM:

    11  crypt-port-test.r3   FIPS-197 test vectors
     2  crypt-port-test.r3   NIST test vectors (ECB)
     1  crypt-port-test.r3   NIST test vectors (CBC)
     2  crypt-port-gcm-test.r3

**And thirteen more in goal 1 behind it.** `codec-safe.reb` opens a crypt port
by hand to encrypt what it saves, which is why the thirteen SAFE entries left
in goal 1 all stop at `no-scheme: crypt`. It picks its cipher by preference and
asks for `chacha20` first, which the JVM has, so they clear with this half
rather than with 4b. Nothing else in the suite is waiting on either. *(Four of
the thirteen cleared. See the note above: the rest want SET-USER.)*

**`system/catalog/ciphers` was empty here and holds 42 entries in a real
Rebol** — AES, Camellia and ARIA each in ECB, CBC, CCM and GCM at three key
widths, then ChaCha20, ChaCha20-Poly1305 and four DES spellings. Filling it was
part of this goal and it is what makes the split legible: a cipher in the
catalogue that the port cannot serve is the failure mode to avoid, so it names
only what is served and lets the rest say so. **4b's job is to move names
across that line.**

**What the JVM gives you, measured rather than assumed.** AES in ECB, CBC and
GCM, ChaCha20, ChaCha20-Poly1305, DES and 3DES are all one
`Cipher.getInstance` away. That is 18 of the 42, and fourteen of them went in
the catalogue: ChaCha20-Poly1305 is left out because REBOL drives it through a
two-step protocol of its own that does not map to a JVM AEAD, and it has no
assertion behind it here. The three AES-CCM entries came later, from 4b, and
are built rather than found — so the catalogue is seventeen.

**Galois counter mode needed a trick worth knowing.** The port hands the
computed tag back for the caller to compare; the JVM compares it itself while
decrypting and throws when it disagrees, so it will not hand one back. Counter
mode is its own inverse, so enciphering the cipher text recovers the plain
text, and enciphering *that* produces the tag the caller is owed. Two passes,
no hand-written cryptography. Tags are always computed at sixteen bytes and cut
down, because the JVM's parameter object refuses anything under twelve and
REBOL's own tests ask for four.

The pattern followed is `ChecksumPort.java` and the checksum scheme in
`Interpreter.java`: a scheme registered through the borrowed `sys/make-scheme`,
an actor name in `SCHEMES_THIS_BUILD_SERVES`, and open/read/write/update/close
branches in `Natives.java`.

### 4b. The ciphers the JVM has not got — 32 — DONE

Every crypt-port file is off the list. `crypt-port-test.r3`,
`crypt-port-camelia-test.r3`, `crypt-port-ccm-test.r3` and
`crypt-port-gcm-test.r3` owe nothing between them, and
`system/catalog/ciphers` names all forty-two a real 3.22.5 does, in the same
order.

**Four things were written out, because no JVM provider has them.** Counter
with CBC-MAC is a mode over a block cipher, so it came first and cheapest.
Camellia and ARIA are whole block ciphers and each arrived as twelve catalogue
entries at once. ChaCha20 with Poly1305 is none of those — an authenticator
built on arithmetic modulo a prime, joined to a stream cipher — and it was the
only one with a consumer: `prot-tls.reb` names it first when it builds its
cipher suites.

**Counting with Galois moved off the JVM along the way.** The JVM checks the
tag itself while deciphering and will not hand one back, where this port hands
it to the caller to compare, so AES-GCM had been deciphering twice over.
`CounterWithGalois` does it in one pass and serves Camellia too.

**ARIA is done too, and it closed the catalogue.** Twelve more entries with no
assertion anywhere behind them -- it was the cheapest cipher left and the least
useful, and it went in because a catalogue that names forty-two and serves
thirty is the same fault this project keeps finding elsewhere.
`system/catalog/ciphers` now matches a real 3.22.5 in length and in order.

It was the third cipher through the same seam, and it needed no mode written
for it: `Aria.java` is the algorithm and the twelve pairings arrived together.
Its key schedule is the fiddlier of the two written out here -- four derived
words rotated by four different amounts across a hundred and twenty-eight
bits, in the opposite byte order from the one they are stored in -- so the
thousand-round walk earns its keep more here than anywhere.

---

#### The package, which is the part worth keeping

`org.jebol.domain.cipher` holds the idea that made twelve entries arrive for
one cipher. `OneBlock` is a block cipher and nothing else, sixteen bytes in and
sixteen out; `BlockModes`, `CounterWithCbcMac` and `CounterWithGalois` take one
and cannot tell whether it came from the JVM or from `Camellia`. `Camellia` and `Aria` are the two block ciphers
written out; `Poly1305` and `ChaChaWithPoly1305` sit beside them because that
pairing is not a block cipher at all.

`CryptPort` stays in `eval` because it is a port rather than a cipher. The one
place the families differ is `theBlockCipherBehind`, and the one place a
protocol differs is the header: two modes read it from the front of a write
told apart by a length, and ChaCha20-Poly1305 takes a whole write and derives
its nonce from it.

### 5. What is left of the file ports — 36

`port-test.r3`. The file and directory schemes work now
(`SeekableFilePort.java`); these are the remainder.

**Two stops, re-derived with the fixed `SuiteStops` (goal 20).** This goal has
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

### 6. The checksum port — 34

`checksum-test.r3` has one stop, and it is environment-dependent:
`file-checksum system/options/boot` — the boot path is outside the sandbox
root, so opening it fails. Decide what a sandboxed interpreter should say
about its own boot file; the honest answer may be that this assertion belongs
in `fails-on-rebol-too.txt` reasoning, or that `system/options/boot` should
name something inside the root.

**The other 28 are not "the Checksum port failing on its merits", which is what
this goal used to say.** They are the `xxh3` and `xxh128` blocks, which JEBOL
never enters because it has not got those algorithms — the same shape as goal 3,
and subject to the same correction: declaring them absent retires nothing,
because an unreached assertion is scored as a failure.

**Do not settle a checksum-port question on a single `./r3-head` run.** The
canonical reference is not deterministic on this file. Running `checksum-test.r3` through
Rebol's own runner fails 13 assertions in roughly one run in eight, reproduced
from cold in two unrelated directories with byte-identical inputs; the failing
one is `--assert not open? close port`, so a real Rebol sometimes reports a
closed checksum port as still open. Nothing on `known-gaps.txt` depends on it
today, but two audit passes disagreed with each other because one hit a bad run.
Run it three times.

### 7. ENBASE, DEBASE and their parts — 29

`enbase-test.r3`. One stop — `load` of bytes that are not valid UTF-8,
`#{B7D3}` — and then wrong answers.

One is already isolated and small: **`enbase/url` must not pad.**

    enbase/url "a" 64     ; r3 "YQ"    JEBOL "YQ=="
    enbase/url "ab" 64    ; r3 "YWI"   JEBOL "YWI="

The URL-safe alphabet leaves the padding off; the plain one keeps it. The
decoder already knows this (see `octetsOfBase64` in `Encodings.java`, which
lets a URL-safe group end short); the encoder does not.

"debase/part" is 21 of the 29 and is worth a look as one piece.

### 8. The environment — 9 — DONE

`os-test.r3` has no line left on `known-gaps.txt`. The "unblocks others" in the
old title was wrong — it came from `SuiteStops` reporting stops in
`port-test.r3` and `module-test.r3` that do not happen under the real gate. See
goal 20.

Both causes were real and both are fixed. The three environment natives take a
`word!` as well as a `string!`, like a real Rebol. And SET-ENV exists: the old
refusal said "a JVM cannot change its own environment", which is true of the
process and beside the point — what a script means by setting a variable is
that GET-ENV answers it afterwards and a child process sees it, and a JVM can
do both. `ProcessEnvironment` keeps a per-port overlay over the host's names,
and `JavaProcesses` hands the whole view to a child rather than letting it
inherit, so a name the script took away is missing from the child too.

### 9. Modules and IMPORT — 17

`module-test.r3` stops nine times, and every one of them comes back to
`system/options/modules` being none:

    write modules-dir/mymodule.reb {...}  -> cannot select word! from none!
    import mymodule                        -> cannot select file! from none!
    same? lib-local system/contexts/user   -> lib-local has no value

**The stated cause was wrong twice over.** This goal used to say the environment
is unreadable under the sandbox so `sys-start.reb` cannot work the directory
out, and that this therefore depends on goal 8. Re-derived with the fixed
`SuiteStops` (goal 20): `get-env "HOME"` answers, `call/shell/wait` runs, and
none of the nine stops is about either. Something else leaves
`system/options/modules` unset — start by reading what `sys-start.reb` does with
it and finding which step does not happen here.

After that: `import`, which is the substance of the goal.

### 10. The elliptic curves — 27

`dh-test.r3` stops at

    foreach ecurve system/catalog/elliptic-curves [...]

with "ecdh does not allow none! for its public-key argument". The catalogue is
not the problem — JEBOL lists all thirteen curves a real Rebol does, secp192r1
through curve448 — so `ecdh/init` is not answering a key for at least one of
them. `EllipticCurveKey.java` is the JEBOL side; the C is `n-crypt.c`.

### 11. Sweepable files that run clean — 132 between them

None of these stops, so every entry is a wrong answer and `scripts/sweep.py`
will show it side by side with a real Rebol's. Small enough to take in one
sitting each, and the cheapest work on the list per assertion:

| file | entries | note |
| --- | --- | --- |
| `func-test.r3` | 29 | 15 "Other issues", 14 "OP!" |
| `unicode-test.r3` | 21 | one stop: `repeat` refuses a `string!` count |
| `time-test.r3` | 15 | all in "time" |
| `map-test.r3` | 12 | 10 are "set operations with map!" |
| `make-test.r3` | 10 | |
| `file-test.r3` | 9 | one stop: `read file://temp.txt` — the `file://` URL scheme |
| `thru-cache-test.r3` | 10 | |
| `parse-test.r3` | 9 | "Other parse issues" |
| `vector-test.r3` | 9 | |
| `lexer-test.r3` | 8 | one is `NULLs inside loaded string`, which loads through a subprocess |

`power-test.r3` used to be a row here with eight entries, and working it would
have made JEBOL disagree with the canonical reference. Goal 16 took them off: they are
`--red--` assertions that a real Rebol fails too. Two `make-test.r3` entries
went the same way.

### 12. Two Java exceptions escaping to the top — 7

`issue-test.r3`. These are not wrong answers but crashes, and a Java exception
reaching the interpreter's edge is a defect of a different kind:

    to-hex/size 1  0   -> java.lang.IllegalArgumentException: a word needs a spelling
    to-hex/size 1 -1   -> java.lang.StringIndexOutOfBoundsException: Range [17, 16)

`spec/embed.allium` says nothing a script does may reach the host as a
throwable. Both should be REBOL errors; ask `./r3-head` which.

### 13. The binary dialect's missing keywords — 8 — DONE

`bincode-test.r3` has no line left on `known-gaps.txt`. `EncodedU32`,
`EncodedU64`, `VINT` and `SKIPBITS` are ported; `MSDOS-DATETIME` was already
there and failed for a different reason than the other four.

That reason is worth keeping. Rebol stores a date in UTC and puts the offset
back on only to mold it or to answer a path, so `u-bincode.c` reading the year,
month, day and clock straight out of the struct gets the instant for free.
JEBOL keeps the time as it was written, so a date carrying an offset went in as
the wall time and every MS-DOS field came out an hour wrong. `DateValue`
answers `asStoredInUtc` now and `DateParts` uses the same one for `/utc`.
Written up as finding 21 in `docs/rebol-findings.md`.

Boundary work around the four new keywords found five more divergences that no
suite assertion covers, all now fixed and tested: the narrower variable-length
code takes a negative down to -4294967295 and writes its lowest thirty-two bits
rather than refusing; `SKIPBITS` reads its count unsigned, so a negative one
runs off the end instead of doing nothing, and names the count in the error;
and the seven-bit MS-DOS year wraps at both ends rather than raising. One
knowing divergence is left: `MSDOS-DATETIME` given a bare time reads a date out
of a struct holding a time, and JEBOL refuses where the C writes whatever those
bits happened to be.

### 14. The PDF codec times out — 9

`codecs-test-pdf.r3` runs past the harness's 5000ms limit on its first step.
Either the PDF codec is doing something quadratic or it is looping. Worth
finding out which before deciding what to do about it.

### 15. The scattered singles and pairs — 31 across fourteen files

What is left when the others above are taken out. Fourteen files:

    6  date-test.r3          3  mold-test.r3         1  copy-test.r3
    4  task-test.r3          2  bitset-test.r3       1  csv-test.r3
    3  error-test.r3         2  gob-test.r3          1  datatype-test.r3
    3  struct-test.r3        2  percent-test.r3      1  evaluation-test.r3
                                                     1  bbcode-test.r3
                                                     1  series-test.r3

The sum closes exactly
against the gap list as it stood when this was written, which is the point of
the goal: 109 + 55 + 45 + 40 + 38 + 34 + 29 + 9 + 17 + 27 + 135 + 7 + 8 + 9
= 562, leaving 40. If that no longer matches what
`grep -c '^[^#]' src/test/resources/rebol-suite/known-gaps.txt` says, the
difference is work someone has done and a size above is stale — re-derive per
file rather than trusting the table.

The sum still balances because those are entry counts, but **at least eleven of
the entries inside it are goal 16's and are not work at all**: eight in
`power-test.r3`, two in `make-test.r3`, one in `module-test.r3`. Real remaining
work is 604 or fewer, and nobody has established the ceiling.

    6  date-test.r3
    4  crypt-port-camelia-test.r3      goal 4b's work, not their own
    2  crypt-port-ccm-test.r3          goal 4b's work, not their own
    2  crypt-port-gcm-test.r3          goal 4a's work, not its own
    4  task-test.r3
    3  error-test.r3
    3  mold-test.r3
    3  struct-test.r3
    2  bitset-test.r3
    2  gob-test.r3
    2  percent-test.r3
    1  each of bbcode, copy, crypt, csv, datatype, evaluation and series

Sixteen of the eighteen have no stop at all, so every entry is a wrong answer
and `scripts/sweep.py` will put the two answers side by side. That makes this
the cheapest goal per assertion on the list and a reasonable place to start
cold, because each one is small enough to hold in your head whole.

**One stop, not two.** Re-derived with the fixed `SuiteStops` (goal 20):

    task-test.r3   to task! [...]   -> cannot use to task! on block!

`task!` is a datatype word here without a datatype behind it, which is recorded
in `TODO.md` and is a larger decision than four assertions warrant on its own.

The `evaluation-test.r3` stop this goal used to name — `call/shell/wait` giving
"no way to start a program" — was the tool's own answer and never the gate's,
and the line about CALL being "the same missing capability as three of goal 9's
stops" described a coupling that does not exist. `evaluation-test.r3` now stops
nowhere and owes one entry.

The eight crypt-port entries are listed here for the arithmetic only. Two are
goal 4a's and six are goal 4b's; on their own they are algorithms with nowhere
to run.

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
