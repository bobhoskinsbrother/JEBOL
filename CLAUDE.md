# JEBOL

## Editing files: use Edit and Write, never a script

**No `python3 - <<'PY'` heredocs to rewrite source, and no `sed -i`.** Use the
`Edit` tool for a change and `Write` for a new or fully-replaced file.

I have leaned on python heredocs to batch several edits into one call. It is
faster to type and worse in every other way: the diff is invisible until it has
already happened, an `assert old in t` that fails leaves the earlier edits of the
same script applied, the escaping of the code being edited has to survive two
languages, and nothing checks that the file was read first. `Edit` fails loudly on
a non-unique or absent match and changes nothing.

Batch by making several `Edit` calls, not by writing a program that edits.

Scripts are still right for what they are for: `scripts/c-parity.py` and
`scripts/c-surface.py` are the project's own measures and are run, not text
inline.

## Searching this codebase: use the IntelliJ MCP, not grep

**Every search of code in this repository goes through `mcp__intellij-mcp__*`.**
Reaching for `grep`, `rg`, `sed` or `awk` on a path under this project is a
mistake, and one I have made repeatedly after being told not to.

Which tool for which question:

| The question | The tool |
| --- | --- |
| Where is this class or file? | `ide_find_class`, `ide_find_file` |
| Where is this defined? | `ide_find_definition` |
| Who calls this? Who reads this field? | `ide_find_references` |
| What implements this interface? | `ide_find_implementations` |
| What does this override? | `ide_find_super_methods` |
| What calls what, transitively? | `ide_call_hierarchy` |
| What is the type tree here? | `ide_type_hierarchy` |
| Text or a pattern, when structure will not do | `ide_search_text` |
| Are there errors in this file? | `ide_diagnostics` |
| Rename a symbol everywhere | `ide_refactor_rename` |
| Delete a symbol and its uses | `ide_refactor_safe_delete` |

`ide_search_text` is the fallback when the question really is textual -- a
comment, a string literal, a REBOL word inside a `.reb` resource. It is still the
MCP and still better than grep, because it knows the project's scope and
exclusions.

**Rebol's own source is in the project too.** `rebol3-source/` is a checked-in copy
of the Oldes/Rebol3 checkout, so the IDE indexes it and the MCP searches it like
anything else. That is where every port is read from:

| Reading | Path |
| --- | --- |
| The datatype table, the 224 declarations, the error catalogue | `rebol3-source/src/boot/` |
| The C itself | `rebol3-source/src/core/` |
| The structs and the lexer's character classes | `rebol3-source/src/include/` |
| Rebol's own library, the files JEBOL borrows | `rebol3-source/src/mezz/` |
| Rebol's own test files -- a third authority | `rebol3-source/src/tests/units/` |

So `ide_search_text` with `filePattern: "*.c"` answers a question about the C, and
there is no reason left to grep it.

**IntelliJ indexes the checked-in copy.** All three file types resolve:
`Skip_Left_Arrow` finds `rebol3-source/src/core/l-scan.c`,
`integer-divide` finds `rebol3-source/src/boot/ops.reb`, and a suite group name
finds `rebol3-source/src/tests/units/lexer-test.r3`. Run `ide_sync_files` if a
search comes back empty for something that is certainly there.

One thing to expect from it: JEBOL vendors copies of the mezz and the suite under
`src/main/resources/org/jebol/mezz/` and `src/testFixtures/resources/rebol-suite/`, so a
search often returns the same line twice, once from each. The `rebol3-source/` hit
is the authority; the vendored one is what JEBOL currently runs, and where they
differ that difference is itself worth knowing.

**The one place a shell search is still correct** is build output: the test result
XML under `build/` and what the parity scripts print. That is data rather than
code, and it is not indexed.

Everything else -- `src/`, `spec/`, `corpus/`, `docs/`, `rebol3-source/` and the
build files -- is the project, and the MCP is how it gets searched.

**After editing Java, ask `ide_diagnostics` before running the gate.** It answers
in a second where `./gradlew` takes five minutes, and it catches the whole class of
mistakes -- a missing import, an unreachable branch, a dangling doc comment under
`-Werror` -- that have cost several round trips here.

