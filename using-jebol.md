# **Using JEBOL**

This user guide explains how to execute REBOL scripts using JEBOL. For details on building and porting JEBOL itself, please consult docs/porting-guide.md and goals.md.

Sections 8 and 9 outline planned future documentation. Their scope and requirements are explicitly noted to clarify missing content.

---

## **1. What is JEBOL?**

JEBOL is a port of Rebol 3 (specifically Oldes/Rebol3 version 3.22.5) from C to the Java Virtual Machine (JVM). It allows host Java applications to instantiate an interpreter, execute REBOL scripts, and retrieve evaluation results.

JEBOL enforces two key security principles by default: strictly enforced execution time limits and complete isolation from external resources unless explicitly permitted by the host environment.

```java
Interpreter interpreter = new JavaHost().create();  
ScriptOutcome outcome = interpreter.run("1 + 2");  
System.out.println(outcome.asHostValue());   // 3
```

Script execution errors, timeouts, or stack overflows do not throw Java exceptions. Instead, execution results are encapsulated in a ScriptOutcome object. If a Java throwable is caught, it indicates an internal error within JEBOL itself.

The JEBOL distribution artifact has no external third-party dependencies.

As a port of Oldes/Rebol3 ([https://github.com/Oldes/Rebol3](https://github.com/Oldes/Rebol3)), unmodified components of the original project are bundled inside the JAR archive. See the "Licence and Attribution" section for licensing details.

---

## **2. Installation and Execution**

**Prerequisite:** JEBOL requires Java 25 or higher. On an older Java it says so and stops: `JEBOL needs Java 25 or later; this is Java 21.`

### **Running from the Distribution Package**

The binary release contains launch scripts for Unix and Windows, the compiled JAR file, documentation, and licensing files (LICENSE and NOTICE).

```commandline
unzip jebol-3.22.5.zip  
jebol-3.22.5/bin/jebol
```
### **Building and Running from Source**

```
./gradlew installDist  
./build/install/jebol/bin/jebol
```
Launching the REBOL interactive console initializes start (similar to r3) and presents the startup banner. Enter REBOL expressions directly at the prompt:

```commandline
╔══════════════════════════════════════════════════════════════════════════╗  
║  REBOL/core 3.22.5 (JEBOL port)                                          ║  
...  
>> 2 + 3 * 4  
== 20  
>> name: "world"  append name "!"  
== "world!"  
>> divide 1 0  
** math error: division by zero
```
To evaluate a one-line expression and terminate, use the --do flag:

```
./build/install/jebol/bin/jebol --do "print 1 + 2"
```

Rebuild the application using `./gradlew installDist` following any modifications to Java source files.

You can pass a script file or an application directory path to the command line. When an application directory is specified, JEBOL executes `main.reb` using that directory as the working path for relative operations like `do %...` or `read %...`. 
Directories lacking a `main.reb` file will produce an error.

```
jebol my-script.reb  
jebol my-app/
```
A script may also be given by https url. It runs the same way a local script does: its header is read, `system/options/script` is the url, `Needs:` entries are imported, and QUIT still decides the exit status. A url that cannot be fetched is reported and leaves with one.

```
jebol https://example.com/app/main.reb
jebol --cafile company-ca.pem https://example.com/app/main.reb
```

A module listed in `system/modules` by an address outside the build is fetched only if its SHA-256 is recorded first, in `system/options/module-digests`. A module with no digest is refused before anything is requested. Bytes that do not match fail the import with `invalid checksum (tampered file)`, and are neither kept nor run. Bytes that match are kept in the modules directory and loaded from there on later runs. The modules JEBOL ships with are read from the build and need no digest.

```
extend system/modules 'greeting https://example.com/modules/greeting.reb
put system/options/module-digests https://example.com/modules/greeting.reb #{CAF0557DBCFBF943E17F4B7840B5E1E43CC7A05D0BE45BDBABF9189C25D13E6B}
import 'greeting
```

`--cafile` adds the authorities in that file to the ones the machine trusts; it does not replace them. See "Trusted certificates" in section 4.

Alternatively, pass script content via standard input:

```
cat my-script.reb | jebol
```

### **Running a GUI Application**

A script that uses `VIEW` needs a screen. `--screen=window` attaches a native window; with no switch the interpreter runs headless and a view draws nothing. `--screen=web` serves the same picture to a browser page instead and prints the address to open.

```
./build/install/jebol/bin/jebol --screen=window examples/r3-gui/
```

### **Running with an Extension Jar: SQLite**

Some modules R3 downloads as native extensions come instead as a jar beside JEBOL's own, so JEBOL itself keeps no dependencies. `jebol-sqlite.jar` is the first: Rebol-SQLite 3.46.0.0's `sqlite` module and `sqlite:` scheme. Build it, then put both jars on the class path; `java -jar` ignores `-cp`, so name the launcher class:

```
./gradlew jar :extensions:sqlite:assembleExtension
java -cp build/libs/jebol-3.22.5.jar:extensions/sqlite/build/jebol-extension/jebol-sqlite.jar org.jebol.adapter.cli.JebolLauncher script.r3
```

The libraries the jar needs sit beside it in `build/jebol-extension/`, named by its manifest; keep the folder together. Without the jar, `import 'sqlite` fails as a module that cannot be found. A site carries it when built with `-Pextensions=` and the jar's path. `extensions/sqlite/README.md` shows the commands with SQL, and `docs/writing-an-extension.md` section 11 says how to build an extension of your own this way.

#### **VID: windows from a layout block**

VID is REBOL's dialect for describing a window: a block that lists the things on it, not code that draws them. Rebol 3.22 ships no VID of its own, so JEBOL carries the one R3 had: the R3 GUI from REBOL Technologies, version 0.2.1 of 2010, documented in its guide at https://www.rebol.com/r3/docs/gui/gui.html. It is REBOL from end to end and draws every widget with DRAW, so a layout looks the same in a native window, on `--screen=web` and in a page.

The guide's scripts start with `load-gui`. Here there is no need: the GUI is loaded the first time a script gives `view` a block, and calling `load-gui` yourself does no harm, because it loads once. A script that never views a block never pays for it. `view` given a gob, an image or a face object is Rebol's own and loads nothing.

```
view [
    title "Hello World"
    button "Close" close
]
```

**How a layout reads.** Each item is a style word followed by what it needs, in any order: a string for its text, a tuple for its colour, a pair for its size, a number where the style takes one. Then come the actions it takes when used. A set-word before a style names that face.

```
view [
    title "Opinion Survey"
    panel 2 [
        label "Name:"    entry: field
        label "Comment:" area
    ]
    group [
        button "Submit" submit none
        button "Reset"  reset
        button "Show"   do [print get-face entry]
        button "Cancel" close
    ]
]
```

`panel` and `group` hold other faces. A number after either sets how many columns it lays them out in, and a tuple after `panel` gives its colour, as in `panel 2 80.200.180.80 [...]`.

**The styles.** `title`, `head-bar`, `text`, `label`, `info`, `info-area`, `doc`, `button`, `toggle`, `check`, `radio`, `arrow-button`, `field`, `area`, `code-area`, `text-area`, `text-box`, `text-list`, `slider`, `scroller`, `progress`, `box`, `bar`, `div`, `image`, `drawing`, `sensor`, `pad`, `panel`, `group`, `tight`, `backdrop` and `window`. `words-of guie/styles` lists them once the GUI is loaded.

**The actions** a face can take when it is used:

| Action | What it does |
| --- | --- |
| `do [...]` | Runs the block. Named faces in the same layout can be used by name inside it. |
| `set name value` | Sets the named face's value. |
| `attach name` | Sets the named face to this face's value, so one slider can drive a scroller. |
| `reset` / `reset name` | Puts faces back to their starting values. With no name, every face in the panel the button is in. |
| `clear name` | Empties the named face. |
| `focus name` | Sends typing to the named face. |
| `of name` | Makes faces sharing a name exclusive, as radio buttons are. |
| `submit target` | Collects the panel's values and sends them to a url, a file or a function; `none` prints them. |
| `close` | Closes the window. |
| `alert "message"` | Shows a message. |
| `print value`, `dump` | Prints a value, or the face itself, to the console. |
| `browse url`, `launch file` | Opens a page, or runs another script. |
| `halt`, `quit` | Stops the script, or the process. |

**Reaching a face from the script.** A face's name belongs to its panel, not to the script, so `entry` above is not a word the script can use outside the layout. `get-panel-var window 'entry` finds the face from the window gob `view` answered. `get-face` reads its value, `set-face` changes it, and `do-face` runs its actions as though the operator had used it.

```
window: view/no-wait [entry: field "typed"]
print get-face get-panel-var window 'entry
```

**Waiting.** `view` waits until the window is closed, and then the script carries on. `view/no-wait` answers the window at once. The script then waits for the operator itself, either with `do-events`, which returns when every window has closed, or with `wait [gui-event-port 0.1]` in a loop of its own. That is how `examples/r3-gui/main.reb` prints each change as it is made. `view/options [...] [title: "..."]` gives the window its title.

**What it is not.** This is the R3 GUI, not REBOL 2's VID, and the two dialects are different: a REBOL 2 layout with `banner`, `effect [...]` or a `feel` object will not read. A word the GUI does not know prints `** GUI ERROR: Cannot parse the GUI dialect at:` with the words that follow, and halts the script, as the GUI always has; `try` cannot catch a halt, and at the command line the run ends with status 15. The GUI is in the jar at `org/jebol/modules/r3-gui.reb`, and the changes made so it runs on 3.22 are listed in `org/jebol/mezz/ORDER.txt`.

The shipped example uses the same GUI. The window opens with the sliders and survey layouts, and changes to either are printed in the console as the operator makes them. Closing the window ends the process.

`--screen=window` is also what the dialogs need. `request-color`, `request-file`, `request-dir` and `browse` are refused without a screen, and a run given the native window may use them. `request-password` reads the terminal instead, as R3 does, with a screen or without. The second shipped example opens the machine's colour chooser and prints each colour chosen; cancelling ends it.

```
./build/install/jebol/bin/jebol --screen=window examples/choose-colour/
```

The third draws a gallery of what DRAW draws: shapes, curves, joins, dashes and arrows, the four gradients, transparency, transforms, `push`, `clip`, images and text, each in a tile of its own, drawn at half scale so the window fits a browser. It is a web app as it stands:

```
./build/install/jebol/bin/jebol --screen=window examples/draw/
./gradlew assembleSite -PsiteDir=examples/draw && cd build/site && python3 -m http.server 8000
```

The fourth shows Blend2D's 29 blend modes, one tile each, drawn by the `blend2d`
module's own `draw` -- `b2d: import 'blend2d` -- whose pixels are the release's
byte for byte. That module fills boxes, rounded boxes, polygons, triangles,
circles, ellipses, arcs and cubics, and strokes lines and every outline with
its widths, joins and caps, and draws `shape` and `path` from the path dialect
with smooth curves and SVG arcs, under any transform, clip, alpha and blend
mode today, draws points, and blits images, R3's own or PNG, BMP, QOI and
JPEG files.
Gradients, image patterns, images scaled or placed between pixels, and JPEG
files are drawn within a couple of levels of Blend2D rather than exactly.
Text is drawn byte for byte in any TrueType or CFF font a script names with
`font`, kerned and with ligatures as Blend2D shapes it.

```
./build/install/jebol/bin/jebol --screen=window examples/blend2d/
```

The fifth steps through what `cursor` can show, every system cursor, the pointer hidden and an image, changing every two and a half seconds with the current one named in the title bar. Closing the window ends it.

```
./build/install/jebol/bin/jebol --screen=window examples/cursor/
```

`to image! gob` paints a gob and its pane into an image of the gob's size, over white, as R3 does on Windows. It needs a screen: on the JVM Java2D paints it, with or without a display, and on a page the worker paints it into an OffscreenCanvas. Without a screen it is the refusal R3 gives on a host that cannot compose, `cannot MAKE image! from: gob`.

### **Places, Back and Forward**

An app on a screen has one history, and these words move through it. None of them is in a real Rebol; they are JEBOL's, and `spec/nav.allium` holds the rules.

| Word | What it does |
| --- | --- |
| `go place` | Goes to a place and answers it. A place is a word, string, number or logic, or a block of them: `go 'survey`, `go [survey 3]`. Going where the app already is adds nothing. |
| `go/replace place` | Replaces the place the app is at, adding no entry. |
| `go-back`, `go-forward` | One entry each way. At either end they do nothing. |
| `current-place` | The place the app is at, as a block. On a page this is where the address pointed when it loaded, so a reload or a shared link lands there. |
| `current-place/refused` | The address a page was loaded at when it could not be read as a place, or none. |
| `unsaved-work flag` | With `true`, a reload, closing the tab or closing a window asks the operator first. |

Back and Forward, the operator's or the app's own, arrive on `system/ports/event` as events of type `back` and `forward`, with the place in `event/data`. Handle them in a handler of your own, as the 2010 GUI does not know them. On a page the address is the place: `[survey 3]` is `#survey%203`. Moves are limited to 100 in 30 seconds and an address to 2,048 characters, on every screen.

### **Laying an App Out for Every Screen**

The same script runs on a desktop, in a browser, on a tablet and on a phone, and it lays itself out the way the mobile world does: it reflows to the width it has and scrolls down when it is taller. The rules are the same on every platform (decision 79, `spec/screen.allium`).

| What the screen does | When |
| --- | --- |
| Leaves a window as it was made | It fits its space: the screen on a phone, a tablet or a page; its frame on the desktop |
| Makes it fit, and sends it `resize` | It is bigger than its space and its flags say `resize`. Every window the 2010 GUI and R3's VIEW open for a layout says so, and the GUI reflows itself on that `resize`. |
| Shows it smaller, keeping its proportions, and maps the pointer back | It is bigger than its space and cannot take another size: a plain gob window opened without `resize` |

A 2010 GUI window narrower than its layout reflows its columns down to their faces' minimum widths; one shorter than its layout keeps its layout's height and scrolls. A finger dragging on something that does not take a drag scrolls it: a text area's text, or the window. A drag that starts on a slider or a scroller still drags it.

`size-class? none` answers the screen's size class, and `size-class? window` a window's, as Android names them: `compact` under 600 wide (a phone upright), `medium` to under 840, and `expanded` from 840. It needs no GUI. The examples use it: `examples/r3-gui` opens its two windows side by side when the screen is expanded and one scrolling column otherwise, and `examples/draw` lays its tiles in as many columns as the class and the width allow, scrolling the rest by finger or wheel.

A pointer event from a finger or a pen says `touch` in `event/flags`, after R3's `double`, `control` and `shift`; a mouse's does not. R3 has no such word, so a script that cares tells the two apart with `find any [event/flags []] 'touch`.

`show-soft-keyboard/attach gob` raises an on-screen keyboard where the screen has one, Android's or a phone's browser's, and keeps `gob` in sight above it; the 2010 GUI calls it when a field takes the focus. The keyboard goes when the operator touches outside that gob, when the gob leaves the screen, or on the keyboard's own Done. On a desktop it does nothing, as in R3.

### **An App of Your Own, on Every Platform**

JEBOL gives no app of its own to put your script in. `jebol-devtools` is a Gradle plugin that turns a directory of REBOL into an app of yours for the web, Android, Linux, macOS and Windows, each with your app's name, identity and icon (goal 2e, decision 78, `spec/packaging.allium`). `examples/an-app-of-ones-own` is an app made this way.

**Say what the app is, once, in `app.reb`** at the top of its directory. It is a REBOL block that is loaded and never run, and each value's type is part of what it means:

```rebol
title:     "Notes"
id:        com.example.notes
version:   1.2.3
build:     7
icon:      %icon.png
summary:   "Quick notes that sync nowhere"
publisher: "Example Ltd"
contact:   dev@example.com
website:   https://example.com/notes
category:  productivity
colour:    0.120.215
uses: [
    notifications "Notes reminds you of a note at the time you set"
    camera        "Notes attaches a photo to a note"
]
requires: [sqlite]
entry:     %main.reb
```

`title`, `id`, `version`, `build`, `icon`, `summary`, `publisher` and `contact` are required. `id` is the app's identity on every system, a word of two or more dotted parts. `build` is a whole number that only ever goes up; the store and the phone compare it, not the version. `icon` is a square PNG of at least 1024 pixels, and every size and format each platform wants is made from it; `icon-foreground` may give Android's adaptive icon a foreground of its own. `category` is one of utility, productivity, game, education, graphics, audio, video, developer and network. `uses` names what the app needs the system's permission for, with the sentence the system shows the user. `requires` names the JEBOL extensions the app imports. `entry` is the script to start from when it is not `main.reb`. A wrong `app.reb` is reported whole, every fault at once, and nothing is made.

**Apply the plugin** in the directory's own build:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        maven(url = uri("path/to/JEBOL/build/devtools-repo"))
        gradlePluginPortal()
        mavenCentral()
    }
}

