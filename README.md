# JEBOL

REBOL 3 on the JVM.

## What is REBOL?

A small language from 1997 by Carl Sassenrath, built on one idea: **a program
is data, and the language is what reads it.** A source file is a block of
values. Evaluating it is only one of the things you can do with it.

Two things follow, and between them they are most of the language.

**The datatypes are in the notation.** A date, a time, money, an email
address, a file, a URL, a tag, a pair of coordinates and a run of bytes are
each text directly, and each behaves as itself:

```
>> 12-Jan-2026 + 30
== 11-Feb-2026
>> $19.99 * 3
== $59.97
```

No parsing step and no date library, because the reader already knew what it
was reading. Fifty-eight datatypes, most of them text like this.

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
Java: an ordinary jar with no dependencies, running on any JDK 25.

```commandline
./gradlew jar
java -jar build/libs/jebol-3.22.5.jar
╔══════════════════════════════════════════════════════════════════════════╗
║  REBOL/core 3.22.5 (JEBOL port)                                          ║
...
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

A script file goes on the command line, one expression goes after `--do`, and
standard input pipes into the console.

```commandline
java -jar build/libs/jebol-3.22.5.jar my-script.reb
java -jar build/libs/jebol-3.22.5.jar my-app/
java -jar build/libs/jebol-3.22.5.jar https://example.com/app/main.reb
java -jar build/libs/jebol-3.22.5.jar --cafile company-ca.pem my-script.reb
java -jar build/libs/jebol-3.22.5.jar --do "print 1 + 2"
cat my-script.reb | java -jar build/libs/jebol-3.22.5.jar
```

A script may be given by https url as well as by path; it runs the same way,
header, `Needs:` and exit status included. `--cafile` adds the authorities in a
PEM file to the ones the machine trusts, which the command line already uses:
the keychain on macOS, the Windows root store, or the Linux system bundle,
beside the JVM's own store.

A directory is an app: it runs the `main.reb` inside it, with the app as the
working directory, so `do %lib/whatever.reb` and `read %data.txt` find the
app's own files. That is how a desktop app ships.

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

## JEBOL Web

**The same Java interpreter, compiled to WebAssembly, running in an html page using a
worker.** There is no server-side code: the page fetches the module and your
app's REBOL, the worker boots the interpreter where the screen is, and the page
is the screen.

The GUI has been designed to have one frontend everywhere - whether it's web, desktop app, or mobile app. 
The domain walks the gob tree once and turns it into surface calls — fill a path, stroke a path, draw an image, draw a
piece of text — and each platform implements those calls with its own toolkit.
On the desktop that is Java2D; in a page it is a canvas. Events come back the
other way as ordinary postings, and the worker refuses what a page cannot do —
network, processes, environment — with an error that says the browser does not
allow it rather than pretending otherwise.

What the browser supplies instead: files live in the page's private store and
survive a reload, HTTP and HTTPS go through `fetch`, digests and HMACs come from
WebCrypto, and text is measured with the one font the build carries so a page
and a window lay out the same words in the same places.

**A directory app is:**

- `main.reb` — the entry. Whatever REBOL is in it is what the worker interprets.
- Any other files — every file in the directory is fetched into the worker's
  store before `main.reb` runs, so `do %lib/whatever.reb` and `read %data.txt`
  find them.
- Nothing else. No HTML, no JS. The build writes `index.html` for you from
  `site/runner.html` (canvas, replayer, worker hookup) with your file list baked
  in, and puts the module, the loader, the worker's shell and the font beside it.

```
$ ./gradlew assembleTheSite -PsiteDir=my-app
$ cd build/site && python3 -m http.server 8000
```

If you put your own `index.html` in the directory, that is used instead of the
generated one. The shipped demo takes the same path: `examples/r3-gui` is the
app, and the driver that used to live in the page's tag is now just
`examples/r3-gui/main.reb`.

**The JVM Host Option.** The front end can also be served through a Swing window from the Java interpreter.

```
$ java -jar build/libs/jebol-3.22.5.jar --screen=web
Screen: a page at http://127.0.0.1:54463/ -- open it, then VIEW.
```

Open the address it prints; `VIEW/no-wait [...]` in the console then draws into
the page. `--screen=window` is the default arrangement and puts that picture in
a native window instead.

**The end to end tests** `./gradlew endToEndTests` builds the web module, drives the
assembled site in a real Chrome, and compares what the page draws against what
Java2D draws from the same paint list, pixel for pixel. It is the reason a page
and a window showing the same picture is a thing the build knows. `docs/web-port.md`
is the port's record and decisions 31 to 35 carry the shapes it settled on.

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

**Declaration checking.**
`scripts/c-parity.py` compares two files, so it cannot fail on anything a
declaration does not say. `scripts/runtime-parity.py` asks two *running*
interpreters instead — what datatype each function reports itself as, what
`words-of` gives back, and its specification text for text — and when it was
first text most of Rebol's library answered differently here. `goals.md`
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

**Rebol's own test suite is the measure.** Every file from `src/tests/units/`
that Rebol's own runner runs is vendored (the few deliberately left out are
named in `not-vendored.txt`) and every assertion they write is reached and run.

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
accounted for by name** — `fails-on-rebol-too.txt` - assertions that fail in r3 too. Those are usually one arm of an `either error? try
[...]` whose other arm is the one taken, and each line carries the `r3-head`
session that settled it. For the figures, see `goals.md`.

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
- **DRAW paints every command its dialect table declares except `effect`**,
  including the ones no toolkit has — Gouraud-shaded triangles, conic and
  diamond gradients, two-colour dashes, keyed and warped images — because those
  are worked out in the domain and handed to both renderers as plain shapes and
  pixels. `effect` is a dialect of its own and belongs with the codecs. There is
  no Rebol to check the pictures against: a stock 3.22.5 has no `draw` at all.
- **`read https://` works, and the TLS client does not authenticate the
  server.** An expired certificate, a self-signed one, one issued for another
  host and one from an untrusted root are all read without complaint — by a
  real 3.22.5 as well, which is why this is a divergence from good practice
  rather than from the C. That is confidentiality against somebody listening
  and nothing against somebody in the middle.
