# Porting guide

How this port is done, what decides an argument, and what must not move. None
of it is work to be finished, which is why it is here rather than in `TODO.md`.

## The goal

**Port every function a real Rebol 3 has into JEBOL.** The imported Rebol test
suite is how a port is checked, not what is being aimed at.

## The rule about layers

Rebol writes about a third of its library in C and the rest in REBOL.

**A function Rebol writes in C is written in Java here. A function Rebol
writes in REBOL is imported from its own file and loaded as a resource. It is
never rewritten and never copied into `prelude.reb`, not even verbatim.**

The test is mechanical: find which R3 file defines it. `src/core/*.c` means
Java. `src/mezz/*.reb` means copy that file into
`src/main/resources/org/jebol/mezz/`, add it to `ORDER.txt`, and fix whatever
native it turns out to need. Decision 13 in `decisions.md` says why a verbatim
copy is still a fork, and what it cost last time.

## The C is the authority

Rebol's C says what the language is, and it explains itself. Every place the
two disagreed during the port, the C was right. The sources, in order:

1. `rebol3-source/src/boot/types.reb` -- the datatype table: each datatype's
   typeclass, path handler, MAKE support, and typesets.
2. `src/boot/actions.reb` and `src/boot/natives.reb` -- the declared specs of
   60 actions and 164 natives, with every argument's datatypes and every
   refinement. **They are not the whole C surface.** 54 more natives carry
   their spec in a comment above the function in `src/core/*.c` --
   `//\tclamp: native [...]` -- and are mentioned in no boot file, which is
   279 in all. A collector that reads only the boot files cannot see them, and
   that is how `binary` stayed missing while the parity report said
   MISSING: 0.
3. `src/core/*.c` -- the arms themselves. `t-*.c` for datatypes, `n-*.c` for
   natives, `c-do.c` for the evaluator, `l-scan.c` for the reader, `s-mold.c`
   for molding, `boot/errors.reb` for the error catalogue. The host is in
   `src/os/` and matters for graphics: `host-window.c`, `host-draw.c`.
4. `src/tests/units/*.r3` -- Rebol's own tests, a third authority and the
   cheapest one: read them before writing a line.

**There is an `./r3-head` binary, gitignored, and it is a fourth authority.**
It settles what the C leaves ambiguous and it has killed a wrong reading every
time it has been used. It is for *checking an expectation before writing it
down*, not for deciding what the language is.

**Build it, do not download it, and `scripts/build-r3.sh` does that.**

```
./scripts/build-r3.sh            # bulk, to ./r3-head, about a minute
./scripts/build-r3.sh core
```

A downloaded release is always older than the checkout beside it, and the two
disagree in ways that cost real time: the release here was ten weeks behind
and four separate wrong readings were traced to it -- empty-vector statistics,
cross-signedness vector comparison, `iconv/to` into UTF-8, and CRC-24. The
last is the clearest. `Compute_CRC24` in `s-crc.c` now starts from `CRCINIT`,
and the comment beside that line says "originally there was not standard seed:
len + *str", which is exactly what the older binary still computes. So
`checksum "abc" 'crc24` gives 1664899 there and 12196987 in the source, and a
probe that trusted the binary would have made JEBOL wrong.

The measure that settles it: run Rebol's own tests with both. The release
fails 72 of them, and a binary built from the checkout fails 59 while running
a thousand more assertions.

```
cd rebol3-source/src/tests && ../../../r3-head run-tests.r3
```

**The downloaded `./r3` is still needed, to build with.** Rebol's pre-make step
is itself a Rebol script, so an old binary is what makes the new one; after
that it has no other job. Rebol's own build tool, Siskin, is a separate
download and is not needed -- `scripts/resolve-nest.r3` reads
`make/rebol3.nest` far enough to answer the one question pre-make asks, and
clang does the rest.

## The regression floors

All of these stay where they are. A change that moves one is wrong.

