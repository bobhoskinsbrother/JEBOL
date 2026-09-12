# The Brotli port

Thirty-nine files under `org.jebol.domain.eval.brotli`, one of them public.
This note carries what the code cannot: which C file each piece came from, and
the handful of format facts that a reader would otherwise have to derive.

`CLAUDE.md` carries the method that produced it -- build the canonical reference
first, list boundaries rather than counting samples, use the reference project's
own corpus, and check that a fixture is what it says it is. Each of those four
rules exists because it was broken here and cost something.

The C is checked out at `rebol3-source/`. Where a name below is a C function,
`ide_search_text` with `filePattern: "*.c"` or `"*.h"` finds it.

---

## Where each piece came from

| JEBOL | C |
| --- | --- |
| `BrotliBits` | `write_bits.h` |
| `BrotliCodes` | `entropy_encode.c`, `prefix_code.h` |
| `BrotliStaticCodes` | `entropy_encode_static.h` |
| `BrotliHistogram`, `BrotliHistogramCost` | `histogram.h`, `bit_cost_inc.h` |
| `BrotliClustering` | `cluster_inc.h` |
| `BrotliBlockSplit`, `BrotliBlockSplitter` | `block_splitter.h`, `metablock_inc.h` |
| `BrotliContextBlockSplitter` | `ContextBlockSplitter` in `metablock.c` |
| `BrotliMetaBlockSplit`, `BrotliMetaBlock` | `metablock.h`, `metablock.c` |
| `BrotliCommand` | `command.h` |
| `BrotliMatch`, `BrotliMatches` | `BackwardMatch` in `hash.h` |
| `BrotliDictionarySearch` | `SearchInStaticDictionary`, `TestStaticDictionaryItem` in `hash.h` |
| `BrotliDistances` | `BrotliInitDistanceParams` in `params.c` |
| `BrotliQuickHasher`, `BrotliFullHasher` | the `H2`..`H6` hashers in `hash.h` |
| `BrotliBinaryTreeHasher` | `hash_to_binary_tree_inc.h` |
| `BrotliPricedParse` | `backward_references_hq.c` |
| `BrotliBackwardReferences` | `backward_references.c` |
| `BrotliRingBuffer` | `ringbuffer.h` |
| `BrotliLog2` | `fast_log.h` |
| `BrotliDecoder` | `decode.c` |

## The format facts worth writing down

**The bit stream is least significant bit of each byte first**, and every write
leaves the byte after the last one it touched at zero, so the next write can OR
into the partial byte it starts in. Two places go back over what they wrote and
both depend on that: storing a block plainly after deciding the compressed form
was not worth it, and widening a meta-block's declared length after deciding to
carry on into it.

**A block split is a run of lengths, not a run of positions.** The reader is
told the lengths in order and switches code when one runs out. Two stretches far
apart may share a type, which is the whole point: text alternating between two
kinds of content pays for two codes rather than one muddled one.

**The splitter asks whether merging costs entropy.** It gathers a target number
of symbols and then asks whether that batch is better off with a code of its
own: start a new type, hand the batch to the type before last, or fold it into
the type it just used. If merging into either of the last two would add more
than the threshold to the total, the batch deserves its own code.

**A literal's code depends on the two bytes before it.** One block type then
owns two or three histograms rather than one -- a letter after a space is
counted separately from a letter after a letter -- and the split-or-merge
decision is taken on the total across all of them, so the contexts move together
and a block type is never half one thing and half another. Because each type
costs several codes rather than one, the ceiling on types is lower than for the
other two alphabets.

**An empty context map means every context of a block type shares that type's
one histogram**, which the writer has a shorter form for. Only literals get a
context map below quality ten; distances can carry one in the format and nothing
here builds one.

**A histogram's cost is a guess used to compare, not a measurement.** Four or
fewer symbols get a flat answer, because the format has a short form whose cost
is known without building anything. Anything larger is the entropy of the
symbols plus a guess at what declaring the code costs -- counting how many
symbols would sit at each depth if each cost its own surprise rounded to the
nearest bit, and how long the runs of unused symbols are, since a run of three
or more is written as a repeat with three extra bits. The final run of unused
symbols is free, the format taking the code as ending there, so it is not
counted.

**A distance is a code plus extra bits, and two knobs change the split**: how
many of the smallest distances get a code of their own with no extra bits, and
how many low bits of the rest move out of the extra bits and into the code. Both
start at zero; a meta-block may pick better ones once it has seen every distance
it needs to write, and the commands are then re-coded.

