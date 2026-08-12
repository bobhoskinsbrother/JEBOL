# TODO

Only work that is left. History lives in git and in `docs/`.

## The goal

**Port every function a real Rebol 3 has into JEBOL.** The imported Rebol test
suite is how a port is checked, not what is being aimed at.

## The rule about layers

Rebol writes about a third of its library in C and the rest in REBOL.

**A function Rebol writes in C is written in Java here. A function Rebol
writes in REBOL is imported from its own file and loaded as a resource. It is
never rewritten and never copied into `prelude.reb`, not even verbatim.**

The test is mechanical: find which R3 file defines it. `src/core/*.c` means
Java. `src/mezz/*.reb` means copy that file into
`src/main/resources/org/jebol/mezz/`, add it to `ORDER.txt`, and fix whatever
native it turns out to need. Decision 13 in `docs/decisions.md` says why a
verbatim copy is still a fork, and what it cost last time.

## The C is the authority. There is no binary

`./r3` has been deleted on purpose. A running build answers what one build
does on one machine; Rebol's C says what the language is, and it explains
itself as well. Every place the two disagreed during the port, the C was
right.

So the sources, in order:

1. `~/Code/personal/rebol3-source/src/boot/types.reb` -- the datatype table:
   each datatype's typeclass, whether it has a path handler, whether MAKE can
   build one, and which typesets it belongs to.
2. `src/boot/actions.reb` and `src/boot/natives.reb` -- the declared specs of
   all 224 C functions, with every argument's datatypes and every refinement.
3. `src/core/*.c` -- the arms themselves. `t-*.c` for datatypes, `n-*.c` for
   natives, `c-do.c` for the evaluator, `l-scan.c` for the reader, `s-mold.c`
   for molding, `boot/errors.reb` for the error catalogue.
4. `src/test/resources/rebol-suite/` -- Rebol's own tests, as a check on the
   port rather than as a specification of it.

**There is no record of the binary either, and there is not going to be.**
`surface.txt`, the dump of a running 3.22.1's library, is deleted. It was not
merely weaker evidence than the source: it was wrong about what it was being
asked. It listed every top-level word of every file that build had loaded, and
most of those files are modules whose words no script can reach -- forty in
`prot-tls.reb`, forty more in `codec-swf.reb`. The porting backlog read 134 of
580 against the dump and reads 30 of 353 against the source, so the work had been
pointed at functions nobody can call.

It hid a second thing. The parity audit skipped any function the C declares that
the dump did not carry, silently, and the skip covered a bug of our own: the spec
parser was reading `;`-commented lines, so it demanded PARSE's `/all`, TRACE's
`/stack` and the `/as` that READ and WRITE both carry commented out. Both are
fixed, and the audit now compares the source against JEBOL and nothing else.

## The three measures

**`ActionParityTest`** multiplies the datatype table by the arms table and
calls every pair. 575 calls the C implements; the ratchet says how many still
answer `cannot-use` or `expect-arg`. This is the sharpest measure there is,
because a missing arm is invisible in every declaration: APPEND on a map,
CHANGE on a binary, FIND on an object and the walk over a map were all found
this way, and each had looked complete.

**`scripts/c-parity.py`** compares the declared specs against JEBOL's
registry and prints five verdicts: MISSING, WRONG LAYER, REFINEMENTS,
ARGUMENTS, TYPES. The five goals below are those five verdicts.

```
./gradlew test --tests 'org.jebol.suite.SurfaceReportTest' \
               --tests 'org.jebol.suite.PortingBacklogTest'
python3 scripts/c-parity.py
```

**`RebolSuiteTest`** runs Rebol's own 3721 assertions in eleven seconds on its
own, where the whole gate takes eight minutes. Use it between changes and the
gate before committing.

```
./gradlew test --tests 'org.jebol.suite.RebolSuiteTest'
```

`scripts/c-surface.py` rebuilds `src/test/resources/r3/c-surface.txt`, which
is what the first two read. Rerun it when Rebol's tree changes.

## How to port one function

Four steps, in order. Do not begin one before the last is finished.

1. **Read the C.** The whole function, not the part that looks relevant. The C
   carries the rules no probe will show you: which flags are set together, what
   a count of zero means, where a search starts, which `case` labels share one
   body and which fall through into a refusal.
2. **Copy the logic into Java.** Follow the C's structure. Where the C has a
   branch, have a branch. A rewrite that looks tidier is a rewrite whose bugs
   are yours and not Rebol's.
3. **Write the tests from the C, not from your Java.** Read the C again and
   write a test for every branch, flag combination and boundary it guards.
   Tests written by reading the port only prove the port agrees with itself.
4. **Run the suite.** A failure there is a rule the C states and the port
   missed.

**The spec in `spec/` is distilled from the Java at the end, not written
first.** The order changed for a reason: RECYCLE, STATS and STACK were each
specified here from what seemed sensible, and all three disagreed with
`n-system.c`. A spec written before the C has been read is a guess wearing a
spec's clothing.

**Goal 1 looked like the exception and was not.** Five of its functions were
about to be specified first, on the grounds that they report on structures a JVM
has not got and so had nothing to distil. Reading them said otherwise every
time: DS prints the frame stack, DUMP prints nothing in any shipped build, CHECK
tests an invariant that cannot break here, EVOKE already refuses its debug chants
with `feature-na`, and LIMIT-USAGE records a number that nothing enforces and
that no script can reach. There was something to read in all five, and it was
never in the function -- it was in `#ifdef DEBUG`, in a default policy value, or
in the line of `mezz-secure.reb` that takes the word away.

So the order stands as it is. Where you think a decision is needed, read wider
first: the caller, the policy it consults, the file that unsets it.

**Read the declaration and the arm as two different things.** A datatype the
spec block does not list never reaches an arm, so it is refused as `expect-arg`
by the declaration; a datatype the spec lists and the arm turns away is
`cannot-use`. A script can tell them apart. `indexz? 5` is the first and
`indexz? none` is the second, and writing one arm to answer for both put a
corpus case red. The C settles it either way: an integer is not on
`indexz?`'s declared list, so it cannot reach an arm.

**Rebol's own test files are a third authority, and the cheapest one.**
`src/tests/units/*.r3` in the source tree records what a real 3.22.1 answers, in
Rebol's own words, for cases nobody would think to ask. Reading the struct in
`reb-gob.h` and the arms in `t-gob.c` gave a gob that was wrong in six places;
`gob-test.r3` named all six in seventy lines. It settled that a fresh gob is a
hundred square, that `change` on an empty pane is `past-end`, that `pick g
'offset` is `invalid-arg` rather than a field read, that two gobs are never equal,
and that molding a gob shows only offset, size and one content field. The same
file's `image-test.r3` settled that a pixel reads back as four parts and not
three, which the struct cannot tell you and which a whole class of assertions
here had wrong. Read `boot/`, read `src/core/`, then read `src/tests/units/`
before writing a line.

