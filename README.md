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

**It is the real case against Truffle.** Not runtime distribution — Truffle
languages have been ordinary Maven artifacts on a standard JDK since 23.1.
The objection that survives is operational: a polyglot context is a
heavyweight thing to hold per request, and what a profiler shows you is the
framework's frames rather than yours.

Oldes' branch rather than REBOL 2, R3-Alpha as it is the version with the most
surviving reference material (and it is alive) so there is a running binary to
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
`sys-load`. None of it is rewritten in Java and none of it is copied into a
prelude.

That means a function is ported by making the C it depends on work, not by
reimplementing the function. When `join` misbehaves the fault is underneath it,
and the borrowed file is a fixed point that says so. It also means the surface
is Rebol's rather than an approximation of it: every C function R3 exposes has
a match, none of its 404 functions is missing, and `system/catalog/datatypes`
has all fifty-eight of Rebol's (plus `java-object!`). `scripts/c-parity.py`
measures that on demand and `TODO.md` records what it last said.

## How it is checked

**Rebol's own test suite is the measure.** All sixty-seven files from
`src/tests/units/` are vendored and every assertion they write is reached and
run. What still fails is named line by line in
`src/test/resources/rebol-suite/known-gaps.txt`, and that list only ever
shrinks: the build fails if a listed assertion starts passing, so nothing comes
off it quietly and nothing goes on it without being seen.

**The counts live in `TODO.md` and nowhere else**, so there is one place to
correct when they move rather than four that drift apart.

Beside it, and outliving it:

- **A corpus of 1,140 entries** — published REBOL examples with their published
  results, plus fourteen complete real programs that must load and survive a
  round trip through MOLD.
- **Standalone tests** for every behaviour fixed because of a suite assertion,
  which build an interpreter and read no `.r3` file. The suite is scaffolding
  and will be deleted when it goes green; these are what lasts.
- **`./r3-head`, a real Rebol 3.22.5 built from the checkout beside it**, used
  as an oracle. Where the suite and the C disagree, the C wins; where reasoning
  and the binary disagree, the binary wins. It is the only Rebol consulted: an
  older 3.22.1 download sat beside it for a while and cost four wrong readings
  before it was deleted, so a binary that is not built from the source it is
  being read against is worse than no binary at all.

## Where it has got to, and what is left

**Most of Rebol's own suite passes, and every assertion that does not is
accounted for by name** — either in `known-gaps.txt`, which is what still fails,
or in `fails-on-rebol-too.txt`, which is assertions the Rebol this is measured
against does not run either. Those are usually one arm of an `either error? try
[...]` whose other arm is the one taken, and each line carries the `r3-head`
session that settled it. For the figures, see `TODO.md`.

The useful split is not by feature but by whether they ran at all. A suite file
is a script, so an assertion that raises takes the rest of its block with it,
and a good share of the backlog is assertions standing behind an earlier
failure rather than failures in their own right. Fixing one thing routinely
moves dozens, and the count moves in steps rather than one at a time — the file
and directory schemes took thirty-six with them in a single commit.

Roughly, what is left is the codecs, `image!` as a series, the compression
algorithms with no JDK equivalent, four of the port schemes, ENBASE and DEBASE,
the elliptic curves, and a long tail across unicode, time, map, make, module
and parse.

**`goals.md` breaks all of it into pieces of work**, each with its size, what
blocks it, which C file to read and how to check the answer against a real
Rebol. **The sizes live there and are not repeated here.** It also carries the
working method: one authority and one oracle, the measuring tools, the ratchet,
and a rule for the assertions a real Rebol does not run either.

Six of those goals are not porting at all. They correct faults in the measure,
found by an audit that ran three independent adversarial passes over it — a
handful of listed assertions that a real Rebol also fails, so that fixing them
would move JEBOL *away* from Rebol; an allowlist with no ratchet behind it; a
measuring tool that reports blockers the gate never sees; and a file the oracle
itself answers differently on consecutive runs. Those come first, because until
they are done a falling backlog does not reliably mean progress.

Bigger things that are known rather than counted:

- **The image codecs are mostly the JDK's already.** PNG, JPEG, GIF, BMP and
  TIFF all round-trip through `javax.imageio`, which is in `java.desktop` and
  so costs nothing to reach, and the suite asserts a round trip and the pixels
  rather than the encoded bytes. Two catches: `ImageIO.write` refuses an
  image with an alpha channel for JPEG and BMP, so those two have to drop it
  as Rebol's own codecs do; and a codec would live in an adapter behind a
  port, because the domain may not touch the JDK's I/O.
- **PDF belongs in an optional extension**, not in the jar. **SWF is not worth
  writing at all**, and both of those decisions are worth more than the
  assertions they cost.
- **CRUSH and LZW are written out here**, by hand from `u-crush.c` and
  `u-lzw.c`, because they are Rebol's own and `java.util.zip` gives Deflate,
  GZIP and ZIP and stops. Brotli and LZMA are still the choice between writing
  them and marking them not-in-this-build, which is a branch Rebol's own suite
  is written to accept.
- **DRAW does not render every command R3 does.** `image` and `text` are the
  two whose absence makes a page look wrong rather than plain.
- **TLS loads but does not connect.**
- **Not every one of Rebol's error ids can be raised**, so a script that
  catches by id can still meet one JEBOL has no way to produce.
- **Seven of R3's scheme names are not registered.** `file`, `dir` and
  `checksum` are served now; `crypt` is the next that matters.
- **`task!` is a datatype word and not yet a datatype.**

`TODO.md` carries the rest with the numbers, and every number in it was
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
                 c-parity, error-parity, and sweep.py for diffing one suite
                 file against a real Rebol assertion by assertion
docs/            decisions, the porting guide, findings about Rebol itself
goals.md         the remaining suite failures, broken into pieces of work
```

The dependency rule points inward and is enforced by `DependencyRuleTest`
rather than by convention: the domain knows nothing of the application or the
adapters, and nothing in the domain touches `java.io`, `java.nio.file` or
`java.net`.

## Building

Java 25, Gradle, no runtime dependencies. The shipped jar is about 1,024 KB, of
which 860 KB is the borrowed library.

```
./gradlew check          # the whole suite, about four minutes
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

- `goals.md` — the remaining suite failures as fifteen pieces of work, and
  the method for doing any of them: start here to pick something up
- `TODO.md` — what is left beyond the suite, with the numbers, each one
  checked by running it
- `docs/decisions.md` — what has been decided, why, and what it rules out
- `docs/porting-guide.md` — how to port a function, and what the authorities are
- `docs/rebol-findings.md` — what reading Rebol's source turned up about Rebol
- `using-jebol.md` — the manual
