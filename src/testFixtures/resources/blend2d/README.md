# What Blend2D's compositing answers

Two tables of what Blend2D's JIT pipeline writes when it fills a 64x64
premultiplied picture with a solid colour, made by `table.c` linked to Blend2D
`bae3b6c6` and asmjit `e8c8e2e4`, built with GCC 15.2 at `-O3` as a static
library with JIT, run on an x86-64 CPU with AVX2 and FMA.
`Blend2DCompositingFromTheSourceTest` reads both and expects every pixel.

Each row is `operator` (0 to 28, `BLCompOp`), `colour` (straight
`0xAARRGGBB`, as `blContextSetFillStyleRgba32` takes it), `alpha` (the global
alpha as a double), `seed`, and `pixel crc32`, the CRC-32 of the 4,096 pixels
afterwards as little-endian bytes.

The picture starts as noise in all four bytes from `seed`, by the generator in
`table.c`, which the test repeats. It is not premultiplied, as R3's pictures are
not, so a colour channel above its alpha is common; that is what the
extension hands Blend2D.

## blend2d-fill.tsv

6,264 rows of `blContextFillAll`. The first 2,784 are every operator with each
of 12 colours (transparent black, opaque white and black, half-transparent red,
near-transparent, near-opaque, two mid colours, 25% white, opaque green, a grey
at 127 and two more) at each of 8 alphas (1, 0, .5, .25, .75, .001, .999, .3).
The other 3,480 are 120 random colours for each operator, every eleventh with its
alpha zeroed and every eleventh opaque, at alphas that are 1 for a third, one of
the boundaries 1/255, 2/255, 127/255, 127.5/255, 128/255, 254/255, 254.5/255,
.5/255 and .49/255 for a third, and random for a third.

## blend2d-mask.tsv

1,740 rows of `blContextFillMaskI` through an A8 mask the size of the picture:
rows 0 to 3 of the mask are 0, rows 4 to 7 are 255, rows 8 and 9 are 0 or 255 at
random, and the rest are random bytes. 60 random colours an operator, chosen as
above, with every thirteenth transparent black.

## Boundaries

- alpha scaled to 0 (nothing drawn), 1, 2, 127, 128 (half rounds up), 254, 255:
  the fill table's boundary third
- colour of 0 (its own format), alpha 0 with colour (premultiplies to 0 but is
  not 0), opaque (simplified to other operators): both tables
- mask 0, 255 with and without alpha, anything between: the mask table
- a destination channel above its alpha, which sends colour-burn's float past
  the range of an int: noise in every row
- every operator's constant-mask and variable-mask paths: both tables

## blend2d-draw.tsv

510 rows of what the extension's own `draw` does, made by running the
extension's C unchanged -- `blend2d-command-*.c` at tag `53f6ffe9` -- against
`probe/stub.c`, a stand-in for R3's `RL_` table that reads a small subset of
REBOL, linked to the same Blend2D. Each row is `image` (`noise WxH seed`, the
generator above in R3's own memory order, or `pair WxH` for a new image),
`block`, the `size` drawn, and the `pixel crc32` of the image afterwards.
`Blend2DDrawFromTheSourceTest` draws every row in JEBOL and expects the same
pixels, except two rows that need the rasteriser, which it expects refused by
name.

`probe/draw-cases.py` writes the cases. They cover, in order:

- each branch of a box that is not on whole pixels, as
  `initBoxU8bpc24x8` takes it -- one pixel wide, whole-pixel sides, a row of
  masks at most 20 wide and one wider, with and without the left padding, one
  row high, two rows, and top and bottom rows whose masks round to 0 -- each
  with all 29 operators, alternating four colours and four alphas;
- `fill-all` under an alpha, under an unaligned clip, and after a clip is
  lifted, with all 29 operators;
- boxes under `translate` and `scale`, negative scales, `scale 0 0` (an
  invalid transform, which draws nothing) and `reset-matrix`;
- `clear` and `clear-all`, nested clips, a clip under a scale, a clip wholly
  outside the image;
- the walk itself: values before the first command, words that are not
  commands, commands missing arguments, `blend` with a word that is not an
  operator, an integer, a lit-word, `off`, `none` and nothing at all,
  `line-join` given a pair, `alpha` as a percent, above 1, below 0 and too
  small to count, shapes read but not drawn because there is no fill or pen,
  and tuples of five and four parts.

A block that opens with something that is not a command reads `cmd_pos`
before the C has set it, so what happens is the compiler's: this probe, built
with GCC 15.2 at `-O2`, behaves as if it were 3, and so does JEBOL.

## Making them again

Blend2D and asmjit are not checked in. Clone them at the commits above, read
them, build Blend2D with `-DBLEND2D_STATIC=TRUE`, and then:

    gcc -O2 -DBL_STATIC -I<blend2d>/src table.c -o table libblend2d.a -lstdc++ -lpthread -lm && ./table

For the draw table, also clone Siskin-framework/Rebol-Blend2D at `53f6ffe9`
and read it, then build the probe from its `src` with the stub, run the cases
in chunks of a hundred or so (the stub's tables are small), and turn each
image it prints into a row:

    gcc -O2 -std=gnu17 -fpermissive -w -DTO_LINUX -DTO_LINUX_X64 -D__LP64__ -DENDIAN_LITTLE \
        -DBL_STATIC -I<rebol-blend2d>/src -I<blend2d>/src probe/stub.c probe/nojit.c \
        <rebol-blend2d>/src/blend2d-*.c -Wl,--wrap=blContextInitAs -o probe libblend2d.a -lstdc++ -lpthread -lm
    python3 probe/draw-cases.py cases.r3 cases.tsv

## Licence

Blend2D is under the zlib licence, Copyright (c) 2017-2024 The Blend2D
Authors. The tables are its output.
