# Decisions

What was decided, when, and what it rules out. Newest last.

## 0. Why this exists

To write REBOL against the JVM's libraries, and to run it in an ordinary
web production environment while doing so: an ordinary jar on any JDK,
deployed down the pipeline that already exists, watched with the tools
the operations team already has, in the containers everything else
already runs in.

**Interoperability is the point.** REBOL against a Java postgres driver,
and the rest of the ecosystem behind it, instead of reimplementing each
of them again in a language whose own library is small and whose
maintained ports are few. A language on the JVM that cannot reach the
JVM's libraries is a language that has to build everything twice.

And REBOL is an unusually easy language to write dialects in, because a
dialect is a block interpreted by a function rather than a grammar and a
generator. That makes a light translation from an allium spec to a
dialect worth aiming at, which is a thing this project wants and no
other REBOL runtime is placed to give it.

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
- **Embedding and interop are still two things**, and both are wanted.
  Embedding is a Java application creating an interpreter, running a
  script and getting a value back, under a time and memory bound it sets.
  Interop is REBOL code calling Java. Keeping them apart is how the bounds
  stay meaningful; it is no longer a claim that one of them is optional.

**Corrected.** This entry used to say the opposite: that interoperability
was *not* the point, that building a whole REBOL implementation so REBOL
could call Java would be a poor trade, and that two-way interop was in
scope only because it is useful once you are here. It is the reason for
coming.

The rest of the entry survived the reversal unchanged, which is worth
noticing: the workload argument and the Truffle argument never depended
on the interop question, and decision 6 had already reached the opposite
conclusion by a different route -- "a language on the JVM that cannot
reach the JVM's libraries is a language that has to reimplement them".
Two entries in one file disagreeing for months, and neither one wrong on
its own terms.

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

**Which is what governs every toolkit listener in the adapters.** Swing calls a
listener on its own event thread, so each one in `DesktopScreen` does exactly
one thing: it puts an event on a queue and returns. Nothing there runs a
handler, evaluates a block or touches a series, because anything more would be
a second thread inside the interpreter -- and two threads appending to one block
corrupt it without either of them failing.

The other direction waits: work handed to the toolkit goes through
`invokeAndWait`, because SHOW answers when the window is there rather than when
it has been asked for, and a caller that opened a window and then measured it
would otherwise measure nothing.

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
were text down. Recorded here so they are decisions rather than
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

That last one is not a footnote, and it is no longer only a note. A script
calls into the host only through an extension the embedder adds to the
bounds, and the bounds carry none by default. A host that has not thought
about what a script may reach has not thereby decided it may reach
everything. (This was a `HostAccess` policy until extensions replaced
`defineFunction`; the policy gated only that, so it went
with it.) Mutability of an object
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

**Reversed in part by decision 52.** Graphics stay in scope; markup is no
longer the target. A layout block is read by the 2010 R3 GUI into gobs, and a
browser is one more screen that paints them.

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

## 10. The target is Rebol 3.22.1, the maintained fork

The corpus began as claims read from the REBOL/Core User Guide. A guide
describes an implementation and is not one, so every entry was a belief nobody
had checked. For a while two scripts checked them by running a real binary, and
the first sweep found nineteen wrong entries, including nine in PARSE and a pair
of quoting sigils specified exactly backwards.

**That binary and those scripts are gone, on purpose.** A build answers what
one build of one fork does on one machine; the C says what the language is. Both
scripts and the binary were deleted, and what replaced them reads Rebol's own
source instead:

- `scripts/c-surface.py` records the C surface -- the datatype table, the
  declared specs of all 224 C functions, and which actions each typeclass
  actually implements -- into `src/testFixtures/resources/r3/c-surface.txt`.
- `scripts/c-parity.py` compares that against JEBOL's registry.
- `ActionParityTest` multiplies the datatype table by the arms table and calls
  every pair, which is how a missing arm is found at all.

The corpus entries that were confirmed against the binary stay as evidence. They
are not authority, and a new entry is traced to a line of C.

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

**Read the source. There is nothing else to read.** Boundary analysis against
a black box means guessing where the boundaries are. `to-integer` on a
string fails in three distinguishable ways, and the third was found by
chance rather than by design. The branches are all in the C, and
`src/boot/errors.reb` lists all 158 error ids in one file where JEBOL
reaches 18. A good deal of REBOL is also text in REBOL, in `src/mezz/`,
which reads directly as a specification of what those functions do.

The fork also ships `src/tests/units/`: 77 files, 3,290 named tests and
11,899 assertions, text by the people who maintain the implementation.
That is a far better source of cases than boundaries we invent. Sixty-eight of
those files are now imported under `src/testFixtures/resources/rebol-suite/` and run on
every build as `RebolSuiteTest`: 10,144 assertions, every one run -- 10,132 by
every build and 12 by `./gradlew networkTests`.

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
Tests text by reading the port only prove that the port agrees with
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
- A refinement text as `f/:flag` and turned down still takes its
  arguments out of the block and drops them. It has to: the values are
  already text down and something must consume them, or everything
  after the call shifts by one.
- A REBOL comparator handed to SORT need not behave like one. A plain
  predicate such as `[a < b]` answers false for a pair either way round,
  which is a contradiction as far as a sort is concerned. The C's merge
  takes from the left run whenever the comparison is at or below zero, so
  that contradiction leaves the order alone; TimSort reads it as a
  descending run and reverses it. The merge sort is therefore text out
  rather than handed to the JVM, and the difference shows up only as
  equal keys coming back shuffled.

**Do not write JEBOL's own answer down as R3's.** Three corpus entries
and one unit test asserted behaviour that R3 does not have, each citing
JEBOL's own specification as its origin -- exactly what `corpus/README.md`
warns against. An entry is worth nothing unless it says where the claim
came from, and "the implementation does this" is not a source.

## 13. Only Rebol's C functions may be text in Java

Rebol implements its library in two layers. About a third is C, in
`src/core/*.c`. The rest is REBOL, in `src/mezz/*.reb`, and it is twenty-five
thousand lines.

**A function Rebol writes in C is text in Java here. A function Rebol
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

### What this cost before it was text down

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
(`p-file.c`) that nobody had text.

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
and it is listed in `goals.md` rather than done here, because each replacement
needs the natives underneath it to be right first.

## 14. A module's private words never reach the library

A file whose header says `Type: module` loads into a context of its own, and
only the words its `Exports:` field names are copied into the library.
Everything else stays where the file put it.

JEBOL loaded all thirty-six borrowed files into one context. That is not a
smaller interpreter, it is a wrong answer, and the wrongness is silent.
Rebol's JSON codec is a module holding a parse rule named `exp` and another
named `stack`. Loaded flat, both replaced the library functions of those
names, and nothing reported it: the word still answered, it just answered a
block. `PortingBacklogTest` counted both as missing and could not say why.

The split follows Rebol's own three lists in `make/rebol3.nest`, carried in
`ORDER.txt` as a mark against each file rather than guessed from the name.
Guessing from the name is wrong in a way worth recording: `sys-codec.reb` is
in Rebol's sys list and its last line is
`export [register-codec decode encode encoding?]`. EXPORT is a real function
in `sys-base.reb` that copies each named word into the library, so the file
belongs in sys and four of its words reach lib because the file says so.

The sys files get Rebol's two-pass bind, and both passes are load-bearing.
Their own top-level set-words go to sys; every other word binds to the
library; and then sys wins again wherever sys holds the word, which is what
lets one sys helper call another. Leaving the third pass out bound EXPORT to
the library, where there is no such word, and stopped all fourteen codec
files on their first line.

### What it cost to find out

Nine natives were wrong and had to be fixed before a module would build. They
are listed in `goals.md`, and two patterns account for most of them: a
refinement declared in a native's spec and then never read in its body
(`assert/type`, `switch/all`, `construct/with`, `protect/lock`), and a rule
walked in pairs where the C walks one value at a time (`switch`).

The largest of them is that `bind` always copied. The C copies only for
`/copy`; without it, BIND binds the caller's own block and answers it.
MAKE-MODULE* binds one body four times over and then evaluates that body, so
with every bind answering a copy nobody kept, `do body` ran an unbound block
and the module came out holding a slot per word and a value for none of them.
Changing it moved no suite assertion either way, which says the suite does not
cover in-place binding rather than that nothing depends on it.

## 15. MAKE MODULE! and MAKE PORT! both call back into loaded REBOL

Neither builds its value. Each hands its spec to a REBOL function in the
borrowed library: MAKE-MODULE* in `sys-base.reb` and MAKE-PORT* in
`sys-ports.reb`. Rebol's C does the same in the same place -- `Make_Module` is
four lines and one of them is
`Do_Sys_Func(SYS_CTX_MAKE_MODULE_P, spec, 0)`.

So the seam runs both ways: Java calls REBOL as readily as REBOL calls Java.
Writing MAKE-MODULE* in Java instead would fork ninety lines of Rebol's own
code, and those ninety lines are where the EXPORT and HIDDEN keywords in a
module body are handled, where the header is checked, and where the choice
between a shared and an isolated namespace is made. Every one of those is
behaviour a script can observe, so a second copy of them is a second set of
answers. See decision 13.

The consequence worth knowing: `make module!` works only after
`sys-base.reb` has loaded, and `open` only after `sys-ports.reb` has. Both
answer `not-defined` on the helper before that, which is the right failure and
not a graceful one.

## 16. The load order is Rebol's, and it is text down in Rebol's own build

`make/rebol3.nest` holds three ordered lists -- `mezz-base-files`,
`mezz-sys-files`, `mezz-lib-files` -- and `Do_Global_Block` runs them in that
order with a different rebind for each. `ORDER.txt` is now those three lists,
in that order, with `-> sys` marking the middle group.

JEBOL had invented its own order: base and mezz files interleaved, sys files
last. That is why `base-funcs.reb` was absent for so long without anyone
noticing. It is second in Rebol's list, ahead of everything that uses FUNC,
FUNCTION, APPLY, DEFAULT, MODULE and CAUSE-ERROR, and JEBOL carried its own
versions of all six in the prelude instead. Nothing failed, so nothing pointed
at it.

Nineteen files became thirty-two. Seven were not on disk at all:
`base-funcs.reb`, `mezz-secure.reb`, `mezz-types.reb`, `mezz-control.reb`,
`mezz-help.reb`, `mezz-banner.reb`, `mezz-tail.reb`.

### What that cost, and what it says about the method

Every one of the following was found by a borrowed file stopping on it. None
was found by reading a catalogue, and none would have been:

- `collect-words` refused none for /IGNORE and had no /AS at all. Ten files.
- `apply` refused a block shorter than the function's argument list. The C pads
  it with none: `for (; n < len; n++) DS_PUSH_NONE`.
- A function's `/local` words held UNSET at entry. In R3 they are the arguments
  of the /local refinement, so they hold NONE. Rebol's LOAD is one CASE/ALL
  whose fourth clause is `none? body [body: source]`, and CASE/ALL evaluates
  every clause -- so reading a local before writing it is ordinary REBOL.
- `/local` was not a refinement at all, so its own word held unset. LOAD opens
  with `assert/type [local none!]` to check nobody passed /local.
- `assert/type` ignored /TYPE and just tested truthiness.
- `length?` refused an object. INTERN opens with
  `index: 1 + length? usr: system/contexts/user`.
- `to string!` of a binary printed the bytes as hexadecimal instead of decoding
  them as UTF-8. The url-parser works on a binary throughout.
- `utf?` was a codepoint-range predicate beside ASCII? and LATIN1?. R3's
  answers which byte order mark a binary carries.
- A loop body's context hung off the system context rather than the caller's
  frame, so a loop inside a function could not see the function's own words.
- `system/options` and `system/state` were missing most of `sysobj.reb`'s
  fields, and a set-path cannot create a field. Absent fields do not degrade,
  they stop the file that writes to one.

### Two things about binding that the order alone does not give you

**A sys file needs two binds, not one.** R3 runs these with rebind 2, which is
`Bind_Block(Sys_Context, ..., BIND_SET)` followed by `Bind_Block(Lib_Context,
..., BIND_DEEP)`: the file's own set-words go to sys and everything else goes
to lib. Two rather than one, because a sys file can use the same spelling for
both. `base-defs.reb` declares `decode-url: none` with the note "set in sys
init", and `sys-ports.reb` sets it with `set 'decode-url` from inside a nested
block -- that lit-word has to reach the library while the top-level
`decode-url: none` on the next line has to reach sys. Loading them into the
library instead is what made DECODE-URL none: the two became one word and the
none, being last, won.

**Every sys word is declared before any sys file is bound.** A file is bound as
it is loaded and a word with nothing to bind to stays unbound for good, so a
sys file calling a helper defined in a later sys file gets a word that never
resolves. That is not a load failure and says so nowhere: the file loads, the
function is defined, and it raises the first time anybody calls it. `sys/do*`
in `sys-base.reb` calls `do-needs`, defined in `sys-load.reb` three files
later, so every script run through it stopped on `do-needs has no value`. R3
has no such ordering because the sys context is built from a boot list before a
line of it runs; JEBOL does the same thing in two passes, which is cheap
because `LibrarySource` reads each file once for the whole process.

## 17. A function body is bound to its own words, and to nothing else

`Bind_Relative` in the C. A call rebinds the function's arguments,
refinements and locals; every other word in the body keeps the binding it was
text with.

