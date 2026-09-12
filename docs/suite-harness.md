# Running Rebol's own suite

`RebolSuiteTest` runs Rebol's own `.r3` files against JEBOL, one JUnit case per
`--assert`. This note carries what the harness code cannot: why it is shaped the
way it is, and the handful of ways of building it that looked right and silently
lost hundreds of assertions.

`CLAUDE.md` carries the rule about what the suite is for - it is scaffolding, it
will be deleted when it goes green, and every behaviour it drives out gets a
JEBOL test of its own that outlives it.

---

## Why the suite is here at all

The corpus in `corpus/` says what we believe REBOL does, and was written from
documentation. The suite says what the people who maintain REBOL believe it does,
and was written from the implementation. **It is the stronger of the two and it is
not ours**, which is the point: a case here is one nobody on this side thought to
try.

One JUnit case per `--assert`, because that is what the suite is already shaped
like, and because a case that checks one thing says what broke without needing to
be read.

Most of it does not pass yet. `known-gaps.txt` lists what fails today, **so a new
failure is a regression and shows up red** while the backlog stays visible and
countable rather than being skipped into silence.

---

## The two lists, and why both ratchet

**`known-gaps.txt` is work to do.** A line comes off when the assertion it names
starts passing.

**`fails-on-rebol-too.txt` is not.** Those assertions are wrong about the Rebol
they came from: JEBOL answers what that Rebol answers. Leaving them in the gap
list would say there is work here and there is not, and deleting them would lose
the finding, so they sit in a file of their own with the `r3-head` output that
settled each one written beside it.

**Every line of the gap list must name an assertion that exists.** Without that
check the list rots in the one direction nobody looks: a line comes off when its
assertion starts passing, an assertion that no longer exists never starts
passing, so a line whose wording or position has shifted stays on the list for
good and is counted as outstanding work for ever. **It had happened to 182 of
1,016 lines by the time anybody checked** - 95 of them in `image-test.r3` alone,
which had 101 lines against 14 assertions. The gap list read as eighteen per cent
worse than the port was, and the number was quoted in the readme.

**The findings list needs the same ratchet, in the other direction.** The gap list
only shrinks because the build fails when a listed assertion starts passing;
nothing asked the same of the findings list, so an entry moved there left the
published backlog one smaller and the gate green. That was demonstrated rather
than argued: moving `bitset-test.r3 #139`, which a real Rebol passes and JEBOL
fails, out of one file and into the other took the count down by one and broke
nothing. A finding on that list says a real Rebol does not pass this either; **if
JEBOL starts passing it, the finding was wrong or the world moved**, and either way
somebody has to go and look.

---

## `--red--` is not work to do, and treating it as work cost eight lines

Rebol's own runner counts a failing `--red--` assertion apart from its failures,
as a difference from Red rather than a defect.

Grading one here anyway is **not merely stricter**, because the strict reading asks
for behaviour a real Rebol has not got: eight lines of the gap list wanted
`integer? power 2 16`, which is `65536.0` in Rebol and therefore false there too.
The ratchet would have gone green for whoever made JEBOL disagree with a real
Rebol. **A stricter rule is only safer when the thing it is strict about is true.**

The mark is read from the suite's own text rather than from a list, deliberately:
a list would have to be kept in step with the vendored files by hand, and the mark
cannot drift from the assertion it is written beside.

`--assert-er` is ASSERT with an error whose id is `feature-na` let through. It is
not counted at all - one assertion in ten thousand, spelled differently enough
that the slicer never sees it. Nineteen `--assertf~=` assertions are missed the
same way. Rebol's own runner counts all three spellings.

---

## A test file is a script, and six ways of forgetting that

**One interpreter per file, steps in order, setup included.** Assertions lean on
words set up above them, sometimes many lines above. Running each assertion in a
fresh interpreter loses that, and it showed up as **roughly four hundred failures
on words called a, b, i and obj** - the shape of a harness bug rather than of a
language one. The outcome is computed once and cached, because the parameterized
test asks about assertions one at a time and re-running a file per assertion would
be quadratic.

