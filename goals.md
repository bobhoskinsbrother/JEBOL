# Goals

What is left to do on this port, in one order. A finished goal, or a finished
part of one, is deleted; what it said is in the git history.

Nothing else goes here. What has been decided is in `docs/decisions.md`, where
the port stands and where it differs on purpose is in `docs/standing.md`, and
how to work on any goal is in `docs/porting-guide.md`.

---

## The goals

Ordered by importance. The two that take JEBOL to Android and iOS come
first, with the defects that would hurt there fixed before each stage.
Agreeing with the C comes before improving on it: a place where JEBOL answers
something a real 3.22.5 does not is a defect.

### 2. Android, with the interpreter running on Android's own Java

Owner's decisions 2026-11-14, decision 73: API 35 at the least; the
project's level is Java 21; Android draws the one way every platform draws
(decision 35); the app is an `android/` build in this repo, in Java; Android
has unit, functional and end-to-end suites as the other platforms do, run
by an `androidTests` gate.

The stages left, in this order, from 2c; 2b, the app, is done (decision 75,
`docs/android-port.md`), and so is the end-to-end suite's restart in place
that came after it. Each is planned in full -- what is true today,
spec, failing tests, code, docs -- in its own plan commit before work on it
starts, as 1l was; what follows is each stage's reach, its done-when and the
questions its plan has to settle. A defect that would hurt a stage is fixed
on the JVM before that stage, not on Android after it.

**The suites, from 2a on.** `android/` holds three source sets that match the
JVM's: `androidUnitTests` (Android's adapters on their own, no interpreter),
`androidFunctionalTests` (an interpreter on ART with Android's ports, running
REBOL, Rebol's suite included) and `androidEndToEndTests` (the installed app
driven from outside, by UI Automator). All three run on the device, because
ART and Android's own classes are what they test. `androidTests` runs them
in shards across x86_64 emulators under KVM that `android/emulator.sh` boots
and stops (decision 73), and archives into `test-history/` as `allTests`
does. It is not part of `allTests`: it needs the Android SDK, which not every
machine has. Run it at the close of every Android stage and after any change
to the core.

#### 2c. Android's adapters

It starts from the phone's figures in `docs/android-port.md`, "How quick it
is, on a phone": a Galaxy S24 Ultra, 2,819 ms to the first picture, 77 ms
tap to repaint, 49 ms a frame while dragging.

Every port in `HostPorts` gets Android's answer, and the app is given them.
After this stage a script in the app can read and write its own files, open
`tcp://`, `tls://` and `http(s)://`, load and save pictures and use the
clipboard. Each port Android cannot supply refuses the way a page's does,
never with a host exception (`NoFailureEscapesAsAHostException`).

**Who does what.** All of it is the session's, on the emulators. The owner
has already answered the plan's questions (step 1). The owner's phone stays
plugged into this machine for the closing run.

**What is true on 2026-11-17, before any step below.**

- `AndroidHost.ports()` (`android/host`) gives four ports: the carried files
  (`ClasspathFiles`), digests (`JvmDigests`), the machine (`AndroidMachine`)
  and the pause (`ThreadPause`). `AppSession.open` adds `MemoryFiles` filled
  from the APK by `CarriedApp`, so whatever a script writes is lost when the
  process ends. `AppSession.appBounds()` grants files, the working
  directory, the clock, windows and navigation. It marks clipboard, MIDI,
  web, network, processes, environment and certificates unavailable.
- The desktop's ports are put together in `Repl.hostServicesFor`
  (`desktop/.../cli/Repl.java`). Several of the adapters it uses already live
  in the core, and `checkCoreForAndroid` finds nothing in them that Android
  35 lacks:
  - `FileSystemPort`, the confined filesystem, with `findScriptPath` for
    decision 69's home;
  - `JavaSocket`, the network, whose threads are platform daemon threads
    (`DaemonThreads`), not virtual ones;
  - `JavaSecureTransport` (TLS on `SSLEngine`), `JavaCertificateAuthority`
    and `JavaTrustAnchors`;
  - `ProcessEnvironment` and `ConfinedEnvironment`;
  - `ThreadTasks` and `WakeablePause`;
  - `Uts46`, IDNA, which `JavaSocket` uses for host names. It is 489 lines,
    plus a 55 KB `Uts46Tables`.

  The adapters that live in `desktop/` are `JavaImage` (ImageIO, Java2D),
  `JavaClipboard` (AWT), `JavaProcess` and `JavaMachine`.
- On ART, `JavaTrustAnchors.jdksOwn()` asks `TrustManagerFactory` for the
  default store. On Android that is the system's CA store, which is the
  trust decision 65 wants. Its `platformsOwn()` reads `/etc/ssl` bundles that
  Android does not have, and finds none.
- The device's functional suite already drives TLS through
  `JavaSecureTransport` on Conscrypt, Android's own TLS: those tests pass on
  the emulators. One is kept off the device because BoringSSL will not serve
  a chain out of order.
- `android/app/src/androidTest/resources/not-on-a-device.txt` lists 31
  tests, and `not-on-a-device-in-rebols-suite.txt` 49 suite entries.
  - Of the 31, five are 2c's: three need a picture codec, one starts a
    program, one signals a process.
  - Among the suite entries, the groups headed "Until 2c, Android has no
    picture codec" and "system/options/boot ... until 2c" are 2c's.
  - The rest stay off the device for reasons 2c does not change: CALL, the
    repository's files, `com.sun.net.httpserver`, `ProcessHandle`, ArchUnit.