**The C has its own bugs, and copying one is not fidelity.** `To_Local_Path`
reads a segment of `..x` by falling through its double-dot branch into a line
that writes the character it looked ahead at, so Rebol answers `x..x`. Where the
C is plainly wrong rather than surprising, write what it meant and say in a
comment what it does.

---

# Goal 1. Done. The C functions JEBOL had not got

**`scripts/c-parity.py` now reports MISSING: 0.** Twelve at the start and twelve
done: `trace`, then `check`, `ds`, `dump` and `evoke`, then `limit-usage`, then
`as-color` with `image!`, `map-gob-offset` with `gob!`, `map-event` and `wake-up`
with `event!`, and `do-codec` and `release` with `handle!`. Every function Rebol
writes in C, JEBOL now writes in Java.

Kept rather than deleted, because what each of them turned out to be is the
useful part. In almost every case the answer was in a line somewhere other than
the function.

**The four datatypes were part of this goal, not a prerequisite outside it**, and
that reading is what closed it. Six of the twelve functions were small arms over
values JEBOL could not make; treating the datatypes as the work and the functions
as the last hour of each was right every time.

The order is not ours: it is what `boot/types.reb` declares and what the
dependencies say, and the two agree. `gob!` holds an image in six places and
`event!` references a gob in two, while `image!` leans on nothing.

| # | Datatype | The C | Functions it closes | Suite assertions | State |
| --- | --- | --- | --- | --- | --- |
| 1 | `image!` | `t-image.c` 1617 lines, 27 arms, `n-image.c` 749 | `as-color` | 13 | done |
| 2 | `gob!` | `t-gob.c` 991 lines, 24 arms | `map-gob-offset` | 3 | done |
| 3 | `event!` | `t-event.c` 554 lines, 2 arms | `map-event`, `wake-up` | 7 | done |
| 4 | `handle!` | `c-handle.c` 200 lines, 2 arms | `do-codec`, `release` | 1 | done |

**All four datatypes are built, and so are the five functions they unblocked.**
`ImageStorage`, `ImageValue`, `ImagePath` and `Colours` for the first;
`GobStorage`, `GobValue` and `GobPath` for the second; `EventValue`,
`EventCatalogue` and `EventPath` for the third; `HandleValue` and `Codecs` for the
fourth. 45 tests in `GobFromTheSourceTest`, 32 in `ImageFromTheSourceTest`, 23 in
`ColourFromTheSourceTest`, 42 in `EventFromTheSourceTest`, 31 in
`HandleFromTheSourceTest`, 15 in `MapGobOffsetFromTheSourceTest` and 13 in
`MapEventAndWakeUpFromTheSourceTest`. MISSING 5 → 0, the porting backlog 30 → 24,
and the suite 345 → 343 -- the two that moved are the codec assertions, because
DECODE reaches DO-CODEC and could not before.

**Two gaps came out of the event that were not about events.** A spec block could
only carry values the source spelled out, because neither spec walker ran its
values through `Get_Simple_Value` -- so `make gob! [size: g/size]`, which Rebol's
own gob test asserts, could not work. And a port's fields could be read through a
path and not written, though `boot/types.reb` gives an object, a module, an error
and a port the same path handler: `p/awake: func [event] [...]` is how a scheme
says what to do when an event arrives, and WAKE-UP reads that same field back.
Both are fixed. An error's fields are still a record here rather than a context,
so writing one is still a gap.

**Two things the C settled that reading the struct did not.** A pixel reads back
as a *four*-part tuple, always: `Set_Tuple_Pixel` writes `VAL_TUPLE_LEN(tuple) =
4` before it writes a byte, so `img/1` on a white image is `255.255.255.255`.
Rebol's own `image-test.r3` asserts it twice. And a fresh gob is not an empty
one: `Make_Gob` clears the struct and then writes 100, 100 and 255, so `make gob!
[]` is a hundred square and opaque. Both were wrong here first, and both were
found by reading `src/tests/units/` rather than the struct -- Rebol's own test
files are a third authority beside `boot/` and `src/core/`, and `gob-test.r3`
settled six more.

**`handle!` needed a producer, and the producer was not the one this file named
for two sessions.** Its make column in `boot/types.reb` is `-`, so `make handle!`
cannot build one, and `handle-test.r3` reaches for `rc4/key` and `aes/key` -- so
the note here said the datatype waited on a cipher, and that a cipher was separate
work.

Wrong, and one grep settled it. `Init_Codecs` in `b-init.c` runs at boot and calls
`Register_Codec` twice, and `Register_Codec` is four lines whose last one is
`SET_HANDLE(value, dispatcher, SYM_CODEC, HANDLE_FUNCTION)`. So a stock build
hands out two handles before a script runs, both in `system/codecs`, and both are
pure computation: `Codec_Text` decodes UTF bytes to a string and `Codec_Markup`
splits HTML into strings and tags. No cipher, no window, no file.

**And the two layers met on their own.** `base-defs.reb` -- Rebol's own file,
imported and loading for months -- opens its codec section with
`foreach [codec handler] system/codecs [if handle? handler [...]]` and wraps each
boot handle in an object with a name, a title and a list of suffixes. That loop
had nothing to walk until these two handles existed. Then `sys-codec.reb`'s DECODE
does `either handle? try [cod/entry] [do-codec cod/entry 'decode data]`. So
DO-CODEC was never a function without a caller: two of Rebol's own files had been
reaching for it all along, and the suite went down by two the moment it answered.

The lesson is the one this file already states twice and that this still got
wrong: read wider before deciding something is blocked. The blocker was named
from `handle-test.r3` alone, and `handle-test.r3` tests the *other* kind of
handle.

**The open question about `handle!` is answered too.** It asked
whether a handle should carry a Java object directly or stay opaque as the C's
does. It stays opaque, and the C's own shape says how:

- A handle has a *type*, which is a word: `h1/type = 'rc4`, and
  `words-of h1` is `[type]`. Nothing else about it is readable.
- The types are a fourth catalogue. `Register_Handle` appends the word to
  `system/catalog/handles`, up to `MAX_HANDLE_TYPES` of 64, and a handle stores
  the index. Same shape as an event's type, and the same reason: a script can
  read the list.
- `==` compares type *and* data, `=` compares type *only*. So two rc4 handles
  with different keys are `equal?` and not `same?`, which Rebol's test asserts
  twelve times over.
- `lesser?` compares the registration index, not the spelling. `aes` sorts before
  `rc4` because `Init_Crypt` registers it first, and `sort` on a block of handles
  depends on that.
