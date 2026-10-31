# The web port

One interpreter and one way of drawing on every platform, the web first, with
the interpreter compiled to WebAssembly. This was the first goal in
`goals.md` and it is finished, so the goal is deleted and this note is the
record. It carries what the code cannot; `CLAUDE.md` carries the method.

---

## Where it stands

The core compiles to WebAssembly with TeaVM 0.16.0 and runs in a real browser,
inside the page's worker. The build is JEBOL's own: `./gradlew buildWasmGC`
writes `jebol-web.wasm` and `jebol-web-runtime.js` into
`build/generated/teavm/wasm-gc` (with `jebol-worker.js`, the shell the worker
runs), and `./gradlew endToEndTests` depends on that task and then drives a
page in Chrome.

The pieces:

| Piece | Where |
| --- | --- |
| The web's main class, the run loop in the worker | `src/teavm/java/org/jebol/adapter/web/WorkerInterpreter`, booting on `main` and awaiting scripts the page hands over |
| The worker's shell | `src/teavm/js/jebol-worker.js`, copied beside the module by the `copyWorkerScript` task |
| The page's half of an ask | `src/main/resources/jebol-page/jebol-asks.js`, copied beside the module by `copyPageScripts` and served by the command line's page from the jar |
| The page's half of the history | `src/main/resources/jebol-page/jebol-navigation.js`, copied and served the same way: it reports the address at load and each popstate, carries out `push`, `replace`, `back` and `forward`, holds the unsaved-work flag for `beforeunload`, reports the close on `pagehide` and reloads a page restored from the back-forward cache (decision 50) |
| The replayer of the surface's calls | `src/main/resources/jebol-page/jebol-paint.js`, one `JebolPainter` over any canvas 2D context, copied and served the same way and loaded by three places: the runner page, the command line's page, and the worker, which paints a gob into an OffscreenCanvas over white for `to image!` |
| The web's substitutions and the library-file list | `src/teavm/java` and `src/teavm/resources`, wired in `build.gradle.kts` |
| The library files TeaVM must embed | listed by the `writeTheWebResourceList` task from `src/main/resources/org/jebol`, so the list cannot drift |
| The page and its scripts | `site/runner.html` is the runner every site is built around -- canvas, `jebol-paint.js` for the surface's calls, and the worker hookup -- fill in with an app's file list and entry; the browser tests serve it as it ships through `ShippedRunner`, and `src/testFixtures/resources/web/rebol-on-a-page.html` is the one gate page of its own |
| The site a host serves | `assembleSite` copies the app (`examples/r3-gui` by default, `-PsiteDir=my-app`), the module, its loader, the worker's shell and the font with its licence into `build/site`, and writes `index.html` from `site/runner.html` around the app's file list; `TheExamplesWithNoServerEndToEndTest` serves that directory and nothing else, so the gate drives the artifact itself |
| The unit level | `AScriptInAPageTagFromTheSourceTest` runs the first tag's script on the JVM and pins its eight printed lines; `AFilesystemInTheWorkersMemoryTest` and `ThePageConsoleFromTheSourceTest` cover the browser's files and console |
| The end-to-end level | `AScriptInAPageTagRunsInABrowserEndToEndTest` serves the build and the page, drives Chrome, and checks the eight lines, the canvas, a click, and a resize; `TheBrowserAdaptersEndToEndTest` fetches from the test's own server, writes and reads a file, types into the console element, and catches the refusals; `TheExamplesWithNoServerEndToEndTest` drives the assembled site -- a real drag changes the slider, a real wheel turn changes the scroller, and a real click and real keys put a name in the survey's field |

Measured on 2026-10-12, `gzip -9`: the module is 13.7 MB unobfuscated and 4.1 MB
gzipped, the runtime loader 38 KB and 8.6 KB, the worker's shell 5.5 KB and
1.7 KB, the carried font 556 KB and 277 KB, the page with its examples 90 KB and
26 KB -- the whole site 14.3 MB, 4.4 MB gzipped, which is what a page downloads
to run its first example. Obfuscation is deliberately off: the TeaVM faults
below are read from the names in a stack trace. `goals.md` carries these
figures.

The trial below measured a boot in Chrome of 477 ms going cold and 164 to 201 ms
warm. The shipped page now prints its own boot -- page start to the interpreter
ready, worker spawn, module fetch and compile included -- and in the gate's
headless Chrome it reported 598 ms: the module's fetch and compile 69 ms of
it, and the interpreter started inside the worker 471 ms, about 6.3x the JVM's
warm create.