**The reason a failure happened is recorded when it happens**, not worked out
later. Working it out later means running the assertion again, and running it again
on its own loses the setup the file did above it. That mistake produced a work list
whose top four entries were words called a, s, b and v - none of which was a real
gap.

**`--assert` takes one expression and the suite often puts more on the line.**
`--assert all [...] a: none` asserts the ALL and then resets `a`. Slicing to the
next dialect word takes the reset with it, so the assertion runs through
`Interpreter#runNext` and whatever follows runs after it. Two attempts to do that
beside the interpreter failed first: REDUCE fails wholesale when a later expression
does, and a hand-built evaluator did not carry the same bounds or fresh-word
handling. The seam belonged in `Interpreter`.

**An assertion inside a FOREACH or an IF cannot be sliced out and run on its own**
- the loop variable it reads only exists while the loop is running. So it is not
sliced: the enclosing expression runs as it stands and each nested `--assert`
reports as it goes, which is how Rebol's own harness works and the only way those
assertions run at all. **The slicer takes top-level values, so an assertion inside
a block was never anybody's step**: not sliced, not run, not counted. Thirty-seven
of Rebol's files put assertions there, and `crypt-port-camelia-test.r3` puts all
four of its inside two nested loops, so it reported zero of four while a real Rebol
ran them two thousand times.

**Every dialect word has to be defined even where doing nothing is its whole job.**
The slicer already read the group and test names out of the file. Leaving them
undefined meant a wrapper block holding any of them died on the first one, and
every assertion after it in that block was never reached: **371 of them**, which
read as failures of the port and were failures of the harness.

**Nested reports are folded per assertion at the end of the file, not step by
step.** An assertion written inside a function runs when the function is called,
which is a later step and often a much later one; reading per step throws those
reports away as belonging to nobody. An assertion in a loop body runs once per
turn and there is one of it in the file - it holds when every run of it held, so
the letters fold onto the assertions in order and extra runs fold onto the last
one. That is the same reading Rebol's own count of thirteen thousand executions
against ten thousand written implies.

**Each nested `--assert` is told which assertion it is.** Reporting only whether it
held means matching reports to assertions by counting, and counting is wrong twice
over: a function defined in one step and called in another reports where it ran
rather than where it was written, and a loop reports three assertions a hundred
times.

**The number goes into the text, not into a molded copy of the values.** Molding
would put the port's own MOLD between the suite and what the suite actually runs -
the measure would depend on a part of the thing being measured, and a mold that
broke would quietly change the tests rather than fail. The reader is unavoidable
(nothing can slice the file without reading it); MOLD is not, so it stays out.
Scanning text for a word is a guess, so the result is checked against the source it
came from: read both, walk them together, and every value must be the same except
the numbered ones. A step that fails that check keeps the source it was written
with.

---

## Four places where the obvious implementation is wrong

**A run of setup must be cut into the expressions it was written as.** Left whole,
one raise takes the rest of the run with it - and a run is everything up to the
next *top-level* dialect word. `codecs-test.r3` is a sequence of
`if find codecs 'wav [...]`, `if find codecs 'der [...]`, `if find codecs 'crt [...]`
whose dialect words are all nested inside those blocks, so the whole tail of the
file was one step: the DER codec raising took the WAV, CRT and SWF groups with it,
and **187 assertions were recorded as failures of the port when they had never been
asked.**

The cut is where a **word** begins a line, because that is how these files are
written and because nothing here knows REBOL's arity well enough to find an
expression boundary properly. **Only a word may open one**: cutting at any value
that begins a line splits `switch-fun: func [/local i][` from its body block
whenever the bracket starts a line, and both halves read perfectly well on their
own - one is a function of one argument, the other is a block. **Reading is not the
same as meaning the same thing**, and 32 assertions that had been passing said so.
It is still a guess, so every piece has to read on its own and a run with a piece
that does not is left exactly as it was.

**Positions arrive counted in code points, as every offset the reader hands out
does.** Indexing the source in sixteen-bit units instead put every position after
the file's first emoji in the middle of some other line, so the cut never fired
**and left no trace of not having fired.**

