# Where these came from

The five archives here are copied byte for byte from Rebol's own test files,
`rebol3-source/src/tests/units/files/`, which are Apache 2.0 licensed like the
rest of Rebol; the licence is reproduced in `LICENSE`.

| File | What it holds |
| --- | --- |
| `test-deflate.zip` | one entry, `host-table.c`, 333 bytes, deflated |
| `test-stored.zip` | `0.txt` (empty), `directory/`, `directory/1.txt` (the one byte `2`), all stored |
| `test-lzma.zip` | one entry, `xJSFL.komodoproject`, 899 bytes, LZMA (method 14) |
| `test1-deflate.swf` | a `CWS` (zlib) movie of 25 frames |
| `test2-lzma.swf` | the same movie as `ZWS` (LZMA) |

They are here because LZMA is an optional part of the core (goal 2a step 6),
and what an archive does without it is a behaviour of JEBOL's own: the core
reads the deflated and stored ones, and answers `invalid-arg` naming `lzma` for
the others, as R3's Core product does. Rebol's suite asserts the same loads in
`codecs-test.r3`, but its copies leave with the suite when it goes green, and
the tests that pin this behaviour must not.
