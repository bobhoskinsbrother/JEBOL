# TODO

## The goal

**Port every function a real Rebol 3 has -- the C natives and the REBOL
mezzanine alike -- into JEBOL.** The imported Rebol test suite is how a
port is checked, not what is being aimed at.

The distinction is not pedantic and it has already cost time. A passing
assertion is evidence a port is right; a failing one may be about a
function that is there and wrong, or about setup three tests earlier, or
about nothing much. A function that is missing altogether usually shows up
as no failure at all. Working the failure report therefore finds a
different set of things from working the porting list, and only one of
them is the goal.

**The porting list is `PortingBacklogTest`.** It prints what R3 has and
JEBOL has not, grouped by what is blocking each part, and it asserts the
count so that ignoring it breaks the build. `SuiteFailureReportTest` is
the other half of the picture and answers a different question: of what
has been ported, what is wrong.

Read the backlog first. Reach for the failure report when a port needs
checking, or when the backlog is empty.

## Committing

**Nothing is committed until every test passes.** That includes the
imported Rebol suite: 3721 assertions, all of them green, and not a
scoreboard that goes up.

This is settled. Do not raise it again, do not ask whether the suite
counts, and do not offer to commit "the safe part". There is no safe part
until the port is finished.

## How to port one function

Four steps, in this order. Do not begin one before the last is finished.

1. **Read the C.** `src/core/*.c` for a native, `src/mezz/*.reb` for the
   rest. Read the whole function, not the part that looks relevant. The C
   carries the rules that no probe will ever show you: which flags are
   set together, what a count of zero means, where a search starts.
2. **Copy the logic into Java.** Follow the C's structure, not your own
   idea of how the function should work. Where the C has a branch, have a
   branch. Where it has a special case, have that case. A rewrite that
   looks tidier is a rewrite whose bugs are yours and not Rebol's.
3. **Write the tests from the C, not from your Java.** Read the C again
   and write a test for every branch, every flag combination, every
   boundary it guards. Tests written by reading the port only prove the
   port agrees with itself.
4. **Run the imported suite.** It is Rebol's own, thus a failure there is
   a rule the C states and the port missed.

Probing `./r3` is the last resort and not the first. Its console
interleaves prompts with printed output, thus a bare probe is easy to
misread -- four defects were claimed and withdrawn in one afternoon that
way. If a probe is needed, print a label and run the same file through
both.

## Where the answers come from

Three sources, in this order.

1. **Rebol's own C source**, at https://github.com/Oldes/Rebol3. Clone it
   and read `src/core` for the natives and `src/mezz` for the rest. This
   says what a function does and why, and it settles a question in one
   reading that a hand probe settles in ten.
2. **Rebol's own test suite**, already here in
   `src/test/resources/rebol-suite/`. This says what the behaviour must
   be rather than how one implementation reaches it, thus it is the
   better source when the two seem to disagree.
3. **The `./r3` binary**, for a question the first two leave open.

Reaching for the binary first is a mistake that has already cost this
project time. A hand probe gives one answer to one question. The source
gives the rule, and the rule is what a port needs.

## Working notes

Practical things a fresh session has to know, each of which cost time to
find out.

**Get the C source first.** It is not in this repository and every port
depends on it.

```
git clone --depth 1 https://github.com/Oldes/Rebol3 <somewhere outside the repo>
```

Read `src/core/t-*.c` for the datatypes, `src/core/n-*.c` for the natives,
`src/core/c-do.c` for the evaluator, `src/core/l-scan.c` and `l-types.c`
for the reader, `src/boot/actions.reb` and `natives.reb` for argument
specifications, `src/boot/errors.reb` for the error catalogue, and
`src/mezz/*.reb` for the REBOL half of the library.

**Do not use `./gradlew run` to try something out.** It starts the REPL
and waits on standard input, so it hangs until it is killed. To probe,
write a throwaway JUnit test and delete it afterwards:

```java
// src/test/java/org/jebol/Probe.java
String[] cases = { "mold 1.2.3", "length? 'a" };
for (String source : cases) {
    Interpreter interpreter = Interpreter.create();
    interpreter.defineFreshWordsIn(source);
    System.out.println("  " + source + "   ==>   "
            + interpreter.display(interpreter.run(source)));
}
```

Run it with `./gradlew test --tests 'org.jebol.Probe' -i` and filter for
the arrow. Always print a label beside the answer; unlabelled output is
how four defects were claimed and withdrawn in one afternoon.

