# TODO

Only work that is left. History lives in git and in `docs/`. How to port a
function, what the authorities are and what the regression floors say live in
`docs/porting-guide.md`, because they are guidance rather than work.

Every number below was checked on 2026-08-31 by running it.

## What the measures say now

The first five are clean. Every assertion Rebol's 67 vendored files write is
now run -- Goal 1, and it is done. The sixth is new and is not clean.

| Measure | Reads |
| --- | --- |
| `scripts/c-parity.py` | 279 of 279 C functions match R3's surface |
| `PortingBacklogTest` | 0 of R3's 404 functions missing |
| `Interpreter.borrowedLoadFailures()` | empty -- every borrowed file loads whole |
| `system/catalog/datatypes` | 59 against R3's 58, the extra being `java-object!`, though `task!` is a name without an arm |
| `RebolSuiteTest` | all 10,100 assertions Rebol's 67 vendored files write are run. 963 failing and every one named in `known-gaps.txt` |
| `scripts/error-parity.py` | **69 of Rebol's 142 error ids can be raised. 73 cannot** |

`./gradlew check` is 16,093 tests, 0 failed, 0 skipped. An unread suite file
fails the build outright -- no list, no exception.

---

# Goal 1. Every suite file must be read to the end -- DONE

**Every one of the 10,100 assertions Rebol's sixty-seven files write is now
run. Thirteen files did not read to the end when this started and 3,044
assertions were never reached.**

```
reader reaches      7,181  ->  10,100  of 10,100 written
suite runs          7,169  ->  10,102  assertions
files stopping         13  ->       0
files short            37  ->       0
```

Both gates are absolute now. `everyFileIsReadToTheEnd` fails on a file the
reader cannot take whole; `everyAssertionWrittenIsRun` fails on an assertion
the harness does not run. Neither has a list to add anything to.

## The reader: thirteen stops

Ten were one line. `Transcoder.builtFrom` was a hardcoded switch whose
`default` answered `malconstruct`, where Rebol's `Construct_Value` skips the
datatype word and calls `Make_Dispatch[type]` -- a type has construction
syntax exactly when it has a maker, and there is no list to keep. What must
*not* be tried comes off the Make column of `types.reb`, whose header says
what that column is for.

The other three: a percent may carry an exponent; a file may open with a
percent escape (`%%40b` is the file `@b`, while `%%/x` is not the modulo
operator); and a path may hold a tag or a character.
`firstOffendingCharacter` judged the inside of a captured paren by the rules
for a word, truncated the lexeme there and re-read, so `m/(<A>)` became the
path `m/(`, a tag and a stray bracket. `b/#"a"` went the same way and is the
one worth remembering: it read as **two values instead of one without
changing how many assertions the file appeared to have**.

Three more fell out: `make date!` and `make time!` from a block, `float!` and
`double!` as struct field types, and a struct field carrying a dimension.

## The harness: six defects, every one a measure that lied

- **Bisecting for the longest readable prefix**, on a predicate that is not
  monotone. It cost error-test.r3 thirteen assertions the reader already had
  and named the wrong line in three of twelve stops.
- **Counting lines that begin with `--assert`**, missing every one indented
  inside a block, so eleven files could not fail the gate at all.
- **Counting every `--assert` in the text**, including 125 in comments and one
  parked inside `comment { ... }`. Twenty files then looked permanently short
  of a target that was never there.
- **Never running an assertion inside a FOREACH.** It cannot be sliced out --
  the loop variable only exists while the loop runs -- so `--assert` is bound
  to a recorder and the enclosing expression runs as it stands.
- **Never counting what trailed an assertion.** A top-level `--assert` takes
  everything up to the next dialect word, so the `if find ... [...]` blocks
  after one were run and their assertions neither counted nor recorded. That
  alone was 140 of checksum-test.r3's 264.
- **Reading before any interpreter existed.** The reader takes its maker from
  the evaluator at boot, so it refused constructs it can read and made the
  construction fix look like no fix at all.

## Nulls

