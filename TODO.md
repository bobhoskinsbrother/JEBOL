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

That is the same disease as the measures were: something gets written down
and nobody checks it against the thing it claims to describe.

---

# Goal 1. The bugs -- DONE

The map key case, the two lying measures, the three natives they found, an
error's fields, and LAYOUT. Fixed in `6e9c9a7` and `2bb77e0`.

Worth keeping from it. The measures had four separate things wrong and between
them they hid five real gaps: `c-surface.py` read only the boot files and so
could not see the 54 natives the C declares in its own comments; it counted
two objects as functions; `PortingBacklogTest` asked one context of two; and
`limit-usage` was counted as work when Rebol deletes it on purpose. The
backlog now reads 3 of 404 rather than 24 of 353, and those three -- `clamp`,
`distance`, `factorial` -- are ported.

Two smaller things the measures turned up are still open and are in Goal 5:
`dir?` is written in the wrong layer, and `access-os` and `request-color` have
the wrong refinements.

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

`dir?` written in REBOL where Rebol writes it in C. `access-os` missing its
`/set` and its second argument; `request-color` with an `/rgb16` R3 has not
got. `vector!` and `task!` carry 60 of the 110 TYPES lines. Seven more schemes
the JDK could serve. TLS loaded but not connected. 48 open questions across ten
spec files.

# Goal 6. The 68ms boot

Pool first, then library caching.

---

## Where I'd go next

**Goal 1's two lying measures.** They have now hidden three separate things,
including one item in this very file. Everything else you decide is downstream
of measures you can believe.