- `TheAndroidPlatform` (the device's `PlatformUnderTest`) answers
  `ImagePort.none()` and `ProcessPort.none()`.
- The manifest already asks for `android.permission.INTERNET`.

**The ports, and what each becomes.**

| Port | Android's, after 2c |
| --- | --- |
| files | `FileSystemPort.rootFileSystem` at a folder in `Context.getFilesDir()`, so the app's own storage, private to it, is the script's whole world. Home is the root `/`, by decision 69's rule. The APK's carried files are read in place beneath it, through a layered `FilePort` in the core (decision 77). |
| carried files | `ClasspathFiles`, as now |
| digests | `JvmDigests`, as now |
| environment | A new `AndroidEnvironment`: a standard desktop configuration's variables, wrapped to the app's own places, and none of the device's (decision 77) |
| images | A new `AndroidImage`. It decodes with `ImageDecoder`/`BitmapFactory`: PNG, JPEG, GIF, BMP, and WebP and HEIF besides. It encodes PNG, JPEG and WebP with `Bitmap.compress`. BMP is written by hand as `JavaImage` writes it, the writer moved to the core so both share it. GIF, every frame and encoding, goes through a GIF codec in the core (decision 77). |
| console | None. Printing goes to logcat, as 2b made it. |
| windows, screen | 2b's `OneSurfaceScreen`, as now |
| pause | `WakeablePause`, so a network event wakes WAIT as on the desktop |
| processes | `ProcessPort.none()`. CALL refuses as a page's does, and `system/options/boot` is none. |
| machine | `AndroidMachine`, as now |
| network | `JavaSocket`, given Android's host names (`android.icu.text.IDNA`) where the desktop's is given `Uts46`. Plain `http://` is refused in the port wherever Android's policy refuses it (decision 77; step 3). |
| certificate authority | `JavaCertificateAuthority`, the system's CA store. A release build trusts the system's roots alone, Android's own default. The debug build's network security config adds the user's (`<debug-overrides>`), so a developer can watch the app's HTTPS through a proxy of their own (decision 77). |
| secure transport | `JavaSecureTransport`, which on Android is Conscrypt |
| http | Empty, as on the desktop: `http://` goes through `prot-http.reb` over the network port |
| clipboard | A new `AndroidClipboard` on `ClipboardManager`. Android 10 and later give the clipboard only to the app in focus, so a read in the background answers none. Android 13 and later show their own notice on a copy. |
| MIDI | None, as on a page |
| tasks | `ThreadTasks`, with a factory that builds each task's ports as the app's own |

**The steps, in order.**

1. **The owner's answers, all given on 2026-11-17 and recorded in decision
   77 ("Android's ports").** Nothing is left to ask before the plan commit.
   The plan writes down only the details each answer leaves to it:
   - *Printing on the screen* is settled: logcat only, nothing on the
     screen (decision 77, the owner, 2026-11-17).
   - *The carried files* are settled (decision 77, the owner, 2026-11-17).
     They stay in the APK and are read in place, beneath the app's storage.
     The core gets a `FilePort` that lays a writable port over a read-only
     one:
     - a read finds the store's file first, then the APK's;
     - a write goes to the store, so a script's own `main.reb` shadows the
       carried one;
     - a directory lists both;
     - deleting a carried file is refused, as the APK's own files cannot be
       deleted.

     An app update brings its new files with it, and nothing is copied.
     Whether the page's store takes the same layering is the plan's to look
     at, not to do.
   - *The environment* is settled (decision 77, the owner, 2026-11-17). It
     answers what a standard desktop configuration answers, wrapped to mean
     the app's own places, through an `AndroidEnvironment` adapter of
     `EnvironmentPort`. The device's own process environment (`PATH`,
     `ANDROID_DATA`, `BOOTCLASSPATH` and the rest) never reaches a script.
     The plan writes down the list, and the starting point is:
     - `HOME`: the root `/`, so `sys-start.reb`'s `get-env "HOME"` agrees
       with `system/options/home` as decision 69 asks;
     - `TMPDIR`: a folder in the app's cache, as the script sees it;
     - `LANG`: the device's locale, as `en_GB.UTF-8`;
     - `USER` and `LOGNAME`: a name the plan picks;
     - `SHELL` and `PATH`: absent, since nothing can be started.

     A `set-env` is the script's own and lasts as long as the process. The
     list is checked against what Rebol's suite and `sys-start.reb` read, and
     against `ConfinedEnvironment`, which the adapter reuses where it can.
   - *GIF* is settled: every frame decodes and GIF encodes on Android, as on
     the desktop (decision 77, the owner, 2026-11-17). Android has no API
     for either, since `ImageDecoder` gives an animated GIF as a drawable and
     not as frames, so the core carries a GIF codec of its own (step 3).
   - *IDNA* is settled: Android uses its own `android.icu.text.IDNA`, and
     `Uts46` becomes the desktop's (decision 77, the owner, 2026-11-17).
     Step 3 does the work.
   - *Settled on 2026-11-18* (decision 77): a GIF's frames compose by their
     disposal methods on every platform; above 256 colours the quantiser is
     median cut with Floyd-Steinberg dithering; USER and LOGNAME are the
     app's identifier; TMPDIR is `/tmp` in the store, emptied at start; the
     memory ceiling is checked by the evaluator; plain http is asked in
     `TcpPort` when the parent port is http's. The stage is committed once,
     after both gates and the phone, beside the plan commit.

2. **The spec.** A section of `spec/android.allium` for the ports, one rule
   a port, in the shape of its existing rules:
   - the store is private and survives the process;
   - home is `/`;
   - the carried files read beneath the store;
   - a picture decodes and encodes in each format the table names, and is
     refused in the others;
   - the clipboard answers none from the background;
   - CALL refuses by the name a page's uses;
   - a socket and a TLS connection reach a local server;
   - the trust is the system's.

   Commit the spec and this goal's plan as the stage's plan commit
   (`Plan goal 2c: Android's ports`), as 2b's step 0 did.