## Rebol's suite is a ratchet. JEBOL's own tests are the record

Two different things run here and they have different jobs.

**`RebolSuiteTest` runs Rebol's own `.r3` files.** It is the authority on what a
real 3.22.1 answers and the measure of how far the port has got, and while JEBOL is
catching up its assertions are what to obey: if it says `e/arg1 = "path"`, that is
the answer, and a disagreement means JEBOL is wrong until the C says otherwise.

**It is scaffolding, though.** It is vendored somebody else's test code, it is one
class of 10,144 assertions (10,132 on every build, 12 in `./gradlew
networkTests`) so a failure names a group rather than a behaviour, and it
will be deleted when it goes green. Coverage that lives only there does not survive
that.

**So every behaviour fixed because of a suite assertion gets a JEBOL test too**, in
`src/functionalTest/java`, standing on its own: it builds an interpreter, asserts on JEBOL, and
reads no `.r3` file. Quote the suite in the comments -- "Rebol's own test asserts
this twice" is worth knowing -- but never depend on it.

Which means each piece of porting produces two things, and the second is the one that
lasts:

- the suite assertions go green, and the count in 5c comes down;
- a `*FromTheSourceTest` covers the same behaviour independently, including the cases
  the suite does not check.

That last clause earns its keep. The suite asserts two charsets are equal; a JEBOL
test can also check the range really holds `#"m"` and rejects `#"A"`, because two
equal bitsets could both be empty. The suite checks `img/1` on one image; a JEBOL test
walks all four hash forms. Going past the suite is how a wrong reading gets caught
that the suite would have let through.

## Porting a compressor, a codec, or anything else that must be byte-exact

Four rules, each of which exists because it was broken and cost something.

**Build the canonical reference before the port, not after.** The reference C is right there
under `rebol3-source/`, and a hundred lines of shell will compile the encoder
alone into a program that reads a file and writes its compressed form to stdout.
Then any input can be diffed against the truth in a second. On the Brotli
encoder that harness found two defects within minutes of existing, and one of
them -- a hash table cleared at every block instead of once -- was invisible in
every test text before it, because it needed more than one block of input to
show. Building it first also means the port can be bisected: shrink the input
until the smallest failing case is one line.

Instrumenting the reference is fair game and often the fastest answer. Copy the
tree into the scratchpad, add a `fprintf`, recompile. That is how the Brotli
port learned that the encoder is told the input length even though Rebol never
sets it -- which changes which of four hashers runs -- after reading the code
had concluded the opposite twice.

**Coverage is a list of boundaries, not a count of samples.** The Brotli decoder
was reported as checked against a hundred and thirty two real streams at all
twelve levels, and could not read a fifty kilobyte one. All hundred and thirty
two were short. A number sounds like evidence and is not; what is evidence is a
text list of the boundaries -- nothing, one, two, three bytes, one under and
one over each block size, either side of the megabyte where the code changes --
with the test that covers each one beside it, or the word `(none)`.

For a compressor the boundaries that matter are almost never the ones in the
public interface. They are the sizes at which the implementation changes what it
does: the block it reads at once, the window, the point where a table is swapped
for a wider one, the length at which it stops bothering to compress at all. Read
the C for those numbers and test either side of every one.

**Use the reference project's own test corpus, not one you invented.** Brotli's
top two levels passed every input this project could think of -- English text,
noise, long repetitions, mixtures of all three, three thousand generated cases
-- with a constant wrong that makes one of them divide its blocks three times
where the C divides them ten. Three files in Brotli's own corpus catch it
immediately, and its twelve megabyte file then caught two more faults that
nothing smaller reached. The corpus is a release asset rather than part of the
repository: fetch `testdata.txz` from the `dev/null` release at
https://github.com/google/brotli/releases. Where a slice of it is the only thing
that pins a behaviour, check the slice in with its licence and say why.

**Check that a fixture is what it says it is.** A test that builds a binary in
REBOL with `to char!` gets two bytes for every value above 127, so
`counting 16384` is forty-eight thousand bytes and a test named for a block
boundary sits nowhere near one. Assert the input's length and checksum, not only
the output's. A fixture that lies makes a green test that measures nothing, and
nothing else in the build will ever notice.

