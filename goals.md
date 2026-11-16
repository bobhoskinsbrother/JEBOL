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

Seven stages, in this order, from 2b. Each is planned in full -- what is true today,
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

#### 2b. The app: one screen, touch, the keyboard, the lifecycle, Back

The first stage a person can hold: `examples/r3-gui` running in an app on a
device, drawn by the domain's paint list on Android's `Canvas`, driven by
touch and the soft keyboard, with Back, rotation and the background handled,
and the owner's own phone saying whether it is quick enough.

**What is true today (measured 2026-11-16).**

- The core runs on ART: `./gradlew -p android androidTests` runs the core's
  functional tests and Rebol's suite on emulators in shards, 25,601 cases, none
  failing (`docs/android-port.md`). Nothing draws: `android/host`'s
  `AndroidHost` (17 lines) gives carried files, digests, the machine and a
  pause, and no screen. The only APKs are `android/suites`, a test harness
  whose own comment says 2b replaces it with the app.
- The view's logic is the domain's and already runs on ART: gob trees, text
  layout by the carried font's metrics (`DefaultFont`, `DefaultFontRenderer`),
  DRAW, and `PaintList`, which a screen turns into `DrawingCommand`s -- `Fill`,
  `Stroke`, `Picture`, `Text` -- executed one at a time on a `Surface`
  (decision 35). `Java2DSurface` (desktop) and the page's `jebol-paint.js` are
  the two executors; Android's is the third.
- **A phone is a page, not a desktop.** `BrowserScreen`
  (`web/src/main/java/org/jebol/adapter/web/`) is the one-surface screen:
  windows painted on one viewport (`WindowsOnOneSurface`, already in the
  core), events `report`ed into it, sizes `measure`d into it, and everything
  the platform must do behind its `Viewer` interface -- `paint`,
  `takeTyping`, `releaseTyping`, `useCursor`, `changeHistory`, `guardLeaving`,
  `showTitle`, `paintOffscreen`. It depends on nothing but the core; its
  constructor builds its history as `new PageHistory(viewer)`. `WebFiles`,
  the page's in-memory `FilePort`, depends on nothing but the core either.
- **Back is already the domain's.** `Navigation` takes a
  `reportMoveRequest(BACK)` from an operator and finds the target in
  `History`; at the first place there is none. Every `push`, `replace` and
  `back` hands the screen's `NavigationPort` a `HistoryEntry` with its index,
  and `isGuarding()` is the unsaved-work flag (decision 50).
