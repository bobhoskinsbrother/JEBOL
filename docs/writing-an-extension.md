# Writing an extension

An extension is how a Java program adds its own words to JEBOL. A script calls
them like any other REBOL function. This guide shows how to write one, a step
at a time, with an example for each step. Every example here was run against
JEBOL 3.22.5, and the output shown is what it printed.

It follows the shape of R3's own hosted extensions. Under the bonnet, JEBOL
loads yours with Rebol's `load-extension/dispatch` and `load-ext-module`, as
R3 loads one. So the words behave the way an R3 extension's words behave.

---

## 1. The smallest extension

An extension has a name, a version, and one or more commands. A command is
a word, a REBOL spec, and the Java that runs when a script calls the word.

```java
import org.jebol.application.*;
import org.jebol.domain.value.*;
import java.util.List;

Extension greeter = new Extension("greeter", "1.0.0", List.of(
        new ExtensionCommand("greet", "{Says hello.} who [string!]",
                call -> StringValue.of("hello " + ((StringValue) call.arguments().getFirst()).text()))));

Interpreter interpreter = Interpreter.withBounds(Bounds.standard().add(greeter));
```

The script can now call it:

```
>> greet {world}
== "hello world"
```

That is the whole of it. The rest of this guide is what you can put inside a
command, and what happens around it.

---

## 2. Writing a command's spec

The spec is a REBOL function spec, text as a Java string without its
outer brackets. Put REBOL strings in braces, `{...}`, so nothing in Java
needs escaping.

| You want                      | Write                              |
|-------------------------------|------------------------------------|
| Help text                     | `{Says hello.}` first              |
| An argument of any type       | `value`                            |
| An argument of some types     | `who [string! file!]`              |
| Help text for an argument     | `delay [integer!] {Milliseconds.}` |
| A refinement                  | `/loud`                            |
| A refinement with an argument | `/times count [integer!]`          |
| No arguments                  | `""`                               |

JEBOL checks the arguments against the spec before your Java runs. A script
that passes the wrong type gets REBOL's own error, naming the word, the
argument and the type it got:

```
>> e: try [timer-elapsed 5] reduce [e/id e/arg1 e/arg2 e/arg3]
== [expect-arg timer-elapsed timer #(integer!)]
```

So your Java can cast an argument to the type the spec allows without
checking it again.

The script sees your spec, and `help` shows it:

```
>> spec-of :timer-alarm
== ["Wakes a port after a delay." port [port!] delay [integer!] "Milliseconds."]
>> type? :timer-alarm
== #(command!)
```

A command's datatype is `command!`, as an R3 extension's words are.

---

## 3. What your Java gets, and what it answers

Your command's Java is a function from a `Command` to a REBOL value:

```java
call -> IntegerValue.of(42)
```

The `Command` holds:

| Call                                       | Gives you                                                           |
|--------------------------------------------|---------------------------------------------------------------------|
| `call.arguments()`                         | The arguments, in the order of the spec, as REBOL values            |
| `call.refinements()`                       | The refinements the script used, without their slashes              |
| `call.evaluated(block)`                    | A block the script gave you, evaluated in the script's interpreter  |
| `call.handleOf(kind, object)`              | A handle carrying a Java object, to give to the script (section 5)  |
| `call.heldBy(handle, Class)`               | The Java object a handle carries (section 5)                        |
| `call.failure(category, id, arguments...)` | An error to throw (section 4)                                       |
| `call.postbox()`                           | The interpreter's postbox, for work that finishes later (section 6) |

**Arguments arrive as REBOL values, not Java ones.** A `file!` arrives as a
`FileValue` and a `string!` as a `StringValue`, so you can tell `%a.txt` from
`"a.txt"`. A block arrives as a block. Read them with their own methods:

```java
String text = ((StringValue) call.arguments().get(0)).text();
long number = ((IntegerValue) call.arguments().get(1)).magnitude();
boolean loud = call.refinements().contains("loud");
```

**Answer with a REBOL value.** The common ones:

| To answer             | Write                                                               |
|-----------------------|---------------------------------------------------------------------|
| An integer            | `IntegerValue.of(42)`                                               |
| A string              | `StringValue.of("text")`                                            |
| A file                | `FileValue.of("made.txt")`                                          |
| A url                 | `UrlValue.of("http://example.com")`                                 |
| A block               | `BlockValue.block(List.of(IntegerValue.of(1), IntegerValue.of(2)))` |
| none                  | `NoneValue.none()`                                                  |
| Nothing at all        | `UnsetValue.unset()`                                                |
| One of your arguments | `call.arguments().getFirst()`                                       |

---

## 4. When a command fails

There are two ways to fail, and the script sees each one differently.

**On purpose, with a REBOL error.** Throw what `call.failure` makes. Give the
category, the error's id from Rebol's catalogue, and its arguments:

```java
if (delay < 0) {
    throw call.failure(ErrorCategory.SCRIPT, "out-of-range", call.arguments().get(1));
}
```

```
>> e: try [timer-alarm a -5] reduce [e/id e/arg1]
== [out-of-range -5]
```

A script handles this exactly as it handles the same error from a built-in.
`ErrorCategory` holds Rebol's categories: `SCRIPT`, `MATH`, `ACCESS`,
`COMMAND`, `USER` and the rest.

**By accident.** Anything else your Java throws becomes `command-fail`,
carrying the exception's message:

```
>> break-the-host
** Command error: "the host fell over"
```

A script can catch it with `try`, like any error. JEBOL never lets a Java
exception escape into the host from a script.

---

## 5. Giving the script a Java object: handles

A script can't hold a Java object directly. Give it a handle instead. A
handle carries your object, and the script can pass it back to you but not
look inside.

First declare the kind of handle, naming the class it carries:

```java
.withHandleKind(new HandleKind("timer", Timer.class))
```

Then make one in a command, and read it back in another:

```java
new ExtensionCommand("timer-start", "{Starts a timer and answers it.}",
        call -> call.handleOf("timer", new Timer(System.nanoTime()))),

new ExtensionCommand("timer-elapsed", "{Milliseconds since the timer started.} timer [handle!]",
        call -> {
            Timer timer = call.heldBy(call.arguments().getFirst(), Timer.class);
            return IntegerValue.of((System.nanoTime() - timer.startedAt()) / 1_000_000);
        })
```

```
>> t: timer-start
== #(handle! timer)
>> t/type
== timer
>> integer? timer-elapsed t
== #(true)
```

If a script passes a handle of another kind, `heldBy` refuses it for you:

```
>> e: try [timer-elapsed rc4/key #{00}] e/id
== invalid-handle
```

---

## 6. Work that finishes later: the postbox

Some work doesn't finish while the command runs: a photo being taken, a
location arriving, a timer going off. For that, an extension posts to the
interpreter's **postbox**. The postbox is safe to use from any thread. What
you post waits there until the script next calls `wait`.

Keep the postbox when the extension starts, and use it later:

```java
private final AtomicReference<Postbox> postbox = new AtomicReference<>();

// in the extension:
.startingWith(postbox::set)
```

There are two things you can post.

### An event for a port

`post(port, "read")` wakes a script waiting on that port, as a port's own
input would.

```java
new ExtensionCommand("timer-alarm",
        "{Wakes a port after a delay.} port [port!] delay [integer!] {Milliseconds.}",
        call -> {
            PortValue port = (PortValue) call.arguments().get(0);
            long delay = ((IntegerValue) call.arguments().get(1)).magnitude();
            Thread.ofVirtual().start(() -> {
                sleep(delay);
                postbox.get().post(port, "read");
            });
            return port;
        })
```

The script needs a port to wait on. The extension can make a scheme for it
in its own REBOL (section 7). The port's `awake` decides what the event
means; answering `true` wakes the wait.

```
>> a: make port! alarm://
>> timer-alarm a 100
>> same? a wait [a 2]
== #(true)
```

The first two lines also print the port, which is left out here.
`wait [a 2]` waits for `a`, or two seconds, whichever comes first. It works
whether the event arrives before the `wait` starts or during it.

### A callback

`callBack(object, word, arguments, answer)` asks for a function in an object
to be called with those arguments. It runs on the script's own thread when
the script next waits, and its answer, or its failure, comes back to you:

```java
postbox.get().callBack(listener, "heard", List.of(StringValue.of("a shutter")),
        new Postbox.CallbackAnswer() {
            @Override
            public void answered(Value value) {
                // the function's answer
            }

            @Override
            public void failed(RuntimeException failure) {
                // what the function raised
            }
        });
```

```
>> listener: make object! [heard: func [what] [print join {heard } what]]
>> wait [system/ports/callback 2]
heard a shutter
```

This is how R3 carries callbacks too: as an event on `system/ports/callback`,
which runs `do-callback`.

---

## 7. Schemes and codecs

An extension can carry its own REBOL. It runs in the extension's module
after the commands are made, so it can use them. This is where you make a
scheme or register a codec, exactly the way Rebol's own files make `http`
and `png`.

**A scheme.** Here a `greet://` url reads through a command:

```java
.withScript("""
        sys/make-scheme [
            name: 'greet
            title: "Greetings"
            actor: [read: func [port] [greeting-for port/spec/host]]
        ]""")
```

```
>> read greet://world
== "hello world"
```

**A codec.** Here `decode 'shout` decodes through a command:

```java
.withScript("""
        register-codec [
            name: 'shout
            type: 'text
            title: "Shouting"
            suffixes: [%.shout]
            decode: func [data] [louder to string! data]
        ]""")
```

```
>> decode 'shout to binary! {quiet}
== "QUIET"
```

**A scheme with no actor**, only an `awake`, is all section 6's alarm needs:

```java
.withScript("""
        sys/make-scheme [
            name: 'alarm
            title: "Alarms"
            awake: func [event] [true]
        ]""")
```

---

## 8. Starting, and refusing to start

`startWith` runs once, as the extension is installed in each interpreter.
Use it to check that what the extension needs is there, and to keep the
postbox. If it throws, the extension is not installed:

```java
.startingWith(postbox -> {
    if (!aCameraIsAttached()) {
        throw new IllegalStateException("no camera on this machine");
    }
    kept.set(postbox);
})
```

---

## 9. Adding it, and checking it went in

Add an extension to the bounds you build interpreters with:

```java
Bounds bounds = Bounds.standard()
        .add(timers)
        .add(camera);

Interpreter interpreter = Interpreter.withBounds(bounds);
```

Every interpreter built with those bounds installs both, in the order you
added them. A task's interpreter is built with the same bounds, so it gets
them too. Adding an extension is the grant: an interpreter whose bounds
don't carry it has no such word.

Installing can fail. Ask what happened:

```java
interpreter.extensions();
// [ExtensionReport[name=timers, refusal=Optional.empty]]
```

`report.installed()` is true when it went in. Otherwise `report.refusal()`
holds the reason:

| Refusal          | Why                                                             | Example                                                                                                                |
|------------------|-----------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|
| `already-used`   | A command has a name `lib` already holds                        | a command called `add`, `append` or `secure`                                                                           |
| `bad-extension`  | Something doesn't read                                          | a blank name, a version that is not a tuple like `1.0.0`, a spec with a missing bracket, or two commands with one name |
| `handle-exists`  | A handle kind another extension declared with a different class | two extensions both declaring `session`, holding different classes                                                     |
| `extension-init` | Your start step threw                                           | no camera on this machine                                                                                              |

```java
Extension clash = new Extension("clash", "1.0.0", List.of(
        new ExtensionCommand("add", "a b", call -> StringValue.of("mine"))));

Interpreter.withBounds(Bounds.standard().add(clash)).extensions();
// [ExtensionReport[name=clash, refusal=Optional[already-used]]]
```

Installing is all or nothing. A refused extension leaves no word, no
module and no handle kind behind, even if most of its commands were fine.

**Names are first come, first served.** Every word in `lib` when an
interpreter is built is sealed: the natives, the actions, and Rebol's own
functions text in REBOL. An extension can't take any of those names.
Between two extensions that want the same name, the one added first wins.
The seal is only against extensions. A script can still write
`lib/add: func [a b] [...]`, as it can in R3.

**Where it lives.** An installed extension is a module, listed in
`system/modules` under its name. Its commands are words in `lib`:

```
>> select spec-of select system/modules 'timers 'version
== 1.0.0
>> same? import 'timers select system/modules 'timers
== #(true)
```

---

## 10. Testing an extension

