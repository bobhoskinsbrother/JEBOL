# The blend2d port

R3's `system/modules/blend2d` names the 0.12.0 release of
Siskin-framework/Rebol-Blend2D. JEBOL serves it in Java as decision 58 says. This
note carries what the code cannot: what the extension does, which Blend2D it
builds, how the port is checked, and how the pixels are split between the domain
and the platform.

Work in progress: the reference is built and the extension's C read; the port is
not started.

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

Decision 35 keeps rasterising out of the domain. So the platform answers one
question -- how much of each pixel a shape or a run of glyphs covers -- and the
domain does the rest as arithmetic ported from Blend2D: the compositing
operators, global alpha, solid colours, gradients and patterns. Interior pixels
then match Blend2D exactly and only the anti-aliased edges are the platform's.

---

## The reference

The probe (in a scratch directory, not checked in) compiles the extension's own
`blend2d-command-*.c` unchanged against a stand-in for R3's `RL_` table and a
reader of a small subset of REBOL, linked to Blend2D built with GCC 15.2 at `-O3`
as a static library with JIT. `-Wl,--wrap=blContextInitAs` lets the same probe run
with JIT disabled, which is how the two pipelines were compared.