The two pieces of blocking code named in this note are gone. WAIT's sleep is a
port (`PausePort`, in `org.jebol.domain.host`) that the interpreter asks one
fifty millisecond slice at a time, so it can still end a wait at its own
deadline between slices; the standard ports carry a `ThreadPause` and a host
that has its own supplies it when the interpreter is built. And `ScreenEventQueue` is plain
storage again: an adapter that hears a toolkit's listener leaves the event in a
`ConcurrentLinkedQueue` through `GobScreen.posted`, and `acceptNextEvent`
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
`RemoveAction`, `EncodingNative` and `ParityAction`, each now text with
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
`ShippedPageBounds` builds the browser's bounds: console, files, a working
directory, the clock, windows, the clipboard, MIDI and web are granted,
because each of those the page can supply; network, processes, environment
and certificates are `unavailable`, so a
script that reaches for a raw connection or another program reads
`not_present` with the browser named rather than `not_granted`. That is
decision 31 in `docs/decisions.md`, and `ABrowserHostFromTheSourceTest` pins
the sentences.

**HTTP is the browser's own.** `WebHttp` (`src/teavm`) answers the `HttpPort`
the worker supplies, and `read http://...` goes through `fetch` -- names,
redirects and certificates are the browser's, and the run waits on the
promise while it is in flight, which is the pause-and-resume decision 28
describes. Decision 32 has the shape; a page that fetches outside its own
origin is subject to the browser's CORS rules like any other page.

**Files are the page's private store for the site** - `WebFiles` and
`BrowserStore` (`org.jebol.adapter.web`). The whole store is loaded once at
boot and every change is awaited into it, so a reload finds what a script
wrote; a browser without OPFS falls back to memory. Decision 33.

**The console's line is typed into JEBOL's own form on the screen.**
`WebConsole` says it is typed on the screen, so INPUT draws the same Rebol
form REQUEST-PASSWORD uses, and the page holds a hidden text input under it
for the keyboard and input methods. Decision 49.

**The page answers the rest itself, over one round trip.** Each service is an
adapter in `src/main` over `PageRoundTrip`, with the page's half in
`jebol-asks.js` and the worker's transport in `WorkerRoundTrip`. Decision 49
has the design; where each landed:

| Service | Adapter | The browser's part |
| --- | --- | --- |
| BROWSE, REQUEST-FILE, REQUEST-DIR, REQUEST-COLOR | `PageWindows`, with `PickedFiles` copying picks into `%/picked/N/` and sending saves when finished | a prompt the operator clicks, then `window.open`, a file input, a `webkitdirectory` input, a colour input with a hex field beside it, `showSaveFilePicker` or a download |
| REQUEST-PASSWORD and INPUT | `ScreenForm` running `forms/screen-form.reb` | the hidden input `#jebol-typing` |
| The clipboard | `PageClipboard` | `navigator.clipboard`, the read behind the prompt |
| POST and the write dialect | the write arm of `HttpSchemeActor` | `fetch`, as for READ |
| Pictures | `BrowserImageCodec` over `BrowserImages` | `createImageBitmap` with no premultiplying or colour conversion, and `OffscreenCanvas.convertToBlob` |
| MIDI | `PageMidiDevices` | `requestMIDIAccess` on the page; what an input hears is posted back as `midi-arrived` |

What a browser cannot do is a catchable error at the moment it is used: dds
always, tiff outside Safari, writing gif, bmp or tiff anywhere. A png, gif or
bmp decodes to the same pixels a real 3.22.5 reads from the same file, in
Chrome and in Firefox; a jpeg decodes to the browser's own reading, and
`PicturesOnAPageEndToEndTest` holds it as a literal. Only the first frame of a
picture is read.

**Crypto is the browser's own where it can be, and refused where it cannot.**
`DigestPort` (`org.jebol.domain.host`) is the port; the worker supplies
`BrowserDigests` over `crypto.subtle`, so SHA-1, SHA-256, SHA-384, SHA-512 and
HMAC over them are the browser's answers and everything else -- `md5`,
`sha224`, the SHA-3 sizes -- is absent from the catalogue and refused by
name. JEBOL's own Java digests work everywhere, and so do its ciphers. The
browser gate asserts the worker's SHA-1 and HMAC equal the JVM's. Decision 34;
the TLS checking is decision 43's.

The web build starts no processes: `ProcessHandle` answers "there is only this
one" and `ExecutableFile.markRunnable` answers true without touching the file.

The worker measures text with the font this build carries: the bytes are read
through the carried-files port and `DefaultFontRenderer` reads the metrics, so
`DefaultFont` is the one measure every screen answers with and SIZE-TEXT,
wrapping and carets are the domain's and the same on every platform. The page
draws its glyphs with the same face, loaded from the file the build serves.
Decisions 33 and 35.

What replaced the trial's scratchpad harness: the shipped page reports its own
boot in the output panel, and `TheExamplesWithNoServerEndToEndTest` fails if
that line is missing, so the shipped entry point is where the timing comes
from.
