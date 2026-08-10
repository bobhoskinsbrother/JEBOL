# TODO

Only work that is left. History lives in git and in `docs/`.

## The goal

**Port every function a real Rebol 3 has into JEBOL.** The imported Rebol
test suite is how a port is checked, not what is being aimed at.

Two measures, and they answer different questions. `PortingBacklogTest`
says what Rebol has and JEBOL has not. `SuiteFailureReportTest` says, of
what is here, what is wrong. A function that is missing usually shows up as
no failure at all, so the backlog is read first.

**Nothing is committed until every test passes.** That includes the
imported suite: 3721 assertions, all green. This is settled; do not raise
it again and do not offer to commit "the safe part".

## The rule about layers

Rebol writes about a third of its library in C and the rest in REBOL.

**A function Rebol writes in C is written in Java here. A function Rebol
writes in REBOL is imported from its own file and loaded as a resource. It
is never rewritten and never copied into `prelude.reb`, not even
verbatim.**

The test is mechanical: find which R3 file defines it. `src/core/*.c` means
Java. `src/mezz/*.reb` means copy that file into
`src/main/resources/org/jebol/mezz/`, add it to `ORDER.txt`, and fix
whatever native it turns out to need. Decision 13 in `docs/decisions.md`
says why a verbatim copy is still a fork, and what it cost last time.

## How to port one function

Four steps, in order. Do not begin one before the last is finished.

1. **Read the C.** The whole function, not the part that looks relevant.
   The C carries the rules no probe will show you: which flags are set
   together, what a count of zero means, where a search starts.
2. **Copy the logic into Java.** Follow the C's structure. Where the C has
   a branch, have a branch. A rewrite that looks tidier is a rewrite whose
   bugs are yours and not Rebol's.
3. **Write the tests from the C, not from your Java.** Read the C again and
   write a test for every branch, flag combination and boundary it guards.
   Tests written by reading the port only prove the port agrees with itself.
4. **Run the imported suite.** A failure there is a rule the C states and
   the port missed.

Probing `./r3` is the last resort. Its console interleaves prompts with
output, so a bare probe is easy to misread. If you probe, print a label and
run the same file through both.

Sources, in order: Rebol's own C at `~/Code/personal/rebol3-source`
(`src/core/t-*.c` for datatypes, `n-*.c` for natives, `c-do.c` for the
evaluator, `l-scan.c` and `l-types.c` for the reader, `s-mold.c` for
molding, `src/boot/errors.reb` for the error catalogue, `src/mezz/*.reb` for
the REBOL half); then the suite in `src/test/resources/rebol-suite/`; then
`./r3`.

---

# 1. Undo the forks

JEBOL implements 46 functions that Rebol writes in REBOL. Each is a fork by
the rule above, and each blocks the R3 file that defines it from ever being
loaded over the top.

**14 in Java** (`Natives.java`), of 208 Java functions total. The other 194
are legitimate: 176 are C in Rebol, 11 are ones `base-defs.reb` generates
from the catalogue, and 7 are JEBOL's own with no Rebol counterpart
(`did`, `layout`, `make-error`, `read-dir`, `with`, and the two shift
operators).

| R3 file | functions in Java that belong to it |
| --- | --- |
| `base-files.reb` | `exists?` `load` `make-dir` |
| `mezz-files.reb` | `ask` `input` |
| `base-funcs.reb` | `function` `use` |
| `base-defs.reb` | `quote` `true?` |
| `base-constants.reb` | `abs` |
| `mezz-func.reb` | `context` |
| `mezz-tail.reb` | `func` |
| `mezz-series.reb` | `split` |
| `view-funcs.reb` | `view` |

**32 in `prelude.reb`**, of 47 there. The prelude's own header already
admits these are "something Rebol's own library silently replaces the
moment that file is borrowed".

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

**11 duplicated generators.** `base-defs.reb` loads and generates the
typeset predicates from `system/catalog/datatypes`. JEBOL also writes
`datatype?`, `error?`, `get-word?`, `lit-word?`, `map?`, `none?`,
`object?`, `ref?`, `refinement?`, `set-word?` and `unset?` in Java. One of
the two is redundant and it is not the generator.

`abs` is the smallest one and a good first move: `base-constants.reb` is
already loaded, so deleting the Java definition should need nothing.

# 2. Finish the file-library import

`mezz-func.reb` and `mezz-files.reb` are on disk and out of `ORDER.txt`.
Adding both makes **every borrowed file load to its end for the first
time** and gains 25 functions: `clos`, `closure` with `/with`, `has`,
`context`, `map`, `task`, `enum`, `wildcard`, `confirm`, `dir-tree`,
`list-dir`, `in-dir`, `to-relative-file`, and the twelve
`mezz-shell.reb` defines.

