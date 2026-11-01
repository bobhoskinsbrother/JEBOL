# The blend2d port

R3's `system/modules/blend2d` names the 0.12.0 release of
Siskin-framework/Rebol-Blend2D. JEBOL serves it in Java as decision 58 says. This
note carries what the code cannot: what the extension does, which Blend2D it
builds, how the port is checked, and how the pixels are split between the domain
and the platform.

Work in progress. Served: the module, the whole `draw` walk, the compositing,
`fill-all`, `clear-all`, `clear`, boxes and clips under any transform, and the
rasteriser with its curves -- `polygon`, `triangle`, `circle`, `ellipse`,
`arc` with `pie` and `chord`, rounded boxes, `cubic`, and any of them under a
rotation -- each filled byte for byte against the release's C, and the
stroker, which draws `line` and every one of those outlines under `pen`,
`line-width`, `line-join` and `line-cap`, and `shape` and `path` with their
dialect. Gradients and image patterns are drawn too, but not byte for byte:
see below. Not yet: the rest goals 1a lists, each of which raises an error
naming what it cannot draw.

---

## The rasteriser

Blend2D fills a shape in three steps, and JEBOL ports each:

- **`EdgeBuilder`** (`raster/edgebuilder_p.h`) maps the points through the
  fixed-point transform -- `x * sx + tx` when the transform only scales, the
  full affine map otherwise -- clips each line to the clip box in doubles, and
  truncates the ends to 24.8 fixed point. A line outside the clip on the left
  or right is not dropped: its rows are gathered into a vertical edge along
  that side, so the coverage it would have carried still reaches the pixels
  inside. Its `goto`s are a state machine whose states are the C's labels.
- **`AnalyticRasterizer`** (`raster/analyticrasterizer_p.h`) is AGG's cell
  and area method with exact integer error stepping. It writes a cover and an
  area into each cell an edge crosses and marks the 4-pixel group the cell is
  in. JEBOL ports the paths the C takes without banding.
- **`AnalyticFill`** is the JIT's `FillAnalyticPart`. Along each row it sums
  the cells from the first marked group, turns the running sum into a mask,
  and composites marked groups pixel by pixel with that mask through the
  variable-mask path, and the gap between two marked runs at one constant mask
  -- the mask of the gap's first pixel -- through the constant-mask path,
  skipping it when the mask is 0. The mask is the JIT's: shift the sum by 8,
  apply the fill rule, take `|m - 0x200|` capped at 0x200 by an unsigned
  minimum, and multiply by the global alpha shifted left 7, keeping the high
  16 bits. That is exactly twice the reference pipeline's mask over twice its
  divisor, so the two agree byte for byte.

Polygons, triangles and paths fill non-zero, as the extension never sets a
fill rule; a box under a rotation becomes a four-point polygon filled even-odd,
Blend2D's preferred rule, as `fillUnclippedBoxD` does.

What the port leaves out, and why the pixels are still the same:

- **Bands.** Blend2D rasterises one band of rows at a time, saving each edge's
  state between bands. JEBOL rasterises the whole image at once. The probe
  gives the same bytes with no worker threads, one, and four, which rasterise
  with bands of different heights.
- **Edge vectors split when an arena block fills.** Each segment is rasterised
  on its own and cells are integer sums, so where a vector splits, and the
  order edges arrive in, cannot change a cell.
- **Asynchronous rendering.** The extension asks for one thread, which makes
  Blend2D queue its commands; the probe draws the same bytes synchronously.

`blend2d-draw.tsv` holds 139 rows for this, at the rasteriser's boundaries:
lines steeper and shallower than 45 degrees in both directions, edges crossing
4-pixel groups, points on pixel and sub-pixel boundaries, polygons clipped on
each side and wholly outside, self-crossing ones, rows past the 64-row band
height, and rotations through 90 degrees, under alpha and thirteen operators.
Changing one rasteriser error term by one fails rows; skipping the constant-mask
gaps fails 106.

## Curves

Every curve the extension's `draw` makes is a cubic. `Blend2DPath` builds the
path Blend2D's `PathAppender` does: a circle or an ellipse is four cubics from
its rightmost point with `kKAPPA` = 0.55228474983, a rounded box is lines and
four cubics with the radii capped at half the sides (a box when either radius
is not above 1e-14), and an arc is `arcToCubicSpline`'s quarter-turn cubics
and a last conic. That conic reaches the path as a cubic, because the release's
`PathAppender::conicTo` turns a conic into one with `k = 4w / (3(1 + w))`; its
conic form is commented out. So the edge builder needs only its cubic half:

- **Fast rejection.** A run of cubics wholly above, below, or to one side of
  the clip is skipped; to a side it still adds a border edge for the rows it
  spans.
- **`CubicSpline`** is `splitCubicToSpline`: it cuts a cubic at its cusp, its
  inflections and its extrema in x and y -- roots from `quadRoots` between
  1e-40 and 0.999999999999999889, insertion-sorted -- into pieces monotonic in
  both directions, ending exactly on the cubic's end.
- **`MonoCubic`** is `FlattenMonoCubic`: subdivision in halves on a stack 32
  deep until the larger of the two control points' squared distances from
  the chord is within the tolerance -- 0.2 pixel, 51.2 in fixed point,
  squared -- times the chord's squared length.
- **Clipped flattening** is `flattenUnsafeMonoCurve`, which subdivides only to
  find where a piece enters and leaves the clip and adds border edges for the
  rows it spends outside. Its `goto`s between its three loops -- above the
  clip, before its near side, and visible -- are returns naming which loop to
  enter and where.

The arcs call glibc's `sin` and `cos`, and the port calls `StrictMath`'s; the
two agree on every angle in the table. 177 rows cover circles from a radius of
0 to one far past the image, negative radii, ellipses from a corner and as a
circle with two radii, rounded boxes whose radii are capped, zero, negative or
past the sides, arcs open, pie and chord through every quadrant and past a
full turn, cubics with loops, cusps and inflections, curves clipped on each
side and under rotations, and two curves under thirteen operators. A
tolerance of 0.21 in place of 0.2 fails 13 rows; dropping the border edges a
clipped curve adds fails 17.

## The stroker

`PathStroker` is `pathstroke.cpp`. It strokes in user space -- the release
leaves `transformOrder` at `AFTER` -- and hands each figure to the sink as three
paths: A, the outer offset with its end cap; B, the inner offset; C, the start
cap. The edge builder adds A and C as open paths and B reversed, as the
release's `strokeGeometrySink` does, so the three close into one outline.
`EdgeBuilder.addReversedPath` rebuilds the path `EdgeSourceReversePathFromStrokeSink`
would read backwards as a forward one, so there is one edge source.

The context's defaults are the stroke's: width 1, miter limit 4, join
miter-clip, butt caps, simplify tolerance 0.05, offset parameter 0.414213562.
Curves are offset as Blend2D offsets them: a cubic is approximated by pairs of
quads in steps from `pow(...)`, each quad is cut at its offset cusps (from
`cbrt`) and by angle, and each piece is offset as one quad. So the edge builder
now flattens quads too (`MonoQuad`, `QuadSpline`).

Two lerps are in the C and both are kept: `(a - t*a) + t*b` for a general `t`
and `0.5*a + 0.5*b` for a midpoint. The extension's own quirks carry over:
`pen` given an image sets the fill style, not the stroke's; `line-width 0`
turns stroking off without changing the width; an end cap past 5 sets the
start cap and then fails the command.

**The C library's last bits.** Blend2D calls the platform's `sin`, `cos`,
`pow` and `cbrt`; JEBOL calls Java's `StrictMath`, which gives the same bits
on the JVM and in the web build. On this machine `StrictMath` disagrees with
glibc 2.43 in the last bit for 3.4% of `sin` and `cos` inputs, 10% of `pow`
and 54% of `cbrt`, and glibc's `cbrt` is not correctly rounded, so no
correctly rounded routine would match it either; glibc's code is LGPL, which
JEBOL does not take. A last-bit difference changes a pixel only where it moves
a coordinate across a 1/256-pixel boundary. In 1,095 rows that happens twice,
both in the stroke of one closed cubic whose quad steps come from `pow`, and
the table keeps both rows, checked to draw without error but not pixel for
pixel. R3 itself differs this way between platforms: on Windows it calls
Microsoft's library.

## Gradients and patterns, close rather than exact

Everything else here is Blend2D's own arithmetic; gradients and patterns are
not. Porting Blend2D's gradient lookup table, its JIT fetchers and its
compositing of a source that varies per pixel would follow several thousand
more lines of C, and the decision was to draw them plainly instead.
`Blend2DGradient` and `Blend2DPattern` give the colour at a point in user
space, and `PaintedFill` hands each pixel's colour to the same compositing a
solid colour goes through, so every operator, mask and alpha still behaves as
Blend2D's.

