# Where the port stands

The counts, the measures and the deliberate differences from a real 3.22.5 live
here and nowhere else. `README.md`, `docs/porting-guide.md`, `using-jebol.md` and
`CLAUDE.md` point here for them. What is left to do is `goals.md`.

## The measures

Measured on 2026-10-12, the jar row on 2026-11-08, the web build row on 2026-11-06,
and the timing rows on 2026-10-13. The timing rows were taken on an arm64 Mac; the same two figures on
an Intel one read about 122ms median and 187ms p95, so compare a change against
a baseline from the same machine rather than against the row.

| Measure                       | Reads                                                                                                                                                                                                             |
|-------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `fails-on-rebol-too.txt`      | 160 a real 3.22.5 also fails or never runs, and apart from them 1 JEBOL fails on purpose (`image-diff/part`, below)                                                                                               |
| `scripts/c-parity.py`         | 279 of 279 C functions match R3's surface                                                                                                                                                                         |
| `scripts/runtime-parity.py`   | 0 of 582 absent; 3 differ, all deliberate -- see below                                                                                                                                                            |
| `scripts/error-parity.py`     | 120 of Rebol's 142 error ids can be raised                                                                                                                                                                        |
| `PortingBacklogTest`          | reports `init-schemes` missing, wrongly -- see goal 5                                                                                                                                                             |
| `corpus/`                     | 1,140 of 1,140 entries pass                                                                                                                                                                                       |
| the shipped jar               | 4,103 KB, of which the 2010 GUI is 75 KB before compression and the UTS #46 tables 56 KB, and no dependencies                                                                                                    |
| the web build                 | 16.9 MB of WebAssembly (5.1 MB gzipped) beside a 38 KB loader, a 9.6 KB worker and a 556 KB font; the whole site 17.6 MB, 5.4 MB gzipped                                                                          |
| a warm `Interpreter.create()` | 44 to 57ms median across runs (45.5ms on 2026-10-22, with the GUI carried and not loaded), 66 to 73ms p95, the first call 424 to 427ms; 16,300 of them per `./gradlew functionalTests` (`scripts/timing.py boot`) |
| the interpreter's speed       | 8x to 14x slower than a real 3.22.5 on `scripts/timing.py speed`; about 2x more again in the browser                                                                                                              |

## Where JEBOL differs from a real 3.22.5 on purpose

- **`request-color`, `request-dir` and `request-file` answer `native!` where R3
  answers `function!`, on purpose.** R3 shells out to `osascript` on macOS
  only; JEBOL serves them through its own port. See `mezz/ORDER.txt` before
  changing it.
- **A TLS server must be trusted, where R3 trusts every one, on purpose.** An
  expired, self-signed, wrong-host or untrusted-root certificate reads in a
  real 3.22.5 and is refused here. The handshake is the host's own TLS, so
  what a platform's other programs read reads here too: TLS 1.2 elliptic
  curves, finite-field Diffie-Hellman, servers asking for a client certificate,
  Ed25519, 8192-bit RSA and Unicode hosts all read here and fail in a real
  3.22.5, and a chain sent out of order or with its leaf twice reads in both.
  Decisions 43 and 65, finding 222.
- **R3's listening TLS server answers clients here, and answers none in a real
  3.22.5.** Finding 237.
- **The command line trusts the machine's authorities, and a certificate with
  a GeneralizedTime validity reads here, on purpose.** The keychain, the
  Windows root store and the Linux bundle are added to the JDK's, and
  `--cafile` adds a bundle of its own; the verdicts above still hold. The
  borrowed DER/CRT codecs now decode GeneralizedTime, which X.509 permits, so
  a server behind a company's TLS inspector reads here and stops a real
  3.22.5 at "Missing public key in certifiate". The nine github assertions
  that came off `fails-on-rebol-too.txt` for that reason are this divergence.
  Decision 47.
- **A file is judged by where it really leads, where R3 judges the path as
  text, on purpose.** A symbolic link in a script's own folder pointing out
  of it reads in a real 3.22.5 and is a security violation here, naming where
  it leads; a link leaving a host's `--root` is refused even with every policy
  at allow. Links that stay inside are followed. `AFileIsJudgedByWhereItReallyLeads`
  in `spec/natives.allium` and `ALinkLeavingTheRootIsRefused` in
  `spec/embed.allium`.
- **An app has a history, and `system/catalog/event-types` has 49 types where
  R3 has 47, on purpose.** `back` and `forward` come after the C's last, so no
  index the C fixed moves; `go`, `go-back`, `go-forward`, `current-place` and
  `unsaved-work` are JEBOL's own words. Decision 50 and `spec/nav.allium`.
