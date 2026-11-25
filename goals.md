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

The stages left, in this order, from 2e's Mac and Windows part; 2b, the app, is done (decision 75,
`docs/android-port.md`), and so are the end-to-end suite's restart in place
that came after it, 2c, Android's adapters (decision 77), 2c2, the user's own
files through Android's picker and the dialogs (decision 80), and 2d, touch
and layout for every screen (decision 79). Touch left for later: selecting text
by long press, pinch to zoom, and more than one finger. Each is
planned in full -- what is true today, spec, failing tests, code, docs -- in its own plan commit before work on it
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

#### 2e. An app of one's own: what is left, on the owner's Mac and Windows

The owner, 2026-11-18 (decision 78): JEBOL provides a packager, not an app.
`jebol-devtools`, the example app `examples/an-app-of-ones-own`, the packages
for the web, Android and Linux with their tests, SQLite on Android and the
desktop's Java 25 are done (decision 78, "What building 2e settled";
`using-jebol.md`, "An App of Your Own, on Every Platform"). What is left needs
the owner's machines.

**Left.**
1. **The owner runs `scripts/app-on-my-mac.sh`** and pastes back
   `build/mac-app-report.txt` and its screenshot: the `.app` built, its
   ad-hoc signature verified, its `Info.plist` and the `.icns`
   (`iconutil`) read back, the app opened as Finder opens it, and its store
   made. Whatever it finds wrong is fixed and the script run again.
2. **The owner runs `scripts/app-on-my-windows.ps1`** and pastes back
   `build\windows-app-report.txt`. It answers first whether a registration
   under `HKEY_CURRENT_USER\Software\Classes\AppUserModelId` lets an app no
   installer placed raise a toast, then reads back what the installer's
   Start-menu shortcut carries.
