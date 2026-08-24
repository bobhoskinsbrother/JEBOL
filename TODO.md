# TODO

Only work that is left. History lives in git and in `docs/`. How to port a
function, what the authorities are and what the regression floors say live in
`docs/porting-guide.md`, because they are guidance rather than work.

Every number below was checked on 2026-08-24 by running it.

## What the measures say now

Five of them, and all five are clean or nearly so. That is worth stating
plainly because the list below is short for a reason, not because nobody
looked.

| Measure | Reads |
| --- | --- |
| `scripts/c-parity.py` | 279 of 279 C functions match R3's surface |
| `PortingBacklogTest` | 0 of R3's 404 functions missing |
| `Interpreter.borrowedLoadFailures()` | empty -- every borrowed file loads whole |
| `system/catalog/datatypes` | 59 against R3's 58, the extra being `java-object!`, though `task!` is a name without an arm |
| `RebolSuiteTest` | 4337 assertions, none failing, one recorded as a gap Rebol shares |

`./gradlew check` is 10462 tests, none failing, none skipped.

---

# Goal 1. The type-major refactor

**The original complaint, and much the largest piece left.** One `t-*.c` per
increment, bitset as the pilot.

The graphics work left a hint about the shape: `PaintInstruction` is sealed, so
adding a kind broke every renderer's switch at compile time. `VectorValue`
proved it again -- adding it to `SeriesValue permits` made the compiler
enumerate every arm that needed work. That is what the action seam wants.

# Goal 2. Graphics

**DRAW renders 22 of R3's 36 commands.** The fourteen it does not:

```
arrow  clip  gamma  grad-pen  image  image-filter  image-options
image-pattern  invert-matrix  line-pattern  spline  text  transform  triangle
```

`image` and `text` are the two that make pages look wrong rather than plain.

Also here: the stroked-curve comparison problem, the 522 lines of old markup
path, VID, Android, and the events-name-the-wrong-window one.

# Goal 3. The boot

**343ms for the first interpreter, 72ms once the JVM has settled.** A
7900-test run pays the 72ms per class, and that is the floor rather than the
machine.

Pool first, then library caching. The series byte accounting behind STATS is
already in that allocation path, and it costs about 2ms of the 72.

# Goal 4. Loose ends

**`task!` is a datatype word and not yet a datatype.** `task!` answers
`#(datatype!)` on both, but `make task! [1 + 1]` gives `#(task!)` on a real
Rebol and `cannot-use` here. It is the last one that is not really there.

**Ten scheme names R3 registers and JEBOL does not.**

```
callback  checksum  clipboard  crypt  dir  file  midi  serial  system  udp
```

`file` and `dir` are the two that matter. JEBOL reaches files through the
host-grant system instead, and the command-line REPL grants only WINDOWS, so
`read %TODO.md` answers `no-service` there. Whether that is the design or a
gap in the CLI is worth settling before the schemes are written.

**TLS loads but does not connect.**

**Four fields of `access-os` answer `not-here`** -- `uid`, `euid`, `gid`,
`egid` -- where a real Rebol answers a number. The JVM has no portable way to
ask. `pid` works.

**55 open questions across nine spec files**, the heaviest being
`natives.allium` with 19.

# Goal 5. The 32 prelude forks

`prelude.reb` defines 36 words and Rebol defines 32 of them in `src/mezz` too:

```
all-of  any-of  body-of  cause-error  clean-path  clos  closure  collect
default  dirize  does  empty?  enum  funco  has  join  keys-of  map  max  min
rejoin  script?  spec-of  split-path  suffix?  title-of  to-word  types-of
undirize  values-of  words-of  wrap
```

Audit by identity rather than by datatype: for each one, is JEBOL's version
the same function, and if not, why was it forked?

# Goal 6. The one suite assertion that cannot come off

**The gate is green: 10462 tests, none failing, none skipped.** One assertion
sits in `src/test/resources/rebol-suite/known-gaps.txt`, which is not a skip
list -- it is run on every build and `RebolSuiteTest` fails if a listed
assertion starts passing.

```
 1  SWAP  a real Rebol fails it too, which is why it can never come off. It
          asks whether "🙂" equals the LENGTH? of a string whose index a SWAP
          has just invalidated -- a string against a number, false whatever
          the number is. Rebol's own comment on the line reads "Known
          issue!!!", and running its suite with a binary built from this
          checkout prints "FAIL: swap invalidating index (known issue) (1)".
```

# Goal 7. LLM-friendly MCP tools

**The reader will only ever be an LLM, and that decides the design.** A model
does not misunderstand, it infers confidently from training data that is mostly
REBOL 2. It has no faculty for caution, so a warning it is asked to heed is a
warning that gets ignored under task pressure. **Every warning has to become a
mechanism or it is not there.** And it reads exactly one channel without being
asked to: the text of the error it just caused.

## 7a. Before any of the tooling

Nothing below pays until a dialect can be written, run, and diagnosed in one
command. All of this is hours.

- **`jebol file.reb`.** There is no argument that takes a script file, and a
  path on the command line is ignored without a message -- the console starts
  instead, which is worse than a refusal. The loop today is a scratch file
  piped into a REPL whose prompts come back interleaved with the output.
- **A dialect that fails loudly.** A rule that does not match answers false, and
  the natural shape of the calling code drops it. `parse [add 200 grams of flour
  wibble]` against a four-command grammar returns `[200 grams flour]` with no
  error. A person notices the answer looks short. A model accepts it, and there
  is no second line of defence.
