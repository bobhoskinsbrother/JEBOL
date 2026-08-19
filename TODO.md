# TODO

Only work that is left. History lives in git and in `docs/`.

Everything below was checked on 2026-08-19 by running it, not by reading the
previous version of this file. Three items were already done and one listed
defect had fixed itself; both kinds of staleness are the same disease as
Goal 1b, so this file gets re-verified whenever it is read.

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
   for molding, `boot/errors.reb` for the error catalogue. The host is in
   `src/os/` and matters for graphics: `host-window.c`, `host-draw.c`.
4. `src/tests/units/*.r3` -- Rebol's own tests, a third authority and the
   cheapest one: read them before writing a line.

**There is now an `./r3` binary, gitignored, and it is a fourth authority.**
It settles what the C leaves ambiguous and it has killed a wrong reading every
time it has been used. It is for *checking an expectation before writing it
down*, not for deciding what the language is. Run a probe from a file, never
from `--do` with quoting in it.

## The regression floors

All three measures stay where they are. A change that moves any of them is
wrong.

- **`RebolSuiteTest`** -- Rebol's own 3721 assertions, green, ~13 seconds
  alone. The inner loop between changes.

  ```
  ./gradlew test --tests 'org.jebol.suite.RebolSuiteTest'
  ```

- **`ActionParityTest`** -- the datatype table times the arms table, ratchet
  at zero, nothing parked behind it.
- **`scripts/c-parity.py`** -- MISSING, WRONG LAYER, REFINEMENTS and ARGUMENTS
  all empty. TYPES prints 101 lines and every one is a known shape: 41 are
  JEBOL enumerating concrete datatypes where R3 writes `any-type!`, and 60 are
  `vector!` refusals from the datatype backlog. Nothing else is in there.
  **But see 1b: that MISSING is over an incomplete input.**

`./gradlew check` is the gate before a commit: **9297 tests, 0 failed,
0 skipped**, about three minutes.

`./gradlew browserCheck` is the second gate and is not optional, only
separate: it drives a real Chrome and compares what it paints against Java2D.
Run it after any change to `PaintList`, either renderer, or the page.
`scripts/c-surface.py` rebuilds `c-surface.txt` when Rebol's tree changes.

## How to port one function

Four steps, in order. Do not begin one before the last is finished.

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

**A spec is distilled from the Java at the end for ported functions, and
written first for anything new.** The rule is about where the truth lives: for
a port the C is the truth and a spec written before reading it disagreed every
time, but for something JEBOL is deciding for itself -- the screen, the paint
list, the renderers -- there is no C to distil and the spec comes first.

**Read the declaration and the arm as two different things.** A datatype the
spec block does not list is refused as `expect-arg` by the declaration; one
the spec lists and the arm turns away is `cannot-use`. A script can tell them
apart, and the C settles every case.

**The C has its own bugs, and copying one is not fidelity.** Where the C is
plainly wrong rather than surprising, write what it meant and record what it
does.

---

# Goal 1. The bugs. Before any refactoring

A refactor over known-wrong behaviour preserves the wrongness and makes it
harder to find. These go first.

## 1a. A map matches a string key case-sensitively

Still reproduces, checked 2026-08-19: `m: make map! [] m/("k"): 1
select m "K"` answers none here and 1 in Rebol.

`Find_Entry` is called with `cased` false for FIND, SELECT and a path read --
only `find/case` and `put/case` pass true. The fix is a lookup that knows the
flag, not a line in a native.

## 1b. Two measures report things that are not so

Both have the same disease and it is the one that has now hidden three
different things: they record **what a failure said** rather than what is
actually missing. A number gets written down, it reads like a work queue, and
nobody checks it against the thing it claims to measure.

It hid the sixteen missing `system/standard` fields for months. It let this
file claim `load-json` was broken long after it worked. And it is why
`MISSING: 0` cannot be trusted.

**`PortingBacklogTest` says 24 functions are missing. The real number is
zero.** `STILL_TO_PORT = 24` at line 58. Checked all twenty-four:

- **twenty-one are in `system/contexts/sys`** -- `do*`, `do-needs`, `export`,
  `export-words`, `init-schemes`, `load-header`, `load-module`,
  `make-module*`, `make-port*`, `make-scheme`, `start`, `bind-lib`, `log`,
  `read-decode`, `assert-utf8`, `mixin?`, `remove-ansi`,
  `download-extension`, `load-boot-exts`, `load-ext-module`,
  `locate-extension`. That is where Rebol puts them too. The test asks the
  library context alone, so it counts a correctly-placed function as a gap.
- **`limit-usage` is absent because Rebol removes it** --
  `mezz-secure.reb:334` is `unset in lib 'limit-usage`, which JEBOL runs
  faithfully. Its absence is the port working.