- **`RebolSuiteTest`** -- Rebol's own assertions, about a minute alone. The
  inner loop between changes. **All 10,100 assertions its sixty-seven files
  write are run**, and 1,716 fail. Every failure is named in
  `known-gaps.txt`: not a skip list, because every line in it runs on every
  build and the test fails if a listed assertion starts passing.

  **It fails two different ways and they mean opposite things.** A
  `theAssertionHolds` failure is a regression and has to be fixed. A
  `theGapListHasNoPassingEntries` failure is progress: those assertions now
  pass and their lines come out of the file. Take the list out of the failure
  message in `build/test-results/test/*.xml`, check the message ends in `]`
  in case AssertJ truncated it, and filter those lines out. Never empty
  `known-gaps.txt` to see the whole picture -- restoring it from HEAD and
  reading the two failure kinds apart is what tells a regression from a win.

  Two gates hold the count honest, and neither takes a list. A file the
  reader cannot take to the end fails the build; an assertion the harness
  does not run fails the build. Both were added because the count had been
  true of what it was given and silent about what it was not -- thirteen
  files stopped partway, 125 commented-out assertions were counted as real,
  and an assertion inside a FOREACH was never run at all.

  ```
  ./gradlew test --tests 'org.jebol.suite.RebolSuiteTest'
  ```

- **`ActionParityTest`** -- the datatype table times the arms table, ratchet at
  zero, nothing parked behind it.
- **`scripts/c-parity.py`** -- **279 C functions, 279 matching R3's surface.**
  MISSING, WRONG LAYER, REFINEMENTS, ARGUMENTS and TYPES are all empty. The
  only thing it still prints is that JEBOL has a `java-object!` datatype and
  R3 has nothing like it, which is deliberate.
- **`PortingBacklogTest`** -- 0 of R3's 404 functions missing, and it asks
  both `lib` and `sys`. It has been wrong three times, each because a number
  was believed and the question behind it was not.
- **`scripts/error-parity.py`** -- 69 of Rebol's 142 error ids can be raised
  and 73 cannot. This one is not a floor yet: it is a count that did not
  exist until `too-long` was found to be missing by needing it. Read the
  Script and Syntax columns first, because those name behaviour JEBOL already
  has and reports under the wrong id or not at all.

`./gradlew check` is the gate before a commit: about 14,961 tests, 0 failed,
0 skipped, and five to twenty-five minutes depending on what it has to
recompile.

`./gradlew browserCheck` is the second gate and is not optional, only
separate: it drives a real Chrome and compares what it paints against Java2D,
pixel for pixel. Run it after any change to `PaintList`, either renderer, or
the page.

`scripts/c-surface.py` rebuilds `c-surface.txt` when Rebol's tree changes.

## How to port one function

Five steps, in order. Do not begin one before the last is finished.

1. **Read the C.** The whole function, not the part that looks relevant. The C
   carries the rules no probe will show you: which flags are set together, what
   a count of zero means, where a search starts, which `case` labels share one
   body and which fall through into a refusal.
2. **Check the surprising readings against `./r3-head`** before writing them down.
   The C is the authority and it is also easy to misread; every reading that
   looked obvious and turned out backwards was caught this way.
3. **Copy the logic into Java.** Follow the C's structure. Where the C has a
   branch, have a branch. A rewrite that looks tidier is a rewrite whose bugs
   are yours and not Rebol's.
4. **Write the tests from the C, not from your Java.** Read the C again and
   write a test for every branch, flag combination and boundary it guards.
   Tests written by reading the port only prove the port agrees with itself.
5. **Run the suite.** A failure there is a rule the C states and the port
   missed.

**A spec is distilled from the Java at the end for a ported function, and
written first for anything new.** The rule is about where the truth lives: for
a port the C is the truth and a spec written before reading it disagreed every
time, but for something JEBOL decides for itself -- the screen, the paint
list, the renderers -- there is no C to distil and the spec comes first.

**Read the declaration and the arm as two different things.** A datatype the
spec block does not list is refused as `expect-arg` by the declaration; one
the spec lists and the arm turns away is `cannot-use`. A script can tell them
apart, and the C settles every case.

**The C has its own bugs, and copying one is not fidelity.** Where the C is
plainly wrong rather than surprising, write what it meant and record what it
does.

## Working notes

Each of these cost time once.

- **Multi-line REBOL collapsed onto one line feeds the first call the next
  one's arguments.** Write a probe to a file and run the file. The same is
  true of `./r3-head --do` with quotes in it.
