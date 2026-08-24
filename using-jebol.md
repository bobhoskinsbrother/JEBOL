# Using JEBOL

This document is for a person or an agent that wants to execute REBOL scripts
with JEBOL. It is not about how JEBOL is built. For that, read
`docs/porting-guide.md` and `TODO.md`.

Two sections at the end are not written yet. They are named, and what each one
must hold is stated, because a gap that is named is safer than a gap that is
not.

---

## 1. What JEBOL is

JEBOL is REBOL 3 (R3-Alpha) ported from C to work on the Java Virtual Machine. A Java program holds
an interpreter, gives it a script, and gets a value back.

Two limits make JEBOL different from most embedded languages. A script stops
when its time limit passes. A script reaches nothing outside itself until the
host permits it, and the default is nothing at all.

```java
Interpreter interpreter = Interpreter.create();
ScriptOutcome outcome = interpreter.run("1 + 2");
System.out.println(outcome.asHostValue());   // 3
```

Nothing a script does arrives at the host as a Java exception. A script that
fails, that takes too long, or that nests too deep gives back a
`ScriptOutcome` like any other. Thus a host that catches a throwable knows
that JEBOL itself has a defect.

The shipped file has no dependencies.

JEBOL is a port of Oldes/Rebol3 (https://github.com/Oldes/Rebol3), and part of
that project ships unmodified inside the jar. The section named "Licence and
attribution" at the end of this document states what and under which terms.

---

## 2. How to get JEBOL and start a script

**JEBOL needs a Java 25 runtime on the machine already.** The distribution
does not contain one. A Java that is older stops with a class version error
that does not name the cause.

### From the distribution file

The distribution holds a launcher for Unix, a launcher for Windows, one jar,
this document, `LICENSE` and `NOTICE`.

```
unzip jebol-0.1.0-SNAPSHOT.zip
JAVA_HOME=/path/to/java25 jebol-0.1.0-SNAPSHOT/bin/jebol
```

### From the sources

```
./gradlew installDist
JAVA_HOME=$(ls -d ~/.gradle/jdks/*25*/*/Contents/Home | head -1) \
    ./build/install/jebol/bin/jebol
```

Both start the console. Type an expression and press enter.

```
JEBOL -- REBOL 3 on the JVM. Type quit to leave.
>> 2 + 3 * 4
== 20
>> name: "world"  append name "!"
== "world!"
>> divide 1 0
** math error: division by zero
```

To execute one piece of text and stop, give the `--do` argument.

```
./build/install/jebol/bin/jebol --do "print 1 + 2"
```

Rebuild with `./gradlew installDist` after any change to the Java sources. The
installed files do not change until you do.

There is no argument that takes the name of a script file. A path given on the
command line is ignored without a message, and the console starts. A script
must arrive on standard input or inside `--do`.

```
cat my-script.reb | jebol
```

---

## 3. How to embed JEBOL in a Java program

Five calls do the whole job.

```java
Interpreter interpreter = Interpreter.withBounds(
        Bounds.standard()
              .withWallClockLimit(Duration.ofMillis(200))
              .withHostAccess(HostAccess.READING_AND_CALLING));

interpreter.define("vatRate", 0.2);
interpreter.defineFunction("lookupPrice", 1, arguments -> priceOf(arguments.get(0)));

ScriptOutcome outcome = interpreter.run("multiply lookupPrice {widget} add 1 vatRate");
Object total = outcome.asHostValue();
```

`Bounds.standard()` holds a default for every limit except one. The defaults
are a time limit of 5 seconds, a nesting limit of 10000, and a check for
whether to stop after every 1000 steps. The exception is the set of host
services, which starts empty, because the correct set is the one that nobody
guesses.

`HostAccess` has three values and the default is `NONE_AT_ALL`. With
`NONE_AT_ALL` the script sees only REBOL. With `READING` the script can read
host values that it was given. With `READING_AND_CALLING` the script can also
call functions that the host defined.

Braces mark a REBOL string. Write `{widget}` inside a Java string and no
character needs an escape. Do not write `\"widget\"`.

One interpreter belongs to one thread. A host that wants more than one thread
makes more than one interpreter. The exception is `cancel()`, which another
thread calls to stop a script that is executing.

The first interpreter takes about 343 milliseconds to start, and about 72
milliseconds once the JVM has settled. A service that makes a new interpreter
for every request must know those numbers before it is deployed.

---

## 4. What a script can reach

By default a script reaches nothing outside itself. No files, no network, no
clock, no console, no other program.

Each kind of access needs two steps. Give the service in the bounds, then give
the port that serves it.

| What the script must do | The service | The port to supply |
| --- | --- | --- |
| Read, write, delete, rename, list files | `FILES` | `useFileSystem(FileSystemPort.rootedAt(path))` |
| Know where a relative path starts | `WORKING_DIRECTORY` | the same file port |
| Read the names and values of the environment | `ENVIRONMENT` | `useEnvironment(port)` |
| Start another program and wait for it | `PROCESSES` | `useProcesses(port)` |
| Read a line from the operator | `CONSOLE` | `useConsole(port)` |
| Ask what the time is | `CLOCK` | none |
| Open a connection to another machine | `NETWORK` | `useNetwork(port)` |
| Ask the operator through a window | `WINDOWS` | `useWindows(port)` and `useScreen(port)` |

```java
Interpreter interpreter = Interpreter.withBounds(
        Bounds.standard().granting(HostService.FILES));
interpreter.useFileSystem(FileSystemPort.rootedAt(Path.of("/srv/scripts")).readOnly());
```

The file port is rooted at one directory and that root is a boundary. A path
that goes outside the root is refused. This is true whether the path climbs
with `..` or names an absolute path from the start.

Each interpreter keeps its own working directory inside that root. A Java
process cannot change its own working directory, thus the port holds it. One
interpreter can never move another.

A script that asks for a service that it does not have gets an error. The
outcome is `RAISED` and the error names one of three reasons. `not_granted`
means that the host has the service and did not give it. `not_present` means
that the host has no such service. `never_portable` means that no host can
ever offer it.

This error is more important than it looks. A read that quietly answered none
would read as an empty file. A script could not tell the two apart.

---

## 5. What happens when a script stops

Every way out is one of six conclusions. Ask `outcome.conclusion()`, or ask
`outcome.succeeded()` when only the first one matters.

| Conclusion | What it means | Where the value is |
| --- | --- | --- |
| `PRODUCED_A_VALUE` | The script finished. | `asHostValue()` |
| `RAISED` | The script failed, and it could have caught the error itself. | `errorId()` and `display()` |
| `TIMED_OUT` | The time limit passed first. | none |
| `CANCELLED` | The host called `cancel()`. | none |
| `QUIT_EARLY` | The script called QUIT. | `asHostValue()` |
| `HALTED` | The script called HALT and gave control back. | none |

The two that hosts confuse are `CANCELLED` and `QUIT_EARLY`. The first is the
host that stops the script. The second is the script that stops itself because
it meant to.

Examine the conclusion before you take the value. `asHostValue()` answers for
a failed script as well as a finished one, and the answer is then the error.
This is the most common defect in code that embeds JEBOL, because the shortest
code that compiles does not do the examination.

```java
ScriptOutcome outcome = interpreter.run(source);
if (!outcome.succeeded()) {
    log.warn("script stopped: {} {}", outcome.conclusion(), outcome.display());
    return fallbackValue;
}
return outcome.asHostValue();
```

An interpreter that stopped a script still operates. A time limit that passes
does not damage it, and a script that stops in the middle never leaves a
series half changed.

---

## 6. How values cross between Java and the script

The mappings are the obvious ones and nothing more. A value with no obvious
counterpart stays what it is, because an incorrect conversion is worse than no
conversion.

Into the script, through `define` and through the arguments of a
`defineFunction` result:

| Java | REBOL |
| --- | --- |
| `Boolean` | `logic!` |
| `Long`, `Integer`, `Short`, `Byte` | `integer!` |
| `Double`, `Float` | `decimal!` |
| `BigDecimal` | `money!` |
| `String`, `Character` | `string!` |
| `List<?>` | `block!` |
| anything else | `java-object!`, held and not converted |

Out of the script, through `asHostValue()`:

| REBOL | Java |
| --- | --- |
| `integer!` | `long` |
| `decimal!` | `double` |
| `money!` | `BigDecimal` |
| `logic!` | `boolean` |
| `string!` | `String` |
| `block!` | `List<Object>` |
| `none!`, `unset!` | `null` |
| anything else | the molded text, as a `String` |

A Java null and REBOL's `none` stay apart in both directions. A Java null is
the absence of a value. REBOL's `none` is a value that means nothing. Use
`asOptionalHostValue()` when you want both to arrive as an empty `Optional`.

---

## 7. What is not complete

Read this section before you build on JEBOL. Some of what is here is absent,
and some of it answers incorrectly, which is worse.

**Words that answer incorrectly.** Rebol's own suite runs 4337 assertions here
and 2 fail, and neither of those two is a port defect. Take the current number
from a run rather than from this page, because it is the number that moves.

**Words and types that are absent.** `task!` is the one datatype that R3 has
and JEBOL does not. Four fields of `access-os` (`uid`, `euid`, `gid`, `egid`)
answer `not-here`, because the JVM cannot ask a portable question. Some schemes
that the JDK could serve are not written. TLS loads but does not connect.

**Graphics.** DRAW renders 22 of R3's 36 commands. The fourteen that are absent
include `image` and `text`, which are the two that make a page look wrong
rather than plain. VID is absent.

**The console.** There is no argument that takes the name of a script file,
and a path given on the command line is ignored without a message.

Every count here comes from `TODO.md` and changes with the work. Take the
current numbers from a run of `./gradlew check` rather than from this page.

---

## 8. Worked examples

**Not written yet.** This section must hold about six complete programs. Each
one must be a file that executes. Each one must have a test, so that it stays
true. The set must cover these tasks:

- a script under a time limit
- a call from the script back into Java
- a read of a file through a granted directory
- a script that fails, and a host that handles the failure
- a small dialect

They must ship inside the distribution file, because a reader who has only
that file has no other example.

Prose in this document can become incorrect without anything noticing. A
program that the gate executes cannot.

---

## 9. The word reference

**Not written yet.** This section must name every function that the
interpreter has, with the arguments that each one takes. It must be generated
from the interpreter rather than typed, for the same reason as section 8.

`scripts/c-surface.py` and `scripts/c-parity.py` already report the surface
against Rebol's own C. A reference for a reader is a third report of the same
shape.

---

## Where to read more

These files are in the source repository. The distribution file does not hold
them.

| The question | The file |
| --- | --- |
| What a host can decide, and what a script can reach | `spec/embed.allium` |
| What PARSE supports, keyword by keyword | `spec/parse.allium` |
| How a dialect is read by DELECT | `spec/dialect.allium` |
| What a REBOL value is | `spec/values.allium` |
| What each function does | `spec/natives.allium` |
| How source text becomes values | `spec/load.allium` |
| How a block is evaluated | `spec/eval.allium` |

Every claim in those files was put to a real REBOL 3.22.1 before it was
written down. They say what the language must do. This build does not agree
with them everywhere, and section 7 names where.

---

## Licence and attribution

JEBOL is Copyright 2026 Ben Hoskins, under the Apache License, Version 2.0.
The full text is in the `LICENSE` file, beside this one in the distribution.

**JEBOL is a port of Oldes/Rebol3.** The source is at
https://github.com/Oldes/Rebol3, and that project is itself a continuation of
REBOL, Copyright 2012 REBOL Technologies, with Copyright 2012-2026 Rebol Open
Source Contributors. It is under the Apache License, Version 2.0. Every
function that JEBOL writes in Java was read from that project's C, and the
behavior was put to a real REBOL 3.22.1 built from it.

**Part of that project ships inside this jar, unmodified.** Rebol writes about
a third of its library in C and the rest in REBOL. JEBOL ports the C and
borrows the REBOL. The files under `org/jebol/mezz/` in the jar are byte for
byte as published upstream, and each one carries its own copyright and licence
header. They are the larger part of the jar. JEBOL loads and evaluates them at
startup, thus much of what a script calls is Rebol's own code and not a
rewrite of it.

The `NOTICE` file states this in full, and the Apache License requires you to
keep it with any copy that you give to somebody else.

REBOL is a trademark of REBOL Technologies. JEBOL is not associated with
REBOL Technologies, and neither REBOL Technologies nor the maintainers of
Oldes/Rebol3 endorse it.