// build.gradle.kts
plugins {
    id("org.jebol.devtools") version "3.22.5"
}
```

`./gradlew :devtools:publishDevtools` in JEBOL's own directory puts the plugin in `build/devtools-repo`. Building the Android package needs JEBOL's devtools built on a machine with the Android SDK, and the SDK on the machine that packages.

**The tasks:**

| Task | What it makes, under `build/jebol/` | Where it builds |
| --- | --- | --- |
| `generateProjects` | `android/`, `linux/`, `macos/`, `windows/` and `web/` in the app's directory, each only if it is not there | anywhere |
| `packageWeb` | `web/site/`: the app, the page, the runtime, a web app manifest and its icons, ready for any static host | anywhere |
| `packageAndroid` | `android/package/`: an APK and a bundle (`.aab`) for the Play Store | anywhere with the Android SDK |
| `packageDeb` | `linux/package/`: a `.deb` that carries its own Java | Linux |
| `packageMac` | `macos/package/`: a `.app`, signed ad hoc | macOS |
| `packageWindows` | `windows/package/`: an installer, and the app as a folder that runs from anywhere | Windows |
| `packageAll` | every one this machine can build; `-Pjebol.skip=android` leaves one out | anywhere |

**The generated projects are yours.** Each is written once and never again, so edit them freely and commit them: the page's `index.html`, Android's manifest and build, the `.desktop` entry, the `Info.plist`, and so on. What every platform shares -- the title, the identity, the version, the uses -- is never copied into them; each build reads it from `app.reb`, so changing `app.reb` reaches every platform. A script that one platform needs a version of its own of goes in that platform's `files/` folder, where it wins over the shared script of the same name.

**A packaged desktop app keeps a store of its own.** Its files are carried read-only, and what it writes goes to a folder named by its identity -- `~/.local/share/<id>` on Linux, `~/Library/Application Support/<id>` on a Mac, `%APPDATA%\<id>` on Windows -- with the carried files showing through beneath, as on Android. The app sees that store as `%/`.

**Signing for Android.** A dev build is signed with the machine's debug key. A release build (`-Pjebol.release=true`) is signed with a key of your own, and refuses without one, saying how to make it:

```
keytool -genkeypair -v -keystore upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

