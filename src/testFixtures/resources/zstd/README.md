# What zstd answers in its own C

Five tables of what zstd v1.5.7's decoding pieces answer, computed by `table.c`
against zstd's own `lib/` sources compiled with gcc 15.2 on x86-64 Linux, with
`-O2 -DZSTD_DISABLE_ASM` on a CPU with BMI2. `ZstdDecodingFromTheSourceTest`
reads all five and expects every row exactly.

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

## Making them again

zstd's source is not checked in. Fetch the v1.5.7 tag of
https://github.com/facebook/zstd as source, read it, and then, from a scratch
directory:

    gcc -O2 -DZSTD_DISABLE_ASM -I <zstd>/lib -I <zstd>/lib/common table.c \
        <zstd>/lib/common/*.c <zstd>/lib/compress/*.c <zstd>/lib/decompress/*.c -o table && ./table

## Licence

The tables are zstd's output and do not ship. zstd is under the BSD licence,
Copyright (c) Meta Platforms, Inc. and affiliates.