JEBOL rebound the whole body through the frame chain at every call, and that is
wrong in a way that only shows up through one door. FUNC is itself a REBOL
function, so `make function!` runs inside FUNC's frame. A body rebound through
that chain resolves its free words in the library rather than where they were
text.

Rebol's own COLLECT is exactly that shape. It creates its KEEP function inside
itself, and KEEP writes to COLLECT's own OUTPUT:

    unless output [output: make block! 16]
    do func [keep] body func [value [any-type!] /only] [
        output: insert/:only output :value
        :value
    ]

With the body rebound, KEEP's OUTPUT was a different slot from COLLECT's, and
it held none. So did SPLIT, which is text on COLLECT, and so did every
corpus case that uses either.

The fix is `Binder.bindOnly`, which takes the set of names the function owns.
It is the smaller operation and the correct one: binding is not something a
call does to a body, it is something the body already has.

### A word that escapes its call still means the innermost call

Rebol binds a function's body once and stamps the *function* into every word
there, never a call. So a word in a body has no frame of its own, and `Get_Var`
finds one at the moment it is read - the C's comment says it plainly: "a
negative index indicates that the value is in a frame on the data stack, so now
we must find it by walking back the stack looking for the function that the word
is bound to". It walks from the innermost call outwards and stops at the first
frame of that function, so a word a function wrote always means the innermost
call's copy.

JEBOL binds a body to the frame of the call running it, which is a different
object each time and almost always the innermost one anyway. The exception is a
word that escapes: a function hands a value holding one of its own words to a
call of *itself*, and that word is read while the inner call is running. Rebol
reads the inner call's value; the straightforward port reads the outer call's.

So an outer frame carries a pointer to the call that has superseded it, and
points back at nothing when that call ends.

Rebol's own ARRAY is built on this. Each level of a multi-dimensional array puts
the word `block` into a list of index expressions and passes the list down, and
every level's copy of that word has to read the level that is running when the
innermost one finally evaluates it.

Two related facts fall out of the same design. A function's declared words are a
context that holds nothing when no call is running - a body shared between two
functions belongs to whichever was made last, and calling the other one has to
fail rather than read the wrong frame. And the C reuses a returned function's
stack frame, so a word still bound into one answers whatever call took the frame
over; under DO, that is DO.

### A declaration *is* a spec, and Rebol keeps them in three places

The docstring, the parameters with their types and their own docstrings, and the
refinements in the order they were text with their arguments after them. That
is what SPEC-OF and WORDS-OF answer out of - not something rebuilt from the
native registry, which knows the types and not the order, not the documentation,
and not which refinements take no argument at all.

`actions.reb` declares the sixty actions and `natives.reb` the hand-text
natives. The rest are text as comments beside the C that implements them, and
Rebol's build collects those into `generated/gen-natives.reb`. Missing that third
file left 45 functions - `gcd`, `access-os`, `compress` and the like - still
answering a rebuilt spec with no refinements in it.

### `system/platform` names the operating system, not the runtime

Six files in the borrowed library branch on that word, and every branch is about
local conventions: which character separates the entries of PATH, how a shell
argument is quoted, whether a filename comparison minds case, where an
application keeps its own files. A JVM on Windows has Windows conventions, so the
answer has to be Windows - and a word true of no operating system at all sends
all six down the arm meant for something else.

The host supplies it, through the machine it hands the interpreter
(`MachinePort`), because only something outside the domain may ask the machine.
A host that supplies none gets `JavaMachine`, which reads the JVM's own answer and
falls back to `JVM` - the C's own name for a build that knows nothing about where
it is - only when the JVM reports no operating system at all. The same machine
answers the home and start directories, the separator TO-LOCAL-FILE writes, the
process number ACCESS-OS reads, and the launcher behind `system/options/boot`, so a
host describing Windows gets a script that sees Windows throughout.

### Control-flow signals stop being signals at the outermost walk

BREAK, CONTINUE, RETURN and THROW travel as Java exceptions, which is how a loop
catches one without every native in between having to hand it back. When there is
no loop and no function, the signal arrives at the top - and the C has an error for
each: `break: {no loop to break}`, `continue:`, `return:` and `throw:`, the whole
of the Throw category in `boot/errors.reb`.

So the outermost walk is where they are turned into those errors. `spec/embed.allium`
says nothing a script does may reach the host as a throwable, and `do reduce [p 7]`
with a BREAK path in P throws `LoopSignal` out of the interpreter otherwise.

TRY does **not** do this itself, and that is deliberate: the C's TRY traps errors and
lets a thrown value past, so `try [break]` still ends the script. Only TRY/ALL disarms
one, and it makes the same four errors.

### NEAR and WHERE are fill in on the way out, once

Both fields exist because a script reads them, and they cannot be fill in where the
failure happens - a native raising `zero-divide` has no idea what block it is in. So
they are attached at the one place that has the frames: the walk's own catch.

NEAR is the fragment from where the *innermost call* began, which is why the pending
call records that - by then the block has moved past it. WHERE is the chain of names
those calls were reached through, innermost first.

Once, and only if nothing has said already, because that catch sits in a loop every
enclosing frame also runs: the innermost answer is the true one and the outer passes
must leave it alone.

It is not R3's answer exactly and cannot be. R3 fills both from its own data stack, so
WHERE runs on down into the console's frames - `[/ try do either either if -apply-]` -
and NEAR points at the *caller's* block whenever a failure happens before the callee
gets a frame, which is where its argument checking runs. What matches is the part that
is about the script rather than about the interpreter.

### The record of open calls is a field, and the walk's frames are not

The frames are local and go when the walk returns, however it returns. The record of
which calls are open is a field, so a raise that unwinds past the loop leaves every
call it passed through still recorded - which makes STACK/DEPTH climb by one for every
error a script catches and never come back down, and those same entries are what DS
prints. Closing the record in the walk rather than where a frame is popped covers the
exceptional way out as well as the ordinary one.

A paren and a function body push a frame rather than recursing, so a script that
recurses a thousand deep costs a thousand small objects on the heap instead of a
thousand JVM frames. That is what lets the depth limit be a promise rather than a
hope, and why `forever: func [n] [forever n]` reports an error instead of killing the
process.

## 18. No runtime dependencies. The JDK, or write it out

A function with no direct equivalent in Java is text in Java. It does not
acquire a library.

`build.gradle.kts` has four dependencies and all four are `testImplementation`:
JUnit, jqwik, AssertJ, ArchUnit. The interpreter itself is compiled against the
JDK and nothing else, and that is a property to keep rather than a coincidence
to notice. A REBOL interpreter that needs a jar fetched from a repository to
read a PNG or hash a string is not portable, and the whole point of putting it
on the JVM was that it would run wherever the JVM does.

What that has meant in practice, all of it in `Encodings.java`:

- **Bundled and used**: `java.util.zip` for deflate and the CRC-32,
  `java.security.MessageDigest` and `javax.crypto.Mac` for the digests and
  their keyed forms, `java.nio.charset` for ICONV.
- **Written out** because the JDK has no equivalent: gzip's ten-byte header and
  eight-byte trailer (the JDK only offers them wrapped in a stream, and a
  stream is `java.io`, which the dependency rule keeps out of the domain),
  CRC-24 as OpenPGP defines it, the sixteen-bit one's complement TCP sum, base
  36 and base 85, Rebol's own CLOAK scrambler, and the five PNG delta filters
  with their Paeth predictor.

The rule has a second effect worth naming: writing it out forces the port to be
read out of the C rather than delegated to a library that is nearly right. The
Paeth predictor's tie-breaks are ordered -- left, then above, then above-left --
and a library that broke them differently would corrupt flat colour and pass
every casual test.

## 19. The library may read the clock while it loads. A script may not, unless the host said so

`Interpreter`'s constructor grants `HostService.CLOCK` for the duration of the
boot and then narrows to exactly what the host granted:

```java
Set<HostService> duringTheBoot = EnumSet.of(HostService.CLOCK);
duringTheBoot.addAll(bounds.grantedServices());
...
natives.grantOnly(bounds.grantedServices());
```

Rebol's own `prot-mysql.reb` opens with `last-activity: now/precise` inside a
top-level MAKE OBJECT!, so the file cannot load at all without a clock. Refusing
it means a host that granted nothing gets a language with no MySQL scheme in it,
and the grants exist to confine what a *script* can reach rather than to decide
which of Rebol's own files exist. Loading the library is building the
interpreter; running a script is the thing the grants are about.

**The clock and nothing else.** Reading it cannot leak anything and cannot change
anything outside the process, which is not true of files, the network or starting
another program. If a borrowed file ever wants one of those at load time, that is
a decision to take with the file in hand rather than a precedent this sets.

`natives.forgetStartupState()` follows for the same reason: what the library's
own loading caught is not what the script did, and `system/state` is the script's
view.

## 20. An image's bytes are RGBA here, whatever the platform stores

**Rebol's own pixel byte order is per-platform**, and this is the one place so
far where copying the C byte for byte would be the wrong port.
`include/reb-c.h` picks a layout at compile time:

```c
#ifdef ENDIAN_BIG
#define C_A 0   //ARGB pixelformat used on big endian systems
#define C_R 1
#define C_G 2
#define C_B 3
#else
#ifdef TO_ANDROID_ARM
#define C_R 0   //we use RGBA pixelformat on Android
#define C_G 1
#define C_B 2
#define C_A 3
#else
#define C_B 0   //BGRA pixelformat is used on Windows
#define C_G 1
#define C_R 2
#define C_A 3
```

So the same image is ARGB, RGBA or BGRA in memory depending on where Rebol was
built. That matters here more than it does there, because **the plan is desktop,
the web and Android**, and Android is exactly the platform Rebol treats
differently. Three builds of JEBOL that stored pixels the way each host prefers
would be three languages.

**JEBOL stores red, green, blue, alpha in that order, everywhere, and translates
at the edges.** The edges are few and all of them are already fixed by the
language rather than by the platform:

- **Molding** writes `RRGGBB` per pixel and appends a second binary of one alpha
  byte per pixel, and only when some pixel's alpha is not `0xFF`.
  `Mold_Image_Data` composes each pixel through `TO_RGBA_COLOR(pixel[C_R],
  pixel[C_G], pixel[C_B], pixel[C_A])` -- it reads the platform's order and
  writes a fixed one. That fixed one is the language.
- **A pixel as a value** is a tuple, `r.g.b` or `r.g.b.a`, in that order.
- **MAKE from a binary** reads `RRGGBB` triples, and the alpha binary one byte a
  pixel.

Which is to say Rebol's own observable behaviour is RGBA on every platform; only
its storage moves. Storing what it observes removes a whole class of
platform-dependent defect and costs nothing, because nothing outside the value
class sees the bytes.

**The shape when a renderer arrives: one port, three adapters, chosen by the
runtime.** Each surface wants a different native order -- an `int[]` in
`TYPE_INT_ARGB` for `BufferedImage` on desktop, an RGBA byte buffer for a canvas
on the web, `ARGB_8888` for a `Bitmap` on Android -- so the conversion is exactly
the kind of thing this codebase already puts behind a port:

```
domain/eval/PixelSurfacePort      what the domain asks for, in RGBA
adapter/host/DesktopPixelSurface  BufferedImage, BGRA/ARGB int[]
adapter/host/WebPixelSurface      canvas buffer, RGBA bytes
adapter/host/AndroidPixelSurface  Bitmap, ARGB_8888
```

Same arrangement as `FilePort` and `FileSystemPort`, `WindowPort` and
`DesktopWindows`. The host chooses the implementation and hands it in, which is
what it already does for every other service, and `Bounds` is where a host that
has no surface says so.

The dependency rule makes this mandatory rather than advisory.
`DependencyRuleTest` forbids `java.io..`, `java.nio.file..` and `java.net..`
inside `org.jebol.domain..`, and forbids the domain depending on adapters at all.
`java.awt.image.BufferedImage` belongs on that forbidden list the day a desktop
surface exists: reaching for it inside `ImageValue` is the mistake this decision
exists to prevent, and the linter should be the thing that says so.

**Not built yet, and not to be built speculatively.** There is no renderer and no
second implementation, so the port is a plan rather than code -- the rule about
patterns earning their name applies. What is fixed today is the half that costs
nothing: the value stores RGBA, and nothing above `ImageValue` ever sees a byte
order. If that holds, adding the three adapters later is additive. If it does not,
the language has become three languages.

## 21. What ImageIO gets wrong, and what is text by hand because of it

The JVM's own image codec arrives with the runtime and reads and writes PNG,
JPEG, GIF and BMP everywhere Java runs, so the "only on Windows and macOS so
far" the C says of its own shim is not a limit this platform has. Four of its
behaviours are wrong for this port, and each cost a real defect before it was
understood. They are recorded here because `JavaImage` now reads as ordinary
code and nothing in it says why.

**The BMP writer refuses every thirty-two-bit picture it is offered**, under
every compression type: "Image can not be encoded with compression type BI_RGB
and 32 bits per pixel". Going through it means writing twenty-four bits and
losing the transparency, or not writing a BMP at all. A real 3.22.5 loses
nothing, so the file is text out directly instead. The fifth version of the
header is what makes the alpha possible -- `BITMAPV5HEADER` carries a mask per
channel, so the file says which bits are which rather than leaving a reader to
assume. The runtime reads that back perfectly; only writing it is missing.

**Left to itself the GIF writer sets the interlace flag**, and its own reader
then hands back a picture with the rows in the wrong order. A two-by-two of four
colours came back as three, and JEBOL could not read a GIF it had just text
even though a real 3.22.5 read it perfectly. A real 3.22.5 writes those flags as
nought, so the writer is driven directly and progressive mode is disabled.

