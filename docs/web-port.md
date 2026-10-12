# The web port

Goal 1 in `goals.md`: one interpreter and one drawing engine on every platform,
the web first, with the interpreter compiled to WebAssembly. This note carries
what the code cannot. `CLAUDE.md` carries the method.

---

## Where it stands

The core compiles to WebAssembly with TeaVM 0.16.0 and runs in a real browser.
The build is JEBOL's own: `./gradlew buildWasmGC` writes `jebol-web.wasm` and
`jebol-web-runtime.js` into `build/generated/teavm/wasm-gc`, and
`./gradlew browserCheck` depends on that task and then drives a page in Chrome.

The pieces:

| Piece | Where |
| --- | --- |
| The web's main class | `src/teavm/java/org/jebol/adapter/web/TheScriptOnThePage`, handed the script as its first argument |
| The web's substitutions and the library-file list | `src/teavm/java` and `src/teavm/resources`, wired in `build.gradle.kts` |
| The library files TeaVM must embed | listed by the `writeTheWebResourceList` task from `src/main/resources/org/jebol`, so the list cannot drift |
| The page and its script | `src/test/resources/web/rebol-on-a-page.html`, the Rebol inside a `<script type="text/rebol">` tag |
| The unit level | `AScriptInAPageTagFromTheSourceTest` runs that tag's script on the JVM and pins its seven printed lines |
| The end-to-end level | `AScriptInAPageTagRunsInABrowserEndToEndTest` serves the build and the page, drives Chrome, and reads the same seven lines out of the page |

Measured so far: the WebAssembly module is 12.8 MB unobfuscated beside a 38 KB
runtime loader; the trial below measured a boot in Chrome of 477 ms going cold
and 164 to 201 ms warm. The shipped form, obfuscated and compressed, is goal 1
step 5's business.

The two pieces of blocking code named in this note are gone. WAIT's sleep is a
port (`PausePort`, in `org.jebol.domain.host`) that the interpreter asks one
fifty millisecond slice at a time, so it can still end a wait at its own
deadline between slices; an interpreter is built with `ThreadPause` and a host
replaces it through `Interpreter.usePause`. And `ScreenEventQueue` is plain
storage again: an adapter that hears a toolkit's listener leaves the event in a
`ConcurrentLinkedQueue` through `GobScreen.posted`, and `takeTheNextEvent`
moves what was posted into the interpreter's own queue on the interpreter's
own thread. `BrowserScreen` already worked this way; `DesktopScreen` now does
too.

A measurement worth keeping for step 1: **TeaVM's `Thread.sleep` works in a
browser**, and so does the pause port over it - a page script that waits
prints its later lines once the fiber is resumed by `setTimeout`. But
`teavm.exports.main` returns at the first suspension, so the page harness
writes its output as soon as `main` returns and a script that waits prints
only what it printed before the wait; delaying the read of the output by a
second shows the rest arriving. Until the worker's entry point can tell the
page when the script has really finished, the browser gate cannot hold a
script that waits: the pause is pinned by the JVM tests and measured here.

## The TeaVM trial, 2026-10-10

The trial that decided the web port was a one-day exercise: compile the domain
and application with TeaVM, run `print 1 + 2` in a page and then the Brotli
cases, and write down what the compiler refused, how big the download is and
how long a boot takes. TeaVM 0.16.0, Java 25 sources, Chrome headless on macOS.

The trial ran in a scratch Gradle project outside the repo, compiling copies of
`src/main/java` and `src/main/resources`. Its numbers:

- **JavaScript.** `print 1 + 2` printed 3 in a real headless Chrome. Boot times
  from the page: 672 to 685 ms first, then 371 to 404 ms warm; the run itself 0
  to 5 ms. Against a warm `Interpreter.create()` on the JVM of about 44 ms. The
  build was 5.35 MB, obfuscated and not compressed.
- **WebAssembly (Wasm GC).** Boot 477 ms first, then 164 to 201 ms warm; the
  module fetched and compiled in 64 to 83 ms. 12.8 MB unobfuscated.
- **The Brotli cases**, through the interpreter compiled by TeaVM: the six
  checks the test file that follows `docs/brotli-port.md` asserts, all true.

Two Gradle notes from the trial. Building the JavaScript and the WebAssembly
targets in one run thrashed a 4 GB daemon heap until the daemon died (`since the
JVM garbage collector is thrashing`); the repo builds the wasm alone and sets
`outOfProcess`. And TeaVM embeds a resource only when it is told to, through
the classlib's `ResourceSupplier`; without a list of all 111 library files the
interpreter boots into nothing.

Two more the repo build itself learned. The teavm source set is compiled with
`options.release = 21`, because the TeaVM tool runs on the Gradle daemon's Java
21 and loads the substitution policy as a service: a policy class compiled at
25 cannot be loaded by it. And annotation processing is off for every compile,
`-proc:none`, because the TeaVM plugin puts its own processor on the path and
nothing in this project uses annotations; the processor's source-version
warning is a fatal error under `-Werror` otherwise.

## What the compiler refused, and what each one needed

Twenty-six refusals, the same set for the JavaScript and the WebAssembly
targets:

| Count | Refused | Answer |
| --- | --- | --- |
| 7 | `javax.crypto.Cipher` | a classlib stand-in that throws |
| 6 | `java.security.Signature` | a classlib stand-in that throws |
| 5 | `java.lang.ref.PhantomReference` | `org.teavm.classlib.java.lang.ref.TPhantomReference`, a `TWeakReference` with a null `get()` |
| 5 | `java.lang.ProcessHandle` | `org.teavm.classlib.java.lang.TProcessHandle`: this process at pid 0, no other process exists |
| 4 | `java.lang.Character.toString(int)` | `new String(Character.toChars(int))` at four sites |
| 4 | `java.security.MessageDigest` | a classlib stand-in that throws |
| 4 | `java.security.KeyPair` | a classlib stand-in that throws |
| 3 | `java.lang.Math.unsignedMultiplyHigh(long,long)` | `UnsignedProducts.highOf`, verified against the JDK |
| 3 | `java.security.KeyPairGenerator` | a classlib stand-in that throws |
| 2 | `ConcurrentHashMap.newKeySet()` | `Collections.newSetFromMap(new ConcurrentHashMap<>())` |
| 2 | `java.lang.String.lines()` | `TextLines.of`, an exact clone, verified against the JDK |
| 2 | `javax.crypto.spec.SecretKeySpec` | a classlib stand-in that throws |
| 2 | `java.security.KeyFactory` | a classlib stand-in that throws |
| 1 each | `Math.fma`, `Long.parseUnsignedLong(String,int)`, `Float.floatToFloat16`, `Float.float16ToFloat`, `File.setExecutable`, `NamedParameterSpec.X25519`, `javax.crypto.spec.IvParameterSpec`, `javax.crypto.spec.ChaCha20ParameterSpec`, `javax.crypto.Mac`, `javax.crypto.KeyAgreement`, `java.security.spec.NamedParameterSpec`, `java.security.spec.ECPoint`, `java.security.AlgorithmParameters` | `ExactFusedMultiplyAdd` (the JVM uses `Math.fma`; the web build is substituted to the `BigDecimal` form, which rounds once and agrees with the JDK), `BigInteger` behind the same length check (with a sign refused, because `parseUnsignedLong` refuses one), `HalfFloats`, `File.setExecutable` behind `ExecutableFile`, and classlib stand-ins that throw for the crypto left over |

Three of those answers exist only because the JDK's own behaviour is not what
its source says. `Float.floatToFloat16` and `Float.float16ToFloat` are
intrinsics: on this machine they quiet a NaN and shift its payload simply,
where the JDK's Java body scrambles it differently. `HalfFloats` matches the
intrinsic, and its tests compare all 65,536 half values, every half's four
neighbours, and a quarter of a million random float bit patterns against the
JDK. `TextLines.of` matches `String.lines` over every text of six characters or
fewer built from `a`, newline and carriage return. `UnsignedProducts.highOf`
matches `Math.unsignedMultiplyHigh` on random pairs and on the patterns either
side of every 32-bit boundary.

## The TeaVM faults met

**A switch with two guarded destructuring patterns of one record holding a
`long` compiles to invalid wasm.** The symptom, from Chrome:

```
CompileError: WebAssembly.compileStreaming(): Compiling function #3691:
"org.jebol.domain.eval.WritingIntoText::theCodep..." failed:
ref.is_null[0] expected reference type, found local.get of type i64
```

A single guarded pattern is fine, and so is a bare one. Two of them in one
switch, or one guarded beside one bare, and the module will not compile. A
minimal repro is a sealed interface with a record `Whole(long magnitude)` and
a method:

```java
return switch (value) {
    case Whole(long magnitude) when magnitude == 1 -> "one";
    case Whole(long magnitude) when magnitude == 2 -> "two";
    default -> "other";
};
```

The workaround is a bare pattern with the accessor in the guard
(`case Whole whole when whole.magnitude() == 1`), and it compiles and runs.
Five switches in the core needed it: `WritingIntoText`, `TimeValue`,
`RemoveAction`, `EncodingNative` and `ParityAction`, each now written with
`case IntegerValue whole` and the accessor in the guard. Nobody has reported it
upstream yet.

**A JDK class the classlib lacks can become a type with no supertype.** A catch
of `java.security.InvalidKeyException`, a class TeaVM's classlib does not have,
made it emit a `br_on_cast` from a type that was not a `Throwable`, which Chrome
also refuses to compile (`invalid types for br_on_cast`). Supplying the class,
`TInvalidKeyException` extending the classlib's `TGeneralSecurityException`,
fixed it. The general shape is worth remembering: when TeaVM says a class "was
not found", it sometimes means the class will be stubbed rather than refused,
and a stub without a hierarchy is invalid wasm wherever a cast touches it.

## What the web build refuses

Crypto is refused by stand-ins that throw `UnsupportedOperationException`
naming the method, and so is HMAC, signatures and key agreement. A page that
reaches one gets an error rather than a wrong answer made up in the browser.
What the web does instead is goal 1 step 2's business, and the TLS checking is
goal 3's.

The web build starts no processes: `ProcessHandle` answers "there is only this
one" and `ExecutableFile.markRunnable` answers true without touching the file.
`CALL` is refused for the same reason, under step 2.

## What is left

- Gzipped sizes, which is what a page downloads, and the obfuscated module's
  size beside the jar's in `goals.md`.
- Boot measured on the shipped entry point rather than the trial harness (the
  entry runs the script once and prints no timings; the harness that measured
  boot five times a page is gone with the scratchpad).
- Goal 1, steps 1 to 5: the worker, the browser's own adapters, one drawing
  engine in the domain, the examples with no server, and a build that ships.
