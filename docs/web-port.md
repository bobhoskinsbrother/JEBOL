# The web port

Goal 1 in `goals.md`: one interpreter and one drawing engine on every platform,
the web first, with the interpreter compiled to WebAssembly. This note carries
what the code cannot. `CLAUDE.md` carries the method.

---

## Where it stands

The core compiles to WebAssembly with TeaVM 0.16.0 and runs in a real browser,
inside the page's worker. The build is JEBOL's own: `./gradlew buildWasmGC`
writes `jebol-web.wasm` and `jebol-web-runtime.js` into
`build/generated/teavm/wasm-gc` (with `jebol-worker.js`, the shell the worker
runs), and `./gradlew browserCheck` depends on that task and then drives a
page in Chrome.

The pieces:

| Piece | Where |
| --- | --- |
| The web's main class, the run loop in the worker | `src/teavm/java/org/jebol/adapter/web/TheInterpreterInAWorker`, booting on `main` and awaiting scripts the page hands over |
| The worker's shell | `src/teavm/js/jebol-worker.js`, copied beside the module by the `copyTheWorkerScript` task |
| The web's substitutions and the library-file list | `src/teavm/java` and `src/teavm/resources`, wired in `build.gradle.kts` |
| The library files TeaVM must embed | listed by the `writeTheWebResourceList` task from `src/main/resources/org/jebol`, so the list cannot drift |
| The page and its scripts | `src/test/resources/web/rebol-on-a-page.html`, two Rebol tags -- the Brotli script and a GUI script -- a canvas, and the renderer copied from `WebScreenPage`; `rebol-adapters-on-a-page.html` adds an input element and a script that fetches, keeps a file and reads a typed line |
| The unit level | `AScriptInAPageTagFromTheSourceTest` runs the first tag's script on the JVM and pins its eight printed lines; `AFilesystemInTheWorkersMemoryTest` and `ThePageConsoleFromTheSourceTest` cover the browser's files and console |
| The end-to-end level | `AScriptInAPageTagRunsInABrowserEndToEndTest` serves the build and the page, drives Chrome, and checks the eight lines, the canvas, a click, and a resize; `TheBrowserAdaptersEndToEndTest` fetches from the test's own server, writes and reads a file, types into the console element, and catches the refusals |

Measured so far: the WebAssembly module is 13.7 MB unobfuscated beside a 38 KB
runtime loader (the worker's shell is a few KB more); the trial below measured
a boot in Chrome of 477 ms going cold and 164 to 201 ms warm. The shipped
form, obfuscated and compressed, is goal 1 step 5's business.

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

The worker closes the gap this note used to carry: a page script that waits
now prints its later lines, and the worker posts a `finished` message when the
run is really over, which is what lets the browser gate hold a script that
waits. The waiting happens inside main's fiber -- see decision 28 in
`docs/decisions.md` for why it has to.

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

**A page has less outside it than a machine, and the worker says so once.**
`TheInterpreterInAWorker` builds the browser's bounds: console, files,
windows and web are granted; network, processes, environment and working
directory are `lacking`, so a script that reaches for a raw connection or
another program reads `not_present` with the browser named rather than
`not_granted`. That is decision 31 in `docs/decisions.md`, and
`ABrowserHostFromTheSourceTest` pins the sentences.

**HTTP is the browser's own.** `WebHttp` (`src/teavm`) answers the `HttpPort`
the worker supplies, and `read http://...` goes through `fetch` -- names,
redirects and certificates are the browser's, and the fiber suspends on the
promise while it is in flight. Decision 32 has the shape; a page that fetches
outside its own origin is subject to the browser's CORS rules like any other
page.

**Files are the worker's memory and the console is the page's element** -
`WebFiles` and `WebConsole` (`org.jebol.adapter.web`). Nothing survives a
reload; decision 33 says why, and what would change it.

**Crypto is refused by stand-ins that throw, and the refusals are ordinary
errors.** The stand-ins name the method they cannot compute, and the domain
catches them and raises `feature-na`, so `checksum "abc" 'sha1` and
`random/secure` are values a script can catch. What the web supplies instead
is decided in decision 34: digests and HMACs from WebCrypto, the rest
refused. The TLS checking is goal 3's.

The web build starts no processes: `ProcessHandle` answers "there is only this
one" and `ExecutableFile.markRunnable` answers true without touching the file.

The worker measures no text: `TextMeasures` is substituted whole to
`WebTextMeasures`, which refuses with the same no-service a screen without a
display gives. A page's canvas knows its own fonts and the interpreter cannot
reach it synchronously from where it runs; the bundled font and the reader
belong to the drawing engine, step 3.

## What is left

- Files that survive a reload: a port over the browser's private store for
  the site, which is asynchronous, so every call would suspend the fiber.
- The WebCrypto digests decision 34 records: a port behind the domain's
  hashing, reached asynchronously the way HTTP is.
- Gzipped sizes, which is what a page downloads, and the obfuscated module's
  size beside the jar's in `goals.md`.
- Boot measured on the shipped entry point rather than the trial harness (the
  entry runs the script once and prints no timings; the harness that measured
  boot five times a page is gone with the scratchpad).
- Goal 1, steps 3 to 5: one drawing engine in the domain, the examples with
  no server, and a build that ships.