**Handed a full-colour image, the GIF writer picks a palette by quantising**,
and quantising merges colours that sit close together -- two dark reds a step
apart came back as one. A picture with no more colours than the table holds
needs no quantiser: it gets an entry apiece and every colour survives. Past two
hundred and fifty-six there is no room and something must go, so the picture is
left as it was and the writer quantises after all. Which colours it loses is its
own business, and pinning that would pin a version of ImageIO rather than any
behaviour of the language.

**JPEG has no alpha channel**, and handing ImageIO a picture that still has one
gives back a picture with its colours rotated. The channel comes off before
writing and a JPEG comes back opaque, which is what it would have been anyway.

One more that is not a fault, only a shape: `ImageIO.read` takes the first image
in a stream and an animated GIF holds several, so reading goes through a reader
that is asked for its own count first. Every frame up to the one wanted is
drawn, not just that one -- a GIF frame after the first is usually a patch
rather than a picture, a small rectangle placed at an offset covering only what
changed, so reading it alone gives that patch on its own. What a viewer shows is
the patch laid over what was already on the canvas, and that is what the
checksums a real 3.22.5 answers are taken of.

## 22. The library is read once per process, and never shared

Rebol's own library is six hundred thousand characters and every interpreter
was reading all of it. That was most of the sixty milliseconds a new
interpreter cost, and a suite that builds sixteen thousand of them spent most
of its afternoon on it. The text does not change between interpreters, so
neither does what it reads as -- `LibrarySource` reads each file once for the
whole process.

**What does change is what happens to it afterwards, and that is the whole of
the risk.** A block loaded from source is the block a function's body *is*, so
a script that appends to a literal inside one is changing that literal for
good. Within one interpreter that is REBOL behaving as it should; across two it
is a disaster. So nothing shared is ever handed out: every series in the cached
reading is copied on the way to the caller.

**A reading holding a series the copier does not know how to copy is not cached
at all.** Construction syntax can put a map, a bitset, a vector or an image into
a source, and each has mutable storage of its own. None appears in the files
JEBOL borrows today; the day one does, that file drops out of the cache rather
than sharing storage between every interpreter in the process. Refusing to cache
is slower and always right, where guessing that some other datatype is safe to
share would be faster and wrong in a way no test would name.

## 23. The boot order, and why each step sits where it does

`Interpreter`'s constructor runs eight steps and the order of five of them is
forced by something. The code now reads as a plain list of calls, so the
reasons live here.

**The prelude before anything else.** The standard function set is two layers:
natives text in Java because they reach something the language cannot, and
`prelude.reb`, text in REBOL because it can be. Loading it in the
constructor rather than lazily means its functions see the natives and each
other, and that nothing can observe an interpreter without it. A failure there
is a defect in JEBOL rather than something a script did, so it raises rather
than leaving an interpreter half-built -- which is true of every step below.

**`system/modules` before the borrowed library, not after.** A module that
loads replaces its own address in the same object: `repend system/modules [name
module]` is the last thing LOAD-MODULE does. So that table is both the list of
what may be fetched and the record of what has been, and filling it in
afterwards would write the addresses back over the modules.

Each address names the `bundled:` scheme rather than `src.rebol.tech`, so a
module is read out of this build and cannot change under a running system.
Rebol evaluates what comes back from that host with no signature, no checksum
and no pinned version between the wire and the evaluator. DOWNLOAD-EXTENSION
does `content: read source` either way and never learns the difference. Only
the thirteen modules this build has no other way to reach are listed: Rebol's
table also names fourteen compiled shared libraries, which nothing here can
load, and nineteen modules this build already vendors.

**The schemes partway through the borrowed library, not after it.** Exactly one
borrowed file forces this. `view-funcs.reb` ends by calling INIT-VIEW-SYSTEM,
which reads `system/ports/event/extra` on its ninth line, so the event port has
to be open before that file runs -- and opening it needs MAKE-SCHEME, which
comes from `sys-ports.reb`, loaded earlier in the same walk.

The event port is opened even where no host supplied a screen, and a real
3.22.1 does the same: its console build has a whole event port with a default
AWAKE that prints and no graphics behind it.

**`system/ports/system` is the one queue everything goes on.** Its STATE is the
events waiting and its DATA is the ports that have woken, and WAIT is nothing
but a loop over its AWAKE. Without it a protocol has nowhere to put an event
for a port other than the one the event arrived on -- which is exactly what TLS
needs, because its caller waits on the TLS port while the events come from the
TCP port underneath.

**`system/ports/output` exists even though nothing writes through it.** The
output port does the writing; REBOL code asks the console port how wide the
terminal is, and HELP asks on its first line. Left as none, a script calling
HELP got `query does not allow none!` instead of help.

**JEBOL's own boot steps are REBOL files, not Java text blocks.** They live in
`/org/jebol/boot/` because a scheme's INIT is a function and a table of
addresses is a block, and neither reads as either when quoted inside another
language. Writing any of the six scheme registrations in Java would be a second
implementation of a rule REBOL already states once. A missing one is a broken
build rather than something a script did.

**RESIZE samples the nearest pixel.** The C offers a choice of filters, named in
`system/catalog/filters`, and defaults to Lanczos. Choosing between them changes
how a shrunken photograph looks; it does not change what RESIZE is, and nothing
here can yet ask for one.

**MD4 and RIPEMD-160 are text out, because the catalogue lists them and
`java.security` has not got them.** The shipped jar takes no dependencies, so the
alternative to writing them is not offering them, and a name in
`system/catalog/checksums` that cannot do anything is the one thing a catalogue
must not be. MD4 is thoroughly broken as a cryptographic hash and has been since
the nineties; it is here because file formats and protocols text when it was
new still carry MD4 sums, and reading one of those means computing one.

**A `string!` is stored as codepoints, not as the host language's string type.**
REBOL strings are mutable series indexed by character with constant-time access.
Java's `String` is immutable and indexed by UTF-16 code unit, so `"a😀b".charAt(1)`
is half an emoji. Neither property is negotiable for a `string!`.

**SHAPE is read directly rather than through DELECT**, because its arguments are
all pairs and numbers in text order - ten commands, each with a relative form
text as a lit-word. Specified in `spec/draw.allium`.

## 24. Two renderers are held to the paint list exactly, and to each other only where it is physical

**The paint list is the one representation, and it stays that way.** A DRAW
block is read once into a list of instructions carrying absolute positions;
Java2D executes it for a window and a canvas executes it for a page. Neither
decides anything. That is why thirty-four commands cost one reader rather than
two renderers' worth of dialect.

**No SVG.** A DRAW block is a run of orders with state carried between them --
set the pen, set the transform, draw, push, pop -- and Canvas 2D and Java2D are
that same model, near enough one to one. SVG is a document you assemble, so
`push` and `matrix` would become nested groups with transforms on them: a
conversion that can be wrong, kept in step with two renderers that do not need
it. It would also buy nothing for agreement, because a browser rasterises SVG
with the engine it rasterises canvas with.

**What no toolkit has is worked out in the domain**, and reaches both
renderers as something they already draw identically. Gouraud shading becomes
a mesh of flat triangles; a conic gradient becomes a fan of wedges; a diamond
one becomes rings; a two-colour dash becomes two paths; a keyed image becomes
a copy with a colour made see-through; a warped or scaled image becomes a
resampled copy. The alternative -- each renderer approximating separately --
is two pictures, and no reference anywhere to say which is right.

Scaling is the clearest case. How a rasteriser resizes a picture is its own
business and the two disagree, so IMAGE-FILTER resamples here and hands both
the finished pixels.

**The renderers share one primitive beyond the instruction list: clipping to a
path.** Both toolkits have it natively. It is what makes a conic gradient fill
a circle rather than the square the circle sits in.

### The comparison is exact inside a shape and tolerant on its edge

The browser gate demanded zero differing pixels, and passed for as long as
every shape in its picture was an axis-aligned box. A box has no part-covered
edge, so both engines answer the same thing. The first curve, sloped stroke,
gradient or letter ends that -- two rasterisers disagree about how much of an
edge pixel a shape covers, and no care in either port removes it.

So the gate was split rather than loosened:

- a pixel away from any edge must match exactly, because a difference there is
  one renderer executing the instruction differently;
- a pixel on an edge may differ by up to 24 of 255, and at most a fortieth of
  the picture may be edge that uses the allowance.

Both numbers live in `spec/screen.allium`, so widening one is a change to the
specification rather than a number somebody nudged in a test. "On an edge" is
decided from the picture and not from the geometry: a pixel is on an edge when
the eight around it are not all one colour. A deliberate twelve-by-twelve
patch differing by seven is still caught.

**Text is out of the raster comparison on purpose.** Java2D and a browser
measure and hint glyphs differently, so the same string at the same size lands
on different pixels. The words, the place, the size, the weight and the colour
are decided in the domain; the shapes of the letters are the toolkit's.

## 25. The web build compiles the core as it is, and substitutes only what the browser lacks

**There is one copy of the interpreter.** The web build compiles `src/main`
with TeaVM exactly as the JVM compiles it. `src/teavm` holds the web's own
additions and nothing else: the entry point the page calls, one substitution
policy, the classlib stand-ins for the JDK classes the browser does not have,
and the resource list. No core class is forked, and no library file is text
twice. Where TeaVM cannot compile a construct at all, the construct is rewritten
in place to one it can, and the rewrite is answer-preserving rather than a
switch: for example two guarded `case Whole(long magnitude) when ...` arms of one
switch become bare patterns with the accessor in the guard, which both compilers
take. The JDK gate is what proves those rewrites change nothing: the whole
suite runs against them on the JVM.

**What the browser lacks is substituted, never branched on.** One policy class
names every substitution, so a reader can see the whole list in one place.
`Math.fma` stays `Math.fma` on the JVM and becomes the `BigDecimal` form, which
rounds once and agrees with it, on the web. `ProcessHandle` answers that only
this process exists; `PhantomReference`, which the classlib lacks, is a weak
reference with a null `get()`; `File.setExecutable` is behind one class whose
web form answers true without touching the file. Nothing tests a runtime flag to
decide which platform it is on.

**Crypto is refused, not faked.** Every `javax.crypto` and `java.security`
algorithm class the core reaches for is a stand-in whose methods throw an error
naming the method. A page that asks for a signature gets a refusal rather than
an answer invented in the browser. Digests and HMACs came later, through the
browser's own WebCrypto -- decision 34 -- and how TLS is checked is decision
43's.

**The web compile lives in the second gate.** `browserCheck` depends on
`buildWasmGC` and then runs the page the way a reader would, in a real Chrome;
`check` stays a JDK-only gate. The trial's script is now a fixture: the Rebol
sits in a `<script type="text/rebol">` tag on a real page, one test runs that
tag's script on the JVM and pins the seven lines it prints, and one drives the
same page through the WebAssembly build and reads the same seven lines out of
the DOM. The TeaVM faults met on the way, and the numbers the trial measured,
are in `docs/web-port.md`.

## 26. The process opens no window unless a window is asked for

**JEBOL runs headless, and `--screen=window` is what turns the display back
on.** A page draws its own window, so the JVM serving it has no use for
macOS's window server - but measuring a letter to answer SIZE-TEXT loads
`java.awt`'s fonts, and the moment AWT initialises macOS gives the process a
Dock icon. Someone who opens the page and sees a stray app appear beside it
reads that as a fault in the page, and they are nearly right.

So the CLI chooses `java.awt.headless` from the switch before anything else
runs: true for a page, and for the bare console, and false only when
`--screen=window` was asked for. The launcher script a child is started from,
and the JDK start scripts the build produces, carry the same flag. Headless
AWT still measures text, so nothing about layout changes; the Dock entry goes.

## 27. WAIT's pause is a port, and the screen's queue holds no lock

**The two pieces of blocking code the web port named are gone.** They were
WAIT's `Thread.sleep` and the `synchronized` methods on `ScreenEventQueue`,
and both are gone: the first is a port and the second is the adapter's job.

**The pause is a port.** `PausePort` (`org.jebol.domain.host`) has one method,
`pauseFor(long milliseconds)`, and answers whether the slice happened. WAIT
asks for one fifty millisecond slice at a time and checks its own reason to
stop between them, so the length and the deadline stay the interpreter's and
only the pause crosses. Every interpreter is built with `ThreadPause`
(`org.jebol.application`), which stops the thread the script runs on, so `wait`
works with no host at all; a host with its own supplies it when the interpreter
is built. The web build
gets its own when the worker arrives, which is why the seam exists: a worker
can be stopped outright, and a page's main thread cannot.

**The pause is not a grant.** A host that granted nothing still gets a working
`wait 1`, because waiting for time is the interpreter's own act rather than a
service; what a host may refuse is a wait on a screen or a port, and those
refusals are already there. A platform that will not let the time pass answers
false, and the wait ends there rather than failing - the same path an
interrupted thread takes, which `isEndedByAnInterrupt` in
`BrowserScreenFromTheSourceTest` was already pinning.

**The queue is plain.** `ScreenEventQueue` is now an `ArrayDeque` with no
`synchronized`, because only the interpreter's own thread touches it. The
crossing belongs to the adapter: `GobScreen.posted` leaves an event in a
`ConcurrentLinkedQueue` from whatever thread the toolkit calls on, and
`acceptNextEvent` moves what was posted into the interpreter's queue when the
interpreter asks for one. `BrowserScreen` already worked this way; a desktop
window now does too. The cross-thread tests in
`ScreenInvariantsFromTheSourceTest` exercise the crossing, including an event
posted while the interpreter is inside `do-events`.

