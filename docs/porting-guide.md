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
   all 224 C functions, with every argument's datatypes and every refinement.
3. `src/core/*.c` -- the arms themselves. `t-*.c` for datatypes, `n-*.c` for
   natives, `c-do.c` for the evaluator, `l-scan.c` for the reader, `s-mold.c`
   for molding, `boot/errors.reb` for the error catalogue. The host is in
   `src/os/` and matters for graphics: `host-window.c`, `host-draw.c`.
4. `src/tests/units/*.r3` -- Rebol's own tests, a third authority and the
   cheapest one: read them before writing a line.

**There is an `./r3` binary, gitignored, and it is a fourth authority.** It
settles what the C leaves ambiguous and it has killed a wrong reading every
time it has been used. It is for *checking an expectation before writing it
down*, not for deciding what the language is.

## The regression floors

All of these stay where they are. A change that moves one is wrong.

- **`RebolSuiteTest`** -- Rebol's own 3721 assertions, green, about thirteen
  seconds alone. The inner loop between changes.

  ```
  ./gradlew test --tests 'org.jebol.suite.RebolSuiteTest'
  ```

- **`ActionParityTest`** -- the datatype table times the arms table, ratchet at
  zero, nothing parked behind it.
- **`scripts/c-parity.py`** -- MISSING, WRONG LAYER, REFINEMENTS and ARGUMENTS
  all empty. TYPES prints 101 lines and every one is a known shape: 41 are
  JEBOL enumerating concrete datatypes where R3 writes `any-type!`, and 60 are
  `vector!` refusals from the datatype backlog. **That MISSING is over an
  incomplete input** -- see the measures in Goal 1.

`./gradlew check` is the gate before a commit: 9297 tests, 0 failed,
0 skipped, about three minutes.

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
2. **Check the surprising readings against `./r3`** before writing them down.
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
  true of `./r3 --do` with quotes in it.
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