Name it in `android-signing.properties` beside `app.reb` -- `storeFile`, `storePassword`, `keyAlias`, `keyPassword` -- or in `JEBOL_ANDROID_STORE_FILE`, `JEBOL_ANDROID_STORE_PASSWORD`, `JEBOL_ANDROID_KEY_ALIAS` and `JEBOL_ANDROID_KEY_PASSWORD`, which win over the file, so a CI machine needs no password on disk. A keystore `keytool` makes today is PKCS12, which keeps one password for the store and its key, so the two are the same. Keep the keystore and its passwords out of git -- the build warns when either is tracked -- and back them up: an app signed with a lost key cannot be updated. Registering with the Play Store, and Play App Signing, are yours to do there.

**An app that runs on a server is set up from the command line, not packaged.** Every package above is an app a person opens: the site runs JEBOL in the visitor's browser, and a desktop package opens a native window. JEBOL also runs as a backend, from the shipped jar and the app's file or folder:

```
java -jar jebol.jar --screen=web my-app/     # the script runs on this machine; a browser is its screen
java -jar jebol.jar server.reb               # a script that listens itself, e.g. on tcp://:8080, with no screen
```

`--screen=web` serves the page and prints the address to open; the script runs in the JVM and the page shows its windows. A script that opens a listening port serves whatever it likes through the JVM's own sockets. How such a process is started and kept running -- a systemd unit, a container, a supervisor -- is the machine's own setup; the packager makes nothing for it.

