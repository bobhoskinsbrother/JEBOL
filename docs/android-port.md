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
| The host | `android/host`: `AndroidHost` and `AndroidMachine`, which gives the process number; it runs no programs and has no picture codec until 2c |
| The device suites | `android/suites`, a test application whose instrumentation tests run under Jupiter (android-junit5); `TheAndroidPlatform` is the `PlatformUnderTest` the core's tests find through `ServiceLoader` on a device |
| The emulators | `android/emulator.sh` creates the AVD from the installed x86_64 API 35 image, boots N read-only instances with no window and waits for `sys.boot_completed` |
| The shards | `deviceShards` installs both APKs on each emulator and runs `am instrument -r` with `numShards`/`shardIndex` on all at once, writing `TEST-shard-i.xml` |
| What a device cannot run | `not-on-a-device.txt` (the core's tests, by class or method) and `not-on-a-device-in-rebols-suite.txt` (Rebol's, by file, group or test), each entry with why |
| The API check | `scripts/AndroidApiGaps.java`, run by `./gradlew checkCoreForAndroid` with `--fail-on-gaps` over the core's jar |
| The SDK | `scripts/setup-machine.sh` installs it in `~/Android/Sdk`; `ANDROID_HOME` must point there |

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

## The optional core on Android

R3's six Bulk compressors are optional parts of the core (decision 74), and
Android carries none: the APK takes the core, which is R3's Core product.
Their tests moved to `optional/`, so the device suite lost them, Brotli's
levels 10 and 11 among them, which had been 25 of 165 emulator-minutes.
Rebol's suite on the device takes the branch a Core build takes.