Three, where the rule is none. `StructValue.from` returned null for a layout
it could not use; that null reached a block read out of struct-test.r3 and
surfaced a file away as a NullPointerException from a copy in the harness.
Asking is now separate from building, `BlockStorage` refuses a null at the
door, and the reader refuses to answer one.

## What this leaves

`known-gaps.txt` holds 963 entries, from 1,032 over 25 files. The list grew
because the suite did. **None of those failures was new when it appeared: they
were not passing, they were not being asked.** They are the real porting
backlog and the honest measure of the port, and the list only ever shrinks --
1,033 came off with the MAKE and TO work, with COPY, with bitsets, with the
checksum port and with the binary dialect.

## What the suite does not ask

**The suite is the measure, and it is not the whole surface.** Running all 930
combinations of MAKE and TO against fifteen target types and thirty-one source
values, through JEBOL and through `./r3` side by side, found 140 answers that
differ. Rebol's own suite asserts most of those families and names them in
`known-gaps.txt`, so they were already counted. Some it never asks at all, and
those had no name until the sweep: `to integer! #FF` answered a refusal where a
real Rebol reads the digits as hex and says 255.

**A hundred and thirty-nine of the hundred and forty are fixed.** What is left
is one answer, and it is a quirk rather than a rule:

```
to paren!  1   a typeset converted to a paren comes back a *block*, because
               the C's `Set_Block` writes REB_BLOCK whatever type was asked
               for. JEBOL returns the paren that was asked for, which is
               arguably the better answer, and no assertion covers it
```

The decimal, percent and binary columns went by porting the make-and-to switch
of `T_Decimal`, of `make_binary` and `Scan_Decimal` with them. The path column
went by fixing molding rules rather than any conversion. The string column went
by porting `make_string`, which turned out to be about FORM: an any-string is
copied as it stands so a tag loses the brackets FORM keeps, a path keeps its
slashes, and `Form_Object` writes one field to a line with no `make object!`
around them -- while *molding* each field's value, which is the part of it that
cannot be guessed. And the last seven went with one line: **a nought byte ends
the source**, so `make block! #{31 00 32}` is `[1]` and an empty one is `[]`
rather than a block holding a nought.

**Both ported columns turned out to be a list and not a rule**, and reading
them as a rule is what had gone wrong. Anything with bytes underneath looks
convertible to a binary, and a percent, a paren, a path and an issue all have
bytes and are all refused, where the decimal, block, string and word they
resemble are taken.

**The path column was not a conversion problem at all.** Thirty-nine of the
forty-six were one molding rule read too loosely -- `isAnyWord()` where the C
says `IS_WORD` -- which wrote `a:/b`, `/a/b` and `#a/b` in a form that does not
read back. Worth remembering when a column of the sweep looks large: count the
causes, not the cases.

**A question never asked reads as a wrong answer.** Twice more, and both in
bitsets. `pick` on one accepted a char and returned false for everything else,
so `pick charset "a" 97` said the set does not hold `a` -- where the C shares
one arm between `A_PICK` and `A_FIND` and `Check_Bits` takes a char, an
integer, a string, a binary or a block of ranges. And `zero?` refused a bitset
outright although the C declares its argument as a bare `value`: for a bitset
it is not a comparison but `Is_Zero_Bitset`, which asks whether every byte is
what an empty set would hold -- nought, or `0xFF` where the set is written as a
complement. Reading that arm also turned up a range test over the datatype
table, so a zero pair and a zero tuple are zero too.

**COPY of a map returned the same map.** Not a suite gap -- a data-corruption
bug: `c: copy m` then `c/a: 99` changed `m/a` too, because the `copied` switch
had a case for every container except a map and a map fell to
`default -> original`. The same shape as the bitset COPY bug already recorded in
`BitsetValue.duplicate`, where a shallow copy let the url parser scribble on the
catalogue's own charset.

Chasing it turned up two more. **`same?` compared maps and bitsets by content**,
so a map was the same value as its own copy -- everything holding its contents
somewhere answers by *where*, which is the whole of the C's mode three. And
**`/types` and `/deep` were one question when they are two**: `/types` says
which datatypes are duplicated rather than shared, `/deep` says whether to keep
doing it inside what was duplicated. Without `/deep` a copied member is copied
and its own contents stay shared, which is what
`if ((types & CP_DEEP) != 0)` guards. Sixty-three of `copy-test.r3`'s
sixty-four assertions came off together, and it is now down to one.