**A dictionary word is referred to by a distance beyond the end of everything
written so far**, so a match there costs nothing to store and saves whatever the
word is long. Only a prefix need match -- cutting the last few characters off is
one of the transforms -- so a word that agrees for all but its last three
letters is still usable, with the transform saying which letters to drop.

**A match on a dictionary word carries a third number**: the length its code
will name, which is the length of the word before the transform rather than the
length of the bytes it produces. The C packs that into the low five bits of the
length and spells "same as the length" as zero, and that packing is kept,
because it is what the priced parse compares.

**Quality two writes fixed prefix codes.** A meta-block of a hundred and
twenty-eight commands or fewer has too few to make measuring worthwhile, so the
encoder writes a code chosen once for all inputs and spends its bits on the
literals instead. The two trees are stored as fixed bit patterns rather than
built.

## What ImageIO-style faults look like here

Two defects the canonical-reference harness found within minutes of existing,
both invisible to every test written before it:

- **A hash table cleared at every block instead of once.** It needed more than
  one block of input to show, and every test until then was short.
- **The encoder is told the input length even though Rebol never sets it**,
  which changes which of four hashers runs. Reading the code had concluded the
  opposite twice; instrumenting the reference with an `fprintf` settled it in
  one run.

And one the invented corpus could not reach: **the top two levels divided their
blocks three times where the C divides them ten**, from a wrong constant. Three
files from Brotli's own `testdata.txz` caught it immediately, and its twelve
megabyte file then caught two more.

## Four things that cannot be derived from the code

**`log2` had to be written out, and the reason is measured.** Java has no
`log2` and the obvious substitutes are not close enough. Brotli's encoder prices
every choice in bits and two candidates often cost within a hair of each other,
so a result differing from the C's in its last bit picks a different match and
writes different bytes. Over the three million whole numbers from 256 upward,
`log(v) / log(2)` disagrees with the C on twenty-three in a hundred, and
reducing the argument to the range one to two first still disagrees on one in a
hundred. The C's own `log2` is correctly rounded on the platforms checked --
confirmed against exact decimal arithmetic on four thousand values -- so
`BrotliLog2` computes the correctly rounded result rather than imitating any
particular library: split into a power of two and a mantissa, divide the
mantissa by the nearest sixteenth whose logarithm is tabulated, and put the
remainder through a short series, all carried in pairs of doubles holding about
a hundred and six bits.

**The dictionary hash table is copied from the C, not rebuilt.** Rebuilding it
would need the same insertion order and the same collision losses to come out
the same, and a table differing by one slot compresses differently. It is
carried deflated and base64 encoded in the source for the same reason the
dictionary itself is -- this package may not read a file -- and ninety-six
kilobytes of mostly empty slots deflate to twenty-three.

**The ring buffer keeps seven spare zero bytes past everything**, because the
hash of a position reads eight bytes and the last few positions have fewer than
eight left. Without them the answer would depend on whatever happened to be in
memory. (The C also keeps a copy of the last two bytes just before the start; it
is written and never read, so it is not here.)

**Only the UTF-8 context tables are written down.** Brotli conditions a literal
on the two bytes before it, by one of four rules: the low six bits or the high
six bits of the previous byte, a reading of the pair as UTF-8 asking what kind
of character each was, or a bucketing by magnitude for data that is really
numbers. The other three follow from a line of arithmetic each, and a test
checks that all four still agree with the C's own 2,048 bytes.

## Where a plausible improvement would change the bytes

Each of these looks like something to tidy up and is not. All of them were
transcribed from the C deliberately.

**Costs are float, not double.** `BrotliCostModel` and `BrotliLiteralCosts`
both work in double and write down float, which is the C's type. The priced
parse compares costs for less-than, and the two widths disagree about which of
two nearly equal matches wins.

**The running total carries its own lost bits forward.** Adding several
thousand small floats into one large one loses the low bits of every addition.
The C carries the lost part into the next addition, which keeps the total close
to what double arithmetic would give without using double. Those three lines are
the C's and the order of operations matters.

**A command's top seven length bits are read two different ways**, and the two
disagree for any command whose length was shifted down by a dictionary word. One
reads them as a signed amount, the other as a plain number; the C reads them the
plain way in exactly one place, where it re-codes a command it has just
lengthened. Reproduced as the C has it rather than corrected, because the bytes
are what is being matched.

