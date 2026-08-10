# Decisions

What was decided, when, and what it rules out. Newest last.

## 0. Why this exists

To run REBOL in an ordinary web production environment, and to get the
operational benefits of the JVM while doing it: an ordinary jar on any
JDK, deployed down the pipeline that already exists, watched with the
tools the operations team already has, in the containers everything else
already runs in.

That is the point. **Interoperability is not.** Building a whole REBOL
implementation so that REBOL could call Java would be a poor trade;
plenty of things call Java already. Two-way interop is in scope because
it is useful once you are here, not because it is the reason for coming.

This matters because it decides arguments that otherwise go in circles:

- **It settles the workload question.** A web production environment
  means many short, request-scoped scripts rather than a few long ones.
  Warmup dominates and per-instance cost is the number that matters, so
  an interpreter wins and a compiler would never amortise.
- **It is the real case against Truffle**, and a better one than the
  argument first made here about runtime distribution, which has since
  expired: Truffle languages have been ordinary Maven artifacts on a
  standard JDK since 23.1. The objection that survives is operational.
  A polyglot context is a heavyweight thing to hold per request, and what
  a profiler shows you is the framework's frames rather than yours.
- **It separates embedding from interop**, which had been treated as one
  thing. Embedding is a Java application creating an interpreter, running
  a script and getting a value back, under a time and memory bound it
  sets. Interop is REBOL code calling Java. The first is what the purpose
  above requires; the second is optional on top of it.

## 1. Target R3-Alpha, not REBOL 2 or Red

Unicode strings natively, which suits the JVM, and it is the version with
the most surviving reference material.

Rules out matching REBOL 2 output where the two differ.

**Corrected.** This entry used to give decimal printing as an example of
such a difference, claiming R3 molds the shortest form that reads back
while REBOL 2 gives fifteen digits. That was reasoning, not evidence, and
it was wrong. Running a real R3 (Oldes 3.22.1, arm64 macOS) shows
`mold 10 / 3` giving `3.33333333333333` and `mold 0.1 + 0.2` giving `0.3`.
The Core guide was right and this repo was wrong for several days.

Worth naming the shape of the mistake, because it was a comfortable one:
the wrong answer was more interesting than the right one, arrived with a
JDK probe attached that made it look verified, and the verification was of
Java rather than of REBOL.

## 2. Gradle, Java 21, no runtime dependencies

`./gradlew check` runs the spec gate as well as the tests, so the
specifications are validated by the same command as the code.

## 3. An interpreter, with its own evaluation stack on the heap

Not a compiler to JVM bytecode, and not GraalVM Truffle.

**Why.** REBOL resolves arity at evaluation time from a word's current
binding, so `foo 1 2` cannot be turned into a call tree until you reach
it. Blocks are mutable and programs build their own code at runtime;
`corpus/sources/color-names.r` appends to a block in a loop and then
evaluates it. Anything compiled ahead of time is a guess that needs a
guard and somewhere to fall back to, and that somewhere is an interpreter.
So the interpreter is needed whichever path is taken. It is the semantic
reference, the cold path, and the fallback.

Truffle was ruled out on ecosystem grounds rather than technical ones. A
Truffle implementation only performs on GraalVM; on a stock JVM it
degrades to a plain interpreter. That hands whoever operates it a runtime
requirement instead of a jar, in exchange for a performance benefit not
yet shown to be needed.

**The stack is the part that cannot be retrofitted.** Evaluation state
lives in heap structures the interpreter owns, not in JVM stack frames.
That is what makes a running script interruptible, bounded in memory, and
suspendable, and those follow from JEBOL being meant to run server-side.
An evaluator that recurses on the JVM stack cannot be given any of them
later without being rewritten.

**What this does not settle.** Whether an interpreter instance may be used
from more than one thread, what may cross between instances, and whether
interruption is promised to scripts at all. Those are still open questions
in `spec/values.allium` and `spec/eval.allium`, and they are spec
decisions rather than consequences of this one.