**Finding how much of a file the reader can take in walks forward rather than
bisecting.** Bisection needs "does this prefix read" to stay false once it turns
false, and it does not: a prefix cut in the middle of a multi-line block fails for
the missing bracket rather than for anything wrong, and a longer prefix that closes
the block reads again. Bisecting that predicate stops at the first open bracket it
lands on - it cost `error-test.r3` thirteen assertions the reader could already
have had, and it named line 14 of `copy-test.r3` as the stop when the refusal is on
line 30.

**The source text of a run is taken as written, never molded back.** Molding is
lossy in REBOL and equally lossy in R3: molding `1.7976931348623157e308` gives
fifteen digits, and reading that back gives `1.#INF`. Sixteen assertions were being
run in a form the file never contained, and a measuring tool built on this then
reported that R3 fails its own tests.

**A molded string of more than fifty characters comes back in braces, not quotes.**
The letters the nested assertions wrote are read back out of the interpreter
molded, so the delimiters have to come off - and which delimiters depends on the
length. Accepting only quotes and answering an empty string otherwise made a block
of more than fifty assertions report every one of them as never reached, however
many had just passed: `struct-test.r3` lost all 174 of its that way while 172 held,
and it was invisible because an empty answer reads exactly like a block that ran
nothing. **An answer that is not a molded string is a fault in the harness rather
than a verdict about the port**, and it says so.

---

## Two more things the harness has to get right

**An assertion id must be unique within its file.** The ordinal counts assertions
across the whole file rather than within a test. Numbering within a test looked
tidier and produced duplicate ids wherever two tests shared a name, which made a
gap list unusable: one assertion under a shared id passed while another failed.

**The `Rebol [...]` header is data, not code** - evaluating it would call whatever
REBOL is bound to.

**One interpreter is booted before anything reads.** The reader does not build a
function or a construction on its own: the evaluator hands it a builder at boot,
because MAKE and spec parsing belong to the evaluator and the reader must not reach
upward for them. A reader asked a question before any interpreter has existed
answers for a reader that has not finished being built, and refuses constructs it
can perfectly well read. That made every count too low, and made a fix to
construction syntax look like no fix at all.

**The suite interpreter gets the host services Rebol's own tests assume.** Those
tests were written for a full host, so a suite that grants nothing measures the
grant and not the port. Files are confined to a directory made for the run, so a
test that writes one cannot reach anything the build did not make.

---

## The action parity measure

`ActionParityTest` calls every action Rebol's C implements for a datatype, on that
datatype. The declared surface says APPEND takes a `series!`, a `port!`, a `map!`,
a `gob!`, an `object!` and a `bitset!` - it does **not** say which of those the C
has an arm for, nor which JEBOL has an arm for. **Every gap found by hand in the
last round was of that shape** - APPEND on a map, CHANGE on a binary, FIND on an
object, the walk over a map - and each was invisible in the declaration.

So it reads `r3/c-surface.txt`, which `scripts/c-surface.py` builds from Rebol's
own source: the datatype table in `types.reb` says which typeclass serves each
datatype, and the `REBTYPE` blocks say which actions each typeclass implements.
The product of the two is the list of calls that must do something here.

**"Does something" means not `cannot-use` and not `expect-arg.`** The first is
what JEBOL answers for an arm it has not got; the second is what it answers when
the declared spec refuses the datatype before the arm is reached. Any other
outcome counts - including a different error, because an arm that exists and
refuses these particular arguments is a question about the arguments and not
about the arm.

**The declared spec has to narrow the product, or the measure asks for things a
real R3 refuses.** Every scalar with a position has a POKE arm - `REBTYPE(Pair)`
has `case A_POKE` - and POKE declares `series! port! map! gob! bitset!`, so
`poke 1x2 1 5` is an error there as it is here. Those arms are reached by writing
through a path instead, which is a different question with its own tests.

