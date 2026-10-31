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

## Making them again

Blend2D and asmjit are not checked in. Clone them at the commits above, read
them, build Blend2D with `-DBLEND2D_STATIC=TRUE`, and then:

    gcc -O2 -DBL_STATIC -I<blend2d>/src table.c -o table libblend2d.a -lstdc++ -lpthread -lm && ./table

## Licence

Blend2D is under the zlib licence, Copyright (c) 2017-2024 The Blend2D
Authors. The tables are its output.
