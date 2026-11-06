# The Zstandard port

Rebol-Zstd 0.1.0, the extension R3's `system/modules` names at
`https://github.com/Oldes/Rebol-Zstd/releases/download/0.1.0/`, wrapping
Zstandard. JEBOL serves it in Java as decision 58 says. This note carries what
the code cannot. The decoder, the encoder at every level and both streams are in
`org.jebol.domain.eval.compression.zstd`, and the module, its seven commands and
its two handles are `ZstdExtension` and `ZstdStream`. The tables in
`src/testFixtures/resources/zstd/` hold zstd's own answers, and the Java gives
every one of them octet for octet.

---

## Which Zstandard

**The release does not pin Zstandard.** Its `Rebol-Zstd.nest` says
`github: facebook/zstd`, and Siskin clones the repository's default branch,
which is `dev`, not a release. The newest `dev` commit before the 0.1.0 assets
were uploaded on 22 December 2025 is `ae9f20ca2`, which calls itself 1.6.0.

**JEBOL follows the v1.5.7 tag instead**, by the project's choice on
2026-11-06: a tagged release, 39 library commits short of what the binaries
probably hold. Where `dev` differs from 1.5.7 and a script can see it, `version`
first, the difference is JEBOL's and is recorded here when it is ported.

Zstandard is dual-licensed, BSD or GPLv2. JEBOL takes the BSD licence alone, and
the ported files stay under it (`NOTICE`).

---

## What the decoder does that a clean decoder would not

**Huffman decoding depends on the CPU.** `ZSTD_decodeLiteralsBlock` passes
`HUF_flags_bmi2` where the CPU has BMI2, and only then does a four-stream
decode try the fast loop (`HUF_decompress4X1_usingDTable_internal_fast` and its
X2 twin), falling back to the slow one where it cannot apply. Without BMI2 the
fast loop is never tried. The fast loop's last check is that each output segment
is exactly full; it never asks whether the stream's bits ran out, which the slow
loop does. JEBOL follows a machine with BMI2, as the release on any x86-64 since
2013 does.

**The fast loop applies only at table log 11.** A single-symbol table is rescaled
up to log 11 when its own log is smaller, and left at 12; a double-symbol table
is built at 11 when the weights allow it. Each of the four streams must also be
at least eight octets long, and the fourth output segment must start before the
end.

**The four slow loops write two octets per double symbol** and can write one
octet into the next stream's segment, which that stream has already written.
JEBOL keeps the same writes in the same order.

**The double-symbol decoder's last symbol clamps the bits consumed at 64**
(`HUF_decodeLastSymbolX2`), so it can accept a stream the single-symbol decoder
refuses. Which decoder runs is `HUF_selectDecoder`'s guess at which is faster,
from the sizes alone.

**The weights of a Huffman table are FSE-decoded in a workspace sized for weights
up to 11** (`HUF_READ_STATS_WORKSPACE_SIZE_U32`). A weight header naming more
symbols at FSE table log 6 is refused as `tableLog_tooLarge`, though log 6 is
allowed.

**The default sequence tables are hard-coded in the C** as
`LL_defaultDTable`, `OF_defaultDTable` and `ML_defaultDTable`. JEBOL builds them
from the default distributions, as the C's comment says they were made, and
`ZstdSequenceTableTest` checks every cell against the C's copy.

---

## What the decoder does that a clean one would not

- `ZSTD_decompressStream` decodes a whole frame in one pass, into the caller's
  buffer, when the frame's content size is known, the room left is at least that,
  and the whole frame is in the input. That pass never checks the window against
  the 2^27 limit the streaming pass enforces.
- After 16 calls with no progress it fails, with `noForwardProgress_destFull` or
  `noForwardProgress_inputEmpty`.
- Its memory is modelled, not its meaning: literals sit in the output buffer,
  in its extra buffer or split between them exactly where the C puts them, and
  the 8-, 16- and 32-octet copies write what the C's wildcopies write, so a
  damaged frame whose matches run over literals not yet copied, or over the
  wrapped window's history, decodes to the same octets as in C.

## What the encoder does that a clean one would not

- **A reused context keeps its tables.** When a stream starts its next frame
  with the same workspace, zstd continues its indexes rather than clearing its
  tables, and the row matchfinder moves its hash salt on, mixing in every hash
  it looked up. A second frame from one encoder handle can therefore differ from
  the same input through a fresh one. JEBOL models the workspace's table region
  as one array, as the C lays it out, and the salt as the C computes it.
  Whether the workspace is resized, which resets everything, follows
  `ZSTD_estimateCCtxSize_usingCCtxParams_internal` to the octet.
- **`ZSTD_ldm_gear_reset` does nothing.** It rolls the hash into a local and
  never stores it, so long-distance matching starts every chunk, and restarts
  after every match, from the hash it already had.
- **The optimal parser's shortest path has `} {` where `} else {` was meant**,
  so the last literals' bookkeeping is always overwritten. Ported as written.
- **A literal or sequence header cost of an error is still a number.** Where
  `HUF_writeCTable` fails inside the block splitter's estimate, the C adds the
  error code to sizes as an unsigned number before noticing; JEBOL does the same
  sums.

## What the extension does

- `compress` defaults to level 6. COMPRESS with `'zstd` and no `/level` passes
  R3's unknown level, 0xFFFFFFFF, which the C clamps to 22.
- `version` packs `ZSTD_versionNumber()` 10507 into a tuple's octets as
  `(n >> 24)`, `(n >> 12)` and `n`, each truncated to a byte, so it answers
  0.2.11.
- The one-shot `decompress` and the registered method share one decoder for the
  whole session, re-initialised each call, so its buffers carry over. It stops
  at the end of the first frame and starts with room for twice the input, or the
  limit given. When the room fills it expands the binary by its whole rest and
  gives zstd `SERIES_AVAIL` as the buffer's size, which is the room after the
  tail rather than the room in all; JEBOL grows the series as `Expand_Series`
  does (`ExpandingBinary`) and passes the same size.
- **Where JEBOL differs:** WRITE on an encoder calls `ZSTD_compressStream` until
  the input is consumed without ever growing the output, so a write whose
  compressed form outgrows the binary's rest spins forever in C. JEBOL raises
  the extension's own compress error instead of hanging.