**What stays available.** Compiling blocks into a tree of executable
objects with inline caches, if measurement asks for it. That keeps the
heap stack and stays plain Java. JVM bytecode generation stays a third
step, for code proven hot and stable, and is not planned.

## 4. One interpreter instance, one thread

An instance is single-threaded and owns every value reachable from it.
Concurrency comes from running many instances, not from sharing one.

This is what makes the value model tractable. Series values share mutable
storage by design, so aliasing is observable; confine that to one thread
and it needs no synchronisation at all. Sharing storage across threads
would mean synchronising nearly every operation, for a gain nobody has
asked for.

Rules out shared mutable state between concurrently running scripts. Two
scripts that need to talk do it through whatever the host provides, not by
holding values into the same series.

## 5. Infix operators and path evaluation are in milestone 1

Without them milestone 1 can read real REBOL and cannot run it. The
fourteen demo programs use operators 205 times across 10 files and paths
478 times across all 14. The Core guide's first example is `1 + 2`.

Makes milestone 1 larger. Makes it able to run the corpus, which was the
point of gathering one.

## 6. Two-way Java interop

REBOL code can call Java and hold Java objects. Java can hold and inspect
REBOL values.

This is the largest of the four decisions and the one with consequences
the specs do not yet carry. It puts a new datatype in the value model for
a Java value, which means every rule that dispatches on datatype has a
case it did not have, and MOLD has to print something for a thing that has
no REBOL spelling. The boundary is a surface to be specified, not an
implementation detail.

Chosen over embedding-only because the ecosystem argument that ruled out
Truffle cuts the same way here: a language on the JVM that cannot reach
the JVM's libraries is a language that has to reimplement them.

## 7. The lexical types stay lexical

`email!`, `url!`, `tag!`, `issue!` and `tuple!` are syntactic shapes, as in
R3-Alpha. A tuple will hold `999.999.999` and an email is not checked
against RFC 5322.

Accepts every source R3-Alpha accepts and keeps the reader simple.
Validation is a library concern.

Worth naming the tension: this is the one decision that runs against the
no-primitive-obsession rule in the global instructions. The types still
carry meaning and cannot be confused with each other, which is most of the
benefit; what they do not do is reject nonsense at construction.

## 8. money! is a BigDecimal with a currency designator

R3-Alpha's `money!` is a 128-bit type with 26 significant digits, an
exponent from -128 to +127, and no normalisation, so `$1.50` keeps its
trailing zero. A currency designator of up to three characters sits
alongside. Earlier notes in this repo said it was a lossy decimal; that
was REBOL 2 being described, and it was wrong.

BigDecimal maps onto that closely: 26 digits is a `MathContext` and
BigDecimal preserves scale, which is what non-normalised means.

Integer minor units, which the global instructions give as the reference
model for money, would have been wrong here twice over. Twenty-six
significant digits do not fit in a long, and a bare `$1.50` names no
currency whose minor unit could be looked up. That rule is about modelling
a domain; this is implementing a language whose money type is already
specified as a wide decimal.

Two consequences are recorded as open questions rather than assumed:
whether `$1.50` equals `$1.5` under `=` and under `==`, since BigDecimal
answers differently for `equals` and `compareTo`; and what precision and
rounding division uses, since `$10 / 3` does not terminate.

## 11. Five choices the implementation made, now ratified

These were decided while building milestone 1 and were working before they
were written down. Recorded here so they are decisions rather than
accidents, and so reversing one is a visible act.

**Incomplete console input is detected by asking the reader, not by
counting brackets.** The console offers the text to TRANSCODE and treats
`missing-close` and `unterminated-string` as "want more" and everything
else as a mistake. Counting delimiters gets a brace inside a braced string
wrong, and a braced string is exactly what spans lines.

