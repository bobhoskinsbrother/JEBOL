#!/bin/zsh
#
# Builds a Rebol binary from the vendored source in rebol3-source/.
#
#   scripts/build-r3.sh [core|bulk] [output]      default: bulk, ./r3-head
#
# Why this exists. The `./r3` that was downloaded is a release build, and a
# release is always older than the checkout beside it. Four places have been
# found where the two disagree, and each cost time before the cause was known,
# because a binary that answers differently from its own source is a fourth
# authority that contradicts the third. Building from the checkout makes the
# two the same thing.
#
# Rebol's own build tool is Siskin, a separate download. Nothing here needs it:
# Siskin's job is to read make/rebol3.nest, run the pre-make step and call the
# compiler, and the first of those is what resolve-nest.r3 does instead.
#
# The pre-make step needs a working Rebol to run, which is what ./r3 is for.
# So the old binary builds the new one and is then only needed again to build
# the next.

set -e
here=${0:a:h}
jebol=${here:h}
rebol=$jebol/rebol3-source
bootstrap=$jebol/r3

product=${1:-bulk}
out=${2:-$jebol/r3-head}
spec=$jebol/build/r3/spec-$product.reb

if [[ ! -x $bootstrap ]]; then
  echo "no ./r3 to build with: the pre-make step is a Rebol script" >&2
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
  -o $out

echo "built $out"
$out --version | head -1