- It molds as `#(handle! rc4)`, works as a map key, and takes part in every set
  operation.

So the Java object goes behind the handle where a script cannot see it, and the
only thing a handle publishes is which kind it is. Built that way.

**And three of those five rules read like defects until you see which kind they
are about.** Every one of them is guarded by `IS_CONTEXT_HANDLE`, and a codec is
the other kind:

- `h/type` on a codec is **none**, not `codec`. `PD_Handle` ends "for the data
  handles, return NONE on get", so a function handle tells a script nothing about
  itself.
- `equal?` on a codec is **false, even against itself**. Both sides have to be
  context handles: `IS_CONTEXT_HANDLE(a) && IS_CONTEXT_HANDLE(b) && ...`. A
  function handle has no type to compare, so there is nothing for equality to
  answer.
- Which would make FIND unable to find one -- except FIND does not use equality.
  `Find_Block` calls `Cmp_Value`, and `case REB_HANDLE: return Cmp_Handle(s, t)`
  is the *ordering* comparison. So `find` works on handles where `equal?` does
  not, and that is the one place in the language where those two part company
  this far. It cost a red test to notice.

**What this bought, stated plainly:** a closed goal, four datatypes' worth of new
rows in `ActionParityTest`'s matrix, 14 of 5b's TYPES lines becoming real work,
the porting backlog down from 30 to 24, and two of Rebol's own suite assertions.
The suite was the surprise -- these datatypes were expected to buy none.

## 1a. Done: the four that looked like they reported on the C's own memory

**Not one of them needed a refusal, and the reading is why.** They were about to
be specified as four errors naming the host. Reading them again -- which the
preamble's own rule demands before deciding anything -- said otherwise, and the
finding is worth keeping because it will recur: **the C's source and the C's
shipped build are not the same program.** Two of these four have bodies inside
`#ifdef DEBUG`, so a released 3.22.1 runs the last line and nothing else.

| | What a shipped 3.22.1 does, and so what JEBOL now does |
| --- | --- |
| `ds` | Prints the frame stack: the word the call was made through, the slot count, the function's datatype, then a line per slot. Not a C memory structure at all, and compiled into every build. |
| `dump` | Answers its argument and prints nothing. The whole body is debug-only, so `return R_ARG1;` is all a release build reaches. |
| `check` | Answers the series it was given. It checks the C's own invariant -- a terminator past the tail and none before it -- and a JEBOL series has no terminator to be wrong, so the check holds always. |
| `evoke` | The six watch-and-crash chants raise `feature-na`, which is Rebol's own error for a build that cannot do what was asked. `stack-size` and `delect` are accepted and need nothing done; an integer asks for pool checks that cannot fail here; anything else prints the list of chants. |

`feature-na` is the part worth reusing. It is in the Internal category of
`boot/errors.reb`, the C raises it exactly where a body is compiled out, and it
already means "this build, not this language" -- which is what a JEBOL that has
not got something needs to say. It is now an `EvaluationFailure`, and Rebol's own
suite has a case waiting for it: `remove/key "abcd" #"a"` must raise it.

DS needed one change under it. The evaluator kept a stack of the *names* of the
functions being run, which can answer the first field of a frame line and
nothing else. It now keeps the call -- name, function and locals -- so the slot
lines have values to print. That is also what STACK/ARGS and STACK/FUNC would
need, and both still answer none: they are not part of this goal, and changing
what they answer needs its own tests.

Specified in `spec/natives.allium` under "The four diagnostics, read again", and
pinned by `DiagnosticsFromTheSourceTest` in 28 tests.

## 1b. Done: LIMIT-USAGE, which does less than its name

**Two lines elsewhere decide what it is worth, and both say "nothing yet".**

The native is four lines: record a number for `eval` or for `memory`, once each,
and answer unset. `if (Eval_Limit == 0) Eval_Limit = Int64(...)` is the whole
rule, so a second call for the same field writes nothing and a field that is
neither falls out of the bottom untouched.

**Nothing enforces the number.** It is read in one place, inside `Do_Signals`,
and handed to `Check_Security(SYM_EVAL, POL_EXEC, 0)`. Every policy in
`boot/sysobj.reb` starts at `0.0.0`, which is ALLOW, and an allowed policy does
nothing. So a stock Rebol records the limit and runs straight past it.

**And no script can call it.** `mezz-secure.reb` ends the boot with `unset in lib
'limit-usage`, and `mezz-tail.reb` calls that. Both files are Rebol's, borrowed
verbatim, so the word is gone here for the same reason it is gone there.

That leaves Rebol's own SECURE broken, and JEBOL faithfully broken with it:
SECURE's `eval` and `memory` cases call `limit-usage`, SECURE is bound to the
slot PROTECT-SYSTEM unset, so `secure [eval 100]` raises `no-value` in a stock
3.22.1 exactly as it does here. `LimitUsageFromTheSourceTest` pins that rather
than fixing it. Fixing it means deciding what SECURE is here, which is its own
piece of work -- see the open question in `spec/natives.allium`.

Two things nearly got written and should not have been. An enforcement, which
would stop a script Rebol lets run. And a host-facing way to read the recorded
limit, which is a public API for something no script can set.

## 1c. Done: the six that waited on a datatype

Each was a small arm over a value JEBOL could not make, so the datatype was the
work and the function was the last hour of it. In the C's own declaration order:

- **`image!`** (22nd) unblocks `as-color`, and per 5g may bring VID with it: the
  draw dialect is REBOL rather than C, so `view-funcs.reb` and the draw dialect
  may load and work once `n-image.c` is ported. Built, less `resize`, `blur`,
  `premultiply`, `image-diff` and `image` from `n-image.c`.
- **`gob!`** (53rd) unblocks `map-gob-offset`. Both built. The pane is the series
  and the content is a union, and its 24 arms all work on the children rather
  than on the gob.

  One thing came out of it that is not about gobs. COPY's declared typeset was
  open here where the C names eight datatypes, so `copy 5` answered 5 and a test
  asserted that it should. It raises `expect-arg` in a real 3.22.1, and narrowing
  the declaration is what made `copy make gob! []` refuse. SORT was the same and
  is narrowed too. Every remaining TYPES line in 5b is potentially this: a
  declaration left open, and a test written against the gap.
- **`event!`** (54th) unblocks `map-event` and `wake-up`. All three built. It is
  a value cell rather than a container, so it has two arms and no more, and three
  of its fields are one four-byte word read three ways.