**MOLD/ALL is a flag every value reads, not a choice made once.** The last
eight went with it. `MOPT_MOLD_ALL` sits on the C's mold state, so a date below
it writes ISO and a typeset writes its construct form, and a path that has to
fall back to a construct sets the flag for its own contents whether or not the
caller asked for it. JEBOL had no molder object to hang a field on, so the flag
is a thread-local beside the two the file already keeps for nesting depth and
recursion.

**The datatype enum was not in `types.reb` order and now is.** Forty-three of
the fifty-eight sat somewhere else, which is invisible until a typeset molds
itself: a typeset writes its members by walking the table, so
`mold any-string!` was `[string! file! url! email! tag! ref!]` where a real
Rebol writes `[string! file! email! ref! url! tag!]`. Reordering the enum fixed
every typeset at once and moved nothing else -- the gate was clean and
`system/catalog/datatypes` still reads in order. The enum's own doc now says
the order is not free to change, and why.

**Twice now, one JEBOL helper stood for two different C functions.** MAKE and
TO were one `convertedTo`; FORM and the arm TO STRING! uses were one
`runTogether`. Both looked like tidy sharing and both gave wrong answers,
because the C has two functions for a reason and the difference is exactly what
gets lost. The second one was worse: taking a tag's brackets off in the shared
helper broke AJOIN, AJOIN/with and COMBINE in the same commit that fixed
TO STRING!. When the C has two entry points, JEBOL wants two as well, even when
they agree on nearly everything.

**And the sweep does not replace the suite.** A sweep builds values and asks
about them, so every value in it stands at its head. `mold next 'a/b` is a path
standing at its second of two, and that is what caught the same rule reading
the remaining count where the C reads the whole series length. All 930 cases
agreed and mold-test.r3 still said no.

The sweep itself is worth repeating on other datatypes. It is cheap, and it
found two things that four separate readings of the C had not. See
`docs/rebol-findings.md` entries 21 and 22, both of which came out of one run.

## The error catalogue has never been counted

`too-long` is one of Rebol's error ids and JEBOL simply did not have it. That
was found by needing it, which is no way to find things, so the whole
catalogue was compared: **`src/boot/errors.reb` names 142 ids and JEBOL can
raise 69 of them.**

```
Access    32   ports, files, network, security -- areas JEBOL reaches through
               the host-grant system instead, so most of these have no arm
Script    21   the interesting column: behaviour JEBOL does implement and
               reports under a different id or not at all
Internal   8   memory and stack limits the JVM does not let us ask about
Syntax     4   bad-char, bad-checksum, bad-header, no-header
the rest   8   Note 3, Command 2, Throw 2, Math 1
```

The Script and Syntax columns are twenty-five ids naming behaviour that is
already here. `parse-series` is the one already known to matter: a get-word in
a string parse whose value is not a series should raise it, and JEBOL answers
no-match. `expect-type`, `bad-refine`, `no-return`, `type-limit` and
`self-protected` are the others worth reading the C for.

An id JEBOL cannot raise is not automatically a gap -- some of these are
raised nowhere in R3 either -- but the count is a measure that did not exist
before. `scripts/error-parity.py` prints it, so it is run rather than
remembered.

# Goal 2. The type-major refactor

**The original complaint, and much the largest piece left.** One `t-*.c` per
increment, bitset as the pilot.

The graphics work left a hint about the shape: `PaintInstruction` is sealed, so
adding a kind broke every renderer's switch at compile time. `VectorValue`
proved it again -- adding it to `SeriesValue permits` made the compiler
enumerate every arm that needed work. That is what the action seam wants.

# Goal 3. Graphics

**DRAW renders 22 of R3's 36 commands.** The fourteen it does not:

```
arrow  clip  gamma  grad-pen  image  image-filter  image-options
image-pattern  invert-matrix  line-pattern  spline  text  transform  triangle
```

`image` and `text` are the two that make pages look wrong rather than plain.

