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

The fifteen porting goals are **mostly, not entirely, independent**. Known
couplings are named in the goals themselves; the ones this file used to claim
did not exist are goal 1 to goal 3, goal 5 to goal 8, goal 9 to goal 8, and goal
15 double-counting eight of goal 4's assertions. Goals 1, 3, 4, 6 and 14 are
also one question asked five times: what the capability catalogues claim is
present. `system/codecs` is longer here than in a real 3.22.5, so JEBOL enters
blocks the oracle skips and then raises inside them.

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
      org.jebol.suite.SuiteStops 7

(The system `java` is too old for the class files; use the JDK Gradle uses,
above.)

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

### 19. `do %anyfile` is broken — 3 assertions, and one green test is green because of it

`DO` given a `file!` evaluates the name instead of reading the file:

    Rebol []
    print mold try [do %units/files/unset.r3]

Real Rebol runs the script. JEBOL raises `no-value` on the word `units`. `READ`
works, `do to string! read %f` works, and the lexer types `%units/files/unset.r3`
as `file!` correctly, so the fault is in `DO` rather than in the reader.

Goal 18 put the three assertions back and they are now on `known-gaps.txt`:

    evaluation-test.r3 / do script / script returning UNSET value #40
    evaluation-test.r3 / do script / script with quit #42
    evaluation-test.r3 / do script / script with quit #43

The part that makes this urgent rather than ordinary: the fourth assertion in
that group is `error? try [do %units/files/error.r3]`, it currently passes, and
**it passes because the feature is broken.** Real Rebol raises `zero-divide`
from inside the script; JEBOL raises `no-value` on the word and never runs it.
Both are errors, so the assertion is satisfied. Fixing `DO` will turn that entry
red, which is correct and is not a regression.

`lexer-test.r3 / Special tests / NULLs inside loaded string #452` also came back
as a gap in goal 18. It is a different defect — it loads a 40,000-character
string through a subprocess and checks the buffer survives being extended — but
it is in the same file-and-process corner and worth reading alongside this.

### 20. Fix the measuring tools before trusting them — half a day

**`SuiteStops` under-provisions the interpreter and invents stops.** It grants
every `HostService` but installs only the file system, where `RebolSuiteTest`
also installs `useEnvironment(new ProcessEnvironment())` and
`useProcesses(new JavaProcesses())`. Granting a service is not providing one.

    grep -n "use[A-Z][a-zA-Z]*(" src/test/java/org/jebol/suite/RebolSuiteTest.java
    grep -n "use[A-Z][a-zA-Z]*(" src/test/java/org/jebol/suite/SuiteStops.java

So every stop it reports reading "given no environment to read" or "given no way
to start a program" is an artefact. Under the real gate `get-env`, `list-env` and
`call/shell/wait` all work, and `port-test.r3` has no stops at all. Goals 5, 8, 9
and 15 were partly derived from that output and are corrected in place below.
`SweepRunner` needs the same check.

**`scripts/c-parity.py` cannot fail on the defects that matter.** It compares two
files rather than two interpreters, and argument counts rather than argument
names, so its clean report is true and says nothing about goal 21. A
runtime arm that asks the running interpreter would have caught `action?` alone.

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

The scale of the reflection problem, measured across all 279 C functions at
runtime: 158 differ, 113 are missing refinements, 84 have a renamed argument.
All 60 of Rebol's actions report as `native!`. `action?` appears zero times in
all 67 vendored files and `no-refine` appears zero times, so no assertion in the
suite could ever catch any of it.

Unknown refinements are accepted silently on every REBOL-defined function, which
is the whole borrowed mezzanine. Natives and actions refuse them correctly. That
is the sharpest available answer to "does a borrowed `.reb` passing its own tests
mean the port is right".

The work is a sweep rather than a fix: generate every function in `lib` against
both interpreters, diff, and fix by group. It found every one of the above in an
afternoon.

---

### 1. The remaining codecs — 111

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