It cost 21 unit assertions when first measured. **Eleven are now fixed** --
CLEAN-PATH needed CASE to answer true for a truthy condition with no block
after it, and MAKE to take a value as a prototype rather than only a
datatype. Both were C behaviours, both are ported, and both were general
rules rather than anything to do with files.

**Two blockers remain, and each is bigger than the import:**

- **`input` and `ask`** (7 assertions) want `system/ports/input` and
  `open [scheme: 'console]`, which is the whole `port!` datatype. JEBOL's
  own INPUT and ASK are Java natives duplicating Rebol's REBOL, so they are
  forks and Rebol's should win -- once there is a port to read.
- **`enum`** (4 assertions) wants `system/standard/enum`, and its PARSE rule
  uses INSERT and CHANGE as keywords. The values come out shifted by the
  number of names, which points at one of those two keywords or at
  FUNCTION's collection of locals. A real defect either way, and worth
  finding on its own: it is in the evaluator rather than in ENUM.

Then delete the `mezz-shell.reb` line from `BorrowedFilesLoadWholeTest`'s
`STOPS_ON`, which is what that test's javadoc says to do when a stop is
fixed, and the partial-load count reaches zero.

Also unblocked once `base-files.reb` can be imported: `size?`, `modified?`,
`delete-dir`, `file-type?`, `import`, `intern`. That file is out for a
different reason: its `LOAD` is the REBOL-level one and wants the whole of
`sys/load-header` underneath it, which costs 115 assertions. Getting that
LOAD to work is its own piece of work and it is worth six functions plus
whatever `sys-load.reb` brings.

# 3. What is wrong with what is ported

500 failing suite assertions of 3721. Grouped by subsystem, because each
has its own C source and can be finished and gated on its own.

### The reader and the molder (153)

lexer 91, load 32, mold 30. Source: `l-scan.c`, `l-types.c`, `s-mold.c`.
Molding is the inverse of reading, so they go together: a value cannot be
made to write back correctly while it still reads wrongly.

Clustered into six pieces, largest first: invalid construction and which id
it raises (14), `load/header` and SAVE (19), the special slash and
arrow-like words in `Lex_Map` (15), the `#(datatype! ...)` construction
form read and molded (~27, spanning both halves), TRANSCODE's error
reporting (9), `mold/part` and string molding (~10).

One piece already known: `deci_to_string` uses the exponent form when a
money's digits run out, so `mold $1e-100` is `$1e-100` where JEBOL writes a
hundred zeros. Nothing in the suite asserts it.

### Series and PARSE (154)

series 98, parse 56. Source: `f-modify.c`, `t-block.c`, `t-string.c`,
`u-parse.c`. No dominant cluster: FIND and SELECT 11, a series past its
tail 10, then PUT, REMOVE, TAKE, POKE, CHANGE and merging at 6 to 7 each.

### Evaluation, objects and protection (168)

evaluation 102, object 35, protect 18, func 13. Source: `c-do.c`,
`c-frame.c`.

**Carve out `delta-profile` before estimating.** It is 24 of evaluation's
102 and it is not evaluation semantics at all: every assertion is about
`p/evals`, `p/eval-natives`, `p/series-made`, `p/series-freed`, which need
counters in the evaluator rather than a rule from the C.

### Typesets and datatypes (25)

typeset 13, error 6, datatype 3, and one each in char, conditional and
tuple. Source: `t-typeset.c`, `t-datatype.c`. No goal has ever named these
and goal 3 counted the typesets without naming a source file. They are the
last two datatype ports and the method from the money and pair work fits
them exactly.

# 4. What is still missing

129 of Rebol's 580 functions. **21 of those are already written and shipped
in files JEBOL holds** — see section 2 — so the real figure is 108, and
`PortingBacklogTest` cannot tell the difference because it measures
`Interpreter.create()`.

### The host boundary (37 functions)

`spec/embed.allium` specifies it and most of the code is now written:
`HostService`, `ServiceRefusal`, `Bounds`, and ports for files, processes,
console, environment and the working directory all exist. What is left is
the functions on top.

- **Ports as a datatype** (5): `open`, `open?`, `close`, `query` on a port,
  `create`. `port!` is a datatype JEBOL has not got, and this is really its
  own piece of work rather than part of the boundary.
- **The WINDOWS service** (5): `browse`, `request-color`, `request-dir`,
  `request-file`, `request-password`. Mostly refusals; `ServiceRefusal`
  already has the three reasons to choose between.
- **Interpreter internals** (7): `recycle`, `stats`, `evoke`, `secure`,
  `wait`, `set-scheme`, `flush`. Decide each one's reason from the C;
  several are `never_portable`.
- **The rest** (20) sit on the file and process services that already work.

### The console layer (about 25 of the 53 "ordinary language")