**`kInvalidMatch` is seven hexadecimal Fs, not eight.** It has to lose to every
real answer under the comparison that keeps the cheapest, and eight would work
too -- but it is not what the C says, and this is a file where "it would work
too" is not the standard.

**`BrotliDictionaryMatches` was converted from the C mechanically, not by
hand.** About eighty of the hundred and twenty-one transforms are a word with a
fixed suffix, so the search walks a tree of suffixes written out as four hundred
lines of nested character comparisons. That is exactly where a transcription
error hides and never shows up except as different bytes.

**Both dictionary tables are precomputed and copied, not rebuilt**, because
rebuilding needs the same insertion order and the same collision losses to come
out the same. A bucket whose words are in a different order picks a different
match.

**An unused symbol still has to be affordable.** The parse may want one, so it
is priced as if it had appeared once, plus two bits. For literals the unused
ones are not counted into the total, which makes the used ones look commoner and
so cheaper.

**One histogram of literal costs, not two.** The C's own comment says the count
should be two and that one compresses better, which is why it starts at one and
only ever falls.

**The clustering queue is not a heap, despite the C calling it one.** Only the
first entry is ordered; the rest sit in whatever order they arrived, and a new
pair either displaces the front or is appended. That is what the C does, and the
order it leaves behind decides which merges happen — so making it a real heap
would change the output.

**The greedy walk is not purely greedy, in two ways.** It looks one byte ahead
before committing: if the copy starting at the next byte scores a hundred and
seventy-five better, the byte here is written as a literal and the better copy
taken instead. That may repeat, but only four times in a row, so a pathological
input cannot walk it forward for ever. And it gives up on data that is not
compressing — after sixty-four bytes with no copy it hashes every second
position, and after another two hundred and fifty-six every fourth. The point is
not only speed: hashes of incompressible data would crowd out the hashes of the
compressible data that may follow.

**A copy does not hash every position it skipped.** A copy of a long run of one
byte would fill the table with hashes of that run and evict everything useful,
so a copy whose distance is short relative to its length has only its last
stretch hashed.

**The furthest a copy may reach is sixteen short of the window**, so that a
distance and the window it sits in cannot be confused at the boundary.

**Lengths twenty-five to thirty-one have a slot holding zero.** The dictionary
tables run further than the longest length that has any words in it, so a stream
asking for one of those is refused for asking about a length nobody has words
of, rather than for reading off the end of a table.

**Two of the twenty-three transform kinds are not here.** The two that shift a
code point exist for shared dictionaries, which take their shift amount from a
parameter table; the built-in transforms use neither.

**The dictionary is carried in the source, not read from a file**, because the
domain does nothing a filesystem or a stream is needed for and a dependency-rule
test holds it to that. Deflated and base 64 rather than a hundred and twenty
thousand numbers, which would be seven hundred kilobytes of source and would not
fit in one method. It costs seventy-eight kilobytes of jar against fifty-eight
for a resource, and a build that only ever writes Brotli never inflates it.

**`BrotliDictionary` and `BrotliDictionaryWords` inflate without
synchronisation, deliberately.** Two threads racing there build the same bytes
from the same constant and the later write wins with the same answer; a lock
would cost every reader to protect nothing. What the records exist for is that a
reader sees the whole set of tables or none of it.

**The shape and the indentation of `BrotliDictionaryMatches` are the C's**, not
this project's. It is the one place in the port where reading the two side by
side is how a difference would be found.

**The upper-casing is "an overly simplified uppercasing model for UTF-8",** in
the C's own words: a two-byte sequence has bit five of its second byte flipped
and a three-byte one has bit two of its third byte flipped, neither of which is
upper-casing in any language. It is what the format says happens.

**The count of how many positions a hash has seen is sixteen bits and is allowed
to wrap**, exactly as the C's is. It is a position within a ring, so wrapping
loses nothing that was not already being overwritten.

**The quick hasher is forgetful on purpose.** One slot holds one position and a
later position overwrites it, so a match findable a moment ago may not be
findable now. Losing matches is what makes qualities two to four fast. Quality
three widens it to two slots per hash and quality four to four, chosen by three
bits of the position so consecutive positions land in different slots rather
than evicting each other; quality three is the only one of the twelve that never
consults the dictionary.