---

## **3. Embedding JEBOL in Java Applications**

Embedding JEBOL requires configuring bounds, defining optional extensions or variables, and executing the script:

```java
Extension shop = new Extension("shop", "1.0.0", List.of(new ExtensionCommand(  
"lookup-price", "{The price of one item.} item [string!]", call -> DecimalValue.of(priceOf(((StringValue) call.arguments().getFirst()).text())))));

Interpreter interpreter = new JavaHost().withBounds(Bounds.standard().withWallClockLimit(Duration.ofMillis(200)).add(shop));
interpreter.define("vatRate", 0.2);
ScriptOutcome outcome = interpreter.run("multiply lookup-price {widget} add 1 vatRate");  
Object total = outcome.asHostValue();
```

`Bounds.standard()` sets default execution constraints: a 5-second wall-clock time limit, a maximum call stack depth of 10,000, and instruction checks every 1,000 steps. Host services and external capabilities are disabled by default.

Scripts interact with host Java logic exclusively through extensions declared in the Bounds configuration (detailed in Section 4).

REBOL string literals use curly braces `({...})`. This allows embedding strings inside Java string literals without quote escaping (e.g., "{widget}" instead of "widget").

An Interpreter instance is single-threaded and must be confined to one thread at a time. Concurrent execution requires multiple interpreter instances. The only thread-safe cross-thread method is cancel(), which halts execution from another thread.

Initial interpreter instantiation takes approximately 430 ms, settling to around 50 ms after JVM warmup. High-throughput applications creating per-request interpreters should account for initialization overhead (see docs/standing.md and scripts/timing.py boot for benchmarks).

---

## **4. Capabilities and Security Model**

By default, scripts execute in a strict sandbox without access to file systems, network adapters, system clocks, consoles, or subprocesses.

Enabling external capabilities requires granting the corresponding service in Bounds and supplying the matching port when the interpreter is built, as one HostPorts value:

| What the script must do                      | The service       | The port to supply                            |
|----------------------------------------------|-------------------|-----------------------------------------------|
| Read, write, delete, rename, list files      | FILES             | withFiles(FileSystemPort.rootFileSystem(path)) |
| Know where a relative path starts            | WORKING_DIRECTORY | the same file port                            |
| Read the names and values of the environment | ENVIRONMENT       | withEnvironment(port)                         |
| Start another program and wait for it        | PROCESSES         | withProcesses(port)                           |
| Read a line from the operator                | CONSOLE           | withConsole(port)                             |
| Ask what the time is                         | CLOCK             | none                                          |
| Open a connection to another machine         | NETWORK           | withNetwork(port)                             |
| Fetch a url with HTTP or HTTPS               | WEB               | withHttp(port)                                |
| Trust a server's certificates over TLS       | CERTIFICATES      | withCertificateAuthority(authority)           |
| Ask the operator through a window            | WINDOWS           | withWindows(port) and withScreen(port)        |
| Read and write what the operator copied      | CLIPBOARD         | withClipboard(port)                           |
| List, hear and play MIDI instruments         | MIDI              | withMidiDevices(devices)                      |
```java
HostPorts ports = new JavaHost().ports()  
.withFiles(FileSystemPort.rootFileSystem(Path.of("/srv/scripts")).readOnlyFileSystem());  
Interpreter interpreter = Interpreter.createOn(ports, OutputPort.discard(),  
Bounds.standard().grant(HostService.FILES));
```

