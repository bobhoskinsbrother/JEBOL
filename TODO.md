# TODO

Only work that is left. History lives in git and in `docs/`.

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
native it turns out to need. Decision 13 in `docs/decisions.md` says why a
verbatim copy is still a fork, and what it cost last time.

## The C is the authority. There is no binary

Rebol's C says what the language is, and it explains itself. Every place the
two disagreed during the port, the C was right. The sources, in order:

1. `rebol3-source/src/boot/types.reb` -- the datatype table: each datatype's
   typeclass, path handler, MAKE support, and typesets.
2. `src/boot/actions.reb` and `src/boot/natives.reb` -- the declared specs of
   all 224 C functions, with every argument's datatypes and every refinement.
3. `src/core/*.c` -- the arms themselves. `t-*.c` for datatypes, `n-*.c` for
   natives, `c-do.c` for the evaluator, `l-scan.c` for the reader, `s-mold.c`
   for molding, `boot/errors.reb` for the error catalogue.
4. `src/tests/units/*.r3` -- Rebol's own tests, a third authority and the
   cheapest one: read them before writing a line.

## The regression floors

All three measures are at zero/green and stay that way. A change that moves
any of them is wrong.

- **`RebolSuiteTest`** -- Rebol's own 3721 assertions, green, ~13 seconds
  alone. The inner loop between changes.

  ```
  ./gradlew test --tests 'org.jebol.suite.RebolSuiteTest'
  ```

- **`ActionParityTest`** -- the datatype table times the arms table, ratchet
  at zero (vector! TYPES lines excepted, which are datatype backlog).
- **`scripts/c-parity.py`** -- declared specs against JEBOL's registry;
  MISSING, WRONG LAYER, REFINEMENTS, ARGUMENTS and TYPES all print empty
  apart from the vector!/task! lines.

`./gradlew check` is the gate before a commit: 8664 tests, 0 failed,
0 skipped. `scripts/c-surface.py` rebuilds `c-surface.txt` when Rebol's tree
changes.

## How to port one function

Four steps, in order. Do not begin one before the last is finished.

1. **Read the C.** The whole function, not the part that looks relevant. The C
   carries the rules no probe will show you: which flags are set together, what
   a count of zero means, where a search starts, which `case` labels share one
   body and which fall through into a refusal.
2. **Copy the logic into Java.** Follow the C's structure. Where the C has a
   branch, have a branch. A rewrite that looks tidier is a rewrite whose bugs
   are yours and not Rebol's.
3. **Write the tests from the C, not from your Java.** Read the C again and
   write a test for every branch, flag combination and boundary it guards.
   Tests written by reading the port only prove the port agrees with itself.
4. **Run the suite.** A failure there is a rule the C states and the port
   missed.

**The spec in `spec/` is distilled from the Java at the end, not written
first.** Every spec written here before the C had been read disagreed with the
C. Read wider before deciding anything is blocked or needs a decision: the
caller, the policy it consults, the file that unsets it.

**Read the declaration and the arm as two different things.** A datatype the
spec block does not list is refused as `expect-arg` by the declaration; one
the spec lists and the arm turns away is `cannot-use`. A script can tell them
apart, and the C settles every case.

**The C has its own bugs, and copying one is not fidelity.** Where the C is
plainly wrong rather than surprising, write what it meant and record what it
does.

---

# Goal 7. Un-smear the domain: one datatype, one place

The port grew action-major -- POKE holds a bitset arm, a gob arm, an image
arm; APPEND holds another bitset arm; the Evaluator and Comparison hold more
-- so "what can a bitset do" is answered nowhere. The C is type-major:
`t-bitset.c` holds every bitset action in one file. Restore that slicing in
Java, one increment per `t-*.c` file, every increment green on `main`.

1. **The seam.** An interface carrying the C's action set (`boot/actions.reb`:
   pick, poke, find, insert, change, remove, clear, copy, tail?, complement,
   ...), every method defaulting to the type's refusal as `Trap_Action` does.
   An `EnumMap<Datatype, handler>` registry; the generic natives ask it first
   and fall back to their inline arms while a type is unmoved.
2. **Pilot: bitset.** Smallest surface, and it exercises the awkward parts:
   the protection check that runs before every mutation, a mutable value with
   no position, an Evaluator set-path branch. Fixes the conventions.
3. **One datatype per increment, smallest first:** typeset, map, pair, tuple,
   money, date, time, event, gob, image, char/logic and the scalars, the word
   family, object and module, error, function, then string family and block
   family last.
4. **The series commons.** `clampedToTail`, `insertInto` and friends become
   the Java `f-series.c`: one place the string and block handlers share.

What moves and what stays, decided by the C itself: comparison arms move
(`CT_*` lives in `t-*.c`), MAKE/TO arms move (`MT_*` likewise), molding stays
in Molder (`s-mold.c` is central). The existing `GobPath`/`ImagePath`/
`EventPath`/`BlockPath` classes fold into their datatype's handler when its
increment comes. Handlers sit beside their values in `domain/value`, so a
datatype's data and behaviour share a package.

Per increment: find every arm (`instanceof XValue` / `case XValue` through
the IDE), move verbatim -- no improving while moving -- suite loop while
working, one full gate, commit.

# Goal 5 remainder

## 5d. Two live defects, each with a reproducer

- **A map matches a string key case-sensitively.** `Find_Entry` is called
  with `cased` false for FIND, SELECT and a path read -- only `find/case` and
  `put/case` pass true. The fix is a lookup that knows the flag, not a line
  in a native. Reproducer: `m: make map! [] m/("k"): 1 select m "K"` answers
  1 in Rebol and none here.
