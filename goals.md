# Goals

What is left to do on this port, in one order. A finished goal, or a finished
part of one, is deleted; what it said is in the git history.

Nothing else goes here. What has been decided is in `docs/decisions.md`, where
the port stands and where it differs on purpose is in `docs/standing.md`, and
how to work on any goal is in `docs/porting-guide.md`.

---

## The goals

Ordered by importance. Any Rebol file running the same on a real 3.22.5 and
on JEBOL comes first (goal 1). The two that take JEBOL to Android and iOS
come next, with the defects that would hurt there fixed before each stage.
Agreeing with the C comes before improving on it: a place where JEBOL answers
something a real 3.22.5 does not is a defect.

### 1. Any Rebol file runs as it does on a real 3.22.5, and the loose ends

Eleven pieces of work, in the order to do them.

**1b to 1g come first: each makes the same script answer differently on a
real 3.22.5 and on JEBOL.** The aim is that any Rebol file runs the same on
either. Ordered by how much ordinary code they break: the modules a script
can ask for (1b), the console and
https (1c, 1d), then what a script sees when it fails (1e to 1g).

**1h to 1l change nothing a script run on both would see**: the spec, a
process-wide table, confinement and tasks, and then the five Rebol bugs
JEBOL fixes on purpose (1k), which make the two differ deliberately.

**And one Rebol file that does not run here is this project's own**:
`scripts/resolve-nest.r3`, the pre-make step `scripts/build-r3.sh` needs, so
JEBOL cannot bootstrap `./r3-head` and a release has to be fetched to get the
first one (the script says how). It stops at `foreach key listed [spec/:key:
copy []]` with `invalid-path`, naming `:key`. Measured on 2026-10-25: the same
write works on a fresh map, in a `foreach`, after `secure [file allow]`, and
with `scan-names` run over a small literal block -- it fails only once
`scan-names` has walked the real `rebol3-source/make/rebol3.nest`, which is
1,200 lines of `set-word!` and `to word!`. Shrink that input until the
smallest failing case is one line before reading any code.

#### 1b. The `brotli` module, and the streaming encoder it needs

`compress` and `decompress` with `'br` already run on JEBOL's Brotli and
write R3's bytes. `system/modules/brotli` is something else: a link to
Oldes/Rebol-Brotli 0.1.0, a native extension R3 downloads on `import brotli`.
Its source is at https://github.com/Oldes/Rebol-Brotli (`src/brotli-rebol-extension.r3`
declares the commands, `src/brotli-commands.c` implements them).

What `import brotli` gives a script, against what JEBOL's port has:

| Command                                                      | What it does                                                  | In the Java port            |
|--------------------------------------------------------------|---------------------------------------------------------------|-----------------------------|
| `version`                                                    | the Brotli library's version                                  | no; the vendored C is 1.2.0 |
| `compress` `/part` `/level`                                  | compress in one call                                          | yes, `Brotli.compress`      |
| `decompress` `/part` `/size`                                 | decompress in one call                                        | yes, `Brotli.decompress`    |
| `make-encoder` `/level`, `make-decoder`                      | a handle holding a stream's state                             | no                          |
| `write` `/flush` `/finish`, `read`                           | feed a stream in chunks and take its output                   | no                          |
| an encoder's `/mode` and `/size-hint`, a codec's `/finished` | tune the encoder; ask whether a stream ended                  | no                          |
| on import                                                    | appends `br` to `system/schemes/http/headers/Accept-Encoding` | trivial                     |

The port compresses and decompresses a whole input in one call. It has no
encoder that keeps its state between calls, never flushes a block partway
through, and takes no mode and no size hint. None of that is cosmetic: the
extension sets `BROTLI_PARAM_MODE` and `BROTLI_PARAM_SIZE_HINT`, and
`docs/brotli-port.md` records that the size hint changes which of the four
hashers runs.