**Reading the suite result.** `./gradlew check` is the gate. The report
is in `build/test-results/test/TEST-org.jebol.suite.RebolSuiteTest.xml`,
one `<failure>` per assertion, and the assertion text is in the message.
Two things to watch: every failure appears twice in that file, once in
the attribute and once in the body, so halve any count taken by grepping;
and running a single test class deletes the suite's XML, so re-run the
suite before reading it again.

**Writing a test from the C.** The shape used throughout: a private
`answerTo(String)` that builds an `Interpreter`, calls
`defineFreshWordsIn` and then `run`, and a private `errorIdOf(String)`
that wraps the source in `try` and answers `e/id` or the word `no-error`.
Name the file `<Thing>FromTheSourceTest` and cite the C function in the
class javadoc, so a later disagreement is settled by reading that
function rather than by argument.

Three traps in those tests. JEBOL molds none as `_`, not `#(none)`. A
literal block compares as words, so `x = ["a" true]` is false and
`x = reduce ["a" true]` is what was meant. And `e: try [...]` followed by
`error? e` raises when the body answered unset, so use `unset? do ...`
for those.

**The build is strict.** `-Werror` is on with `dangling-doc-comments`, so
inserting a helper between a javadoc and the declaration it documents
fails the build. `DependencyRuleTest` enforces the hexagonal boundary:
the domain imports nothing from `application` or `adapter` and does no
I/O, so a domain class reaching `java.io.File` is a build failure rather
than a review comment.

**Commits use `hoskins_ben@hotmail.com`.** It is set repository-locally
already. Do not change it and do not fall back to the git global, which
is a work address.

**`known-gaps.txt` is deliberately empty of gaps.** It is not a skip
list: every assertion runs on every build. Leave it empty.

**`src/test/resources/r3/surface.txt`** is the checked-in record of R3's
library: 580 functions with their full argument and refinement shapes.
`PortingBacklogTest` compares it against a booted interpreter and asserts
the difference, so the count only ever goes down.

## The three goals

The remaining work splits by subsystem rather than by count, because each
subsystem is one part of the interpreter with its own C source and can be
finished and gated on its own. Counts are of failing suite assertions at
3221 of 3721 passing, and are approximate.

Take them in the order given. Goal 1 is a dependency for the load and save
assertions that sit in other files; goal 3 is the largest and wants
splitting in two by the time it is reached.

### Goal 1 -- the reader and the molder (~155)

lexer-test 91, load-test 32, mold-test 30.

Source: `l-scan.c`, `l-types.c`, `s-mold.c`. These go together because
molding is the inverse of reading, and a value cannot be made to write
back correctly while it still reads wrongly.

The known pieces: which character runs form a word (`Lex_Map` and
`Scan_Token`), what an invalid construction raises and with which id, raw
strings, the literal none, TRANSCODE's error reporting, and molding a
url.

One piece is already known and unported: `deci_to_string` uses the
exponent form when a money's digits run out, so `mold $1e-100` is
`$1e-100` and JEBOL writes a hundred zeros. Nothing in the suite asserts
it, which is why goal 2 left it.

### Goal 2 -- the scalar datatypes -- **DONE**

money 21, pair 24, decimal 20, compare 27, integer 10: all green.

Source: `t-money.c`, `t-pair.c`, `t-decimal.c`, `t-integer.c`,
`n-math.c`. Five ports of the shape the tuple port took: read the
datatype's C file, copy its actions, write the tests from the C.

What the C settled that no probe would have. A pair's halves are C floats,
so a half above 3.4e38 is infinite and `mold 2147483647x1` is
`2.147484e9x1`. A money is ninety-six bits, so doubling $1 five hundred
and nine times raises where an unbounded decimal answers happily. The
decimal equality allowance is twenty-one steps and not ten. There are two
comparison functions with different coercion rules and Rebol runs both, so
`equal? "a" %a` is true and `equal? ["a"] [%a]` is false. `//` is
integer-divide. Four spellings cover three definitions of remainder.

Two things were found by finishing it rather than by planning it. An error
carried one of the three arguments its catalogue entry words, so
`e/arg3 = integer!` was false for every expect-arg; widening that was a
gap the code already had a comment about. And four existing JEBOL tests
asserted behaviour the C contradicts -- pair infinities, the zero
rounding scale, string-datatype equality, and `//` as remainder -- each of
which had looked right from every angle but the C.