Also here: the stroked-curve comparison problem, the 522 lines of old markup
path, VID, Android, and the events-name-the-wrong-window one.

# Goal 4. The boot

**343ms for the first interpreter, 72ms once the JVM has settled.** A
7900-test run pays the 72ms per class, and that is the floor rather than the
machine.

Pool first, then library caching. The series byte accounting behind STATS is
already in that allocation path, and it costs about 2ms of the 72.

# Goal 5. Loose ends

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

# Goal 6. The 32 prelude forks

`prelude.reb` defines 36 words and Rebol defines 32 of them in `src/mezz` too:

```
all-of  any-of  body-of  cause-error  clean-path  clos  closure  collect
default  dirize  does  empty?  enum  funco  has  join  keys-of  map  max  min
rejoin  script?  spec-of  split-path  suffix?  title-of  to-word  types-of
undirize  values-of  words-of  wrap
```

Audit by identity rather than by datatype: for each one, is JEBOL's version
the same function, and if not, why was it forked?

# Goal 7. The suite assertions that cannot come off -- DONE

**They have a file of their own now.**
`src/test/resources/rebol-suite/fails-on-rebol-too.txt` holds the assertions a
real Rebol fails as well. They are not run and they are not gaps: JEBOL
answers what the Rebol they came from answers, and the assertion is wrong
about that Rebol. Leaving them in `known-gaps.txt` said there was work here
and there is not; deleting them would have lost the finding.

Nothing goes in on reasoning. Each line carries the `./r3-head` session that
settled it, and two gates hold the file honest: every line must name an
assertion that exists, and no line may be in both files at once.

Two are in it today -- `to char! #FF`, where the C grew a `case REB_ISSUE`
after the test was written, and the SWAP one whose own comment says "Known
issue!!!".

**The DER codec looks like a third and is not yet proven.** On `./r3-head`,
`codecs/der/verbose: 2` followed by `load %test.pfx` raises `not-defined
SEQUENCE`, so that group's assertions cannot hold on the Rebol they came
from. They are still counted as gaps because they are *blocked* rather than
run -- see Goal 9 -- and an assertion that never ran is not evidence of
anything.

# Goal 9. The assertions that never run -- MOSTLY DONE

**Was 510 of 1,016 never asked. Now 463 of 965.** The harness cut a run of
setup at the next *top-level* dialect word, and codecs-test.r3 is a sequence
of `if find codecs 'wav [...]`, `if find codecs 'der [...]` whose dialect
words are all nested inside those blocks. The whole tail of the file was one
step, 23,183 characters and 187 assertions, and the DER codec raising took
every other group with it.

Each expression is its own step now. Four things had to be right:

1. Apply the cut in all three branches of `stepsIn`. `===end-group===` falls
   to the default arm and that is the one holding the tail of the file.
2. Count in code points. The spans are, and indexing the source in Java's
   sixteen-bit units put every position after the file's first emoji in the
   middle of another line, so the cut never fired and left no trace.
3. Only a word may open an expression. Cutting at any line-starting value
   splits `f: func [spec][body]` when the body bracket starts a line, and
   both halves read: one a function of one argument, the other a block.
4. Resolve nested assertions at file scope, not per step -- see the numbering
   work. An assertion written inside a function runs when the function is
   called, which is a later step.

## What is left of it

**450 assertions are still behind "the block it is written in ended first".**
These are not the harness cutting too coarsely: they are blocks whose own
earlier line raised, which is what a script does. The largest are
handle-test.r3 (47), checksum-test.r3 (28), crypt-port-test.r3 (26) and the
four compression groups (72), and every one of those is a feature this build
has not got rather than a slicing fault.

Worth checking a sample against `./r3-head` before doing more here: if the
same line raises there, the assertions behind it belong in
`fails-on-rebol-too.txt` rather than in the gap list.

# Goal 8. LLM-friendly MCP tools

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

**Goal 0, and it is not close.** A refactor is safe in proportion to what the
tests can tell you, and they are currently telling you about a third of the
language. Moving a load-bearing wall on that footing is how a regression gets
committed and stays hidden. Vendor the 54 files, take the failures, and then
Goal 1 is a different and much safer proposition.

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