3. **Failing tests, then the adapters, one port at a time.** Files and the
   environment first, because the rest write files. Then pictures, the
   clipboard, the network and TLS together, tasks last. Each port gets:
   - an `androidUnitTests` test of the adapter alone, in
     `org.jebol.android.unit`;
   - an `androidFunctionalTests` test through REBOL, an interpreter on the
     app's ports, in `org.jebol.android.functional`;
   - its rule in the spec.

   **The GIF codec** (decision 77) is the core's own Java, written from the
   GIF89a specification. Nothing is ported from a copyleft decoder
   (licensing is strict). The CLAUDE.md rules for byte-exact ports apply:
   - *The reference is built first.* The reference is `JavaImage`. A desktop
     test decodes every frame of every GIF in a set through both and asserts
     the same pixels.
   - *The set is a list of boundaries, not a count of files.* Each of these
     is listed with the file that covers it, or `(none)`:
     - LZW minimum code sizes 2 to 8, the code width growing to 12, a clear
       code, and the table full at 4,096 with no clear after it;
     - interlaced and not;
     - a global palette, a local one, and neither;
     - transparency;
     - disposal methods 0 to 3, a frame smaller than the screen and offset
       within it;
     - one frame, two, and a hundred.
   - *Decoding composes frames by their disposal methods* (decision 77,
     2026-11-18), so `JavaImage` is the reference for the first frame and for
     frames whose earlier frames dispose by 0 or 1; a frame after a 2 or a 3
     is checked against pixels worked out by hand from the specification.
   - *Encoding answers the same when decoded back.* A picture of 256 colours
     or fewer is written against its own palette as `JavaImage` writes it,
     and decodes back to the same pixels. Above 256, `JavaImage` leaves the
     choice to ImageIO's own quantiser, which a core codec cannot match. So
     the desktop's `JavaImage` moves to the core codec for GIF too, and one
     quantiser, chosen and documented in `docs/android-port.md`, answers on
     every platform. The web's GIF follows if it has one of its own.

   **Host names** (decision 77). IDNA becomes a port, an interface in
   `org.jebol.domain.host` that turns a host name into its ASCII form or
   refuses it, and `JavaSocket` takes one where it calls
   `Uts46.toAsciiHostName` today, which is its only caller.
   - `Uts46`, `Uts46Tables` and `Uts46Test` move to `desktop/`, and the
     desktop's ports give `JavaSocket` the `Uts46` one.
   - Android gets an `AndroidHostNames` on `android.icu.text.IDNA`, with
     UTS #46's nontransitional processing and the same flags `Uts46`
     follows.
   - `Uts46Test`'s cases become a fixture both platforms run: the desktop
     against `Uts46`, and an `androidUnitTests` test against ICU. A case
     where ICU answers differently is listed in `docs/android-port.md`
     with both answers. Check that `checkCoreForAndroid` no longer sees
     `Uts46`, and record what the APK loses.

   **Plain http** (decision 77). Android's cleartext policy is enforced by
   its own HTTP stacks, not by sockets. JEBOL's `http://` is `prot-http.reb`
   over a raw `tcp://` socket, so Android would let it through unasked.
   - The network port gains a question, whether a host may be reached in
     the clear, which the http scheme asks before it opens. `https://` does
     not ask.
   - Android answers from `NetworkSecurityPolicy.isCleartextTrafficPermitted(host)`,
     which since API 28 is no for every host unless the manifest says
     otherwise. The desktop and the page answer yes.
   - A refusal is a REBOL error that names Android's policy, and is never a
     host exception.
   - A raw `tcp://` is not HTTP, and Android does not restrict it, so it is
     not asked.
   - The debug build's network security config allows cleartext to
     `10.0.2.2` and `localhost` alone, so step 6's end-to-end test can reach
     the host. The release build allows none.

   The adapters go in `android/host`, beside `AndroidMachine`. Anything
   moved to the core, such as the BMP writer or the layered `FilePort`, keeps
   its JVM tests passing and gains one in the core's own suites. Try a fix
   on the device in minutes with
   `-Pclasses=org.jebol.android.functional.TheClass`.

4. **The app takes them.** `AndroidHost.ports()` builds the full set from a
   `Context`. `AppSession` stops building `MemoryFiles`.
   `AppSession.appBounds()` grants what is now supplied, the same services
   the command line grants but processes, MIDI and the web. Give `TheAndroidPlatform` the same ports, so the
   core's own functional tests run on them on the device.

   **Memory** (the owner, 2026-11-17). An app's heap is fixed: a few hundred
   MB (`ActivityManager.getMemoryClass()`, or `getLargeMemoryClass()` with
   `largeHeap`), far below a desktop JVM's.
   - An `OutOfMemoryError` nobody catches does not end one thread. Android's
     default handler ends the whole process, so the app vanishes.
   - In the background, Android's low-memory killer ends the process
     whatever its heap holds.
   - So the app's `Bounds` take a memory limit from the device's memory
     class, low enough that a script which grows too large gets REBOL's own
     out-of-memory error and the app lives. The plan reads how the core
     bounds memory today and what `heapExhaustionTests` proves on the JVM,
     and measures the margin on the emulator.
   - An `androidFunctionalTests` test grows a block until it fails, and
     asserts the REBOL error and a process still alive.

5. **Bring the tests back.** Remove from the two `not-on-a-device` lists
   every entry 2c answers: the picture codec's, `system/options/boot`'s, and
   any other whose reason is now gone. Read each reason, and do not just
   delete the "until 2c" lines. An entry that still cannot run gets its
   reason rewritten to say why.

6. **End to end.** Two new tests in `android/endtoend`:
   - "what a script saves is there after Android ends the app": write a
     file, end the process, relaunch, read it back;
   - "a script reaches a server on the host": a local server the test
     starts, reached from the emulator at `10.0.2.2`, over `tcp://`, and
     over `tls://` with a root the test supplies.

   The second runs on the emulator only, and is skipped by assumption on a
   phone, where 10.0.2.2 is not this machine.

7. **The docs.** `docs/android-port.md` gets a "Ports" section: the table
   above as built, and what differs from the desktop with each reason.
   `docs/standing.md` lists each difference: the clipboard in the
   background, and CALL. `using-jebol.md` says what an Android app's script may
   reach.

8. **The gates, then the phone.** Run `./gradlew allTests` for anything
   moved into the core, then `./gradlew -p android androidTests`. Then run
   `scripts/android-on-my-phone.sh --with-tests` on the owner's phone, and
   ask the owner to try a script that saves a file and reaches the network.
   Commit after both gates pass.

**Done when** each port in the table has an `android.allium` rule, an
`androidUnitTests` test of its adapter and an `androidFunctionalTests` test
through REBOL; the app is given every port in the table; the `not-on-a-device`
lists hold nothing whose reason was 2c; both new end-to-end tests pass on
the emulator; and `androidTests` and `allTests` pass.

#### 2c2. The user's own files, through Android's picker

The owner, 2026-11-17. After 2c a script sees its own storage alone, as
Android's scoped storage allows. To reach a file the user chooses -- in
Downloads, Documents, a cloud drive -- an app asks Android's own picker:
- `ACTION_OPEN_DOCUMENT` and `ACTION_CREATE_DOCUMENT` for a file;
- `ACTION_OPEN_DOCUMENT_TREE` for a folder.

The picker is Android's UI, so nothing of the GUI is ported. What comes back
is a `content://` reference, read and written through `ContentResolver`, not
a path.

