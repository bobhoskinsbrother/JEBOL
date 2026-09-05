# Goals

Rebol's own test suite is the measure of this port. 615 of its assertions
still fail, listed one per line in
`src/test/resources/rebol-suite/known-gaps.txt`. This file breaks the
remaining work into goals that can be taken one at a time.

Each goal below is independent. Take one, finish it, commit it, stop. There is
no order to them beyond the sizes shown.

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

Do not `rm -rf build/test-results` to force a re-run; it makes Gradle fail on
its own binary results directory. `./gradlew cleanTest` instead.

### When an assertion cannot pass

Some assertions a real Rebol does not run either — one arm of an
`either error? try [...]`, for instance, where the other arm is the one taken.
Those go in `src/test/resources/rebol-suite/fails-on-rebol-too.txt`, with a
comment recording the `./r3-head` session that settles it. They are not gaps
and are not run. Never move an assertion there because it is hard.

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
fifteen of them account for all 615 with nothing left over — goal 15 exists to
close that sum and shows the arithmetic. Treat a size as a floor rather than an
estimate: fixing a stop frees everything standing behind it, which is often
more than the count suggests, because those assertions were never reached to
be counted as failures in the first place.

### 1. The remaining codecs — 111

`codecs-test.r3`. The largest single file, and it is not one problem but about
eleven, each an `if find codecs 'name [...]` block that raises and takes its
whole group with it. `SuiteStops` lists them. As of writing:

- `load` of a file whose bytes are not valid UTF-8
- `do %units/files/issue-1677.txt` — DO of a file path
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

Two honest routes. Port them, which is a lot of code. Or decide the build does
not have them, which is a real answer that Rebol's own suite is written to
accept: each group opens with `either error? try [compress "test" 'lzma]` and
takes the "not available in this build" branch. That is already how `br`,
`lz4` and `lzav` are handled — see `COMPRESSIONS_ELSEWHERE` in
`Encodings.java`. If you take that route the assertions in the taken branch
pass and the rest stop being gaps, but say so plainly in the commit; do not
present it as having implemented them.

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
(`SeekableFilePort.java`); these are the remainder. Three stops:

- `get-env "PWD"` twice — the environment is not granted to the harness, or
  `list-env`/`get-env` do not answer what a real Rebol answers. See goal 8.
- `open %issue-2447` — a port that could not be opened

The rest are wrong answers; sweep the file. 23 are in "file port" and 14 in
"directory port".

### 6. The checksum port — 34

`checksum-test.r3` has one stop, and it is environment-dependent:
`file-checksum system/options/boot` — the boot path is outside the sandbox
root, so opening it fails. Decide what a sandboxed interpreter should say
about its own boot file; the honest answer may be that this assertion belongs
in `fails-on-rebol-too.txt` reasoning, or that `system/options/boot` should
name something inside the root.

The other 28 are the "Checksum port" group failing on their merits. Sweep the
file: the port itself works (open, write, read, close, update all match a real
Rebol in isolation), so this is something narrower.

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

### 8. The environment — 9, and it unblocks others

`os-test.r3`, and two of `port-test.r3`'s stops. Three separate things:

- `set-env` and `get-env` refuse a `word!` and want a `string!`. A real Rebol
  takes either.
- `set-env` says "a JVM cannot change its own environment", which is true of
  the process environment but need not be true of what the interpreter
  reports. Decide what a JVM-hosted Rebol should do here and say so in the
  commit.
- `get-env` and `list-env` are not granted in the harness, so `PWD` is
  unreadable.

### 9. Modules and IMPORT — 19

`module-test.r3` stops on `write modules-dir/mymodule.reb`, because
`system/options/modules` is none. It is none because `sys-start.reb` works it
out from `get-env "HOME"` and then `make-dir`s it, and both fail under the
sandbox. So this depends on goal 8, or on deciding where a sandboxed
interpreter's module directory should be.

After that: `import`, which is the substance of the goal, and three
`call`-dependent stops that need the process service.

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
| `make-test.r3` | 12 | |
| `file-test.r3` | 12 | one stop: `read file://temp.txt` — the `file://` URL scheme |
| `thru-cache-test.r3` | 10 | |
| `parse-test.r3` | 9 | "Other parse issues" |
| `vector-test.r3` | 9 | |
| `power-test.r3` | 8 | "power integer" |
| `lexer-test.r3` | 7 | |

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

What is left when the fourteen above are taken out, and the numbers close
exactly: 111 + 55 + 45 + 40 + 38 + 34 + 29 + 9 + 19 + 27 + 144 + 7 + 8 + 9
= 575, and 615 - 575 = 40.

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

Two do stop, and both stops are about reaching outside the process rather than
about the thing the file is named for:

    evaluation-test.r3   call/shell/wait ... -> no way to start a program
    task-test.r3         to task! [...]      -> cannot use to task! on block!

The first is CALL, which is the same missing capability as three of goal 9's
stops — do it once and both move. The second is that `task!` is a datatype
word here without a datatype behind it, which is recorded in `TODO.md` and is
a larger decision than four assertions warrant on its own.

The eight crypt-port entries are listed here for the arithmetic only. Do them
with goal 4; on their own they are four algorithms with nowhere to run.

---

## One loose end

`WebScreenServerFromTheSourceTest` failed once, under a full parallel gate
run, with 404 where it wanted 204, and has passed every time since — twelve
isolated runs, 400 serial rounds, 960 concurrent rounds, and every gate run
after. **A flake is a fail** (see `CLAUDE.md`), so this is an open failure and
not a curiosity.

What has been done: the server now binds the loopback and names the address it
actually bound, rather than calling itself `localhost`, which resolves to two
addresses on a dual-stack machine and left the client to pick. That was the
one ambiguity findable by reading. The failing assertion now carries the whole
response instead of a bare status, so the next occurrence will say which
server answered and what it said.

It is not called fixed. If it recurs, the diagnostics are there.
