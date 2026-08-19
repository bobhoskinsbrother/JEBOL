# TODO

Only work that is left. History lives in git and in `docs/`. How to port a
function, what the authorities are and what the regression floors say now live
in `docs/porting-guide.md`, because they are guidance rather than work.

Every line below was checked on 2026-08-19 by running it.

## Came off the list

Four things were already done and one "live defect" had fixed itself:
`system/standard` carries all 29 fields, `load-json` reads arrays and objects
and round-trips, both borrowed files load whole, the five `n-image.c`
functions are ported, and the Java `view` stub is gone.

That is the same disease as the measures below: something gets written down
and nobody checks it against the thing it claims to describe.

## Found while checking

**`layout` is a Java native that returns its argument.** A VID program runs,
reports success and draws nothing. Worse than a fork, because `layout` is
defined nowhere in Rebol either -- JEBOL invented a stub that says yes. Its
twin, the `view` identity stub, has been replaced by the real thing; this one
has not. A `feature-na` refusal would be better than what is there.

---

# Goal 1. The bugs

A map matching a string key case-sensitively. Two measures that lie --
`PortingBacklogTest` asserting 24 missing where the number is zero, and
`c-parity`'s `MISSING: 0` over an input that cannot see `REBNATIVE(...)`.
`LAYOUT`. And an error's fields being a record rather than a context.

# Goal 2. The type-major refactor

The original complaint, still the largest piece. One `t-*.c` per increment,
bitset as the pilot.

The graphics work left it a hint: `PaintInstruction` is sealed, so adding a
kind broke every renderer's switch at compile time. That is the shape the
action seam wants.

# Goal 3. The 46 prelude forks

Audited by identity rather than by datatype.

# Goal 4. Graphics

What it does not do: eleven DRAW commands, the stroked-curve comparison
problem, the 522 lines of old markup path, VID, Android, and the
events-name-the-wrong-window one.

# Goal 5. Loose ends

`vector!` and `task!` carry 60 of the 101 TYPES lines. Seven more schemes the
JDK could serve. TLS loaded but not connected. 48 open questions across ten
spec files.

# Goal 6. The 68ms boot

Pool first, then library caching.

---

## Where I'd go next

**Goal 1's two lying measures.** They have now hidden three separate things,
including one item in this very file. Everything else you decide is downstream
of measures you can believe.