- `request-file` and `request-dir` answer through the picker. What they
  answer is a file the script can READ, WRITE and QUERY, and for a folder
  list and walk.
- The file port reaches those references, as the page's store reaches the
  files a browser's picker lends it. The page is the closest shape JEBOL
  already has, so its plan reads `PageWindows` and the page's file chooser
  first.
- A grant lasts as long as the app asks Android to keep it
  (`takePersistableUriPermission`). The plan settles whether a script's
  choice survives a restart.
- What the script sees as the file's name and path is the plan's to settle,
  with the C's `request-file` and Rebol's suite read first.

**Done when** a script opens, reads, writes and saves through the picker,
and walks a folder chosen with it. An end-to-end test drives the picker
with UI Automator on the emulator.

#### 2d. Touch in the 2010 GUI

A tap is a click; a drag scrolls; focusing a field brings up the keyboard;
layouts scale with the screen's size. Done once, in the GUI's REBOL --
`src/main/resources/org/jebol/modules/r3-gui.reb`, each edit listed in
ORDER.txt -- and iOS and the web on a phone use it too, so it is driven
in Chrome with touch emulation as well as on the emulator. The examples fit
a phone's screen, portrait and landscape, where 2b proved them on a tablet.
And Chrome on the emulator (an image that carries it; which one is this
stage's to find) opens the shipped site served from the host, and a swipe
from the screen's edge moves its history -- the web on a phone, moved here
from 2b, which is the app's.

Seen on a real phone, 2026-11-17: on a Galaxy S24 Ultra (SM-S928B, Android
16, 1080 by 2340 at density 450, so 384 dp wide upright) the sliders' window
fits, but the survey's window opens to its right, with its field at
905 dp across, off the screen. `TheExamplesInTheAppEndToEndTest`'s "letters
typed reach the survey's field" taps there and fails on that phone for that
reason alone; it passes on the 1280 dp tablet. Laying the windows out for
the screen is what makes it pass. Until then it skips itself on any screen
narrower than the survey's right edge (`isNarrowerThanTheSurvey`, 1085 dp).

**Done when** every example works by touch alone, on a phone-sized emulator
and in Chrome's phone emulation, and the edge swipe moves the site's history.
And the special case is gone: `isNarrowerThanTheSurvey` and its `@DisabledIf`
are deleted from `TheExamplesInTheAppEndToEndTest`, and "letters typed reach
the survey's field" runs and passes on every screen, the owner's phone
included.

#### 2e. An app of one's own

`./gradlew assembleApk -PappDir=my-app` is `assembleSite` for Android: the
app's files and its `app.json` entry, the extensions it names (decision 63)
inside the APK, signed. The gate drives `examples/r3-gui` in the APK it
built, as the browser gate drives the assembled site. `using-jebol.md` says
how.

**Done when** an APK built from a directory of REBOL installs and runs on
the emulator, and the gate drives that APK.

**Its plan settles:** the app's name, icon and identity, and where the
signing key comes from.

#### 2e2. Services: a script that keeps running when the app is not on screen

The owner, 2026-11-17: Android needs a way of handling services, because
nothing keeps working without one.
- When the app leaves the screen, Doze and the app-standby buckets defer
  its timers and cut its network.
- The process can be ended at any time. Today a script that polls a server,
  or waits on a long task, stops when the app is not in front. That never
  happens on the desktop.

Android's own answers:
- a *foreground service*: work the user is told about by a notification
  that stays while it runs. Since Android 14 each one must declare a type
  (`dataSync`, `mediaPlayback`, `location`, `specialUse`, ...), and since
  Android 13 the notification needs `POST_NOTIFICATIONS`;
- *WorkManager*, for work that may wait for the system to choose when, such
  as periodic or deferred jobs;
- *alarms*, for a moment that must be kept.

**Its plan settles:**
- what a script says to ask for each. Search Rebol's suite, the C and R3's
  ports for a shape to follow before inventing one, as 2f to 2h do;
- whether a service runs the app's own script, or a second one the app
  names;
- how a service and the app on screen share state, by decision 70's deep
  copy between interpreters;
- what the desktop and the page do with the same words. The desktop simply
  keeps running. The page has service workers.

The notification permission it needs is the same one 2h asks for, so the
order of 2e2 and 2h is this stage's to settle.

**Done when** a script can keep a timer and a connection alive with the
app off screen, under Doze on the emulator (`adb shell dumpsys deviceidle
force-idle`), with the notification shown. An end-to-end test drives it.

#### 2f, 2g, 2h. The camera, location, notifications

One extension each (`using-jebol.md`, section 4; decision 63), each its own
stage and plan: the REBOL it offers, Android's permission asked at the
moment of use, and where it can come to the web or the desktop too -- the
camera and location in a browser, notifications on both -- it comes there
as well. Each plan searches Rebol's suite and the C for a shape to follow
before inventing one.

### 3. iOS, with the interpreter compiled ahead of time by GraalVM

Not a web view: one would pause JEBOL whenever the app is in the background,
keep it out of widgets, the Watch and app extensions, and make every call to
iOS an asynchronous message with its data copied. Compiled to native code,
JEBOL runs under the same rules as any iOS app and calls iOS directly.

1. **A native image on the desktop first.** Build JEBOL with GraalVM Native
   Image on macOS and run the gate's examples. This proves ahead-of-time
   compiling works: the borrowed REBOL library files must be bundled as
   resources, and nothing may depend on code being loaded at run time.
2. **A minimal iOS app.** Gluon's tools for GraalVM on iOS: an app that
   prints `1 + 2` on a simulator, then on a phone. Signing and Xcode are part
   of this step.
3. **The app.** One view draws the domain's surface calls with iOS's own
   drawing; touches, the keyboard, and pausing and resuming are handled as
   on Android, using goal 2d's work in the 2010 GUI. iOS has no system
   back gesture, so the app recognises a swipe from the left edge as back
   and from the right edge as forward, the way a browser on the phone
   does (`spec/nav.allium`).
4. **iOS's adapters.** Files in the app's own sandbox and real sockets. CALL is
   refused: Apple does not allow an app to start another program.
5. **A way to call iOS itself.** JEBOL reaches iOS's own features -- the
   camera, location, notifications, background work -- through GraalVM's
   direct calls into native code, one extension per feature.
   Whether the same compiled JEBOL fits inside a widget or an extension,
   within the memory iOS allows them, is measured here.
6. **A gate on the simulator**, as on Android: the examples driven in the app,
   a gate of its own.

### 4. A real MIDI device through `javax.sound.midi`

The `midi` scheme is registered and its actor is `MidiSchemeActor`, behind
the `MidiDevices` host port; every interpreter starts with `NoMidiDevices`,
which lists nothing. What is left is an adapter behind `MidiDevices` over
`javax.sound.midi`, wired by the command line as the clipboard is. Input
arrives on another thread and reaches the port's `awake` through the
interpreter's postbox, as a `read` event; the C sends one for each message
that arrives. Opening a port that is already open raises `already-open`
rather than connecting twice (decision 71), and the adapter must keep that.

### 5. The type-major refactor: what is left

Each datatype's behaviour belongs in its own class, not in a switch elsewhere.
What still switches on the datatype, by `scripts/complexity.py`:

- **`Actions.of`** -- a switch over nine datatypes wrapping each value in a
  separate `*Actions` class (`BitsetActions`, `MapActions`, `BinaryActions`,
  `StringActions`, `BlockActions`, `ObjectActions`, `GobActions`,
  `ImageActions`, `VectorActions`). The behaviour moves onto the value classes
  and the switch goes.
- **The rest of the path seam.** `Evaluator.writeThroughTheLastSegment`
  (cc 46) and `Evaluator.selectWith` (cc 41) still chain through time, bitset,
  error, string, gob, struct, image, vector and block, and `BlockPath`,
  `GobPath`, `ImagePath`, `EventPath`, `StructPath` and `VectorPath` are statics
  taking the value as argument one. Each value should answer through
  `PathTarget`, as objects, maps, dates, pairs and tuples already do.
- **`Molder.renderOne`** (cc 47) -- per-datatype writing, which should become
  each value molding itself.

**Tooling to fix on the way:** `PortingBacklogTest` reads R3's function list
from source files and JEBOL's from what is a function at run time, so
`init-schemes` -- which sets its own name to `'done` once run, in both
interpreters -- reads as missing.

### 6. Debugging and tracing

**Before anything in JEBOL: a debugger on the reference.** Goal 1f's stack
drift (decision 66) was found from about forty black-box probes on
`./r3-head` and a reading of `Push_Func`. A watch on `DSP` across `Do_Next`'s
operator case would have shown it in one run. Have `scripts/build-r3.sh`
build a second `./r3-head-debug` with symbols and no optimisation, and check
in a gdb command file under `scripts/` that runs a one-line script and prints
`DSP`, `DSF` and the top cells at each `Do_Next`, `Push_Func` and `Do_Blk`
exit. A probe is never thrown away, so each question it answers is checked in
as a transcript beside the test that uses it.

**Then make `trace` agree with R3.** For `x: 1 + 2` R3 prints the operator
(`+ : op! [value1 value2]`), its call (`--> +`) and its result
(`<-- + == 3`); JEBOL prints none of the three and numbers the lines
differently. `trace/back`, `trace/function` and `stack` with its refinements are
unchecked.

**Then stepping and breakpoints**, which R3 does not have. Three things to
decide first: what drives it (the Debug Adapter Protocol, text here since
the jar takes no dependencies); what a paused script does to the deadline and
grants it runs under; and whether a script may debug itself. The question
moved here from `spec/eval.allium`: evaluation state lives on the heap, so a
running evaluation could be paused, resumed, or moved to another thread --
say which of those JEBOL promises, and at what step a script can be stopped.

**Next, with tracing to see what a boot builds: copy a booted interpreter
rather than boot one per test.** The gate builds about 16,300 interpreters,
and nearly all of each boot is evaluating the borrowed library to define the
same functions and objects every time. Caching the parsed library
(decision 39) and reading each file once only touch the parsing, which is
not where the time goes. Boot one interpreter per test process and hand each
test a deep copy of it. Measure the copy first and stop if it is not clearly
under about 15ms; if it is, every piece of interpreter state -- contexts,
functions, objects, and ports holding host resources -- must copy, proven by
isolation tests of the whole interpreter like `LibrarySourceIsolationTest`.
Compare the gate's time before and after against `test-history/`.

### 7. Dev tools

Two homes: the flag and the example go in JEBOL; the server and its tools are a
second Gradle build in this repo: one executable jar taking JEBOL's jar as its
one dependency, with a gate of its own rather than `allTests`.

1. **`--manual` prints `using-jebol.md` to standard output.** The file is
   copied into the jar's resources at build time. The same pass gives `--help`
   and `-V` their missing output: both are parsed today and neither is read.
2. **One worked dialect with a test**, stating that DELECT matches by type in
   any order where PARSE matches by position, and written so a rule that does
   not match raises rather than answering false and being dropped. This is the
   example an AI author copies.
3. **The dev-tools project.** An executable jar of its own, hand-rolled
   JSON-RPC over stdio, one long-lived interpreter session, so an editor or an
   AI tool starts it once and keeps talking to it.
4. **`run`.** Evaluate a script under a scope the call declares - granted
   services, file root, limits, nothing granted by default - and report every
   service the script asked for, so the caller learns everything it wanted
   rather than where it first failed.
5. **Every dialect's schema, generated from a booted interpreter.**
   `system/dialects` holds draw, effect and text, and each is written out with
   a checked-in file of descriptions beside it. Written for the AI to read: the
   types say what fits, the descriptions say what it does. The generator is a
   test in this project in `SurfaceReportTest`'s shape.
6. **`match`.** Where a block stopped against a dialect's grammar, built on the
   schema.
7. **`word`.** What a word takes and whether it is reliable here, meaning it
   answers as the C does; the declaration parity and the runtime parity's three
   deliberate differences are where reliable comes from.
8. **Hot reload.** The dev server serves and watches a script's files, the
   checksum is the change detector, and a changed file is re-parsed into the
   live session. The same reload serves the session, the browser page and the
   desktop app, since all three run one interpreter; nothing survives a reload
   at first.


### 8. What the command line does differently from r3

1. **`--trace` does not trace the boot.** Do with goal 6, the debugger.
2. **The version and build differ.** `Rebol/core 3.22.5` here against
   `Rebol/Bulk 3.22.5 (...)` and its copyright lines, and `system/build` is none
   in every field. The banner's title line is decided -- it says "(JEBOL
   port)" -- and the rest of what JEBOL claims is still a decision.
