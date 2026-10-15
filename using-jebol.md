# Using JEBOL

This document is for a person or an agent that wants to execute REBOL scripts
with JEBOL. It is not about how JEBOL is built. For that, read
`docs/porting-guide.md` and `goals.md`.

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
unzip jebol-3.22.5.zip
JAVA_HOME=/path/to/java25 jebol-3.22.5/bin/jebol
```

### From the sources

```
./gradlew installDist
JAVA_HOME=$(ls -d ~/.gradle/jdks/*25*/*/Contents/Home | head -1) \
    ./build/install/jebol/bin/jebol
```

Both start the console, which runs REBOL's own `start` first, as `r3` does:
that is where the banner comes from. Type an expression and press enter.

```
╔══════════════════════════════════════════════════════════════════════════╗
║  REBOL/core 3.22.5 (JEBOL port)                                          ║
...
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

A script file or an app directory goes on the command line. A directory is an
app: it runs the `main.reb` inside it, with the app as the working directory,
so the app's own files are what `do %...` and `read %...` find. A directory
with no `main.reb` in it is refused with a sentence that says so.

```
jebol my-script.reb
jebol my-app/
```

Otherwise a script arrives on standard input or inside `--do`.

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
| Fetch a url with HTTP or HTTPS | `WEB` | `useHttp(port)` |
| Ask the operator through a window | `WINDOWS` | `useWindows(port)` and `useScreen(port)` |

```java
Interpreter interpreter = Interpreter.withBounds(
        Bounds.standard().granting(HostService.FILES));
interpreter.useFileSystem(FileSystemPort.rootedAt(Path.of("/srv/scripts")).readOnly());
```

A host that has no way to offer a service at all says so once, on the bounds.
`lacking(NETWORK)` means this host has no network, whatever it grants later.
A script that asks for one reads `not_present` rather than `not_granted`. Use
`called("a phone")` to name the host in that sentence.

`useHttp(port)` is the web build's shape of `WEB`: the host fetches, and the
http and https schemes answer through it. A host that supplies no HTTP port
leaves those schemes to Rebol's own protocol, which opens a `NETWORK`
connection of its own.

Each interpreter keeps its own working directory inside the file port's root.
A Java
process cannot change its own working directory, thus the port holds it. One
interpreter can never move another.

A script that asks for a service that it does not have gets an error. The
outcome is `RAISED` and the error names one of three reasons. `not_granted`
means that the host has the service and did not give it. `not_present` means
that the host has no such service. `never_portable` means that no host can
ever offer it.

This error is more important than it looks. A read that quietly answered none
would read as an empty file. A script could not tell the two apart.

### Two layers decide what a file access may do

Every file access passes two checks, in this order.

1. **The host's root.** The file port is rooted at one folder, and nothing
   outside that folder can be read, written, listed, deleted or renamed. The
   host decides this, and the script cannot change it.
2. **Rebol's security policies.** Rebol's own SECURE function records what
   the script may touch, and JEBOL checks those records where R3 checks them.
   SECURE can only narrow what the root and the grants allow. It can never
   widen them.

The same order holds for every other service. A service the host did not
grant is refused before any policy is read, so `secure allow` does not bring
it back.

The examples below use this folder. Every one was run through JEBOL. In the
output, the folder is shortened to `/work/projects` and the home folder to
`/Users/ana`. Each error also prints its `** Where:` and `** Near:` lines,
which are left out here.

```
/work/projects/
    secret.txt
    shared/logo.txt
    app/
        main.r3
        notes.txt               "app notes"
        again.txt         ->    notes.txt                 (a link inside app)
        logo.txt          ->    ../shared/logo.txt        (a link out of app)
        assets/door.txt   ->    ../../secret.txt          (a link out of app)
        dangling.txt      ->    ../../outside-new.txt     (points at nothing yet)
```

### The root

The script sees the root as `/`. A path that climbs with `..` stops at the
root, because `/..` is `/` on any machine. An absolute path names a file
inside the root, never one on the machine.

Symbolic links are followed. A link that lands inside the root works like any
other path. A link that leads out of the root is refused with a `security`
error that names the path as the script wrote it, so the script never learns
where the root sits. A link that points at nothing yet is judged by where a
write through it would land, so a script cannot use one to make a file
outside. More than forty links in a row are refused, which is where a POSIX
kernel gives up too.

With the command line, `--root /work/projects/app` gives a script that root.
The script is then named inside the root, as `/main.r3`. With `-s`, every
policy allows, so only the root decides:

```
>> what-dir
== %/
>> read/string %again.txt
== "app notes^/"
>> read/string %../secret.txt          ; .. stops at the root: this is /secret.txt
** Access error: cannot open: %../secret.txt reason: 3
>> read/string %logo.txt               ; a link out of the root
** Access error: security violation: %logo.txt  (refer to SECURE function)
>> read/string %assets/door.txt        ; the same, one folder down
** Access error: security violation: %assets/door.txt  (refer to SECURE function)
>> write %dangling.txt "x"             ; writing through a link to nothing yet
** Access error: security violation: %dangling.txt  (refer to SECURE function)
>> exists? %logo.txt                   ; a link out looks like nothing there
== _
>> delete %logo.txt                    ; removes the link; shared/logo.txt stays
```

From Java, a host gets the same behaviour by rooting the port:

```java
Interpreter interpreter = Interpreter.withBounds(
        Bounds.standard().granting(HostService.FILES));
interpreter.useFileSystem(FileSystemPort.rootedAt(Path.of("/work/projects/app")));

ScriptOutcome linked = interpreter.run("read %assets/door.txt");
// RAISED: ** Access error: security violation: %assets/door.txt
```

Deleting, renaming and making a folder act on the entry the path names. They
do not act on what a link points to. The folders above that entry are still
followed, and they must still land inside the root.

### Rebol's security policies

An embedded interpreter starts with every policy at allow. Only the root and
the grants limit it until the script, or the host, calls SECURE.

The command line runs Rebol's own start, as r3 does, and start sets Rebol's
default policy. A script may read and write in its own folder and in the data
folder. It may read in the home folder but not write there. Anything else
asks first. No one can answer a question while a script runs, so the answer
is no. `secure query` shows what is in force:

```
[
    file ask
    %/work/projects/app/ allow
    %/Users/ana/.rebol/ allow
    %/Users/ana/ [allow read ask write allow execute]
    net allow
    secure allow
    protect allow
    debug allow
    envr allow
    call allow
    browse allow
    extension allow
]
```

So `jebol main.r3`, run in `/work/projects/app`:

```
>> read/string %notes.txt
== "app notes^/"
>> read/string %again.txt             ; a link inside the folder is followed
== "app notes^/"
>> read/string %../secret.txt
** Access error: security violation: %/work/projects/secret.txt  (refer to SECURE function)
>> write %../written.txt "x"          ; refused, and nothing is written
** Access error: security violation: %/work/projects/written.txt  (refer to SECURE function)
>> read/string %logo.txt              ; judged by where the link leads
** Access error: security violation: %/work/projects/shared/logo.txt  (refer to SECURE function)
>> read/string %assets/door.txt
** Access error: security violation: %/work/projects/secret.txt  (refer to SECURE function)
```

A bare `jebol`, with no script, has no folder of its own. A file in the
folder where it started therefore asks first. At a terminal, you answer, and
yes is remembered. With input piped in, the answer is no. This is the same as
r3. Start it with `-s` to reach everything.

A script that does not catch the violation stops there, and the command line
exits with status 1. A script can catch it like any error. The error is
`access`/`security`, and `arg1` names the file.

**Here JEBOL differs from R3 on purpose.** R3 checks the path as it is
written and never looks at where a link points. In R3, `logo.txt` and
`assets/door.txt` above are read. JEBOL checks where the link really leads,
and the violation names that place.

#### The switches

| Command | What happens |
| --- | --- |
| `jebol main.r3` | Rebol's default policy, as above. |
| `jebol -s main.r3` | Every policy allows. The script reads `../secret.txt`. |
| `jebol --secure allow main.r3` | The same as `-s`. |
| `jebol +s main.r3` | Every policy asks, so start's own PROTECT is refused. The script never runs. The command line prints `security violation: system/options/boot-level` and exits with 1. |
| `jebol --secure throw main.r3` | The same as `+s`. |
| `jebol --secure quit main.r3` | Start's own PROTECT ends the process at once. Nothing is printed, and the exit status is 101. |

The `+s` result surprises people. It is what a real r3 3.22.5 does, and
JEBOL agrees with it.

#### A script can narrow its own policy

A script can only narrow its own policy. It can never get past the root or
the grants:

```
>> secure [file [allow read throw write]]
>> read/string %notes.txt
== "app notes^/"
>> write %notes.txt "changed"
** Access error: security violation: %/work/projects/app/notes.txt  (refer to SECURE function)

>> secure [envr throw call throw net throw debug throw]
>> get-env "HOME"
** Access error: security violation: "HOME"  (refer to SECURE function)
>> call/wait "true"
** Access error: security violation: "true"  (refer to SECURE function)
>> open tcp://example.com:80
** Access error: security violation: tcp://example.com:80  (refer to SECURE function)
>> trace on                            ; no subject, so the class is named
** Access error: security violation: debug  (refer to SECURE function)

>> secure [file 5]
** access error: invalid file security policy: 5
```

Note the parentheses when the policy is built. SECURE takes its argument
unevaluated, so write `secure (compose [file throw (folder) allow])`, as
Rebol's own start does. Without them, SECURE receives the word `compose`:

```
>> secure compose [file throw (folder) allow]
** access error: invalid all security policy: compose
```

Each level means one thing:

| Level | What happens |
| --- | --- |
| `allow` | The access goes ahead. |
| `ask` | Rebol's own `confirm-policy` asks at the terminal: yes, no, once or quit. Yes is remembered as a policy for that file. With no terminal, the answer is no. |
| `throw` | A `security` error, naming the file, url or variable. Where nothing is named, as for TRACE, it names the class. |
| `quit` | The run ends at once with 101. TRY does not catch it. A host sees `QUIT_EARLY` with the value 101. |

A host can set a policy for the script before it runs. The host's grants are
still the ceiling:

```java
interpreter.run("secure [file [allow read throw write]]");
interpreter.run("write %notes.txt {changed}");
// RAISED: ** Access error: security violation: %/notes.txt

interpreter.run("secure allow");
interpreter.run("call/wait {true}");
// RAISED: processes is not granted. SECURE cannot grant what the host held back.
```

#### What is checked and what is not

JEBOL checks a policy at the same points the C does, and nowhere else.

| Checked | Against |
| --- | --- |
| READ, LOAD and DO of a file. OPEN without /write, and OPEN/read. | `file` read |
| WRITE, WRITE/append, CREATE. OPEN without /read, OPEN/write and OPEN/new. | `file` write |
| READ, OPEN, QUERY and EXISTS? of a folder | `file` read |
| MAKE-DIR, DELETE and RENAME of a folder | `file` write |
| OPEN of a tcp or udp port. HTTP and HTTPS are checked through the tcp port they open. | `net` execute, naming the address |
| CALL | `call` execute |
| BROWSE | `browse` execute |
| GET-ENV / SET-ENV | `envr` read / write |
| PROTECT / UNPROTECT | `protect` write / read |
| EVOKE, STACK, TRACE | `debug` read |
| LOAD-EXTENSION of a file | `extension` execute |

The C does not check these, so JEBOL does not check them either. Under the
default policy, each one works one folder up:

```
>> size? %../secret.txt
== 7
>> rename %../secret.txt %../renamed.txt    ; goes ahead
>> delete %../renamed.txt                   ; goes ahead
>> exists? %../shared/                      ; a folder: queried, refused, so none
== _
```

- DELETE, RENAME, QUERY and MODIFY of a file. This includes SIZE?,
  MODIFIED? and EXISTS? of a file.
- ECHO and IMAGE/SAVE, which write without the file actor.
- A port that is already open. OPEN was checked, and reads and writes after
  it are not.
- LIST-ENV, PROTECTED? and reading a `dns://` url.
- The `eval` and `memory` limits. LIMIT-USAGE is gone before any script runs,
  in R3 as here, so `secure [eval 1000]` fails with `limit-usage has no
  value`.

The root still bounds every one of these. A host that roots a script in its
own folder keeps it there, whatever the policy allows.

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

**Words that answer incorrectly.** Rebol's own suite runs every assertion in
its sixty-seven files here, and one fails — an assertion that asks to read the
launcher a confined child interpreter is started from, which has to sit outside
what a script can see. Take the current number from a run rather than from this
page, because it is the number that moves.

**Words and types that are absent.** Every datatype R3 has is here, `task!`
included — though a task is made, read and answered by DO without ever running
on a thread. Four fields of `access-os` (`uid`, `euid`, `gid`, `egid`) answer
`not-here`, because the JVM cannot ask a portable question. Five scheme names
are not registered: `callback`, `clipboard`, `midi`, `serial` and `udp`. A
scheme name in R3 promises less than it looks — it registers whether or not
the build can serve it, and refuses at open time — so what is missing here is
mostly the registration.

**Transport.** `read https://` works and `import` fetches over it. The TLS
client does not authenticate the server: an expired certificate, a self-signed
one, one issued for another host and one from an untrusted root are all read
without complaint. A fetched module is not checksummed either. A real 3.22.5
does neither check, so this is a divergence from good practice rather than
from Rebol — and both are things to weigh before fetching code over a network
you do not control.

**Memory.** A script that asks for more than the heap can give is refused as
`no-memory` where it asks MAKE for it, and can still take the host down with a
`java.lang.OutOfMemoryError` on any other path that allocates — appending in a
loop, reading a large file, joining strings. Bound the heap of a process that
runs untrusted scripts.

**Graphics.** DRAW paints every command its dialect table declares except
`effect`. VID is absent. There is no Rebol to compare a picture against — a
stock 3.22.5 has no `draw` at all — so the check is that Java2D and a real
browser draw the same paint list the same way, which `./gradlew browserCheck`
does on every run. The same gate builds the interpreter for the web and runs a
script that sits in a page's tag.

Every count here comes from `goals.md` and changes with the work. Take the
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