**The product says which `case` labels exist, not which of them do anything.**
Where the first line inside a case is a refusal, the product over-counts, and a
faithful port has to look like a gap or disagree with Rebol. `REBTYPE(Block)`'s
RANDOM is the whole of that list: `if (!IS_BLOCK(value)) Trap_Action(VAL_TYPE(value), action);`
is its second line, so every block-like datatype that is not a plain block - the
four paths, a hash and a paren - reaches the arm and is sent away with
`cannot-use`. Rebol's own `series-test.r3` pins it:
`all [error? e: try [random 'a/b/c] e/id = 'cannot-use]`. **Nothing goes on that
allowance without the line of C that refuses and the assertion that wants it.**

**Datatypes JEBOL has not got are skipped rather than counted** - those are a
datatype backlog rather than a parity gap, and mixing the two buries the second in
the first. **MAKE and TO are left out**: both take a datatype rather than a value
of one, so their matrix row says something different from the rest.

The missing-arm count is **a ratchet, not a target**. Lower it when an arm lands
and never raise it: going up means an arm that used to answer does not any more.

---

## The suite host, and why there is only one of it

`SuiteHost` exists because there were three definitions of the suite interpreter
and they disagreed. `RebolSuiteTest` is the gate; `SuiteStops` says where a file
stops and `SweepRunner` diffs a file against a real Rebol, and both were written
by copying the gate's setup. Each granted every host service and rooted a
filesystem, and **neither installed the environment or the process runner - and
granting a service is not providing one.**

So both tools reported stops the gate never sees. Every one reading "given no
environment to read" or "given no way to start a program" was the tool's own
doing, and **four entries in `goals.md` were written from them**: a goal to make
the environment work that nothing was waiting on, a dependency on it that did not
exist, three stops in `port-test.r3` that do not happen, and a shared CALL blocker
that was neither shared nor a blocker. A capability added in one place now reaches
all three, which is the only arrangement in which a measuring tool cannot drift
from the thing it measures.

**The environment and the processes are the real ones.** A suite file asks for
`PWD` and shells out to the boot image, and answering "not granted" to either is a
wrong answer rather than a safe one.

**A sandbox whose home lies outside the sandbox is an incoherent host, not a
strict one.** The filesystem is confined to a temporary directory and
`system/options/home` is read from the machine, so a suite file asking where home
is got an answer it was then refused permission to write. Rebol's own SAFE tests
do exactly that - `set-user` keeps a user's storage file at
`system/options/home` - and every assertion after it was lost to a path the run
was never allowed to reach. `system/options/data` is the same incoherence one
field along, and **the word `~` is bound while the library loads**, long before
any of it, so moving the field alone leaves `cd ~` pointing at the old place.
Both are set, which is what `mezz-tail.reb` does in one line.

**The modules a suite file imports are put on disk, so no run fetches one.**
IMPORT looks in three places - what is loaded, a file in the modules directory,
and the address in `system/modules`, which it downloads and saves. The third
works, and a gate that used it would reach `src.rebol.tech` once per file that
imports anything. That is one host too many: `thru-cache-test.r3` already names
`raw.githubusercontent.com` and `httpbin.org` in its own assertions and nothing
can take those out short of rewriting a vendored file.

**The suite's own file names stand in the run directory.** Rebol runs its tests
from `src/tests/`, where `run-tests.r3` sits beside a dozen other scripts, and a
suite file can see that: `port-test.r3` asserts `port? p: try [open %*.r3]`, which
opens only when the pattern matches something. Without them that assertion passed
for the wrong reason - every wildcard opened, matching or not. **The names alone,
not the tree**: the harness reads each suite file from the repository, and these
are copies standing in a directory so a pattern has something to match.
`run-tests.r3` has to be fetched from elsewhere, because `port-test.r3` asserts
`read %run-tests.?3` and the runner itself cannot live beside the suite files -
this harness runs every `.r3` it finds there and would try to run it as a test.

**Every vendored data file is copied, and the whole tree of it.** Naming them
individually got six of seventy-two, and every test that read one of the other
sixty-six answered `cannot-open` and took the rest of its block with it - **191
assertions never run, read as failures of the port**. One of Rebol's data
directories holds a directory of its own - fourteen icons the ICO codec builds an
icon file out of and the ZIP codec archives whole - so a copy that stops at the
first level quietly depends on nobody ever nesting anything.