- **Twenty-eight of Rebol's error ids cannot be raised here**, and every one of
  them has a text reason — twenty-four are ids a real 3.22.5 cannot raise
  either.
- **`midi` is registered and has no instrument behind it yet** (goal 7).
  `query midi:// 'devices-in` lists nothing and `open midi://1` is refused, as
  in a real 3.22.5 with nothing attached; a host can supply devices through
  `Interpreter.useMidiDevices`, and MIDI needs a grant of its own.
  `callback`, `clipboard`, `serial` and `udp` are registered even
  where the device behind them is not, which is what Rebol does on a build with
  no such device compiled into it: the name opens to a refusal that says what
  is missing. JEBOL registers seven scheme names R3 has not, `bundled` and six
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
        Bounds.standard().withTimeLimit(Duration.ofMillis(200)));

ScriptOutcome outcome = interpreter.run("while [true] [1]");
// outcome.conclusion() == Conclusion.TIMED_OUT, and the interpreter still works
```

Bounds are enforced, and cancellation is cooperative so
a stopped script never leaves a series half-changed. A script calls into the
host only through an extension the bounds carry, the way R3's hosted
extensions work:

```java
Extension shop = new Extension("shop", "1.0.0", List.of(new ExtensionCommand(
        "lookup-price", "{The price of one item.} item [string!]",
        call -> DecimalValue.of(priceOf(((StringValue) call.arguments().getFirst()).text())))));

Interpreter interpreter = Interpreter.withBounds(Bounds.standard().add(shop));
interpreter.define("vatRate", 0.2);

Object total = interpreter.run("multiply lookup-price {widget} add 1 vatRate")
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
  domain/render  the surface: one walk of the gob tree, calls an adapter draws
  domain/host    the ports a host implements: screen, files, network, digests
  application    Interpreter: one instance, one thread, owns its values
  adapter/host   the desktop: a Java2D surface in a native window
  adapter/web    the browser: a canvas surface, events over HTTP or postMessage
  adapter/cli    the console
src/teavm/java/  the web's entry point and its substitutions, for TeaVM only
src/teavm/js/    the worker's shell, and the store and digests it lends the page
src/main/resources/org/jebol/mezz/
                 Rebol's own library, vendored and run unchanged
site/            the runner page every site is built around
examples/r3-gui/ the shipped demo app: the 2010 R3 GUI, with main.reb as its entry
editors/         a TextMate bundle: REBOL highlighting for IntelliJ and VS Code
corpus/          published REBOL examples with their published results
  sources/       fourteen complete programs, fetched byte for byte
