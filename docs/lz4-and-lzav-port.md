# The LZ4 and LZAV ports

LZ4 1.10 (`rebol3-source/src/core/lz4/lz4.c` and `lz4hc.c`) and LZAV 5.5
(`rebol3-source/src/include/sys-lzav.h`), as Rebol wires them into
`u-compress.c`. This note carries what the code cannot: what Rebol's use of each
needs, where a plausible simplification changes the bytes or the verdict, and how
the port was checked.

Both are byte-exact against the C. Every compressed stream either writes is the
one a real 3.22.5 writes, and every stream either reads -- broken ones included --
is accepted or refused as the C accepts or refuses it, with the same answer or, for
LZAV, the same error code.

---

## What Rebol's use of them needs

**Both write a bare block.** No frame, no header, no length: LZ4's block format and
LZAV's raw format. Neither stream says where its answer ends, which is why neither
is an inverse on its own and the spec says so (`LzavReadsBackOnlyGivenItsSize`,
`Lz4ReadsBackWhateverFitsItsRoom`).

**LZ4 always goes through the HC compressor.** `CompressLz4` calls
`LZ4_compress_HC` at `MAX(1, MIN(12, (int)level))`, so the fast `LZ4_compress_default`
is never reached. Level one and level two are the same compressor (`lz4mid`), three
to nine are the hash chain with 4 to 256 attempts, and ten to twelve the optimal
parser. Nobody asking is `UNKNOWN`, all ones, which the `(int)` makes minus one and
the clamp makes level one -- the fastest, where every other method's default is
somewhere in the middle or at the top.

**LZAV takes no level at all.** `CompressLzav` calls `lzav_compress_default`, which
picks its minimal reference length from the input alone: five octets below 256 KB
and six from there up. `compress/level x 'lzav 9` is `compress x 'lzav`.

**LZ4 decodes into room, and the room is Rebol's, not the caller's.**
`DecompressLz4` makes a binary of /SIZE -- or three times the compressed length --
and hands `LZ4_decompress_safe` the series' `SERIES_REST`, which is the size of the
memory pool the series came from. So `decompress/size x 'lz4 3` on six octets
answers all six: the smallest pool is eight. `SeriesRoom` is that pool table, read
off `m-pools.c` and checked on r3-head by finding the longest literal run each size
admits:

| octets asked for, plus one for the terminator | room |
|---|---|
| up to 8 | 8 |
| up to 512 | the next multiple of 32 |
| up to 960 | 640, 768, 896 or 960 |
| up to 4096 | the next whole kilobyte |
| beyond | the next whole kilobyte, from the system allocator |

**That room is an `int` to LZ4**, so a /SIZE whose room rounds up to 2^31 is
refused even though Rebol could make the series: 2147482623 reads back and
2147482624 does not. One more, 2147483647, never gets that far: its terminator
takes the series past `MAX_I32` and `Make_Series` raises `no-memory`, for LZAV too.

**LZAV refuses without /SIZE before decoding anything**, and reports its own error
codes through bad-press: -1 bad parameters (an empty stream), -2 the stream ends
early or is shorter than ten octets, -3 the answer is longer than /SIZE, -4 a
reference before the start or nearer than eight octets, -5 the answer is shorter
than /SIZE, -6 a format it does not know. LZ4's refusals all say zero, because
`DecompressLz4` never sets the error.

**LZAV still reads its older second format**, because Rebol builds it with
`LZAV_FMT_MIN` at 2. Nothing writes it.

---

## Where a plausible simplification changes the bytes or the verdict

**LZ4's indices start at 64 KB, not at zero.** `LZ4HC_init_internal` puts the
first octet at index 65536, so an empty hash slot -- index zero -- is always more
than a window away and never mistaken for a match at the start. `Lz4Block.PREFIX_INDEX`
keeps that, and the chain table is indexed by the low sixteen bits of an index, so
it wraps as the C's does on any input over 64 KB.

**The mid compressor's table fill uses the index from before the catch-back.**
`LZ4MID_compress` extends a match backwards and then adds positions `ip+1` and
`ip+2` under `ipIndex+1` and `ipIndex+2` -- the new `ip`, the old index. It reads
like a slip and it is what writes R3's bytes.

**LZ4's decoder has two loops that do not accept the same streams.** The fast loop
runs while the room is at least 64 octets and copies a short literal run without
checking that eight octets of input are left after it; the safe loop checks. So a
block can decode with room to spare and be refused with a little less. The port
keeps both loops and every jump between them, as `Lz4BlockDecoder`'s steps.

**A match of offset zero is zeros, not an error.** The C writes four zero octets
first to quiet a sanitiser and then copies the match onto itself, so the copy
reproduces them. Only an offset reaching before the start is refused.