Ports are supplied once, at construction, and never change afterwards. `new JavaHost().ports()` is the JVM's own machine, a pause that really waits, the build's own carried files, which are where the interpreter's library and font come from, and the JVM's own digests; `HostPorts.none()` is every port refusing, carried files included, and an interpreter whose carried files are refused cannot be built at all. A port the host does not supply is that port's refusing default, and the refusal a script meets names what was not supplied.

A host can explicitly mark capabilities as unavailable using lacking(...). Scripts requesting unavailable services receive a not_present error rather than not_granted.

A host that supplies an http port with withHttp(port) delegates HTTP/HTTPS requests to the host platform. If none is supplied, REBOL's default scheme handler falls back to establishing direct TCP connections via the NETWORK service.

### **Trusted certificates**

The command line trusts the authorities of the machine it runs on: the keychain on macOS, the Windows root store, or the Linux system bundle, together with the JVM's own store. A company's TLS inspection therefore reads the way it does in a browser. `--cafile path.pem` adds the authorities in that file as well; that is how a container or a build host with no readable system store is given one.

Trust is still judged. An expired certificate, one that signed itself, one naming another host, and one from an authority the machine does not trust are each refused, and the script sees which. A real 3.22.5 reads all four.

Each interpreter manages its working directory within the file port's rooted path. Because the underlying JVM process working directory remains fixed, directory state is isolated per interpreter instance.

Accessing an ungranted service raises a RAISED outcome error specifying one of three causes: not_granted (service exists but was not allowed), not_present (service unsupported by host), or never_portable (feature unsupported across hosts).

Explicit security errors prevent ambiguous failures, ensuring scripts can distinguish between missing permissions and empty or missing files.

### **What a script in an Android app may reach**

The Android app gives its script its own private storage as its whole file system, with home and the working directory at `/` and the files the app carries readable beneath it: a script may shadow a carried file with its own, but not delete one. What it writes is still there after Android ends the app. `TMPDIR` names `/tmp`, which the app empties each time it starts.

It may read the environment Android's app is given, `HOME`, `PWD`, `TMPDIR`, `LANG`, `USER` and `LOGNAME`, and nothing of the device's own. It may load and save PNG, JPEG, GIF and BMP pictures, every frame of a GIF included, but not TIFF; WebP, both ways, and HEIF, to read, are reached through `image/load/as` and `image/save/as`, since Rebol's library registers no codec for them on Android. It may open `tcp://` and `tls://` connections and read `https://`, against the device's trusted authorities; plain `http://` is refused wherever Android's network security policy refuses cleartext, which in a released app is everywhere. It may read the clipboard while the app is in front, and gets none from the background. It cannot start a program: CALL refuses as it does on a page. What it prints goes to logcat under the tag `jebol`. `docs/android-port.md` has the full table.

Past its own storage, a script reaches only what the user hands it through Android's own picker. `request-file` opens the picker, `request-file/save` its save form, and `request-dir` its folder picker. Each choice answers a path the page would answer too: `%/picked/1/notes.txt` for a file, `%/picked/2/Docs/` for a folder, the number one past the highest used. That path reaches the user's own document, not a copy. A write changes the file in Downloads or on the cloud drive it came from, and a picked folder lists, walks, and takes new files, folders, renames and deletes as far as its provider allows. A pick lasts: after the app is ended and launched again, `%/picked/1/notes.txt` still names the same file, until the user takes the app's access away in Settings. Then reading it fails with `cannot-open`. `/filter` narrows what the picker shows by MIME type, and `/title` is ignored, since Android's picker has no heading of the app's.

`browse` opens a link, a picked file, or a file of the app's own in whatever app the user views such things with. `request-password` is Android's own password dialog, so the keyboard suggests and learns nothing. `request-color` is a dialog of JEBOL's own, with hue, saturation and value sliders and a hex field, since Android has no colour picker. Each answers none when the user cancels or presses Back.

### **Creating Custom Extensions**

For a complete guide and worked example, see `docs/writing-an-extension.md`. Below is a summary of the extension architecture.

Extensions allow the Java host to expose native commands to REBOL. An extension defines a name, version string, and a list of commands pairing REBOL specifications with Java handlers. Adding an extension to Bounds registers it across created interpreters.

```java
Extension camera = new Extension("camera", "1.0.0", List.of(  
new ExtensionCommand("snap", "{Takes a photo.} quality [integer!] /flash", call -> {
    boolean flash = call.refinements().contains("flash");  
    return call.handleOf("photo", takeAPhoto(call.arguments().getFirst(), flash));  
}),  
new ExtensionCommand("photo-size", "photo [handle!]", call -> IntegerValue.of(call.heldBy(call.arguments().getFirst(), Photo.class).bytes()))))  
                    .withHandleKind(new HandleKind("photo", Photo.class))  
                    .startingWith(postbox -> requireACamera());
Interpreter interpreter = new JavaHost().withBounds(Bounds.standard().add(camera));
```
Extension behavior in REBOL:

* Command words are exported to lib as command! datatypes. Functions like spec-of and help inspect their signatures.
* Argument type validation occurs automatically before Java handlers are invoked. Passing invalid types raises an expect-arg error.
* Java handlers receive native REBOL value wrappers (e.g., FileValue) and return valid REBOL values.
* Custom errors can be thrown via call.failure(...). Uncaught Java exceptions inside extension commands translate to REBOL command-fail errors carrying the exception message.
* call.evaluated(block) evaluates block arguments within the active interpreter context.
* Extensions register under system/modules and can be imported using import '<extension-name>.