- **`handle!`** (55th) unblocks `do-codec` and `release`. All three built, and it
  did have a caller waiting: DECODE in `sys-codec.reb` reaches for DO-CODEC and
  had been failing on the word. The producer turned out to be `Init_Codecs` rather
  than the crypto family, which is written up above -- and `base-defs.reb` wrapped
  the two boot handles into codec objects the moment they existed, with no change
  here.

`vector!` (23rd) and `task!` (51st) are in the same table and blocked no function
in this goal, but they do appear in 5b's list of datatypes a parameter takes, and
they are the next two in table order.

**What is left of these four datatypes, and it is not much.** `n-image.c` has
five functions nobody has ported -- `resize`, `blur`, `premultiply`,
`image-diff`, `image` -- and none of them is on the MISSING list because none of
them is declared in `boot/natives.reb` as a native this build has. A context
handle has no producer, so RELEASE can only ever answer false here; the true
branch waits on a cipher, and `rc4` and `rsa` are on 5a's backlog. An error's
fields are a record rather than a context, so `e/id: ...` is still refused where
`boot/types.reb` says it should not be.

# Goal 3. The refinements JEBOL is missing -- DONE

`scripts/c-parity.py` calls these REFINEMENTS, and the list prints empty as of
2026-08-12: `write`, `call`, `read` and `load-extension` all declare the C's
full refinement set. The work landed together with Goal 4's arguments, one
pass per function -- see Goal 4's table for what each one does now. The port
growth the section predicted happened as written: `FilePort` crosses bytes,
`ProcessPort` crosses ProgramToStart/ProgramResult, and the end-to-end tests
drive the console (`ReplEndToEndTest`: WritingAFile, RunningAnotherProgram).

# Goal 4. The arguments JEBOL is missing -- DONE

`scripts/c-parity.py` calls these ARGUMENTS. **All six functions are done,
2026-08-12, and both the REFINEMENTS and ARGUMENTS lists print empty.** What
remains for these functions is TYPES lines only, which is Goal 5.

| Function | What landed |
| --- | --- |
| `transcode` | `/line count` and the answer's shape; see the note below |
| `write` | `/part /seek /append /allow /lines /binary /all`; a plain write truncates, /seek and /append do not, the answer is the destination; specified under "Writing a file" |
| `read` | `/part /seek /all`, and the C's answer shape: a plain read answers the file's BYTES, /string decodes, /lines splits; a directory answers its names; specified under "Reading a file" |
| `call` | `/console /info /input in /output out /error err`; series redirections append into the caller's buffer and imply /wait; /info answers an object and turns a refusal-to-start into its `error` field where plain CALL raises `call-fail`; ProcessPort now crosses ProgramToStart/ProgramResult |
| `request-file` | `/filter list`: name-and-pattern pairs, formed, odd counts refused |
| `load-extension` | `name [file! binary!]` and `/dispatch function [handle!]`, still always refused |

**These are mostly the same functions as Goal 3**, because a refinement and its
argument are declared together: `write/part` and `write`'s `length` are one
change. Do the two goals as one pass per function rather than two passes over
the list.

**What TRANSCODE turned out to be, now that it is done.** One line of the C
decides the shape of the whole answer:

```c
next = scan_state.opts > 0;
```

`opts` is raised by /NEXT and /ONE (SCAN_NEXT), by /ONLY (SCAN_ONLY) and by
/ERROR (SCAN_RELAX), and by nothing else. Read as a name the line looks like a
bug. Read as a question -- did the caller ask the reader to stop before the end
of the source -- the rest follows, because a caller who wanted the failure
handed to them has the same reason to want the unread text as a caller stepping
through a value at a time.

Five behaviours came out of that one reading, and JEBOL had all five wrong.
/ONLY was ignored entirely, and reads one value at *every* depth, so
`transcode/only "[1 2]"` is `[[1] " 2]"]` -- the bracket is never consumed and
lands in the remainder, which also means `transcode/only "[1"` does not raise
where `transcode "[1"` does. /ERROR threw away the values read before the
failure. The remainder is appended for three refinements, not one. An empty
result plus any of the four raises past-end. And `transcode/next "1d"` answered
`[none "1d"]`, swallowing a failure and handing back something a source could
genuinely have held.

**Two of those five were refuted by a second opinion before being built**: /ONLY
stopping only at the top level, and the nested remainder being empty rather than
`" 2]"`. Both were guesses written up as readings of the C. A sub-agent asked to
refute rather than confirm caught both, and the same pass found the
`load-test.r3` and `sys-load.reb` witnesses that settle the shape.

**A word about `/local`.** The comparison leaves it out, along with the words
after it, and that is on purpose: they are the function's own working names
rather than anything a caller supplies. In the C they are stack slots the
function fills itself -- `*DS_ARG(4)` in `Loop_All` is FORSKIP's `orig` -- and
in a JEBOL function they are locals. Counting them made FORSKIP, REQUEST-DIR and
REQUEST-FILE look short of arguments none of them has any use for. A caller can
still write `forskip/local` in Rebol and cannot here, which is a difference
nobody will meet.

# Goal 5. Everything else the audit found

## 5a. The five action arms still missing -- DONE