### Goal 3 -- series, evaluation and objects (~330)

evaluation-test 102, series-test 98, parse-test 56, object-test 35,
protect 18, func 13, typeset 13.

Source: `f-modify.c`, `t-block.c`, `t-string.c`, `c-do.c`, `c-frame.c`,
`u-parse.c`.

Split this in two when it is reached: series and PARSE in one, evaluation
and objects and protection in the other. Nothing in it spans two C files
that have to change together, so the cut is free.

## What blocks the backlog now

Three things, and none is more porting. `docs/surface-audit.md` has the
detail.

1. **The host boundary.** It blocks 40 of the 88 C natives and 5 more
   written in REBOL. `spec/embed.allium` now specifies it: eight kinds of
   host service, granted one at a time, none by default, and a refusal
   that says which of three reasons applies. The code is not written.
2. **The user context is not published.** `intern` and `module` need
   `system/contexts/user`. JEBOL has the context -- source read at run
   time binds into it -- but nothing exposes it under that name, so no
   REBOL code can reach it. Exposing it is the work, not building it.
3. **No console layer.** `dump-obj`, `help`, `about`, `usage` and the
   five `log-*` functions need `emit`, `reform`, `ansi` and
   `system/options`.

The milestones from `docs/milestones.md` as user stories. Reasoning for
the ordering lives there; decisions and their consequences live in
`docs/decisions.md`.

Every story follows the same three passes, so it is not repeated on each
one: specify in Allium and get `./gradlew check` clean, write the tests
from the spec and confirm them red, then build against them.

Roles:

- **script author** — writes REBOL that runs on JEBOL
- **host developer** — embeds JEBOL in a Java application
- **operator** — runs it in production
- **maintainer** — works on JEBOL itself

---

## Done

- [x] **As a maintainer, I want the value model specified**, so that
      datatypes, series storage and binding are decided rather than
      discovered while coding.
      *`spec/values.allium`, checks clean.*
- [x] **As a maintainer, I want reading specified**, so that TRANSCODE and
      LOAD are separable and syntax failures are values.
      *`spec/load.allium`, checks clean.*
- [x] **As a maintainer, I want the evaluation walk specified**, so that
      dispatch, argument gathering and error propagation are pinned.
      *`spec/eval.allium`, checks clean.*
- [x] **As a maintainer, I want real REBOL to test against**, so that
      correctness is measured against published behaviour rather than
      against my own expectations.
      *88 corpus entries and 14 complete programs in `corpus/`.*
- [x] **As a maintainer, I want a spec gate in the build**, so that
      specifications cannot drift unnoticed.
      *`./gradlew check` runs it; proven to fail on a new warning.*
- [x] **As a maintainer, I want infix operators specified**, so that the
      walk accounts for them rather than having them bolted on.
      *No precedence, strictly left to right, in `spec/eval.allium`.*
- [x] **As a maintainer, I want path evaluation specified**, so that
      selecting and calling through a path is decided.
      *`spec/eval.allium`, including refinements and set-paths.*
- [x] **As a maintainer, I want the native set specified**, so that the
      awkward semantics are pinned before any of them are written.
      *`spec/natives.allium`. The list of forty is still an open question.*
- [x] **As a maintainer, I want the console specified**, so that its two
      conveniences stay out of the language.
      *`spec/repl.allium`.*
- [x] **As a maintainer, I want the test obligations listed**, so that the
      audit has something to run against.
      *359 obligations in `docs/obligations.md`, regenerated by
      `scripts/obligations.py`. 96 attributed so far.*

---

## Milestone 1 — the language runs

**Done when** every corpus entry passes except the one marked `r2-only`,
and all fourteen programs in `corpus/sources/` load and survive a round
trip through MOLD. **Met**, with the caveats under "still owed" below.

### Reading

- [x] **As a script author, I want to load a source file and get the
      values it describes**, so that I can inspect and rewrite code before
      running it.
      *All 14 files transcode; words come back unbound; every series is at
      its head. `SourceProgrammeLoadingTest`.*
- [x] **As a script author, I want every literal form read correctly**, so
      that `1.2` is a decimal, `1.2.3` is a tuple, `-1` is one value and
      `- 1` is two.
      *The 26 entries in `corpus/loading.corpus` pass, including
      `window/pane/:n/color: clr` as a single set-path.*