Extensions can embed initialization REBOL scripts executed after native command creation, facilitating custom schemes or codec registrations:
```java
.withScript("""  
sys/make-scheme [  
name: 'greet  
title: "Greetings"  
actor: [read: func [port] [greeting-for port/spec/host]]  
]""");

```

For example, read greet://world delegates execution to the registered scheme actor.

**Asynchronous Events and Callbacks:** Extensions receive a reference to the interpreter's Postbox. Calling postbox.post(port, "read") queues port events across threads, while postbox.callBack(...) schedules REBOL object method execution on the interpreter thread during wait cycles (e.g., wait [port 5]).

**Extension Refusal Reasons:** Extension installation is atomic; failure leaves no partial state. Call interpreter.extensions() to inspect status and refusal causes:

| Refusal        | Why                                                                                                                                                                      |
|----------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| already-used   | A command is named like a word lib already holds: a native, an action, or one of Rebol's own functions such as secure. Between two extensions, the first installed wins. |
| bad-extension  | The name is blank, the version is not a tuple, a spec does not read, or two commands share a name.                                                                       |
| handle-exists  | A handle kind another extension declared holding a different class.                                                                                                      |
| extension-init | Your start step threw.                                                                                                                                                   |
```java
Extension clash = new Extension("clash", "1.0.0", List.of(  
new ExtensionCommand("add", "a b", call -> StringValue.of("mine"))));

new JavaHost().withBounds(Bounds.standard().add(clash)).extensions();  
// [ExtensionReport[name=clash, refusal=Optional[already-used]]]

```

While lib prevents extensions from overriding existing command names, user scripts retain the standard REBOL ability to rebind words (e.g., lib/add: func [a b] [...]).

### **File Access Security Controls**

File system access undergoes a two-tier evaluation sequence:

1. **1. Host File System Root:** The file port restricts all file operations to a configured root directory. Path traversal attempts above this root are constrained to the root directory itself.
2. **2. REBOL Security Policies:** Fine-grained permission rules configured via SECURE. Policy rules can further restrict access but cannot override or expand the root boundary established by the host.

This ordering applies across all host capabilities: ungranted host capabilities cannot be re-enabled using secure allow within the script.

The following file structure illustrates traversal behavior. In error outputs below, directory paths are abbreviated for readability (/work/projects and /Users/ana), and standard ** Where: / ** Near: stack traces are omitted:
```
/work/projects/  
secret.txt  
shared/logo.txt  
app/  
main.r3  
notes.txt               "app notes"  
again.txt         ->    notes.txt                 (a link inside app)  
logo.txt          ->    ../shared/logo.txt        (a link out of app)  
assets/door.txt   ->    ../../secret.txt          (a link out of app)  
dangling.txt      ->    ../../outside-new.txt     (points at nothing yet)
```

### **Root Boundary Behavior**

Scripts observe the root folder as /. Path resolution attempting to climb above the root via .. terminates at /. Absolute paths map relative to the configured root rather than the host system root.

Symbolic links pointing within the root execute normally. Links pointing outside the root raise a security violation error displaying the path as requested, preventing host path disclosure. Symbolic link resolution supports up to 40 consecutive links before raising an error.

When using the CLI, --root /work/projects/app sets the root directory. Combined with -s (unrestricted security policy), file operations remain constrained by the root boundary:
```commandline
>> what-dir  
== %/  
>> read/string %again.txt  
== "app notes^/"  
>> read/string %../secret.txt          ; .. stops at the root: this is /secret.txt  
** Access error: cannot open: %../secret.txt reason: 3  
>> read/string %logo.txt               ; a link out of the root  
** Access error: security violation: %logo.txt  (refer to SECURE function)  
>> read/string %assets/door.txt        ; the same, one folder down  
** Access error: security violation: %assets/door.txt  (refer to SECURE function)  
>> write %dangling.txt "x"             ; writing through a link to nothing yet  
** Access error: security violation: %dangling.txt  (refer to SECURE function)  
>> exists? %logo.txt                   ; a link out looks like nothing there  
== _  
>> delete %logo.txt                    ; removes the link; shared/logo.txt stays
```

In Java, root restrictions are established when the port is built:
```java
HostPorts ports = new JavaHost().ports()  
.withFiles(FileSystemPort.rootFileSystem(Path.of("/work/projects/app")));  
Interpreter interpreter = Interpreter.createOn(ports, OutputPort.discard(),  
Bounds.standard().grant(HostService.FILES));

ScriptOutcome linked = interpreter.run("read %assets/door.txt");  
// RAISED: ** Access error: security violation: %assets/door.txt

```

Directory mutations (delete, rename, make directory) operate on the entry path itself rather than symlink targets. Ancestor path components must remain strictly within the root.

### **REBOL Policy Rules**

Embedded interpreters default to allowing operations unless restricted by SECURE or host bounds.

CLI execution triggers default initialization (matching r3), allowing reads and writes within the application and user settings directories while prompting or denying access elsewhere. Inspect active settings via secure query:
```rebol
[  
file ask  
%/work/projects/app/ allow  
%/Users/ana/.rebol/ allow  
%/Users/ana/ [allow read ask write allow execute]  
net allow  
secure allow  
protect allow  
debug allow  
envr allow  
call allow  
browse allow  
extension allow  
]
```

Executing jebol main.r3 within /work/projects/app demonstrates policy enforcement:
```
>> read/string %notes.txt  
== "app notes^/"  
>> read/string %again.txt             ; a link inside the folder is followed  
== "app notes^/"  
>> read/string %../secret.txt  
** Access error: security violation: %/work/projects/secret.txt  (refer to SECURE function)  
>> write %../text.txt "x"          ; refused, and nothing is text  
** Access error: security violation: %/work/projects/text.txt  (refer to SECURE function)  
>> read/string %logo.txt              ; judged by where the link leads  
** Access error: security violation: %/work/projects/shared/logo.txt  (refer to SECURE function)  
>> read/string %assets/door.txt  
** Access error: security violation: %/work/projects/secret.txt  (refer to SECURE function)
```