All five landed 2026-08-12 and the ratchet is at zero: binary! complement
(new bytes, position respected), bitset! insert (APPEND's arm, membership
even on a complemented set -- which also fixed APPEND's raw OR), bitset!
remove (/key or /part or missing-arg, both is bad-refines, the range is four
kinds), object! insert (APPEND's arm, shared method), object! put (a non-word
key is invalid-arg). REMOVE/KEY on an ordinary series raises feature-na, as
series-test.r3 asserts. One open question parked in the spec: the C's REMOVE
skips the complement inversion APPEND gets, and wants measuring against a
real R3. Specified under "Reading and writing the bits of a set"; tests in
ActionArmsFromTheSourceTest. The old text follows for the C references.

```
binary!   complement
bitset!   insert  remove
object!   insert  put
```

Each is a small arm with the C to hand:

- `complement` on a binary is `Complement_Binary` in `s-ops.c`: every byte
  flipped into a new binary rather than changed in place.
- `insert` on a bitset is APPEND's arm: `case A_APPEND: case A_INSERT: diff =
  TRUE; goto set_bits;`, because a set has no front.
- `remove` on a bitset needs `/key` or `/part` and raises `missing-arg`
  without either: `else Trap0(RE_MISSING_ARG); // /key or /part is required`.
- `insert` on an object is APPEND's arm there too, in `t-object.c`.
- `put` on an object is `Extend_Obj`, which refuses a key that is not a word --
  `else { Trap_Arg(key); }` -- rather than answering `cannot-use`.

**One of these has a suite case waiting for it.** `remove/key` on a string must
raise `feature-na`, and `series-test.r3` asserts exactly that:

```
--assert all [
    error? e: try [remove/key "abcd" #"a"]
    e/id = 'feature-na
]
```

`feature-na` arrived with Goal 1a, so the id exists now and the arm is the only
part left.

## 5b. The datatypes a parameter takes -- DONE

Was 60 lines on 2026-08-12 and is at zero apart from vector!, which is
datatype backlog. The last round: AS takes an example value as its type and
shares storage as the C does; READ, WRITE and RENAME declare the
port-machinery types and route them to schemes, which this host refuses by
name with no-service (WRITE on the console port works and prints); QUERY
reaches a date's fourteen parts and a handle's type in the same four field
shapes as a file; POKE writes an image pixel.

What landed, each from the C and pinned in MathsFamilyTypesFromTheSourceTest,
SurfaceTypesFromTheSourceTest and ActionArmsFromTheSourceTest: the trig and
log family narrowed to number! alone, as-pair and absolute likewise; the
/part range family widened to number!+series!+pair! with Partial1's runtime
refusals (a percent is not a decimal to Partial1; a foreign series is
invalid-part -- and the same-series form now works for APPEND's block arm,
which it never had); /dup takes number!+pair! with Int32's invalid-type
refusals; ENBASE and DEBASE limits narrowed and now actually bound the
reading; TO-HEX gained its char and tuple arms; COMPLEMENT gained image!;
AT and SKIP count as Get_Num_Arg counts (false is two); IN reaches modules
and refuses parens; COMPOSE/INTO fills any block-family target; QUERY's
field takes datatype! and drops the literal get-word; TAIL? and UNIQUE
narrowed to the C's sets (EMPTY? keeps none via its own made spec); COPY and
IN declare the unmakeable typeclasses as R3 does. The append/insert/change
/part and /dup arguments are named range and count as the C names them.

The vector!/task! lines remain datatype backlog, not surface work.

## 5c. The suite: 28 failing of 3721

**A malformed number is refused now, and it took three changes that only work
together.** The one-line refusal had been tried twice before and cost about twenty
assertions each time. What was missing was not the line:

1. **`0:0.001` had to become a time.** `Scan_Time` lists four shapes in a comment, and
   the fourth changes what the first two numbers mean: `12:34` is twelve hours and
   thirty-four minutes, `12:34.5` is twelve *minutes* and 34.5 seconds. JEBOL had no
   MM:SS shape, so a two-part time with a fraction was not a time at all -- it fell
   through to a *word*, silently. `mezz-debug.reb` line 114 is the only place in the
   borrowed library that writes one, and refusing digit-leading words stopped that
   file loading. A zero fraction does not count, so `12:34.0` is hours again:
   `if (part4 == 0) part4 = -1;`.
2. **The angle bracket has to be cut off before classifying**, which the C flags
   itself: `case LEX_CLASS_NUMBER: /* order of tests is important */`. Otherwise the
   refusal fires first and the whole `<` family goes -- `1<`, `1.2<`, `1.0<a>`,
   `1.#INF<`, `19-Jan-2010<`.
3. **Then the refusal**, because a word cannot begin with a digit and there is nothing
   left for `1d` to be.

**How it was found is the transferable part.** The refusal was applied deliberately as
a *detector*: it turns a silent word-fallback into a loud failure, and then transcoding
every borrowed file and every suite file named the offender. Exactly one across all of
them -- `0:0.001` at `mezz-debug.reb:114`. Two earlier attempts guessed at the cause
and paid twenty assertions; one diagnostic run named it in seconds.

That trick generalises: when a fallback is too permissive, refuse it temporarily and
transcode the corpus to see what was relying on it.


Present-and-wrong rather than absent, which is a different programme from the
four goals above and much the larger one. Where they sit:

| File | Failing | The clusters inside it |
| --- | --- | --- |
| `lexer-test.r3` | 51 | TRANSCODE's refinements (7), malformed literals (6), sign before pound (6), special tests (6) |

**Five of the fourteen malformed-literal cases are done: the file and email
scanners now validate.** `%^`, `%a^b`, `%a^ `, `%a%2h` and `a@%2h` were all read
as perfectly good files. The rules are in `Scan_Item`, `Scan_File` and
`Scan_Email`, and there are five: a control character is refused, a backslash
quietly becomes a forward slash, a percent sign wants two hex digits after it, a
caret is refused in an unquoted name and is an escape in a quoted one, and the
refused set turns away the rest.

**The lesson was the two stages.** `Scan_File` names eight characters to refuse --
`":;()[]\"^"` -- and reading that as one pass makes `(clean-path %a/b) = %a/b` a
syntax error, because the closing bracket gets taken as part of the name. The
lexer delimits the token *first*, stopping at any `IS_LEX_DELIMIT` character, and
only then does `Scan_File` check what it found. Five of those eight are
delimiters, so they can never be inside an unquoted name at all, which leaves the
colon and the caret as the two that really bite. Collapsing the stages cost 3700
tests in one gate run.

**And the caret is worth its own line.** In an unquoted file it is refused rather
than read, which the C flags in a comment -- "checks also if not used in file
like: %a^b which must be invalid!" -- and in a quoted one it escapes again,
including escaping the closing quote, which is why `%"a^"` fails as an
unterminated string rather than as an invalid one.

**And nine more went with the misplaced sigils.** A sigil names a word, and
`Scan_Token` answers a *negative* token -- its way of saying syntax error -- for
nine spellings that ask for a word which cannot exist. Each carries the C's own
comment naming what somebody tried: `// no '2nd`, `// no ':X`, `// no ''foo`,
`// no :'foo ::foo`. Plus `/a:`, from `if (*(scan_state->end - 1) == ':') return
-type;`, and `///refine`, which was already refused.

JEBOL read all nine as perfectly good lit-words and get-words.

**It also refused one shape the C allows on purpose.** `'///` is legal -- slashes
alone are ordinary words, so there is a word `///` and a lit-word naming it, and
the C flags it deliberate with `// allow '///`. Refusing it meant the reader could
not read back what its own MOLD wrote. Both directions matter, and only one of
them shows up as a suite failure.

**And one of the nine was already handled better.** `'_` and `:_` were refused
before this change by `refuseTheNoneWordAsAName`, whose error names *which kind* of
word was being read -- a script reads that as ARG1. The new check fired first and
answered without the name, which two existing tests caught. The duplicate is gone
and the reason is a comment where it was.

**The six slash words are done, and they were unspellable rather than merely
missing.** `/` and `//` are two rows of `boot/ops.reb` -- divide and
integer-divide -- so a script that wants to rebind one writes `/: :my-divide`, and
one that wants to pass it writes `:/`. Neither works by accident: a slash is a
`LEX_DELIMIT_SLASH` and ends the token before the colon is reached, which is why
the C gives each spelling its own arm and why the get-word arm carries the comment
"must be modified, because / is delimiter!".

**And the fix was narrowing a rule written an hour earlier.** The misplaced-sigil
pass refused any slash lexeme ending in a colon, on the strength of
`if (*(scan_state->end - 1) == ':') return -type;`. That check lives in the arm the
C reaches when a *word* follows the slash run; a colon immediately after the run is
a different arm two cases down and makes a set-word. So `/a:` is refused and `/:`
is not, and what sits between the slashes and the colon is the whole difference.
The same lesson as the file scanner's two stages: a check read out of its arm is a
check applied too widely.

**One thing corrected on the way, in JEBOL's favour.** This test claimed `//` takes
a remainder, because the spelling says so. `boot/ops.reb` says `// integer-divide`
and `% remainder`, so `9 // 2` is 4 and JEBOL already had it right.

**And the four arrow set-words are done.** A run starting with `<` that holds
nothing but `- = > ~` is a word, and a colon at the end belongs to it:
`Skip_Left_Arrow` consumes the colon and stops, and the caller reads the last
character to choose the token -- `return (np[-1] == ':' ? TOKEN_SET : TOKEN_WORD);`.
So `<-->:` is one set-word where it used to be the word and a stray colon, which
reads as an assignment and is not one.

**And the four angle-bracket cases are done, as one piece.** `a<a>` is a word and a
tag, `a<--` is two words, `a/b<` fails with `e/arg1 = "word"`, and `a/3<` loads as
`[a/3 <]`. Two rules meet, and what came before the bracket picks which:

- **A number just ends.** A path is assembled from separate tokens, so the last
  segment of `a/3<` is scanned as a number and a number stops at any character that
  is not a digit. The word rule never runs.
- **A word obeys `scanword`**, whose comment states it: "Allow word&lt;tag&gt; and
  word&lt;/tag&gt; but not word&lt; word&lt;= word&lt;&gt; etc." The character
  after the bracket decides -- a name or a slash finishes the word, while another
  bracket, an equals, a space or the end of input is a mistake.

So `a/3<` and `a/b<` are the same path shape with the bracket in the same place, and
the last segment is the whole difference. Neither is a rule about paths, and the
test asserts the pair together so that fixing one by breaking the other cannot pass.

**This one was implemented in the wrong place first, and the gate caught it.** The
rule went into the lexeme reader, where it collided with the splitting rule JEBOL
already had downstream in `classify` -- the one `WordCharactersTest` covers for
`1<`, `1.0<a>` and `1.#INF<`. The run went from 7974 tests to 7631 and four more
borrowed files stopped partway. Backed out whole, then done inside the existing
branch, where the number cases were already right and only the word and path cases
needed adding.

Left in this cluster: `--1:23` and three construction cases. Four.

**A sign against a hash form is the sign alone.** Six more, under Rebol's issue
#2319. One line of the C, in the plus and minus case: `if (*cp == '#') {
scan_state->end = cp; return TOKEN_WORD; }` -- the token ends *at* the hash, so
`-#"a"` is the word `-` and the character. JEBOL read `-#` as one word and then a
string, which is not two values and not one.