- **Pixel parity** for the page is `PageRendersLikeTheDesktopTest`
  (`desktop/src/endToEndTest`): four shape scenes (gob panels, a DRAW block,
  stroked curves, see-through colours and rounded corners) painted by Java2D
  and by Chrome in one process and compared with `Differences`: no pixel inside
  a shape may differ, and edge pixels may differ by 24 of 255 over at most a
  fortieth of the picture (decision 35's bound), with three text scenes checked
  by where their ink falls. On Android, Java2D is not there, so the two
  pictures cannot be made in one process.
- `examples/r3-gui/main.reb` reads `sliders.r` and `survey.r` with `load`,
  opens the sliders window at 20x20 and the survey at 620x20, and prints where
  its slider, field and scroller are -- which is how the site's gate drives it.
  The survey opening at 620 dp, before its own width, is already wider than
  a phone in portrait (about 411 dp) and leaves it little room in landscape
  (about 914 dp); its laid-out width is not yet measured.
- `examples/r3-gui` needs a file port (2c's), and printing that reaches a
  person (2c's as first written). 2b takes both in their simplest form, so
  its done-when needs nothing a later stage builds.

**What the plan settles, the owner's decisions 2026-11-16:**

- **A rotation is a resize.** The Activity declares
  `android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden"`,
  so it is not recreated; the view's new size is `measure`d into the screen
  and a maximised window refits, as a resized page does, and the script hears
  `resize`.
- **The background is `minimize`, coming back is `restore`.** `onStop` queues
  `minimize` on every open window and the screen stops painting; `onStart`
  queues `restore` and repaints (decision 72's events, which R3's own hosts
  send). If Android ends the process with no warning, the script hears
  nothing -- there is nothing to hear it with -- and the next launch starts
  the app afresh **at the place it was on**: the current place's address is
  saved in `onSaveInstanceState`, and a relaunch hands it to the history as a
  reloaded page's address is (decision 50, `Navigation.reportStart`).
- **2b's done-when runs on a tablet-sized emulator** (1280 by 800 dp). Step 2
  confirms both windows fit it by printing `survey-window/size` on the
  desktop first; if they do not, the AVD grows until they do, and the size
  chosen goes in decision 75. Fitting the examples on a phone is 2d's, whose
  done-when is a phone-sized emulator.
- **Speed is judged on the owner's own phone, by hand.** The measurements
  matter -- cold start to first picture, and tap to repaint -- and are taken
  on the phone over USB and recorded, but the test is whether using the app
  feels snappy. The owner uses it and says so; if it does not, the numbers say
  where the time goes and the owner decides before 2c.

**The work, in order, keeping `allTests` and `androidTests` green after each.**

1. **Move the one-surface screen into the core** (JVM only, no Android yet).
   - `BrowserScreen` becomes `org.jebol.application.OneSurfaceScreen`, its
     `Viewer` with it, by `ide_move_file` and `ide_refactor_rename`
     (`relatedRenamingStrategy: "none"`). Its constructor takes the
     `NavigationPort` instead of building a `PageHistory`, so the page passes
     `PageHistory` (which stays in `web/`) and Android its own.
   - `WebFiles` becomes `org.jebol.application.MemoryFiles` the same way.
   - `PageRendersLikeTheDesktopTest`'s comparison leaves the test for the
     core's test fixtures: `PixelAgreement` over two `int[]` ARGB pictures (no
     AWT, so ART can run it), with decision 35's two allowances as its
     constants, and `ParityScenes` holding the seven scenes' REBOL. The page
     test uses both and asserts exactly what it asserted.
   - **References a device can compare with.** A desktop functional test,
     `Java2DParityReferencesFromTheSourceTest`, paints each shape scene with
     `Java2DSurface` at 420x260 on white and holds it against a checked-in
     reference, `src/testFixtures/resources/parity/<scene>.rgba` (width, height,
     then RGBA bytes); a `-Dparity.write=true` run writes them. Text scenes have
     no reference: hinting differs by platform, so they are checked by where the
     ink falls, as on the page.
   - Gate green; nothing a script sees has changed.
2. **Reshape `android/`** into the three suites `goals.md`'s Android preamble
   names.
   - `android/app` (`com.android.application`, `org.jebol.android`): the app.
     Its `androidTest` holds `androidUnitTests` (package
     `org.jebol.android.unit`) and `androidFunctionalTests` (the core's
     functional tests and Rebol's suite, as `suites` ran them, plus
     `org.jebol.android.functional`). `android/suites` is deleted;
     `NotOnADevice`, `TheAndroidPlatform`, the two not-on-a-device lists and
     `TheCoreBootsOnArtTest` move with their history.
   - `android/endtoend` (`com.android.test`, `targetProjectPath = ":app"`):
     `androidEndToEndTests`, UI Automator driving the installed app from its
     own process.
   - `deviceShards` runs the unit and functional suites as two runs of the
     shards, told apart by the runner's `package`/`notPackage` arguments, and
     archives `androidUnitTests/` and `androidFunctionalTests/`; then
     `androidEndToEndTests` runs on the first emulator alone, since UI tests
     share a screen, into `androidEndToEndTests/`.
   - `emulator.sh` makes the AVD a tablet profile (1280 by 800 dp), so the
     shards and the end-to-end suite run on one kind of device. The default
     becomes two emulators: three left under 2 GB beside IntelliJ on
     2026-11-16, the condition that crashed the machine on 2026-11-15.
   - `-Pdevice=<serial>` runs the suites on a phone plugged in over USB instead
     of starting emulators.
   - `androidTests` green with the same counts as before; the app opens to an
     empty screen.
3. **Spec**: `spec/android.allium`, beside `spec/web.allium` as that file says
   it will be, for the screen and the lifecycle (2c adds the ports):
   - sizes are dp, the view's pixels divided by the display's density; the
     viewport is the view's size in dp, `screen-dpi` 160 times the density;
   - one finger is the pointer: `down`, `move`, `up` at its position in dp; a
     second finger is ignored until the first lifts (2d may add gestures);
   - `takeTyping` raises the soft keyboard and `releaseTyping` lowers it; text
     the keyboard commits is one `key` event per code point, delete is
     `key` 8 and the action key `key` 13, the arrow keys `control` events named
     as the desktop names them;
   - Back is enabled while the history has a place behind the current one, or
     while unsaved work is flagged; it is the history's Back, and at the first
     place it leaves the app -- asking first, with the app's own dialog, when
     unsaved work is flagged;
   - stop is `minimize` on every window, start is `restore`, a rotation is a
     resize, and a relaunch after the process was ended resumes at the saved
     place;
   - what a script prints is one logcat line per finished line, tag `jebol`,
     `Log.i` (and `Log.e` while error output is on), a part line held until it
     ends -- the shape decision 64 gives a site's console.
   The spec check counts 13 files.
4. **Failing tests first**, each standing on its own and reading no `.r3`:
   - `androidUnitTests` (adapters alone, no interpreter): `AndroidSurface` --
     a fill's colour at a pixel, a stroke's width, a clip, a transform, opacity
     over white, an image's pixels, a text run's ink inside its box;
     `AndroidPixelSurface` -- RGBA out, alpha kept; the key mapping table; dp
     from pixels at densities 1, 2 and 2.625; `AndroidHistory` -- Back enabled
     after a push, disabled after Back to the first place, enabled while
     guarding; `LogcatOutput` -- part lines held, one line per finished line.
   - `androidFunctionalTests` (an interpreter on ART with Android's screen):
     `view` puts a paint list in front of a viewer; a measured size reaches
     `system/view/screen-gob/size`; a reported `down`/`up` at a face's
     position reaches its handler; `go` then a reported Back arrives as a `back`
     event with the place; `to image!` of a gob is the pixels
     `AndroidPixelSurface` painted; the four parity scenes agree with their
     Java2D references (`PixelAgreement`, decision 35's bound) and the three text
     scenes put their ink where the page's test says.
   - `androidEndToEndTests` (the installed app, UI Automator): a script prints
     and the line is in logcat; a tap clicks a face; typing reaches a field
     through the soft keyboard; Back moves the history and, at the first place,
     leaves the app; Back with unsaved work asks, and Cancel stays; a rotation
     reaches the script as `resize`; Home then return reach it as `minimize`
     then `restore`; `am kill` then relaunch resumes at the saved place; and
     `examples/r3-gui`: a drag moves the slider, typed letters reach the
     survey's field, a drag moves the scroller -- the three the site's gate
     drives (`TheExamplesWithNoServerEndToEndTest`).
   - A debug build accepts a script in its launch `Intent` (extra `script`), so
     each end-to-end test starts the app on its own few lines; a release build
     ignores the extra and runs only the app it carries.
5. **`AndroidSurface`**, in `android/host`, executing each `DrawingCommand` on a
   `Canvas` and deciding nothing, as `Java2DSurface` does (read beside it, method
   for method): the path from `PathStep`s into `android.graphics.Path` with the
   `FillRule` as its `FillType`; fills with `Paint.Style.FILL` and a
   `LinearGradient`/`RadialGradient` for a `Gradient`; strokes with the width,
   `Cap`, `Join`, miter limit and a `DashPathEffect` from `PaintState`, drawn
   where the call puts them (decision 35's "pure" strokes); the `Transform` as a
   `Matrix` concatenated after the density scale; clips and opacity per
   command, saved and restored around it; a `Picture` from the image's RGBA
   (decision 20) into an `ARGB_8888` `Bitmap`; `Text` drawn piece by piece where
   `PlacedText` puts it, with the carried font as a `Typeface` built from its
   bytes (`android.graphics.fonts.Font.Builder(ByteBuffer)`, API 29), the run's
   size, colour, shadow and selection patches as `Java2DSurface.write` paints
   them. `AndroidPixelSurface` paints a list onto an `ARGB_8888` `Bitmap` over
   white and reads it back as RGBA, for `to image!`.
6. **The screen and the view.** `OneSurfaceScreen` with Android's `Viewer`:
   `JebolView`, one `View` filling the Activity. `paint` hands the newest
   command list to the view and calls `postInvalidate()`; `onDraw` scales by
   the density and replays it with `AndroidSurface` -- the interpreter never
   touches the view. `onSizeChanged` measures the viewport in dp into the screen;
   `onTouchEvent` reports `down`/`move`/`up` in dp; `onCreateInputConnection`
   returns an `InputConnection` turning committed text, delete and the action
   key into reports as the spec says; `takeTyping`/`releaseTyping` show and hide
   the keyboard through `InputMethodManager`; the cursor and the title do
   nothing (an app owns its screen, decision 64). `AndroidHistory`, Android's
   `NavigationPort`, keeps the current index from the entries it is handed and
   enables the Activity's `OnBackInvokedCallback` (registered at
   `PRIORITY_DEFAULT`) while Back has somewhere to go or work is unsaved.
7. **The app.** `JebolActivity` starts one interpreter on a thread of its own,
   built on Android's host ports with `OneSurfaceScreen`, `MemoryFiles` holding
   the app's files, `LogcatOutput`, and of the services `ShippedPageBounds`
   grants a page, those 2b can serve: `FILES`, `WORKING_DIRECTORY`, `CLOCK`,
   `WINDOWS` and `NAVIGATION` (the clipboard, MIDI and the web are 2c's
   adapters, and refuse until then as a page's do). The manifest sets
   `android:enableOnBackInvokedCallback="true"`, which Back's callback needs from
   Android 13. The app's files are `examples/r3-gui`, copied into
   the APK as Java resources under `jebol-app/` with an `app.json` naming them
   and the entry `main.reb`, as `assembleSite` writes one (decision 73: resources,
   not assets); `MemoryFiles` is filled from it and the script runs from `/`.
   `onStop`/`onStart`, Back and the saved place as the spec says. The
   interpreter thread is a daemon; `onDestroy` with `isFinishing()` ends it.
8. **Parity and the examples on the tablet emulator**: the functional and
   end-to-end tests of step 4 pass, `androidTests` green, `allTests` green.
9. **On the owner's phone.** `-Pdevice=<serial>` runs `androidEndToEndTests`
   there too. `AndroidTimingsTest` (end-to-end, not in the default run; asked
   for with `-Pclasses`) measures, over ten launches each: cold start to the
   first picture drawn (`am start -W`'s TotalTime, and the time to the first
   `onDraw` the app logs), tap to repaint (the touch event's time to the end of
   the next `onDraw` that follows its handler), and frame times while the
   slider is dragged (median and 95th percentile), on `examples/r3-gui`'s sliders
   window, which fits a phone. The same run on the emulator gives the
   comparison. Then the owner installs the app (`./gradlew -p android
   :app:installDebug`, the phone plugged in) and uses it. If it does not feel
   snappy, the timings and a `simpleperf` profile of a drag go to the owner
   before 2c, with where the time goes.
10. **Docs.** Decision 75: the screen and the lifecycle -- the one-surface
    screen moved into the core, rotation as a resize, the background as
    `minimize`/`restore`, the saved place, Back's enabling, logcat, the debug
    build's script extra, references painted ahead for parity, the tablet
    emulator and the two-emulator default. `docs/android-port.md` gains the
    screen, the app and the measurements; `docs/standing.md` the app APK's size
    and the phone's figures; `CLAUDE.md`'s Android gate paragraph names the
    three suites, `-Pdevice` and the tablet AVD.

**Done when** `examples/r3-gui` runs in the app on the tablet emulator and the
end-to-end suite drives it as the site's gate drives the page; the parity
scenes agree with Java2D's references within decision 35's bound; every test
of step 4 passes; `allTests` and `androidTests` are green; and the owner has
used the app on their own phone and called it snappy, with the measurements in
`docs/standing.md`.

#### 2c. Android's adapters

Every port in `HostPorts`, Android's: files in the app's own storage, the
script's home inside its root (decision 69), replacing 2b's `MemoryFiles`
with the app's bundled files still readable beneath it; real sockets, on platform
threads where the desktop's take virtual ones (Android has none); TLS
through Android's own (decision 65) and its trust store; IDNA through
`android.icu.text.IDNA`, so `Uts46` stays the desktop's; pictures through
`BitmapFactory` and `Bitmap.compress`, which brings WebP to Android (goal
10); the clipboard; the machine; CALL refused; MIDI none for now. Printing
to logcat is 2b's. A port Android cannot supply refuses as a page's does,
never with a host exception (`NoFailureEscapesAsAHostException`).

**Done when** each port has a `spec/android.allium` rule, an
`androidUnitTests` test of the adapter and an `androidFunctionalTests` test
through REBOL.

**Its plan settles:** whether printing also shows on the screen (the first
goal text said a console view; decision 64 says an app owns its whole
screen).

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

**Done when** every example works by touch alone, on a phone-sized emulator
and in Chrome's phone emulation, and the edge swipe moves the site's history.

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