**A match's score is not a bit count.** It is a rough stand-in: a hundred and
thirty-five per byte saved, less thirty per bit needed to name how far back it
starts. That is what lets a nearer short match beat a distant long one without
pricing either properly.

**The distance-parameter search is not exhaustive, and its order matters.** It
walks the direct-code count upward until the cost stops improving, then halves
where it restarts for the next postfix width. Which combinations get tried
therefore depends on the order, and the order is the C's.

**An empty branch of the binary tree is spelled as an unsigned position so far
past the end that the distance to it always fails the too-far test.** Held here
as the same bits, which read as a negative number — so every comparison against
it has to be made unsigned, or made to treat a negative distance as too far.

**The tree walk rebuilds the tree as it goes**: the new position becomes the
root and everything it passed hangs underneath on the side it belongs, so the
tree stays sorted without a second pass. That only happens where there are a
hundred and twenty-eight bytes left to compare — a position with fewer cannot be
placed, because where it belongs depends on bytes that have not arrived. Nothing
is ever removed; a position that has fallen out of the window is recognised by
being too far back rather than by being deleted.

**Matches come from three sources in a fixed order**: a plain scan of the last
sixteen positions (sixty-four at the top level), which catches the very short
matches the tree will not and only runs while nothing longer than two bytes has
been found; then the tree; then the dictionary, for lengths longer than anything
the tree turned up.

**Quality one hashes the same position twice, and it is a slip that is kept.**
After a match that followed some literals the C hashes offsets nought, one and
*nought again*, so the third position is filed under the first one's hash; after
a match that followed another match it hashes nought, one and two. It reads like
a slip and it is one — but it decides which candidate the next position finds,
so it is part of what a real 3.22.5 writes. Only the four-byte case differs; the
six-byte one is the same at both places.

**Leftover bits from the previous meta-block count toward this one's size.** The
C writes each meta-block into a buffer starting at whatever few bits were left
over from the one before, and weighs the input against how many whole bytes that
buffer holds. So a meta-block four bytes larger than its input is kept while one
five bytes larger is thrown away and the bytes stored plainly.

**Qualities zero and one number their commands differently**, so the shuffle
from their sixty-four working symbols into the format's seven hundred and four
is not the same in both: here the insert lengths come first and there they come
last.

**The distance parameters a top-level meta-block chooses do not carry forward.**
The choice is made on a copy of the settings, so the next meta-block's search
starts from the plainest setting again.

**Quality one looks for longer matches on bigger inputs.** Its match table runs
to a hundred and thirty thousand entries against quality zero's thirty-two
thousand, and once the table is wider than fifteen bits it indexes six bytes
rather than four — which is also why it finds none at all on input too short to
fill a wide table.

**The block splitter's random sequence is part of the answer.** Candidate
histograms are seeded from samples taken at pseudo-random places, and the
generator is the C's - multiply by sixteen thousand eight hundred and seven,
starting from seven. A different sequence gives different starting histograms
and a different division of the symbols, so the constant and the seed are both
load-bearing.

**The splitter redoes its assignment three times at level ten and ten times at
level eleven.** That count is the only place the two levels' block splitting
differs, and it matters: with three rounds level eleven settles on a division
two symbols away from the C's on some inputs and writes different bytes.

**A symbol counted nought times is priced at minus two bits.** The C does this
on purpose in the splitter's cost function, and a sane zero would change which
histogram a stretch is assigned to.

**Level eleven's search prunes in three ways and all three numbers are the
C's.** Only the eight cheapest starting positions are kept; the first of them is
tried at level ten and the first five at eleven. A copy longer than a hundred
and fifty bytes - three hundred and twenty five at eleven - is taken as read
rather than compared against shorter ones. And a position already reachable more
cheaply than any command could manage is skipped.

### Quality zero, where the C explains itself and the explanations are load-bearing

**The literal code is built before any matching, and it is deliberately
flattened.** The first eleven appearances of each byte count triple. The C
explains this as accounting for the balancing effect of the matching phase:
bytes that turn out to sit inside matches are never written as literals at all,
so a flatter histogram is closer to the truth than the raw one. Above
thirty-two kilobytes only every twenty-ninth byte is counted, and then every
byte gets one added so that none ends up with no code at all.

**The scan gives up gradually.** If thirty-two bytes go by without a match it
starts looking at every other byte; after thirty-two more, every third, and so
on, and a match resets the stride. The C puts the price at about a tenth of a
per cent of density on data that compresses, against a large win on data that
does not.

