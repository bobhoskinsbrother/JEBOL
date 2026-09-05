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

The fifteen porting goals are **mostly, not entirely, independent**, and the
couplings are named in the goals themselves. Two that this file used to claim
turned out not to exist: goal 5 and goal 9 were both said to wait on goal 8, and
neither does — that came from a measuring tool inventing environment stops, and
goal 20 has the story. The real ones are goal 1 to goal 3, and goal 15
double-counting eight of goal 4's assertions.

Goals 1, 3, 4, 6 and 14 are also one question asked five times: what the
capability catalogues claim is present. `system/codecs` is longer here than in a
real 3.22.5, so JEBOL enters blocks the oracle skips and then raises inside them.

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
run. Never move an assertion there because it is hard — and note that nothing in
the build would stop you, so this rule is held by discipline alone until goal 17
is done.

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

Sizes are the number of `known-gaps.txt` entries the goal is worth, and the
fifteen of them account for every entry with nothing left over — goal 15 exists
to close that sum and shows the arithmetic.

**Whether a size is a floor or a ceiling is unsettled, and it matters.** This
file used to say floor, reasoning that fixing a stop frees the assertions
standing behind it. Two independent audit passes disagreed with each other: one
confirmed the reasoning, the other found that the harness already scores an
unreached assertion as a failure, which would make the size a ceiling and mean
the count can only fall. Nobody has settled it. Until someone does, treat a size
as an estimate and expect surprises in both directions — and if you fix a stop
and the count goes up, that is the answer, so write it down here.

### 16. Stop the ratchet rewarding wrong answers — 11 entries, and it comes first

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
oracle:

    make-test.r3 #498, #533   error? try [make map! quote (1 2)]  -> #(false) both sides
    module-test.r3 #18        both sides answer _

Reproduce any of them:

    printf 'Rebol []\nprint mold power 2 16\nprint mold error? try [make map! quote (1 2)]\n' > /tmp/p.r3
    ./r3-head /tmp/p.r3

More are suspected in `port-test.r3` and `codecs-test.r3` and could not be
confirmed, for a structural reason worth knowing: in a file where the oracle
performs fewer assertions than the file writes, the ordinals cannot be lined up,
so there is no way to say which listed entry corresponds to which oracle failure.
Five separate counts of "how many are misfiled" came back as 8, 10, 13, 14 and
17 for exactly this reason. **Eleven is the confirmed floor, not the answer.**

### 17. Give the second list a ratchet — an hour

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

### 21. What reaching zero would not prove — the real backlog

None of this is on `known-gaps.txt` and none of it can be, because the suite
tests what functions **return** and these are all about what functions **say
about themselves**. Each was checked side by side against `./r3-head`:

    apply :copy [[1 2 3 4 5] true 3]   r3: [1 2 3]                      JEBOL: [1 2 3 4 5]
    words-of :copy                     r3: [value /part range /deep ...] JEBOL: _
    type? :append                      r3: action!                       JEBOL: native!
    pad/left "ab" 5                    r3: raises no-refine              JEBOL: "ab   "
    e/near after 1 / 0                 r3: [/ 0]                         JEBOL: _
    parse 1 [end]                      r3: raises expect-arg             JEBOL: #(false)

`APPLY` is the worst of them: refinements land in the wrong slots, so a
refinement is silently dropped and you get a wrong answer with no error. Roughly
13 of 20 probes were wrong this way, and up to 68 functions are affected. The
suite uses `APPLY` thirteen times and never once on a native with a refinement.

The scale of it is no longer an estimate. `python3 scripts/runtime-parity.py`
asks both interpreters about every function Rebol's `lib` holds, and says:

    582 asked, 1 absent
    123 report a different datatype   120 action! -> native!, 3 function! -> native!
    581 answer words-of differently   JEBOL gives none for all but one
    430 answer a different spec-of length

`action?` appears zero times in all 67 vendored files and `no-refine` appears
zero times, so no assertion in the suite could ever catch any of it. Run the
script before and after a change here; it is the only thing that will tell you
whether the change worked.

Unknown refinements are accepted silently on every REBOL-defined function, which
is the whole borrowed mezzanine. Natives and actions refuse them correctly. That
is the sharpest available answer to "does a borrowed `.reb` passing its own tests
mean the port is right".