**LZAV carries offset bits into the previous block.** A reference block's last
offset octet has two, three or five spare bits, and the next block ORs the low bits
of its own offset into them; a literal block carries two more in its header. The
encoder's `carryAt` and `carryShift` are that, and the decoder's `carry` and
`carryShift` unpick it. The window check grows with the carry: a reference after
literals may reach four times further, which is how a repeat three million octets
back is still found with a two megabyte window.

**LZAV's second-format decoder copies a fixed number of octets, not the match.**
For an offset of four to fifteen it copies eight or sixteen octets and then moves
the length on, so a malformed stream can leave octets of an earlier overshoot in
the answer. The port performs the same copies of the same widths, overshoot
included, rather than copying exactly the match.

**LZAV reads past the end of a broken stream.** The C reads length octets without
a bound; the port reads zeros there, which is what a freshly made Rebol series
holds past its tail.

---

## How it was checked

The reference is a probe built from the C alone: `lz4.c`, `lz4hc.c` and
`sys-lzav.h` compiled with r3-head's own flags into a program that compresses or
decompresses standard input. It agreed with r3-head on every sample before any Java
existed.

Then every input below through both. LZ4 compressed at every level from minus one
to thirteen, and its streams from levels one, nine and twelve decoded back with no
size, the exact size, one less and one more; LZAV compressed, and decoded at the
exact size, one less and one more:

- 588 generated inputs: zeros, noise, text, eight periods of repetition and a
  mixture with matches up to 70 000 back, at 49 sizes from nothing to 140 000;
- Brotli's own test corpus, `testdata.txz`, including its 12 MB file;
- the Silesia corpus, twelve files from 5 MB to 51 MB, at LZ4 levels 1, 2, 3, 9,
  10, 11 and 12, which also takes LZAV past its two megabyte window and its
  256 KB switch to six-octet references;
- some 54 000 decodings of broken streams -- valid ones truncated, extended and
  mutated -- offered to both decoders at random room sizes. Every LZAV code from
  -1 to -6 came up, and the C and the port agreed on every verdict, every code and
  every answer.

### The boundaries, and the test that covers each

| Boundary | Test |
|---|---|
| LZ4: no input is one zero octet, refused when read | `Lz4FromTheSourceTest` `compressesNothingToOneZeroOctet`, `refusesNothingAndTheEmptyBlock` |
| LZ4: 12 and 13 octets, below and at the first input searched for matches | `startsLookingForMatchesAtThirteenOctets` |
| LZ4: each of the twelve levels, 70 000 octets | `writesRebolsBytesAtEveryLevel` |
| LZ4: 140 000 octets, the 64 KB chain table wrapped twice | `writesRebolsBytesPastTheChainTable` |
| LZ4: a repeat 65 535 back, and 65 536 | `reachesBackNoFurtherThanTheLargestOffset` |
| LZ4: a match longer than the optimal parser's 4 095-octet "good enough" | `writesALongRunAsOneMatch` |
| LZ4: levels 0, -1, 13 and the low 32 bits | `clampsTheLevelAtBothEnds`, `readsTheLowThirtyTwoBitsOfTheLevel` |
| LZ4: every pool edge of the room | `roundsTheRoomUpToRebolsPool`, `SeriesRoomTest` |
| LZ4: three times the compressed length, 64 octets and 65 | `givesThreeTimesTheCompressedLengthWithoutASize` |
| LZ4: room of 2^31 | `refusesRoomThatDoesNotFitAnInt` |
| LZ4: an offset of zero, an offset before the start | `copiesZerosForNoOffsetAndRefusesOneBeforeTheStart` |
| LZ4: the fast decoding loop against the safe one | (none in the suite; the fuzzing above) |
| LZAV: nothing, refused | `LzavFromTheSourceTest` `refusesNothing` |
| LZAV: 8 and 9 octets, padded and not | `padsAShortInputToNineOctets` |
| LZAV: 15 and 16 octets, written whole and compressed | `compressesFromSixteenOctets` |
| LZAV: 512 and 513, the first hash table doubling | `writesRebolsBytesEitherSideOfTheFirstTableSize` |
| LZAV: 262 143 and 262 144, five-octet references to six | `growsTheShortestReferenceAtTwoHundredAndFiftySixKilobytes` |
| LZAV: offsets 1 023 and 1 024, 32 767 and 32 768 | `widensTheOffsetAtEachThreshold` |
| LZAV: a repeat beyond the two megabyte window | `reachesBeyondTheWindowWhenLiteralsCarryTheOffset` |
| LZAV: /SIZE short, exact, long, zero, and too large to make | `needsTheExactSize`, `refusesASizeOutOfRange` |
| LZAV: each error code | `reportsLzavsOwnCodes` |
| LZAV: the second format | `readsTheSecondFormat` |
| LZAV: the decoder's 63-octet fast-copy margin | (none in the suite; the fuzzing above) |