It matters for what it unblocks rather than for itself: `charset [#"a"-#"z"]` is how
a range is written without spaces, and it has to mean what the spaced form means. A
range is three values and reading `-#"z"` as one word broke the middle of it.

**And four percent-word cases were the error's argument, not its id.** `'%/`,
`:%/`, `'%%/` and `:%%/` already failed with `invalid`; what was missing was
`e/arg1 = "path"`. A run of percent signs is a word -- the C gives it an arm in
three token cases, each commented "special words like :%, :%%, :%%% etc..." -- and
the arm wants a delimiter after the run. A slash *is* a delimiter, so `'%/` forms a
perfectly good lit-word and the block scanner then makes a path out of it, which has
nothing after the slash to be a second segment.

So the kind reported is `path` and not `word`, because that is how far the reader
got. One line: the missing-segment failure now carries the token kind. It covers
`a/` and `a//b` too, which were failing with no argument for the same reason.

**A syntax error's ARG2 was holding the wrong thing.** `Scan_Error` fills three
fields from three places and a script reads each for something different:

```
Set_String(&error->nearest, "(line N) " + the whole line);
Set_String(&error->arg1, the token's name);
Set_String(&error->arg2, Copy_Bytes(arg, size));   // the token's own text
```

ARG2 was being given the line, so the same text sat in two fields and the one scripts
compare had the wrong thing in it. Three of Rebol's four money assertions land on
fixing that: a money literal run into an operator with no space -- `$1*$2`, `$1+$2`,
`$1-$2` -- each of which is one token and none of which is a number.

The mechanism is general rather than about money, so it is worth more than three
assertions: every failure that names a token can now say what it was reading.

**The fourth money case wants `arg2 = "$1/"`, and that is a different mechanism.**
A slash is a delimiter, so in the C `$1/$2` is not one money token at all -- it is a
path being built, and `$1/` is how far the path token had got. JEBOL reads the whole
thing as one lexeme and fails it as money. Same id, same failure, different text, and
closing it means splitting the token where the C splits it.

**And a sigil cannot go in front of an at-sign.** Two more, which Rebol's own test
files under two different groups -- `'@foo` under Ref and `:@foo` under Get-word --
and which are one line of C, the first thing `LEX_CLASS_SPECIAL` checks:
`if (*cp == '\'' || *cp == ':') return -TOKEN_WORD; // no '@foo abd :@foo`. An
at-sign makes a ref or an email, and a sigil names a word, so there is nothing for it
to name. The test is on the at-sign *anywhere* in the lexeme, so `'a@b` goes with it.

The two exceptions in that condition are worth knowing, because they are why it is a
flag test and not a scan: a tag may hold an at-sign, and so may a file whose percent
escape decodes to one -- "for case like: %61@b which is actually: a@b", says the C.

Still failing beside them: `:2nd`. The C reads a colon followed by a digit as a
*time* -- `case LEX_SPECIAL_COLON: if (IS_LEX_NUMBER(cp[1])) return TOKEN_TIME;` --
so `:2nd` fails as a malformed time rather than as a bad get-word. Routing it there
also decides what `:12` means, which nothing here tests, so it wants its own read
rather than a guess.

**TRANSCODE/ONE now reads one value and stops.** Four assertions across two groups
that looked unrelated -- the tag cases `<]>` and `<)>`, and the /part cases
`transcode/part/one "123]" 4`. All four were failing on an error from text /ONE was
never asked about.

**The obvious fix is wrong, and it cost a gate run to learn.** /NEXT already answers
a first value, so reusing its walk looks free. That walk tries successively longer
prefixes and keeps the longest that parses as one value -- a heuristic, not "the
first token". `transcode/one {'%/}` must fail, and the walk finds `'%` two characters
in and answers it happily. Twelve tests went red, three of them written an hour
earlier in the same file.