3. **A `quit/return 42` typed at the console leaves with nought.** R3 leaves
   with 42, because its console carries what QUIT held out to the process. The
   console loop here returns without it.
4. **`input` in a script run by the command line refuses.** `Repl.portsFor`
   grants CONSOLE and hands no `ConsolePort` over, so a script's read of a
   line meets `ConsolePort.none()`'s "this script was given no console to
   read", where a real r3 reads the terminal. One adapter over the command
   line's own input stream is the fix.

### 9. A drag still lags, and the interpreter has a next tier

The picture reaches the page 10 to 22ms after each pointer move, and a fast
drag settles at about 35 frames a second, which still reads as laggy. The
measurements are decision 36's. Three costs, in the order they bite: the
wait polls every ten milliseconds and that sets the floor; each paint walks
the whole surface and writes its JSON afresh -- 98KB for the two examples --
where only what has changed is ever looked at; and the page clears and
replays the whole canvas for each. The three actions for that, unchanged:
patch the paint list rather than rebuilding it, paint at most once a frame,
and drop a pointer move a later one has superseded.

If the drag still reads slow after the paint work, the interpreter's next
tier, from the profile:

1. **`BlockStorage.at(int)`**, 6 to 8 per cent of some workloads: every value
   read from a block pays bounds arithmetic and an indirection.
