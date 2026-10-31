# The blend2d port

R3's `system/modules/blend2d` names the 0.12.0 release of
Siskin-framework/Rebol-Blend2D. JEBOL serves it in Java as decision 58 says. This
note carries what the code cannot: what the extension does, which Blend2D it
builds, how the port is checked, and how the pixels are split between the domain
and the platform.

Work in progress: the compositing is ported and checked; the rasteriser, the
gradients, the patterns, the text and the extension's commands are not yet.

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
CPU with FMA. A float past the range of an int converts to `0x80000000`, which
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