What they keep of Blend2D's behaviour:

- The geometry of each gradient: linear from one point to another, radial
  with a centre, a focal point and a radius, conic from a centre and a start
  angle in radians with a repeat count. Linear and radial pad past their
  ends, conic repeats, as the extension creates them.
- The stops: an offset outside 0 to 1 is refused rather than clamped, a stop
  goes after every stop at or before its offset, and a third stop at one
  offset replaces the second, so two make a sharp edge.
- The style's transform is the one in force when `fill` sets the style, not
  when a shape is drawn, because `setStyleImpl` builds the fetch data then.
- Patterns sample bilinearly, Blend2D's default pattern quality, with the nine
  extend modes the extension's words name, and their pixels are taken as
  premultiplied, as the extension hands R3's memory to Blend2D as PRGB32.

`blend2d-paint.tsv` holds 65 cases drawn by the extension's C, with every
pixel kept; `Blend2DPaintFromTheSourceTest` expects JEBOL within 2 levels a
channel of each. That holds for every gradient kind, stop list and transform
in it, and for an opaque pattern under every extend mode, a fractional
translation, a scale and a rotation.

One thing it does not hold for, and does not try to: a translucent R3 image
used as a pattern. R3 images are straight alpha and the extension passes them
as premultiplied, so a colour above its alpha is invalid data to Blend2D,
which composites it with byte arithmetic that wraps. JEBOL composites the same
data with the saturating arithmetic it uses everywhere. Both draw something
odd; they draw different odd things.

## Shape and path

`shape` takes a block of the path dialect, or a handle `path` made from one;
`b2d_init_path_from_block` reads both, and `Blend2DDrawing.buildPath` is it.
The dialect shares `draw`'s command words and its recovery -- a word that is
not a command, or a command whose arguments do not fit, sends the walk back to
just after the last command and on to the next command word -- but its
remembered position starts at 0 where `draw`'s is undefined. Its commands
call Blend2D's path functions, ported in `Blend2DPath`:

- `move`, `line` (one resolved pair, then raw pairs), `curve` (cubics in
  threes), `qcurve` (quads in twos), and `close`.
- `curv` and `qcurv` are `smoothCubicTo` and `smoothQuadTo`: they reflect the
  previous control point only when the command before the last point was a
  cubic or a quad, and do nothing on an empty path or after `close`.
- `hline` and `vline` read the last vertex as `getLastVertex` does: after
  `close` they walk back to the figure's `move`, and on an empty path they read
  0x0, which the C then uses although the call failed.
- `arc` is `ellipticArcTo`, SVG's arc: its end point, two radii and a rotation
  in degrees, then `sweep` and `large` in that order. Radii too small for the
  chord are scaled up, radii at or below 1e-14 make a line, an arc ending where
  it starts draws nothing, and the arc is quarter-turn cubics and a last conic
  written as a cubic. It calls `atan2` as well as `sin` and `cos`, with the
  last-bit caveat above.
- A `line` to a path with no `move` appends a bare point, as Blend2D does;
  filling skips it, and stroking a figure that does not begin with a move
  draws nothing.

92 rows cover the dialect, each shape filled, stroked, and filled and
stroked: every command, smooth curves after and not after their own kind, both
arc flags and their combinations, rotated and degenerate arcs, `hline` and
`vline` after `close` and on an empty path, words that are not commands, a
second figure, an empty block, and `shape` given a number or an unknown word.
Swapping `sweep` and `large` fails 13 rows; nudging the smooth quad's
reflection by a hundredth fails 3.

269 rows cover strokes: 35 shapes at four widths, each under a join and a cap
taken in turn, every join on four angles, every cap on an open and a bent
line, turns from 20 to 40 degrees either side of the miter limit under the
three miter joins, outlines over fills, under rotations, a non-uniform scale,
a clip and a translation, `pen off` and `line-width 0`, invalid caps, and
thirteen operators. A miter limit of 3.9 in place of 4 fails 13 rows; a cap
half a hundredth too wide fails 74.

---

## The walk

`Blend2DDrawing` is `cmd_draw` line for line. Every command reads its
arguments as the C does, so a block flows the same way through what JEBOL
cannot draw yet: a shape read with neither a fill nor a pen consumes its
arguments and draws nothing, as in the C, and only a shape that would put down
pixels raises.

