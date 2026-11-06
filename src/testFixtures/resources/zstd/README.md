# What zstd answers in its own C

Tables of what zstd v1.5.7 answers, computed against zstd's own `lib/` sources
compiled with gcc 15.2 on x86-64 Linux, with `-O2 -DZSTD_DISABLE_ASM` on a CPU
with BMI2. Five come from `table.c` and test the decoder's pieces
(`ZstdDecodingFromTheSourceTest`), one from `frames.c` tests whole streams
decoded (`ZstdStreamDecodingFromTheSourceTest`), and two from `enc.c` test the
encoder at every level (`ZstdCompressionFromTheSourceTest`). Every row is
expected exactly.

Every Huffman call passes `HUF_flags_bmi2`, as `ZSTD_decodeLiteralsBlock` does on
a CPU with BMI2, so the four-stream decoders take their fast loop where it
applies. With assembly disabled that loop is zstd's C one, which the release's
`huf_decompress_amd64.S` mirrors. Without BMI2 zstd never takes the fast loop,
and the fast loop accepts streams the slow one refuses, so the tables are the
answers of a machine that has it.

Inputs are made in `table.c` from a xorshift sequence and checked in as hex,
since JEBOL has no Huffman or FSE encoder to make them again. Digests are 64-bit
FNV-1a. An error is `e` and zstd's error code.

## zstd-bits.tsv

660 rows. A random buffer of 1 to 40 octets, opened by `BIT_initDStream`, then up
to 40 random steps of look, fast look, read, fast read, reload, fast reload and
skip, each recording the value, the reload status, the bits consumed, the
pointer and whether the stream has ended. Once more than 64 bits are consumed the
next step is a reload, which overflows, and the row stops: zstd's decoders never
fast-reload past 64.

## zstd-ncount.tsv

3,354 rows. A normalized count made by `FSE_normalizeCount` and `FSE_writeNCount`
from a random histogram, then read by `FSE_readNCount` whole, cut by one octet,
cut in half, with a bit flipped seven-thirteenths in, with its table log
damaged, and with two stray octets after it. Some rows ask for fewer symbols
than the header carries, and some for 255.

## zstd-huffman-stats.tsv

855 rows. Each Huffman header the compressions below wrote, read by
`HUF_readStats` whole, one octet short and with a bit flipped.

## zstd-huffman.tsv

2,975 rows. Literals of six kinds -- three symbols, sixteen, sixty-four, all 256,
a geometric run and one symbol with noise -- compressed by `HUF_compress1X_repeat`
or `HUF_compress4X_repeat` at table logs 11 and 12, then damaged in seven ways:
whole, cut by one octet, a bit flipped seven-thirteenths in, the top bit of the
last octet flipped, decoded one octet too long, one octet too short, and with a
stray octet after it. One stream is decoded by `HUF_decompress1X1_DCtx_wksp` and
`HUF_decompress1X2_DCtx_wksp`, four by `HUF_decompress4X_hufOnly_wksp`.

## The boundaries, and what covers each

Read from the C:

| Boundary | Where in the C | Covered by |
| --- | --- | --- |
| a bitstream under 8 octets | `BIT_initDStream` builds the container by hand and its missing end mark is `corruption_detected` | bits rows at 1, 2, 3 and 7 |
| a bitstream of 8 or more | the end mark is `GENERIC`, and reloads read whole words | bits rows at 8, 9, 15 to 40 |
| a last octet of 0 and of 1 | no end mark, and an end mark with nothing above it | every seventh and fifth bits row |
| more than 64 bits consumed | `BIT_reloadDStream` overflows and stays overflowed | bits rows that end in an overflow |
| a count header under 8 octets | `FSE_readNCount` copies it into 8 zeroed octets | ncount rows cut short |
| table logs 5 to 12, and a damaged one past 15 | `tableLog_tooLarge` | ncount rows at every log, and the damaged-log rows |
| fewer symbols asked than present | the read stops early and the count is wrong | ncount rows asking one fewer |
| weights stored direct, header octet 128 and up | `HUF_readStats`' special header | stats rows with direct headers |
| weights compressed by FSE at log 6 | `FSE_decompress_wksp`, whose workspace fits weights up to 11 | stats rows with compressed headers |
| one, two, five octets of literals | below four-stream's minimum of 6 | huffman rows at 1, 2 and 5 |
| 6, 7, 8, 9 | `dstSize < 6`, and a segment of one or two | huffman rows at each |
| a stream under 8 octets | the fast loop falls back to the slow one | small four-stream rows |
| table log 11 against 12 | the fast loop needs `HUF_DECODER_FAST_TABLELOG` 11; X1 rescales up to it | rows at both logs |
| single against double symbols | `HUF_selectDecoder` chooses by size and ratio; X2 needs long, compressible literals | four-stream rows to 12,000 octets |
| 255, 256, 257, 1023 to 1025 | `HUF_selectDecoder`'s `D256` steps | huffman rows either side |
| the fast loop's leftover bits | it checks only that each segment is full, never that its stream ended | (none known: no row is known to be accepted by the fast loop and refused by the slow) |
| X2's last symbol | `HUF_decodeLastSymbolX2` clamps the bits consumed at 64 | (none known: no row is known to end on a pair) |

Each of the six paths has rows that succeed and rows that fail: one stream X1
and X2, and four streams X1 and X2, each fast and slow.

## zstd-frame-header.tsv

3,272 rows. Every frame descriptor octet with six window octets, read by
`ZSTD_getFrameHeader` whole and cut at a random length, then 200 short inputs:
zstd's magic and the skippable magics, whole and cut, and noise.

## zstd-frames.bin, zstd-frames.tsv and zstd-decode.tsv

Made by `frames.c`. `zstd-frames.bin` holds 89 frames end to end and
`zstd-frames.tsv` says where each starts, how long it is and how it was made:
zstd's encoder over the inputs below, at every level from -5 to 22, with and
without a checksum and a content size, at windows from 2^10 to 2^28; seven
files of zstd's own `tests/golden-decompression` and
`tests/golden-decompression-errors`, copied in whole; and one skippable frame.
`ZstdStreamDecodingFromTheSourceTest` checks the file's length and digest
before trusting it.

`zstd-decode.tsv` has 9,426 rows. Each frame, and each frame damaged — cut at
fourteen lengths, an octet xored at up to fourteen random places, random octets
appended, a second frame appended — is fed to a fresh `ZSTD_DStream` by
`ZSTD_decompressStream` on a schedule: everything at once into room for the
whole content, everything at once into room for half of it, random chunks of
input and output up to 2^17, and chunks of up to 15 octets. A row records the
calls, the input read, the output written and its digest, a digest of every
value `ZSTD_decompressStream` returned, and its last answer. A row stops at the
end of the frame, at a refusal, or after 32 calls in a row that read and wrote
nothing. That is past zstd's own limit of 16, so every refusal it would make is
still reached, and a stream that progresses at least once in 32 calls must end.

What a decoder does that a frame can steer, read from the C:

| Boundary | Where in the C | Covered by |
| --- | --- | --- |
| a frame decoded in one pass, into room for its content | `ZSTD_decompressStream`'s single-pass shortcut | every whole-room row |
| a frame decoded through the stream's own buffers | `zdss_load` and `zdss_flush` | every chunked row |
| the output buffer wrapping, the window becoming an extDict | `ZSTD_checkContinuity` | frames at windows 2^10 and 2^12 |
| literals in the output, in the extra buffer, split between them | `ZSTD_allocateLiteralsBuffer` | in_dst 805, not_in_dst 12,129, split 64 times in zstd's own run |
| the long-offset decoder, plain, split and in the output | `ZSTD_decompressSequencesLong` | 48 calls, 14 split, 4 in the output |
| a window over 2^27 | `frameParameter_windowTooLarge` from a stream, none in one pass | the 2^28 frame |
| a checksum, a dictionary id, a reserved bit | `ZSTD_decodeFrameHeader` | damaged rows: e22, e32, e14 |
| no progress for 16 calls | `noForwardProgress_destFull` and `_inputEmpty` | cut and half-room rows: e80, e82 |
| a frame cut inside its header | `zdss_loadHeader` returns its hint before the no-progress check, so it is never refused | the 2,109 cut rows stopped after 32 calls |
| a hostage octet held until the output is flushed | the end of `ZSTD_decompressStream` | chunked rows ending in 1 |