**So the reader answers it directly.** `Transcoder.firstValue` stops the walk when
one whole top-level value has been read, which is what `Scan_Token` gives the C for
free and what this had no way to be asked. Two lines in the walk and one entry point,
because the walk already recorded where each top-level value began and ended -- the
capability was half there for `topLevelSpans`.

"One value" has to mean after a block *closes*, not after the first thing inside it,
and the two completion points in the walk are where that is decided.

**Two of the three clusters this was blocking remain, and they want the same
structure one step further on** -- the extent of a token rather than of a value:

- **A malformed integer.** Done, on the third attempt, and it needed three changes
  none of which works alone. Written up below.
- **The fourth money case** (1) wants `arg2 = "$1/"`, which is a path token's extent
  rather than a money one.

Seven assertions, and both are about where a *token* ends rather than where a value
does.

**/NEXT uses the same reader now, and the prefix walk is gone.** It was the guess
/ONE was about to inherit: transcode the source once per character, keep the longest
prefix that reads as a single value, then step back over trailing whitespace by hand.
Quadratic in the value's length, and wrong on the same inputs -- it just never met
one, because /NEXT is asked about well-formed sources.

Answers identical on ten shapes -- a scalar, a block, a paren, a string, leading and
trailing whitespace, a binary source, an empty source, a trailing bracket -- and the
suite unmoved at 295. Two calls where there was a loop, and the hand-rolled
whitespace step-back went with it: the reader already knows where the value ended.

The one thing worth keeping from the old code is its bug: it cut the remainder with
`String.substring` on a code-point count. `topLevelSpans` carries a comment about
exactly that mistake -- "which `mold-test.r3` has, and which sliced sixty-six
assertions in half" -- so the new cut goes through the code-point array.

**And one assertion in that group looks defective upstream.** Rebol writes
`bitset? b1: try load {charset [#"a"-#"z"]}`, and LOAD does not evaluate -- it
answers the two-value block `[charset [...]]` in JEBOL and in a real 3.22.1 alike,
so `bitset?` is false either way. It is not in `_known-issues_.r3`. JEBOL's own test
asserts the thing it was reaching for instead: that the tight and spaced charsets
are equal, and that the range really holds the characters between its ends.

**One separate gap fell out of it.** A file whose name needs its quotes back does
not get them: `%a b` molds as `%a b` and so does not read back as one value.
`Mold_File` adds them when the name holds anything the unquoted form refuses.
That belongs with the molding cluster below rather than with the scanner.
| `series-test.r3` | 62 | FIND and SELECT (11), index past the tail (8), TAKE (6), INSERT (6) |
| `parse-test.r3` | 56 | other parse issues (14), COLLECT/KEEP (10), REMOVE (10), CHANGE (7) |
| `evaluation-test.r3` | 52 | delta-profile (13), CATCH (9), DO/NEXT (7), COMPOSE on a map (8) |
| `mold-test.r3` | 29 | molding a url (8), a string (7), MOLD/ALL (7) |
| `load-test.r3` | 28 | LOAD/header (8), SAVE (7), length-specified loads (5) |
| `object-test.r3` | 21 | SET on two objects, EXTEND, PROTECT/WORDS |
| `func-test.r3` | 13 | |
| `protect-test.r3` | 12 | eight unrelated sub-features; leave this one longest |

**`delta-profile` is instrumentation, not language.** Its thirteen need the
evaluation counter to cancel exactly between the calibration run and the
measured one, and nine of its fields name Rebol's own series pool and are left
at zero here -- which reads the same as a real count of zero.

**One lexer rule is deliberately not fixed.** A lexeme opening with a digit and
reaching the end of the classifier is a malformed number in Rebol -- its
scanner never reaches a word from `LEX_NUMBER`, so `1d` is an invalid integer
rather than a name. Refusing the whole lexeme cost twenty assertions in
`Special cases with < char` to gain four, because Rebol also *splits* at the
first character that cannot continue the number: `1<` is the number 1 followed
by the word `<`. The fix belongs in the tokenizer, where the split can happen.
The comment in `Transcoder.classifyScalarOrWord` says so.

## 5d. Two live defects, each with a reproducer

A defect you can trigger in one expression is cheap to chase.

**A map matches a string key case-sensitively.** `Find_Entry` is called with
`cased` false for FIND, SELECT and a path read -- only `find/case` and
`put/case` pass true. JEBOL holds keys in a hash keyed by value equality, so
the fix is a lookup that knows about the flag rather than a line in a native.
Reproducer: `m: make map! [] m/("k"): 1 select m "K"` answers 1 in Rebol and
none here.

**`load-json` cannot read a JSON array or an object.** `load-json "5"`,
`"true"` and `"[]"` work; `load-json "[1]"` raises the codec's own "Invalid
JSON string". `to-json` works in both directions, so the two halves disagree
and the encoder is the one to trust. Points at PARSE rather than at the codec.
Reproducer: `load-json "[1]"`.

## 5e. Two Rebol files still stop partway

Sixty-seven files from Rebol and sixty-five run to their end.
`BorrowedFilesLoadWholeTest` names the two that do not, each waiting on a named
feature rather than on a defect:

| Wants | File |
| --- | --- |
| `binary`, the bincode dialect (`u-bincode.c`) | `prot-tls.reb` |
| the view dialect's `font` object | `view-funcs.reb` |

**One file is deliberately not loaded.** `mezz-osx-dialogs.reb` opens by
unsetting REQUEST-DIR, REQUEST-FILE and REQUEST-COLOR and defining its own,
each of which shells out to `osascript`. JEBOL serves those three through the
WINDOWS port: that works on every platform and asks the host's grant first,
where the vendored file works on one and asks nobody.

**Two ordering rules, both learned the hard way.** mezz-tail.reb goes last of
what Rebol boots and before the on-demand imports, because it sets the aliases
they read (`codecs: :system/codecs`) and because PROTECT-SYSTEM ends by
unprotecting the one part REGISTER-CODEC writes to. And codec-der.reb goes
before codec-crt.reb, which opens with `der-codec: system/codecs/DER`.

## 5f. The forks still in the prelude

Forty-six functions JEBOL implements that Rebol writes in REBOL. Each blocks
the R3 file that defines it from being loaded over the top. Goal 2 is the
opposite direction; these are the ones to delete rather than to move.

