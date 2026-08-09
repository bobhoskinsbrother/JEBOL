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
