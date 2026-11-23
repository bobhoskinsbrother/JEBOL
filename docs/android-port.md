# The Android port

The interpreter running on Android's own Java, ART. Goal 2a -- the core runs
on ART -- is finished, so its text is gone from `goals.md` and this note is
the record of it. The later stages (2b onwards: the app, Android's adapters,
touch) add to it. It carries what the code cannot; `CLAUDE.md` carries the
method and decision 73 the choices.

---

## Where it stands

The core every platform carries runs on Android 35 (API 35, Java 21 as the
language level). `./gradlew -p android androidTests` builds the app and test
APKs, boots emulators, runs the core's functional tests and Rebol's own suite
on ART in shards, and archives the results into
`test-history/<timestamp>-android/`.

| Piece | Where |
| --- | --- |
| The build | `android/`, a Gradle build of its own that takes the core through `includeBuild("..")`, so the root build and `allTests` need no Android SDK (decision 73) |
| The host | `android/host`: `AndroidHost` and `AndroidMachine`, which gives the process number; the screen's adapters -- `AndroidSurface`, `AndroidPixelSurface`, `Density`, `AndroidKeys`, `AndroidHistory` -- and `LogcatOutput`; it runs no programs and has no picture codec until 2c |
| The runtime | `android/runtime`, a library (namespace `org.jebol.android.runtime`) over `android/host`: `JebolActivity`, `JebolView` (the `OneSurfaceScreen.Viewer`), `AppSession` and `CarriedApp`. Every app is built on it: JEBOL's own, and each one `jebol-devtools` packages, which carries its release AAR (goal 2e, decision 78) |
| The app | `android/app`, `org.jebol.android`, JEBOL's own app over the runtime, with `examples/r3-gui` carried as Java resources under `jebol-app/` with an `app.json` |
| SQLite | `android/sqlite`: `SqliteOnAndroid`, the extension's `android` setup, and `AndroidSqliteEngine`, SQLite's statement interface over `android.database.sqlite` (decision 78). SQLite's own functional tests run in `androidFunctionalTests` on it |
| An app of one's own | `examples/an-app-of-ones-own`, packaged by `jebol-devtools` in the end-to-end suite and installed beside JEBOL's own app; `androidPackagingTests` runs the plugin's Android packaging tests through the core build |
| The device suites | `androidUnitTests` and `androidFunctionalTests` are the app's `androidTest`, told apart by package (`org.jebol.android.unit` against the rest); `androidEndToEndTests` is `android/endtoend`, a self-instrumenting `com.android.test` APK that drives the installed app with UI Automator. All run under Jupiter (android-junit5); `TheAndroidPlatform` is the `PlatformUnderTest` the core's tests find through `ServiceLoader` on a device |
| The emulators | `android/emulator.sh` creates a Pixel Tablet AVD (1280 by 800 dp) from the API 35 image and a Pixel 7 phone AVD (412 by 915 dp) from the API 37 image, for the machine's processor, boots N read-only instances with no window, the phone second, and waits for `sys.boot_completed`; two by default |
| The shards | `androidUnitTests` and `androidFunctionalTests` each install both APKs on every device and run `am instrument -r` with `numShards`/`shardIndex` on all at once, writing `TEST-shard-i.xml`; `androidEndToEndTests` runs every test on each device (`DeviceShards.everyTestOnEachDevice`). Classes named by `-Pclasses` are dealt out whole, one device's share each, with no `numShards`: given a class and a shard, the runner splits the class's tests, and a device whose share of a class is no test at all reports it as `initializationError: No test methods found` |
| A phone | `-Pdevice=<serial>,<serial>` runs the suites on phones over USB, sharded across them, and starts no emulator; `scripts/android-on-my-phone.sh` installs the app, measures it and writes `build/phone-timings.txt` |
| What a device cannot run | `not-on-a-device.txt` (the core's tests, by class or method) and `not-on-a-device-in-rebols-suite.txt` (Rebol's, by file, group or test), each entry with why |
| The API check | `scripts/AndroidApiGaps.java`, run by `./gradlew checkCoreForAndroid` with `--fail-on-gaps` over the core's jar |
| The SDK | `scripts/setup-machine.sh` installs it in `~/Android/Sdk` on Ubuntu and `~/Library/Android/sdk` on a Mac; the root build finds it there, or by `ANDROID_HOME`, and takes `android/` in (decision 73). Checked on Ubuntu only: the Mac's branch and `scripts/check-android-build.sh` there were left unrun when goal 2b closed (owner, 2026-11-17) |