- **A task's body runs, where R3 never runs it, on purpose.** R3 makes a
  task and DO answers it, but nothing ever starts the body: an obvious bug.
  JEBOL runs it where a host supplies a way to: on a thread of its own in a
  fresh interpreter on the JVM, which the console and the command line wire.
  The body crosses as a deep copy the child owns, made when DO is called.
  A browser runs none. `TheFateOfATasksBodyIsTheHostsToDecide` in
  `spec/natives.allium`, decisions 30 and 70.
- **No Java PDF implementation in the interpreter jar.** The borrowed codec is
  what Rebol has; more than that is a library the caller adds, which is goal 12.
- **bzip2 answers where its C never does.** Decision 62.
- **An operator with nothing on its left takes NONE here where R3 reads a
  cell it does not own.** `reduce next [5 <> 0 7]` is `[#(true) 7]` here and
  `[7]` there, `print <> 0` prints `true` here and the PRINT native there, and
  `reduce/into next [5 + 1] []` refuses `none!` here and `block!` there.
  Everywhere R3's answer is deterministic, including the `missing-arg` its
  own suite asserts for `do next [1 <> 0]`, JEBOL answers the same.
  Decision 66.
- **On Windows the console's modes change nothing, and `read-key` reads no
  key.** R3's Windows device turns the console's own echo off and reads keys
  through the console API; JEBOL accepts `echo`, `line` and `error` there and
  does nothing, and answers none for a key. Linux and macOS answer as R3's
  posix host does. Decision 64.
- **A password past ASCII is read as UTF-8, where R3 makes one character per
  byte.** `aéb` is three characters here and four there. Decision 64 and
  finding 236.
- **SQLite is a jar beside JEBOL's, not inside it.** `jebol-sqlite.jar` serves
  Rebol-SQLite 3.46.0.0 on the command line and in a site built with it; without
  it `import 'sqlite` fails. It asks SECURE where the C never does, and answers
  where the C crashes. Decision 63 and `docs/sqlite-port.md`.
- **The brotli module refuses what CVE-2026-111497 lets through.** Rebol-Brotli
  0.1.0 is built from a Brotli before the fix, so a real 3.22.5 that imports it
  waits for more input on a stream it should refuse, and overruns a buffer
  doing so; JEBOL refuses it, as Brotli 1.2.1 does. It also finishes a level 0
  or 1 stream given `none` where the C crashes. Decision 58 and
  `docs/brotli-port.md`.
- **`check` on a vector or an image answers it, where R3 may raise.** The C
  walks the series' own memory a byte at a time, so `check make vector! [1 2
  3]` raises `bad-series` on the zero byte inside the first number, and
  `check make image! 2x2` the same. A JEBOL vector holds numbers, not bytes,
  so there is nothing to walk. CHECK is a debugging native that asks about
  memory rather than data, and the owner dropped a byte-exact one.
  `CheckAnswersTheSeriesItWasGiven` in `spec/natives.allium`.
- **`find` and `select` in a binary find every byte, where R3 finds none above
  127, on purpose.** `Find_Str_Char` gives up on any number above `0x7F`
  before it looks, so `find #{00FF} 255` and `select #{FF01} 255` are none in a
  real 3.22.5, under every refinement. A char or a binary needle finds 255 in
  both. Decision 71; `FindingAByteInABinaryFindsEveryByte` in
  `spec/natives.allium`.
- **`make image!` reads a block of tuples, where R3 refuses every block, on
  purpose.** `Create_Image` reads the block and never steps past it, so
  `make image! [2x1 [1.2.3 4.5.6]]` is `malconstruct` there. The block means
  what `Tuples_To_RGBA` makes it mean in APPEND and CHANGE: one tuple a pixel,
  a fourth byte as transparency (alpha 255 less the byte). A block holding
  anything else is `invalid-arg` naming that value, where R3's points past the
  block and names nothing. Decision 71; `ABlockOfTuplesIsOneTupleAPixel` and
  `ABlockOfTuplesHoldingAnythingElseNamesIt`.
- **`image-diff/part` clips a rectangle at the edge, where R3 answers 0%, on
  purpose.** `n-image.c` subtracts the size twice, so any rectangle running
  past the right or bottom compares nothing. A corner past the far edge is
  refused exactly as R3 refuses it. Rebol's own `image-test.r3` asserts the
  0%, and that one assertion is under "Assertions JEBOL fails on purpose" in
  `fails-on-rebol-too.txt`. Decision 71; `ARectanglePastTheEdgeIsClippedToIt`.
- **A second OPEN of MIDI or the clipboard is `already-open`, on purpose.** R3's
  MIDI actor connects again and drops the first connection without closing
  it; its clipboard on Linux refuses every action after the first OPEN with
  `expect-arg`. File, directory CREATE and crypt already refuse in both, and
  checksum, dns, console and event answer the port in both. Decision 71;
  `OpeningAnOpenMidiPortIsRefused` and `TheClipboardRefusesToBeOpenedAgain`.
