# JEBOL

REBOL 3 (R3-Alpha) on the JVM.

## Where this is up to

REBOL runs on the JVM. Functions, loops, series, errors, and a REPL.

The build targets Java 25, so the launcher needs a Java 25 runtime. Gradle
downloads one; point `JAVA_HOME` at it if your system Java is older.

```
$ ./gradlew installDist
$ JAVA_HOME=$(ls -d ~/.gradle/jdks/*25*/*/Contents/Home | head -1) \
    ./build/install/jebol/bin/jebol
JEBOL -- REBOL 3 on the JVM. Type quit to leave.
>> 2 + 3 * 4
== 20
>> if 0 ["zero is a value, so zero is true"]
== "zero is a value, so zero is true"
>> any [none none 100]
== 100
>> name: "world"  append name "!"
== "world!"
>> divide 1 0
** math error: division by zero
```

906 tests pass. All 207 corpus entries give their published result, all
fourteen real REBOL programs load, and all fourteen survive a round trip
through MOLD.

**Milestone 1 is finished.** Functions, loops, series, branching, non-local
exit and the failure branches, all driven by published REBOL. 87 natives and
12 operators. Every one of the 359 test obligations derived from the specs is
attributed to a test.

```
>> fizzbuzz: func [n] [
       case [
           zero? remainder n 15 ["FizzBuzz"]
           zero? remainder n 3  ["Fizz"]
           zero? remainder n 5  ["Buzz"]
           true                 [n]
       ]
   ]
>> repeat i 15 [prin fizzbuzz i prin " "]
1 2 Fizz 4 Buzz Fizz 7 8 Fizz Buzz 11 Fizz 13 14 FizzBuzz
```

**Milestone 2 is finished** apart from modules: `make object!`, `context`,
`in`, `bind`, methods that see their own fields, and copies that are
genuinely independent.

**Milestone 3 is finished**: insert, change, clear, copy/part, sort, the
set operations, string handling and conversion.

**Milestone 4 is finished apart from profiling.** A host creates an
interpreter with bounds, hands it a script and gets a `ScriptOutcome`:

```java
Interpreter interpreter = Interpreter.withBounds(
        Bounds.standard().withWallClockLimit(Duration.ofMillis(200)));

ScriptOutcome outcome = interpreter.run("while [true] [1]");
// outcome.conclusion() == Conclusion.TIMED_OUT, and the interpreter still works
```

Nothing a script does escapes as a host exception, including running out of
time. Bounds are enforced rather than advertised, and cancellation is
cooperative so a stopped script never leaves a series half-changed.

**Milestone 5 is finished.** Values cross both ways with the obvious
mappings and nothing more, host throwables become catchable `error!`s, and
what a script may reach is a `HostAccess` policy defaulting to nothing:

```java
Interpreter interpreter = Interpreter.withBounds(
        Bounds.standard().withHostAccess(HostAccess.READING_AND_CALLING));
interpreter.define("vatRate", 0.2);
interpreter.defineFunction("lookupPrice", 1, args -> priceOf(args.get(0)));

Object total = interpreter.run("multiply lookupPrice \"widget\" add 1 vatRate")
        .asHostValue();
```

**Milestone 6 is finished for VID.** A layout renders to HTML, VID-shaped
rather than pixel-faithful, as a pure function from values to markup with
everything a script supplied escaped.

**Milestone 7 is finished.** A view lives on the server, an event names
which face was touched, the block runs, and the page is rendered again
rather than patched. Nothing but markup crosses to the browser.

**Milestone 8 is finished.** PARSE, over blocks and over strings: `to`,
`thru`, `any`, `some`, `opt`, `into`, `set`, `copy`, alternatives, matching
by datatype, and rules held in words so a grammar can be built from named
parts.

```
>> digit: [integer!]
>> parse [when 10:30] ['when set found time!]
== true
>> found
== 10:30
>> parse "707-467-8000" "-"
== ["707" "467" "8000"]
```

**Milestone 9 is finished for files.** A script reads and writes through a
port the host supplied, and one given no port reaches nothing. A port is
rooted at a directory it cannot climb out of, by `..` or by naming an
absolute path. Network and the wider scheme model are not built.

- `TODO.md` — the work as user stories, with acceptance criteria
- `docs/decisions.md` — what has been decided, why, and what it rules out

Milestone 1 is the language running end to end: read source into values,
evaluate a block, paths, infix operators, the natives and a REPL. All five
specs are written and check clean.

## Layout

```
spec/            Allium specifications - the primary artefact
  values.allium  what a REBOL value is: datatypes, series storage, binding
  load.allium    TRANSCODE and LOAD: source text to a block of values
  eval.allium    DO: walking a block, paths, infix, raising errors
  natives.allium the built-in function set
  repl.allium    the console, and the two conveniences that live only there
src/main/java/org/jebol/
  domain/value   the value model: datatypes, series storage, contexts, MOLD
  domain/read    the reader
  domain/eval    the evaluator, the natives, the output port
  application    Interpreter: one instance, one thread, owns its values
  adapter/cli    the REPL and the stream it writes to
corpus/          real REBOL examples with their published results
  sources/       fourteen complete programs, fetched byte for byte
docs/            decisions, milestones, obligations, checker notes
scripts/         the spec gate
```

The dependency rule points inward and is enforced by `DependencyRuleTest`
rather than by convention: the domain knows nothing of the application or
the adapters, and nothing in the domain touches `java.io` or `java.net`.

## Building and checking

Java 25, Gradle, no runtime dependencies.

```
./gradlew check
```

`check` depends on `checkSpec`, so the specifications are validated by the
same gate as the code rather than by a step somebody has to remember.
Run the spec gate alone with `./scripts/check-spec.sh`.

The gate fails on any error, any analysis finding, and any warning not
listed in `spec/.allium-warning-allowlist`. Seven warnings are allowlisted;
all seven are one checker gap, written up with its repro in
`docs/allium-checker-notes.md`. Nothing else is waved through.

## What was decided, and what wasn't

Targeting R3-Alpha rather than REBOL 2 or Red: Unicode strings natively,
which suits the JVM, and it's the version with the most reference material.

The specs deliberately stop short of the character-level grammar. What
reading guarantees, and the handful of resolutions a caller can actually
be surprised by (`1.2` is a decimal but `1.2.3` is a tuple, `-1` is one
value but `- 1` is two), are stated in `load.allium`. The exhaustive
form-by-form mapping belongs in a golden corpus that the tests read,
where it is more precise than prose could be.

Each spec ends with its open questions. Twenty remain across the five
files, and they are real unknowns rather than placeholders: whether
`$1.50` equals `$1.5`, what may cross between interpreter instances, what
a soft-literal parameter does with a paren. They want answering before the
code that depends on them is written, not after.

## The corpus, and what it says about scope

`corpus/` holds published REBOL examples with their expected results, in a
tagged plain-text format described in `corpus/README.md`. 88 entries, plus
fourteen complete programs in `corpus/sources/`.

All 60 entries asserting a result or an error now pass, including the one
that documents a deliberate divergence: the Core guide prints `10 / 3` to
fifteen significant digits because that is REBOL 2, and JEBOL prints the
shortest form that reads back, because that is R3-Alpha.

The 26 loader entries are lines lifted from the fourteen programs and
cited by file and line, which is how `window/pane/:n/color: clr` came to
be tested at all. Real code uses the awkward combinations; a hand-written
example tends not to.