Measured on 2026-11-18: the app's debug APK is 6,017 KB and the test APK,
which dexes every core test and fixture, 47,742 KB. The first interpreter on
ART boots in about 1.2 seconds; a cold emulator boot under KVM takes about 19.

## The device run

The core's functional tests and Rebol's suite run on ART with the same cases
as the JVM, apart from those listed with why. On 2026-11-18, with Android's
ports in (decision 77): **25,734 cases, none failed, 42 skipped**, on two
emulators in 1 hour 13 minutes, build, boots and the unit and end-to-end
suites included.

Against the JVM's 26,902 the difference is all named:

| Cases | Why they do not run on a device |
| --- | --- |
| 1,139 | `EvaluationCorpusTest` reads `corpus/`, which only the repository has |
| 25 | Rebol's suite withholds what `not-on-a-device-in-rebols-suite.txt` names: programs, creation times, the launcher |
| 21 | `WhatEachRemainingNativeDeclaresTest$AccessOs` compares with `ProcessHandle`, which Android lacks |
| 13 | `ReadingHttpFromALocalServerFromTheSourceTest` and `TheThruCacheFunctionsFromTheSourceTest` serve from the JDK's `com.sun.net.httpserver` |
| 1 | `ScreenAndGobTreeAgreeProperties`: jqwik's engine reads generics through `Class.getAnnotatedSuperclass`, which ART lacks |

The device also runs thirty cases of its own: `TheCoreBootsOnArtTest`'s
three, `AndroidPortsThroughRebolTest`'s fourteen, and `AndroidScreenTest`'s
and `ParityWithJava2DTest`'s thirteen; and it
reports the one `exhausts-the-heap` case as skipped, which the JVM runs in its
own fork.

Sharding is by test, not by class, so `RebolSuiteTest`'s 19,389 cases are
split between the shards, 9,859 to one of them; the two shards finish close
together, because Rebol's assertions are quick. Three emulators ran the same
suite in 63 minutes on 2026-11-15, and four do not fit beside the IDE: each
holds about 3.6 GB beyond what Android is given. On a machine running
IntelliJ, two leave room; `-Pemulators=N` sets how many.

## The gaps, and how each closed

Measured from the class files, since `javac` will not compile against
`android.jar` past level 8: `AndroidApiGaps` lists every platform class and
member the core references that Android 35 lacks, through supertypes, and
names apart the Java 21 bootstraps D8 rewrites away (none survives in the
dex). Each gap was closed on the JVM side, so every platform runs the same
code:

- `ByteBuffer` and `CharBuffer`'s covariant `flip` (Java 9): `Buffers.flip`
  takes a `Buffer`.
- Virtual threads and `Thread.Builder`: `DaemonThreads` makes named daemon
  platform threads for sockets, processes and tasks. Platform threads showed
  a race virtual threads had hidden -- a read queued before its own peer's
  write -- now waited out for at most a second (rebol-findings 251).
- `SSLEngineResult.HandshakeStatus.NEED_UNWRAP_AGAIN`, DTLS-only: no longer
  named.
- `ProcessHandle`: the JVM's machine moved to `adapter.host`; Android's
  `AndroidMachine` answers the process number its own way.
- What remained after those was the JVM host's, the desktop's and the command
  line's own code, which the APK does not carry: the desktop and the web
  became projects of their own (decision 73), and the core names neither.

The probe now reports nothing for the core's jar, and the check fails the
build if that changes.

## What the device found