**This goal used to list three stops. Under the real gate there are none.** The
two `get-env "PWD"` stops were artefacts of `SuiteStops` not installing the
environment adapter — see goal 20 — and the environment works when
`RebolSuiteTest` runs the file. Re-derive the third (`open %issue-2447`) with a
fixed tool before believing it.

So all 38 are wrong answers rather than blockers, and the whole file is
sweepable: `scripts/sweep.py port-test.r3`. 23 are in "file port" and 14 in
"directory port". There is no dependency on goal 8; that was invented by the
same artefact.

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

Two real things:

- `set-env` and `get-env` refuse a `word!` and want a `string!`. A real Rebol
  takes either.
- `set-env` says "a JVM cannot change its own environment", which is true of
  the process environment but need not be true of what the interpreter
  reports. Decide what a JVM-hosted Rebol should do here and say so in the
  commit.

### 9. Modules and IMPORT — 19

`module-test.r3` stops on `write modules-dir/mymodule.reb`, because
`system/options/modules` is none.

**The stated cause was wrong.** This goal said the environment is unreadable
under the sandbox so `sys-start.reb` cannot work the directory out, and that this
therefore depends on goal 8. Under the real gate `get-env "HOME"` works — the
"no environment" reading came from `SuiteStops`, which does not install the
adapter (goal 20). Re-derive the actual cause with a fixed tool before starting.
The same applies to the three `call`-dependent stops: `call/shell/wait` works
under `RebolSuiteTest`.

After that: `import`, which is the substance of the goal.

### 10. The elliptic curves — 27

`dh-test.r3` stops at

    foreach ecurve system/catalog/elliptic-curves [...]

with "ecdh does not allow none! for its public-key argument". The catalogue is
not the problem — JEBOL lists all thirteen curves a real Rebol does, secp192r1
through curve448 — so `ecdh/init` is not answering a key for at least one of
them. `EllipticCurveKey.java` is the JEBOL side; the C is `n-crypt.c`.

### 11. Sweepable files that run clean — 144 between them

None of these stops, so every entry is a wrong answer and `scripts/sweep.py`
will show it. Small enough to take in one sitting each:

| file | entries | note |
| --- | --- | --- |
| `func-test.r3` | 29 | 15 "Other issues", 14 "OP!" |
| `unicode-test.r3` | 21 | one stop: `repeat` refuses a `string!` count |
| `time-test.r3` | 15 | all in "time" |
| `map-test.r3` | 12 | 10 are "set operations with map!" |
| `make-test.r3` | 12 | 2 of them are goal 16's, not work |
| `file-test.r3` | 12 | one stop: `read file://temp.txt` — the `file://` URL scheme |
| `thru-cache-test.r3` | 10 | |
| `parse-test.r3` | 9 | "Other parse issues" |
| `vector-test.r3` | 9 | |
| ~~`power-test.r3`~~ | ~~8~~ | **do not work these — goal 16.** All eight are `--red--` assertions a real Rebol fails too |
| `lexer-test.r3` | 7 | |

So this goal is 136 of real work, not 144. The `power-test.r3` row is the trap
goal 16 exists to remove: making those eight pass means making JEBOL disagree
with the oracle, and the ratchet will go green when you do it.

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
the goal: 111 + 55 + 45 + 40 + 38 + 34 + 29 + 9 + 19 + 27 + 144 + 7 + 8 + 9
= 575, leaving 40. If that no longer matches what
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

Two appeared to stop, and one of those was my tool lying:

    evaluation-test.r3   call/shell/wait ... -> no way to start a program
    task-test.r3         to task! [...]      -> cannot use to task! on block!

**The first is not a stop.** `call/shell/wait` works under `RebolSuiteTest`,
which installs the process adapter; `SuiteStops` does not, and reported a stop
that the gate never sees. See goal 20. The line this file used to carry — "CALL
is the same missing capability as three of goal 9's stops, do it once and both
move" — described a coupling that does not exist.

The second is real: `task!` is a datatype word here without a datatype behind
it, which is recorded in `TODO.md` and is a larger decision than four assertions
warrant on its own.

`evaluation-test.r3` also has three assertions that are not in this count at all,
because they were cut out of the vendored file. They are goals 18 and 19.

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