**It is a bundled extension, not a jar of its own.** Decision 58: it needs no
library, because the Brotli port is already in the core, so it is served from
inside JEBOL's jar as bzip2 and zstd are. It is an `Extension` with its own
header, waiting in `system/modules` until `import 'brotli`, the route goal 1a
gave all eight bundled extensions. Decision 63's own jar is for an extension
that needs libraries or should not ship in every build; neither applies.

**What the source says.** Read on 2026-11-07, at tag `0.1.0` (`5e93cd0`,
2025-12-22), the tag R3's `system/modules/brotli` address names. `master` has
since moved to 1.2.1 and is not what R3 asks for.

- **The header** is `REBOL [Title: {Rebol Brotli Extension} Type: module
  Version: 0.1.0 needs: 3.20.5]`, with no `Name:` and no `Exports:`. The commands
  are `init-words` (hidden), `version`, `compress`, `decompress`, `make-encoder`,
  `make-decoder`, `write` and `read`.
- **Its Brotli is not pinned.** The `.nest` builds against `@brotli`, Google's
  `master` when the release was built, not a tagged release. The reference is
  built against `rebol3-source/src/core/brotli/`, 1.2.0, so `version` answers
  `1.2.0` there; whatever the release binary was built with is recorded as
  unknowable, not guessed.
- **Its handles have fields**, which JEBOL's handles do not have yet:
  `brotli-encoder` sets `mode` and `size-hint` and gets `finished`;
  `brotli-decoder` gets `finished`. A field R3 does not know is
  `PD_Handle`'s answer: `/type` for a context handle, otherwise a bad select. Both
  mold as `#(handle! brotli-encoder 0#...)`: `Mold_Handle` appends the
  extension's own `Common_mold`, an address, after the kind.
- **Importing it replaces `br`.** `RX_Init` calls
  `RL_REGISTER_COMPRESS_METHOD("br", ...)`, so after `import 'brotli`,
  `compress`/`decompress` with `'br` run the extension's functions, not the
  core's. Its compress sets `SIZE_HINT` to the input length, which the core never
  does, but the C sets the same hint itself when one call feeds the input and
  finishes: the probe confirms the bytes agree before anything is built on it.
  Its decompress answers `#{}` for an empty input and for `/size 0` without
  decoding, and stops at the limit.
- **`Accept-Encoding` is already right.** `prot-http.reb` appends `,br` when the
  catalog has `br`, which the core's does, and the module's import code
  appends only when no `br` follows a comma. So importing changes nothing there;
  a test pins that.
- **The C's streaming quirks**, each to be confirmed by the probe: `write`
  answers the handle unless `/flush` or `/finish`, and then a copy of the buffer;
  `write` of `none` finishes; `read` on an encoder flushes first; `write` or
  `read` after the encoder finished is `"Brotli encoder is finished!"`; a
  handle of another kind is `"Invalid Brotli encoder or decoder handle!"`; a level
  outside 0 to 11 is clamped through an unsigned cast, so a negative one is 11.
  `write` checks `hob->handle` and then uses `hob->data`, which is the same
  pointer: the two are a union in `REBHOB`.