The work is a sweep rather than a fix: generate every function in `lib` against
both interpreters, diff, and fix by group. It found every one of the above in an
afternoon.

---

### 1. The remaining codecs — 109

`codecs-test.r3`. The largest single file, and it is not one problem but about
eleven, each an `if find codecs 'name [...]` block that raises and takes its
whole group with it. `SuiteStops` lists them. As of writing:

- `load` of a file whose bytes are not valid UTF-8
- `do %units/files/issue-1677.txt` — DO of a file path, which is goal 19 and
  not a codec problem at all
- DER, CRT and PLIST codecs: `a word with no binding was evaluated: SEQUENCE`
  (the decoded block holds words the codec then evaluates)
- SWF: needs LZMA, which is goal 3
- ZIP: `_ is not a count of bytes`
- JPEG: `image encoding through the operating system` — needs an encoder;
  `javax.imageio` is already on the build path via `java.desktop` and covers
  PNG, JPEG, GIF, BMP and TIFF
- MIME-field: `bytes that are not valid UTF-8 where text was wanted: #{C3}`

Take one block at a time; each is its own commit. Note that the group names in
`known-gaps.txt` are wrong for this file — the slicer takes the last top-level
`===start-group===`, and this file nests its groups inside the `if` blocks, so
102 entries all claim to be in "TEXT codec". They are not.

### 2. Image, read and written — 55

`image-test.r3` runs to the end with no stops, so every one of these is a
wrong answer and `scripts/sweep.py image-test.r3` will show them in pairs.
The biggest group is "Image as a series" (31): an image is a series of pixels
and the series operations on it are not all right yet. "Image difference" is
another 9.

The C is `rebol3-source/src/core/t-image.c`. JEBOL's side is
`src/main/java/org/jebol/domain/eval/ImagePath.java` and the image branches of
`Natives.java`.

### 3. LZMA and Brotli — 45

`compress-test.r3`, which runs clean; these fail because the two algorithms do
not exist here. Both are large third-party algorithms and neither is Rebol's
own — unlike CRUSH and LZW, which were ported by hand and are in
`src/main/java/org/jebol/domain/eval/Crush.java` and `Lzw.java`.

**This goal used to offer two routes and one of them does not exist.** It said
you could decide the build has not got them and take the suite's "not available
in this build" branch, and that "the rest stop being gaps". That is wrong, and it
was checked: JEBOL **already** answers `feature-na` for both, already takes that
branch, and the 38 Brotli and LZMA entries are on the list *because* of it. The
harness scores an unreached assertion as a failure, so declaring a feature absent
retires nothing. The route was taken before this file was written and yielded
nothing further.

So the only route is to port them, which is a lot of code, and the honest size of
this goal is 38 assertions that cannot be cleared any other way. Both are large
third-party algorithms and neither is Rebol's own — unlike CRUSH and LZW, which
were ported by hand and are in
`src/main/java/org/jebol/domain/eval/Crush.java` and `Lzw.java`. `br`, `lz4` and
`lzav` are handled by `COMPRESSIONS_ELSEWHERE` in `Encodings.java`, with the same
consequence.

**And there is a trap in the other direction.** Assertion `#62` of
`compress-test.r3` is the `feature-na` check itself, and it currently passes —
it is one of the passes. Implement Brotli correctly and that assertion starts
failing and the gate goes red until somebody edits a list. Expect it, and do not
read it as a regression.

The exact-byte assertions matter here. A compressor that reads its own output
back is not thereby the same compressor: Rebol builds CRUSH with the constants
Red uses, not the ones upstream ships, and a port that took the originals
would round-trip perfectly and share not one byte with a real 3.22.5.

### 4. The crypt port — 40

