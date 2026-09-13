# One PDF, checked in on purpose

`hello-linearized.pdf` is Rebol's own test fixture, from
`src/tests/units/files/` of the Rebol3 repository, under the Apache License
2.0 that covers that repository.

It is here rather than read out of `src/test/resources/rebol-suite/` because
the suite is scaffolding and will be deleted when it goes green, and the
behaviour this file pins has to outlive it.

What it pins is two things nothing smaller reaches. Its objects are numbered
with gaps in them -- 1, 4, 5, 7, 8, 9, 11 and up -- so the encoder writes its
cross-reference table as several runs of consecutive numbers rather than one,
which is the walk that never finished while SORT put pairs in the order of
their written form. And its document information dictionary lives inside a
compressed object stream and carries a PDF date, `(D:20210913102842+02'00')`,
whose offset the codec turns into a zone with `to time!` on a string that
still has its sign on the front.