- **The C it is built from has CVE-2026-111497.** Brotli 1.0.3 to 1.2.0 report a
  block switch in a category with one block type as "needs more input" rather
  than a format error, so the streaming decoder overflows its 8-byte
  `s->buffer` (`DecodeBlockTypeAndLength` in `c/dec/decode.c`; fixed by Google's
  commit `0ed4268a4401abcb4472096340477cf4af7d1a2b`, "Detect and report invalid
  stream earlier", released in 1.0.10, 1.1.1 and 1.2.1). `rebol3-source/` carries
  1.2.0 and Rebol-Brotli 0.1.0 was built from `master` in December 2025, before
  the fix: a real 3.22.5 with this extension is affected. JEBOL's Java decoder is
  not (one block type never reads a switch, and it has no streaming state), and
  the resumable decoder of step 4 must keep it that way: **port the fixed
  behaviour, not the vulnerable one.** That stream is a format error,
  `BROTLI_DECODER_ERROR_FORMAT_BLOCK_SWITCH`, never a request for more input.

**The order.** Each step's failing tests come before its code, and each step is
gated and committed on its own.

0. **The reference, checked in.** `src/testFixtures/resources/brotli/module/
   generate.sh` fetches Rebol-Brotli at `0.1.0`, compiles its C against
   `rebol3-source/src/core/brotli/` into a `.rebx`, and runs `module-*.r3`
   scripts in `./r3-head`, as `extensions/sqlite/.../reference/generate.sh`
   does. Cases that crash run one process each. Beside it, a small C program
   linked against the same Brotli drives `BrotliEncoderCompressStream` and
   `BrotliDecoderDecompressStream` through a list of operations, writing each
   output chunk's length and digest as a table, so every boundary below has a
   C answer. Brotli's `testdata.txz` is fetched by the generator, not checked
   in, except a slice where only it pins a behaviour. The decoder's table is
   built with commit `0ed4268` applied to the 1.2.0 sources, so it records the
   fixed answer; the module transcripts keep the unpatched build, and the one
   case where the two differ is listed as a CVE the port does not reproduce.
1. **Handle fields in the core,** in `src/functionalTest`, before anything uses
   them. `HandleType` gains fields a script reads and sets by path, and an
   optional mold, as R3's `REBHSP` has `get_path`, `set_path` and `mold`. Reading
   and setting answer as `PD_Handle` does: an unknown field, a field that only
   reads being set, and a wrong type each give R3's error. A new group in
   `ExtensionsFromTheSourceTest`, the spec in `spec/embed.allium`, and a section
   in `writing-an-extension.md`.
2. **The module over the one-shot port.** `BrotliExtension`, bundled, with the
   header above: `version`, `compress`, `decompress`, the `br` registration and
   the import script. `TheBrotliModuleFromTheSourceTest` checks the transcripts.
   If the probe finds the extension's compress writes other bytes than the core's,
   the size hint goes into the Java encoder here, before any streaming.
3. **The streaming encoder.** `BrotliGenericEncoder.run` becomes the C's
   `BrotliEncoderCompressStream` state machine: PROCESS, FLUSH and FINISH, the
   ring buffer kept between calls, the size hint updated as the C updates it, and
   `mode`. Qualities 0 and 1 stream through the C's
   `BrotliEncoderCompressStreamFast`, which buffers differently from the one-shot
   fragment encoders, so that path is ported too. Byte-exact against the table.
4. **The streaming decoder.** The Java decoder becomes resumable, the C's
   `BrotliDecoderDecompressStream`: it takes input in pieces and gives out what
   the C gives out after each piece, not only at the end. Byte-exact against the
   table, with every input split at every position for small streams.
5. **The handles and commands.** `make-encoder`, `make-decoder`, `write`, `read`,
   the fields and the errors, over steps 3 and 4. The transcripts, and separate
   `*FromTheSourceTest`s for every boundary without reading a `.r3` file.
6. **The docs.** `docs/brotli-port.md` gains the streaming state, where JEBOL
   differs, and the CVE; decision 58 gains the brotli module;
   `docs/standing.md`; and these goals.

**Where to start, for a session that did not do goal 1a.** The precedents to
copy, not reinvent:

- **A bundled streaming extension:** `Bzip2Extension` and `Bzip2Stream`, from
  the same author's Rebol-Bzip2, with the same `make-encoder`, `write` and `read`
  shape; `TheBzip2ModuleFromTheSourceTest` for its transcripts.
- **A checked-in reference generator:** `extensions/sqlite/src/functionalTest/
  resources/reference/generate.sh` and its README, which compile an extension's
  C, run scripts in `./r3-head` each in a fresh folder, and run crashing cases in
  a process each. `src/testFixtures/resources/bzip2/generate.sh` for a C table
  harness.
- **Extension machinery:** `ExtensionInstallation`, `Extension.withHeader`,
  `HandleType`, `Command.handleKind`, and `ExtensionsFromTheSourceTest` for the
  tests a core change adds a group to. A command's refinement arguments arrive
  only for the refinements used, in order, so read them as bzip2 does.
- **Handle paths today:** `Evaluator`'s `HandleValue` branch answers `/type` and
  nothing else; R3's `PD_Handle` in `rebol3-source/src/core/t-handle.c` and
  `Mold_Handle` in `s-mold.c` are what step 1 ports.
- **The C:** `rebol3-source/src/core/brotli/` (1.2.0) and `u-compress.c` for the
  core's `'br`; Rebol-Brotli is fetched by the generator at tag `0.1.0`.
- **Things that bit goal 1a:** `./r3-head` runs a script from the script's own
  folder, so copy each script into its work folder; `import` of an R3 module
  address on `./r3-head` downloads a release binary, which the project forbids,
  so always import the compiled `.rebx` by path; an `AnyBlockValue.index()` is
  1-based where the C's is 0-based.

**The boundaries.** Each is covered by the reference and by a JEBOL test:

- **Levels:** every level 0 to 11, a level below 0, and one above 11, for
  `compress`, `make-encoder` and `compress 'br` after import.
- **Input sizes:** 0, 1, 2 and 3 bytes; either side of each quality's input
  block (`1 << lgblock`) and of the window (`1 << 22`); either side of
  `BrotliEncoderCompressStreamFast`'s buffers for qualities 0 and 1.
- **Chunking:** one byte at a time; chunks either side of the input block;
  a chunk exactly filling the window.
- **Operations:** `/flush` on an empty stream, twice in a row, and after a
  chunk; `read` with nothing written, and after a write; `/finish` with `none`
  and with data; `write` and `read` after `/finish`; `finished` before and after.
- **Fields:** `mode` 0, 1 and 2, and out of range; `size-hint` under, equal to and
  over the real length, and set after the first write; an unknown field; setting
  `finished`; a field of the wrong type.
- **Decoding:** a stream split at every byte for small streams; an empty
  input; a truncated stream; a damaged stream; writing after the stream ended;
  `/size` 0, under and over the real size; `/part`.
- **CVE-2026-111497:** a block switch for literals, commands and distances each
  in a category with one block type, fed whole and one byte at a time, through
  `decompress`, `decompress 'br` and the streaming decoder: a format error each
  time, and no more input asked for.
- **Strings:** a string as input, with wide characters, and not at its head, as
  bzip2 reads them.
- **Handles:** one kind where the other belongs; another extension's handle;
  `mold` of each.
- **The module:** `spec-of`, `words-of` with `init-words` hidden, `version`,
  `Accept-Encoding` unchanged, and `compress 'br` before and after import.

**Done when** every command, field and error answers as the reference does,
with each exception recorded in `docs/brotli-port.md`; the stream's bytes match
the C's table at every boundary on the list; every boundary has a standalone
JEBOL test; and Brotli's own corpus round-trips through the streaming API in
chunks, at every level, compressed bytes equal to the C's.


#### 1c. The console honours echo, and `call/console` runs without one

- **Echo.** `modify system/ports/output 'echo false` is accepted and changes
  nothing: what is typed is still shown, so a script asking for a password
  shows it here and not in R3. JEBOL also writes the setting into a field
  R3's port has not got (`system/ports/output/echo` answers false here and
  is `invalid-path` in R3). The console adapter holds the three modes --
  echo, line and error -- and honours them; the field goes. First say, in
  the spec, what each mode means for a host that is not a terminal.
