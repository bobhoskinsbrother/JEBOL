# What bzip2 answers in its own C

Tables of what libbz2 answers, computed against bzip2's own sources at commit
`66c46b8` of https://gitlab.com/bzip2/bzip2, whose codec files are the same as
libarchive's mirror and, whitespace and comments aside, as release 1.0.8.
`Bzip2CompressionFromTheSourceTest` and `Bzip2DecodingFromTheSourceTest` read
them, and every row is expected exactly.

    generate.sh <bzip2 checkout>              writes the tables
    generate.sh <bzip2 checkout> --coverage   the same at -O0, and leaves gcov's files
    generate.sh <bzip2 checkout> --sanitize   the same under ASan and UBSan

All three write byte-identical tables. Digests are 64-bit FNV-1a. A table's
"calls" column hashes every call's answer, the input it took and the output it
gave, so a stream that ends right having stopped at the wrong places still fails.

The C's memory is `malloc`'s, so a damaged stream can read a Huffman table entry
no block ever set. `table.c` gives the decoder zeroed memory, so that answer is
repeatable, and the Java's fresh arrays hold the same zeroes.

## The tables

| File | Rows | What it holds |
| --- | --- | --- |
| `bzip2-compress.tsv` | 442 | `BZ2_bzBuffToBuffCompress` on the inputs `inputs.h` makes |
| `bzip2-compress-stream.tsv` | 96 | `BZ2_bzCompress` in random chunks, flushes and output rooms |
| `bzip2-decode.tsv` | 5,172 | `BZ2_bzDecompress` on the corpus, crafted and damaged streams, called again after an error |
| `bzip2-streams.tsv` | 36 | the streams `bzip2-decode.tsv` names, as hex |
| `bzip2-recompress.tsv` | 105 | each corpus stream's output up to 16 MB, compressed again at levels 1, 5 and 9 |

## The corpus

`corpus/` is https://sourceware.org/git/bzip2-tests.git at `5f31d0d`, each
directory with its own README and licence, and `corpus/samples/` is bzip2's own
`tests/input/quick` with its `COPYING`. None of it ships, and `LICENSE` lists each
directory's licence. `corpus/lbzip2/` is held here because it has most of the
deliberately bad files.
`bzip2 -dc` agreed with every `.md5` file when the corpus was checked in.

## The boundaries, and what covers each

| Boundary | Where in the C | Covered by |
| --- | --- | --- |
| 0 to 24 octets | every path's smallest case | compress rows at levels 1, 5, 9 |
| run lengths 1 to 7, 254 to 260, 510 to 512 | `add_pair_to_block`, `ADD_CHAR_TO_BLOCK` | `RUNS` rows |
| block full, `100000*level-19`, either side | `nblockMAX` | `NO_RUNS` rows at every level, 2 each side |
| a block filled by counted runs | the same limit overshot by a pair | `RUNS` rows of three blocks at every level |
| 200, 600, 1200, 2400 symbols | `nGroups` in `sendMTFValues` | `NO_RUNS` rows, each size from 10 under to 10 over |
| 10,000 octets | `nblock < 10000` picks the fallback sort | `NO_RUNS` and `WORDS` rows, 9990 to 10010 |
| sort budget running out | `budget < 0` in `mainSort` | `PERIODIC` rows, 6 periods by 6 sizes |
| a code longer than 17 bits | the rescaling loop in `BZ2_hbMakeCodeLengths` | `FIBONACCI` rows, 18 to 40 symbols |
| more than one block | the combined CRC and `blockNo` | `WORDS` rows to 12 MB |
| flush and finish with no room left | `BZ_FLUSH_OK`, `BZ_FINISH_OK` | stream rows with rooms from 1 octet |
| every header and trailer octet wrong | `RETURN(BZ_DATA_ERROR)` in `BZ2_decompress` | `flip:` rows of every bit |
| input ending at every octet | `BZ_OK` with input short | `cut:` rows |
| level byte outside 1 to 9 | `BZ_X_MAGIC_4` | `crafted:level-below`, `level-above` |
| groups 1 and 7, no selectors, selector past groups | `BZ_X_SELECTOR_*` | `crafted:one-group`, `seven-groups`, `no-selectors`, `selector-past-groups` |
| 18,002, 18,003 and 32,767 selectors | `BZ_MAX_SELECTORS` | `crafted:selectors-at-limit`, `selectors-past-limit`, `selectors-at-most` |
| code lengths 0, 20 and past 20 | `curr < 1 \|\| curr > 20` | `crafted:length-*` |
| origin at and past each limit | `origPtr` checks | `crafted:orig-*` |
| run past two million, past the block | `N >= 2*1024*1024`, `nblock >= nblockMAX` | `crafted:run-past-*`, `symbols-past-the-block` |
| randomised blocks | `unRLE_obuf_to_output_FAST` | `crafted:randomised`, the corpus's `rand.bz2` |
| 45 MB from 46 octets | run lengths at their largest | `corpus/pyflate/45MB-*.bz2`, `corpus/lbzip2/ch255.bz2` |
| several streams end to end | input left after `BZ_STREAM_END` | `corpus/lbzip2/concat.bz2`, `stray:` rows |

## What the generator's run does not reach

`gcov` over the run reaches every line of `blocksort.c`, `huffman.c` and
`compress.c` except verbose printing. It does not reach, in `decompress.c`, the
small decoder JEBOL does not port, or three checks on `unzftab` and `cftab` that
no stream it made fails (`(none)`; the Java keeps them), or, in `bzlib.c`, a
randomised block's run overrunning its end (`(none)`, and the Java keeps that too).

## The module

`module-answers.r3`, `module-chunks.r3` and `module-flush.r3` run against
Rebol-Bzip2 v1.1.0 and this library compiled from source and loaded into `r3-head`,
given the `.rebx` as their argument. `.txt` beside each is what it printed.
`TheBzip2ModuleFromTheSourceTest` holds those answers. `module-chunks.r3` turns
garbage collection off: see `docs/bzip2-port.md`.