## Running the suite

**`./gradlew allTests` is the gate.** It runs the spec check, then three suites,
each its own source set and its own task:

| Task | Source set | What is in it |
| --- | --- | --- |
| `unitTests` | `src/unitTest` | Tests that never build an interpreter: values, codecs, the reader, the domain on its own |
| `functionalTests` | `src/functionalTest` | Tests that build an interpreter and run REBOL through it, Rebol's own suite included |
| `endToEndTests` | `src/endToEndTest` | Tests that drive a real interface: the command line, a real Chrome, servers the tests spin up themselves |

A fourth source set, `src/networkTest`, holds the one gate that reaches out:
`./gradlew networkTests` runs the twelve suite assertions named in
`needs-the-network.txt` against the hosts they name. It is not part of
`allTests`, because a build that needs the network cannot run everywhere. The
default suite does not run those steps at all rather than skipping the
assertions conditionally.

Where a test lives says what it is: put a new one in the source set that
matches. Helpers and every resource the three share are in `src/testFixtures`.
`./gradlew test` and `./gradlew check` both run `allTests`, so no name runs
less than it sounds like.

`endToEndTests` needs Chrome and Firefox installed, and Selenium fetches both
browser drivers the first time it runs. The page's services -- navigation, the
dialogs, the clipboard, pictures, MIDI, POST and the console's line -- are driven
in Chrome, and in Firefox only where the two browsers really differ: leaving the
page and coming back to it, the colour and file choosers, BROWSE's popups,
decoding and encoding pictures, and the clipboard. Where the behaviour is JEBOL's
own -- its form, its addresses, its history -- a Firefox run tests the same
JavaScript twice and costs a browser start, so it is not added.

**A browser is started once per suite, never per class or per test.** A page
test class is `@ExtendWith(TheSuitesPageBrowsers.class)` and takes a
`PageBrowsers` in its constructor; that one object lives in JUnit's root store,
so every class in a test process shares it and JUnit quits the browsers when
the run ends. It starts one browser per configuration (the browser, whether
popups are blocked, whether leave prompts are watched), only when a test first
asks, maximises it, and replaces one that has died. Nothing else in the suite
starts a browser: a test that drives the page itself takes `operator.browser()`
from an `AnOperatorAtThePage` opened on its own server. A test reads the
window's size from the page (`window.innerWidth`) rather than assuming one, and
a test that resizes the window has it maximised again on close. Gradle runs end-to-end tests in several
processes, and each starts its own. Each test works in a fresh WebDriver BiDi
user context, which `AnOperatorAtThePage` removes on close, even when opening
failed partway: its tabs, storage, cookies, permissions and history go with it,
and the shared downloads folder is emptied. A browser serves one test at a
time. `ABrowserSharedByTheSuiteStartsEachTestCleanEndToEndTest` proves the next
test finds none of what the last left -- keep it passing, and keep any new
per-test state inside the user context or torn down beside it. Never close a
`PageBrowsers` from a test class. Firefox runs with its own testing preferences
for the clipboard and for Web MIDI's test devices, set in `PageBrowsers`. Its TLS and http checks put the command line to servers
it spins itself up: a local server whose chain the test root vouches for is
read, and local servers that are self-signed, expired, wrongly named or chained
to a stranger are refused. It builds the core for the web
with TeaVM, drives a real Chrome through WebDriver, compares the same paint list
rendered in Java2D and in the browser pixel for pixel, and puts the command
line's TLS to hosts a browser trusts and hosts it refuses. Run it after any
change to the core, to `PaintList`, to either renderer, to the page, to
`prot-tls.reb`, the certificate natives or `JavaCertificateAuthority`.

Two files name hosts on the internet: `thru-cache-test.r3` (github and httpbin)
and `module-test.r3` (one github import). Their twelve assertions run in
`./gradlew networkTests`, not in `allTests`; `needs-the-network.txt` lists them
and decision 46 says why. They pass: the command line trusts the machine's
authorities, and the borrowed DER/CRT codecs decode the GeneralizedTime
validity certificates a TLS inspector presents. `--cafile file.pem` adds
authorities to the machine's. A script may be given by https url and runs
through the same path as a file; `sys-start.reb` is edited for that, and
ORDER.txt lists the edit beside the other borrowed-file changes.