- **`load-json` cannot read a JSON array or an object.** `load-json "5"`,
  `"true"` and `"[]"` work; `load-json "[1]"` raises the codec's own error.
  `to-json` works both directions, so the encoder is the one to trust; points
  at PARSE rather than the codec. Reproducer: `load-json "[1]"`.

## 5e. Two Rebol files still stop partway

`BorrowedFilesLoadWholeTest` names them; each waits on a named feature:

| Wants | File |
| --- | --- |
| `binary`, the bincode dialect (`u-bincode.c`) | `prot-tls.reb` |
| the view dialect's `font` object | `view-funcs.reb` |

**One file is deliberately not loaded.** `mezz-osx-dialogs.reb` shells out to
`osascript`; JEBOL serves REQUEST-DIR/FILE/COLOR through the WINDOWS port,
which works everywhere and asks the host's grant first.

**Two ordering rules, learned the hard way:** mezz-tail.reb goes last of what
Rebol boots (it sets aliases the on-demand imports read, and PROTECT-SYSTEM
ends by unprotecting what REGISTER-CODEC writes to); codec-der.reb goes
before codec-crt.reb.

## 5f. The forks still in the prelude

Forty-six functions JEBOL implements that Rebol writes in REBOL. Each blocks
the R3 file that defines it from being loaded over the top.

**Check each by identity, not by datatype.** Rebol defines four of these as
aliases *to* natives (`max: :maximum`, `min: :minimum`, `abs: :absolute`,
`context: :object`), so `same? :max :maximum` is the question, not
`type? :max`. And `empty?` is neither: `mezz-series.reb` redefines it as
`make :tail? [...]`, which loads after and wins.

First step is a test rather than a judgement: assert of each of the 46 that
the word resolves to what the R3 file gives it. That turns 46 decisions into
a list of the real forks, and catches any fork that creeps back.

**32 in `prelude.reb`:**

| R3 file | functions in the prelude that belong to it |
| --- | --- |
| `base-defs.reb` | `body-of` `spec-of` `title-of` `types-of` `values-of` `words-of` |
| `mezz-func.reb` | `clos` `closure` `enum` `has` `map` |
| `base-files.reb` | `dirize` `script?` `split-path` `suffix?` |
| `mezz-control.reb` | `all-of` `any-of` `wrap` |
| `base-funcs.reb` | `cause-error` `default` `does` |
| `mezz-series.reb` | `collect` `empty?` `rejoin` |
| `mezz-files.reb` | `clean-path` `undirize` |
| `mezz-tail.reb` | `funco` `keys-of` |
| `base-constants.reb` | `max` `min` |
| `base-series.reb` | `join` |
| `mezz-types.reb` | `to-word` |

**14 in Java** that belong to a REBOL file:

| R3 file | functions in Java that belong to it |
| --- | --- |
| `base-files.reb` | `exists?` `load` `make-dir` |
| `mezz-files.reb` | `ask` `input` |
| `base-funcs.reb` | `function` `use` |
| `base-defs.reb` | `quote` `true?` |
| `base-constants.reb` | `abs` |
| `mezz-func.reb` | `context` |
| `mezz-series.reb` | `split` |
| `view-funcs.reb` | `view` |

**11 duplicated generators.** `base-defs.reb` generates the typeset
predicates from `system/catalog/datatypes`; JEBOL also writes eleven of them
in Java. The generator is the one to keep.

`abs` is the smallest and a good first move: `base-constants.reb` is already
loaded, so deleting the Java definition should need nothing.

## 5g. Loose ends

- **Datatype backlog: `vector!` and `task!`**, the next two in table order.
  They carry the last TYPES lines in the parity report.
- **The five unported `n-image.c` functions:** `resize`, `blur`,
  `premultiply`, `image-diff`, `image`. Not on any MISSING list because they
  are not declared in `boot/natives.reb`.
- **An error's fields are a record rather than a context**, so `e/id: ...` is
  refused where `boot/types.reb` gives error! the object path handler.
- **The console gets a shell written in Java, hexagonally.** Terminal side an
  adapter behind a port, the shape TTY? already uses: the domain asks
  `console().isATerminal()` and never sees a `java.io.Console`.
- **VID may come free now `image!` exists.** VID is a dialect and a dialect
  is REBOL, so `view-funcs.reb` and draw may load and work once the
  `n-image.c` functions are ported. Worth trying before designing anything.
- **Host object mutability** is decided in `docs/decisions.md` and not yet in
  the embedding documentation or the API javadoc.
- **`draw` dialect to SVG.** One renderer. Milestone 5's open fork in
  `docs/milestones.md` covers the thinking.

## Working notes

- **Multi-line REBOL collapsed onto one line feeds the first call the next
  one's arguments.** Write a probe to a file and run the file.
- **`-Werror` with `dangling-doc-comments`** means inserting code between a
  javadoc and its declaration fails the build.
- **zsh `no matches found` kills a `until ls *.xml` wait loop.** Use
  `find ... | wc -l`.

# Goal 6. The boot is 68ms, and everything is boot-bound

**Last on purpose.** An embedding that builds an interpreter per request pays
this on every one, and nearly every test asks for a fresh interpreter, so the
test wall clock is close to 68ms times the test count.

**`Interpreter.create()` costs about 68 milliseconds**; `run("1 + 1")` on a
warm one is too fast to measure. What the 68ms buys is loading and evaluating
the whole imported library, which produces the same result every time and is
thrown away after every test. Two things would have to be true to cache it:
the library context copied cheaply rather than rebuilt, and the copy deep
enough that a test mutating a library value cannot be seen by the next one.
REBOL series are shared and mutable, so the second part is the whole
difficulty and is why this is a goal rather than a tidy-up.