Interactive execution without a script context prompts for confirmation on ungranted file access. Non-interactive pipe executions deny access automatically. Use -s to bypass interactive prompts.

Uncaught policy violations terminate CLI execution with status code 1. Scripts can catch violations using standard error handlers (error category: access/security, argument: target file).

**Symlink Security Difference from R3:** Unlike original C-Rebol (which validates literal path strings), JEBOL evaluates resolved symlink target destinations. If a symlink points outside permitted boundaries, access is denied.

#### **CLI Security Flags**

| Command                      | What happens                                                                                                                                                         |
|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| jebol main.r3                | Rebol's default policy, as above.                                                                                                                                    |
| jebol -s main.r3             | Every policy allows. The script reads ../secret.txt.                                                                                                                 |
| jebol --secure allow main.r3 | The same as -s.                                                                                                                                                      |
| jebol +s main.r3             | Every policy asks, so start's own PROTECT is refused. The script never runs. The command line prints security violation: system/options/boot-level and exits with 1. |
| jebol --secure throw main.r3 | The same as +s.                                                                                                                                                      |
| jebol --secure quit main.r3  | Start's own PROTECT ends the process at once. Nothing is printed, and the exit status is 101.                                                                        |

Note: The behavior of +s matches Oldes/Rebol3 3.22.5 behavior, halting execution when system protection settings are violated.

#### **Programmatic Policy Restrictions**

Scripts can reduce their own permissions but cannot exceed host grants or root limits:
```
>> secure [file [allow read throw write]]  
>> read/string %notes.txt  
== "app notes^/"  
>> write %notes.txt "changed"  
** Access error: security violation: %/work/projects/app/notes.txt  (refer to SECURE function)

>> secure [envr throw call throw net throw debug throw]  
>> get-env "HOME"  
** Access error: security violation: "HOME"  (refer to SECURE function)  
>> call/wait "true"  
** Access error: security violation: "true"  (refer to SECURE function)  
>> open tcp://example.com:80  
** Access error: security violation: tcp://example.com:80  (refer to SECURE function)  
>> trace on                            ; no subject, so the class is named  
** Access error: security violation: debug  (refer to SECURE function)
>> secure [file 5]  
** access error: invalid file security policy: 5

**Evaluation Note:** SECURE receives unevaluated block arguments. Dynamic block composition requires parenthesized expressions: secure (compose [file throw (folder) allow]).

>> secure compose [file throw (folder) allow]  
** access error: invalid all security policy: compose
```

Security levels and actions:

| Level | What happens                                                                                            |
|-------|---------------------------------------------------------------------------------------------------------|
| allow | Permits the requested operation.                                                                        |
| ask   | Prompts interactively at the console. At the command line that is the terminal or pipe it was started on, edited as R3 edits a line; on a site it is a form on the page. Without a console, requests fail automatically. |
| throw | Raises a catchable REBOL security error identifying the target resource.                                |
| quit  | Terminates script execution immediately with exit status 101. Cannot be caught using try.               |

Java hosts can apply policies prior to running user code, bound by maximum host capability grants:
```java
interpreter.run("secure [file [allow read throw write]]");  
interpreter.run("write %notes.txt {changed}");  
// RAISED: ** Access error: security violation: %/notes.txt

interpreter.run("secure allow");  
interpreter.run("call/wait {true}");  
// RAISED: processes is not granted. SECURE cannot grant what the host held back.
```

#### **Checked Operations vs. Unchecked Operations**

JEBOL matches REBOL C security check placement precisely:

| Checked                                                                               | Against                         |
|---------------------------------------------------------------------------------------|---------------------------------|
| READ, LOAD and DO of a file. OPEN without /write, and OPEN/read.                      | file read                       |
| WRITE, WRITE/append, CREATE. OPEN without /read, OPEN/write and OPEN/new.             | file write                      |
| READ, OPEN, QUERY and EXISTS? of a folder                                             | file read                       |
| MAKE-DIR, DELETE and RENAME of a folder                                               | file write                      |
| OPEN of a tcp or udp port. HTTP and HTTPS are checked through the tcp port they open. | net execute, naming the address |
| CALL                                                                                  | call execute                    |
| BROWSE                                                                                | browse execute                  |
| GET-ENV / SET-ENV                                                                     | envr read / write               |
| PROTECT / UNPROTECT                                                                   | protect write / read            |
| EVOKE, STACK, TRACE                                                                   | debug read                      |
| LOAD-EXTENSION of a file                                                              | extension execute               |

The following operations do not perform policy checks (matching C behavior), though they remain strictly constrained by the host file system root:
```
>> size? %../secret.txt  
== 7  
>> rename %../secret.txt %../renamed.txt    ; goes ahead  
>> delete %../renamed.txt                   ; goes ahead  
>> exists? %../shared/                      ; a folder: queried, refused, so none  
== _
```
* DELETE, RENAME, QUERY and MODIFY of a file. This includes SIZE?, MODIFIED? and EXISTS? of a file.
* ECHO and IMAGE/SAVE, which write without the file actor.
* A port that is already open. OPEN was checked, and reads and writes after it are not.
* LIST-ENV, PROTECTED? and reading a dns:// url.
* The eval and memory limits. LIMIT-USAGE is gone before any script runs, in R3 as here, so secure [eval 1000] fails with limit-usage has no value.

File root constraints remain active across all unchecked operations.

---

## **5. Execution Outcomes and Termination**

Every script execution returns a ScriptOutcome representing one of six outcome conclusions. Inspect status using outcome.conclusion() or outcome.succeeded():

| Conclusion       | What it means                                                 | Where the value is      |
|------------------|---------------------------------------------------------------|-------------------------|
| PRODUCED_A_VALUE | The script finished.                                          | asHostValue()           |
| RAISED           | The script failed, and it could have caught the error itself. | errorId() and display() |
| TIMED_OUT        | The time limit passed first.                                  | none                    |
| CANCELLED        | The host called cancel().                                     | none                    |
| QUIT_EARLY       | The script called QUIT.                                       | asHostValue()           |
| HALTED           | The script called HALT and gave control back.                 | none                    |

