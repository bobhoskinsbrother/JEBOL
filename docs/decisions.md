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
  actually implements -- into `src/test/resources/r3/c-surface.txt`.
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
reaches 18. A good deal of REBOL is also written in REBOL, in `src/mezz/`,
which reads directly as a specification of what those functions do.

The fork also ships `src/tests/units/`: 77 files, 3,290 named tests and
11,899 assertions, written by the people who maintain the implementation.
That is a far better source of cases than boundaries we invent. Sixty-seven of
those files are now imported under `src/test/resources/rebol-suite/` and run on
every build as `RebolSuiteTest`: 10,133 assertions, none of them skipped.

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

## 16. The load order is Rebol's, and it is written down in Rebol's own build

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
written with.

JEBOL rebound the whole body through the frame chain at every call, and that is
wrong in a way that only shows up through one door. FUNC is itself a REBOL
function, so `make function!` runs inside FUNC's frame. A body rebound through
that chain resolves its free words in the library rather than where they were
written.

Rebol's own COLLECT is exactly that shape. It creates its KEEP function inside
itself, and KEEP writes to COLLECT's own OUTPUT:

    unless output [output: make block! 16]
    do func [keep] body func [value [any-type!] /only] [
        output: insert/:only output :value
        :value
    ]

With the body rebound, KEEP's OUTPUT was a different slot from COLLECT's, and
it held none. So did SPLIT, which is written on COLLECT, and so did every
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
refinements in the order they were written with their arguments after them. That
is what SPEC-OF and WORDS-OF answer out of - not something rebuilt from the
native registry, which knows the types and not the order, not the documentation,
and not which refinements take no argument at all.

`actions.reb` declares the sixty actions and `natives.reb` the hand-written
natives. The rest are written as comments beside the C that implements them, and
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

The application supplies it, because only the application may ask the machine.
The domain's default is `JVM`, which is the C's own name for a build that knows
nothing about where it is.

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

### NEAR and WHERE are filled in on the way out, once

Both fields exist because a script reads them, and they cannot be filled in where the
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

A function with no direct equivalent in Java is written in Java. It does not
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

## 21. What ImageIO gets wrong, and what is written by hand because of it

The JVM's own image codec arrives with the runtime and reads and writes PNG,
JPEG, GIF and BMP everywhere Java runs, so the "only on Windows and macOS so
far" the C says of its own shim is not a limit this platform has. Four of its
behaviours are wrong for this port, and each cost a real defect before it was
understood. They are recorded here because `JavaImages` now reads as ordinary
code and nothing in it says why.

**The BMP writer refuses every thirty-two-bit picture it is offered**, under
every compression type: "Image can not be encoded with compression type BI_RGB
and 32 bits per pixel". Going through it means writing twenty-four bits and
losing the transparency, or not writing a BMP at all. A real 3.22.5 loses
nothing, so the file is written out directly instead. The fifth version of the
header is what makes the alpha possible -- `BITMAPV5HEADER` carries a mask per
channel, so the file says which bits are which rather than leaving a reader to
assume. The runtime reads that back perfectly; only writing it is missing.

**Left to itself the GIF writer sets the interlace flag**, and its own reader
then hands back a picture with the rows in the wrong order. A two-by-two of four
colours came back as three, and JEBOL could not read a GIF it had just written
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
natives written in Java because they reach something the language cannot, and
`prelude.reb`, written in REBOL because it can be. Loading it in the
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

**MD4 and RIPEMD-160 are written out, because the catalogue lists them and
`java.security` has not got them.** The shipped jar takes no dependencies, so the
alternative to writing them is not offering them, and a name in
`system/catalog/checksums` that cannot do anything is the one thing a catalogue
must not be. MD4 is thoroughly broken as a cryptographic hash and has been since
the nineties; it is here because file formats and protocols written when it was
new still carry MD4 sums, and reading one of those means computing one.

**A `string!` is stored as codepoints, not as the host language's string type.**
REBOL strings are mutable series indexed by character with constant-time access.
Java's `String` is immutable and indexed by UTF-16 code unit, so `"a😀b".charAt(1)`
is half an emoji. Neither property is negotiable for a `string!`.

**SHAPE is read directly rather than through DELECT**, because its arguments are
all pairs and numbers in written order - ten commands, each with a relative form
written as a lit-word. Specified in `spec/draw.allium`.

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