Test it the way a script will use it: build an interpreter with it, run
REBOL, and look at the answer.

```java
class TimersTest {

    private final Interpreter interpreter = Interpreter.withBounds(
            Bounds.standard().add(new Timers().extension()));

    private String answerTo(String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("it installs")
    void itInstalls() {
        assertThat(interpreter.extensions().getFirst().installed()).isTrue();
    }

    @Test
    @DisplayName("a timer starts and counts")
    void aTimerCounts() {
        assertThat(answerTo("integer? timer-elapsed timer-start")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a negative delay is out-of-range")
    void aNegativeDelay() {
        assertThat(answerTo("""
                e: try [timer-alarm make port! alarm:// -5] e/id""")).isEqualTo("out-of-range");
    }
}
```

Section 13 lists the tests that cover each part of this guide. They are
good examples to copy from.

---

## 11. The whole worked example

This is the timers extension from the sections above, in one piece.

```java
import org.jebol.application.*;
import org.jebol.domain.eval.Postbox;
import org.jebol.domain.value.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class Timers {

    record Timer(long startedAt) {
    }

    private final AtomicReference<Postbox> postbox = new AtomicReference<>();

    Extension extension() {
        return new Extension("timers", "1.0.0", List.of(
                new ExtensionCommand("timer-start",
                        "{Starts a timer and answers it.}",
                        call -> call.handleOf("timer", new Timer(System.nanoTime()))),
                new ExtensionCommand("timer-elapsed",
                        "{Milliseconds since the timer started.} timer [handle!]",
                        call -> {
                            Timer timer = call.heldBy(call.arguments().getFirst(), Timer.class);
                            return IntegerValue.of((System.nanoTime() - timer.startedAt()) / 1_000_000);
                        }),
                new ExtensionCommand("timer-alarm",
                        "{Wakes a port after a delay.} port [port!] delay [integer!] {Milliseconds.}",
                        call -> {
                            PortValue port = (PortValue) call.arguments().get(0);
                            long delay = ((IntegerValue) call.arguments().get(1)).magnitude();
                            if (delay < 0) {
                                throw call.failure(ErrorCategory.SCRIPT, "out-of-range", call.arguments().get(1));
                            }
                            Thread.ofVirtual().start(() -> {
                                try {
                                    Thread.sleep(delay);
                                } catch (InterruptedException stopped) {
                                    return;
                                }
                                postbox.get().post(port, "read");
                            });
                            return port;
                        })))
                .withHandleKind(new HandleKind("timer", Timer.class))
                .withScript("""
                        sys/make-scheme [
                            name: 'alarm
                            title: "Alarms"
                            awake: func [event] [true]
                        ]""")
                .startingWith(postbox::set);
    }
}
```

And a session using it:

```
>> t: timer-start
== #(handle! timer)
>> integer? timer-elapsed t
== #(true)
>> a: make port! alarm://
>> same? a wait [a timer-alarm a 100 2]
== #(true)
>> e: try [timer-alarm a -5] reduce [e/id e/arg1]
== [out-of-range -5]
>> e: try [timer-elapsed 5] reduce [e/id e/arg1 e/arg2 e/arg3]
== [expect-arg timer-elapsed timer #(integer!)]
```

---

## 12. Quick reference

| Class                                     | What it is                                                                 |
|-------------------------------------------|----------------------------------------------------------------------------|
| `Extension(name, version, commands)`      | An extension. `withScript`, `withHandleType` and `startWith` add the rest. |
| `ExtensionCommand(name, spec, behaviour)` | One word: its name, its spec without brackets, and the Java behind it.     |
| `Command`                             | What a command's Java receives (section 3).                                |
| `HandleKind(name, class)`                 | A kind of handle and the class it carries.                                 |
| `Postbox`                                 | Where to post events and callbacks from any thread (section 6).            |
| `Bounds.add(extension)`                   | Adds it to every interpreter built with these bounds.                      |
| `Interpreter.extensions()`                | Whether each one was installed, and why not.                               |
| `ExtensionReport`                         | One extension's outcome: `installed()`, `refusal()`.                       |

The rules behind all of this are in `spec/embed.allium`, under "Extensions:
what a library adds", and `spec/natives.allium`, under "Extensions, from the
script's side".

