# Where this came from

`mapsdatazrh-first-30k.bin` is the first thirty thousand bytes of
`tests/testdata/mapsdatazrh` from Google's Brotli project, which is the same
project whose C the interpreter's Brotli is ported from. It is MIT licensed;
the licence is reproduced in `LICENSE`.

It is here because nothing generated catches what it catches. The two hardest
compression levels redo their block division ten times where the ten below them
redo it three, and the difference only shows on a few hundred kilobytes of
varied structured data. Every input this project could invent -- text, noise,
repetition, and mixtures of them -- gives the same answer after three rounds as
after ten. Three files in Brotli's own corpus do not, and this is the smallest
slice of the smallest of them that still tells them apart.

Downloaded from the `testdata.txz` asset of the `dev/null` release at
https://github.com/google/brotli/releases
