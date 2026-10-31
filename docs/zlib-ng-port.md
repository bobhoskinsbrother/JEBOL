# The zlib-ng and deflate ports

Two of R3's `system/modules` entries are DEFLATE: `deflate` 0.1.0
(Oldes/Rebol-Deflate, wrapping libdeflate) and `zlib-ng` 2.3.2
(Oldes/Rebol-Zlib-ng, wrapping zlib-ng 2.3.2). JEBOL serves both in Java as
decision 58 says. This note carries what the code cannot: what each extension
does, how the ports were checked, and the shape the code keeps on purpose.

---

## Which library each extension builds

**deflate builds libdeflate 1.25.** Its `.nest` names `github: @libdeflate`
with no tag, so Siskin builds whatever libdeflate's default branch held when the
release was made, 21-Dec-2025. The last commit before that date is the `v1.25`
tag of 1-Nov-2025, and the only compressor change since `v1.24` widens a match
length and offset to `u32`. R3 itself vendors libdeflate 1.25 under
`rebol3-source/src/core/deflate/`, and JEBOL's `DeflateCompressor` is already a
byte-exact port of it. So the deflate extension needs no encoder of its own.

**zlib-ng builds zlib-ng 2.3.2**, pinned as `github: zlib-ng/zlib-ng /2.3.2`, with
CMake and runtime CPU detection on Linux.

**zlib-ng's output does not depend on the CPU.** Built with `--without-optimizations`
and with its SIMD paths (AVX2, PCLMULQDQ, VPCLMULQDQ on the machine that checked
it; AVX-512 was not available to test), 1,690 compressions -- every level 0 to 9,
all five strategies, windows 9, 12, 15, -15 and 31, memLevels 1, 8 and 9, streams
flushed every thousand and every 65,536 bytes -- were the same bytes. So
"zlib-ng's bytes" is one answer, and the port has one target.

---

## What each extension does on import

**Both re-register the three DEFLATE methods.** `RX_Init` calls
`RL_REGISTER_COMPRESS_METHOD` for `deflate`, `zlib` and `gzip`, and R3's
`Register_Compress_Method` replaces the handlers of a method already known. So
after `import 'zlib-ng`, `compress data 'zlib` is zlib-ng's bytes, not libdeflate's,
and after `import 'deflate` it is libdeflate's again. The last import wins.

**zlib-ng also adds three names**, `deflate-ng`, `zlib-ng` and `gzip-ng`, which
R3 appends to `system/catalog/compressions`.

**A registered method gets the level as R3 holds it.** The native passes
`VAL_UNT32` of `/level`, or `UNKNOWN`, `(REBLEN)-1`, without it. libdeflate clamps
anything above 12 to 12; zlib-ng's `CompressCommon` turns `NO_LIMIT` into
`Z_DEFAULT_COMPRESSION` (6) and anything else above 9 into 9. So a negative level
is 12 for one and 9 for the other.

**deflate appends `,deflate` to the http scheme's `Accept-Encoding`** unless it is
there, which it always is. zlib-ng's edit is commented out at 2.3.2.

**zlib-ng's module holds `Z_FILTERED: 1`, `Z_HUFFMAN_ONLY: 2`, `Z_RLE: 3` and
`Z_FIXED: 4`.**

---

## What the commands do that the names do not say

**Both modules' `compress` default to level 0, which stores.** `cmd_compress`
reads `RXA_REF(frm, 4) ? RXA_INT32(frm, 5) : 0`. libdeflate's level 0 and
zlib-ng's both write stored blocks, so `deflate/compress data` and
`zlib-ng/compress data` do not compress. The native `compress` asks for the
default level instead.

**deflate's `decompress/size` must be exact.** It calls libdeflate with
`actual_out` NULL, which fails unless the output is exactly that long, as the
native does for these three methods. A size of 0 answers an empty binary at once.

**zlib-ng's `decompress/size` cuts the output short instead**, and decodes only
as far as the room R3 gives its binary: `Make_Binary(limit)` rounded up by R3's
pools, so damage past that room goes unseen. A size of 0 decodes everything,
because `limit && ...` is false.

**Every zlib-ng streaming encoder is level 1.** `make_stream_handle` computes
`MIN(1, MAX(9, level))`, which is 1 whatever was asked, so `make-encoder/level 9`
and both wrapped encoders compress as zlib-ng's `deflate_quick`.

**`make-decoder/window` does nothing.** It reads `RXA_REF(frm, 3)`, but the
decoder's `/window` is the first refinement, so every `make-decoder` is a raw
decoder with a 15-bit window.

**`write` without `/flush` or `/finish` answers the codec handle**, because the
command returns its first argument. Writing twice with nothing new and no flush
is zlib-ng's `Z_BUF_ERROR`, which the command reports as a failure.

**A decoder cannot `/finish` past its buffer.** `inflate` with `Z_FINISH` answers
`Z_BUF_ERROR` whenever it runs out of room before the end, and `cmd_write` reports
every negative result as "Failed to compress using the Zlib encoder!", the
encoder's message, for a decoder too. The buffer starts as large as the first
write's input, so a decoder finished on a write whose output outgrows that fails,
where `write/flush` grows the buffer and succeeds.

**The encoder's buffer grows by a guess that can underflow.** When the output
fills the room, `cmd_write` grows the buffer by `(total_in - total_out) / 3`, in
unsigned arithmetic, and `total_out` is set back to nought at every flush while
`total_in` is not. Output that has caught up with its input makes the guess wrap
to about 1.4 gigabytes. R3 allocates that, and the encoder then writes a second
empty stored block where the first flush filled the room exactly. A guess with its
top bit set is `past-end`, as `Expand_Series` traps it.