- [x] **As a script author, I want a syntax error to name a line and
      column**, so that I can find it without bisecting the file.
      *`SourcePosition` on every failure; reported as an `error!` of
      category `syntax`, never as a host exception.*
- [x] **As a script author, I want anything MOLD prints to read back
      equal**, so that code-as-data survives a round trip.
      *`MoldRoundTripTest` over all 14 files, and the round trip is stable
      rather than merely working once.*

### Evaluating

- [x] **As a script author, I want a block evaluated left to right with
      the last value as its result**, so that `do [add 1 2  add 3 4]` is 7.
- [x] **As a script author, I want `1 + 2` to be 3**, so that I can write
      ordinary REBOL rather than a prefix-only dialect of it.
      *And `2 + 3 * 4` is 20, because there is no precedence. Running it
      is what found that bug: the first version gave 14.*
- [x] **As a script author, I want `face/color` to select through
      values**, so that I can read structured data.
      *Paths select and call; refinements are gathered once a function is
      reached. Set-paths assign through.*
- [x] **As a script author, I want zero, empty strings and empty blocks to
      be true**, so that conditionals behave as REBOL does rather than as
      the language I arrived from.
- [x] **As a script author, I want an unset word to raise an error I can
      catch**, so that a typo does not silently produce nothing.
      *`no-value` and `not-defined` are distinguished; `try` hands the
      error back as a value.*
- [x] **As a script author, I want a failed assignment to leave the word
      alone**, so that a word never holds a half-computed result.

### The library and the shell

- [x] **As a maintainer, I want the native set specified and built**.
      *`spec/natives.allium`, 55 natives and 12 operators. Which forty
      exactly is still an open question; what exists is what the corpus
      asked for.*
- [x] **As a script author, I want a REPL**, so that I can try something
      without writing a file.
      *Reads, evaluates, prints, loops. Errors end the expression, never
      the session. Multi-line input is decided by asking the reader rather
      than by counting brackets, so a brace inside a string is not an
      unclosed brace.*

### Running it safely

- [x] **As an operator, I want a runaway script to stop rather than take
      the process down**, so that one bad script does not cost me the
      server.
      *Nesting past 1000 is a syntax error; no `StackOverflowError`
      escapes. See "still owed" for why the bound is at the reader.*
- [x] **As an operator, I want interpreter instances isolated**, so that
      two scripts cannot see each other's values.
      *An instance owns its contexts and everything reachable from them.*

### Still owed on milestone 1

Nothing. All 359 obligations in `docs/obligations.md` are attributed to a
test, the evaluator keeps its state on the heap, and the corpus covers
functions, loops, series, branching, non-local exit, parameters and the
failure branches.

Two things were found by finishing it rather than by planning it. The
corpus had no loops chapter and no functions chapter, so 60 green entries
were describing a language nobody could write a program in. And the audit
turned up 68 obligations with no test beside them, of which the path
failures and the unset-condition failures were real gaps; writing those
found that `nothing/here` blamed the path when the word was the problem.

---

## Milestone 2 — objects and contexts

- [x] **As a script author, I want `make object!`**, so that I can group
      data and the functions over it.
      *Fields are the set-words in the body, defined before it runs so a
      method can see a field declared after it.*
- [x] **As a script author, I want predictable binding rules**, so that I
      can tell which context a word resolves in without experimenting.
      *An object hangs beneath where it was written, so a word it does not
      define still means what it meant there. Field selection uses the
      object's own words only, or every global would look like a field.*
- [x] **As a script author, I want `in`, `bind` and `context`**, so that a
      field can be reached by a name worked out at runtime.
- [x] **As a script author, I want a copy of an object to be independent**,
      so that two instances do not tread on each other.
      *Including its methods, which is the part that was broken: a copied
      function still closed over the object it was written in, so depositing
      into the copy moved money in the original. Found by running it.*

Not done: modules. They are a bigger thing than objects and nothing yet
needs them.

---

## Milestone 3 — the standard library

**Done when** a second corpus, harvested from the Core guide chapters not
yet used, passes.

- [x] **As a maintainer, I want that corpus harvested**, so that the
      library has a target before it has code.
- [x] **As a script author, I want the series natives**, so that I can
      take things apart and put them back together.
- [x] **As a script author, I want the string natives**, so that I can
      work with text without dropping to the host.