3. **From those answers, the Windows identity.** jpackage writes the
   shortcut in a WiX fragment it generates, which `--resource-dir` does not
   replace, so `windows/` gains a template of JEBOL's own that sets the
   shortcut's `System.AppUserModel.ID`; and the portable app registers
   itself on first start under the part of the registry the script found to
   serve, with a COM activator, removing it when the app removes itself (the
   developer's part, below).

**Done when** both scripts report the example app running, with its
identity on Windows' shortcut and the portable app registered; `allTests`
and `androidTests` pass.

**Why the desktop needs packaging.** Researched on 2026-11-18, with the
sources in `docs/platform-services-research.md`:
- macOS's `UNUserNotificationCenter` and CoreLocation need an app bundle
  with an identifier, and usage strings in its `Info.plist`. A bare process
  crashes (`bundleProxyForCurrentProcess is nil`) or is refused. The camera's
  consent falls to whichever signed app launched the process, usually
  Terminal, and a bundle without `NSCameraUsageDescription` is ended on
  first use.
- Windows raises a toast only for an app whose Start-menu shortcut carries
  its `System.AppUserModel.ID`. jpackage has no option for that property.
- Linux's GeoClue authorises an app by its desktop ID, through
  `geoclue.conf` or the desktop's agent, which names the app from its
  `.desktop` file.
- jpackage builds a package only on the system it targets. The macOS app is
  built on the Mac and the Windows one on Windows, each by a script the
  owner runs there. The build gains nothing for either (owner, 2026-11-18).

**The developer's part and the framework's** (owner, 2026-11-18). JEBOL is
the framework other developers build their apps with, and the example app
shows how. Each platform follows the convention other frameworks use, with
the sources in `docs/platform-services-research.md`:
- **Windows.** The developer declares the app's ID once, in `app.reb`, and
  does nothing more, as with Electron and its installers. The packager's
  installer puts that ID on the Start-menu shortcut as its
  `System.AppUserModel.ID`, through a WiX template of JEBOL's own for the
  fragment jpackage generates. The app declares the same ID to Windows when it starts
  (`SetCurrentProcessExplicitAppUserModelID`), so its toasts and windows
  match the shortcut.
- **Windows, portable** (owner, 2026-11-18: in 2e, not later). A packaged app
  run without installing it, from jpackage's app image unzipped anywhere,
  registers its own ID when it first starts. It writes
  `Software\Classes\AppUserModelId\<ID>` with its display name and icon, as
  the Windows App SDK and Firefox's portable builds do, and a COM activator,
  so a click on a toast reaches it even when it is not running. Its toasts
  then work with no shortcut. It removes that registration when the app
  removes itself, and the plan says how an app that is simply deleted leaves
  nothing harmful behind. A bare `java -jar` never registers itself: the
  command line keeps only what Windows gives an unpackaged program.

**Settled by the owner, 2026-11-18:** the command line (`java -jar`) keeps
whatever an operating system gives an unpackaged program: on Linux,
notifications and the camera through V4L2; on Windows, location behind the
one desktop-apps switch. It refuses, with `no-service` saying a packaged app
is needed, only where the system will not serve a program it cannot name.

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

The notification permission it needs is the one 2f asks for, so 2e2 may
come after 2f; that order is this stage's to settle.

**Done when** a script can keep a timer and a connection alive with the
app off screen, under Doze on the emulator (`adb shell dumpsys deviceidle
force-idle`), with the notification shown. An end-to-end test drives it.

#### 2f, 2g, 2h. Notifications, location and the camera, on every platform

The owner, 2026-11-18 (decision 78): each is a feature of every platform JEBOL
runs on, not of Android alone -- the web, Android, and the desktop on Linux,
macOS and Windows -- and iOS answers the same three when goal 3 arrives.

**What is true today.**
- `HostPorts` holds eighteen ports, and none of these three.
- `HostService` has thirteen grants, and none of these three.
- Rebol has nothing for any of them. Its C, its mezz, its boot files and its
  suite were searched on 2026-11-18 for a camera, a webcam, location,
  geolocation and notifications, and the only hit was JEBOL's own "camera"
  example of an extension. So the REBOL is JEBOL's own, and each plan writes
  it into `spec/` before any code.
- The web's interpreter runs in a worker. It reaches the page by
  `PageRoundTrip`, as `PageClipboard` does, and the page answers. A worker
  can ask for none of the three itself: the Notifications API's permission,
  the Geolocation API and `getUserMedia` are the window's.
- The Android app asks for no runtime permission today.
- The desktop calls nothing native. The three need each operating system's
  own API, through FFM on Java 25, which 2e step 1 moves the desktop to.
  Nothing is compiled in C, and no native binary is shipped or fetched.
- Each operating system gives the three only to an app it can name, and 2e's
  packager gives an app that name.

**Shared by all three.**
- **A port scheme in the core:** `notification://`, `location://` and
  `camera://`. Each is a `HostPorts` port, which each platform answers with
  an adapter of its own and `none()` refuses, behind a `HostService` grant of
  its own (`NOTIFICATIONS`, `LOCATION`, `CAMERA`). A packaged app is given
  the grants its `app.reb` declares under `uses`. 2e only reads and checks
  that list and writes it into Android's manifest and macOS's `Info.plist`;
  each of 2f, 2g and 2h wires its own entry into its grant, and its plan
  and done-when include an app that did not declare the use being refused
  with `access no-service`.
- **Events from a device thread.** Each platform's callback arrives on a
  thread of its own and is queued for the interpreter, as the screen's events
  are (decision 27). The interpreter's thread is never held by a device, and
  a device never touches a value.
- **The event types come from R3's catalogue** (`reb-evtypes.h`), which has
  no `click`: `open`, `read`, `active`, `close` and `error` are the ones
  proposed below, and each plan confirms them against how R3's own ports use
  them.
- **Refusals, the same on every platform.**
  - Not granted, or no such device here: JEBOL's own `access no-service`, as
    every host service answers today.
  - The user said no: `access cannot-open`, with the port as `arg1` and
    "the user refused" as `arg2`.
  - The operating system needs an app it can name, and the script is not in
    one: `access no-service`, saying so.
  A script can catch each one, and tell them apart by `id` and `arg2`.
- **Permission is asked at the moment of first use,** by each platform's own
  means: Android's runtime permission through the activity, the browser's
  prompt, macOS's privacy consent, Windows's first-use prompt. A script never
  asks for permission separately. An app off screen (2e2) cannot be asked,
  and refuses.
- **The tests reach no real device and no site on the internet.** Each
  platform is faked at its own boundary, as the table under each stage says.
  The fakes are checked in, written in Java where the boundary is a protocol.
- **New desktop machinery, written once and shared:**
  - a D-Bus client for Linux, over a Unix-domain socket (Java 16 and up),
    with SASL `EXTERNAL`, the marshalling of the types the three services
    use, method calls and signals. It is tested against a real
    `dbus-daemon` on a private bus. 2f writes it.
  - an Objective-C bridge for macOS through FFM: `objc_msgSend` in each call
    shape, classes made at run time with `objc_allocateClassPair` for
    delegates, and blocks for completion handlers, built as the block ABI
    lays them out with an upcall stub. Nothing found on 2026-11-18 shows Java
    doing blocks this way, so 2f's first Mac step is a probe that proves it.
  - a WinRT and COM bridge for Windows through FFM: `RoInitialize`,
    `HSTRING`, `RoGetActivationFactory`, calls through a vtable, and
    delegates implemented as COM objects with upcalls (`IUnknown`,
    `IAgileObject`) for events and `IAsyncOperation` completions.
- **macOS and Windows are checked by the owner's scripts** from 2e step 6,
  each extended per stage, and their reports are pasted back. The build gains
  nothing for either.

**2f. Notifications.**

The REBOL proposed, for the plan to settle:

```rebol
write notification:// [title: "Backup" body: "Finished at 14:02"]

n: open notification://
write n [title: "New message" body: "From Sam" tag: "inbox"]
n/awake: func [event] [
    switch event/type [
        active [print "the user clicked it"]
        close  [print "the user dismissed it"]
    ]
    false
]
wait [n 60]
close n
```

`write` shows one and answers at once. A second `write` with the same `tag`
replaces the first, rather than adding another. `close` takes back whatever
the port showed.

**One kind of notification, for now** (owner, 2026-11-18). Android has
several: heads-up, ongoing, progress, the messaging and big-picture styles,
action buttons, groups, and the importance a channel gives. 2f does one,
the plain one with a title and a body, on one channel. The others are left
for later, and so are their equivalents on the other platforms.

| Platform | Shown by | Asks the user | Click and dismiss | The test's fake |
| --- | --- | --- | --- | --- |
| The web | the page, `new Notification` | the page, `Notification.requestPermission`. Firefox wants a click or a key first, so the page shows a bar of its own the user clicks. | `onclick`, `onclose` | a preload script replacing `Notification` with a recorder, plus `permissions.setPermission` over BiDi in Firefox and `Browser.grantPermissions` over CDP in Chrome |
| Android | `NotificationManager`, with a channel made on first use | `POST_NOTIFICATIONS`, API 33 and up, through the activity | a `PendingIntent` to the activity, and a `deleteIntent` | UI Automator opens the shade, finds it by its text, and clicks or swipes it; `pm revoke` for a refusal |
| Linux | `org.freedesktop.Notifications.Notify` on the session bus, with the `desktop-entry` hint | nothing: the desktop shows what it is sent | `ActionInvoked` for the `default` action, and `NotificationClosed` with reason 2 | a fake `Notifications` service on a private `dbus-daemon`, written with JEBOL's own D-Bus code |
| macOS | `UNUserNotificationCenter`, from the packaged `.app` | `requestAuthorizationWithOptions:completionHandler:` | a delegate's `didReceiveNotificationResponse:`, and a category with `customDismissAction` for a dismissal | the owner's Mac script: the Mac shows it, and a person clicks it |
| Windows | `ToastNotificationManager.CreateToastNotifier`, with the app's AppUserModelID | the first-use prompt | the toast's `Activated` and `Dismissed` events | the owner's Windows script, the same way |

Steps:
0. Plan commit: the REBOL, the event types, the refusals, and the order of
   2e2 against 2f.
1. The Mac probe: from the example `.app`, through FFM, ask for permission
   with a block and show one notification. If FFM cannot do blocks, the plan
   comes back to the owner before any other step.
2. Spec: `spec/notification.allium`.
3. Failing tests, then the adapters, one platform at a time: the web,
   Android, Linux with the D-Bus client, macOS, Windows.
4. The example app gains a notification. End to end: Chrome and Firefox; the
   emulator; the `.deb` with the fake service; and the owner's two scripts.
5. Docs: `using-jebol.md`, and decision 78 with what the plan settled.

**2g. Location.**

The REBOL proposed:

```rebol
here: read location://
here/latitude here/longitude here/accuracy

p: open location://
p/awake: func [event] [
    if event/type = 'read [print [p/data/latitude p/data/longitude]]
    false
]
```

A position is an `object!` with `latitude` and `longitude` in degrees,
`accuracy` in metres, `altitude` in metres or `none`, and `time`, the
`date!` it was fixed at. `read` waits for one fix, up to the time the port's
spec gives. An open port reports each move as `read`.

**Coarse and fine are both offered** (owner, 2026-11-18), and the scheme's
form leaves room for both and for what may follow. Proposed:
`location://coarse` and `location://fine`, with the host naming the
accuracy, and bare `location://` meaning coarse, the one that asks the user
for less. A block spec (`open [scheme: 'location accuracy: 'fine
timeout: 0:00:30]`) says the same and carries the port's other settings.
Each platform's word for it:

| Platform | Coarse | Fine |
| --- | --- | --- |
| The web | `enableHighAccuracy: false` | `enableHighAccuracy: true`. The browser has one permission for both, so `accuracy` is what tells them apart. |
| Android | `ACCESS_COARSE_LOCATION` | `ACCESS_FINE_LOCATION`. From Android 12 the user may give only coarse when fine is asked for. |
| Linux | GeoClue's `RequestedAccuracyLevel` city (4) | exact (8) |
| macOS | `kCLLocationAccuracyReduced` | `kCLLocationAccuracyBest`. The user may switch off precise location for the app. |
| Windows | `PositionAccuracy.Default` | `PositionAccuracy.High` |

The plan settles what a script asking for fine is given when the user allowed
only coarse. Proposed: the coarse position, with `accuracy` saying so and
`query` naming the accuracy granted, rather than a refusal.

| Platform | Asked by | Asks the user | The test's fake |
| --- | --- | --- | --- |
| The web | the page: `getCurrentPosition` and `watchPosition` | the browser's prompt, which needs no click first | `emulation.setGeolocationOverride` and `permissions.setPermission` over BiDi in Firefox (139 and up); Chrome's CDP `Emulation.setGeolocationOverride`, or BiDi if Chrome has it by then |
| Android | `LocationManager.getCurrentLocation` (API 30 and up) and `requestLocationUpdates`, through AOSP's own providers, never Google Play services | `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION` | `adb emu geo fix` on the emulator |
| Linux | GeoClue2 on the system bus: `GetClient`, then `DesktopId` (the packaged app's), `RequestedAccuracyLevel`, `Start`, and the `LocationUpdated` signal | GeoClue's agent (GNOME Shell's), or `geoclue.conf` | a fake `org.freedesktop.GeoClue2` on a private bus, with `DBUS_SYSTEM_BUS_ADDRESS` pointed at it |
| macOS | `CLLocationManager` with a delegate, from the packaged `.app` with `NSLocationUsageDescription` | `requestWhenInUseAuthorization` | the owner's Mac script |
| Windows | `Windows.Devices.Geolocation.Geolocator`, `GetGeopositionAsync` | an unpackaged app shares the one "desktop apps" switch, and 24H2 prompts on first use; `RequestAccessAsync` is for packaged apps and throws outside an app container | the owner's Windows script |

Two things the plan has to answer:
- **A machine that cannot find itself.** Since Mozilla's location service
  shut down in June 2024, a desktop with no GPS and no Wi-Fi database may
  get no fix at all from GeoClue; Debian moved GeoClue to BeaconDB. Such a
  machine answers `timeout`, or `no-service` if it never has a source: the
  plan says which.
- **The run loop on macOS.** `CLLocationManager` delivers on the run loop of
  the thread that made it. The plan says which thread that is, beside the
  window's AppKit thread.

Steps: as 2f's, with `spec/location.allium`, and no probe beyond 2f's
bridges.

**2h. The camera.**

The REBOL proposed:

```rebol
shot: read camera://

cam: open camera://
cam/awake: func [event] [
    if event/type = 'read [preview/image: copy cam  show preview]
    false
]
query camera://
```

`read` is one `image!`. An open port reports each frame as `read`, and
`copy` takes the newest. A frame the script has not taken is replaced by the
next, never queued, so a slow script misses frames and never runs a device
out of memory. `query camera://` lists the cameras, and `camera://2` picks
one. The plan settles how a script asks for a size and a rate, and what it is
given when the device offers neither.

| Platform | Captured by | Its frames | Asks the user | The test's fake |
| --- | --- | --- | --- | --- |
| The web | the page: `getUserMedia` | the page draws the newest into an `OffscreenCanvas` and hands the worker its pixels when asked | the browser's prompt | Chrome's `--use-fake-device-for-media-stream` and `--use-fake-ui-for-media-stream`; Firefox's `media.navigator.streams.fake` and `media.navigator.permission.disabled` |
| Android | Camera2, with no `androidx.camera` dependency | an `ImageReader` in `YUV_420_888`, turned into RGB | `CAMERA` | the emulator's virtual scene (back) and emulated (front) cameras |
| Linux | V4L2 through FFM: `VIDIOC_QUERYCAP`, `S_FMT`, `REQBUFS`, `mmap`, `QBUF`, `DQBUF`, `STREAMON` | `YUYV` turned into RGB; `MJPEG` through ImageIO, with the standard Huffman tables a camera leaves out put back | nothing: the device file's permissions | a `v4l2loopback` device the test writes frames of a known picture into. `scripts/setup-machine.sh` installs `v4l2loopback-dkms`, which needs `sudo` once. |
| macOS | `AVCaptureSession` and `AVCaptureVideoDataOutput`, with a sample-buffer delegate, from the packaged `.app` with `NSCameraUsageDescription` | `CVPixelBuffer` in BGRA | `AVCaptureDevice requestAccessForMediaType:completionHandler:` | the owner's Mac script, with the Mac's own camera |
| Windows | Media Foundation: `MFEnumDeviceSources`, `IMFSourceReader` and a synchronous `ReadSample` | RGB32, through the reader's own video processing | the one "desktop apps" camera switch | the owner's Windows script |

The plan has to answer:
- what the fake pictures are, so a test checks pixels rather than only a
  size: the Linux loopback's are the test's own; Chrome's and the emulator's
  are theirs, and the plan reads what they draw;
- how a frame crosses from the page to the worker without copying it more
  than once.

Steps: as 2f's, with `spec/camera.allium`; the Linux loopback fixture first,
since every Linux test stands on it.

**Done when**, for each stage:
- the port has its rules in `spec/`;
- every platform above has an adapter with a unit test, a functional test
  through REBOL in that platform's suite, and an end-to-end test that drives
  the packaged example app;
- the refusals are the same on every platform, with a test of each;
- `using-jebol.md` says what a script may reach on each platform;
- `allTests` and `androidTests` pass, and the owner's macOS and Windows
  scripts report it working.

The research behind 2e to 2h, with every source and how far each can be
trusted, is `docs/platform-services-research.md`. Each plan re-checks there
what it builds on.

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
7. **The packager makes an iOS app from the same `app.reb`** (goal 2e). 2e's
   packager is Gradle, as Android's own build is, and iOS is built by Xcode.
   The owner, 2026-11-21: how the one packager reaches Xcode -- a Gradle
   task driving `xcodebuild`, Gluon's tools, or something else -- is asked
   when this goal is planned, not before.

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
### 18. CI on a machine of its own, and the tests split by what a change can reach

The owner, 2026-11-21: the full Android run needs CI, on a second machine
the owner is setting aside for it, since the development machine has no
room left beside the owner's own work. Corrected by the owner, 2026-11-24:
every check-in runs everything, the full `androidTests` included -- there is
no nightly split -- and a failed run alerts the agent that pushed it, not
only the owner. Until this goal is done the process does not change: every
check-in still runs every suite it touched, the full `androidTests`
included.

What the 136 Android runs in `test-history/` showed, read with the owner:
ART found almost nothing that is ART's own. `to binary!` of an image and
`mold/part` were waste a small heap exposed, the platform-thread race
could have failed on any runtime, and only the test servers binding
`::1` was genuinely Android's. What is per-platform is the platform's own
code: Android's adapters, its SQLite engine, the screen and the app.

1. *The CI machine.* `scripts/setup-machine.sh` sets it up as it does the
   development machine, and a GitHub self-hosted runner on it is driven by
   the repository: a run on each push, and a nightly one. It needs
   hardware virtualisation for the emulators, memory for two of them
   beside a build and a browser (about 32 GB; with less, one emulator and
   a longer night), and it runs headless, so the window tests get a
   display from Xvfb -- where, unlike Xwayland here, `Robot` and screen
   capture may work, which is checked rather than assumed. Results are
   archived as `test-history/` is, somewhere the owner can read them, and
   a failure is reported where the owner will see it. Whether the phone
   moves to it for the nightly run is the owner's choice.
2. *The JVM stands in for what ART exposed.* A functional run with a
   tight heap, and one with platform threads where virtual threads are
   used, so waste and races show in minutes rather than on an emulator.
3. *Every check-in:* `allTests`, `networkTests`, the runs of step 2, the
   full `androidTests` on every device -- the core's functional suite and
   Rebol's suite on ART included -- and the Windows job. Its length is
   brought down by more devices, not by running less.
4. *A red run starts its own fix* (owner, 2026-11-24). No agent waits on
   CI. A service on the development machine notices a run that ended red
   and starts a Claude session for it in a tmux window of its own, named
   for the run, working in a worktree of its own, with the run, its commit,
   the failures in brief and where the run's logs are in its first prompt.
   It finds the cause, writes the failing test, fixes it, and pushes
   through `ci push` unattended once the smaller set passes; only a green
   CI run reaches GitHub. One red run gets one session, and a later red
   run while one is open is handed to it.
5. *Every agent knows CI is red.* The state of CI -- green or red, what
   is running, which run failed and with what, and who is fixing it -- is
   kept in one record file on the development machine, which agents read
   and through which a fixer claims a red run and hands it back. A hook puts one line
   in a working agent's context while CI is red, so it neither fixes the
   same failure nor waits on CI, and `ci push` and `ci status` say so too.
6. *Later: an architecture review in CI* (owner, 2026-11-24) -- further
   stages that review a change's design and can reject a build that every
   test passed. The rules already exist, in CLAUDE.md and
   `docs/decisions.md`; they move into a skill the stage runs over the
   change, and a rejection is a failure like any other. Not part of this
   goal's done.
7. *The rules follow.* A check-in then means the commit passed the
   smaller set and was pushed to CI; a red run is the first thing fixed,
   and a flake is still a fail. CLAUDE.md's section on running the suite
   and the memory on earning a check-in change to say so.
8. *The Apple Silicon app, on GitHub's hosted macOS runner.* jpackage
   builds for the processor it runs on, and the owner's Mac is Intel
   (2026-11-21), so 2e's script proves only the Intel `.app`. A hosted
   macOS runner, which is Apple Silicon, runs the example app's
   `packageMac` and the checks `scripts/app-on-my-mac.sh` makes that need
   no one at the screen: the bundle built for arm64, its signature
   verified, its `Info.plist` and `.icns` read back, and the app started
   far enough to make its store. Nightly, since the minutes are charged
   for a private repository; the report is archived with the night's
   results.

**Done when** a push runs every suite on the CI machine and archives its
results, a red run has started its own fixing session with its failures
and that session's fix has gone green, a working agent has been told CI was
red while it was being fixed,
the stand-in runs of step 2 exist, the Apple Silicon app is built and
checked, and CLAUDE.md describes the new way.

### 19. Android 13 and 14: lowering the floor from API 35 to API 33

The owner, 2026-11-25: a third phone joins CI, an older one whose last
Android is 13, beside the two on Android 17. Decision 73 set API 35
(Android 15) as the floor, knowing it left most phones out, because
Android 15 is the first with `SequencedCollection`:
the core calls `getFirst`, `getLast` and `reversed` about four hundred
times. This goal takes the floor to API 33 (Android 13) and amends
decision 73.

1. *What D8 already does.* Android's compiler backports some newer
   library methods to older API levels. Check, with the AGP the build
   uses, which of `getFirst`, `getLast`, `removeFirst`, `removeLast`,
   `addFirst`, `addLast` and `reversed` it backports on `List`, `Deque`
   and the other sequenced types, by building with `minSdk = 33` and
   reading what D8 reports, not by reading release notes.
2. *What is left.* Every call D8 does not backport, and every other
   API 34 or 35 call in the core, the Android adapters, the app and the
   SQLite extension, listed from `lint`'s `NewApi` check at `minSdk = 33`.
   Each is replaced by one the core already has, or by a small helper of
   the core's own; the core stays at Java 21 for the desktop and TeaVM.
3. *What Android 13 and 14 do differently.* Notifications need
   `POST_NOTIFICATIONS` from 33 (2f already plans for it); predictive
   back, edge-to-edge and the foreground service types 2e2 names change
   at 34 and 35. Each difference gets a test, which the Android 13 phone
   runs on 13 and the others on 17.
4. *CI.* The Android 13 phone joins the CI machine through the phones
   role and `jebol-phone`, with the same rules as the others (testing
   settings only, never wiped), so every run covers Android 13 and 17.
   The device suites shard across the three phones by each test's
   measured time rather than by count: the phones differ in speed, the
   older one most, and a shard sized by count leaves the fastest waiting
   on the slowest. The CI machine has two USB-A ports, so the third phone
   needs a powered hub or another port, which the owner decides.

**Done when** the app, its test APKs and the SQLite extension build at
`minSdk = 33` with no `NewApi` warning, the full `androidTests` passes in
CI on the Android 13 phone beside the two on Android 17, and decision 73
says Android 13.