- **`call/console` on a host with no console** runs the program as plain
  inheritance would. One rule in `embed.allium`.

**Done when** echo off hides what is typed at the REPL (an end-to-end test
through the command line), the port has no extra field, and `call/console`
runs under a host with no console.

#### 1d. TLS: an out-of-order chain, and a Unicode host name

R3 trusts every server, so a script reading a server like these works there
and fails here.

- **A chain sent out of order is put in order** before it is checked, as
  browsers do. A chain missing an intermediate is still refused: fetching
  it would be a network call per handshake.
- **A Unicode host name is converted to its ASCII form** (`java.net.IDN`)
  before it is matched against the certificate, so `https://bücher.de` can
  be read.

Both need servers the end-to-end suite spins up itself, like the expired and
wrongly named ones it already has.

**Done when** both are rules, and the command line reads a local server that
sends its chain backwards and one named in Unicode.

#### 1e. An error a native raises carries its `near` and `where`

For `try [compress "a" 'brotli]` R3 gives `near: [compress "a" quote brotli]`
and `where: [compress try ...]`; JEBOL gives none for both. Errors from
evaluated code, paths, PARSE and CAUSE-ERROR already agree with R3. A script
that stops on such an error prints a different `** Near:` and `** Where:`.

1. Sweep: raise an error from every native that can raise one, in both
   interpreters, and diff `near` and `where`. COMPRESS is the one found so
   far; how many others share it is not known.