## zstd-compress.tsv and zstd-compress-stream.tsv

Made by `enc.c` from inputs `inputs.h` makes: zstd's own `RDG_genBuffer`
from `programs/datagen.c`, runs of one octet, two symbols, short random chunks
repeated 17 MB later, one octet with noise, a datagen block repeated, and noise
whose start repeats after a gap. `ZstdTestInputs` makes the same inputs in Java
and each row's input digest proves it.

`zstd-compress.tsv` has 1,529 rows: `ZSTD_compress`, as the extension's
`CompressZstd` calls it, at levels 0 to 22 over sizes either side of 16, 128 and
256 KB, where the level table changes, and of every power of two up to 8 KB,
and at every level over half and two and a half megabytes. Each row records the
compressed length and digest.

`zstd-compress-stream.tsv` has 250 rows: a fresh `ZSTD_CStream` at levels from
-131072 to 22, fed by `ZSTD_compressStream` in random chunks, with
`ZSTD_flushStream` and `ZSTD_endStream` between them as the extension's WRITE,
READ and WRITE/FINISH call them, so one stream writes several frames. A log of
100 and more in the `log` column means the schedule never ends a frame early.

What the encoder does that an input can steer, counted in zstd's own run of the
two tables:

| Boundary | Where in the C | Covered by |
| --- | --- | --- |
| every strategy, fast to btultra2 | `ZSTD_selectBlockCompressor` | 4,451 to 829 blocks each |
| every strategy searching an extDict, the window wrapped | the `_extDict` compressors | 71 to 294 blocks each, from the streams that never end a frame early |
| the row matchfinder, plain and extDict | `ZSTD_RowFindBestMatch` | 6,056 and 359 blocks |
| a block split after its sequences | `ZSTD_compressBlock_splitBlock` | 27 blocks split, 7,722 kept whole |
| a block split before | `ZSTD_splitBlock` | 1 split, 1,045 kept whole |
| a literal run or match past 64 KB | `ZSTD_llt_literalLength` and `_matchLength` | 7 and 365 blocks |
| each FSE table type and Huffman's repeat | `ZSTD_buildCTable`, `ZSTD_compressLiterals` | every type, thousands each |
| a context reused, its tables dirty and its salt moved on | `ZSTD_resetCCtx_internal` continuing | 6,893 resets |
| the workspace resized | `ZSTD_cwksp_create` | 1,783 times |
| long-distance matching | level 22 streams | every level 22 stream row |
| long-distance matching over an extDict | `ZSTD_ldm_generateSequences_internal` with a wrapped window | (none: it needs a frame past 128 MB) |
| the hash-chain search over an extDict | `ZSTD_HcFindBestMatch` | (none: a stream's window is never 2^14 or under, so it uses rows) |
| indexes past 3.5 GB | `ZSTD_window_correctOverflow` | (none) |

## Making them again

zstd's source is not checked in. Fetch the v1.5.7 tag of
https://github.com/facebook/zstd as source, read it, and then, from a scratch
directory:

    gcc -O2 -DZSTD_DISABLE_ASM -I <zstd>/lib -I <zstd>/lib/common table.c \
        <zstd>/lib/common/*.c <zstd>/lib/compress/*.c <zstd>/lib/decompress/*.c -o table && ./table

and the same for `frames.c`, which takes the path of zstd's `tests` directory,
and for `enc.c`, which includes `inputs.h`.

## Licence

The tables are zstd's output and do not ship. zstd is under the BSD licence,
Copyright (c) Meta Platforms, Inc. and affiliates.