Running on ART found faults the JVM had hidden, each fixed on the JVM side
with a test:

- `to binary!` of an image widened every byte and copied it twice more, and
  overran an image at an offset: a 33 MB image needed over 192 MB.
- `mold/part` copied a whole binary to keep eight bytes of it.
- The suite's withholding split names holding a slash wrongly, ran a withheld
  test's setup, and counted a withheld assertion as expected to pass.
- Tests' servers bound `getLoopbackAddress()`, which Android answers `::1`.
- Tests that leaned on the JVM: `System.gc()`, a `HOME` variable,
  SoftAssertions' generated classes, jqwik's engine.

Hang guards in the tests' interpreters stretch ten times on the emulator
(`PlatformUnderTest.timeLimitFactor`), since ART under emulation is slower
than the JVM on the same machine.

## The app (goal 2b)

`examples/r3-gui` runs in the app on the tablet emulator, drawn by the
domain's paint list on Android's `Canvas` and driven by touch and the
keyboard, as the site's gate drives the page. Decision 75 has the choices;
`spec/android.allium` the rules.

**A phone is a page.** The app's screen is the core's `OneSurfaceScreen`, the
page's one-surface screen moved into the core, with `JebolView` as its viewer
and `AndroidHistory` as its `NavigationPort`. `JebolView` is one `View`
filling the activity inside the system bars: `paint` keeps the newest command
list and asks for a redraw, and `onDraw` scales by the density and replays it
through `AndroidSurface` -- the interpreter, on a daemon thread of its own,
never touches the view. Sizes are dp, rounded down; `screen-dpi` is 160 times
the density.

**`AndroidSurface` executes each command as `Java2DSurface` does**, read
beside it method for method. Two things ART lacks shaped it: the Android
modules are compiled against `android.jar`, which has neither
`SwitchBootstraps` nor `MatchException`, so a switch over the sealed commands
is an `instanceof` chain and a switch over cap and join is a table; and Java2D
applies a composite's opacity to each primitive, so Android's comes on each
`Paint` rather than a layer. Arcs pass their angles straight through: Java2D's
negated degrees in a y-up frame are Canvas's clockwise ones.

**Parity is checked against references painted ahead.** Java2D is not on a
device, so `Java2DParityReferencesFromTheSourceTest` (desktop) paints the four
shape scenes at 420x260 and holds them against
`src/testFixtures/resources/parity/*.rgba`, and `ParityWithJava2DTest` on the
device paints the same scenes with `AndroidPixelSurface` and compares by
`PixelAgreement`, decision 35's bound. All four agree; the three text scenes
put their ink where the page's test says.

**The end-to-end suite drives the app from its own process.** A
`com.android.test` APK runs in the app's process by default, so a test that
force-stops the app ended itself; `android.experimental.self-instrumenting`
gives it its own, as Macrobenchmark's are, and a `<queries>` entry lets it see
the app. A debug build runs the script in its launch intent's `script` extra;
the tests print markers to logcat and read them back.

