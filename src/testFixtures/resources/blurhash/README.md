# What BlurHash answers in the extension's own C

Two tables of what `blurHashForPixels` and `decodeToArray` answer, computed by
Rebol-BlurHash 1.0.0's `src/encode.c` and `src/decode.c` (Wolt's C, MIT),
compiled with gcc 15.2 against glibc 2.43 on x86-64 Linux.
`BlurHashFromTheSourceTest` reads both and expects the same answer exactly.

Both are tab-separated, because the hash alphabet holds both `,` and `|`.

## blurhash-encode.tsv

1,986 rows: eleven sizes from 0x0 to 100x37, each with six pictures, encoded at
3x3 components (the only count the extension uses) and, for 0x0, 7x1, 3x3 and
64x64, at every count from 1x1 to 9x9.

The six pictures are made by `fill` in `table.c` and again by the test, from the
same seed and the same linear congruential sequence: noise in all four bytes, a
gradient, black, white, one colour with zero alpha, and a pattern of x and y.
The bytes are in the order the C reads them -- the first three of each pixel
taken as red, green and blue -- which is not the order a script writes them in;
see `docs/blurhash-port.md`.

A 0x0 picture divides by zero and every factor is NaN. It still encodes, to
`K0TSUA~q~q~q~q~q~q~q~q` at 3x3, because the C's `fmaxf` and `fminf` drop a
NaN operand.

## blurhash-decode.tsv

2,586 rows. `hash row` counts from 1 into the encode table's rows. Rows 1987 to
1994 are the eight hashes that do not read or only just read, in this order:
the empty string, `LEHV6`, `LEHV6nWB2yk8pyo0adR*.7kCMdnj!`,
`LEHV6nWB2yk8pyo0adR*.7kCMdn`, `~~~~~~`, `00000000`,
`L~HV6nWB2yk8pyo0adR*.7kCMdnj` and `LEHV6nWB2yk8pyo0adR*.7kCMdnj`.

Every hash is decoded at 32x32 with a punch of 1. The 3x3 hashes and those
eight are also decoded at 0x0, 1x1, 3x2 and 64x48, and at 32x32 with punches of
-1, 0, 2 and 5. `pixel digest` is the 64-bit FNV-1a of the four-byte pixels the
C wrote, over a buffer that started filled with 7, so a refusal leaves a digest
too and it must be the untouched one.

## Making them again

`encode.c`, `decode.c`, `encode.h`, `decode.h` and `common.h` are not checked
in. Fetch them as text from https://github.com/Siskin-framework/Rebol-BlurHash at
tag 1.0.0, read them, and then:

    gcc -O2 -o table table.c encode.c decode.c -lm && ./table

## Licence

Rebol-BlurHash is MIT, Copyright (c) 2023 Oldes, and the encoder and decoder in
it are MIT, Copyright (c) 2018 Wolt Enterprises. The tables are their output.