- **`-Werror` with `dangling-doc-comments`** means inserting code between a
  javadoc and its declaration fails the build. It also refuses a
  try-with-resources whose resource the body never mentions.
- **zsh `no matches found` kills a `until ls *.xml` wait loop.** Use
  `find ... | wc -l`.
- **A `make` spec block is not evaluated.** `make gob! [image: make image!
  4x4]` puts the MAKE native in the field. Name the value first.
- **`mold none` is `_`, not `#(none)`**, in both JEBOL and the binary. Tests
  that compare molded strings need the underscore.
- **A pair's halves are decimals.** `first 0x22` is `0.0`, type `decimal!`.
- **A gob colour's fourth octet is opacity, not transparency.** 255 is opaque,
  the same way round as Java's alpha. The guess goes the other way.
- **A C spec comment has a `return:` line and a boot file does not.** Dropping
  the word is not enough: the datatype block behind it then attaches to the
  argument in front. That is what made `factorial value [integer!]` look as
  though it took a decimal.
- **`any-type!` is not in any row's typesets column.** `types.reb` builds it
  from the table's length, so a collector reading the column alone never sees
  it, and every argument declared that way reads as a difference.
- **A suite file that will not read contributes nothing, and a file that is
  not there contributes nothing either.** `series-test.r3` sat inside a green
  3721 for as long as vector literals would not lex, and 54 of Rebol's 76 unit
  files were never vendored at all. `SuiteCoverageTest` now fails on the first
  and `TODO.md` Goal 0 counts the second, but the habit is the point: before
  trusting a count, ask what it is counting and what it silently is not.
- **A borrowed file that loads is not a borrowed file that works.** REWORD,
  SPLIT, PAD and SUM are Rebol's own REBOL and all four were loaded and all
  four gave wrong answers, because each stands on a primitive JEBOL had
  slightly wrong. When one of them misbehaves, ask what it is standing on
  rather than reading its body: PAD is four lines and the answer was in
  `insert/dup`.
- **A word a function body wrote is bound to the function, not to the call.**
  Rebol binds a body once, at MAKE time, and stamps the function into every
  word. Two consequences, both of which JEBOL had to be taught: one word is
  the same word on every call, and `Get_Var` resolves it against the innermost
  call of that function running at the time it is read. Only a word the
  function *names* is bound this way -- a free word in a body keeps the
  binding it had where it was written, which is why `same? :given 'given`
  behaves differently depending on whether `given` is a parameter.
- **A word in a PARSE rule is resolved before its kind is decided.**
  {@code Get_Parse_Value} does it for counts, parens, none, and what TO and
  THRU look for. Reading what was written instead means every rule assembled
  at run time fails while the same rule typed by hand works.
- **`if (IS_NONE(item)) return index;` is in `Parse_Next_String` alone.** A
  none matches nothing in a string parse and is a value to match in a block
  parse. Copying the line into both parsers broke a passing assertion.
- **A path written onto a literal is not a path.** `#(u8! [1])/size` lexes as
  a vector and the refinement `/size`, and so does `(next v)/length`. Name the
  value first, in probes as much as in tests -- three expectations here were
  wrong for this reason before the code was.
- **Do not `rm -rf build/test-results`** to force a re-run; Gradle then fails
  with `NoSuchFileException` on its own binary results directory. Use
  `./gradlew cleanTest`.

## Ordering rules learned the hard way

- `mezz-tail.reb` goes last of what Rebol boots: it sets aliases the on-demand
  imports read, and PROTECT-SYSTEM ends by unprotecting what REGISTER-CODEC
  writes to.
- `codec-der.reb` goes before `codec-crt.reb`.
- `mezz-osx-dialogs.reb` is deliberately not loaded. It shells out to
  `osascript`; JEBOL serves REQUEST-DIR/FILE/COLOR through the WINDOWS port,
  which works everywhere and asks the host's grant first.
- The schemes are registered and the event port opened *partway through* the
  library load, just before `view-funcs.reb`, because that file calls
  INIT-VIEW-SYSTEM on its own last line and needs `system/ports/event` to
  exist by then.
