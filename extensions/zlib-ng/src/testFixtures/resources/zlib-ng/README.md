# What zlib-ng answers in its own C

Two tables of what zlib-ng 2.3.2's `deflate` and `inflate` answer, computed by
`table.c` against zlib-ng built with `./configure --static`, compiled with gcc 15.2
on x86-64 Linux. `ZlibNgFromTheSourceTest` reads both and expects every row
exactly.

The inputs are `DeflateFixtures`' -- `words`, `noise`, `mixed` and `zeros` --
which `table.c` makes again from the same seeds and the same linear congruential
sequence. Every digest is 64-bit FNV-1a.

## zlib-ng-deflate.tsv

6,892 rows. Each row is a whole compression: the input's kind and length, the
`deflateInit2` arguments, then the input fed in `chunk`-sized pieces (0 is all at
once) under `flush` (0 none, 1 partial, 2 sync, 3 full, 5 block), with output
handed out `outstep` octets at a time (0 is all the room at once), and a final
`Z_FINISH`. It records the output's length and digest and a digest of every
return code along the way.

## zlib-ng-inflate.tsv

11,520 rows. Each stream is compressed at level 1 or 6, raw, zlib or gzip, and
then damaged: 0 left whole, 1 cut in half, 2 cut by one octet, 3 a bit flipped
seven-thirteenths in, 4 a bit flipped in the third octet (in the header of a
wrapped stream), 5 the top bit of the last octet flipped (the check of a wrapped
stream). It is decoded under windows -15, 15, 31 and 47, fed whole and seven
octets at a time, with output whole and 300 at a time. The row records the last
result, the totals in and out, and the output's digest.

## The boundaries, and what covers each

The sizes at which zlib-ng changes what it does, read from its C:

| Boundary | Where in the C | Covered by |
| --- | --- | --- |
| 0, 1, 2, 3 octets | `STD_MIN_MATCH` 3, the empty stream | deflate rows at 0 to 5 |
| 4 octets | `WANT_MIN_MATCH`, the shortest match quick, fast and medium take | deflate rows at 4 and 5 |
| 257 to 263 | `STD_MAX_MATCH` 258 and `MIN_LOOKAHEAD` 262 | deflate rows at 257, 258, 259, 261, 262, 263 |
| 16,383 to 16,385 | `lit_bufsize` at memLevel 8, where a block's symbols run out | deflate rows either side |
| 32,767 to 32,769 | `w_size`, the window | deflate rows either side |
| 65,535 to 65,537 | `MAX_STORED` and `window_size`, where the window first slides | deflate rows either side |
| 131,073 and 200,000 | several slides of the window | deflate rows at both |
| memLevel 1 and 9 | `lit_bufsize` 128 and 32,768 | the two memLevel rows |
| windows 9 and 12 | a window shorter than the input, slid often | the window rows |
| level 9 | the rolling hash and `longest_match_slow` | every level-9 row |
| output a few octets at a time | `deflate_stored` sizing blocks by `avail_out`, and every resumption | the outstep rows (level 0 only through the extension, whose room is never short) |
| a match or code split across reads | inflate's `PULLBYTE` at every state | the inflate rows fed seven at a time |
| output short of a match | inflate's `MATCH` with `left` 0, and the window | the inflate rows given 300 at a time, and 70,000 octets past the window |

## Making them again

zlib-ng's source is not checked in. Fetch the 2.3.2 tag as source, read it, and
then, from a scratch directory:

    ./configure --static && make libz-ng.a
    gcc -O2 -I <zlib-ng> -o table table.c <zlib-ng>/libz-ng.a && ./table

## Licence

The tables are zlib-ng's output and do not ship. zlib-ng is under the zlib
licence, (C) 1995-2024 Jean-loup Gailly and Mark Adler.