**A test restarts the app rather than launching it** (decision 75). On the
emulator (2026-11-18, one Pixel Tablet AVD), the fourteen tests the suite had
before took 69.9 s with a cold launch each, and 52.7 s restarting in place.
A test that only restarts went from 4 to 7 s down to 2 to 4 s. The process-death
test still launches cold and takes about 9 s. The whole suite, with the
restart's own two tests, is 16 tests in 67.8 s. On the owner's phone
(2026-11-18), the twelve tests both runs share went from 111.8 s to 90.0 s.
One of them is the survey's field, which fails on a phone and waits out its
minute either way (goal 2d's). Without it, eleven tests went from 47.5 s to
28.4 s. The phone needs Stay awake on, which `adb shell svc power stayon usb`
sets. The restart's test asks whether the last window's colour is left on
the screen, not whether the view is blank, because a Samsung draws its Edge
panel handle over every app.

**How quick it is, on the emulator** (2026-11-16, a Pixel Tablet AVD at
2560x1600, x86_64 under KVM with the software GPU; `AndroidTimingsTest`, ten
of each, the comparison for the owner's phone):

| What | Median | 95th percentile |
| --- | --- | --- |
| cold start, `am start -W`'s TotalTime | 415 ms | 455 ms |
| cold start, asked to the first picture drawn | 2,978 ms | 3,029 ms |
| a tap on the slider to the end of its repaint | 45 ms | 61 ms |
| a frame while the slider is dragged | 48 ms | 81 ms |

The first picture waits on the interpreter's boot and the GUI's loading on
ART, which is most of the three seconds; a frame on the emulator is the
software renderer filling 4 million pixels. The phone's own figures are the
judgement (goal 2b).

**How quick it is, on a phone** (2026-11-17, the owner's Samsung Galaxy S24
Ultra, SM-S928B, Android 16, API 36, arm64, 1080x2340 at density 450; the
same `AndroidTimingsTest`, through `scripts/android-on-my-phone.sh`):

| What | Median | 95th percentile |
| --- | --- | --- |
| cold start, `am start -W`'s TotalTime | 215 ms | 228 ms |
| cold start, asked to the first picture drawn | 2,819 ms | 2,848 ms |
| a tap on the slider to the end of its repaint | 77 ms | 97 ms |
| a frame while the slider is dragged | 49 ms | 87 ms |

The owner used it by hand and called it "pretty quick": snappy enough, so
the phone part of goal 2b closed without a profile. A tap answers inside the
100 ms that reads as instant; a drag runs at about twenty frames a second,
which is goal 9's to raise. On that phone the sliders' window fits upright
and the survey's opened off the screen's right edge until goal 2d.

## Touch, and every screen (goal 2d)

Decision 79 has the choices, `spec/screen.allium` and `spec/android.allium`
the rules, and `using-jebol.md` what a script sees.

**A finger is touch.** `JebolView` reports a pointer whose tool type is
anything but `TOOL_TYPE_MOUSE` as `ScreenEventDetail.Touched`, so a finger
and a stylus say `touch` in `event/flags` and a mouse plugged into the
device does not. An unknown tool type is a finger: on a touchscreen that is
what it is.

**The keyboard rises for a field alone.** `JebolView` is a text editor only
while the keyboard is wanted, so a tap on a slider raises nothing. The 2010
GUI's FOCUS calls `show-soft-keyboard/attach` with the field's gob, which
takes the typing. The activity stays `adjustNothing`, so the screen keeps its
size: the frame's insets listener hands the keyboard's height (the IME's
bottom inset less the system bars') to the screen, and the screen moves the
picture up just enough to show the attached gob above it, moving every
pointer down by as much. A touch outside the gob lowers the keyboard.

**Two emulators, a tablet and a phone.** `android/emulator.sh` boots the
Pixel Tablet on API 35, the app's least, as the first, and a Pixel 7 (412 by
915 dp) on API 37 as the second. The phone is on 37 for its Chrome: API 35's
image carries Chrome 124, which refuses the web build's WebAssembly
(`exnref`, which Chrome has had since 137), and API 37's carries 145. On the
tablet the Chrome test skips itself, saying so. The
unit and functional suites shard across both as before, and the end-to-end
suite runs every test on each (`DeviceShards.everyTestOnEachDevice`), so the
examples are driven at both sizes. `TheSiteInChromeEndToEndTest` opens the
shipped site in the image's own Chrome, served from the host by `HostSite`
at 10.0.2.2, with Chrome's first-run screens passed by its command-line file
and gesture navigation switched on, and a swipe from the screen's left edge
moves the site's history. The site's app moves to its second place on a tap,
never as it loads: Chrome's back skips a history entry a page pushed without
a gesture of the user's, so a `go` at load leaves the page on the first swipe.

**The emulators keep their writes on disk, in `android/build/emulator-tmp/`.**
A `-read-only` instance writes everything it changes to a qcow2 overlay in
`ANDROID_TMP`, which defaults to `/tmp`, and on Ubuntu `/tmp` is a tmpfs with
a per-user quota: memory, not disk. One booted tablet holds 880 MB there and
grows with every byte the device writes, so two emulators through the
functional suites ran the machine short of memory once and filled `/tmp`'s
quota the next time, failing `startEmulators` (2026-11-19).
`android/emulator.sh` points `ANDROID_TMP` at the folder on disk, writes the
emulators' logs there, and empties it when it starts with no JEBOL emulator
running, which clears the overlays of one that was killed rather than
stopped. The test forks do the same for the same reason: `java.io.tmpdir` and
`TMPDIR` are `/var/tmp/jebol-<user>/<task>/`, which is disk on Linux and on a
Mac, emptied as the task starts, so the browsers Selenium starts keep their
profiles there too. Two things fix where that folder can be. It must be
outside the repository, because the command line's tests make a file "outside"
a script's folder in it and expect the jar, started at the root, to refuse it.
And it must be short: Chrome makes its `SingletonSocket` in
`$TMPDIR/.com.google.Chrome.XXXXXX/`, and a Unix socket's path holds 107
characters, so under `build/tmp/test-forks/extensions-sqlite-endToEndTests/`
Chrome could not start.

## The optional core on Android

R3's six Bulk compressors are optional parts of the core (decision 74), and
Android carries none: the APK takes the core, which is R3's Core product.
Their tests moved to `optional/`, so the device suite lost them, Brotli's
levels 10 and 11 among them, which had been 25 of 165 emulator-minutes.
Rebol's suite on the device takes the branch a Core build takes.

## Ports (goal 2c)

Every port in `HostPorts` has Android's answer, put together by
`AndroidHost.appPorts` from a `Context`. Decision 77 has the owner's choices;
`spec/android.allium`, "The ports", the rules.

| Port | Android's | Where it differs from the desktop, and why |
| --- | --- | --- |
| files | `MountedFiles`: `%/picked/` mounted on `DocumentFiles` (goal 2c2, below), over `LayeredFiles`: a `FileSystemPort` rooted at `files/jebol` in the app's private storage, laid over `ListedFiles`, the APK's `jebol-app/` files read in place | Past the app's storage, the script reaches only what the user picked. A carried file cannot be deleted or renamed (`read-only`); deleting the store's own file of a carried name shows the carried one again |
| carried files, digests, machine | `ClasspathFiles`, `JvmDigests`, `AndroidMachine`, as before | |
| environment | `AndroidEnvironment`: `HOME` and `PWD` `/`, `TMPDIR` `/tmp`, `LANG` the device's locale as `en_GB.UTF-8`, `USER` and `LOGNAME` the app's identifier | Nothing of the device's process environment (`PATH`, `ANDROID_DATA`, `BOOTCLASSPATH`) is told. `TMPDIR` is in the store, not the cache, because the cache is outside what a script can reach; the app empties it as it starts |
| images | `AndroidImage`: `BitmapFactory` decodes PNG, JPEG, BMP, WebP and HEIF in each picture's own colour space, and the stored bytes are copied out with `copyPixelsToBuffer`; `Bitmap.compress` writes PNG, JPEG (quality 75) and lossless WebP; the core's `BitmapWriter` and GIF codec do BMP and GIF both ways | No TIFF, which Android cannot read; WebP and HEIF, which the desktop cannot, reached through `image/load/as` and `image/save/as`, since Rebol's library registers no codec for WebP and HEIF's on macOS alone. Only GIF has frames past the first |
| console | none: printing is logcat (decision 77) | |
| screen | 2b's `OneSurfaceScreen` | |
| windows | `AndroidWindows` (goal 2c2, below) | |
| pause | `WakeablePause` | |
| processes | `ProcessPort.none()`, PROCESSES marked unavailable | CALL refuses as a page's does, and `system/options/boot` is none |
| network | `JavaSocket` with `AndroidHostNames` (`android.icu.text.IDNA`), asking `NetworkSecurityPolicy` before plain http | Plain `http://` is refused where Android's policy refuses it: everywhere in a release build, everywhere but `10.0.2.2` and `localhost` in a debug one. `tcp://` and `https://` are never asked |
| certificate authority | `JavaCertificateAuthority`, the system's store through `TrustManagerFactory`; a debug build adds the user's (`debug-overrides`) and, for one launch, roots given in its `trust` extra | No `--cafile` |
| secure transport | `JavaSecureTransport`, on Conscrypt | |
| http | none: `http://` is `prot-http.reb` over the network port | |
| clipboard | `AndroidClipboard` on `ClipboardManager` | Read while the app is not in front, it answers none: Android gives the clipboard only to the focused app |
| MIDI, web | none, marked unavailable | |
| tasks | `ThreadTasks`, each task given the app's own ports with the store's working directory copied | |

**A PNG's colours are read as stored.** Asked for sRGB, `BitmapFactory`
applies a PNG's `gAMA` chunk, and `Bitmap.getPixels` converts to sRGB
whatever it is asked, so Rebol's `flower.png` came out with other colours
than ImageIO and a real R3 read, and Rebol's BLUR and IMAGE-DIFF assertions
failed on the device. Decoded in the picture's own colour space and copied
out as stored, it reads as on the desktop; `AndroidImageTest` holds the PNG
to the same flower's BMP, byte for byte.

**The HEIF the test decodes is checked in, made once on an emulator.**
`AndroidImageTest` used to make its HEIF on the device with the first HEVC
encoder Android offered. An emulator offers its software encoder, which takes
a 64 by 64 frame; a real phone may offer its own hardware one, and one
refused to start, so the test failed before it decoded anything. Now it decodes
`resources/heif/android-made-64x64.heic` (522 bytes, its length and MD5
asserted), the same bytes on every device. `TheHeifFixtureIsMadeOnAnEmulatorTest`
is what made it, in a package the functional suite leaves out: run it by
name on an emulator (`-Pclasses=org.jebol.android.fixtures.TheHeifFixtureIsMadeOnAnEmulatorTest`)
and read the file back with `adb exec-out run-as org.jebol.android cat
files/android-made-64x64.heic`.

**Memory.** The app's bounds hold a ceiling of three quarters of the heap
Android gives it (`Runtime.maxMemory()`, which follows `largeHeap`). Every
thousand steps the evaluator compares the heap in use with it, collects once
when it is over, and raises `no-memory` when it is still over, so a script
that grows too large gets REBOL's own error and the app lives on rather than
being ended by an `OutOfMemoryError` on some other thread. A collection's
answer is trusted for a quarter of a second, so a script making garbage just
under the ceiling does not collect on every check.

## The user's own files, and the dialogs (goal 2c2)

Decision 80 has the owner's choices, and `spec/android.allium`, "The user's
own files and the dialogs", the rules.

**The pieces.**
- `AndroidWindows` is the app's `WindowPort`. It asks the activity through
  `ActivityRequests`, which starts a picker for its result, or shows a
  dialog, on the main thread, and holds the interpreter's thread until the
  answer comes. A restart in place, or the activity ending, answers every
  open request with none, so no interpreter is left waiting on a picker.
- `PickedDocuments` is the table from N to reference, one tab-separated line
  a pick, in `files/picked-documents.txt`. That is beside the store, not in
  it, so no script reads it. It is read again on every question, so a task,
  or the next interpreter after a restart, sees each pick at once.
- `DocumentFiles` is the `FilePort` over the references. It walks a path
  under a picked folder a name at a time through `DocumentsContract`'s child
  queries, and reads and writes through `ContentResolver.openFileDescriptor`:
  `wt` for WRITE, truncated again where the descriptor is a file, `rw` for a
  write at a position, and `wa` for an append.
- `MountedFiles`, in the core, sends `%/picked/` to it and the rest to the
  store. It keeps a working directory of its own while one is inside the
  mount.
- `BrowsedFiles` is the host library's read-only `ContentProvider`, at
  `<application id>.browsed`. `browse` of a file that is not picked copies
  it into the cache's `browsed/N/` and offers it there; the copies go when
  the next session opens.

**A grant Android lets go.** Android keeps 512 persisted grants for an app,
and past that drops the oldest (AOSP's `UriGrantsManagerService`, read, not
tried: it takes 513 picks through the picker to try). A pick whose grant is
gone, whether dropped, taken back by clearing the app's storage, or deleted
from its provider, names nothing, and READ raises `cannot-open`.
`PickedDocumentsThroughRebolTest` covers that path with a document deleted
under its pick.

**Every refusal is the desktop's.** A provider throws whatever it likes:
`SecurityException`, `UnsupportedOperationException`, `FileNotFoundException`
or `IllegalStateException`. `DocumentFiles` turns each into the id a desktop
gives for the same thing. That is `cannot-open` for a read or a write,
`no-delete` for a delete, and `cannot-open` for a rename. MAKE-DIR's
`cannot-open` is turned into `no-create` by Rebol's own MAKE-DIR, as on the
desktop. A folder is never deleted while it holds anything, since
`deleteDocument` would delete it whole.

**`request-password` was answered already.** The plan said all five dialogs
refused. `request-password` did not: wherever the screen has a display, the
core asks through the screen form it draws itself (decision 49), so on a
phone it was a form in the app's own picture. Its field was not a password
field to the keyboard. `WindowPort.hasOwnPasswordDialog` lets a platform say
its own dialog comes first. Android says so; every other platform keeps the
form.

**The tests.** `PickedDocumentsTest`, `PickerFilterTest` and `HexColourTest`
are unit tests. `PickedDocumentsThroughRebolTest` runs REBOL against
`TestDocuments`, a `DocumentsProvider` in the debug build, over the cache's
`test-documents/` folder. A name containing `read-only` refuses every change,
and one starting `unsized` leaves its size and date empty. The provider is
the debug build's because one must be protected by `MANAGE_DOCUMENTS`, which
only the system holds. So only the app's own identity can call it without a
grant from the picker. It also shows in the picker of a debug build, as
"JEBOL test documents". `ThePickerEndToEndTest`,
`TheDialogsEndToEndTest` and `BrowseEndToEndTest` drive the real
DocumentsUI and the dialogs with UI Automator, on files under
`Download/jebol-e2e/`. That folder holds nothing else, because a phone's own
Download folder is too long to find a file in by scrolling.

### The GIF codec

Android decodes an animated GIF only as a drawable and writes no GIF at all,
so the core carries its own (decision 77), written from the GIF89a
specification: `GifDecoder`, `LzwDecoder`, `GifEncoder`, `LzwEncoder` and
`MedianCutQuantiser` in `org.jebol.domain.image`. The desktop's `JavaImage`
takes it too, both ways, so a GIF is the same picture on every platform.

A frame is the frames before it composed by their disposal methods, on a
canvas the logical screen's size: 0 and 1 leave a frame in place, 2 clears its
rectangle to transparent, 3 puts back what was there before it. A pixel no
frame has painted is `0.0.0.0`. `JavaImage` had drawn each frame over the last
whatever its disposal, and given the first frame's transparent pixels their
palette colour.

A picture of 256 colours or fewer is written against its own palette, in the
order its colours are first met, as `JavaImage` wrote it. Above 256 it is
quantised by median cut, the box with the widest channel times its pixels
split at its weighted median, and mapped back with Floyd-Steinberg
dithering; every tie breaks by the colour's own value, so ART and the JVM
write the same bytes (`QuantisedGif.MD5`, asserted on both). The owner chose
it on the phone from the sheets `scripts/GifQuantiserComparison.java` draws.
A GIF is written opaque: the alpha is dropped and the colour kept.

The reference is ImageIO, through
`TheCoresGifCodecAgreesWithImageIoTest` (desktop), which decodes every frame
of every fixture both ways. Its fixtures are built by `HandBuiltGif`, whose
own LZW encoder reports what it did, so each fixture is checked to be what
it says. The boundaries, and what covers each:

| Boundary | Covered by |
| --- | --- |
| LZW minimum code size 2, 3, 4, 5, 6, 7, 8 | `eachCodeSize`, each asserting the code-size byte written |
| the code width growing to 12 | `eachCodeSize`, asserting the widest code written is 12 |
| a clear code mid-stream | `aClearCodeMidStream`, asserting a clear after the first |
| the table full at 4,096 with no clear after it | `aFullTableWithNoClear`, asserting over 100 codes written with the table full |
| interlaced, 1 to 5, 7 to 9, 16 and 17 rows | `interlacedFramesPutEachRowBack`, against the rows written; ImageIO from 5 rows up |
| not interlaced | every other fixture |
| a global palette | `eachCodeSize` and most others |
| a local palette alone | `aLocalPaletteAlone` |
| a local palette over a global one, for its frame only | `aLocalPaletteOverTheGlobal` |
| neither palette | `neitherPalette`: black, the specification leaving it to the decoder |
| transparency, on the first frame and a later one | `transparency` |
| disposal 0 and 1 | `offsetFramesUnderDisposalNoneAndKeep`, `disposalKeep` |
| disposal 2 | `disposalRestoresTheBackground`, by hand |
| disposal 3 | `disposalRestoresThePrevious`, by hand |
| a frame smaller than the screen, offset within it | `offsetFramesUnderDisposalNoneAndKeep` |
| one frame, two, a hundred | `frameCounts` |
| a frame past the last | every `assertEveryFrameAgrees` |
| bytes that are not a GIF, or stop short | `notAGif` |
| Rebol's own flower, rbgw and animation | `rebolsOwnGifs`; the animation's three frames by MD5 in Rebol's suite and `AndroidImageTest` |
| 1, 2, 3, 255 and 256 colours written | `ownPaletteRoundTrips` (desktop), `ownPaletteReadsBackExactly` (core) |
| more than 256 colours written | `manyColoursAreQuantised`, `quantisingIsTheSameEveryTime` |
| a picture spanning many LZW tables | `aLargePicture` |

**ImageIO misreads an interlaced frame under five rows high.** Given a GIF of
one column and three rows, interlaced, whose rows are red, green and blue,
ImageIO's reader answers red, red, green; Pillow and the core answer red,
green, blue. `imageIoMisreadsShortInterlacedFrames` keeps the evidence, and
the desktop no longer meets it, since it no longer decodes GIF with ImageIO.

### Host names on Android

`AndroidHostNames` runs ICU's UTS #46 without transitional processing,
checking Bidi and ContextJ and leaving off the checks a browser leaves off
(hyphens, DNS lengths, empty labels), as `Uts46` does. Every host-name case
`HostNameCases` holds answers alike on both. On Unicode's conformance file
for version 16.0.0, 86 of its 6,389 cases differ on Android 35's ICU 75:
`android/app/src/androidTest/resources/icu-differs-from-uts46-icu75.txt` lists
each with both answers, and `AndroidHostNamesTest` asserts exactly the list
for the device's own ICU major version, failing if none has been recorded for
it. Android 37's ICU 78 differs in three cases alone
(`icu-differs-from-uts46-icu78.txt`): two are one name ICU accepts with a
digit after a dot and the file refuses, and one is `xn--9-i0j5967eg3qz.ss`,
likewise. Android 35's ICU is older than the 16.0 file. 84 cases are names ICU refuses
and the file converts: nearly all hold a Georgian capital (Ⴀ to Ⴭ), which the
file maps to its small letter, or ≠, ≮, ≯ or `?`, which the file lets through
with STD3 rules off. The other two, `xn--xn--a--gua.pt` and `xn--xn---epa`,
are names ICU accepts as they are and the file refuses. A refusal is the
cautious answer: a name ICU refuses fails to resolve, and no name resolves
to a host it should not.

`Uts46` and its 55 KB of tables left the core for the desktop, so the APK no
longer carries them.