`crypt-port-test.r3` stops at its first line:

    port: open make port! [scheme: 'crypt algorithm: 'AES-128-CBC key: #{...}]

`make port!` of a block is refused, and there is no `crypt` scheme. Everything
after that is `port is unset`. So this is one blocker in front of forty
assertions, of which 26 are Camellia and 11 are the FIPS-197 AES vectors,
which the JVM can do.

`system/catalog/ciphers` is empty here and holds about twenty entries in a
real Rebol — the AES modes, Camellia, ChaCha20 and the rest. Filling it is
part of the goal, and it decides which of the forty can pass: a cipher not in
the catalogue should say so rather than answering wrongly.

The pattern to follow is `ChecksumPort.java` and the checksum scheme in
`Interpreter.java`: a scheme registered through the borrowed `sys/make-scheme`,
an actor name in `SCHEMES_THIS_BUILD_SERVES`, and open/read/write/update/close
branches in `Natives.java`. `crypt-port-camelia-test.r3`,
`crypt-port-ccm-test.r3` and `crypt-port-gcm-test.r3` are 8 more between them.

### 5. What is left of the file ports — 38

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
oracle is not deterministic on this file. Running `checksum-test.r3` through
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

### 8. The environment — 9, and it unblocks nothing

`os-test.r3`. **The "unblocks others" in the old title was wrong** — it came
from `SuiteStops` reporting stops in `port-test.r3` and `module-test.r3` that do
not happen under the real gate, where `get-env`, `list-env` and `call/shell/wait`
all work. See goal 20. This is nine assertions in one file and nothing waits on
it.

Seven stops, all of them here, and both causes are real:

- `set-env` and `get-env` refuse a `word!` and want a `string!`. A real Rebol
  takes either.
- `set-env` says "a JVM cannot change its own environment", which is true of
  the process environment but need not be true of what the interpreter
  reports. Decide what a JVM-hosted Rebol should do here and say so in the
  commit.

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

### 11. Sweepable files that run clean — 135 between them

None of these stops, so every entry is a wrong answer and `scripts/sweep.py`
will show it. Small enough to take in one sitting each:

| file | entries | note |
| --- | --- | --- |
| `func-test.r3` | 29 | 15 "Other issues", 14 "OP!" |
| `unicode-test.r3` | 21 | one stop: `repeat` refuses a `string!` count |
| `time-test.r3` | 15 | all in "time" |
| `map-test.r3` | 12 | 10 are "set operations with map!" |
| `make-test.r3` | 10 | |
| `file-test.r3` | 12 | one stop: `read file://temp.txt` — the `file://` URL scheme |
| `thru-cache-test.r3` | 10 | |
| `parse-test.r3` | 9 | "Other parse issues" |
| `vector-test.r3` | 9 | |
| `lexer-test.r3` | 8 | one is `NULLs inside loaded string`, which loads through a subprocess |

`power-test.r3` used to be a row here with eight entries, and working it would
have made JEBOL disagree with the oracle. Goal 16 took them off: they are
`--red--` assertions that a real Rebol fails too. Two `make-test.r3` entries
went the same way.

### 12. Two Java exceptions escaping to the top — 7

`issue-test.r3`. These are not wrong answers but crashes, and a Java exception
reaching the interpreter's edge is a defect of a different kind:

    to-hex/size 1  0   -> java.lang.IllegalArgumentException: a word needs a spelling
    to-hex/size 1 -1   -> java.lang.StringIndexOutOfBoundsException: Range [17, 16)

`spec/embed.allium` says nothing a script does may reach the host as a
throwable. Both should be REBOL errors; ask `./r3-head` which.

### 13. The binary dialect's missing keywords — 8

`bincode-test.r3`: `EncodedU32`, `EncodedU64`, `VINT`, `SKIPBITS`,
`MSDOS-DATETIME`. The dialect is in
`src/main/java/org/jebol/domain/eval/Bincode.java`; the C is
`rebol3-source/src/core/u-bincode.c`. Each keyword is small and independent.

### 14. The PDF codec times out — 9

`codecs-test-pdf.r3` runs past the harness's 5000ms limit on its first step.
Either the PDF codec is doing something quadratic or it is looping. Worth
finding out which before deciding what to do about it.

### 15. The scattered singles and pairs — 40 across eighteen files

What is left when the fourteen above are taken out. The sum closes exactly
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
    4  crypt-port-camelia-test.r3      these three are goal 4's work,
    2  crypt-port-ccm-test.r3          not their own: the same cipher port
    2  crypt-port-gcm-test.r3          under three more algorithms
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

The eight crypt-port entries are listed here for the arithmetic only. Do them
with goal 4; on their own they are four algorithms with nowhere to run.

---

## Two loose ends, both flakes

**A flake is a fail** (see `CLAUDE.md`). Neither of these is closed.

### The oracle is not deterministic on one file

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
Nobody has read the C to find out why. Until someone does, ask the oracle three
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