2. **The wasm worker's cold start**, about 460ms, which is the JVM's first
   create by another route. Not the parse: decision 39's pre-parsed library
   image left it unchanged, because building the values costs what scanning
   them did. What is left is constructing and evaluating the library on every
   boot, which only a boot image in the strong sense would skip.
3. **Rebol's own pre-make**, 2.98s on the jar against 0.16s on `./r3-head`
   (`scripts/compare-pre-make.py`, 2026-11-14). Three quadratic paths took it
   from 267s to there in goal 1m; no single cause is left. The largest share,
   about 12 per cent of samples, is whole-string copies through
   `AnyStringValue.text()` from PARSE and FIND over long strings, which a
   view onto the code points would avoid.

### 10. WebP as an image format

A real 3.22.5 has no WebP codec, so this goal improves on the C rather than
agreeing with it, and it comes after the agreement work for that reason. Every
browser reads and writes WebP, phones take pictures in it, and a script that
loads a picture from the web meets it often.

What it needs:

1. **The codec.** `codec-image.reb` is borrowed and stays as it is, so the
   `webp` codec -- suffix `.webp`, identified by `RIFF`, four bytes of length
   and `WEBP` -- is registered by a file of JEBOL's own, loaded after the
   borrowed one, decoding and encoding through `lib/image/load/as` and
   `lib/image/save/as` with `'WEBP` like the six beside it.
2. **The page.** Nothing beyond the page's `BrowserImages` (decision 49): `createImageBitmap`
   reads it and `OffscreenCanvas.convertToBlob` writes it.
3. **The desktop.** Java's own image library has no WebP, and the shipped jar
   takes no dependency, so the desktop needs a port: VP8L, the lossless form,
   and VP8, the lossy one, decoding first. It is byte-exact work and the rules
   in `CLAUDE.md` for a codec apply in full -- libwebp compiled into a probe
   before the port, the boundaries read from its C, and libwebp's own test
   images as the corpus. Whether the desktop also encodes, and in which form,
   is to be settled before the spec: the browser's encoder is lossy, and a
   lossless encoder alone is a much smaller port.
4. **Android and iOS**, when they land, use the system's decoder. Android's
   `BitmapFactory` reads it and `Bitmap.compress` writes it; iOS reads it from
   14 and has no encoder.

A host that cannot do one side refuses it the way a page refuses dds: a
catchable error naming the format and the host. The spec first, as everywhere:
the codec and each host's answer go in `spec/web.allium` and its desktop
counterpart before any test.

### 11. A url script's own files come from its url, and it cannot leave its root

A script given by url runs today in the directory the command was given in, so
`do %my-alt-vid.rb` inside `https://raw.github.com/myresources/main.rb` reads
a local `./my-alt-vid.rb` or nothing, and never the file beside the script.
That is the current contract and not an accident: `AScriptMayComeFromAUrl` in
`spec/repl.allium` says relative paths count from the command's directory, and
`do*`'s change-dir guard (`sys-base.reb:117`) fires only for a file.

What it should do instead: a relative path inside the script resolves against
the script's own url. `%my-alt-vid.rb` is
`https://raw.github.com/myresources/my-alt-vid.rb`, and every way a script
reaches another file takes it - `do`, `load`, `read`, `import` and a `needs:`
header naming a file.

**The root is fixed and may not be left.** The directory the first script was
fetched from is the root, and every fetch resolves under it: a relative path
may descend into the root, and one that climbs above it (`%../x.rb`) is
refused with a catchable error naming the url, the way a link leaving a host's
`--root` is refused (`ALinkLeavingTheRootIsRefused`, `spec/embed.allium`). A
script fetched under the root resolves its own relative paths against its own
url, still under the same root, so nothing a run fetches widens what it can
reach. An explicit url - `read https://other/x.r3` - is the script choosing,
as DO of a url is, and is left alone.

The spec changes first: `AScriptMayComeFromAUrl` gains the new answer, with
the root rule beside it.
`AScriptGivenByUrlAtTheCommandLineEndToEndTest` grows two cases through the
local TLS server it already builds: a relative include fetched from the
served tree, and a `%../` refused with nothing requested. `ORDER.txt`'s note
on `sys-start.reb` says a url has no directory to run in, and stops being true
the day this lands.

### 12. PDF as an optional extension

**The borrowed `codec-pdf.reb` stays as it is.** It reads a file into its
objects and writes them back, which is all a real 3.22.5 does, and `load` and
`save` answer the same with or without this goal's jars. What the codec cannot
do is say what a document contains: its text, or what a page looks like.

**An extension jar as SQLite's is (decision 63, `docs/writing-an-extension.md` section 11)**, `jebol-pdf`, built here
over Apache PDFBox, which no script sees by name. JEBOL's own jar never names
PDFBox.

**Without the jar the commands are still there and answer none,** unlike
SQLite's (decision 63). `pdf` in `system/modules` is already the codec's module,
so these commands join that module and the codec stays reachable through it
either way.

