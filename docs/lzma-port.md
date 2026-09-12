# The LZMA port

`LzmaDec.c` (2018-02-28) and `LzmaEnc.c` (2018-04-29) of the LZMA SDK, as Rebol
vendors them in `u-lzma.c`. This note carries what the code cannot: the format
facts a reader would otherwise have to derive, and the handful of places where a
plausible simplification changes the bytes or loses data.

The C is checked out at `rebol3-source/`. Where a name below is a C function,
`ide_search_text` with `filePattern: "*.c"` or `"*.h"` finds it.

---

## What the format is

An arithmetic coder over adaptive bit models. Each step writes either a literal
byte or a repeat of something earlier, and a twelve-value state remembers what
the last few steps were so the models can be conditioned on it. A repeat is
either one of the four most recent distances, which are cheap to name, or a
fresh distance written as a slot, some direct bits and four aligned bits.

The five property bytes a stream opens with pack three numbers into the first -
how many bits of the previous byte condition a literal, how many bits of the
position do, and how many bits of the position condition everything else -
followed by the dictionary size, little endian, floored at four kilobytes.

**The dictionary size written is not the size asked for.** Below four megabytes
it is rounded up to two or three times a power of two, and above that to a whole
number of megabytes, so a reader can size its window from one byte of exponent
rather than from an arbitrary number.

---

## What only Rebol's use of it needs

**Rebol always knows how long the answer is before it starts**: either
DECOMPRESS/SIZE said so or the four bytes Rebol appends to the stream did. So the
output buffer is made once and the decoder stops when it is full.

**The window is the answer itself.** `LzmaDecode` points the dictionary straight
at the caller's output buffer and sizes it to how much output was asked for, so
nothing wraps and nothing is copied twice.

**The end marker is never written.** `CompressLzma` passes zero for it and states
the length in four bytes of its own instead. A stream from anywhere else may
carry one, and the C treats it as the end rather than as damage: it stops and
reports how much it made.

**The stream-at-a-time interface is not ported**, because Rebol always hands over
a whole buffer.

**Running out of input part way through a symbol is an error, not a short
answer.** `LzmaDec_TryDummy` arranges that in the C by refusing to start a symbol
it cannot finish.

**A run may reach past the end of what was wanted, and that is not an error.**
DECOMPRESS/SIZE asks for the front of something, and the last run before that
point is allowed to be longer than the room left.

---

## Four things that cannot be derived from the code

**`kBadRepCode` is the code a stream cannot open with.** Before a single byte has
been produced there is nothing to repeat, so a first symbol that would decode as
a repeat is data that was never LZMA. The constant is what the range coder's
first two decisions come to when both go that way.

**A single-byte repeat cannot be reported as a length of zero.** Zero is the
shortest length a real repeat has - the symbol zero means two bytes, lengths
being written with two subtracted. Conflating the two lost the last two bytes of
anything that ended on a two-byte repeat, and only the last two, which is exactly
the kind of fault a round trip through short data never shows.

**`checkDicSize` changes what bounds a distance.** Until the answer is longer
than the dictionary, a distance may not reach before the start of the answer;
after it, it may not reach further back than the window the stream was written
with. The C sets the flag between calls to the inner loop and caps that loop so
the crossing lands on a boundary; noticing the crossing at the next symbol
instead is the same boundary.

**`compress/level x 'lzma -5` answers what level nine answers.**
`level = (level == UNKNOWN) ? 5 : MIN(9, level);` in `CompressLzma`, over an
**unsigned** level - so minus one is the level nobody asked for, and every other
negative arrives as a number near four thousand million and gets clamped down to
nine. That is a consequence of the C rather than a reading of it.

---

## Where a plausible improvement would change the bytes

**The reverse bit tree has to be spelled the C's way round.** The node walked to
is `(node << 1) | bit` and the answer is built up the other way, one bit at a
time from the bottom - the exact inverse of `RcTree_ReverseEncode`. The encoder
and the decoder must arrive at the same node for the same run of bits, or they
adapt different models and the second run of three bits already disagrees.

**The direct-bit read subtracts the halved range and looks at whether it went
below zero.** That is the comparison and the update in one, which is what keeps
the branch out of the loop. A language with no thirty-two bit unsigned type has
to mask the subtraction back before reading its top bit.

**The price table is a base-two logarithm worked out in integers.** Squaring the
probability four times and counting how far it has to be shifted back under
sixteen bits is what the C does, and it is why the table is not simply
`-log2(p)` rounded. Prices are in sixteenths of a bit.

**The match-pair count must not be shared between the two searches.** The C
passes it out through a pointer the caller aims at either a local or at
`p->numPairs`, and the difference matters: `GetOptimumFast` shortens its own copy
while looking for a nearer match of the same length, and that shortening must not
be visible the next time the field is read.

**Two ways of choosing what to write, and the level picks one.** Below level five
it is greedy with one byte of lookahead. From level five it prices every choice
over a window of up to four thousand positions and walks the cheapest path back -
`Backward` - which is what makes the same bytes come out as a real 3.22.5 rather
than merely something that reads back. An `extra` on a step means the step was a
match or repeat followed by a literal and then a repeat of distance zero, stored
as one choice and unpacking into two or three.

**The price tables are refreshed as the models drift**: the length tables on a
counter, the distance and alignment tables when enough matches have gone by.

**A run of `FF` bytes is held back until something below it decides whether it
carries.** The arithmetic coder keeps a range and a low bound, both notionally
thirty-two bits wide; each bit written splits the range in proportion to its
model, and whenever the range gets too narrow a byte of the low bound is settled
and shifted out. The carry is why a byte cannot simply be written.

**A match length is written as one of three brackets**: eight short lengths per
position state, eight more, and then 256 long ones shared between every position
state.

---

## The match finder

`LzFind.c` (2017-06-10). **Two of its four finders are reachable from Rebol**, because
`LzmaEnc_SetProps` pins the hash to four bytes and the level decides only whether a
binary tree or a hash chain holds the earlier positions: levels below five use the
chain and the rest use the tree.

**Three hash tables sit one after another in a single array** - two bytes, three bytes
and four bytes wide - and each position updates all three. The two narrow ones answer
at once with a match of exactly two or three bytes; the wide one is the head of the
chain or the root of the tree that the long matches are found down.

**Rebol always compresses a whole buffer it already holds**, which the SDK calls direct
input. So there is no window to slide and no block to re-read: the source array is the
window, and a position in it is a position in the window plus a fixed offset. The
stream-reading half of the original is not ported, and the fields it would have moved
are not here.

**Matches are reported as pairs pushed into a caller's array**, each pair a length and
then a distance less one, in increasing order of length. The count written back is
always even.

**The four-byte table's width is the history size rounded up to one less than a power of
two and never below 65535.** The comment in the C on the last step is "don't change it!
It's required for Deflate". The size the caller asked for is cut down to the size of the
data when the data is smaller, which is what `LzmaEnc_SetDataSize` is for and why
compressing fourteen bytes does not build a sixty-four megabyte table.

**The cyclic buffer can safely be sized at whichever is smaller, the dictionary or the
data.** The C sizes it at the dictionary and nothing else, so the default level asks for
sixteen megabytes of positions to compress fourteen bytes and level nine asks for
sixty-four. Nothing is ever written past the end of the data, so a buffer longer than the
data cannot change an answer: every distance a real match has is at most how far into the
data this position is, and every empty hash slot is cut by the same test either way.

**The binary tree walk splits the chain by what sorts before and after the bytes at this
position and rebuilds both halves as it goes**, leaving the tree in order for the next
position to walk.