2. Fix the path they share, not each native.

**Done when** the sweep shows no difference, and is a test.

#### 1f. An operator with nothing on its left raises what R3 raises

Measured on 2026-10-23:

| Source         | R3                                   | JEBOL                                                                   |
|----------------|--------------------------------------|-------------------------------------------------------------------------|
| `do [+ 1]`     | `no-op-arg`, arg1 `+`                | `no-op-arg`, arg1 the sentence "the operator + has nothing on its left" |
| `do [1 + + 3]` | `cannot-use`, arg1 `add:`, arg2 none | `missing-arg`, arg1 the same sentence                                   |

`AnOperatorWithNothingOnItsLeftFails` in `spec/eval.allium` names
`missing-arg` for the second case, which R3 does not raise there: the rule is
wrong, so it is fixed first.

1. Read the C for what the second case pushes and why it surfaces as
   `cannot-use` on `add:`; probe a few more positions (after a set-word,
   inside a paren, after a function's last argument).
2. Correct the rule, then the tests, then the code.

**Done when** every position probed answers R3's id and arguments.

#### 1g. An error's arguments are values, not sentences

Twice now JEBOL has put an English sentence where R3 puts a value: arg1
of the operator error (1f), and arg1 of `no-value`, which for `get first
[zork]` is a `word!` spelt `zork has no value` where R3's is `zork`. A script
that reads `e/arg1` gets prose.

1. Sweep: raise every error JEBOL can raise, read arg1, arg2 and arg3, and
   list every one that is a sentence. `scripts/error-parity.py` is the place
   to start.
2. For each, find what R3 puts there. Where R3 cannot raise it, the
   argument is the value the error is about, and the wording goes in the
   catalogue's message.

**Done when** no error argument is a sentence, proven by the sweep as a
test.

#### 1h. Clear the spec's open questions

Every open question in the spec has an answer. The work that follows from the
answers is 1c, 1d, 1j, 1k and 1l; this sub-goal is the spec's side of it.
Every edit runs `allium check` clean.

1. **Already answered: the question becomes a rule, or goes.** Each of
   these now matches R3 or is built. Where the behaviour is not already a
   rule, write it as one, and point it at the test that covers it (write the
   test if none does):
   - `parse.allium`: THEN commits the alternative (`parse "ab" [["a" then
     "x" | "a" "b"]]` is false in both); a mark into another series is read
     as a rule, as R3 reads it.
   - `eval.allium`: a get-word refinement is checked whatever it holds
     (`append/:bogus` is `no-refine` with BOGUS false, in both).
   - `values.allium`: `#[none]` is `invalid-arg` in both; `struct!` matches
     R3 as a binary layout tool (typed fields, arrays, nesting, `rebval!`
     fields, `change s #{...}` to lay bytes over it, `to binary!`) -- that
     is all R3 does with one, so the question goes.
   - `load.allium`: `a%b`, `a#b`, `a$b` and `a\b` are `invalid` in both; an
     export never defined says nothing, in both; HELP, SOURCE, WHAT, ABOUT
     and DUMP-OBJ exist; the unbalanced-delimiter spellings match R3.
     Delete the paren-led path question outright: R3 reads
     `(make object! [q: 7])/q` as a paren and a refinement too.
   - `natives.allium`: the two error ids for giving a native a body match
     R3 on all three spellings; `read %*.r3` lists matches; REMOVE on a
     complemented bitset matches; PROTECT/HIDE hides; REPEAT takes a
     series and a decimal; the six natives waiting on datatypes exist; a
     handle holds a value and stays opaque;
     `CompressedBytesAreTheOnesARealRebolWrites` now holds for all nine
     methods (zlib, gzip and deflate write R3's bytes at levels 0, 1, 6 and
     9; LZ4 and LZAV were ported byte for byte), so the libdeflate question goes and the invariant loses its
     exception; a wait on a connection with no timeout answers none until
     connections are read on their own thread (a rule, not a question);
     `system/options/boot` serves children only, which is enough (owner's
     answer); a byte-exact CHECK over a vector is dropped.
   - `screen.allium`: VIEW waits until a deadline ends it, built; a draw
     block on a gob paints; a missing screen's metrics are zero, as R3's
     host answers; the window's size is settled by the sliders example
     matching its reference screenshot.
   - `draw.allium`: SPLINE is built; a block's defaults read the gob's
     size, as the C does.
   - `dialect.allium`: no dialect has a slot after a repeating slot (every
     `*` in `dial-draw.reb` is last); swapped slots of one type get no
     warning (owner's answer); a near-miss count stays debugging output
     (owner's answer).
   - `embed.allium`: the evaluator checks its bounds every `checkEvery`
     steps, 1,000 by default; a file feeding a program's input stays a path
     for the host, since no script can tell; a host sees nothing of a run
     until it ends (owner's answer).
   - `web.allium`: an empty folder chosen in a browser answers the empty
     folder; no browser can name it.
2. **Answered by the owner, with work to do: the question becomes a rule
   when its sub-goal starts**, not before, so the rule and its tests arrive
   together: console echo and `call/console` (1c), TLS chains and Unicode
   hosts (1d), home and values between interpreters (1j), the Rebol bugs
   (1k), `effect` in DRAW and GUI-METRIC/SET (1l). The `call/console`
   question is in `embed.allium` and `natives.allium` both; the rule goes
   in `embed.allium` and the copy in `natives.allium` is deleted.
3. **Not a question for JEBOL:** move the `open/new` question -- a real
   3.22.5 answers two different ways on two identical runs -- to
   `docs/rebol-findings.md`. `change/part` on an image with a count copies
   R3 (owner's answer): the question becomes a finding beside it.
4. **Questions about the spec itself**, decided while editing:
   - Write down how far PARSE backs up when an alternative fails, and
     whether a repeat is retried at a lower count -- read off the C and
     `./r3-head`.
   - `TranscodeRequest` holds the text twice (`source_text` and `source`);
     keep `source` and derive the text.
   - State the mold property once (whatever would not read back is written
     as a construct) rather than listing members, with the round-trip
     invariant enforcing it.
   - `mold_indent_width`, `longest_quoted_string` and
     `newlines_forcing_braces` become named constants, not configuration.
5. **Moved:** whether a running script can be paused and resumed goes to
   goal 6, where stepping needs it.
6. **Still open, rewritten in plain English:**
   - Should a host be able to cap a script's memory? (Later.)
   - Should JEBOL check whether a certificate was revoked, and if it cannot
     find out, allow or refuse? (Later.)
   - Does a partly transparent pixel decoded in the browser come out
     visibly wrong? Look at one first, then decide whether a second decoder
     is worth it.
   - A swipe moves an app back and forward on every platform: decided. How
     it arrives differs on each, and the desktop is the one undecided:
     - **Web, desktop and mobile browsers:** the browser's own swipe moves
       its history, and JEBOL's history is the browser's (decision 50,
       `spec/nav.allium`), so it works today. Prove it with an end-to-end
       test, and check a mobile browser's edge swipe takes the same path.
     - **Desktop window:** macOS sends a swipe to
       `com.apple.eawt.event.SwipeListener`, which JDK 25 still ships but
       does not export (it needs `Add-Exports` in the jar's manifest, and
       works on macOS only); the public alternative is reading a sideways
       scroll as back and forward, which works on every system and cannot
       tell a swipe from scrolling sideways. Try both on a real trackpad,
       then choose.
     - **Android and iOS (goals 2 and 3):** Android's system back gesture
       arrives through the app's back handler; iOS has no system back, so
       an edge swipe has to be recognised by the app itself. Each goes in
       its platform's goal.

**Done when** the spec holds only the four questions in step 6, each in
plain English, and every answered one is a rule or gone.

#### 1i. The table of word spellings belongs to an interpreter

`Context.ONE_NAME_PER_SPELLING` keeps one lower-cased copy of every spelling
ever read, in a static table every interpreter in the process shares and
nothing empties. A long-running process whose scripts make words in a loop
(`to word! join "k" i`) grows it without limit. It is per-interpreter state
held in a static, and it goes.

**Done when** the table lives with the interpreter and is gone when the
interpreter is, proven by a test that makes words in one interpreter and
finds none of them in the next.

#### 1j. A confined script's home, and values between interpreters

The command line confines a script to its folder as R3 does, and both report
the machine's home, so neither changes what a script run on both answers.

- **Home inside the confinement.** `system/options/home` reports the
  machine's home even when the script may only see one folder, so REBOL's
  own user storage cannot write there. Ask the filesystem port where home
  is, so every host gives an answer inside what it lets the script see.
- **Values cross between interpreters by deep copy.** Owner's decision. Say
  in `values.allium` what crosses (every datatype, or which not: ports,
  handles, functions bound to the sender's contexts), and that the receiver
  gets a copy it owns. A task's body is the first place it is used; goal 6's
  copied interpreter is the second.

**Done when** both are rules with tests, and a confined interpreter can make
REBOL's user file at home.

#### 1k. Fix five Rebol bugs, and copy one

Owner's decisions, 2026-10-23. Each fix is a place JEBOL answers something a
real 3.22.5 does not, so each gets an entry in `docs/standing.md` saying what
and why.

1. **`find` in a binary finds 255.** `find #{00FF} 255` is none in R3 and
   `#{FF}` here; JEBOL is already right. Write it in `docs/standing.md`, and
   turn the open question into a rule.
2. **`make image!` takes a block of tuples.** `make image! [2x1 [1.1.1
   2.2.2]]` is a malconstruct in R3, because the reader that consumes the
   block never steps past it. Read the C and decide what the block should
   mean (one tuple per pixel, row by row) before writing the rule.
3. **`image-diff/part` clips an area that runs past the edge.** The C
   subtracts the size twice; `image-diff/part i1 i2 0x0 1x3` on two two by
   two images answers 0% where the pixels differ. Rebol's own
   `image-test.r3` asserts that 0% (line 402), so the suite assertion must
   be recorded as a deliberate difference: decide how the suite harness
   records one -- it has `fails-on-rebol-too.txt` for the other direction
   and nothing for this -- before the fix lands.
4. **A cipher that would swallow a message raises.** The open question named
   one cipher; a probe on 2026-10-23 that writes 32 bytes without a header,
   then UPDATE and READ, answers none for all nine GCM ciphers and only 16
   bytes for chacha20-poly1305, in both. Find which of those are the bug
   (a GCM stream may simply need its tag read differently) and raise on
   those.
5. **Opening a port that is already open raises `already-open`**, as the
   crypt port does, instead of connecting again and leaking the first
   connection. MIDI is the one that leaks; the sweep of every built-in
   scheme on 2026-10-25 found three more places where a port's state should
   gate a verb and does not, each measured against `./r3-head`:
   - `open p` on an open file port is `already-open %held.txt` in R3 and
     answers the port here; `create p` on an open directory port the same.
   - `delete p` on an *open* file port is `no-delete %held.txt` in R3, and
     here it deletes the file.
   - `rename p %zz` refuses in both, as `no-rename`, but arg1 is the file in
     R3 and the whole port object here.
6. **Copied, not fixed:** `change/part` on an image with a count changes
   nothing. The question becomes a finding in `docs/rebol-findings.md`.

**Done when** the five behave as above, each with a rule, tests, and an entry
in `docs/standing.md`, and the suite records the `image-diff` difference.

#### 1l. DRAW refuses `effect`, and GUI-METRIC/SET moves the window

- **`effect`** is the one DRAW command (of the 34 in `dial-draw.reb`) JEBOL
  does not paint, and it is skipped without a word. It raises until it is
  built. Read the C for the id.
- **`gui-metric/set`** with `'restore`, `'minimize`, `'maximize` and
  `'activate` controls the window, on the desktop. Say what each does in a
  browser, where a page cannot minimise itself. This goes past the C, where
  neither host handles those words and each raises `command-fail`, which is
  what JEBOL answers until then.

**Done when** both are rules, with an end-to-end test that minimises,
restores and maximises a real window.

### 2. Android, with the interpreter running on Android's own Java

1. **Check JEBOL's Java against Android's.** JEBOL is text for Java 25.
   Find the language features and library calls Android's compiler and
   runtime do not have, and replace them or choose the oldest Android version
   that does.
2. **The app.** An Android project of its own, so the jar keeps no
   dependencies. One screen draws the domain's surface calls on Android's own
   canvas and turns touches and the on-screen keyboard into JEBOL's events.
   Pausing and resuming the app are handled. The system back gesture (and
   the back button) moves the app's history back, through Android's back
   handler, as a swipe does in a browser (1h, `spec/nav.allium`).
3. **Android's adapters.** Files in the app's own storage, real sockets, a
   console view. CALL is refused. And a task's body must run on the host's
   ports too: `ThreadTasks`
   (`src/main/java/org/jebol/adapter/host/ThreadTasks.java:32`) builds its
   child interpreter with `Interpreter.createWithOutput(...)`, which is
   `Interpreter.standardPorts()`, so a task body on Android would answer
   `system/platform` with `Linux` and reach nothing the host supplied, where
   `TheFateOfATasksBodyIsTheHostsToDecide` already says the child inherits
   the parent's output and pause. `ThreadTasks` wants the parent's
   `HostPorts`, which also settles what a task body may reach, since today it
   has no files at all and a file port carries the directory it is standing
   in, so the parent's own cannot simply be shared.
4. **Touch in the 2010 GUI.** A tap is a click; a drag scrolls; focusing a
   field brings up the keyboard; layouts scale with the screen's size and
   density. This is done once, in the GUI's REBOL --
   `src/main/resources/org/jebol/modules/r3-gui.reb`, with each edit listed
   in ORDER.txt -- and iOS and the web on a phone use it too.
5. **Android's own features**, one extension each (using-jebol.md, section
   4): the camera, location, notifications. Where a feature can come to the
   web or the desktop too -- the camera, location -- it comes there as well.
6. **A gate on an emulator.** The examples are driven on an Android emulator
   the way the browser gate drives Chrome: a gate of its own, never a skipped
   test.

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
   on Android, using goal 2 step 4's work in the 2010 GUI. iOS has no system
   back gesture, so the app recognises a swipe from the left edge as back
   and from the right edge as forward, the way a browser on the phone
   does (1h, `spec/nav.allium`).
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
rather than connecting twice (1k).

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

**First, make `trace` agree with R3.** For `x: 1 + 2` R3 prints the operator
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