**The console prints `==` before a value**, as R3-Alpha does. Presentation
rather than behaviour; the corpus asserts on the molded value either way.

**Arithmetic widens integer to decimal to money.** `add 1 1.5` is a
decimal, `add $1.00 1` is money. Integer division that does not divide
evenly gives a decimal, so `divide 10 3` is not 3.

**`=` ignores case and `==` does not**, for strings and for words. Java's
`equals` is the strict one, so `equals` is `==` and the looser comparison
is a named method. Whether that extends to `file!`, `url!` and `tag!` is
still open.

**Nesting is bounded twice**: the evaluator refuses more than 10000 nested
evaluations, and the reader refuses source nested deeper than 1000. The
reader's limit is the one that actually fires, which is a consequence of
the evaluator still recursing for nested blocks. See the note in README.

## 12. Two-way interop: what crosses and how

**`integer!` becomes a `long`. `block!` becomes a `List`.** The obvious
mappings, taken rather than agonised over. Everything without an obvious
counterpart stays a REBOL value the host must ask about.

**Every host throwable becomes an `error!`.** No exception crosses into
REBOL as an exception, which keeps the promise that a script can catch
anything a script can cause. A host exception caught by REBOL and rethrown
arrives back at the host as an error value rather than the original
throwable; that is a real loss and it is the price of the guarantee.

**Mutability of host objects is the caller's problem, and must be
documented as such.** An interpreter instance is single-threaded and owns
its REBOL values, but a Java object it holds may be shared and mutated by
anyone. JEBOL makes no promise about it. This is the one place the
isolation story has a hole, and it is deliberate: closing it would mean
copying or freezing everything crossing the boundary, which would make
interop useless for the things people want it for.

That last one is not a footnote, and it is no longer only a note: host
access is a `HostAccess` policy the embedder sets, defaulting to
`NONE_AT_ALL`. A host that has not thought about what a script may reach
has not thereby decided it may reach everything. Mutability of an object
that does cross remains the caller's, and `InteropTest` has a test that
demonstrates it rather than a comment that warns about it.

## 10. Java 25, not 21

Asked for as "Java 22, for the memory reasons": the Foreign Function and
Memory API, which `struct!`, `routine!` and `library!` need and which is
final from 22 onward.

Set to 25 rather than 22 because 22 is not an LTS release and was
superseded in September 2024, so it has been out of support for nearly two
years. 25 is the current LTS and carries the same finalised API. One line
in `build.gradle.kts` if that judgement is wrong.

Only Java 21 is installed on this machine, so `settings.gradle.kts` adds
the foojay toolchain resolver and Gradle downloads what the build asks
for. Version 0.9.0 of that plugin is incompatible with Gradle 9.6 and
fails on a missing `IBM_SEMERU` field; 1.0.0 works. Resolved toolchain is
25.0.4+7-LTS.

## 9. Graphics are in scope, and the target is markup

Reverses an earlier note in this repo that put View and VID permanently
out of scope. That note assumed the render target had to be a window,
which would indeed be useless server-side. It does not.

VID is a dialect: a block of data describing a layout, not a sequence of
draw calls. `banner 140x32 effect [gradient 0x1 0.0.150 0.0.50]` is a
declarative description, and rendering it to HTML, CSS and SVG is more
natural than rendering it to a window. A pair is a width and a height, a
tuple is an `rgb()`, and the `draw` dialect maps almost directly onto SVG
paths.

This is what makes the language useful in a web context, which was the
stated goal. It needs the evaluator and objects and nothing exotic beyond
them, because a dialect is just a block interpreted by a function.

Static rendering and interactive rendering are separated into two
milestones, because seven of the fourteen demo programs carry no event
handlers and seven do. The interactive half needs an event model that
survives a browser round trip, which REBOL's own model never had to.

Still undecided: whether VID is reimplemented faithfully or accepted as a
dialect shape and rendered as idiomatic HTML. See `docs/milestones.md`.

