# The golden corpus

Real REBOL examples with their expected results, taken from published
documentation rather than invented here. These are what prove the
language works, as opposed to proving that each part works in isolation.

## Where they come from

Mostly the REBOL/Core User Guide 2.3 at `rebol.com/docs/core23/`, which is
the densest published source of console examples with their output. Each
entry records its origin so a disputed result can be traced back.

**These are REBOL 2 documents and JEBOL targets R3-Alpha.** Most core
language behaviour is identical, but not all of it, and where a result is
known to differ the entry carries `r2-only` and states what R3 does
instead. An entry with no such marker is a claim that R2 and R3 agree, and
that claim is worth checking rather than assuming.

`corpus/sources/` holds fourteen complete programs from the demo gallery
at `rebol.com/pre-view.html`, fetched byte for byte. The page itself has
no inline code, only screenshots and links; the links are where the
material is.

These are View/VID programs, so none of them can be *run* without a
graphics stack that JEBOL will not have for a long time. They are here
because they must all *load*, and loading them is a real test available
today. Between them they contain 184 pairs, 90 tuples, 771 set-words, 478
paths, 86 lit-words, 28 refinements and 21 get-words, including a
set-path with a get-word segment inside it, which no hand-written example
would have thought to include.

Two rules apply to every file in `corpus/sources/`:

- it transcodes without a syntax error;
- molding the result and reading it back gives an equal value, which is
  the `MoldRoundTripsThroughRead` invariant in `spec/load.allium` exercised
  against real input rather than against generated input.

## Format

One entry per `---` block. Fields:

```
--- id evaluation/do-block
--- origin REBOL/Core User Guide 2.3 section 4.3
--- requires op
--- note infix + is not in milestone 1
--- code
do [1 + 2]
--- result
3
```

- `id` — unique, `area/name`. Names the test.
- `origin` — where the example came from, precisely enough to find again.
- `requires` — space-separated capabilities the example needs beyond the
  milestone 1 core. Absent means it should run today.
- `note` — free text, optional.

  Capabilities in use: `op`, `output`, `series`, `control`, `object`,
  `parse`, `reader`, and the ones nothing implements yet — `clock`,
  `random`, `file`, `network`, `map`, `r2-only`. An entry needing one of
  those is reported by the coverage test rather than run, so the gap stays
  countable instead of quietly passing.
- `code` — the REBOL source, verbatim where possible. Self-contained:
  where the original relied on something set up earlier in the chapter,
  the setup is included here rather than assumed.
- `result` — the molded result of the last expression.
- `prints` — expected standard output, when the example prints.
- `error` — expected failure as `category id`, for example `script no-value`.
  Never match on message text; R2 and R3 word their errors differently and
  the wording is not the behaviour.
- `types` — the datatype of each value the text loads to, in order, space
  separated. A loader assertion: it says nothing about evaluating the
  code. A count mismatch fails just as a type mismatch does, which is what
  catches a reader that silently merges or splits values.

An entry has `result`, `prints`, `error`, `types`, or some combination. It
must have at least one, or it asserts nothing.

## Capability tags

| Tag | Means |
|---|---|
| `op` | infix operators, which milestone 1 leaves out |
| `output` | needs standard output captured |
| `series` | needs series natives beyond the core set |
| `control` | needs control natives: if, either, loop, while, foreach |
| `object` | needs object construction |
| `clock` | reads the current date or time, so the result is not fixed |
| `random` | non-deterministic, needs a seeded generator to be testable |
| `file` | touches the filesystem |
| `network` | touches the network |
| `parse` | needs the PARSE dialect |
| `r2-only` | the stated result is REBOL 2 behaviour and R3 differs |

`clock` and `random` entries cannot assert a fixed result as written.
They are kept because the shape of the example is still worth testing once
the clock and the generator are injectable, which the specs already require.

## Origins

Every entry carries an `--- origin` line, and it is worth reading before
trusting the entry.

`confirmed against R3 3.22.1` means the code was put to a real interpreter and
somebody wrote down what came back. Those entries stand as evidence and cannot be
retaken: the binary has been deleted, because it answered what one build of one
fork did on one machine and the C says what the language is.

`read out of <file>.c` is what a new entry carries instead: the line of Rebol's
own C that the answer comes from. It proves the entry is REBOL rather than
JEBOL, and unlike a probe it explains itself.

`REBOL/Core User Guide 2.3 section N` means it came from the
documentation. Good evidence, and not the same thing: the guide describes
REBOL 2 and R3 differs from it in places.

`JEBOL spec/<file>, rule <name>` means the entry records a decision JEBOL
made rather than behaviour it copied. Legitimate, because the spec is the
authority for JEBOL's own choices, but it proves nothing about REBOL.

There used to be a third kind, `JEBOL, found by running it`. That is an
entry recorded from JEBOL's own output, which makes it a test that JEBOL
does what JEBOL does. One of them held the wrong answer for months: it
said an object molds on one line, and a real R3 puts each field on its
own line. The flat mold was wrong and the entry that was supposed to
catch it agreed with the bug.

There are none left, and a new one should not be added. If the C does not settle
it, say so in a note and mark the entry as an open question rather than recording
what JEBOL happens to print.