**Check each one by identity, not by datatype.** The obvious probe -- is
`type? :max` still `native!`? -- says nothing, because Rebol defines four of these
as aliases *to* natives: `max: :maximum`, `min: :minimum`, `abs: :absolute`,
`context: :object` in `base-constants.reb` and `mezz-func.reb`. So a native there
is the right answer and the fork is gone. `same? :max :maximum` is the question
that answers. And `empty?` is neither: `mezz-series.reb` redefines it as
`empty?: make :tail? [...]`, a derived function with a widened spec, which loads
after the alias and wins.

Which makes the first step a test rather than a judgement: assert of each of the
46 that the word now resolves to what the R3 file gives it. That turns this from
46 decisions into a list of the ones that are really still forks, and it keeps
paying afterwards -- a fork that creeps back in gets caught.

**32 in `prelude.reb`**, of 47 there. The prelude's own header already admits
they are "something Rebol's own library silently replaces the moment that file
is borrowed".

| R3 file | functions in the prelude that belong to it |
| --- | --- |
| `base-defs.reb` | `body-of` `spec-of` `title-of` `types-of` `values-of` `words-of` |
| `mezz-func.reb` | `clos` `closure` `enum` `has` `map` |
| `base-files.reb` | `dirize` `script?` `split-path` `suffix?` |
| `mezz-control.reb` | `all-of` `any-of` `wrap` |
| `base-funcs.reb` | `cause-error` `default` `does` |
| `mezz-series.reb` | `collect` `empty?` `rejoin` |
| `mezz-files.reb` | `clean-path` `undirize` |
| `mezz-tail.reb` | `funco` `keys-of` |
| `base-constants.reb` | `max` `min` |
| `base-series.reb` | `join` |
| `mezz-types.reb` | `to-word` |

**14 in Java** that belong to a REBOL file:

| R3 file | functions in Java that belong to it |
| --- | --- |
| `base-files.reb` | `exists?` `load` `make-dir` |
| `mezz-files.reb` | `ask` `input` |
| `base-funcs.reb` | `function` `use` |
| `base-defs.reb` | `quote` `true?` |
| `base-constants.reb` | `abs` |
| `mezz-func.reb` | `context` |
| `mezz-series.reb` | `split` |
| `view-funcs.reb` | `view` |

**11 duplicated generators.** `base-defs.reb` generates the typeset predicates
from `system/catalog/datatypes`. JEBOL also writes `datatype?`, `error?`,
`get-word?`, `lit-word?`, `map?`, `none?`, `object?`, `ref?`, `refinement?`,
`set-word?` and `unset?` in Java. One of the two is redundant and it is not the
generator.

`abs` is the smallest and a good first move: `base-constants.reb` is already
loaded, so deleting the Java definition should need nothing.

## 5f2. Done: the borrowed-library comparison is gone

It ran all 1034 corpus entries twice, on `Interpreter.create()` and on
`Interpreter.borrowingFromRebol()`, and reported every entry whose answer changed.
A real measurement once: each difference named a native that was present and
*wrong*, which no inventory finds. REDUCE refusing a non-block and the eight
missing typeset predicates were both found that way.

**The two interpreters had become one.** The constructor calls
`loadRebolsOwnLibrary()` unconditionally and the `borrowFromRebol` field it stored
was never read, so the test compared an interpreter against itself and reported
`0 of 1034` -- taking two minutes to do it, which made it the longest single thing
in the build and the floor no amount of forking could get below.

Deleted, with the three things it orphaned: the factory method, the field and the
javadoc paragraph promising a switch that had already come off. The one surviving
caller wanted a plain interpreter anyway.

**It should come back against the C binary rather than against JEBOL.** Comparing
JEBOL with its library against JEBOL without it only ever measured JEBOL's own
smaller self; comparing either against a real 3.22.1 measures the thing that
matters. Running Rebol's own suite is the guard until then.

## 5g. Loose ends

- **The console gets a shell written in Java, hexagonally.** The terminal side
  is an adapter behind a port, the same shape as `FilePort`/`FileSystemPort`.
  TTY? is already done this way and is the pattern to copy: the domain asks
  `console().isATerminal()` and never sees a `java.io.Console`.
- **VID may come free with the image datatype.** VID is a dialect, and a
  dialect is REBOL rather than C -- so once `image!` exists and the `n-image.c`
  functions are ported, `view-funcs.reb` and the draw dialect may load and work
  without anything further. Worth trying before designing anything.
- **Host object mutability** is decided in `docs/decisions.md` and not yet in
  the embedding documentation or the API javadoc.
- **`draw` dialect to SVG.** One renderer. Milestone 5's open fork in
  `docs/milestones.md` covers the thinking.

## Working notes

- **Multi-line REBOL collapsed onto one line feeds the first call the next
  one's arguments.** Write a probe to a file and run the file.
- **`-Werror` with `dangling-doc-comments`** means inserting code between a
  javadoc and its declaration fails the build. It has happened six times.
- **zsh `no matches found` kills a `until ls *.xml` wait loop.** Use
  `find ... | wc -l`.

# Goal 6. The boot is 68ms, and everything is boot-bound

**Last on purpose.** Deferred until the goals above land, and it will matter
in production as much as in the test run -- an embedding that builds an
interpreter per request pays this on every one.

Measured rather than guessed, because the class-level times in Gradle's XML are
wall clock in a parallel run and mean nothing: `ReaderNeverThrowsTest` reported
432 seconds and takes 0.1 of one.

**`Interpreter.create()` costs about 68 milliseconds**, and `run("1 + 1")` on a
warm one is too fast to measure. Nearly every test asks for a fresh interpreter,
so the wall clock was close to 68ms times the number of tests: 7931 tests, one
fork, nine and a half minutes with the machine idle on eleven of twelve cores.

Running the classes across six forks took that to three minutes with an identical
result -- 7931 tests, 343 failing, 2 skipped -- and each fork is a separate
process, so nothing about the interpreter became concurrent. More forks buy
nothing: Gradle hands out whole classes, so the floor is the slowest single class.

**That floor is `BorrowedLibraryTest` at two minutes, and it is one test.** It
builds *two* interpreters for each of about a thousand corpus entries -- one plain
and one with Rebol's library over it -- which is the 68ms twice, a thousand times.
Reusing them is not the fix: each entry has to start clean or one entry's words
leak into the next, which would quietly change what the test measures.

**So the work is the 68ms itself.** What it buys is loading and evaluating the
whole imported library, which produces the same result every time and is thrown
away after every test. Two things would have to be true to cache it: the library
context would have to be copied cheaply rather than rebuilt, and the copy would
have to be deep enough that a test mutating a library value cannot be seen by the
next one. REBOL series are shared and mutable, so that second part is the whole
difficulty and is why this is a goal rather than a tidy-up.

Worth roughly seven minutes off every full run, and rather more off the inner
loop, where a single test class is 68ms times its assertion count.