## 10. The target is Rebol 3.22.1, the maintained fork

The corpus began as claims read from the REBOL/Core User Guide. A guide
describes an implementation and is not one, so every entry was a belief
nobody had checked. Two tools now check them:

- `scripts/differential.py` runs each corpus entry through `./r3` and
  reports where the answer differs from the entry.
- `scripts/audit.py` runs the same expression through `./r3` and through
  a built JEBOL, for sweeping a feature at its boundaries before any
  entry exists.

The first sweep found nineteen wrong entries, including nine in PARSE and
a pair of quoting sigils specified exactly backwards.

**Which REBOL.** This said R3-Alpha for most of the project, and briefly
said so in writing here, before the dates settled it: `rebolsource/r3`
last had a commit in **July 2015**, while `Oldes/Rebol3` is worked on
weekly. A target nobody maintains is a target whose bugs are permanent and
whose binary will not build on a current machine, which we had already hit
once. So the target is Rebol 3.22.1, pinned to that version so a new
release cannot silently redefine what correct means.

The two agree almost everywhere. Across nineteen checked divergences from
the documentation, exactly one was a real difference between the fork and
R3-Alpha: PARSE used to take a string, character or NONE as delimiters and
return the pieces, with `/all` to hold whitespace out of that set. 3.22.1
removed both and `split` does that job now. Following the fork is also the
better design, because one name was doing two unrelated things.

R3-Alpha's source stays useful as a reference for **why**, since it is the
origin of nearly everything the fork still does. It is not the arbiter of
**what**.

**Read the source before sweeping, not after.** Boundary analysis against
a black box means guessing where the boundaries are. `to-integer` on a
string fails in three distinguishable ways, and the third was found by
chance rather than by design. The branches are all in the C, and
`src/boot/errors.reb` lists all 158 error ids in one file where JEBOL
reaches 18. A good deal of REBOL is also written in REBOL, in `src/mezz/`,
which reads directly as a specification of what those functions do.

The fork also ships `src/tests/units/`: 77 files, 3,290 named tests and
11,899 assertions, written by the people who maintain the implementation.
That is a far better source of cases than boundaries we invent. Twenty of
those files are now imported under `src/test/resources/rebol-suite/` and
run on every build as `RebolSuiteTest`: 3,721 assertions, none of them
skipped.

The C is a reference, not a thing to translate. Its `REBVAL` is a tagged
union, its series are pointer arithmetic, its errors are `setjmp` and its
memory is a hand-rolled collector; none of that survives the move to the
JVM, and a transliteration would carry a C runtime's shape into the
environment this project exists to run well in.


**Port from the C, not from the failure report.** Two ways of working
were tried and they find different things. Working down the failure
report finds functions that are present and wrong. Working the porting
list finds functions that are absent, which usually produce no failure at
all. Only the second is the goal, and the first is how a port gets
checked.

The method that works, four steps in order: read the whole C function;
copy its structure into Java, branch for branch; write the tests by
reading the C again rather than by reading the port; then run Rebol's own
suite. Step three is the one that is easy to skip and the one that pays.
Tests written by reading the port only prove that the port agrees with
itself.

The C carries rules that no amount of probing will show. Four found in a
single day, each of which had been implemented plausibly and wrongly:

- A tuple keeps a length **and** twelve octets, and the octets past the
  length are zeros rather than absent. That one fact decides why `1.2.3`
  equals `1.2.3.0` but is not strictly equal to it, why `length?` never
  answers below three, and why writing NONE through a path is the only
  way to shorten one. Modelled as a plain list of three to twelve
  segments, none of that is reachable.
- Refinement arguments are read in the order the **path** wrote them, not
  the order the function declares them, so `sort/compare/skip s 1 3`
  gives 1 to the comparator. `Do_Args` resequences the specification walk
  to whichever refinement the path names next, under a comment saying so.
  Declared order agrees whenever the two happen to match, which is most
  calls, so the defect hides.