---

## The porting backlog, and a number that was wrong three times

`PortingBacklogTest` says what is not there at all - the functions a real R3 has
and JEBOL has not. **That is a different question from whether a port is right,
and no failing assertion asks it**: a missing function usually shows up as
nothing rather than as a failure. It exists because the failure report does, and
the work went where the queue pointed.

The count is asserted rather than only reported, so leaving the list alone breaks
the build. **It is a ratchet, not a target** - and if it fails by going *up*, a
function that used to be reachable is not any more, which has happened silently:
a borrowed Rebol file can define a name over one of ours.

**It has been wrong three times and each was the same mistake: the number was
believed and the question behind it was not.**

- It said **134 of 580** while it read a dump of a running binary, which listed
  every top-level word of every loaded file including the modules whose words no
  script can reach - forty in `prot-tls.reb`, forty more in `codec-swf.reb`.
  Reading Rebol's source instead took it to **30 of 353**.
- Then it said **24, and the real number was three**. Twenty-one of the
  twenty-four were in `system/contexts/sys`, where Rebol puts them too, and the
  measure asked the library alone. One was `limit-usage`, which Rebol deletes on
  purpose. Two were `completion!` and `line-editor!`, objects rather than
  functions, collected as though they were.
- And the input was short: `c-surface.py` read only the boot files, so **the 54
  natives the C declares in its own comments were invisible** - `binary` among
  them, the word `prot-tls.reb` stopped on for months while the parity report
  said nothing was missing.

**A dump says what one build had loaded on one machine and cannot be checked
against anything; the source says what the language is and explains itself.**
Where the two disagreed the source was right every time.

**Three kinds of line in `c-surface.txt` name a function**: ACTION and NATIVE for
the third of the library Rebol writes in C, and LIBRARY for the rest, written in
REBOL in `src/mezz/*.reb`. The audit has to tell three things apart - a function
JEBOL has in Java, one it has in REBOL, and one it has not got - so both the
native registry and the booted interpreter are asked. Without both, a function
Rebol writes in C and JEBOL writes in its prelude reads as missing.

**Names Rebol takes back out are not gaps.** `mezz-secure.reb:334` is
`unset in lib 'limit-usage`, which JEBOL runs faithfully: the word is collected
because the file that defines it defines it, and unset because the file that
removes it removes it. Counting the gap between them as work would mean porting a
function in order to delete it again.

---

## The vendored suite is the whole suite, minus a list that says why

Twenty-two of Rebol's seventy-six unit files were vendored and the other
fifty-four were not. **Nothing was wrong with any measure**: the suite passed, the
count was true, and the count was of the files that happened to be there. **A
number that only describes what it was given cannot report what it was not
given**, so the absence has to be checked separately or not at all.

Rebol's own `run-tests.r3` names the files it runs, and that list is the
authority. Every name on it is either vendored here or written in
`not-vendored.txt` with the reason; a file that appears upstream and lands in
neither place fails the build. That check only runs when the Rebol checkout is
present - it is a gitignored symlink and not everybody has one - and it is
**skipped rather than silently passing**, so the reason shows in the run.

**A vendored file is a copy and nothing else.** Counting whole files cannot report
a missing line: **thirty-three assertions had been cut out of nine vendored
files** - fifty-five lines gone and none added - and nothing in the build could
see it, because the coverage test counts the vendored text against itself and
reported every assertion present, which was true of the text it was given.

They were cut for reasons written down at the time and kept in a directory beside
the suite, **and the reasons went stale without anything to notice**: three of the
thirty-three were live failures of this port, excluded on the grounds that they
needed files that had since been vendored; twenty-four had been excluded as
needing functions the Rebol now being measured against has. So a difference of any
kind now fails, and **an assertion that should not be graded is named in a list
where the ratchet can reach it, rather than removed from the file where nothing
can.**

**The bundled modules are held to the same rule**, for the same reason: IMPORT
would otherwise fetch each from `src.rebol.tech` and evaluate what came back.