---

## Where JEBOL answers otherwise, on purpose

**Room past 256 megabytes is `no-memory`.** Where the underflowed guess asks for
more than that, JEBOL refuses rather than giving a script's buffer a gigabyte of
heap. R3 gives it.

**A buffer's growth after the first is a guess at R3's.** `Expand_Series` doubles
a series it expanded recently, judged by a global list of the last sixteen series
expanded anywhere in the interpreter. JEBOL doubles a stream's buffer from its
second growth on, as R3 does when nothing else was expanded between. The bytes do
not depend on the room except where the output fills it exactly at a flush.

**A dictionary-asking stream is a failure.** A zlib stream with `FDICT` makes
zlib-ng's `inflate` answer `Z_NEED_DICT`, which is neither an error nor `Z_OK`, so
`cmd_write`'s loop calls it again for ever. JEBOL reports it as "Failed to
decompress using the Zlib decoder!".

**The headers carry no `Date:`.** Each extension writes `now/utc` into its header
when it is built, and no build's date can be known without running it. The rest of
each header is the extension's own.

---

## How the port was checked

The C was fetched as source, read, and compiled in a scratch directory twice:
`./configure --static` and `./configure --static --without-optimizations`. No
release binary was run.

**The encoder.** A C driver and a Java driver read the same inputs and the same
list of 604 cases -- level, window, memLevel, strategy, input chunk, flush mode,
and output handed out whole or 1, 7 or 100 bytes at a time -- and print each
output's length and FNV-1a digest and a digest of every return code. Over
zlib-ng's own corpus (`lcet10.txt`, `paper-100k.pdf`, `fireworks.jpg`), slices of
`lcet10.txt` either side of 1 to 5, 257 to 263, 16,384, 32,768 and 65,536 bytes,
and six megabytes of R3's C, a megabyte of noise and a megabyte of zeros, 17,952
compressions agreed byte for byte and code for code.

**Level 0's bytes depend on the room given.** `deflate_stored` sizes its blocks by
`avail_out`, so handed a byte at a time it writes a block per byte and outgrows
any buffer. The extension always gives at least `deflateBound + 1`, where the
blocks are the same whatever the room.

**The decoder.** A generator made 2,140 streams: raw, zlib and gzip at levels
0 to 9 and every strategy, a gzip header with every optional field, one with a bad
header CRC, reserved flags and a wrong method, zlib headers asking for a
dictionary, a 16-bit window and a wrong method, trailing data and two members,
and each valid stream cut at seven points and with a bit flipped at up to sixty
places. Decoded under every window, fed whole and 1, 7 and 1,000 bytes at a time,
with output handed out whole and 1 and 300 bytes at a time, 183,120 decodes agreed
on every return code, total and byte: 148,416 `Z_DATA_ERROR`, 15,960
`Z_BUF_ERROR`, 18,696 ends and 48 `Z_NEED_DICT`.

---

**What the gate runs.** `src/testFixtures/resources/zlib-ng/` keeps two tables
made by the same C over `DeflateFixtures`' inputs at every boundary its README
lists, 6,892 compressions and 11,520 decodes, and `ZlibNgFromTheSourceTest` replays
them. `TheZlibNgModuleFromTheSourceTest` and `TheDeflateModuleFromTheSourceTest`
check what a script sees, with the bytes read off a C probe of the same calls.

---

## The shape the code keeps

`ZlibNgDeflateState`, `ZlibNgTrees`, `ZlibNgMatches`, `ZlibNgStrategies`,
`ZlibNgInflater` and `ZlibNgInflateTable` keep the C's shape and its names, and
carry a one-line signpost saying so. Four things in them look tidiable and are not:

- **`ct_data` is two unions**, and the trees lean on them: `gen_codes` writes a
  code over the frequency it was built from, and `gen_bitlen` writes a length over
  the parent it was read from. Each tree is two arrays, `FreqOrCode` and
  `DadOrLen`, so the overwrites happen as the C's do.
- **The bit buffer is 64 bits and flushes as the C's does.** The bytes are the
  same however a bit writer buffers, but `deflate_stored` sizes a block by
  `bi_valid` and `deflate_quick` flushes on the pending count, so both are kept.
- **`longest_match` walks the chain inside the filter loops** without the outer
  loop's `cur_match >= strstart` check, because `GOTO_NEXT_CHAIN`'s `continue`
  lands in the inner loop.
- **The window outlives `deflateReset`.** zlib-ng keeps the window, `prev` and
  `high_water` across a reset, so an encoder reused after `/finish` still holds
  the last stream's bytes past the new data, and `longest_match` can choose a
  different distance by them.

The decoder ports zlib-ng's slow path only. `inffast` is a speed-up that answers
the same output and the same errors, and the slow path decodes as far as the
input allows, so where a stream stops does not depend on which path ran.

## Licence

zlib-ng is under the zlib licence, "(C) 1995-2024 Jean-loup Gailly and Mark
Adler". Its second condition is that an altered version be plainly marked as one:
each `ZlibNg*` file says in its signpost that it is zlib-ng's C converted, and
NOTICE says so again. Those files stay under the zlib licence. Rebol-Deflate and
Rebol-Zlib-ng are MIT (Copyright (c) 2025 Oldes; Rebol-Zlib-ng carries the licence
as an `SPDX-License-Identifier` line rather than a file), and the extension
classes written from their commands stay under MIT.