**Note:** Distinguish between CANCELLED (host initiated termination) and QUIT_EARLY (script self-termination via QUIT).

Always verify outcome.succeeded() before calling asHostValue(), as failed executions return the error representation when coerced directly:
```java
ScriptOutcome outcome = interpreter.run(source);  
if (!outcome.succeeded()) {  
    log.warn("script stopped: {} {}", outcome.conclusion(), outcome.display());  
    return fallbackValue;  
}  
return outcome.asHostValue();
```
Interpreter instances remain valid and reusable after script termination, timeouts, or errors. Execution state transitions leave data structures in a consistent state.

---

## **6. Data Type Conversion between Java and REBOL**

Data values map naturally between Java and REBOL types. Unmapped types are preserved as host object wrappers to avoid lossy conversions.

**Java to REBOL Type Mapping (via** define**):**

| Java                       | REBOL                                                                   |
|----------------------------|-------------------------------------------------------------------------|
| Boolean                    | logic!                                                                  |
| Long, Integer, Short, Byte | integer!                                                                |
| Double, Float              | decimal!                                                                |
| BigDecimal                 | money!                                                                  |
| String, Character          | string!                                                                 |
| List<?>                    | block!                                                                  |
| a REBOL value              | itself                                                                  |
| null                       | Wrapped as java-object! containing Java null (distinct from REBOL none) |
| anything else              | java-object!, held and not converted                                    |

**REBOL to Java Type Mapping (via** asHostValue()**):**

| REBOL                                                     | Java                         |
|-----------------------------------------------------------|------------------------------|
| integer!                                                  | long                         |
| decimal!, percent!                                        | double                       |
| money!                                                    | BigDecimal                   |
| logic!                                                    | boolean                      |
| string!, file!, url!, tag!, email! and every other string | String                       |
| block!, paren!, path! and every other block               | List\<Object>                |
| java-object!                                              | the object it holds          |
| none!, unset!                                             | null                         |
| anything else                                             | the molded text, as a String |

Java null and REBOL none are kept distinct during conversion. Use asOptionalHostValue() to map both to an empty Java Optional.

---

## **7. Known Limitations and Status**

Review this section prior to integration. It details feature coverage, pending work, and intentional deviations from C-Rebol.

**Test Suite Parity:** All assertions in REBOL's test suite either pass in JEBOL or represent known failures present in standard Oldes/Rebol3 3.22.5 (documented in fails-on-rebol-too.txt). Intentional architectural differences are tracked in docs/standing.md.

**Datatypes and System Features:** All standard R3 datatypes are implemented. task! execution requires host thread support enabled via withTasks. OS user context fields in access-os (uid, euid, gid, egid) return not-here due to JVM platform abstractions. Scheme names match R3 registrations, failing at open time if unhandled by the host.

**TLS Transport Security:** Unlike stock R3 3.22.5, JEBOL enforces strict TLS certificate validation. HTTPS connections verify server certificates using host trust stores configured via CERTIFICATES grants.

**Memory Management:** Memory exhaustion raises REBOL no-memory errors which scripts can catch. Process heap allocation should be restricted at the JVM level when executing untrusted scripts.

**Graphics and Rendering:** DRAW supports all dialect commands except effect. VID is not included. Rendering parity between Java2D and browser targets is verified via automated end-to-end tests (./gradlew endToEndTests).

For current test statistics and benchmark numbers, run ./gradlew allTests.

---

## **8. Worked Examples (Pending Documentation)**

**Upcoming content:** This section will include six fully executable reference programs with accompanying automated tests, covering the following integration scenarios:

* a script under a time limit
* a call from the script back into Java
* a read of a file through a granted directory
* a script that fails, and a host that handles the failure
* a small dialect

These samples will be bundled directly within the distribution package.

Automated test execution ensures code examples remain accurate across releases.

---

## **9. Word Reference (Pending Documentation)**

**Upcoming content:** This section will provide an automatically generated reference listing all functions, signatures, and arguments exported by the interpreter engine.

Reference generation utilizes API parity reports generated by scripts/c-surface.py and scripts/c-parity.py.

---

## **Further Reading**

Additional specifications available in the source repository:

| The question                                        | The file            |
|-----------------------------------------------------|---------------------|
| What a host can decide, and what a script can reach | spec/embed.allium   |
| What PARSE supports, keyword by keyword             | spec/parse.allium   |
| How a dialect is read by DELECT                     | spec/dialect.allium |
| What a REBOL value is                               | spec/values.allium  |
| What each function does                             | spec/natives.allium |
| How source text becomes values                      | spec/load.allium    |
| How a block is evaluated                            | spec/eval.allium    |

Specification files reflect target language behaviors verified against C-Rebol. Known implementation differences in JEBOL are detailed in Section 7.

---

## **Licence and Attribution**

JEBOL is Copyright © 2026 Ben Hoskins, licensed under the Apache License, Version 2.0. The full license text is available in the LICENSE file included with the distribution.

**Oldes/Rebol3 Port Attribution:** JEBOL is a port of Oldes/Rebol3 ([https://github.com/Oldes/Rebol3](https://github.com/Oldes/Rebol3)), which derives from REBOL (Copyright © 2012 REBOL Technologies, Copyright © 2012–2026 Rebol Open Source Contributors) under the Apache License 2.0.

**Bundled REBOL Mezzanine Libraries:** Files located under org/jebol/mezz/ inside the JAR are upstream REBOL mezzanine scripts, modified only in prot-tls.reb (adding certificate checks) and mezz-banner.reb (updating the startup banner). Each file preserves its respective copyright header.

See the NOTICE file for complete attribution listings, which must be retained when redistributing JEBOL.

REBOL is a registered trademark of REBOL Technologies. JEBOL is an independent project and is not affiliated with or endorsed by REBOL Technologies or the maintainers of Oldes/Rebol3.