R3 has no such extension, so no C decides these answers and this goal does:

| Command                       | What it does                                        |
|-------------------------------|-----------------------------------------------------|
| `version`                     | the implementation's version                        |
| `open` `/password`            | a handle on a document, from a file or a binary     |
| `close`                       | releases the handle                                 |
| `page-count`                  | how many pages                                      |
| `read-text` `/pages`          | the document's text, or that of a range of pages    |
| `render-page` `/dpi`          | one page as an `image!`                             |
| `merge`                       | several documents as one binary                     |
| `extract-pages`               | a range of pages as one binary                      |

Settle this table in `spec/` before building: it is a proposal until then.

**What the tests compare against.** Rebol's own six fixtures, under
`src/tests/units/files/`, each checked by length and digest before it is trusted.
Text is compared with what each file is known to say, read independently of
PDFBox. A rendered page is compared by size and by chosen pixels, not byte for
byte, because PDFBox's output depends on the JVM's rasteriser and fonts. The
boundaries include an empty document, a damaged and a truncated file, a wrong and
a missing password, a page number either side of the range, and a closed handle.

**Done when** every command in the settled table has a standalone test, the
codec's answers are shown not to change, and an end-to-end test runs the command
line with and without the jars on the classpath and gets each answer.

### 13. Postgres as an extension jar

A real 3.22.5 has no Postgres, so like PDF this goal improves on the C rather
than agreeing with it. `jebol-postgres.jar` is built on the pattern `jebol-sqlite.jar` set,
with only a `jvm` setup, over pgjdbc. Decision 63 says why it has no `web` setup
and how its sockets are checked.

1. **Settle the commands in `spec/` first,** shaped like SQLite's (`open`,
   `exec`, `eval`, `prepare`, `step /rows /with`, `columns`, `finalize`,
   `close`, `info`), with a `postgres://` scheme like SQLite's, so that a script
   reads the same against either.
2. **A Postgres for the tests:** installed from apt by
   `scripts/setup-machine.sh` and started by the tests themselves, as Chrome
   is.

**Done when** every command in the settled table has a standalone test, a site
build that names it is refused, and an end-to-end test runs the command line
with and without the jar against a real Postgres.

### 14. The `miniaudio` module, as a port

`import 'miniaudio` answering as Oldes/Rebol-MiniAudio 0.11.23.0 does: its
devices, playback, fades and effect nodes, ported rather than bound to a native
library. Last on purpose. Sound cannot be compared byte for byte the way a codec
can, so working out what its tests compare against is the first step.

### 15. Find where JEBOL's answers differ from R3's by mutating real code

The sweeps so far are a grid per feature, answered by `./r3-head`:
`make-to-sweep.py` (every MAKE and TO), `near-and-where-sweep.py` (every
native refusing every argument), and `sweep.py` (each suite assertion as a
probe). They are boundary analysis feature by feature, and they work. But
they cannot find a difference that only appears when two features combine.
Goal 1f is the proof: `print + 1` raises `cannot-use` in R3 and
`missing-arg` in JEBOL, and no grid over `print` or over `+` would ever
write it. A one-token mutation of `print 1 + 1` does.

So this is differential testing: real Rebol code, mutated a token at a time,
run through both interpreters, with every disagreement made into a test.
The case that matters most is the one where R3 answers a value and JEBOL
raises, or the reverse. That is a difference in logic, not in reporting.

**The seeds are real code, not invented code:**

- the `--assert` expressions of Rebol's own suite (10,144 of them;
  `sweep.py` already turns each into a probe), read from
  `rebol3-source/src/tests/units/`;
- the `code` entries of `corpus/*.corpus`;
- the top-level expressions of the library JEBOL borrows,
  `rebol3-source/src/mezz/`, run only where they define nothing and have no
  side effects (the filter comes first, see below).

**The mutations work on values, not text.** Rebol code is a block, so a
seed is loaded, one value is changed, and the block is molded back. One
mutation per mutant, so that a difference has one cause. The operators:

- delete a value; duplicate a value (`1 + 1` becomes `1 + + 1`); swap two
  neighbours;
- replace a value with one of a fixed set chosen to sit on boundaries:
  `none`, `true`, `false`, `0`, `-1`, `1`, `""`, `[]`, `#{}`, `0.0`,
  `1x1`, `'a`, `#"a"`, `%f`, and the word of an operator;
- wrap a value in a paren, or in a block; unwrap a paren or block of one
  value;
- turn a word into its set-word, get-word or lit-word, and back;
- insert an operator word before or after a value.

**Each case's outcome is put on a ladder,** and a difference is ranked by
the highest rung where the two disagree:

1. **value against error.** One answers, the other raises. Always a defect
   unless the allowlist names it.
2. **different values.** Compared by `mold/all`, after the normalisation
   below.
3. **different error id.**
4. **same id, different arg1, arg2 or arg3.** The datatype is compared as
   well as the spelling, so `add:` is not `add`.
5. **same id and arguments, different `near`.**

