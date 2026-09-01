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

## What JEBOL is

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
is Rebol's rather than an approximation of it: 279 of 279 C functions match,
none of R3's 404 functions is missing, and `system/catalog/datatypes` has all
fifty-eight of Rebol's (plus `java-object!`).

## How it is checked

**Rebol's own test suite is the measure.** All sixty-seven files from
`src/tests/units/` are vendored and run — 10,100 assertions, every one of them
reached. What still fails is named line by line in
`src/test/resources/rebol-suite/known-gaps.txt`, and that list only ever
shrinks: the build fails if a listed assertion starts passing, so nothing comes
off it quietly and nothing goes on it without being seen.

Beside it, and outliving it:

- **A corpus of 1,140 entries** — published REBOL examples with their published
  results, plus fourteen complete real programs that must load and survive a
  round trip through MOLD.
- **Standalone tests** for every behaviour fixed because of a suite assertion,
  which build an interpreter and read no `.r3` file. The suite is scaffolding
  and will be deleted when it goes green; these are what lasts.
- **A real `r3` binary**, used as an oracle. Where the suite and the C
  disagree, the C wins; where reasoning and the binary disagree, the binary
  wins.

## Where it has got to, and what is left

**9,084 of the 10,100 assertions in Rebol's own test suite pass.** The
remaining 1,016 are named line by line in `known-gaps.txt`, and they group into
six kinds and a tail:

```
 301  format decoders and encoders: PNG, JPEG, GIF, BMP, WAV, PDF, SWF
 286  the rest, thin-spread: csv, func, module, time, map, date, make, error
 116  ports and schemes
 109  cryptography: the crypt port, Diffie-Hellman, ChaCha20, Poly1305
  80  compression formats this build has not got: Brotli, LZMA, LZW, CRUSH
  77  checksums and encodings
  47  handles
```

Bigger things that are known rather than counted:

- **DRAW renders 22 of R3's 36 commands.** `image` and `text` are the two whose
  absence makes a page look wrong rather than plain.
- **TLS loads but does not connect.**
- **69 of Rebol's 142 error ids can be raised**, so a script that catches by id
  can still meet one JEBOL has no way to produce.
- **Ten of R3's scheme names are not registered**, `file` and `dir` among them.
  JEBOL reaches files through a host grant instead, which may be the design or
  may be a gap in the CLI; that is worth settling before the schemes are
  written.
- **`task!` is a datatype word and not yet a datatype.**

`TODO.md` carries all of it with the numbers, and every number in it was
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

Bounds are enforced rather than advertised, and cancellation is cooperative so
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
docs/            decisions, the porting guide, findings about Rebol itself
```

The dependency rule points inward and is enforced by `DependencyRuleTest`
rather than by convention: the domain knows nothing of the application or the
adapters, and nothing in the domain touches `java.io`, `java.nio.file` or
`java.net`.

## Building

Java 25, Gradle, no runtime dependencies. The shipped jar is about 1,024 KB, of
which 860 KB is the borrowed library.

```
./gradlew check          # ~16,000 tests, about five minutes
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

- `TODO.md` — what is left, with the numbers, each one checked by running it
- `docs/decisions.md` — what has been decided, why, and what it rules out
- `docs/porting-guide.md` — how to port a function, and what the authorities are
- `docs/rebol-findings.md` — what reading Rebol's source turned up about Rebol
- `using-jebol.md` — the manual
