# What to look out for when JEBOL first runs on a Mac

Written 2026-11-26, before JEBOL's suites have run on macOS, as predictions
read from the code. Windows taught what this kind of list is for: CI's first
Windows runs failed on paths, line endings, file locks, a code page and a
quoting rule, and nearly every failure came from code that assumed how a
file system or a command line behaves. macOS shares Linux's line endings,
path separator and file locks, so those will not bite. Where it differs
from Linux, they will. Goal 20 step 5 is where they get found; each entry
says where to look and what R3 does, which is the answer to aim for.

## Very likely

### Temporary folders behind a link

`/var` is a link to `/private/var` and `/tmp` to `/private/tmp`. Java's
temporary folders and JUnit's `@TempDir` are under `/var/folders/`, and the
suites keep their own temporary files under `/var/tmp` (`JebolTests.kt`,
`DISK_BACKED_TEMPORARY_ROOT`). Anything that resolves a path answers
`/private/...` where JEBOL was handed `/var/...`: `to-real-file`,
`clean-path`, `what-dir` against a resolved name, `query ... 'name`, and any
JEBOL test that compares a host path it built with one the host resolved.
It is the same family as Windows' 8.3 short names, which `SuiteHost` fixed
by taking the run folder's real path, on Windows only (`realPathOf`). On a
Mac the likely fix is the same, everywhere a root is set up.

## Likely

### A disk that ignores case, though JEBOL's file port minds it

APFS, as Macs ship it, ignores case and keeps it: `a.txt` and `A.txt` are
one file. `FileSystemPort.ignoresCaseInNames()` answers yes only on Windows,
so on a Mac JEBOL believes they are two. Look at wildcards, `exists?` and
`read` of a directory against names written in another case, and at tests
that make two files whose names differ only by case. Check what R3's POSIX
build answers on a Mac before choosing: it asks the file system rather than
deciding itself.

### Retina and Mac font smoothing in every picture test

A Retina screen draws at twice the size, and macOS smooths text its own way.
The Java2D references painted on Linux
(`Java2DParityReferencesFromTheSourceTest`), the browser against Java2D
comparison (`PageRendersLikeTheDesktopTest`) and any test that reads a
screen capture in pixels may meet doubled sizes and different edges. Find
where a test assumes one pixel per point before loosening any tolerance.

### Screen Recording and Accessibility are asked for, not given

macOS asks the user before a program may capture the screen or send input.
Until it is granted, a screen capture shows the desktop without the
window's content, and `Robot`'s keys and clicks never arrive, so the window
tests that capture or type fail for no fault of JEBOL's. On the CI Mac they
are granted once, by hand, to the Java the job runs; a failure that looks
like a blank window should be checked against that first.

## Possible

### Names stored decomposed

macOS file systems may hand back a name in decomposed form (`č` as `c` and a
combining caron) or treat the two forms as one name. A name read back from
a folder can then differ, byte for byte, from the same name written in a
script; `čeština.txt` in Rebol's suite is the first to watch.

### Cmd where Linux has Ctrl

Copy, paste and select-all are Cmd chords, and Back and Forward come from
other keys and buttons. `DesktopSystem` already knows macOS
(`DesktopSystem.MACOS`, used by `BackForwardChords`); tests that send Ctrl
chords, or that expect Linux's back and forward, may answer differently.

### The packaged app on macOS

- The menu bar names the app from `apple.awt.application.name`, which
  `DesktopScreen` sets to `JebolIcon.APPLICATION_NAME`, "JEBOL", even for a
  packaged app; a packaged app should be named by its title.
- The Dock shows the window's icon, now the app's own when packaged
  (`AppIcon`), and the `.app`'s `.icns` before it starts: check both agree.
- An ad-hoc signed `.app` is quarantined when it is downloaded, and
  Gatekeeper refuses to open it until it is cleared; `app-on-my-mac.sh`
  builds and opens it locally, which does not meet that.
- A Mac app does not quit when its last window closes; a test that expects
  the process to end then may wait.
- The store is already where macOS keeps app data
  (`~/Library/Application Support/<identity>`, `PackagedApp.storeFolder`).

## Unlikely

Line endings and the path separator, as on Linux. Process numbers: process
1 is `launchd`, which an ordinary user may not signal, so `access-os`
answers permission-denied as on Linux. File locks, which are POSIX. The
shell: `/bin/sh` runs scripts as Linux's does. Java's own encoding, which is
UTF-8 for files and for the command line.