Things the C does that the walk keeps:

- **A failure goes back, not forward.** A command whose arguments do not fit
  sets the position back to just after the last command word read (`cmd_pos`)
  and scans forward from there for the next command word. A block that opens
  with something that is not a command word reads `cmd_pos` before the C sets
  it; GCC 15.2 at `-O2` makes it 3, and JEBOL takes 3.
- **`fetch_mode` says yes to anything that is not a word or an integer**, and
  past the tail, answering the largest mode. So `line-join 5x5` takes the pair
  and joins round, and `blend` as the last word of a block sets `exclusion`.
- **A word is looked up through its binding and a path is evaluated**
  (`RL_GET_VALUE_RESOLVED`), so `fill red` works and `fill linear` reaches the
  gradient only because `linear` is unset.
- **`pen` given an image sets the fill**, not the stroke, and `image` given
  `x` or a pair makes Blend2D refuse the image, which ends the whole `draw`
  quietly (`goto end_ctx`).
- **`image` the command, given a pair, always fails**: the C reads the pair's
  two floats as the integers `width` and `height`, and Blend2D refuses the
  size. `image 3x2` is `Blend2D failed to make an image handle!` on a real
  3.22.5 too.

## Boxes, clips and the whole image

Blend2D maps a box through the transform in 24.8 fixed point, clips it, and
truncates it (`fillUnclippedBoxD`). A box on whole pixels is `BoxA`, the
constant-mask path at the global alpha. Any other goes through
`initBoxU8bpc24x8` (`UnalignedBoxFill`), which folds the global alpha into
its masks and lays a scanline out as constant spans and runs of per-pixel
masks: one constant span when the box is a pixel wide or its sides are on
whole pixels; otherwise all per-pixel masks when the row, padded left to a
multiple of four, is at most 20 wide, and four per-pixel masks, a constant
middle and a per-pixel tail when it is wider. Which pixel takes which path
matters, because under a global alpha the two paths disagree for the
operators that do not scale the source by the mask. A row whose mask rounds to
0 is skipped; a box one row high whose bottom mask rounds to 0 draws nothing.

A transform that rotates or skews makes a box a polygon, for the rasteriser. An
invalid one (`scale 0 0`) draws nothing.

`clip` maps its rectangle through the whole transform (`mapBox`), narrows the
clip, and is aligned when every coordinate is a whole pixel; `fill-all` then
paints the clip as `BoxA` or as an unaligned box. `clip off` restores the whole
image but not the alignment, which Blend2D forgets to reset; the two paint the
same. `clear` and `clear-all` are source-copy of transparent black at full
alpha, whatever the global alpha.

Every rectangle the extension builds is `x`, `y`, `x2 - x`, `y2 - y` from the
pairs' 32-bit floats, and Blend2D adds `x + w` back: the round trip can move
the last bit, and JEBOL does it the same way.

---

## Which Blend2D the release builds

**The `.nest` takes Blend2D and asmjit from their default branches**, so the
release builds whatever they held when its CI ran, 2025-04-04 17:58 UTC: Blend2D
`bae3b6c6` (29-Mar-2025) and asmjit `e8c8e2e4` (30-Mar-2025). The extension's own
C is the `0.12.0` tag, `53f6ffe9`.

**The release renders with Blend2D's JIT pipeline**, and its pixels are not the
reference pipeline's. The reference pipeline implements three compositing
operators, source-copy, source-over and plus; every other one exists only in the
JIT's `pipeline/jit/compoppart.cpp`. Over the 28 `blend` modes on a
semi-transparent image, the two pipelines agreed on source-over, source-copy and
clear and differed by up to 225 in a channel on every other. So the formulas are
read from the JIT, and checked against the probe running with JIT.

---

## What the extension does

**The module.** Its header says `Version: 0.11.5.0` though the release is 0.12.0,
and `Exports: [draw]`, so only DRAW reaches lib. It holds `draw`, `path`, `font`,
`image`, `info`, `set-threads` and the hidden `init-words`, and a `words` field:
`init-words words: [...] [...]` sets it to the block of command words on import.

**DRAW onto an image changes that image** and answers it; DRAW onto a pair makes a
new image, opaque white as R3's `Make_Image` fills it. A size past 65535 makes
`Make_Image` answer null and the C dereferences it.

