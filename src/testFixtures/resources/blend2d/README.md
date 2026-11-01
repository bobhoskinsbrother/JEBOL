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

1,187 rows of what the extension's own `draw` does, made by running the
extension's C unchanged -- `blend2d-command-*.c` at tag `53f6ffe9` -- against
`probe/stub.c`, a stand-in for R3's `RL_` table that reads a small subset of
REBOL, linked to the same Blend2D. Each row is `image` (`noise WxH seed`, the
generator above in R3's own memory order, or `pair WxH` for a new image),
`block`, the `size` drawn, and the `pixel crc32` of the image afterwards.
`Blend2DDrawFromTheSourceTest` draws every row in JEBOL and expects the same
pixels, except two strokes of one closed cubic whose edges hang on the last bit
of glibc's `pow`, which it expects drawn without error
(`docs/blend2d-port.md` says why).

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
  and tuples of five and four parts;
- the rasteriser for straight edges: 26 polygons, each under three fills --
  lines steeper and shallower than 45 degrees in both directions, edges
  crossing 4-pixel groups, points on pixel and sub-pixel boundaries, polygons
  clipped on each side and wholly outside, self-crossing ones, one point, two
  points -- two polygons under thirteen operators, boxes rotated through eight
  angles about the origin and about a point, polygons under clips, scales and
  an invalid transform, `triangle` with whole and partial triples, and images
  wide and tall enough to cross Blend2D's band height;
- curves: 45 shapes, each under three fills -- circles from a radius of 0 to
  past the image and negative, circles with two radii, ellipses, rounded boxes
  with radii capped, zero, negative and past the sides, arcs open, `pie` and
  `chord` through every quadrant and past a full turn, cubics with loops,
  cusps and inflections, clipped on each side -- two curves under thirteen
  operators, ellipses and rounded boxes rotated through four angles, curves
  under a scale, a clip and a translation, and images wide and tall enough to
  cross the band height;
- strokes: 35 lines and outlines at four widths, each under one join and one
  cap taken in turn, every join on four angles, every cap on an open and a
  bent line, turns from 20 to 40 degrees under the three miter joins either
  side of the miter limit, outlines over fills, strokes rotated, scaled
  unevenly, clipped and translated, `pen off`, `line-width 0`, caps past 5,
  and one line under thirteen operators;
- the path dialect: 29 `shape` blocks, each filled, stroked, and filled and
  stroked -- every command, smooth curves after and not after their own kind,
  `arc` with each of its flags, rotated and degenerate, `hline` and `vline`
  after `close` and on an empty path, words that are not commands, a value
  before the first command, a second figure and an empty block -- and `shape`
  rotated, under alpha and an operator, stroked with caps, and given a number
  or an unknown word.

A block that opens with something that is not a command reads `cmd_pos`
before the C has set it, so what happens is the compiler's: this probe, built
with GCC 15.2 at `-O2`, behaves as if it were 3, and so does JEBOL.

## blend2d-paint.tsv

65 gradient and pattern fills drawn by the same probe, kept whole because
JEBOL is expected near them rather than equal to them. Each row is `image`,
`pattern` (empty, or `opaque WxH seed`: the noise generator with every alpha
255, which the stub reads as `<opaque WxH seed>`), `block` (with `PATTERN`
where the pattern goes), the `size`, and every pixel as an ARGB word.
`Blend2DPaintFromTheSourceTest` binds `PATTERN`, draws each row and expects
every channel within 2 levels.

`probe/paint-cases.py` writes the cases and `probe/paint-table.py` turns the
probe's output into the table. They cover six stop lists -- two stops, three,
transparent ends, stops inside the run, two at one offset, and stops outside
0 to 1 -- under linear, radial with the focus at and off the centre, and conic
gradients, conic start angles and repeats, a vertical and a degenerate linear
gradient, a gradient with no stops, gradients set before and after a rotation,
under a scale and an operator, and an opaque pattern under all nine extend
words, whole and translated, translated by a fraction, scaled, rotated, under
an operator, and given to `pen`.

## blend2d-image.tsv

154 blits drawn by the same probe, run in `images/` so the files are found,
and kept whole. Each row is `image`, `source` (empty, or `opaque WxH seed`,
`premul WxH seed` -- the noise generator with every channel scaled by its
alpha and truncated, so no channel exceeds its alpha -- or `noise WxH seed`),
`setup` (a statement run first, binding a handle `H` or a path `P`), `block`
(with `SOURCE` bound to the source), `kept` (`exact` or `close`), the `size`,
and every pixel as an ARGB word. `Blend2DImageFromTheSourceTest` expects the
121 exact rows byte for byte and the 33 close ones within 2 levels a channel.

`probe/image-files.py` writes the files in `images/` from seeded noise with
Pillow: PNGs of colour types 6, 2, 0, 4 and 3 with a transparent palette
entry, a 24-bit BMP, a V4 BMP with an alpha mask and premultiplied bytes,
colour and grey JPEGs, QOIs of four and three channels, and four that
Blend2D refuses -- a GIF, a text file, the same alpha BMP with a V5 header,
and (by its absence) `missing.png`. `probe/image-cases.py` writes the cases:

- whole-pixel blits of an opaque and a premultiplied image at eight corners,
  inside, across each edge and wholly outside;
- each of the 29 operators with a premultiplied image, and with an opaque one
  under `alpha 0.6`;
- fractional corners, a translation by whole and half pixels, scales, a
  mirror, rotations about the origin and a point, and `scale 0 0`;
- scaled blits larger, smaller, the image's own size, at a fractional corner,
  with negative and zero sizes, past the edges, rotated and under a scale;
- clips aligned, unaligned and outside, `alpha 0.5` and `alpha 0`;
- the walk: the value after a size skipped, `none`, no corner, a pair where
  the image goes, handles drawn plain and scaled, and a path handle refused;
- every file blitted with `source-copy` and `source-over`, and used as a
  `fill` pattern through a handle, and the four refused files ending the walk.

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
    split -l 200 -d cases.r3 chunk- && for f in chunk-*; do ./probe $f >> cases.out; done
    python3 probe/draw-table.py cases.tsv cases.out blend2d-draw.tsv
    python3 probe/paint-cases.py paint.r3 paint.tsv && ./probe paint.r3 > paint.out
    python3 probe/paint-table.py paint.tsv paint.out blend2d-paint.tsv
    python3 probe/image-files.py images && python3 probe/image-cases.py image.r3 image.tsv
    (cd images && ../probe ../image.r3) > image.out
    python3 probe/image-table.py image.tsv image.out blend2d-image.tsv

`B2D_THREADS=0` (or any count) and `B2D_NO_JIT=1` override what the extension
asks Blend2D for, which is how the port's notes on bands, threads and the
reference pipeline were checked.

## Licence

Blend2D is under the zlib licence, Copyright (c) 2017-2024 The Blend2D
Authors. The tables are its output.