- **One worked dialect, thirty lines, with a test.** DELECT matches by type and
  ignores order; PARSE matches by position. Nothing states that choice anywhere,
  so the first move is a guess, and the guess is PARSE because that is what the
  training data talks about.
- **Ship `using-jebol.md` inside the jar, printed by `--manual`.** Written to
  standard output, exit 0. The distribution now carries the file, `LICENSE` and
  `NOTICE`; the flag does not exist yet.

## 7b. The dialect schema, generated

A DELECT command is not a production rule. It is a record with typed optional
slots that arrive in any order, so what falls out of `system/dialects` is a
schema rather than a grammar -- and a schema is the shape a model has seen ten
thousand times in tool definitions.

Generate it by walking a booted interpreter, never by parsing `dial-*.reb` out
of the mezz. That is the trap `c-surface.py` fell into: it read only the boot
files, could not see the 54 natives declared in C comments, and reported
MISSING: 0 while `binary` was absent.

Make it a test rather than a script, in the shape of `SurfaceReportTest`, so a
change to the table fails the build instead of shipping a binary that describes
a dialect it no longer has. The types come from R3's table and stay faithful.
The descriptions -- which R3's objects do not carry, and without which a model
produces acceptable nonsense -- live in a file beside it, and the same test
fails when a command has none.

Its first customer is the error message, not the server: with the schema, a
failure can name the acceptable set at the position it stopped.

Two things to read before writing it: what a command's value looks like inside
a `system/dialects` object, and whether the table marks a slot that is itself a
sub-dialect. `ShapeSubDialect` says DRAW nests. `Delect.read` and
`u-dialect.c` say whether the table knows.

## 7c. Three tools

| Tool | Answers |
| --- | --- |
| `run` | evaluate under a declared fixture -- conclusion, value or error, services asked for, port calls made |
| `match` | this block against this grammar: matched, or where it stopped and what was acceptable there |
| `word` | what this word takes, what it does, whether it is reliable in this build |

`run` replaces a validator returning a boolean, which is the trap: a model reads
"valid" as "correct" and stops. Validation is not separable from execution here
-- a word means nothing until it is looked up, PARSE rules live in words, and
blocks are built by `compose` at runtime -- but it does not need to be. It is
execution with the effects removed, and the ports are already that seam. The
fixture defaults to nothing existing and nothing granted, so the laziest
possible call still answers "it asked for FILES and you granted none".

A run covers a path, not a program, because a stub has to answer and the answer
picks the branch. Say so in the result rather than implying otherwise. Forking
both branches is not viable: `if` is a function here, so are `either`, `case`
and any user word that wraps them, and finding the branch points is the problem
being solved.

**The descriptions carry the doctrine, because they are the one text a model
cannot skip.** `run`'s carries the capability model and the six conclusions.
`match`'s carries DELECT against PARSE in one sentence. `word`'s carries the
warning that a clean run is not a correctness proof.

**The results carry more than the descriptions.** "Asked for FILES, not
granted" and "used a word with failing assertions in this build" arrive unbidden
at the moment they matter. The description sets the frame once; the results
correct the model every call.

No embed API in any of it. A caller reaching an MCP server is writing REBOL, not
Java, and sections 3 and 6 of `using-jebol.md` would be context spent on the
wrong reader.

## What drops, given the audience

`help` inside the console, the banner, and most of the manual's prose. `help
parse` is a thing a person types mid-session. The banner tells someone stuck in
a REPL how to leave. And anything a model would infer correctly is wasted
tokens -- what earns space is only what contradicts the prior: `if 0` is true,
`none` and a Java null stay apart, there are six conclusions rather than two.

One thing to price before starting: error text becomes an interface. Reword it
later and whatever was built on the old wording breaks.

---

## Where I'd go next

**Goal 1.** It is the original complaint, it is the largest thing left, and
everything in Goals 2 to 5 is a bounded list that will still be there
afterwards. The measures are all clean now, which is exactly the moment to
move a load-bearing wall: a refactor is safe in proportion to what the tests
can tell you, and they can currently tell you a great deal.

## What came off the list

Kept short, because the detail is in git.

- **The bugs.** Every difference the measures could name is fixed: the map key
  case, an error's fields, LAYOUT, `clamp`, `distance`, `factorial`, seven
  over-wide argument declarations, `dir?`, `access-os`, `request-color/rgb16`.
- **The measures themselves, which were wrong six times** and each time hid
  something. `c-surface.py` read only the boot files and so could not see the
  54 natives the C declares in its own comments; it counted two objects as
  functions; `PortingBacklogTest` asked one context of two; `limit-usage` was
  counted as work when Rebol deletes it on purpose; a dropped `return:` line
  invented three differences; and an `any-type!` argument read as 41.
- **`vector!`**, its ten encodings, construction syntax, molding, series
  actions, arithmetic and statistics.
- **The 178 assertions `vector!` uncovered.** `series-test.r3` would not load
  at all -- its first vector literal is on line 2000 of 3444 -- so a suite
  reporting 3721 of 3721 green was silently missing a whole file. Sixteen root
  causes took 176 of the 178, almost every one a primitive Rebol's own library
  stands on: CASE not evaluating its branch, PARSE not resolving a word, ICONV
  knowing no codepage numbers, `to-string` ignoring a byte order mark, RANDOM
  drawing unevenly and seeding from the wrong thing, INTO not resolving the
  rule after it, FOREACH taking its items once instead of asking each round,
  and a word in a function body belonging to the call rather than the function.
- **Building the reference interpreter rather than downloading it.**
  `scripts/build-r3.sh` makes `./r3-head` from the vendored source in about a
  minute. The release that was being used was ten weeks behind its own source
  and four wrong readings were traced to it.