- A refinement written as `f/:flag` and turned down still takes its
  arguments out of the block and drops them. It has to: the values are
  already written down and something must consume them, or everything
  after the call shifts by one.
- A REBOL comparator handed to SORT need not behave like one. A plain
  predicate such as `[a < b]` answers false for a pair either way round,
  which is a contradiction as far as a sort is concerned. The C's merge
  takes from the left run whenever the comparison is at or below zero, so
  that contradiction leaves the order alone; TimSort reads it as a
  descending run and reverses it. The merge sort is therefore written out
  rather than handed to the JVM, and the difference shows up only as
  equal keys coming back shuffled.

**Do not write JEBOL's own answer down as R3's.** Three corpus entries
and one unit test asserted behaviour that R3 does not have, each citing
JEBOL's own specification as its origin -- exactly what `corpus/README.md`
warns against. An entry is worth nothing unless it says where the claim
came from, and "the implementation does this" is not a source.

## 13. Only Rebol's C functions may be written in Java

Rebol implements its library in two layers. About a third is C, in
`src/core/*.c`. The rest is REBOL, in `src/mezz/*.reb`, and it is twenty-five
thousand lines.

**A function Rebol writes in C is written in Java here. A function Rebol
writes in REBOL is imported from its own file and loaded as a resource. It is
never rewritten and never copied into `prelude.reb`, not even verbatim.**

The test is mechanical and there is no judgement in it: find which R3 file
defines the function. `src/core/*.c` means Java. `src/mezz/*.reb` means copy
that file into `src/main/resources/org/jebol/mezz/`, add it to `ORDER.txt`,
and fix whatever native it turns out to need.

### Why a verbatim copy is still wrong

A copy is a fork. The moment JEBOL holds its own `size?`, Rebol's own
`base-files.reb` can no longer be loaded over the top without colliding with
it, and the borrowing this whole design exists for stops being available for
that file. `prelude.reb`'s own header says as much about the functions already
copied into it: they are "something Rebol's own library silently replaces the
moment that file is borrowed".

The second reason is the one that costs time. A REBOL function that will not
load is naming a native that is wrong, and that failure is the best work-list
this project has -- better than any inventory, because it is driven by what
the language actually needs. Rewriting the function in the prelude throws the
signal away and leaves the native wrong.

### What this cost before it was written down

`mezz-shell.reb` opens with `ls: dir: :list-dir`. LIST-DIR lives in
`mezz-files.reb`, which was not imported. So the file raised `not-defined` on
its first statement and every one of its twelve definitions was lost --
`pwd`, `rm`, `mkdir`, `cd`, `more`, `su`, `set-user`, `wait-key`, `user's` and
`file-checksum` among them, all of which had their dependencies present and
would have worked.

Following the chain down: LIST-DIR is `closure/with`, CLOSURE with `/with`
lives in `mezz-func.reb`, which was not imported either -- the prelude had
`closure: :func`, a copy, and `/with` was therefore absent. And LIST-DIR's own
body asks QUERY for three fields at once, and QUERY is a C native
(`p-file.c`) that nobody had written.

One unwritten C native hid three unimported REBOL files, which hid nineteen
functions. `PortingBacklogTest` counted every one of them as missing and could
not say why, because it measures `Interpreter.create()`, which loads none of
the borrowed library at all.

### The consequence for the prelude

`prelude.reb` currently holds JEBOL's own version of several dozen functions
Rebol writes in REBOL -- `empty?`, `does`, `rejoin`, `also`, `unique`,
`forever`, `comment`, `join`, `collect`, `split-path`, `funct`, `clos`, the
`-of` family and the `to-x` family among them. Each is a fork by the rule
above. Replacing them with the R3 files that define them is outstanding work,
and it is listed in TODO.md rather than done here, because each replacement
needs the natives underneath it to be right first.