**The image's memory is read as premultiplied.** Blend2D is handed R3's pixels as
`BL_FORMAT_PRGB32`, and R3 keeps them straight, so a pixel that is not opaque is
drawn over as if its colour had already been multiplied by its alpha.

**A command it cannot read is skipped, not raised.** Any word that is not a
command, or a command whose arguments do not fit, sends the walk forward to the
next word that is a command, and everything between is ignored.

**`box`'s second pair is a size, not a corner.** `box 2x1 6x4` fills six by four
from 2x1, whatever the C's comment says.

**Arguments are resolved.** A word in the block is looked up (`fill red`); a word
with no value is `unset!`, which is how `fill linear ...` reaches the gradient.

**A pair is two 32-bit floats** when it reaches the C, and an image's size two
16-bit bitfields.

**`set-threads` reads argument nought**, the frame's count and type bytes, not its
count, and answers the count. **`info/of` on an image prints `heigth:`.**

---

## How the pixels are split

They are not split: the domain does all of it, as decision 59 says. The first
plan let the platform answer coverage, and it could not have matched even
interiors, because which path a covered pixel takes is the rasteriser's.

---

## Compositing

**The pipeline works on four 16-bit lanes a pixel**, blue, green, red, alpha,
and the port keeps them in a `long` (`Lanes`) with the x86 instructions'
behaviour exactly: products keep their low 16 bits, sums wrap, `div255` is
`((x + 128) * 257) >> 16` on the wrapped sum, `inv255` is `x ^ 255`, a pack is
signed saturation to a byte, and darken's and lighten's minimum and maximum are
taken byte by byte across the lane. R3's pixels are not premultiplied, so a
colour above its alpha is ordinary input and every one of those rules is
reached.

**There are two paths and they round differently.** A constant mask --
`fill-all`, a box, the inside of a shape away from its edges -- takes
`cMaskInitRGBA32` and `cMaskProcRGBA32Vec`, which precompute from the solid
colour (`ConstantMaskCompositor`). A mask that varies from pixel to pixel takes
`vMaskProcRGBA32Vec` (`VariableMaskCompositor`). Source-over, for one, is
`(d * (255 - Sa) + 255 * S + 128) * 257 >> 16` on the first and
`div255(d * (255 - Sa)) + S` on the second. Operators the constant path has no
case for scale the source by the mask and go through the variable path without
one, as the JIT's `_solidPre` does.

**Through a mask, a fully covered pixel under a global alpha takes the variable
path** with the alpha as its mask; only full coverage at full alpha takes the
constant one. The mask is `div255(coverage * alpha)`.

**The float operators are the JIT's float code.** Colour-dodge, colour-burn and
soft-light compute in single precision with the JIT's order of operations, its
`max(x, 0.001)`, and soft-light's fused multiply-add, which the JIT emits on a
CPU with FMA. TeaVM has no float `Math.fma`, so `FusedMultiplyAdd` is musl's
`fmaf`: the exact product in double, then one correction for a sum that lands
exactly halfway between two floats; it agrees with the JVM's `fma` bit for bit. A float past the range of an int converts to `0x80000000`, which
the following unsigned pack makes 0; colour-burn reaches it whenever a
destination colour is above its alpha and the source channel is 0.

**The operator is simplified first**, as `compopsimplifyimpl_p.h` does for a
PRGB32 target (`SimplifiedOperator`): a solid colour is ZERO32 when it is
`0x00000000`, FRGB32 when its alpha is 255 and PRGB32 otherwise, so source-over
of an opaque colour is source-copy, clear is source-copy of transparent black,
and source-over of transparent black draws nothing. A colour of alpha 0 that is
not 0 is PRGB32 and is drawn, as 0. A global alpha that scales to 0 draws
nothing; it scales by `alpha * 255` rounded half up (`roundToInt`).

**What this is checked against.** `table.c` under
`src/testFixtures/resources/blend2d/` fills noise through the release's JIT,
and its README lists the boundaries. Before the tables were cut to 64x64, the
same generator at 256x256 agreed on all 6,264 fills and 1,160 masked fills,
every pixel.

---

## The reference

The probe (in a scratch directory, not checked in) compiles the extension's own
`blend2d-command-*.c` unchanged against a stand-in for R3's `RL_` table and a
reader of a small subset of REBOL, linked to Blend2D built with GCC 15.2 at `-O3`
as a static library with JIT. `-Wl,--wrap=blContextInitAs` lets the same probe run
with JIT disabled, which is how the two pipelines were compared.
