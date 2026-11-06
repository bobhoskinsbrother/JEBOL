#!/bin/bash
# Writes this directory's tables from bzip2's own C.
#   generate.sh <bzip2-checkout at 66c46b8> [--coverage]
set -e
LIBRARY=$1
HERE=$(cd "$(dirname "$0")" && pwd)
WORK=$(mktemp -d)
FLAGS="-O2"
if [ "$2" = "--coverage" ]; then FLAGS="-O0 --coverage"; fi
if [ "$2" = "--sanitize" ]; then FLAGS="-O1 -g -fsanitize=address,undefined"; fi
echo '#define BZ_VERSION "1.1.0"' > "$WORK/bz_version.h"
(
    cd "$WORK"
    gcc $FLAGS -I"$WORK" -I"$LIBRARY" -I"$HERE" -o table \
        "$HERE/table.c" "$LIBRARY/blocksort.c" "$LIBRARY/huffman.c" "$LIBRARY/crctable.c" \
        "$LIBRARY/randtable.c" "$LIBRARY/compress.c" "$LIBRARY/decompress.c" "$LIBRARY/bzlib.c"
)
cd "$HERE"
"$WORK/table" $(find corpus -type f \( -name '*.bz2' -o -name '*.bz2.bad' \) | LC_ALL=C sort)
if [ "$2" = "--coverage" ]; then
    (cd "$WORK" && for c in blocksort huffman compress decompress bzlib; do gcov -o . "table-$c.gcda" "$LIBRARY/$c.c" > /dev/null 2>&1 || true; done
     ls "$WORK"/*.gcov)
    echo "coverage in $WORK"
fi
wc -l bzip2-*.tsv