- **`completion!` and `line-editor!` are not functions.** They are objects in
  the console module, collected by `c-surface.py` as though they were.

Fix the measure to ask both contexts, drop the non-functions from the
collector, and let the ratchet say zero honestly.

**`c-parity.py` says MISSING: 0 over an incomplete input.** `binary` is
`REBNATIVE(binary)` in `u-bincode.c`, declared in the C rather than in
`boot/natives.reb`, so `c-surface.py` never collects it and the report cannot
name it. That one is now ported, but the blind spot is not: widen the
collector to the `REBNATIVE(...)` definitions and find out what else is behind
that zero. Until that is done, MISSING: 0 means "of what natives.reb and
actions.reb declare", which is less than what the C ships.

**Two measures are clean and worth keeping as they are:**
`ActionParityTest.KNOWN_GAPS` is 0 with nothing parked behind it, and
`spec/.allium-warning-allowlist` holds fourteen entries that are all one
documented checker gap, each confirmed with a minimal repro.

## 1c. LAYOUT is a stub that answers success

`layout` is a Java native that returns its argument unchanged, so a VID
program runs, reports success, and draws nothing. Checked 2026-08-19:
`type? :layout` is `native!` and its body is none.

It is worse than a fork, because there is nothing to fork: `layout` is defined
nowhere in `src/mezz` or `src/boot`, so a real 3.22.1 has no such function
either. JEBOL invented a stub that says yes. Its twin, the `view` identity
stub, has been replaced by the real thing; this one has not.

Delete it, or write VID (Goal 4d). A refusal would be better than what is
there now: `feature-na` tells somebody why nothing appeared.

## 1d. An error's fields cannot be written

`e/id: 'boom` is refused with `invalid-path` where `boot/types.reb` gives
error! the object path handler, so an error's fields are a record here and a
context there. Checked 2026-08-19.

# Goal 2. Un-smear the domain: one datatype, one place

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

**The graphics work has one lesson for this.** `PaintInstruction` is a sealed
interface, so adding a kind broke the switch in every renderer at compile
time and neither could quietly not handle it. That is the shape the action
seam should have: exhaustive over a closed set, so the compiler finds the arm
somebody forgot.

# Goal 3. The forks still in the prelude

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

**`view` has come off this list.** `view-funcs.reb` loads whole and defines
VIEW, UNVIEW, DO-EVENTS and the handler list, and the Java stub is deleted.
`type? :view` is `function!` rather than `native!`, which is how to tell.

# Goal 4. Graphics: what is built and what is left

The screen, the paint list, two renderers and the DRAW dialect are in. What
follows is what they do not do yet, and each gap is named rather than found.

**The design, so the gaps read correctly.** A gob tree is flattened once into
a paint list -- absolute positions, each instruction's own clip, opacity
already multiplied down the tree -- and a renderer executes it and decides
nothing. That is what holds a desktop window and a browser to the same
picture, and `browserCheck` compares them pixel for pixel with no tolerance.

## 4a. The DRAW commands not painted

Each is out because the two renderers would not agree about it, not because it
was forgotten. A command nobody paints is skipped so the rest of a block still
draws, and the gap is the same gap in every renderer because it is decided in
`DrawDialect` rather than three times.

`grad-pen` (both do linear and radial, neither does conic, diamond or cubic
the same way), `line-pattern` (dash phase and how a pattern restarts at a
join), `image` and its three option commands (scaling quality is a
rasteriser's own business), `text` (glyphs never match, and it needs the TEXT
sub-dialect, which is another entry in `system/dialects`), `arrow`, `gamma`,
`effect`, `spline` (nothing says which interpolation), `triangle`'s Gouraud
shading, `invert-matrix` and `transform`.

## 4b. A stroked curve will not compare pixel for pixel

`browserCheck` asserts zero differing pixels and only ever draws flat
axis-aligned fills, because that is what two rasterisers agree about exactly.
Joins, miter limits and how a cap meets a curve all differ slightly and no
shared input fixes it. Either state a tolerance for those cases or assert the
path rather than the pixels -- decide before writing a test that quietly
widens the tolerance for everything.

## 4c. The old markup path is still there

`org.jebol.render` and `domain/render/{Face,Html,Layout}` -- 522 lines that
read a VID-ish block straight into HTML and never make a gob. Reached by
nothing but their own two tests. They come out when VID is written in REBOL,
not before: nothing gets destroyed ahead of its replacement.

## 4d. VID does not exist and is JEBOL's to write