src/unitTest/       tests that never build an interpreter
src/functionalTest/ tests that build an interpreter and run REBOL through it
src/endToEndTest/   tests that drive the command line, a real browser and real servers
src/testFixtures/   what the three share: helpers, and every test resource
src/testFixtures/resources/rebol-suite/
                 Rebol's own test files, and what a real Rebol fails too
scripts/         the measures, which are run rather than remembered:
                 c-parity (declarations) and runtime-parity (two running
                 interpreters), error-parity, sweep.py for diffing one suite
                 file against a real Rebol, timing.py for boots and speed
docs/            decisions, the porting guide, findings about Rebol itself
goals.md         everything left to do, ordered by importance, and the method
```

The dependency rule points inward and is enforced by `DependencyRuleTest`
rather than by convention: the domain knows nothing of the application or the
adapters, and nothing in the domain touches `java.io`, `java.nio.file` or
`java.net`.

**Highlighting.** `editors/Rebol.tmbundle` is a TextMate grammar of the
language - the word kinds, blocks, parens and paths, every literal datatype,
strings of all four spellings and comments. In IntelliJ: Settings → Editor →
TextMate Bundles, `+`, and point it at the folder. In VS Code: wrap it in an
extension whose `package.json` declares the grammar. `.r`, `.reb` and `.r3`
files then read as REBOL. A test compiles every pattern in it the way the
editor does, so a bad regex cannot land quietly.

## Building

**What you need: a JDK to run Gradle, and nothing else.** The build asks for a
Java 25 toolchain and downloads one itself (the toolchain resolver in
`settings.gradle.kts`), so the JDK on your PATH does not have to be 25. There are
no runtime dependencies; the jar carries everything it needs.

```
./gradlew allTests         # the spec check and all three suites below
./gradlew unitTests        # tests that never build an interpreter
./gradlew functionalTests  # tests that run REBOL through an interpreter
./gradlew endToEndTests    # the command line, a real browser, local servers
./gradlew networkTests     # Rebol's suite assertions that need the network
./gradlew jar              # just the jar: build/libs/jebol-3.22.5.jar
./gradlew build            # allTests, then the jar
./gradlew assembleTheSite  # the static site in build/site (see JEBOL Web)
./gradlew run              # the console, from source
```

`test` and `check` both run `allTests`, so no task runs less than its name
suggests. The jar `build` writes runs itself:

```
$ java -jar build/libs/jebol-3.22.5.jar --do "print 1 + 2"
```

`allTests` runs the spec check too, so the specifications in `spec/` are
validated by the same command as the code. It fails on any error, any analysis
finding, and any warning not on the allowlist.

`unitTests`, `functionalTests` and `endToEndTests` need nothing from the
network: every host they use is spun up by the tests. `endToEndTests` needs
Chrome installed, and Selenium fetches the driver the first time it runs. The
twelve suite assertions that do need the internet are in `networkTests`, run on
its own and not part of `allTests`. It builds the interpreter for the web with
TeaVM, drives a real Chrome through WebDriver, runs a script that sits in a
page's tag, renders the same paint list in Java2D and in the browser, and
compares the two pixel for pixel — which is how "a page and a window show the
same picture" is a thing the build knows rather than a thing somebody says. It
also puts the command line's TLS to local servers it trusts and local servers it
refuses.

**The measures are scripts, and they are run rather than remembered.**
`./r3-head` is a real Rebol 3.22.5 built from the checkout beside it
(`scripts/build-r3.sh` rebuilds it from the C); `scripts/c-parity.py` and
`scripts/runtime-parity.py` compare the surface against it;
`scripts/error-parity.py` asks whether every error id can be raised;
`scripts/sweep.py` diffs one suite file assertion by assertion; and
`scripts/timing.py` reports how long a boot takes and how the interpreter
compares with the C. What each last said is in `goals.md`, which is also where
the jar's size and the counts live.

## Reading further

- `goals.md` — everything left to do, ordered by importance, with the numbers
  and the method for doing any of it: start here to pick something up
- `docs/decisions.md` — what has been decided, why, and what it rules out
- `docs/porting-guide.md` — how to port a function, and what the authorities are
- `docs/rebol-findings.md` — what reading Rebol's source turned up about Rebol
- `docs/web-port.md` — how the web port is put together, and what it cost
- `using-jebol.md` — the manual
