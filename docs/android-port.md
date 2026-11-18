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
| The app | `android/app`, `org.jebol.android`: `JebolActivity`, `JebolView` (the `OneSurfaceScreen.Viewer`), `AppSession`, and `examples/r3-gui` carried as Java resources under `jebol-app/` with an `app.json` |
| The device suites | `androidUnitTests` and `androidFunctionalTests` are the app's `androidTest`, told apart by package (`org.jebol.android.unit` against the rest); `androidEndToEndTests` is `android/endtoend`, a self-instrumenting `com.android.test` APK that drives the installed app with UI Automator. All run under Jupiter (android-junit5); `TheAndroidPlatform` is the `PlatformUnderTest` the core's tests find through `ServiceLoader` on a device |
| The emulators | `android/emulator.sh` creates a Pixel Tablet AVD (1280 by 800 dp) from the API 35 image for the machine's processor, boots N read-only instances with no window and waits for `sys.boot_completed`; two by default |
| The shards | `androidUnitTests` and `androidFunctionalTests` each install both APKs on every device and run `am instrument -r` with `numShards`/`shardIndex` on all at once, writing `TEST-shard-i.xml`; `androidEndToEndTests` runs on the first device alone, since UI tests share a screen |
| A phone | `-Pdevice=<serial>` runs the suites on a phone over USB and starts no emulator; `scripts/android-on-my-phone.sh` installs the app, measures it and writes `build/phone-timings.txt` |
| What a device cannot run | `not-on-a-device.txt` (the core's tests, by class or method) and `not-on-a-device-in-rebols-suite.txt` (Rebol's, by file, group or test), each entry with why |
| The API check | `scripts/AndroidApiGaps.java`, run by `./gradlew checkCoreForAndroid` with `--fail-on-gaps` over the core's jar |
| The SDK | `scripts/setup-machine.sh` installs it in `~/Android/Sdk` on Ubuntu and `~/Library/Android/sdk` on a Mac; the root build finds it there, or by `ANDROID_HOME`, and takes `android/` in (decision 73). Checked on Ubuntu only: the Mac's branch and `scripts/check-android-build.sh` there were left unrun when goal 2b closed (owner, 2026-11-17) |

Measured on 2026-11-16: the app's debug APK is 6,195 KB and the test APK,
which dexes every core test and fixture, 37,674 KB. The first interpreter on
ART boots in about 1.2 seconds; a cold emulator boot under KVM takes about 19.

## The device run

The core's functional tests and Rebol's suite run on ART with the same cases
as the JVM, apart from those listed with why. On 2026-11-16, after the Bulk
compressors left the core (decision 74): **25,601 cases, none failed, 74
skipped**, on two emulators in 1 hour 9 minutes, build and boots included.

Against the JVM's 26,834 the difference is all named:

| Cases | Why they do not run on a device |
| --- | --- |
| 1,139 | `EvaluationCorpusTest` reads `corpus/`, which only the repository has |
| 63 | Rebol's suite withholds what `not-on-a-device-in-rebols-suite.txt` names: programs, picture codecs, creation times, the launcher |
| 21 | `WhatEachRemainingNativeDeclaresTest$AccessOs` compares with `ProcessHandle`, which Android lacks |
| 13 | `ReadingHttpFromALocalServerFromTheSourceTest` and `TheThruCacheFunctionsFromTheSourceTest` serve from the JDK's `com.sun.net.httpserver` |
| 1 | `ScreenAndGobTreeAgreeProperties`: jqwik's engine reads generics through `Class.getAnnotatedSuperclass`, which ART lacks |

The device also runs three cases of its own (`TheCoreBootsOnArtTest`), and
reports the one `exhausts-the-heap` case as skipped, which the JVM runs in its
own fork.

Sharding is by class, so the shard that takes `RebolSuiteTest` takes 9,820 of
its 19,308 cases in that one class; the two shards still ran 66 and 64 minutes
of tests, because Rebol's assertions are quick. Three emulators ran the same
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
restart's own two tests, is 16 tests in 67.8 s. The phone's before and after
are still to come: it needs Stay awake on.

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
and the survey's opens off the screen's right edge, which is goal 2d's.

## The optional core on Android

R3's six Bulk compressors are optional parts of the core (decision 74), and
Android carries none: the APK takes the core, which is R3's Core product.
Their tests moved to `optional/`, so the device suite lost them, Brotli's
levels 10 and 11 among them, which had been 25 of 165 emulator-minutes.
Rebol's suite on the device takes the branch a Core build takes.