The functional tests take about five minutes, and their floor is
`Interpreter.create()`: about 54ms warm, and one full run
builds about 16,300 interpreters. Ask `scripts/timing.py boot` for the current
boot figures, `scripts/timing.py speed` for the interpreter's own speed against
a real 3.22.5, and goals.md for both. It used to be 86ms and twenty-two
minutes, and where that went is worth knowing before trying to speed anything
else up -- reading Rebol's library was most of it, and `LibrarySource` now
reads each file once for the whole process and hands out a copy. Where the
remaining 54ms goes, and what is slow in the interpreter itself, is decisions
37 and 38.

Do not `rm -rf build/test-results` to force a re-run -- it makes Gradle fail with
`NoSuchFileException` on its own binary results directory. Use `./gradlew
cleanFunctionalTests` (or `cleanUnitTests`, `cleanEndToEndTests`) instead.

Selenium is a test dependency and nothing else. **The shipped jar
has no dependencies and this does not change that** -- about 3,090 KB, of which
556 KB is the carried font, 228 KB the borrowed REBOL library, and about 190 KB
Brotli's static dictionary and the three tables that index it, all carried in
the source because the domain may not read a file. The size is in `goals.md`
too and that is the copy to correct when it moves.

## Code comments: never. Javadoc: only on the contract

**Never write a code comment. Make the variable, method and type names carry the
explanation instead.** This is absolute and it supersedes the global rule's
allowance for short "why" comments: the itch to write one is the signal to
extract a method, rename a variable, or introduce a named constant whose name
says what the comment would have said. What cannot be said in a name goes in the
design notes (`docs/`), not beside the code.

**Javadoc is the same itch wearing a jacket, and the same rule applies to it with
one exception.** The exception is a contract a caller outside the package reaches
for: a public type, a public or protected member of one, an interface, an enum.
There the documentation is the contract and a name cannot carry it.

**An `@Override` is not that exception.** The contract lives on the interface or
the superclass, and the implementation restating it puts the same promise in two
places that can drift apart. A javadoc on an override earns its place only where
the implementation genuinely promises something the contract does not, and then
it says the difference and nothing else.

Everything else goes, and there are only three kinds:

- **Naming the thing again in a sentence.** Delete it. The name already said it.
- **Explaining a step inside the body.** Extract that step into a method whose
  name is the sentence, and the javadoc disappears with it.
- **Carrying a fact about the C.** That one is real and still does not belong
  beside the code. It belongs in `spec/` under the rule it is evidence for, or
  in `docs/rebol-findings.md` where no rule owns it -- a reader who needs to
  know why `Form_Hex_Pad` pads from the left is not reading a private helper to
  find out.

**One more thing may stay, and only in `src/main`: a one-line signpost where
tidying the code would break it.** `BrotliDictionaryMatches` keeps the C's shape and indentation on
purpose, and `BrotliLog2` computes a logarithm the long way for a measured
reason. A line saying "see `docs/brotli-port.md`, and do not tidy this" is not
restating a name -- it is the only thing standing between the file and a
well-meant cleanup. Nothing longer, and only where the danger is real.

**Tests carry no comment and no javadoc at all, class level included.** The
`@DisplayName` beside each test says what it asserts, and the class name says
what the file is about; a paragraph above either one is the same sentence a
third time. This rule once carved out the class doc on a `*FromTheSourceTest`
on the grounds that it named the C function each expectation was read from --
and that carve-out kept three hundred and sixty essays in the test tree. The
evidence is worth having and it belongs in `docs/`, where the next reader looks
for it, rather than above a class they have to open the file to find.

## Names: a method name is a message, not a sentence

**You are sending the receiver a message -- `object.dispatchCommand()` -- and
the name is the message.** A command or a question, not prose about the code:
`a`, `an` and `the` are words a sentence needs and a message does not.
`scanTheItem` is `scanItem`, `copyTheMap` is `copyMap`,
`combineWithANumber` is `combineWithNumber`, `combineFromTheLeft` is
`combineFromLeft`, `asMoneyInTheCurrencyOf` is `asMoneyUsingCurrency`. A
fresh `aBlah` or `theBlah` is the sign that a sentence was written and then
pointed at an object. The same words are furniture in a variable or a type
name.

