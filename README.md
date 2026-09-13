# JEBOL

REBOL 3 on the JVM.

## What is REBOL?

A small language from 1997 by Carl Sassenrath, built on one idea: **a program
is data, and the language is what reads it.** A source file is a block of
values. Evaluating it is only one of the things you can do with it.

Two things follow, and between them they are most of the language.

**The datatypes are in the notation.** A date, a time, money, an email
address, a file, a URL, a tag, a pair of coordinates and a run of bytes are
each written directly, and each behaves as itself:

```
>> 12-Jan-2026 + 30
== 11-Feb-2026
>> $19.99 * 3
== $59.97
```

No parsing step and no date library, because the reader already knew what it
was reading. Fifty-eight datatypes, most of them written like this.

**A block doesn't mean anything until meaning is given.** `[deposit 100
withdraw 30]` is four values and nothing else until a function decides what
they say. Such a function is a *dialect*, and REBOL's control structures, its
GUI layouts and its pattern matcher are all dialects rather than syntax:

```
>> plan: [deposit 100 withdraw 30 deposit 5]
>> total: 0
>> parse plan [some ['deposit set n integer! (total: total + n)
                   | 'withdraw set n integer! (total: total - n)]]
== true
>> total
== 75
```

What another language wants a parser generator and a syntax tree for, REBOL
does with a block and a function.

## What is JEBOL?

A port of [Oldes' Rebol3](https://github.com/Oldes/Rebol3), version 3.22.5, to
Java: an ordinary jar with no dependencies, running on any JDK.

```
$ ./gradlew installDist
$ JAVA_HOME=$(ls -d ~/.gradle/jdks/*25*/*/Contents/Home | head -1) \
    ./build/install/jebol/bin/jebol
JEBOL -- REBOL 3 on the JVM. Type quit to leave.
>> 2 + 3 * 4
== 20
>> any [none none 100]
== 100
>> split "707-467-8000" "-"
== ["707" "467" "8000"]
>> checksum "hello" 'sha256
== #{2CF24DBA5FB0A30E26E83B2AC5B9E29E1B161E5C1FA7425E73043362938B9824}
>> divide 1 0
** math error: division by zero
```

## Why Port to JVM?

To run REBOL in an ordinary web production environment, and to get the
operational benefits of the JVM while doing it: a jar on any JDK, deployed
down the pipeline that already exists, watched with the tools the operations
team already has, in the containers everything else already runs in.

It is an incredibly easy language to design dialects with.  The idea is to have a lightweight 
translation from allium spec to a dialect.

**Interoperability is the point.** REBOL with Java postgres jar files and not having to 
re-implement everything every time: make good use of the massive JVM ecosystem.

Oldes' branch rather than REBOL 2, R3-Alpha as it is the version with the most
surviving reference material (and it is currently still active) so there is a running binary to
check answers against.

## What is Ported, and What is Copied?

The two are kept apart on purpose, and the split is the whole design.

**The C is ported.** Everything in `src/core/*.c` — the evaluator, the reader,
the series operations, the natives, PARSE, the binary dialect, the checksums —
is rewritten in Java against the C as the authority.

**The REBOL is copied.** Everything in `src/mezz/*.reb` is loaded and run as
it stands, byte for byte, from a vendored copy under
`src/main/resources/org/jebol/mezz/`. Eighty-two files, about 860 KB of
Rebol's own library: `join`, `collect`, `split`, the codecs, the port schemes,
`sys-load`. 

That means a function is ported by making the C it depends on work, not by
reimplementing the function. When `join` misbehaves the fault is underneath it,
and the borrowed file is a fixed point that says so. It also means the surface
is Rebol's rather than an approximation of it: every C function R3 exposes has
a match, none of its 404 functions is missing, and `system/catalog/datatypes`
has all fifty-eight of Rebol's (plus `java-object!`).

**Declaration checking.**
`scripts/c-parity.py` compares two files, so it cannot fail on anything a
declaration does not say. `scripts/runtime-parity.py` asks two *running*
interpreters instead — what datatype each function reports itself as, what
`words-of` gives back, and its specification text for text — and when it was
first written most of Rebol's library answered differently here. `goals.md`
records what both last said, and they should be read together or not at all.

That second script compared the *length* of a specification until September
2026, and a measure that counts instead of comparing will agree with anything:
a built-in derived with a widened type list and a rewritten docstring read as
identical, and `empty?` advertised the wrong types for as long as it did.
Both scripts exist because the one before them was too easily satisfied, and
neither is finished being wrong.

**The declarations are copied too, for the same reason the library is.**
`actions.reb`, `natives.reb` and the specs Rebol's build collects out of
comments in the C say what every built-in takes, in what order, with what
documentation. SPEC-OF and WORDS-OF read them rather than rebuilding an answer
from JEBOL's own registry, which knows the types and not the order and none of
the prose. `errors.reb` is there on the same footing.

## How it is checked

**Rebol's own test suite is the measure.** All sixty-seven files from
`src/tests/units/` are vendored and every assertion they write is reached and
run. What still fails is named line by line in
`src/test/resources/rebol-suite/known-gaps.txt`, and that list only ever
shrinks: the build fails if a listed assertion starts passing, so nothing comes
off it quietly and nothing goes on it without being seen.

**The counts live in `goals.md` and nowhere else**, so there is one place to
correct when they move rather than four that drift apart.

Beside it, and outliving it:

- **A corpus** — published REBOL examples with their published results, plus
  fourteen complete real programs that must load and survive a round trip
  through MOLD. The count is in `goals.md` with the rest of the measures.
- **Standalone tests** for every behaviour fixed because of a suite assertion,
  which build an interpreter and read no `.r3` file. The suite is scaffolding
  and will be deleted when it goes green; these are what lasts.
- **`./r3-head`, a real Rebol 3.22.5 compiled from the checkout beside it**,
  used as the canonical reference. Where the suite and the C disagree, the C
  wins; where reasoning and the binary disagree, the binary wins. The C and the
  binary are one authority rather than two, because the binary is that C
  compiled.

## Where it has got to, and what is left

**Rebol's own suite passes, and the handful of assertions that do not are
accounted for by name** — either in `known-gaps.txt`, which is what still fails,
or in `fails-on-rebol-too.txt`, which is assertions the Rebol this is measured
against does not run either. Those are usually one arm of an `either error? try
[...]` whose other arm is the one taken, and each line carries the `r3-head`
session that settled it. For the figures, see `goals.md`.

**`known-gaps.txt` is down to a single line, and no work will retire it.** It
asks to read `system/options/boot`, which is the launcher a script runs to
start a confined child interpreter and therefore has to sit outside whatever
root the script can see. Passing it would mean giving up confinement
propagation to win one assertion.

**So the suite has nothing left to say, and what remains is what it could never
see.** No assertion in it asks whether an error id can be raised, whether a
certificate was checked, or what a function says about itself — and each of
those turned out to hold real defects once something else went looking. The
work now comes from measures the suite does not provide: two running
interpreters asked the same question, the C read for a line nobody has
reached, and the reference instrumented and watched.

**`goals.md` breaks all of it into pieces of work**, each with its size, what
blocks it, which C file to read and how to check the answer against a real
Rebol. **The sizes live there and are not repeated here.** It also carries the
working method: one authority and one canonical reference, the measuring tools, the ratchet,
and a rule for the assertions a real Rebol does not run either.

**The order there has one rule above the rest: agreeing with the C comes before
improving on it.** This is a port. A place where JEBOL answers something a real
3.22.5 does not is a defect; a place where both are weak is a decision, and it
waits. So the suite backlog leads, then the equivalence the suite cannot see,
then two security goals — the TLS client does not authenticate the server, and
a fetched module is not checked — which are divergences from the C rather than
gaps against it, because Rebol does neither either.

Things to note:

- **PDF belongs in an optional extension**, not in the jar. **SWF is not worth
  writing at all**, and both of those decisions are worth more than the
  assertions they cost.
- **DRAW paints every command its dialect table declares**, including the ones
  no toolkit has — Gouraud-shaded triangles, conic and diamond gradients,
  two-colour dashes, keyed and warped images — because those are worked out in
  the domain and handed to both renderers as plain shapes and pixels. There is
  no Rebol to check the pictures against: a stock 3.22.5 has no `draw` at all.
- **`read https://` works, and the TLS client does not authenticate the
  server.** An expired certificate, a self-signed one, one issued for another
  host and one from an untrusted root are all read without complaint — by a
  real 3.22.5 as well, which is why this is a divergence from good practice
  rather than from the C. That is confidentiality against somebody listening
  and nothing against somebody in the middle.
- **Twenty-eight of Rebol's error ids cannot be raised here**, and every one of
  them has a written reason — twenty-four are ids a real 3.22.5 cannot raise
  either.
- **Five of R3's scheme names are not registered**: `callback`, `clipboard`,
  `midi`, `serial` and `udp`. JEBOL registers seven R3 has not, most of them
  protocols out of the borrowed library.
- **A `task!` is made, read, molded and answered by DO, and never runs on a
  thread.** Its body is bound to contexts none of which is safe to touch from
  two threads, so running it on the calling thread would be worse than not
  running it.
- **No failure leaves as a host exception — in MAKE.** Every other path that
  allocates on a script's say-so can still throw an `OutOfMemoryError` out of
  the interpreter, and the guarantee is only as good as its thinnest path.

`goals.md` carries the rest with the numbers, and every number in it was
checked by running it rather than by remembering it.

## Embedding

A host creates an interpreter with bounds, hands it a script, and gets a
`ScriptOutcome`. Nothing a script does escapes as a host exception, including
running out of time.

```java
Interpreter interpreter = Interpreter.withBounds(
        Bounds.standard().withWallClockLimit(Duration.ofMillis(200)));

ScriptOutcome outcome = interpreter.run("while [true] [1]");
// outcome.conclusion() == Conclusion.TIMED_OUT, and the interpreter still works
```

Bounds are enforced, and cancellation is cooperative so
a stopped script never leaves a series half-changed. What a script may reach is
a `HostAccess` policy that defaults to nothing:

```java
Interpreter interpreter = Interpreter.withBounds(
        Bounds.standard().withHostAccess(HostAccess.READING_AND_CALLING));
interpreter.define("vatRate", 0.2);
interpreter.defineFunction("lookupPrice", 1, args -> priceOf(args.get(0)));

Object total = interpreter.run("multiply lookupPrice \"widget\" add 1 vatRate")
        .asHostValue();
```

An interpreter is owned by one thread and holds every value reachable from it.
Series share mutable storage by design, so aliasing is observable, and
confining that to one thread is what makes it need no synchronisation at all. A
host wanting concurrency runs several instances.

## Layout

```
spec/            Allium specifications, checked by the same gate as the code
src/main/java/org/jebol/
  domain/value   the value model: datatypes, series storage, contexts, MOLD
  domain/read    the reader
  domain/eval    the evaluator, the natives, the dialects, the ports
  domain/parse   PARSE, over blocks and over strings
  application    Interpreter: one instance, one thread, owns its values
  adapter/cli    the REPL
src/main/resources/org/jebol/mezz/
                 Rebol's own library, vendored and run unchanged
corpus/          published REBOL examples with their published results
  sources/       fourteen complete programs, fetched byte for byte
src/test/resources/rebol-suite/
                 Rebol's own test files, and the gap list
scripts/         the measures, which are run rather than remembered:
                 c-parity (declarations) and runtime-parity (two running
                 interpreters), error-parity, and sweep.py for diffing one
                 suite file against a real Rebol assertion by assertion
docs/            decisions, the porting guide, findings about Rebol itself
goals.md         everything left to do, ordered by importance, and the method
```

The dependency rule points inward and is enforced by `DependencyRuleTest`
rather than by convention: the domain knows nothing of the application or the
adapters, and nothing in the domain touches `java.io`, `java.nio.file` or
`java.net`.

## Building

Java 25, Gradle, no runtime dependencies. The shipped jar is about 1,696 KB, of
which Rebol's borrowed library, its function and error declarations and the
thirteen modules bundled with the build are most of it — SPEC-OF, the error
catalogue and IMPORT are read out of those.

```
./gradlew check          # the whole suite, about four and a half minutes
./gradlew browserCheck   # the second gate: a real browser, pixel for pixel
```

`check` also runs the spec gate, so the specifications are validated by the
same command as the code. It fails on any error, any analysis finding, and any
warning not on the allowlist.

`browserCheck` is separate rather than skipped. It drives a real Chrome through
WebDriver, renders the same paint list in Java2D and in the browser, and
compares the two pixel for pixel — which is how "a page and a window show the
same picture" is a thing the build knows rather than a thing somebody says. It
is out of `check` because it needs a browser installed and a network the first
time it fetches a driver, and the ordinary gate should need neither.

## Reading further

- `goals.md` — everything left to do, ordered by importance, with the numbers
  and the method for doing any of it: start here to pick something up
- `docs/decisions.md` — what has been decided, why, and what it rules out
- `docs/porting-guide.md` — how to port a function, and what the authorities are
- `docs/rebol-findings.md` — what reading Rebol's source turned up about Rebol
- `using-jebol.md` — the manual