**Measured while doing this: TeaVM's `Thread.sleep` works in a browser.** A
page script that waits prints its later lines once the fiber is resumed by
`setTimeout`. But `teavm.exports.main` returns at the first suspension, so the
page harness must be told when the script has really finished rather than
writing its output when `main` returns; that is the worker's business, text
down next.

## 28. The interpreter runs in a worker, and the run loop lives on main's fiber

**A page's worker runs the WebAssembly interpreter and owns the canvas; the
page's own thread only paints what arrives and posts what the operator does.**
The worker's shell (`src/teavm/js/jebol-worker.js`) loads the module, calls the
boot, and speaks for the page. Java's side is `WorkerInterpreter`
(`src/teavm`): `main` boots and then runs the loop - await a script from the
page, run it, report, repeat. Paint lists cross to the page as the same JSON
the server arrangement writes, so the page paints with the existing renderer;
decision 35 replaced that renderer afterwards, and the page now replays the
same surface calls the desktop draws.

**Events go by postMessage and TeaVM's pause-and-resume, not by shared
memory.** The shell buffers the page's postings in a plain JavaScript array;
Java pulls them on the interpreter's own thread, before each run and whenever
the wait asks for an event. Nothing re-enters the module while a fiber is
suspended, and the page needs no cross-origin isolation. Latency is the wait's
ten millisecond poll.

**The run loop has to live on main's fiber, and this cost a day to learn.** A
script's `wait` suspends through TeaVM's fiber machinery, and that machinery
only exists on the path that enters through `main`: a call arriving from a
page callback - an exported method invoked from `onmessage` - runs with no
fiber armed, and the first suspension throws
`IllegalStateException` from `Fiber.suspend`. So the page's call does not run
the script; it hands the script to a `JSPromise` the run loop is awaiting, and
the script runs inside main's fiber where suspension is legal. `JSPromise.await`
(`org.teavm.jso.core`) is the sanctioned pause-and-resume from Java.

**Three smaller lessons.** A method a page calls as a function wants
`@JSExport`, not `@Export`: the first is a JS-facing export the runtime
registers where `teavm.exports` can see it, the second is a raw wasm export
nobody wraps. JSBody classes substitute by whole class and static method -
`DefaultFont` became a factory class because the constructor-call
substitution of `JavaTextMeasure` never applied, while the same shape as a
static call applies fine. And the classlib lacks `ConcurrentLinkedQueue`, so
the web build carries `TConcurrentLinkedQueue` at the classlib's own mapped
name and the classlib's policy finds it; the JVM keeps the real thing.

**The worker says when it has failed.** A worker that died quietly would leave
a page waiting forever, so the shell reports load failures, boot throws and
unhandled rejections to the page, and the browser gate treats any `failed` or
`worker error` line as the end of its wait.

## 29. The page's measurement is taken, not applied

**`BrowserScreen.theBrowserMeasures` queues; the interpreter takes.** The
posting used to write the root gob's size from the thread that carried it,
racing the script that may have been reading that very size. Now the
measurement joins a queue like a click, and the interpreter takes it on its own
thread at two moments: before a run starts, and whenever the wait asks for an
event. A script sees the new size from the moment the interpreter takes it,
and never mid-expression; a window opened after the take centres against the
new size.

**Events are left where they were.** The run-start take takes measurements
only. A click posted between two runs is still judged when the interpreter
takes it inside a wait, which is what
`judgeAClickWhenTaken` in `BrowserScreenFromTheSourceTest` pins - taking
events at run start as well broke that, and the take was narrowed until it
did not.

**A posting that cannot be a size is dropped** - a negative side, or a
missing one - the same rule the event postings already followed: what cannot
be delivered whole is not delivered at all.

## 30. A task's body runs where the host gives it a thread, and nowhere else

**In a browser, never.** A worker has one execution context and the script's
run occupies it; a task's body would be the second thread inside an
interpreter the domain model says one thread owns. `DO` of a task there starts
nothing, which is also what a real 3.22.5 does with it, and the suite's
assertion (`do make task! [1 / 0]` answers the task) stays green.

**On the JVM, by a fresh interpreter on a thread of its own.** The seam is
`TaskPort` (`org.jebol.domain.host`), wired by the host like the file system
and the screen; every interpreter starts with the port that starts nothing.
`ThreadTasks` (`org.jebol.adapter.host`) runs the body in a fresh
interpreter on a daemon thread: the parent's output, none of the parent's
contexts, no deadline, and what it raises stays at its own edge. The console
wires it; the web build does not.

**Decided while building the web worker**, which is what showed what a
platform can give the interpreter. The spec rule is
`TheFateOfATasksBodyIsTheHostsToDecide` in `spec/natives.allium`, replacing the
open question that stood there.

## 31. A host that lacks a service is a different fact from one that did not grant it

**The browser is the host that forced the distinction.** The service model
already had three refusals -- `not_granted`, `not_present`, `never_portable`
-- but nothing could produce the middle one except a window port that was
absent. A page can offer some of what a machine offers and cannot offer the
rest at any price: it has no raw connection for TCP, UDP or DNS, no way to
start a program and no environment it was launched with.

A working directory turned out not to belong in that list. It is the
interpreter's own -- which directory a relative path counts from -- and costs
the page nothing, because it lives inside the store the host already has, so
a browser offers it and grants it. Building the examples with no server is
what showed it: the examples' layouts are loaded by name, and a page
without a working directory cannot `load %sliders.r` at all.

**So `Bounds` carries a second set beside the grants.** `markUnavailable(NETWORK)`
says this host has no such service at all, and `grant` refuses a service
the same bounds mark unavailable, because a host that could change its mind
should have left it ungranted. A refusal picks its reason from the two sets:
in the unavailable set, it
is `not_present`; merely ungranted, it stays `not_granted`. The sentence
names the host, which names itself once with `calls("a browser")`, because
the domain knows nothing about browsers and should not learn.

**The two are refused before anything is attempted**, so a script cannot
learn whether a host exists by timing a name lookup it was never allowed to
make. The web build's three are `NETWORK`, `PROCESSES` and `ENVIRONMENT`;
the spec rule is `ABrowserOffersWhatItsPageCan` in `spec/embed.allium`, and
the tests that pin the sentences are `ABrowserHostFromTheSourceTest`.

## 32. A host that fetches owns the http and https schemes' actor

**CALL is not the only thing a browser does differently with a URL.** Rebol's
`prot-http.reb` implements HTTP over a TCP connection it opens itself, so on
the web it could only ever be refused. The browser has its own fetching, with
its own names, certificates, redirects and cookies, and the goal for the web
is that `read http://...` uses it.

**The seam is the scheme's actor, not the scheme.** `HttpPort`
(`org.jebol.domain.host`) is a new port: one exchange in, one answer out.
A host that supplies one with `HostPorts.withHttp(port)` gets the actor of
`http` and `https` swapped for
`HttpSchemeActor`, a native that answers OPEN, READ and CLOSE through the
port and declines everything else. Rebol's protocol stays loaded and the
scheme keeps its spec and its `info`, so `query http://x none` answers the
same three extra fields as before, `system/modules` still lists `http`, and
a host that supplies nothing is untouched: `read http://` then reaches
`prot-http.reb` and the `NETWORK` service exactly as it always did.

**The answers are Rebol's own, because a script must not be able to tell
which one answered**: the body as text unless `/binary`, `/string` and
`/lines` honoured, `/part` and `/seek` trimmed and sent as a `Range`, a
redirect followed, a status of 300 or 400-and-up raising `protocol`, and a
url that could not be reached raising `no-connect`. `write` has no arm yet
and says so with `no-port-action`, naming the verb.

**Which bodies are text is the content type's decision, as it is in
`prot-http.reb`'s own `decode-result`**, and that was verified against a real
3.22.5 rather than read off the source: a charset parameter, `text/*` or
`application/json` decode (by that charset where one is named, and a charset
nothing can decode leaves the bytes alone); anything else -- an octet stream,
an image, no declared type at all -- is answered as the bytes it is, and
`/string` and `/lines` force text whatever the type says. The first probe of
this was read as saying a 404 answered rather than raised, and turned out to
be pointing at a file that existed, so its "404" was a 200. The corrected
probe is what the rule was text from.

**The body crosses decoded.** Undoing a content coding is part of fetching:
a browser's own `fetch` has done it before it answers, so the actor does not
decompress and the `HttpPort` contract says so.

**The web build's `WebHttp` meets the port with `fetch`,** and the run waits
on the promise while it is in flight -- the same pause-and-resume a WAIT
uses. A `@JSBody` script returns the status, the headers, the bytes and
a reason rather than throwing, because a JS exception crossing back into
Java would be the host exception the invariant forbids.

## 33. The browser's files are the worker's memory, and its console is the page's element

**The browser's files are the page's private store for the site.** OPFS,
reached through `WebFiles.Store`: the whole tree is loaded once at boot, so
every read is answered from memory and never waits, and every change is
awaited into the store, so a file a script has finished writing is a file a
reload finds. A host whose browser has no store gets the memory one, which
keeps nothing. `WebFiles` keeps the confined filesystem's shape: a root that
shows as `/`, a per-port working directory, and `..` clamping at the root.

**The console is the page.** What a script printed the page shows; a line
the operator types arrives as a posting and `WebConsole.readLine` takes it,
stopping the run a slice at a time while it waits, so the worker's event loop
keeps delivering. A line the page cannot read without showing it (a password)
is refused rather than read in the open.

## 34. The browser's cryptography comes from WebCrypto, and its refusals are errors

**Built: digests and HMACs come from WebCrypto, the rest is refused.**
`DigestPort` (`org.jebol.domain.host`) is the port, and the host supplies the
one this build computes with as a port on `HostPorts`: `JvmDigests` over
MessageDigest and Mac, which has Rebol's whole catalogue, and, in the web
build, `BrowserDigests` supplied by the worker over `crypto.subtle`.
WebCrypto's
catalogue is fixed -- SHA-1, the SHA-256 family, and HMAC over them -- so a
browser's digest methods are those four and the rest (`md5`, `sha224`, the
SHA-3 sizes) are not in `system/catalog/checksums` there and are refused
`invalid-arg` by name. The checksum port reads a file through the port's
running digest; the browser's keeps the bytes and digests them when asked,
because crypto.subtle cannot be fed a piece at a time. `random/secure`'s
SHA-1 goes through the same port. JEBOL's own Java digests (RIPEMD-160,
xxHash, MD4) are unchanged and work everywhere.

**And its refusals are ordinary REBOL errors, not host exceptions.** A
method the host has not got raises `feature-na` from the port's own refusal,
and the crypt port's ciphers catch the web stand-ins'
`UnsupportedOperationException` and refuse by name the same way. Nothing a
host cannot compute escapes as a Java throwable, which the invariant
`NoFailureEscapesAsAHostException` forbids. The browser gate asserts that the
worker's SHA-1 and HMAC equal the ones the JVM takes.

## 35. One way of rendering: one executor in the domain, one adapter per platform, one font

**The paint list is walked once, and that walk lives in the domain.** It
becomes calls on a surface -- clip, opacity, transform, fill, stroke, image,
text -- each call carrying its own state so nothing is saved or restored
between them. An adapter implements those calls on one toolkit and decides
nothing: `Java2DSurface` for a desktop window, the page's replayer for a
canvas, `Canvas` and CoreGraphics later. The spec's `DrawingCommand` is that
list, and the rule is that the same paint list produces the same calls on
every surface.

**The alternative was a rasteriser in the domain, and it is not taken.**
The web port's plan as first text wanted fills, strokes, curves,
coverage, gradients, images, clipping and glyphs all scan-converted in
JEBOL, with every platform copying the finished grid. It buys pixels that
are identical everywhere. It costs a rasteriser and a TrueType glyph
rasteriser, built and kept for ever; text without the platform's hinting,
which is a visible loss on the desktop; full-screen software repaints in a
worker and later on a phone where the platform would have used a GPU; and
`gamma` is the only feature it would add that the toolkits lack. R3 ported
AGG into every platform because in its day no platform had a vector API;
today every platform has a good one. What the rasteriser would have
guaranteed, the spec already bounds: a pixel inside a shape matches exactly,
an edge pixel may differ by at most 24 of 255 across at most a fortieth of
the picture, and those two numbers are a change to the specification rather
than a nudge in a test.

**One font supplies the metrics, and it is carried by the build.** Noto Sans
Regular, under the SIL Open Font License, read at the edge and handed to the
domain as bytes the way the error catalogue and the borrowed library already
are, and embedded in the WebAssembly module by the existing resource list.
A reader in the domain takes its `cmap`, `hmtx`, `head`, `hhea` and `maxp`:
units per em, ascender, descender and advance widths, and no outlines at
all. That answers `SIZE-TEXT`, wrapping, line breaks and carets, so those
are the domain's and the same on every platform -- which the spec already
claimed and the code did not do, because each renderer measured with its own
toolkit's font. Each adapter then draws glyphs with the same face, so the
letters are one design everywhere and only the hinting differs. The page
stops laying text out: a few hundred lines of JavaScript that duplicated
`TextLines` are deleted rather than kept in step.

