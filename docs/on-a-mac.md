# JEBOL on a Mac: what broke the first time, and what to look out for

On 2026-11-26 the owner ran `./gradlew --continue allTests` on an Intel Mac,
at commit 6713da66, before any Mac had been in CI. Of about 36,000 tests,
129 failed, from eight causes. The functional suites, 26,978 tests, passed
whole: the predictions written that morning, temporary folders behind the
`/var` to `/private/var` link, a disk that ignores case, names stored
decomposed, did not show. What did show is below, with what was done.

## What failed, and what was done

**The console's terminal was never "restored" (106 tests, one cause).**
`TheCommandLinesConsoleAnswersAsR3EndToEndTest` compared `stty -g` before
and after byte for byte. A new macOS pseudo-terminal starts with the
kernel's PENDIN bit set in `lflag`, and the kernel clears it itself once
input is read, so the two never matched whatever JEBOL did. The test now
leaves out the two local-mode bits only the kernel sets, PENDIN and FLUSHO,
and a mismatch names the fields that differ. If the Mac still says
`changed`, those fields say what JEBOL failed to put back.

**Tests that assumed Linux (11).** The devtools tests named "on Linux" ran
on the Mac and met a Mac's answers: `packageDeb` refuses there and
`packageMac` builds. They run on Linux only now, and macOS has its own:
`packageDeb` and `packageWindows` refuse, and `packageAll` builds the web
and the `.app`. `TheLinuxPackageTest` and the `.deb` window test run on
Linux only. The packaged-app store test looked in Linux's data folder; it
asks `PackagedApp.storeFolder` now, and a Mac test checks
`~/Library/Application Support`. The no-display tests take X11's `DISPLAY`
away, which only Linux has; they run on Linux only. zlib-ng's gzip header
names the host as zlib-ng's `OS_CODE` does, 19 on macOS, and the test
expected Unix's 3; it now checks that byte for each system on its own.

**bzip2 streams ran past 5 seconds (8).** Each test made 300,000 bytes of
noise with a REBOL loop first, about 1.5 seconds on Linux and 5 on the
Mac, before bzip2 ran at all. The same bytes come from Java now, and the
tests take a tenth of a second.

**The JDK's process reaper ran out of memory (1).** At start JEBOL asked
`sh -c 'test -t 0'` whether its input was a terminal, which leaves the
JDK's process reaper thread waiting 60 seconds. The slower Mac was still
filling the heap when it woke, and it printed Java's own OutOfMemoryError.
`StandardInput` now reads what the input is first: a file, a pipe or
`/dev/null` is no terminal, and only a character device is asked about.

**Firefox did not start (3 of its starts).** "Process unexpectedly closed
with status 0", the Mac's sign of two Firefox launches meeting. The test
processes each start their own browser, so `PageBrowsers` now starts one
at a time across them, through a lock file, and tries a start that fails
up to three times, keeping every reason.

**Arranging a window (2): not fixed yet.** An operator's maximise was heard
as a resize, a restore and a run of resizes, with the frame back in state 0;
a script's minimise and restore hung until the owner stopped it after eight
minutes. The class's 120-second limit ran on the stuck thread, so it never
fired; it runs on a thread of its own now. What macOS reports is to be read
before any change: the owner runs a probe that maximises, restores,
minimises and brings back a window and records every state and resize macOS
reports.

## The second run

The same day, at e75baa6e, everything above passed but two things.

**`checkSpec`.** The Mac's `allium` was a newer one than the 3.6.1 the
build pins. It refused `values.GobStorage` for `values/GobStorage` and
warned about a field `Evaluation` declares. The spec now uses the slash
form, which both read the same. `scripts/allium-version` holds the pin, and
`checkSpec` refuses any other version, with the line that installs the
right one.

**Arranging a window, again.** The operator's restore was heard, but the
window still had its maximised size 50 milliseconds later. macOS animates
the un-zoom, so the test now waits for the size to come back. The script's
minimise and restore hung for 36 minutes: the test's time limit fired, then
its `@AfterEach` closed the window with no limit, against the same stuck
toolkit. Opening and closing have limits of their own now. Any timeout writes
every thread's stack into the result XML, so the next hang says where it
stuck.

**What the probe showed.** The owner's probe of a plain `JFrame` on macOS
26 answered three things:
- Maximise and restore are animated. The restore's state change comes at the
  start, and the resizes go on for a third of a second after it, which is the
  operator test's late size.
- Minimising, and coming back from the dock to normal, work as asked. There is
  no need for the hide and show that X11 needs.
- Asking a minimised window to maximise never returns. The toolkit thread is
  stuck for good, and every later request sticks behind it: the JDK's own
  deadlock, and the script test's hang.

So on macOS (`DesktopSystem.bringsMinimisedWindowBackInPlace`), a script's
restore brings the window back in place. Its maximise first restores, waits
for the state to leave the dock, and only then maximises, while the reports
are held as the X11 remap holds them. The script still hears
`resize maximize`. Windows and X11 keep the remap.

## The third run

At 9e149fdf the run took 23 minutes, nothing hung, and `checkSpec` passed
on the pinned allium. Two tests failed.

**Restoring a maximised window from a script.** It worked, and the script
heard `restore`. The test then waited for `seen` to end with `restore`, but
macOS's un-zoom animation keeps sending resizes after it, so it never did.
The restore steps now wait until `restore` has been heard at all.

The fourth run showed the same for maximising: the zoom animation sends one more
`resize` after `maximize` (`[… resize maximize resize]`), so a step waiting for
the events to end with `maximize` waited forever. Each maximise step now waits
until `maximize` has been heard and the window's size matches its maximised
content pane, and it accepts either order of events. Its polling waits on the
event port itself, and the `Thread.sleep` it used is gone.

**A TLS conversation waited out its 30 seconds.** This was JEBOL's own bug,
which the Mac's timing showed. The server's page and its `close_notify`
reached the client in separate TCP reads. Once the session had closed,
`prot-tls.reb` read no further, so the TCP close was never read and the
script never heard `close`. On Linux the two usually arrived together. The
session now keeps reading after it closes until the connection's own close
comes. `LocalTlsServer` can hold its close until it is released, and
`ReleasingAfterPage` releases it at the moment the client asks for its next
read after the page has arrived. That makes the two reads separate every time,
on any machine, with no timing in the test. Without the fix the test waits out
its 30 seconds as the Mac did; with it, the test takes about a tenth of a second.

## Still to look out for

- **Retina and Mac font smoothing in picture tests.** None failed on this
  Mac's run, which may have been on a screen at one pixel per point.
- **Screen Recording and Accessibility.** Window tests that capture or type
  need both granted to the Java the job runs; a blank capture is that first.
- **Cmd where Linux has Ctrl.** `DesktopSystem.MACOS` already chooses the
  chords; no test failed on it.
- **The packaged `.app`.** The menu bar names the app from
  `apple.awt.application.name`, which `DesktopScreen` sets to "JEBOL" even
  for a packaged app. Gatekeeper quarantines a downloaded ad-hoc signed
  `.app`, which a locally built one never meets. A Mac app does not quit
  when its last window closes.