**A weaker check catches the same thirty-three without needing the checkout.**
Cutting an assertion out leaves its `--test--` header standing, so `pair-test.r3`
carried nine test names and no assertions under any of them. That check reads the
text rather than the slicer, on purpose: **the slicer gives an assertion the last
*top-level* test name**, so an assertion inside an `if` block is attributed to a
name written above the block, and asking the slicer which tests own assertions
calls forty innocent tests empty.

**Tests Rebol itself leaves empty are named rather than pattern-matched.** Each
has its assertions commented out upstream with a note saying why - "Not supported
anymore!", "need to decide, which result is correct" - so they are Rebol's own
unfinished business, and a tenth one appearing is a thing somebody has to look at.

## Where each file stops

`org.jebol.suite.SuiteStops` is the counterpart to `scripts/sweep.py`. The sweep
says which assertions answer wrongly; this says which raise, and a raise is worth
more than a wrong answer because everything after it in the file never runs at
all. A file with one stop near the top can owe a hundred entries and need one fix.

Run it, and give it the smallest gap count worth reporting:

```
./gradlew compileTestJava
java -cp build/classes/java/main:build/classes/java/test:build/resources/main:build/resources/test \
     org.jebol.suite.SuiteStops 7
```

It is a `main` rather than a test on purpose. It runs every suite file twice over
and takes a minute, and it answers a question about the state of the port rather
than asserting anything, so it has no business in the gate.

It builds its interpreter through `SuiteHost`, the same one the gate uses. It used
to build its own, granting every service and installing only a filesystem -
granting a service is not providing one, so it reported stops on `get-env`,
`list-env` and `call/shell` that the gate never sees, and four pieces of work in
`goals.md` were written from them.

## The borrowed-file ratchet

`BorrowedFilesLoadWholeTest` holds a map of vendored file to the word it stopped
on. A borrowed file that raises halfway defines nothing below the line it stopped
on, and for a long time said so nowhere - which hid `base-defs.reb` quietly
generating its six reflector functions into a scope thrown away immediately
afterwards, costing five suite assertions and looking for a while like the file
being a bad borrow.

An entry there is a real gap rather than tolerated noise: each names something the
borrowed code expects and JEBOL has not got. A new one is a regression and a
removed one is progress. The list was twelve long and is now empty. `mezz-shell.reb`
stopped on LIST-DIR - the first statement of the file - and lost all twelve of its
definitions; LIST-DIR arrived when `mezz-files.reb` was imported. APPEND on a map
took two out, `make map! 111` two more, NOW's refinements and
`system/standard/file-info` one each, a word selector on a block took `prot-mysql`
the rest of the way, loading `mezz-tail.reb` before the on-demand imports took two,
and filing a loaded module in `system/modules` took the last. `mezz-osx-dialogs.reb`
came off a different way: it is no longer loaded at all, because what it defines is
what the WINDOWS port serves. See ORDER.txt.

**Read an entry as the first thing in the way, never as the whole of what is
missing.** `prot-tls.reb` made the point five times: it stopped on `binary` until
the binary dialect was ported, then on `system/catalog/ciphers` until the
catalogues were filled, then inside `decode-list` until a single-word read answered
a value rather than a block, then on a get-path in a dialect block, and under all of
it REPEND answered NONE because a declined refinement's argument was not being
taken. Not one of those five words was the whole of what that file wanted, and the
last was not a word at all. `view-funcs.reb` stopped on `font` for months, which
read as "waiting on the view dialect"; the truth was that seventeen of
`sysobj.reb`'s twenty-nine standard templates had never been copied into the
prelude, and a set-path cannot make a field. With those declared the file ran a
hundred lines further, to `init-top-window`, which read as "waiting on a widget
toolkit" and was not that either: what it wanted was three commands from
`boot/window.reb`, a port behind them, and an event port to exist.

The words are matched as substrings of the failure, so each is the shortest piece
that names the gap rather than the whole message. Keeping the empty map rather than
deleting the test is the point of the ratchet: a file that starts stopping again
fails there and names itself, rather than quietly defining less than it used to.