**An adapter draws a stroke where the call puts it.** Comparing stroked
curves, splines, joins, dashes and arrows between the two adapters found every
dash end in Java2D smeared across two pixels where the canvas stopped dead.
That was Java2D's default stroke normalisation moving strokes toward pixel
centres, which a canvas never does, so `Java2DSurface` draws strokes pure.
With that, the stroked pictures match exactly inside every stroke and stay
within the edge allowance; the allowance was not widened.

**Decided while planning the web port**, after the plan's first draft
proposed the rasteriser. Goals 3 and 4 change with it: a phone app
implements the surface on its own canvas rather than copying a grid.

## 36. A page is painted between events, not once per show

**The browser adapter coalesced nothing, and a drag showed what that costs.**
A GUI shows every face that changes, so one move of a slider drag is three
shows -- the example's scroller, its progress bar and the slider itself --
and the adapter painted the whole surface for each of the three: 121 paints
for a 40-move drag, around 24ms of work per move, in three pictures that
hash identical because the list is the whole surface every time. A toolkit
adapter never had the problem: `DesktopScreen` invalidates and Swing decides
when to draw. The web adapter had no toolkit to lean on and no coalescing of
its own.

**So a show is remembered, and the picture offered is the tree as it stands
when the interpreter next looks at the screen.** `ScreenPort` gained
`offerShown`: the interpreter calls it before a run, when one ends,
and `BrowserScreen` calls it inside a wait once there are no more events to
take. That is the whole rule, and it is what the spec's browser rule now
says: nothing but the timing changes, because the list is always the whole
surface. The tests that counted paints per show were asserting the timing
rather than the picture, and now assert the picture the page ends up with.

**Measured again the same way:** 40 paints for a 40-move drag at a pointer
posting every 50ms, one per move; at a real mouse's rate -- 200 moves
posting every 8ms -- 56 paints, one per event batch, at a steady 35 frames
a second, draining 24ms after the pointer stopped. The first picture after
a move arrives 10 to 22ms later, which is the wait's ten-millisecond poll
plus one whole-surface walk. The walk itself is the next thing to shrink if
it ever needs shrinking: 98KB of JSON for the two example windows.
## 37. What the timings say: the library load, and streams on the per-call path

**The goal's next move was wrong, and the measurement says so.** The boot
goal said the system object's declaration is evaluated on every boot "although the
answer is the same each time", and that caching the built object was the next
move. Temporary stopwatches around the constructor of a warm
`Interpreter.create()` (2026-10-13, macOS, JDK 25, `scripts/timing.py boot`)
put the phases at: declaration tables 0.2ms, the evaluator and its symbols 5
to 7ms, the system object and its declaration 1.0ms, the borrowed library 56
to 83ms, everything after it 0.1ms. A warm create is 75ms median, 90ms p95,
and a first call 0.6s while the JVM loads and JITs. So the whole of boot is
the borrowed library's evaluation; caching the built system object would save
about one per cent, and that sentence is deleted from the goal.

**A full `./gradlew test` builds about 16,300 interpreters**, counted with a
temporary counter across the 36 forks of one run (5m16s, 27,405 tests), so a
millisecond off create is around sixteen seconds of core time.

**The interpreter is 15x to 34x slower than a real 3.22.5 on the timing
workloads.** `scripts/timing.py speed`, one second per workload, JEBOL JVM
against `./r3-head`: function calls 848ms against 25, a counting loop 821
against 28, recursion 508 against 19, a path into a context 341 against 18,
growing a block 702 against 46, growing a string 699 against 41. Per value
walked that is roughly 20 to 25x. The wasm worker runs the same workloads at
2.2x to 3.3x the JVM, and its own boot -- the shipped page's report, in the
gate's headless Chrome -- is 471ms inside the worker against the JVM's 75ms,
about 6.3x, with the module's fetch and compile 69ms on top of the page's 598ms
total.

**The hotspot is one pattern, not many: stream pipelines on the path every
call takes.** JFR stacks from the call workload attribute about half the
samples to `java.util.stream` machinery, under two JEBOL methods:
`PendingCall`'s constructor runs `arrivingParametersOf`, which runs
`declaredParametersOf`, which filters and collects with a stream, and
`Evaluator.checkArgumentTypes` does the same per native call. Every infix
operator, every native, every set-word builds two or three stream pipelines
and their short-lived lists. The library load pays the same tax: it is 56 to
83ms of calls to natives and functions that each build the pipeline.

The rest of the profile, each with a stack and a share:

- `MapValue`'s case-insensitive lookup streams the whole key set and, per
  key, calls `AnyStringValue.text()` -- building a Java string just to
  compare it (about 5 per cent of boot samples, and `String.equals` and
  `equalsIgnoreCase` show in run profiles too).
- `AnyBlockValue.copied` copies every block through a stream; the boot copies
  the whole library this way (about 8 per cent).
- `SeriesMemory` keeps a `ConcurrentHashMap`-backed identity set and mutates
  it on every series buffer allocated, in a domain that is single-threaded by
  construction (about 3 per cent, plus its rehashing).
- Word evaluation hashes a canonical string in a `HashMap` per lookup;
  `HashMap.getNode` and `String.equals` are 7 to 17 per cent of the
  lookup-heavy workloads.

**What this makes of the two goals.** The boot work and the speed work are
one job: the same per-call overheads dominate both, so fixing the call path
moves the gate, the desktop and the browser at once. Goal 14 carries the
ranked list; this decision holds the numbers. The instrument is
`scripts/timing.py`, whose docstring says how to run it and what the figures
do and do not mean.

## 38. The per-call plumbing comes out, and the web boot was compared wrongly

**What was done, all of it removing work that was redone per call.** The
ranked actions of goal 14, in the order decision 37 measured them:

- `PendingCall` no longer filters parameters with streams on every call. It
  keeps the callee's own parameter list and builds the sequence of parameters
  this call will fill once, lazily, with a loop; `arityOf`,
  `arrivingParametersOf`, `declaredParametersOf` and their pipelines are
  gone, and with them two or three short-lived lists per operator, native and
  set-word.
- `PendingCall.argumentsInDeclaredOrder` was called up to four times per
  invocation, each time copying the argument list; `invoke` asks once, and an
  assignment asks only for its one value. `runFunction`'s three streams over
  the parameter list are three loops over the same list, and `openFrameOf`,
  which streamed the open-call stack on every function call, is a loop.
- `MapValue`'s case-insensitive match walks its keys instead of streaming
  them.
- `AnyBlockValue.copied` and `copyFront` copy with loops, and the per-boot
  copies of the whole library go through them.
- `SeriesMemory`'s outstanding set is a plain `HashSet`; the class's own
  claim is one writer at a time, and a `ConcurrentHashMap` was paying a lock
  and an entry per buffer for a promise nobody needed.
- `Context.slotOrNone` makes the `knows`-then-`slotFor` walk one pass, and
  canonical names are interned, so a word lookup is an identity compare
  rather than a character-by-character one. The C keeps one symbol per
  spelling for the process for the same reason.

**What it bought.** The seven workloads of `scripts/timing.py speed`, before
and after, the real 3.22.5 beside them unchanged: function calls 848 to about
330ms (34x to 13x), a counting loop 821 to 330 (29x to 11x), the same in a
local 764 to 330 (24x to 11x), recursion 508 to 240 (27x to 12x), a path into
a context 341 to 210 (19x to 12x), growing a block 702 to 400 (15x to 8x),
growing a string 699 to 420 (17x to 10x). The wasm worker halved on the same
workloads: calls 1838 to 888ms, a counting loop 2109 to 1028. A warm
`Interpreter.create()` went from 75 to 54ms (first 438, p95 84), and the
gate's test run from 5m16s to 4m19s.

**And a correction: the web boot was never 6.3x the JVM's.** Decision 37
compared the worker's cold interpreter start, 471ms, against the JVM's warm
create, 75ms. The comparison that means something is the JVM's first create
in a fresh process, 590ms then and 438ms now, against the worker's 459ms:
the two spend most of their time on the same one-time work, parsing the
borrowed library and running it once, and the JVM's warm 54ms is that same
work already parsed and JITted. What the worker pays on top is the module's
fetch and compile, 65ms, which is V8's business and not JEBOL's.

## 39. The library image, and what it does and does not buy

**Built: the borrowed library is parsed at build time into one image the
interpreter boots from.** `LibraryImage` (`org.jebol.domain.read`) writes and
reads the whole value tree of a library file as a compact binary, DEFLATE
-compressed with the JDK's own `Deflater`/`Inflater`; line breaks, spellings,
word kinds, the six string kinds, bitsets and maps travel exactly.
`WritingTheLibraryImage` (`org.jebol.application`) walks `src/main/resources`
at build time and writes `org/jebol/library.image` - 109 files, 222,458 bytes
- and the build runs that task before anything compiles tests against it
(`processResources` depends on it), so a library file the scanner or the
image cannot carry fails the build rather than a test. `LibrarySource` reads
it once per process into the same cache it always kept, hands out fresh
copies as decision 22 requires, and falls back to transcribing the text when
the image is absent. The text stays, for that fallback. One file is skipped
by name: `modules/unicode-utils.reb` has an unclosed `[` upstream, and a real
3.22.5 refuses it with `missing "]" at "end-of-script"`, so no interpreter
can read it and the image does not pretend to.

**Exactness is a test, not a hope.** `TheLibraryImageFromTheSourceTest`
round-trips every `.reb` resource in the library through the image and
compares the trees value by value, spelling by spelling, line break by line
break. Everything else boots through the image: the whole suite and the
browser gate.

**What it bought, measured like for like.** The JVM's first create in a fresh
process went from a 427ms median without the image to 408ms with it, and the
warm create moved by about nothing (44 to 57ms across runs either way). The
worker's interpreter start did not move at all - 464ms - because inflating
and rebuilding the values costs about what scanning them cost in wasm. The
gate's test run went from 4m19s to 3m47s, and **that is almost all from the
other half of this work**: the copier learned to duplicate bitsets and maps,
so the eight-odd files holding them (the mezz constants, `sys-ports.reb`,
`prot-mysql`-era protocols and friends) are now cached once per process and
copied per boot, where before every create re-scanned them. Two milliseconds
times 16,300 creates is the thirty seconds.

**The reading that matters for boot.** The cold start is dominated by
building the value objects and initialising the code, not by reading text, so
a pre-parsed image can only take the scan out of it - about 4 per cent.
R3's 10ms is not its boot blob doing something clever: it decompresses text,
scans it and evaluates the whole library too, as `b-init.c` and
`sys-start.reb` show. It is fast because everything else is a C data
structure with no class loading and no per-word interning. A JEBOL boot that
skipped *construction and evaluation* would need a boot image in the strong
sense - R3's delayed module loading is the nearest thing that exists - and
these numbers bound what pre-parsing alone can do.

**Decision 40 took this back out**, for the reason these measurements gave:
19ms of a cold create and nothing else measurable, against 222 KB in both
artifacts and a codec to keep. The finding above is what survives.

## 40. The library image comes back out, and the cold start is the JVM, not the scan

**Built in decision 39, measured, and reverted.** The image was exact and its
test proved it, but what it bought was 19ms of the first
`Interpreter.create()` in a fresh process - 427ms without it against 408 with,
five runs each way - and nothing else that could be measured. That is what its
mechanism predicts: a warm create copies the cached trees whether they came
from the image or from the scanner, so only the first create of a process can
gain, and in the worker inflating and rebuilding the values cost what scanning
them cost, 459 to 467ms either way. The test run's spread across identical
code is 4m02 to 4m29, which swallows any difference the image could make;
the one run of 3m47s was that spread, not the image. So 222 KB in the jar and
the module, a build step, and five hundred lines of codec went back out.
What stayed from the same batch is the part with a mechanism that shows in
warm creates: the copier learning bitsets and maps, which stopped the
eight-odd mezz files carrying them being re-scanned on every create, and the
repair of `modules/unicode-utils.reb` (the findings note has the evidence).

**The cold start, measured rather than named.** Temporary stopwatches on the
first create against the next two: the system and its symbols 121ms cold
against 8 warm, the system object 48 against 1, the library 215 against 65,
the declaration tables 23 against 0.2. That is a first-use cost spread across
every phase, and the JVM's own logging says what it is: 2,626 classes are
loaded during one create, 1,162 of them JEBOL's, and about three thousand
methods are JIT-compiled. The boot's actual work is around 75ms; the other
330ms is the JVM loading, verifying, initialising and compiling the
interpreter's whole surface. It is not dispatch - a switch over a sealed
interface is a table switch and costs nothing - it is that a cold JVM pays
per class and per method, and a boot touches nearly all of them.

## 41. A directory is an app, and a page is the same app on the web