- [x] **As a script author, I want arithmetic that raises on overflow
      rather than wrapping**, so that a wrong answer is never silent.
- [x] **As a script author, I want money arithmetic that keeps its
      precision**, so that `$1.50` stays `$1.50`.
      *Both open questions are now answered from the C. `$1.50` equals
      `$1.5` and molds with its trailing zero: the scale is kept for
      printing and ignored for comparing. Division rounds to twenty-six
      significant digits, because that is all an eighty-seven bit
      significand holds, and going past it raises overflow rather than
      widening.*

---

## Milestone 4 — the host boundary

**Done when** Java creates a context, evaluates REBOL that calls a Java
method, and reads a REBOL value back out.

- [x] **As a host developer, I want to create an interpreter, feed it
      source and get values back**, so that I can embed JEBOL in a server.
- [x] **As a script author, I want to call Java from REBOL**, so that I do
      not have to reimplement libraries that already exist.
- [x] **As a host developer, I want to hold and inspect a REBOL value**,
      so that results are usable without being stringified first.
- [x] **As a host developer, I want a Java exception to arrive as a
      catchable `error!`**, so that the promise that nothing escapes as a
      host exception still holds across the boundary.
- [x] **As a maintainer, I want the conversion rules decided**, so that it
      is clear which values cross automatically and which stay REBOL.
      *`integer!` to `long`, `block!` to `List`, every throwable to an
      `error!`. Decision 12 in `docs/decisions.md`.*
- [ ] **As a host developer, I want to be told plainly that host object
      mutability is my problem**, so that I do not assume an isolated
      interpreter isolates the objects I hand it.
      *Accept: it is stated at the point a host object is handed over, in
      the embedding documentation and the API javadoc, not only in
      `docs/decisions.md`. Decided; not yet written where it will be read.*

---

## Milestone 5 — rendering: dialects to markup

**Done when** the seven demo programs with no event handlers render to
markup: `color-names.r`, `diagram.r`, `tile-game.r`, `emailer.r`,
`feedback.r`, `font-lab.r`, `effect-lab.r`.

- [x] **As a maintainer, I want the faithful-versus-VID-shaped fork
      decided**, so that the layout work has a target. *Open in
      `docs/milestones.md`; recommendation is VID-shaped.*
- [x] **As a script author, I want a VID layout rendered to HTML and
      CSS**, so that I can describe a page in a dialect rather than in
      markup.
- [ ] **As a script author, I want the `draw` dialect rendered to SVG**,
      so that I can produce vector graphics from a block.
- [x] **As a script author, I want pairs and tuples to mean what they look
      like**, so that `140x32` is a size and `0.0.150` is a colour without
      me converting them.
- [x] **As a host developer, I want rendering to be a pure function from
      values to markup**, so that I can cache it and serve it from
      anywhere.

---

## Milestone 6 — interactive rendering

**Done when** the seven demos with event handlers work in a browser:
`clock1.r`, `clock.r`, `calculator.r`, `rebodex.r`, `gel.r`, `mines.r`,
`rebtris.r`.

- [x] **As a maintainer, I want an event model that survives a round
      trip**, so that `feel` and `engage` blocks mean something when the
      browser is not the same process. *REBOL's own model never had to
      deal with this.*
- [x] **As a script author, I want my handler to run when a user clicks**,
      so that a layout can do something.
- [x] **As a script author, I want the view updated after a handler
      runs**, so that I do not manage the DOM myself.
- [x] **As an operator, I want a handler bounded in time**, so that a slow
      script does not hold a request open.

---

## Milestone 7 — PARSE

- [x] **As a maintainer, I want a PARSE corpus**, so that the dialect has
      a target before it has code.
- [x] **As a script author, I want to match and pull apart input with
      PARSE**, so that I can use the feature REBOL is known for.

---

## Milestone 8 — ports and I/O

**Done when** the five demos needing it at runtime work: `effect-lab.r`,
`feedback.r`, `gel.r`, `rebodex.r`, `rebtris.r`.

- [x] **As a script author, I want to read and write files**, so that a
      script can work with data on disk.
- [ ] **As a script author, I want to fetch a URL**, so that a script can
      reach a service.
- [x] **As an operator, I want I/O bounded and refusable**, so that a
      script cannot read anything it likes on my server.

---

## Not on the list

Native desktop windows. The fourteen demo programs will never open one.
They are a loader corpus, and from milestone 5 a rendering corpus, and
that is all they are.