**A predicate is a question, and there the article is the question.**
`isAFile` keeps its: "is a file" is what is being asked, not furniture
around it.

**And the verb is the operation.** `may` hedges the answer into nothing:
`mayLoseATime` is `willDeductTime`, `mayMeetADate` is
`canBeUsedToCalculateDate`. A metaphor hides the operation (`meet`,
`lose`) -- name the operation. A relative clause is the same failure in a
longer shape: `confineAnyInterpreterThisOneStarts` is `constrainInterpreter`,
`writeWhatConsolePrints` is `responseBasedOnOutcome`.

**And `of` belongs to the static constructor, and almost nowhere else.**
`IntegerValue.of(...)` and `List.of(...)` keep it, because it is the idiom
and it builds the object - sparingly even there. Anywhere else a trailing
`Of` is naming the method's own argument, and it goes: `formThePairsOf` is
`formPairs`, `readTheOctetOf` is `readOctetAt`, `findTheChildOf` is
`childAt`.

**A name flagged for review is read, not stripped.** Look at what the body
does and name that. `hasNoOrderToTakeTheFirstSoManyOf` is
`isObjectErrorModuleOrPort` - the positive question the body answers, never
flipped to `isNot` to make a call site read well. `isTheStarThatMeansKeepIt`
is `isWildcard`, `giveBackWhatTheCollectorTook` is
`removeCollectedReservations`, `furnitureOfAResizableWindow` is
`resizableWindowInsets`. Naming the branches is right when the branches are
the method: `lendTheElementOf` is `convertToCharacterValueOrReturnInput`.

**If the shorter name is lossy, keep the longer one.** `canonFor` was
shorter; `canonicalIndexFor` was the name.

The older names are all over the tree and are renamed as they are touched
(`ide_refactor_rename`, never by hand), with `relatedRenamingStrategy:
"none"`: the default also renames what it judges related, which here has
caught test classes, a tree the rule leaves alone. A rename aimed at an
enum constant's declaration can land on the enum type itself; aim it at a
use site. What this rule is for is the new one.

The test tree is outside the rule: a test class and a test method name what
the test exercises -- a description of behaviour, not a message sent to an
object -- and a sentence belongs there.

## Writing tests that carry REBOL source

**Use a text block, always - one-liners included.** A backslash-escaped quote
(`\"`) must not appear in REBOL source inside a Java test. REBOL source inside a
Java string literal needs its quotes, braces and carets escaped, the escaping is
too confusing to read, and it has hidden real defects here more than once: two
wrong expectations in the file-literal work came from miscounting carets through
`\\`. Where a text block is heavy - a varargs list of short session lines - write
the REBOL string with braces (`{...}`) inside an ordinary Java string instead, so
nothing needs escaping either way.

```java
assertThat(errorIdFromLoading("""
        {%a^^b}""")).isEqualTo("invalid");
```

The text block still escapes a `"` at the very end of the block, so prefer braces
for REBOL strings where the source allows it -- `{...}` reads as itself.

## Java strings: text blocks, and never an escaped closing quote

**Use a text block for anything with a quote in it, and never write `\"""`.**
The rule above is about REBOL; this one is about every Java string in the
project, test or production.

A text block whose last character is a `"` has to escape it, and the result --
`fill\"""` -- is the single most misread piece of punctuation in the language.
It is three quotes closing the block, one quote of content, and a backslash,
and nobody reads it correctly at a glance. If a block would end with a quote,
change the block rather than escape it: put a trailing comma or newline inside
it, or move the final quote into the assertion.

Where a string is short and interleaved with expressions, a text block does not
help, and the answer is not a pile of `\"` either -- it is a name. Building
JSON as `"\"kind\":\"" + kind + "\""` is punctuation pretending to be code;
extract a `field(name, value)` and the escaping disappears along with the
question of whether it is right.

```java
// no
assertThat(json).contains("\"kind\":\"fill\"");

// yes -- the block ends on a comma, so nothing is escaped
assertThat(json).contains("""
        "kind":"fill",""");
```