**A directory given to the command line runs the `main.reb` inside it.** The C
has no such form: `./r3-head app/` answers `access error: cannot decode or
encode (no codec): unbound`, which JEBOL answered too, and a directory is what
an app is. The entry is `main.reb`; the app's directory is the working
directory, which the C already makes of a script file (`what-dir` inside one is
the script's own directory, and JEBOL matches); and a directory with no
`main.reb` is refused with a sentence that says so rather than that codec
error. This is a deliberate divergence - an app directory runs here and cannot
run in a real 3.22.5 - taken because running an app is what a command line is
for, and the refusal is a divergence in the right direction besides.

**The same shape on the web: a directory of REBOL is a web app.** The runner
page (`site/runner.html`, the canvas, the replayer and the worker hookup) is
fill in by the build with the app's file list and its `main.reb` as the entry;
every file is fetched into the worker's store before the entry runs. So
`./gradlew assembleSite -PsiteDir=my-app` is the whole of making one, a
directory with an `index.html` in it keeps that page instead, and the shipped
demo is the same with `examples/r3-gui` as the directory: its driver, once the
page's own script tag, is now just `examples/r3-gui/main.reb`.

## 42. The bare console runs start, and the command line grants the machine

**`jebol` with nothing on the command line runs `sys/start` like every other
run.** It used to skip start and print a banner of its own, so the box, the
home and data paths, the user context and the colours a real 3.22.5 console
shows were all missing. R3's host calls `RL_Start` before it opens the console
whether or not anything was asked, and `host-main.c` says so; now JEBOL has
one path through `runCommandLine`, and the console is what
`TheHostDecidesAfterStart` in `spec/repl.allium` opens when no script was
named.

**The plain console does not keep its sandbox.** It was built on
`Bounds.standard()`, which reaches nothing, and that is why it could not run
start at all: the first `get-env "HOME"` in `sys-start.reb` raised
`no-service` before the banner. The console is the same command line with
nothing to run first, so it takes the same grants a script does, and
`TheCommandLineGrantsEveryService` in `spec/repl.allium` now says so for both.
A person at a keyboard can already run `jebol --do "..."` or a script, so the
sandbox was not a boundary; it was an inconsistency that made `read
%README.md` answer `no-service` at the prompt. That resolves the loose end
goal 9 carried.

**What start prints is R3's banner, not JEBOL's.** The old line, "JEBOL --
REBOL 3 on the JVM. Type quit to leave.", is gone from the command line. What
JEBOL calls itself in the banner (`Rebol/core` against `Rebol/Bulk`) and the
empty build fields are goal 13's next part, not this one.

**One difference this leaves open.** A `quit/return 42` typed at the prompt
leaves the process with nought here and with 42 in R3, because the console
loop returns without carrying what QUIT held. It is text down in goal 13
rather than fixed here, since nothing asked for it and the fix is a behaviour
of its own.

## 43. JEBOL checks TLS certificates, and differs from R3 on purpose to do it

**A secure connection is refused unless the host's authority trusts the chain
and the certificate names the host.** That is `spec/embed.allium`'s "Trusting
a server" section, and the R3 behaviour it replaces is finding 222. It is the
one deliberate place JEBOL answers differently from the C, because the
alternative is that every Android or iOS app built on JEBOL can be read and
rewritten by anyone on its network.

**The split.** `CertificateAuthority` is a host port: given the chain as the
server sent it, it answers the names the leaf vouches for or why it is not
trusted. `JavaCertificateAuthority` is the JVM's, over `CertPathValidator` and
the JDK's own trust store, with an injectable clock and revocation off.
Whether the names cover the host is `VouchedNames` in the domain, the same on
every host: subject alternative names only, wildcards as one whole leftmost
label with two labels after it, addresses compared as addresses.
`ZeroTrustAuthority` is what an interpreter has until the host supplies one.

**The grant.** `HostService.CERTIFICATES`, refused by `Bounds.standard()`
like every other grant, and lacking in a browser, which judges certificates
itself inside fetch. The command line and the suite grant it and use the
JDK's store.

**The hook is an edit to the vendored `prot-tls.reb`, not the CRT codec.**
The TLS goal had planned the codec, and it cannot work: the codec decodes one
certificate at a time and never sees the host, and the TLS 1.3 signature hole
is not in the codec at all. Four edits, each the smallest that closes its
hole:

- `do-TLS-open` calls `require-certificate-checks` for a client, so a script
  without the grant is refused by the open itself. Asked any later, the
  refusal would land inside the handshake, where HTTP turns it into
  `no-connect`.
- `decode-certificates` keeps the raw chain and calls `check-certificates`
  with it and `ctx/tcp-port/spec/host`, for a client only.
- `decode-certificate-verify` checks RSA-PSS at all three hashes and ECDSA at
  all three curves, and ends the handshake with `Decrypt_error` when the
  signature does not verify or the scheme is one it cannot check. Ed25519 is
  offered and has no native, so a server choosing it is refused.
- `decode-server-key-exchange` reads RFC 5246's signature algorithm and
  keeps ECDSA's answer.

`require-certificate-checks` and `check-certificates` are natives JEBOL adds
and R3 has not got, registered as extensions beside `size-text`.

**What is not done, and is in the spec as open questions:** revocation, the
repair of a chain sent out of order or missing an intermediate, and Unicode
host names.

**The fixtures** are made by `scripts/make-test-certificates.sh` and checked
in under `src/testFixtures/resources/org/jebol/tls/`. It needs OpenSSL 3.4 or later
for `-not_before`. Running it again replaces every key, which is harmless,
since the tests read only what is checked in. `LocalTlsServer` serves any of
them over TLS 1.2 or 1.3. It can also present a chain with a key that does
not belong to it, which is how the impostor tests stand in for someone in
between.

**The command line's TLS is checked against servers the tests spin up
themselves,** over the fixtures above. `TrustingAServerEndToEndTest` reads a
chain the host trusts and refuses expired, self-signed, wrong-host and untrusted
chains; `SecureConnectionsAtTheCommandLineEndToEndTest` and
`UntrustedServersAtTheCommandLineEndToEndTest` put the command line itself to a
local server it must refuse. Nothing in those checks reaches a host on the
internet.

## 44. Running out of memory is `no-memory`, raised where the work asked for it

**An `OutOfMemoryError` becomes R3's `no-memory`, an internal error worded
"not enough memory", in two places in the evaluator and nowhere else.**
`runNative` converts it beside `StackOverflowError`, so every native and
action is covered. `walkFrames` converts it as well, so the walk of a block,
which is where a function's body runs, raises it too. Both are the innermost
place the work happens, which is what lets the nearest `try` around the work
catch it. `ErrorsAreValuesNotHostExceptions` in `spec/eval.allium` now names
it.

**One mechanism rather than a sweep.** The goal asked for a sweep of
everywhere JEBOL allocates on a script's say-so. That would be hundreds of
sites, and one forgotten site is a crash on a phone. Every one of them runs
inside a native or a walk, so converting there covers the ones nobody has
text yet. TABBING's own catch is gone for that reason. `Datatype`'s stays,
because the value layer can be reached without the evaluator, through a host
building values.

**The error gives nothing back.** A series that grew keeps what it got, so a
script still holding it can meet `no-memory` again. Recovery is the script's
part: let go of the series, and the next allocation succeeds. The tests
release it and then do more work, and that is what makes them a test of
recovery rather than of reporting.

**The tests run out of memory in a child JVM, never in the test JVM.**
`RunningOutOfMemoryEndToEndTest` starts the command line with a 48 MB heap
and the serial collector, which gives up in about two seconds where G1 and
the parallel collector can take twenty. Exhausting the heap of the shared
test JVM would make whatever test ran beside it fail, which is a flake by
construction. Each case is one growth path: appending to a block, string,
binary or map, joining, rejoining, ajoin, molding, forming, copying, inserting
many times, expanding a tab, and reading a file larger than the heap. One more
case leaves the error uncaught and checks that the console reports REBOL's
error rather than Java's.

**The cases run side by side, and they found a slow map.** Each child JVM is
independent, so the class asks JUnit to run its cases concurrently; `test`
switches JUnit's parallel mode on, and every other class still runs one case
at a time because that stays the default. The class takes about fourteen
seconds. Before that it took about two hundred, and a hundred and seventy of
them were filling a map: a lookup that ignores case compared the key with
every stored key in turn, so filling a map took time in proportion to the
square of its size. `KeysAlikeApartFromCase` now indexes each stored key by
its case-folded form, and `MapKeysAlikeApartFromCaseTest` holds a hundred
thousand writes to the interpreter's five-second limit.

## 45. Three test suites, named for what they hold, each a source set

**The tasks used to be named for history rather than content.** `test` and
`check` sounded like everything and ran neither the browser nor the network
tests; `browserCheck` and `networkCheck` were separate gates; only `allTests`
ran the lot. A run of `test` came back green while the browser gate was red,
and nothing in the name said it would.

**Now there are three suites and one gate.**

| Task | Source set | What decides membership |
| --- | --- | --- |
| `unitTests` | `src/unitTest` | The test never builds an interpreter |
| `functionalTests` | `src/functionalTest` | The test builds one and runs REBOL through it; Rebol's own suite is here |
| `endToEndTests` | `src/endToEndTest` | The test drives a real interface: the command line, a real Chrome, servers the tests spin up themselves |

`allTests` runs the spec check and all three. `test` and `check` both run
`allTests`, so no task runs less than its name suggests -- which means `build`
needs Chrome and the internet too, and that is the price of the names being
true.

**A test's kind is where it lives, not an annotation on it.** Each suite is a
Gradle source set, so a new test is filed by the directory it is text in and
there is no tag to forget. An untagged class used to fall into whichever task
excluded nothing; now there is no such task. The browser and network tags went
with the gates they selected for.

**What the three share is a fourth source set, `src/testFixtures`** -- the
suite host, the corpus runner, the TLS server and its certificates, the timing
programs, and every test resource. The scripts that run those programs
(`sweep.py`, `runtime-parity.py`, `timing.py`) put `testFixtures` on their
classpath. Three programs that read the suite -- `ShowAssertion`,
`ShowFailures`, `SuiteStops` -- live with it in `functionalTest`.

**One check had to learn where the suites are.** `EveryPropertyIsActuallyRunTest`
found the test classes by asking where its own class was compiled, which after
the split would have been the unit suite alone, and the rule would have held
for a third of the tests while reporting green for all of them. It is now
handed all three directories by the build, and asserts that it was.

## 46. The assertions that need the network have a gate of their own

**Twelve of Rebol's assertions reach a host on the internet.** Ten are
`thru-cache-test.r3`, which fetches from `raw.githubusercontent.com` and reads
`httpbin.org`; two are the `import needs url!` test in `module-test.r3`, which
imports a script from `github.com`. A real 3.22.5 fails them too, so they are
already findings, but keeping them in `functionalTests` made every build depend
on those hosts -- and on whatever answers them, which on a machine behind TLS
inspection is not github at all.

**They run in `./gradlew networkTests`, from `src/networkTest`, out of
`allTests`.** `needs-the-network.txt` names them beside the vendored files;
`RebolSuiteTest` does not run the step that would fetch, and answers a verdict
saying the network gate grades it. The network gate grades the three that are
not findings and re-checks the nine that are, so a finding that starts passing
still turns a build red. `SuiteSelectionTest` fails if an entry stops naming an
assertion.

**Why not a tag or a property.** Decision 45 settled that a test's kind is
where it lives: a tag would make the same class mean different things on
different machines, and a property would hide which assertions a green build
did not run. The list is data beside the Rebol files it is about, so it moves
with them and is read by both gates.

**What the vendored suite still measures.** Everything else runs on every
build, against local servers or in-process stand-ins. The two files keep their
network assertions, unmodified, in the gate that can honestly run them.

## 47. The command line trusts the machine, and runs a script from a url

**The authorities are the machine's, not only the runtime's.**
`JavaTrustAnchors` adds the platform store to the JDK's: the keychain on macOS,
`Windows-ROOT` on Windows, and the system bundle on Linux. `--cafile` adds a
bundle of its own, read at startup and taken off before R3's argument parser
sees it. The list widens and no verdict moves: expired, self-signed, wrong-host
and unknown still end the handshake. Embedders keep the "trusts nothing"
default, because the anchor list is the host's; this is the command line's
host.

**A certificate with a GeneralizedTime validity now reads.** X.509 permits it.
The borrowed `codec-der.reb`, `codec-utc-time.reb` and `codec-crt.reb` decoded
only `UTC_TIME`, so a certificate that wrote its validity the other way lost
everything after that field and the handshake stopped at "Missing public key in
certifiate". The three files now decode both forms, and ORDER.txt records the
edits. That is what made github readable behind this machine's TLS inspector,
and it is why the nine github assertions came off `fails-on-rebol-too.txt`: a
real 3.22.5 still cannot read them, and JEBOL now can. Standing says so.

**A script may be given by https url.** `jebol https://.../main.reb` takes the
same path as `jebol main.reb`: the header is read, `system/script` is filled,
`NEEDS:` are imported, a module is imported as a module, and QUIT is not caught
on the way out. A url has no directory to run in and no local file to check, so
those two steps are the file's alone; `sys-start.reb` carries the branch and
ORDER.txt the note. A url that cannot be fetched is reported and leaves with
one, as a missing file does. `./gradlew networkTests` runs a real github
script, with its arguments, at the command line.

## 48. The window screen brings this machine's dialogs

**`--screen=window` supplies `DesktopWindows` along with the desktop screen.**
The WINDOW service has five requests -- BROWSE, REQUEST-FILE, REQUEST-DIR,
REQUEST-COLOR and REQUEST-PASSWORD -- and the command line granted every one of
them while supplying a port for none: all five were reachable and none could
succeed, from `jebol --do "browse http://example.com"` to a script asking for a
colour. The adapter has existed since 2026-08-10 and only tests and embedding
hosts could reach it, because the command line never handed it over.

**A window, and for now only a window.**
`--screen=window` is what says a run may use this machine's display; it is the
same switch that turns the JVM's headless mode off, decision 26. `--screen=web`
draws into a page and a bare run draws nothing, so both keep the refusing port
they had, and a request there is refused as the host having no screen rather
than as the service being withheld -- the grant was never the half that was
missing. `TheWindowScreenComesWithTheMachinesDialogs` in `spec/repl.allium`
says so.

**The example is `examples/choose-colour/`, and it is run by hand.**
`jebol --screen=window examples/choose-colour/` opens the machine's own colour
chooser: the script asks with `/default` for a blue, prints each colour picked,
suggests the last pick back to the chooser, and says "no colour chosen" when
the operator cancels. The mac AppleScript version in `mezz-osx-dialogs.reb` is
still not loaded, deliberately; this is what runs instead, on every platform.

**No test opens a dialog, on purpose.** A chooser left open hangs the build
until somebody clicks it, which is exactly what happened when two tests first
reached one; the test JVMs run headless for that reason. So the tests stop at
the port boundary: `ReplPortsTest` pins that the command line hands
`DesktopWindows` over with a window and a refusing port otherwise, and
`TheChooseColourExampleEndToEndTest` runs the example's real file against a
recording operator that answers with colours. The real chooser is proved by
running the example, not by the gate.

**What this leaves: the page's own dialogs, which are goal 1's.** A browser
has a colour picker, file and directory choosers and a save picker, so a page
fills four of the five needs with its own controls; the fifth, the password,
is one Rebol form everywhere, because no environment has a password dialog
worth taking -- Swing's is a generic box and a browser has none at all. The
port asks for what it needs and the environment answers with what it has,
which is what a port is for. The form stays Rebol, and the plumbing to run it
on a page is the web's to carry. That is a second adapter with a transport of
its own -- asks down the page's stream, answers posted back -- and when it
lands this decision's rule becomes "each screen brings its own dialogs": the
machine's with `--screen=window`, the page's with `--screen=web`. Until then a
page run keeps the refusal, because a port that is not built is honestly
absent.

## 49. Each screen brings its own dialogs, and a page answers its services itself

**The second half of decision 48 is reversed.** A page now has dialogs of its
own: `--screen=web` hands over `PageWindows`, and so does the shipped page.
`EachCommandLineScreenBringsItsOwnDialogs` in `spec/repl.allium` replaces
`TheWindowScreenComesWithTheMachinesDialogs`, and `EachScreenBringsItsOwnDialogs`
in `spec/screen.allium` says it for every screen.

**One round trip, two transports.** The interpreter runs in a worker, and a
worker has no elements, no clipboard and no MIDI, so every service that needs
the page is an ask: one JSON object numbered from one, answered once with
given, declined or refused (`PageAsk`, `PageAnswer`, `PageRoundTrip` in
`src/main`, tested on the JVM). In a page the worker posts the ask and awaits a
promise the shell resolves when the page posts the answer (`WorkerRoundTrip`);
on the command line the ask rides the picture's event stream and the answer is
posted to `/event` while the interpreter waits (`WebScreenServer`). The page's
half is one script, `jebol-asks.js`, served by both. Declining is none,
refusing is a catchable error naming why, and an answer to no waiting ask is
dropped.

**A picker opens inside the operator's click.** A script cannot click, and a
picker, a popup and a clipboard read each refuse to open without one, so an
ask that needs a gesture is shown behind a prompt the page draws, with a
button that does it and one that declines.

**Files picked are copied, never handed over as bytes.** REQUEST-FILE answers a
path, as a real 3.22.5 does. Each pick gets a fresh folder numbered one past
the highest already in the store -- `%/picked/1/notes.txt`, then
`%/picked/2/notes.txt` -- because the store outlives a reload and counting from
one again would overwrite yesterday's pick. On the command line the same
folders are under `picked/` in the directory the run started in, because the
default policy -- a real 3.22.5's as much as JEBOL's -- lets a script read
nothing outside its own directory, and a copy in the machine's temporary
folder was refused on the first READ. A save answers a path
the same way, and `PickedFiles`, wrapping the file port, sends the bytes out
when the file is finished with: after a one-shot WRITE, or when a port opened
on it closes. That moment is new to the core -- `FilePort.finishWriting` --
and it is the only change the file port needed. Where the browser has
`showSaveFilePicker` the bytes go to the chosen file, and elsewhere they are a
download.

**What the gate cannot reach, and is checked by hand.** Chrome's own save
dialog cannot be operated by WebDriver, and headless Chrome aborts it, so the
gate proves the picker opens inside the click and that an aborted picker is
none; writing to a file chosen there is checked by hand, as decision 48's
chooser is. Firefox's driver cannot hand a folder to a folder chooser, so
REQUEST-DIR's copy is proved in Chrome and Firefox proves the decline.
Firefox's clipboard read and its Web MIDI need the operator to confirm in the
browser's own chrome, which no driver reaches; the gate runs Firefox with its
testing preferences for both, which leave the API itself as it is and give
Web MIDI the test devices that echo what they are sent.

**The password and the console's line are one Rebol form.**
`src/main/resources/org/jebol/forms/screen-form.reb` draws a window with a
field and runs a modal handler at the front of the event handlers until Enter
or Escape; REQUEST-PASSWORD uses it wherever the screen has a display, and
INPUT uses it where the console says it is typed on the screen, which a page's
is. The screen's adapter carries what a canvas-drawn field needs: on a page a
hidden text input holds the focus while the form is open, so a phone shows
its keyboard and an input method composes into it. The port's own
`askForPassword` stays for a host with a window port and no screen.
The line form is headed by whatever was printed since the last line ended,
as a terminal shows the prompt on the line being typed, so `ask "Your name? "`
heads its form "Your name?"; the hidden input carries that heading as its
accessible name. The first try showed why: two forms in a row with the same
heading looked like one form that had ignored Enter. The runner keeps what a
script prints in a panel over the foot of the screen, for the same reason.

**BROWSE answers unset, and a cancelled REQUEST-FILE/MULTI answers none.**
Both are what the C answers -- `R_UNSET` from BROWSE, `R_NONE` from a chooser
that came back empty whatever the refinements -- and JEBOL answered none and an
empty block before.

## 50. An app has one history, on every screen, and Back is an event

**A real 3.22.5 has no history, no address and no navigation event, so all of
this is JEBOL's own.** The rules are `spec/nav.allium`. One history per app,
however many windows it has, as a phone keeps. Every screen has one: a window,
the shipped page and `--screen=web`, and the phones when they come. A run with
no screen is refused as not present. Navigation is a host service of its own,
granted by the shipped page and by the command line.

**The words are `go`, `go/replace`, `go-back`, `go-forward`, `current-place`
(with `/refused`) and `unsaved-work`.** BACK and FORWARD are series actions,
and `go` is defined nowhere in Rebol's library, the 2010 GUI or the examples.
`go-back` cannot be `go/back`: a refinement cannot remove the argument `go`
takes.

**A place is data a script can write.** It is a word, a string, a number, a
logic, or a block of them nested to any depth, and `go 'survey` goes to
`[survey]`. None is not a place and is refused. The address is the place
written as data: each item as MOLD/ALL writes it, then percent-encoded, so
`[survey 3]` is `#survey%203`. MOLD/ALL, because MOLD writes `true` as a word.
Reading an address is TRANSCODE and a type check, and nothing is ever
evaluated, because anyone can write a link. A link that does not read starts
the app at its start, and `current-place/refused` says what it was. A place
the app does not know is the app's own "not found", since a server never sees
a hash.

**The limits are the strictest host's everywhere.** Safari's rate, 100 changes
the app makes in 30 seconds, and an address of at most 2,048 characters. The
length is chosen rather than measured: every target keeps far longer, and a
link is meant to survive being pasted. Going to the place already current adds
nothing, which Android calls single top.

**Back and Forward are two event types appended after `char`,** so every index
the C fixed keeps its number. They arrive on `system/ports/event`, name no
window, and carry the place in `event/data`, as a dropped file does. An app
that has never asked about its history hears none of them, because the 2010
GUI looks a handler up by the event's type, and an event type it does not know
would stop it. `sysobj.reb` carries the two words; it is JEBOL's copy of the
boot file and now differs from Rebol's there too.

**The domain decides and the screen carries out.** `History` and `Navigation`
are the domain's. A screen hands its `NavigationPort` to the domain:
`WindowHistory` for a window, where a move arrives at once, and `PageHistory`
for a page, where the browser's own `pushState`, `replaceState`, `back` and
`forward` do it and popstate reports the arrival. Each browser entry carries
its index in `history.state`, which survives a reload, so Back after a reload
still names the entry it reached.

**Unsaved work is a flag set in advance, and the host asks.** Leaving cannot
wait on the interpreter: `beforeunload` is synchronous and the interpreter is
in a worker. So the page holds the flag and asks through the browser's own
prompt, and a window asks through Swing's confirmation before the close
reaches the script. Back between two of the app's own places is never
guarded. The page's close moved from `beforeunload` to `pagehide` for the same
reason: a leave the operator cancels must not have closed every window
already. A page brought back from the back-forward cache reloads, because its
app was closed when it was hidden.

**In a window the keys a browser uses move the history too**: Alt+Left and
Alt+Right on Windows and Linux, Cmd+[, Cmd+], Cmd+Left and Cmd+Right on macOS,
and the mouse's side buttons. They still reach the app as key presses. The
trackpad's swipe in a window is an open question in the spec: Java has no
public way to hear it.

**The first window's title is the app's.** On a page it is the tab's title;
in a window it is the first window's title bar, which now follows its text on
SHOW where before it was read once.

**What the gate cannot reach.** The test JVMs are headless, so a real window
is checked by hand, as decision 48's chooser is: the title following, the
Back keys, and the confirmation on close.

## 51. A fetched module is checked against a digest its address was given

**Every address in `system/modules` names the `bundled:` scheme today, so this
guards the day one does not.** Rebol's path from the table to the evaluator has
no check in it: DOWNLOAD-EXTENSION reads the address, writes what came back
into the modules directory, and the next IMPORT evaluates it. A module's own
`checksum:` header is no help, because whoever changed the module changed its
header too. The rules are under "A fetched module is checked before it is
kept" in `spec/natives.allium`.

**The digest is recorded beside the address, by whoever wrote the address.**
`system/options/module-digests` is a map from the address read to the SHA-256
of what is there, declared empty in JEBOL's `sysobj.reb` so each interpreter
has its own. A map rather than a second value in `system/modules`, because
Rebol's own code reads that table as name and url, and `url? module` is how
IMPORT decides to fetch at all. Keyed by the address read rather than the name,
because a compiled extension's table entry is a directory and the file read
from it is named for the platform; each platform's file has its own digest.

**An address outside the build with no digest is refused before it is read**,
with `access/cannot-open` and the reason "no SHA-256 digest is recorded for
it". Anything that is not a binary of exactly thirty-two bytes counts as no
digest, and so does a table that is not a map: a SHA-1, a string of hex or
none could only ever disagree with a SHA-256, and saying "no digest" says why
where a mismatch would not. A `bundled:` address needs none, because its bytes
are the program that is running; a digest recorded for one is checked all the
same.

**A mismatch is Rebol's own error for a tampered file**, `access/invalid-check`,
the one IMPORT/CHECK already raises. The check is of the bytes as they arrived,
before the gzip a compiled extension is served in is undone, so a decompressor
is never handed bytes nobody vouched for. DOWNLOAD-EXTENSION catches every
failure and logs it, which would turn a refused module into "module not
found"; a mismatch and a refused network now escape that TRY.

**A host refuses fetching outright by not granting the network.** That was the
open question, and it needs no switch of its own: the read underneath is an
ordinary network read, it raises `no-service`, and that escapes too.

**Two things are deliberately left alone.** A copy already in the modules
directory is loaded as it is: it was checked when it was fetched, and whoever
can write that directory can write anything a script will run. And an address
a script names itself, `import https://...` or a url in a `needs:` header, is
the script choosing what to run, as DO of a url is. The table is the one place
an address is chosen for the script rather than by it.

**All of it is REBOL.** The change is twenty-odd lines in `sys-load.reb`, listed
in ORDER.txt, and one field in `sysobj.reb`; no Java was added.

## 52. VID is the 2010 R3 GUI, carried in the build and loaded by VIEW

**Rebol 3.22 has no VID.** `view-funcs.reb` calls `layout/background` when
VIEW is given a block, and nothing in the mezzanine or the boot files defines
LAYOUT. The VID that did exist for R3 is the 2010 GUI, version 0.2.1, which
`http://www.rebol.com/r3/gui.r` served and Rebol's own LOAD-GUI fetched; its
guide is at https://www.rebol.com/r3/docs/gui/gui.html. The rules are under
"The GUI the build carries" in `spec/screen.allium`.

**It is REBOL from end to end, so it ships as REBOL.** Its styles describe
themselves in DRAW, its faces are gobs, and its events come off an event
port: all of that already worked, which is how the examples ran it through a
loader for weeks. So the file is carried as `bundled://r3-gui.reb` and no
Java was added for it. The jar grows by the file and nothing else.

**Loaded the first time VIEW is given a block, not at boot.** Seventy-four
kilobytes of REBOL at every `Interpreter.create()` would be paid by every
script and every test, and most never open a window. LOAD-GUI, the name the
guide gives the step, is back in `view-funcs.reb`: it loads the file once,
into the user context as 2010's LOAD-GUI did, and answers the GUI's VIEW.
Rebol's VIEW hands a block to it with /OPTIONS and /NO-WAIT, which is what
the guide meant by "later, this line will not be needed". A gob, an image and
a face object stay on Rebol's VIEW: the GUI's 0.2.1 VIEW accepts a face and
then does nothing with it.

**The GUI's words replace Rebol's for the script, not for the library.**
Once loaded, the script's VIEW, UNVIEW and DO-EVENTS are the GUI's, because
that is what the guide's scripts call. `system/contexts/lib` keeps Rebol's.

**The edits 3.22 needs are made in the file, not by a program that rewrites
it as it loads.** The examples' loader made eight rewrites to the loaded
block every run; each is now an edit listed in ORDER.txt and pinned by
`TheGuiTheBuildCarriesFromTheSourceTest`. Each was checked against what 3.22
does, to keep a rewrite from hiding a JEBOL defect: `./r3-head` refuses a
trailing set-word in an object and a set-word given to SET, its `system/view`
has no event port, and the C's SHOW has a branch for a gob alone. Making the
key edit an edit found one the loader had missed: the text face acted only on
KEY events, so arrow keys reached a field and were dropped, and only the
"nothing raised" assertions of the examples' tests had been watching.

**A layout the GUI cannot read halts, as the GUI always has.** Its FAIL-GUI
prints `** GUI ERROR:` and calls HALT, which TRY does not catch and which
ends a command-line run with status 15, as a real 3.22.5's HALT does. That is
the GUI's behaviour and is kept.

**Treated as Apache 2.** The file and its documentation state no licence.
JEBOL treats it as Apache 2, as Rebol's own source is, and NOTICE says so.

**Reverses part of decision 9.** The target is gobs on any screen rather than
markup, and the Java that read a layout block straight into HTML is deleted.

## 53. A call's frame is an array, and CONTEXT? answers whatever is running

**Every context keeps its slots in an ordered array, not a `LinkedHashMap`.**
`SlotTable` scans up to eight names in order and builds a hash index only
past that, which covers every call frame and most objects. A function works
out its frame's names and their lower-cased forms once, when it is made, and
each call gets an array already holding none in every slot, in the order the
spec writes them. Before, each call registered every argument, refinement and
local into a fresh hash map, lower-casing its name again every time.

**What it bought, measured on 2026-10-23.** `scripts/timing.py speed`, three
runs each, before and after: function calls 308-331ms to 264-278ms (about
13x a real 3.22.5 to about 11x); recursion 223-235ms before and 226-243ms
after, unchanged. A Flight Recorder profile of the calls workload had
building the frame at about 14% of the time and looking words up at about
19%; afterwards the frame is about 10% and lookups 6%. Recursion did not move
because the frame was never its cost: checking each argument's type is 13%
of it, the largest single piece, and is where the next gain is.

It did not measurably change the gate. `./gradlew allTests` read 5m22s the
morning before and 4m46s once after it, but then 5m22s and 5m21s on later
runs, while a warm `Interpreter.create()` stayed at 39-40ms median. The gate
is dominated by booting about 16,300 interpreters and by the corpus and suite
classes, none of which this touches; one run each side cannot show a change
smaller than the run-to-run spread.

Checking each argument's type once, against a bit mask, was built the same
day as the next tier and measured: recursion 226-243ms to 204-213ms, and the
gate 5m21s without it, 5m22s with it. It was dropped as complexity that
bought nothing the gate can see.

**What it did not do: words still find their slot by name.** Giving each
word its slot's position, as the C does, would change binding in every place
that uses a context, for a gain this change had to be measured against
first. It has been: the remaining frame cost is a tenth of a call.

**CONTEXT? of a word bound to a function answers the call that is running
when it is asked, not the word's function.** The C reads the call one below
CONTEXT? on the stack (`Stack_Frame(1)` in `n-data.c`). Measured: EITHER
from inside EITHER, the asking function for a word handed to it, TRY from
inside TRY, and none when nothing at all is running, as at a bare console.
JEBOL had answered the word's own function, or DO once the call had ended,
which agreed with r3 only when CONTEXT? was written directly in the body or
at the top of a script. The frame no longer records whether its call has
ended, because nothing asks. A call written as a path answers the function
it calls, not the refined copy the path makes: inside `do/next` it is the
value `:do` is.

**The suite harness now runs each step inside DO, as Rebol's runner does.**
`run-tests.r3` runs a file as `wrap load file`, and WRAP calls DO, so DO is
running under every test; func-test.r3's wish-2440 asserts
`same? :do context? p` because of it, and says so in a comment. JEBOL's
harness ran each step with nothing running, which the old CONTEXT? hid by
answering DO regardless. `SuiteGrading` runs a setup step as `do [...]` and
an assertion step's first expression through `do/next`, and `SweepRunner`
and `SuiteStops` wrap the file the same way.

**A closure's call copies every series in its body and keeps its frame.**
r3 clones a closure body with TS_CODE, every series but images and vectors,
so strings, files, tags and binaries are fresh per call; JEBOL had copied
only blocks. And a recursive closure call took over the previous call's
frame the way a function's does, so a block bound in an outer call read the
inner one. A closure's frame is now never lent to a later call. Goal 12
first proposed dropping the closure's copy altogether; scripts can see it
(`same? cl cl` is false for `cl: closure [] [[1]]`), so it stays.

**Found on the way: a malformed construct names its first item.** For
`#(k: 1)` r3's malconstruct names `k:`, not `[k: 1]`, because the C reports
the scanner-buffer slot the first item was scanned into and only overwrites
it with the block after seeing a word. And `#(none 1)` is none: a keyword
construct ignores what follows it.

## 54. The wasm compiler runs in the Gradle daemon, not a JVM of its own

**TeaVM's plugin will compile in a forked JVM and talk to it over RMI, and
this build no longer asks it to.** `outOfProcess` is now false, which is the
plugin's own default, and the heap the fork used to get through
`teavm.processMemory` is the daemon's through `org.gradle.jvmargs=-Xmx2g`.
`generateWasmGC` read 59s on a cold daemon after the change; there is no
before figure from this machine, because out of process is what stopped
working.

**What it stopped doing is worth the entry, because the error named none of
it.** `generateWasmGC` failed with

```
org.teavm.tooling.builder.BuildException: java.rmi.ConnectIOException:
  error during JRMP connection establishment; nested exception is:
  java.io.EOFException
```

The fork starts, prints its port, and the registry lookup over loopback
succeeds. The stub that lookup returns carries the endpoint RMI gives an
exported object, and that is `InetAddress.getLocalHost()` -- here
`192.168.86.90`, the machine's address on its LAN, not loopback. macOS's
application firewall holds one entry per `java` binary, this one said *block
incoming connections*, and a blocked connection is accepted and then dropped
rather than refused, which RMI reports as end of file. Reproduced outside
Gradle and outside TeaVM in twenty lines: bind a `UnicastRemoteObject` in one
JVM, look it up from another, and the first call over the stub fails the same
way.

It had worked because the firewall's entry is per path and the daemon had
been running on a Temurin 21 that was never in the list. The fork is the
daemon's own JVM, which is whatever `java` launched Gradle and not the
toolchain's 25; that `java` became a Homebrew 26, a binary the firewall had
never been asked about. So pinning the toolchain back to 25 does not prevent
this, and nothing about the build does: the next machine would meet it on
whatever JDK it happens to run Gradle with. Compiling in the daemon is what
prevents it.

**`java.rmi.server.hostname` would have fixed it and cannot be set.**
`BuildDaemon.start` assembles the fork's whole command itself -- the
classpath, `-Dteavm.daemon.incremental`, `-Xmx`, a heap dump flag -- and
takes no extra JVM arguments, so a build cannot pin the fork's RMI endpoint
to `127.0.0.1`. The alternative was `sudo socketfilterfw --unblockapp` on
every JDK the project is ever built with, on every machine. Compiling in the
daemon opens no socket at all.

## 55. The toolchain is 25 because ArchUnit reads a 26's classes as nothing

**ArchUnit 1.4.1 carries an ASM that stops at class file 69.** Java 25 writes
69 and Java 26 writes 70, so a test tree compiled by a 26 is a tree
`ClassFileImporter` imports as empty, and every architecture rule over it
passes having read no class. The toolchain is pinned to 25 for the Foreign
Function and Memory API as well, but this is the reason that bites a reader
who sees a newer LTS and raises the number. Decision 54 says how close the
build already came: the JDK that launches Gradle had become a Homebrew 26.

**Three of `EveryPropertyIsActuallyRunTest`'s assertions exist for this and
nothing else.** They check that the importer found classes at all, that some
of them declare a jqwik check, and that some of them are Jupiter containers --
each saying "with none of these the rule below holds vacuously". They are the
guard for the whole architecture tree rather than for their own rule, because
nothing else in the suite can tell an empty import from a clean one: both are
a green build.

**So a rule whose subject can go missing gets an assertion that it is there.**
A rule asserting a set is empty holds on a set that was never filled, and
being wrong that way is silent. A path handed in by a system property, an
annotation that has to be on the classpath to be seen, a suite's classes --
assert each is found before asserting anything about it.

## 56. Every action goes to a port's actor, through one dispatch

**A port's actor is reached from one place, and no list of verbs decides which
ones get there.** `Evaluator.runNative` asks `ActionSentToPort.answerFor`
before it calls any native's behaviour: if the first argument is a port and
the native is an action that reaches an actor, the actor answers and the
behaviour never runs. That is `T_Port` in the C, which sends every action to
`Do_Port_Action` and names only three exceptions.

**The exceptions are a question the native answers about itself**,
`NativeValue.reachesPortActor`, false by default and true for every
`ActionValue` but four. MAKE and TO build a port rather than act on one,
REFLECT reads the object a port is rather than the thing it stands for, and a
datatype test looks at a value instead of sending it anything -- which is the
C's `Do_Action` short-circuiting on action number zero, since `integer?` and
its forty-nine siblings are `REB_ACTION` values too. The two wrappers that a
call puts round a native, `AskedNative` and `RespecifiedNative`, forward the
question; the first of those is why the hand-wired dispatch is worth
replacing rather than extending, because a call with a refinement arrives
wrapped and a dispatch keyed on the class of the callee sees the wrapper.

**Fifteen verbs were wired by hand and the other forty-two were not.** The
cost was not the missing fifteen in the abstract: `prot-mysql.reb` declares
an INSERT that could never be called, `insert port x` answered `cannot-use`
-- a complaint about the datatype from a port that had the function all along
-- `find port x` answered none, and a verb the actor did not declare answered
`cannot-use` where R3 answers `no-port-action`.

**The arguments are the ones the caller gave, laid out again against the
actor's own parameters.** Three paths rebuilt the list instead of passing it
through -- COPY handed over the port alone, OPEN the port alone, WRITE the
port and the data -- so `copy/part port 10` reached the actor with PART true
and LENGTH none, and limited the copy to nothing without saying so. Laying
out is by name rather than by position: a refinement of the actor's takes its
logic from the refinement of that name at the call site, and an argument
under it takes the value the caller put under the same refinement, so an
actor declaring `/seek` before `/part` gets `read/seek/part port 5 10` right.
`Redo_Func` is where the C does this, and it searches the frame it is handed
for each refinement by name for the same reason.

**A port whose spec is not an object is now refused for every action**, not
for the five that were wired, because `Validate_Port_Value` is the first line
of `Do_Port_Action` and runs before the actor is read. So `length? p` and
`head p` raise `invalid-port` where `read p` always did.

**An address that names a port reaches the actor too**, through the same
dispatch with the port put in the first argument's place, which is `As_Port`.
Which verbs can arrive that way is settled by what each one's first argument
accepts and not by the dispatch: CREATE, DELETE, OPEN, READ, WRITE, QUERY and
RENAME declare a file, url or block beside the port, and CLOSE, OPEN?, UPDATE
and FLUSH take a port and nothing else. QUERY was the one missing, and
answered the names of the scheme's INFO fields while the protocol's own
QUERY, which `prot-http.reb` declares, was never called.

**A built-in actor declares what it serves, and that is the same answer.**
`SchemeActorNative` is now a `ServesPortActions` that answers nothing and
refuses what its scheme does not serve. Each list is the `case A_` list of
that scheme's actor in the C, which was checked against `./r3-head` verb by
verb rather than only read: a file port serves twenty-six verbs, a directory
port ten, a transport ten, an event queue nine, a checksum six, a cipher
eight, DNS six, the console nine, the clipboard six. Every scheme JEBOL
serves itself now answers what a real 3.22.5 answers for all thirty-seven
verbs, where a file port differed on eleven of them.

**Two things the verb list alone got wrong, and both come from the C's
order.** A closed socket answers `not-open` to every verb but OPEN, CLOSE,
OPEN? and UPDATE -- served or not -- because `Transport_Actor` tests the
socket before it reaches the verb, so a verb list on its own would have made
`insert closedPort 1` say `no-port-action` where R3 says `not-open`. And
`A_FIND` in `p-event.c` is a labelled case that falls straight through to
`default`, so an event queue does *not* serve FIND, however much the comment
beside it (`// add it`) suggests otherwise. Reading the switch is not enough;
the fall-through has to be read with it.

**COPY of a file port answers the file's bytes**, from where the port stands,
honouring `/part`, and leaves the port past what it answered -- which is
`read` with a limit, and is what `p-file.c`'s `A_COPY` does. It answered the
port object before. HEAD? and PAST? read the position too, where they refused
a port outright.

**What this rules out** is adding a verb to a list to make it reach an actor.
A new action reaches one by being an action; one that must not says so on
itself, beside the three that already do. A built-in scheme still keeps a
list, because the C's actor is a `switch` and that list *is* the actor's
declaration -- the equivalent of the functions an actor written in REBOL
holds, not a gate in front of the dispatch.