**Positions inside a copy are filled into the table by hand.** The outer loop
never scans them, so without that step they could never be candidates for
anything later. The C says it does this "to improve compression".

**Quality zero's match table only comes in odd widths**, and an even one is
rounded up rather than down. Quality one has no such rule - that is one of the
two places the two settings' table sizing differs.

**Two ratios decide whether to give up on a block.** An insert is left
uncompressed if what has been written so far is more than one fiftieth of what
is about to be inserted as literals and the literals were costing more than
eight bits each. And the next block joins this meta-block rather than starting
another if one byte in forty-three, priced under this block's literal code,
comes out cheaper than a fresh code plus two hundred bits of header.

**The sixty-four command symbols are shuffled because of the emitting code, not
the format.** The C says it keeps them in this order "because having the symbols
in this order in the command bits saves a few branches in the Emit* functions".

### Two Huffman comparators, one line apart

**The C has two static `SortHuffmanTree` functions and they are not the same.**
The one in `entropy_encode.c`, which the command and distance codes use, breaks
a tie between two symbols of equal count by putting the later symbol first. The
one in `brotli_bit_stream.c`, which the literal codes use, is the whole of
`v0->total_count_ < v1->total_count_` with no tie-break at all, so two symbols of
equal count keep whatever order the shell sort left them in.

Use the wrong one and the code that comes out has exactly the same shape with
two of its symbols swapped. It is valid Brotli, it decodes correctly, and it is
not the bytes a real 3.22.5 writes - which is why a round trip can never catch
it and only a byte comparison against the reference can.

**The count-smoothing arithmetic is fixed point with eight fractional bits.**
That is where every multiplication by two hundred and fifty six comes from, and
it is the C's own description. It gives up early three times over: on fewer than
sixteen used symbols, on fewer than five once the trailing zeros are dropped, and
on fewer than twenty eight. A small alphabet is modelled well enough as it
stands.

**One unsigned comparison in that smoothing is doing two jobs.** It reads as a
test for "too far above the running average", and because subtracting a larger
limit wraps the difference round to an enormous positive number it is also a test
for "too far below". Written with signed numbers, as Java must, both halves have
to be said out loud.

**A run of code lengths comes out most significant piece first**, because the C's
writer builds the pieces backwards and then reverses them. The two pairs of
generated tables it ships - seven hundred and four numbers each - are just that
rule worked out ahead of time: a run of zeros is symbol seventeen with three bits
of count, a run of anything else is symbol sixteen with two.

**Runs are not always coded.** Whether to use them is decided by counting them
first, because for a short code the run markers cost more than the lengths they
save - so a code of fifty symbols or fewer never uses them.

### The decoder

**Two departures from the C, neither of which changes an answer.** The
multi-level lookup tables the C builds for speed are not here: a prefix code is
decoded one bit at a time down a canonical table, which is the same code read the
same way. And there is no ring buffer, because the whole answer is held anyway
and a back-reference can index it directly.

**DECOMPRESS/SIZE stops reading at the limit, and that is the behaviour.** The C
breaks out of its loop as soon as the answer passes the size asked for and cuts
it there, so a stream damaged beyond that point is never looked at. It asks for
the front of something; it does not promise the rest is sound.

**Large-window streams are refused, and Rebol is the reason.** Rebol creates the
decoder without `BROTLI_DECODER_PARAM_LARGE_WINDOW`, so a stream that asks for a
large window is one this build will not read either.

**A metadata meta-block's length is read before the jump to a byte boundary, not
after.** `DecodeMetaBlockLength` reads the reserved bit, the byte count and the
count itself, and only then does its caller pad.

**A dictionary reference has to roll the recent-distance cursor forward itself.**
The two short codes that reuse the most recent distance roll the cursor back so
that the copy's own write puts it where it was. A dictionary reference never
writes, so it undoes the roll by hand - `s->dist_rb_idx += s->distance_context`.

**A prefix code with one symbol costs no bits at all.** There is nothing to tell
apart, so the symbol is the answer whatever comes next. The C says the same thing
by filling every entry of its table with that symbol at a width of zero. It
arises for real: a meta-block whose code lengths are all the same names one code
length and repeats it, and then the code over code lengths has one symbol in it.

**Room for one transformed dictionary word is 255 + 32 + 255.** The C reserves
the widest a prefix or suffix could be, not the widest any of the built-in ones
is, because the length of a piece is written in a byte.