`layout` is defined nowhere in `src/mezz` or `src/boot`, and
`view-funcs.reb:117` calls `layout/background` anyway, so a stock 3.22.1 fails
there too. VID is a dialect, a dialect is REBOL, and it belongs in a `.reb`
file rather than in Java. It compiles to gobs, which is what makes one VID
work on all three renderers. `Layout.java` holds the face-kind and colour
tables that a REBOL VID would need -- read them before deleting 4c.

## 4e. Android is the third renderer, and the renderer is not the risk

Two files are Swing-specific -- `DesktopPainting` and `DesktopScreen`, about
560 lines of 38,000 -- and `java.awt`/`javax.swing` appear in exactly three
files, all under `adapter/host/`. Android's `Canvas` maps onto the paint list
almost call for call.

**Spike the interpreter first.** 437 pattern-matching switch sites, 735
instanceof patterns, 43 files with records and 5 sealed hierarchies all go
through D8's desugaring rather than the JVM. Nothing uses the Foreign Function
and Memory API yet, so the hard blocker is not in place -- but compile the jar
with D8 and run one script in an emulator before any renderer work. If it does
not desugar, no amount of renderer work helps.

## 4f. Smaller ones

- **Events name the wrong window when a page has two.** A browser reports a
  click without saying which window it was in, and `WebScreenServer` picks the
  first showing. Right with one window, a coin toss with two.
- **`view` blocks until the last window closes**, which sits badly with a host
  running scripts under a deadline. Recorded as an open question in
  `spec/screen.allium` rather than decided.
- **A gob's `text` on a window is a title.** Handled, but the same trap waits
  wherever else a field means two things by where it sits.

# Goal 5. Loose ends

- **Datatype backlog: `vector!` and `task!`**, the next two in table order.
  They carry 60 of the 101 TYPES lines in the parity report.
- **The console gets a shell written in Java, hexagonally.** Terminal side an
  adapter behind a port, the shape TTY? already uses: the domain asks
  `console().isATerminal()` and never sees a `java.io.Console`.
- **Host object mutability** is decided in `docs/decisions.md` and not yet in
  the embedding documentation or the API javadoc.
- **The schemes JEBOL does not serve.** `console`, `tcp`, `dns` and `event`
  are registered. The JDK covers `udp` (`DatagramSocket`), `file` and `dir`
  (`java.nio.file`, and `FilePort` exists), `checksum` (`MessageDigest`),
  `crypt` (`javax.crypto`), `clipboard` (`java.awt.Toolkit`) and `midi`
  (`javax.sound.midi`). `serial` has no JDK support.
- **TLS is loaded but not connected.** `prot-tls.reb` registers a whole
  scheme with its actor; nothing wires it to the TCP port beneath it.
- **48 open questions across ten spec files**, each a parked decision. Worth a
  pass to see which have answered themselves.

# Goal 6. The boot is 68ms, and everything is boot-bound

An embedding that builds an interpreter per request pays this on every one,
and nearly every test asks for a fresh interpreter, so the test wall clock is
close to 68ms times the test count.

**`Interpreter.create()` costs about 68 milliseconds**; `run("1 + 1")` on a
warm one is too fast to measure. What the 68ms buys is loading and evaluating
the whole imported library, which produces the same result every time and is
thrown away after every use.

There are two separate fixes and they compose. Do the first one first.

## 6a. A pool, so nobody waits for the boot

Keep a few built interpreters ready. A caller takes one, uses it, throws it
away, and a builder thread starts its replacement. The 68ms still happens; it
happens off the critical path, on a core that was idle anyway -- the full run
sat idle on eleven of twelve cores.

Cheap and safe, because it changes nothing about what an interpreter is:
every caller still gets a genuinely fresh one, so no word from a previous use
can leak. That is the property the caching fix has to work for and this one
gets for free.

Three things to get right:

- **The handoff is not sharing.** `Interpreter`'s contract is one instance,
  one thread. Building on a pool thread and using on a caller thread is fine
  where the queue that carries it establishes a happens-before -- a
  `BlockingQueue` does. Two threads holding one instance at once is still a
  mistake, and the pool must make it impossible rather than merely unlikely.
- **It bounds latency, not total work.** The CPU cost is unchanged, so a
  caller arriving faster than a builder can replace what it took waits
  anyway. Size the pool and the builder count against the arrival rate, and
  measure rather than guess.
- **It belongs to the host, not the domain.** A pool is an application-layer
  concern with a thread in it; keep it out of `domain`.

Worth having in the test run and in any server embedding, and it needs
nothing from 6b.

## 6b. Cache the built library, so the boot is cheaper

The remaining prize is the 68ms itself. Two things would have to be true:
the library context copied cheaply rather than rebuilt, and the copy deep
enough that one user mutating a library value cannot be seen by the next.
REBOL series are shared and mutable, so the second part is the whole
difficulty, and it is why this is a goal rather than a tidy-up.

# Working notes

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