---

## 13. Where the tests are

All of these are functional tests, in `src/functionalTest/java/org/jebol/application/`
unless the table says otherwise. Each test's `@DisplayName` says what it
checks, so a failure reads as a sentence. Run one class with, for example:

```
./gradlew functionalTests --tests 'org.jebol.application.ExtensionsFromTheSourceTest'
```

**`ExtensionsFromTheSourceTest`**: the host's side, through `Bounds` and
`Interpreter`. 40 tests in seven groups.

| Group             | Covers                                                                                                                                                                                                                                                                                                   | Guide section |
|-------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------|
| `CallingACommand` | calling by name; the `command!` datatype; the spec and its help text; `expect-arg` for a wrong type; refinements; values arriving and going back as REBOL values; `call.failure`; `command-fail` from a Java exception; `call.evaluated`; fifteen arguments allowed and sixteen refused as `bad-command` | 1, 2, 3, 4    |
| `WhereItLives`    | words in `lib`; the module in `system/modules` with its version; `import`; every interpreter with the same bounds getting it; none without it; `interpreter.extensions()`                                                                                                                                | 9             |
| `TheSeal`         | a native's name refused; one of Rebol's own functions refused; all or nothing; the first of two extensions winning; a script still able to assign into `lib`                                                                                                                                             | 9             |
| `Refusals`        | `bad-extension` for an unreadable spec, a version that is not a tuple, a blank name and two commands of one name; `extension-init`; `handle-exists`, and the same kind shared; an `Extension` missing a part refused when it is built                                                                    | 8, 9          |
| `ItsOwnRebol`     | a scheme made in the extension's REBOL; a codec registered in it                                                                                                                                                                                                                                         | 7             |
| `Handles`         | a handle going out to the script and back; a handle of another kind refused as `invalid-handle`                                                                                                                                                                                                          | 5             |
| `Posting`         | an event posted from another thread waking a `wait`; a callback running on the script's thread and answering; a callback that fails; `do-callback` of an ordinary event; a `wait` with nothing woken letting its time pass                                                                               | 6             |

**`CommandsFromTheSourceTest`**: the script's side, the natives R3 has for
extensions. 32 tests in three groups.

| Group            | Covers                                                                                                                                                                                                                                                                                                                                                                  | Guide section |
|------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------|
| `MakingACommand` | `make command!` and `to command!` checking their definition as R3's C does: `bad-make-arg` for a block that does not open with a spec, `bad-func-def` for one that does but is not `[spec extension number]`, the largest command number 65535 and one past it, a negative number, a number with no command behind it, and a command made by hand calling the right one | 2             |
| `LoadExtension`  | a file of native code refused as `no-extension`; SECURE checked first; a string and a binary refused; `/dispatch` answering an extension object; a command made from it; any handle accepted, as in R3, and `bad-extension` when it carries no dispatcher                                                                                                               | 7, 8          |
| `DoCommands`     | `do-commands`: set-words, the last answer, word, path and paren arguments, a word that is not a command, a wrong type, a missing argument, a failing command answering unset                                                                                                                                                                                            | 3             |

**Elsewhere**

| Test                                                                                            | Covers                                                                                                     |
|-------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| `InteropTest`, group `HostObjectMutability`                                                     | a Java object stays the host's to change, seen through a command                                           |
| `AnIdOnlyJebolRaisesIsWordedByItsOwnSentenceTest`, `saysHowACommandFailed`                     | the console wording of `command-fail`                                                                      |
| `AWaitsTimeIsPassedByTheHostTest`                                                               | `wait` counting its time in the host's own pause slices, which the postbox's waits rely on                 |
| `LoadExtensionSurfaceFromTheSourceTest` and `HostServiceGrantTest`                              | `load-extension` on a file and a binary, whatever is granted                                               |
| `org.jebol.domain.eval.natives.WhatEachRemainingNativeDeclaresTest`, group `TheExtensionPoints` | `load-extension`, `do-callback` and `do-commands` answering as r3 does, and checking their arguments first |
| `TheSchemesRebolDeclaresFromTheSourceTest`, `doCallbackOfAnOrdinaryEvent`                       | the `callback` scheme's `do-callback` answering none for an ordinary event                                 |
