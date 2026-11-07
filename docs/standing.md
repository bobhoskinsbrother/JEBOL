# Where the port stands

The counts, the measures and the deliberate differences from a real 3.22.5 live
here and nowhere else. `README.md`, `docs/porting-guide.md`, `using-jebol.md` and
`CLAUDE.md` point here for them. What is left to do is `goals.md`.

## The measures

Measured on 2026-10-12, the jar and web build rows on 2026-11-06,
and the timing rows on 2026-10-13. The timing rows were taken on an arm64 Mac; the same two figures on
an Intel one read about 122ms median and 187ms p95, so compare a change against
a baseline from the same machine rather than against the row.

| Measure                       | Reads                                                                                                                                                                                                             |
|-------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `fails-on-rebol-too.txt`      | 160 a real 3.22.5 also fails or never runs                                                                                                                                                                        |
| `scripts/c-parity.py`         | 279 of 279 C functions match R3's surface                                                                                                                                                                         |
| `scripts/runtime-parity.py`   | 0 of 582 absent; 3 differ, all deliberate -- see below                                                                                                                                                            |
| `scripts/error-parity.py`     | 120 of Rebol's 142 error ids can be raised                                                                                                                                                                        |
| `PortingBacklogTest`          | reports `init-schemes` missing, wrongly -- see goal 5                                                                                                                                                             |
| `corpus/`                     | 1,140 of 1,140 entries pass                                                                                                                                                                                       |
| the shipped jar               | 3,939 KB, of which the 2010 GUI is 75 KB before compression, and no dependencies                                                                                                                                  |
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
  real 3.22.5 and is refused here, and a TLS 1.2 elliptic curve server reads
  here and fails there. The vendored `prot-tls.reb` carries four edits for
  it. Decision 43 and finding 222 say what and why.
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
  A browser runs none. `TheFateOfATasksBodyIsTheHostsToDecide` in
  `spec/natives.allium`, decision 30.
- **No Java PDF implementation in the interpreter jar.** The borrowed codec is
  what Rebol has; more than that is a library the caller adds, which is goal 12.
- **bzip2 answers where its C never does.** Decision 62.
