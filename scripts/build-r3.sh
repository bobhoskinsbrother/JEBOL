#!/bin/zsh
#
# Builds a Rebol binary from the vendored source in rebol3-source/.
#
#   scripts/build-r3.sh [core|bulk] [output]      default: bulk, ./r3-head
#
# Why this exists. A downloaded release build is always older than the checkout
# beside it. Four places have been found where the two disagree, and each cost
# time before the cause was known, because a binary that answers differently
# from its own source is a fourth authority that contradicts the third.
# Building from the checkout makes the two the same thing, and `./r3-head` is
# the only Rebol this project consults.
#
# Rebol's own build tool is Siskin, a separate download. Nothing here needs it:
# Siskin's job is to read make/rebol3.nest, run the pre-make step and call the
# compiler, and the first of those is what resolve-nest.r3 does instead.
#
# The pre-make step is itself a Rebol script, so building a Rebol needs a Rebol
# already working. `./r3-head` bootstraps its own replacement, which is why the
# compiler writes to a temporary file and only moves it into place on success:
# a half-written binary here would leave nothing able to build the next one.

set -e
here=${0:a:h}
jebol=${here:h}
rebol=$jebol/rebol3-source
bootstrap=$jebol/r3-head

product=${1:-bulk}
out=${2:-$jebol/r3-head}
spec=$jebol/build/r3/spec-$product.reb
part_built=$out.part

if [[ ! -x $bootstrap ]]; then
  echo "no ./r3-head to build with: the pre-make step is a Rebol script" >&2
  exit 1
fi

mkdir -p ${spec:h}
$bootstrap $here/resolve-nest.r3 $rebol/make/ $product $spec

# pre-make must run from make/, because it reaches its own tools by relative path
(cd $rebol/make && $bootstrap pre-make.r3 $spec > /dev/null)

sources=$(python3 - $spec <<'PY'
import re, sys
text = open(sys.argv[1]).read()
def listed(key):
    return re.findall(r'%(\S+)', re.search(key + r':\s*\[(.*?)\]', text, re.S).group(1))
print(' '.join('src/' + name for name in listed('core-files') + listed('host-files')))
PY
)

cd $rebol
clang -O2 -w \
  -arch arm64 -mmacosx-version-min=10.9 \
  -DTO_MACOS -DENDIAN_LITTLE -DREB_EXE -DUNICODE -DUSE_OLD_PIPE \
  -D_FILE_OFFSET_BITS=64 -D__LP64__ \
  -DREBOL_OPTIONS_FILE='"gen-config.h"' \
  -Isrc/include -Isrc/core/lz4 -Isrc/include/brotli \
  ${=sources} src/os/host-main.c \
  -framework AppKit -framework Foundation -framework CoreFoundation \
  -framework CoreGraphics -framework CoreImage -framework ImageIO \
  -framework CoreServices -framework CoreMIDI -framework AudioToolbox \
  -lm -liconv \
  -o $part_built

mv $part_built $out
echo "built $out"
$out --version | head -1