**The allowlist is what keeps the output readable.** It is a checked-in
file under `src/testFixtures/resources/org/jebol/`. Each line pairs a
pattern (an error id, a native's name, or the shape of the source) with
the entry in `docs/standing.md` ("Where JEBOL differs from a real 3.22.5
on purpose") or the decision number it is the consequence of. The four
fixes JEBOL makes to Rebol on purpose (decision 71) are the first entries. A
difference the allowlist names is counted, not reported. A line the
allowlist has that no case matches anymore is reported too, so the list
cannot go stale silently.

**Shrink every difference before reporting it.** Delete values from the
mutant, from the outside in and from both ends, as long as the two
interpreters still disagree on the same rung. Stop when one more deletion
would make them agree. What is reported is that smallest source, its seed
and the mutation applied to it, and both answers. CLAUDE.md asks for this
of a codec ("shrink the input until the smallest failing case is one
line"), and it is what makes a difference fixable rather than just
counted.

**A probe is never thrown away.** Every shrunken difference becomes a row in
a checked-in transcript, the same shape as `make-and-to-as-r3-answers.tsv`:
source, R3's answer, rung. Once it is fixed, the row moves into a
`*FromTheSourceTest`, standing on its own as CLAUDE.md requires, and the
transcript row stays as the regression. The generator script is checked in
under `scripts/` beside the other sweeps and follows their conventions:
find the repository and the JDK from where the script stands, run
`./r3-head` (never a release binary), and write the TSV that the test
reads.

**What has to be settled before the first run:**

- **Answers that change from run to run.** `now`, `random`, `checksum` of
  anything holding a time, `stats`, molded handles, ports and events, and
  anything that prints an address are either normalised (a date becomes
  `<date>`) or excluded by name. A list of excluded natives is checked in,
  and each entry gives its reason.
- **Side effects.** Each batch runs in a fresh, empty working directory
  under `build/`, with a timeout per case. Mutants that would reach `quit`,
  `halt`, `q`, `wait`, `call`, `browse`, `write`, `delete`, `rename`,
  `make-dir`, `secure`, or a network scheme are dropped before they run, by
  checking the mutant's words against that list. Network scheme means
  `read` or `open` of a url other than a `file:` one. Never test against an
  external site (see the memory on this).
- **R3 crashing.** A few cases segfault a real Rebol (`make event! [[x]
  [x]]` is one; `make-to-sweep.py` says so). The existing sweeps run one
  process per case, which is too slow for tens of thousands of mutants.
  Run cases in batches, each case under `try`, with one line of output per
  case. When a process dies, rerun what is left of the batch from the case
  after the last line written, and record the crash itself as a finding
  for `docs/rebol-findings.md`.
- **JEBOL's boot.** At about 54ms an interpreter, give each batch one
  interpreter, as `SweepRunner` does. A case runs inside its own
  `do`-of-a-copy, so a mutant that redefines a word cannot leak into the
  next. Redefining a word from the library (`print: ...`, `+: ...`) is a
  mutation in its own right, but it is excluded at first, because it
  poisons the rest of the batch on both sides.

**Then measure what the cases reach in the C.** Build a second
`./r3-head` with gcov (`--coverage` on the compile; add it as an option of
`scripts/build-r3.sh` rather than a copy of the script), run the seeds and
the mutants through it, and list the branches of
`rebol3-source/src/core/` that nothing reached. Those branches are the
boundaries that per-feature boundary analysis wants, read from the C
instead of guessed. CLAUDE.md says the same for compressors: the
boundaries that matter are where the implementation changes what it does.
Keep any mutant that reaches a branch nothing before it reached as a seed
for the next round. That is coverage-guided fuzzing, the simplest version
of it, and the uncovered list becomes the stopping rule: a round that adds
no new branch and no new difference ends the search for that seed set.

**The order:**

1. The value-against-error rung only. Suite seeds, one mutation each, the
   allowlist, the side-effect filter, batching. That is the cheapest
   stage, and it catches the worst class of difference.
2. Shrinking, and the transcript plus its test.
3. The other four rungs, rung by rung, triaged as they come in. Each new
   kind of difference is either fixed under the zero-bug rule or, if it is
   deliberate, given an allowlist line that names its decision.
4. The corpus and the library as seeds.
5. gcov on `r3-head`, the list of unreached branches, and coverage-guided
   seeding.

**Done when** a full round over every seed set has no unexplained
difference on any rung, every difference found is either fixed with its
test or named in the allowlist with its reason, the transcript is checked
in and read by a test in `allTests`, and the list of unreached C branches
is in `docs/standing.md` beside the other measures.

### 16. The effect dialect, ported from REBOL 2

No Rebol 3 paints an effect. 3.22 builds no DRAW at all, the 2010 builds
compiled DRAW's `effect` out, and the `FX_*` routines `host-effect.c` calls
are not in the tree. The behaviour lived in REBOL 2's View: `box effect
[gradient 0x1 ...]`, which `clock.r`, `diagram.r`, `effect-lab.r` and the
other R2 programs in `corpus/sources/` use. So this is a port from R2, and
it serves both doors in: DRAW's `effect pair pair block`, which runs a chain
over what is already painted in that rectangle, and a gob whose content is an
effect block. Until it lands, DRAW's `effect` refuses, as decision 72 says.

1. **Find the reference before writing anything.** R2 is a closed binary,
   and running a downloaded one is ruled out. Look for the AGG effect sources
   in the later Saphirion and Atronix forks of R3, and check their licence
   against JEBOL's before reading a line. Without them the authority is R2's
   View documentation, and the claim the tests can make is "as R2's docs
   describe", not "as the C computes". Say which, in a decision.
2. **The 31 commands** of `dial-effect.reb`, which `host-effect.c`
   dispatches with its defaults: per-pixel colour (invert, grayscale, luma,
   tint, contrast, colorize, colorify, hsv, key, alphamul), two images
   combined (add, mix, multiply, difference), geometry (flip, crop, extend,
   rotate, reflect, tile, tile-view), the 3x3 kernels (blur, sharpen,
   emboss, convolve), the gradients (gradient, gradcol, gradmul),
   resampling (fit, aspect, with nearest, bilinear, bicubic and gaussian),
   and shadow.
3. **Written once, in the domain.** An effect works on pixels already
   painted, which a paint instruction cannot carry. Each renderer gains two
   primitives, read back a rectangle and put pixels back, and the domain
   runs the chain on the pixel array, so the desktop and the browser still
   decide nothing.

**Done when** all 31 are rules with tests against the reference chosen in
step 1, DRAW's `effect` and an effect gob both paint, and the renderer
comparison covers an effect.

### 17. The system's own settings, on every platform

The owner, 2026-11-17: what a user sets for the whole system is to be
thought about for every platform at once, not for Android alone. Each
applies to at least two of the three.

- *Text size.* Android's font scale, the desktop's scaling, and a
  browser's zoom and minimum font size.
- *Dark mode.* Android's night mode, the desktop's dark theme, and
  `prefers-color-scheme` in a browser.
- *Right-to-left languages.* Android's layout direction, the desktop
  locale's, and a page's `dir`.

The 2010 GUI ignores all three today. This goal decides once what a
script is told and what the GUI does with each:
- how a script reads them, for which Rebol's suite and the C are searched
  first;
- whether the GUI follows them by itself;
- how a change while running reaches a script, as a resize does.

It belongs with the owner's later thought of one layout across every
platform, as Flutter does layout. 2d's layouts that scale to the screen
are the first piece of that.

**Done when** each setting is read on the desktop, on Android and in the
page, a script can ask for it, and the GUI's examples follow text size and
dark mode on all three.