`help`, `about`, `usage`, `dump-obj`, the five `log-*`, `make-banner`,
`version`, `license`, `source`, `what`, `halt`, `trace`, `?` and the rest.
They need `emit`, `reform`, `ansi` and `system/options`. `spec/repl.allium`
specifies the REPL, not this.

`system/options/ansi` and `system/options/no-color` both raise
`invalid-path` today, which is why `dir-tree` will define and not run.

### The user context and modules (3)

`system/contexts/user` is not published, so `intern`, `module` and `import`
cannot reach it. JEBOL has the context; exposing it under that name is the
work. Milestone 2 deferred modules deliberately and this is what it
deferred.

### Codecs and cryptography (17)

`checksum`, `compress`, `debase`, `enbase`, `rsa`, `ecdh`, `iconv` and ten
more. Libraries rather than language.

### Images and colour (23)

`image`, `resize`, `blur`, `rgb-to-hsv` and the eight `as-colour`
functions. Needs a graphics model JEBOL has not got.

# 5. Two live defects

**A borrowed file's local names replace library functions.** `exp` and
`stack` are both `block!` right now, not functions: `codec-json.reb` writes
`exp: [[#"e" | #"E"] ...]` as a parse rule and `codec-plist.reb` does the
same to `stack`. The name still answers, it just answers the wrong thing,
and the porting backlog reports both as missing without saying why. 36
borrowed files, every one a candidate. Audit gap 5 in
`docs/surface-audit.md` gives two ways out and neither is small; a test
that fails when a borrowed file replaces a library name would at least make
the list visible.

**`decode-url` is `none!`.** `base-defs.reb` loads and leaves it that way.

# 6. Loose ends

- **Host object mutability, documented where it will be read.** Decided in
  `docs/decisions.md`; not yet in the embedding documentation or the API
  javadoc. An afternoon.
- **`draw` dialect to SVG.** One renderer. Milestone 5's open fork in
  `docs/milestones.md` covers the thinking.
- **Fetch a URL.** Part of the host boundary rather than a story of its own.

---

## Working notes

Things a fresh session has to know, each of which cost time to find out.

**Get the C source first.** It is not in this repository.

```
git clone --depth 1 https://github.com/Oldes/Rebol3 ~/Code/personal/rebol3-source
```

**Do not use `./gradlew run` to try something out.** It starts the REPL and
waits on standard input, so it hangs until killed. To probe, write a
throwaway JUnit test and delete it afterwards:

```java
// src/test/java/org/jebol/Probe.java
String[] cases = { "mold 1.2.3", "length? 'a" };
for (String source : cases) {
    Interpreter interpreter = Interpreter.create();
    interpreter.defineFreshWordsIn(source);
    System.out.println("  " + source + "   ==>   "
            + interpreter.display(interpreter.run(source)));
}
```

Run it with `./gradlew test --tests 'org.jebol.Probe' -i` and filter for the
arrow. Always print a label; unlabelled output is how four defects were
claimed and withdrawn in one afternoon.

**Reading the suite result.** `./gradlew check` is the gate. The report is
`build/test-results/test/TEST-org.jebol.suite.RebolSuiteTest.xml`, one
`<failure>` per assertion. Two traps: every failure appears twice in that
file, so halve any count taken by grepping; and **running any single test
class deletes the suite's XML**, so re-run the suite before reading it
again, or save the failures out first.

**The build is strict.** `-Werror` with `dangling-doc-comments`, so
inserting a declaration between a javadoc and the thing it documents fails
the build. `DependencyRuleTest` enforces the hexagonal boundary: a domain
class reaching `java.io.File` is a build failure, not a review comment.

**Writing a test from the C.** A private `answerTo(String)` that builds an
`Interpreter`, calls `defineFreshWordsIn` and then `run`, and a private
`errorIdOf(String)` that wraps the source in `try` and answers `e/id` or the
word `no-error`. Name the file `<Thing>FromTheSourceTest` and cite the C
function in the class javadoc.

Three traps in those tests. JEBOL molds none as `_`, not `#(none)`. A
literal block compares as words, so `x = ["a" true]` is false and
`x = reduce ["a" true]` is what was meant. And `e: try [...]` followed by
`error? e` raises when the body answered unset, so use `unset? do ...`.

**Commits use `hoskins_ben@hotmail.com`**, set repository-locally already.
Do not fall back to the git global, which is a work address. No attribution
trailers.

**`known-gaps.txt` is deliberately empty of gaps.** Not a skip list: every
assertion runs on every build. Leave it empty.

**`src/test/resources/r3/surface.txt`** is the checked-in record of R3's
library: 580 functions with their argument and refinement shapes.
`PortingBacklogTest` compares it against a booted interpreter and asserts
the difference, so the count only goes down.
