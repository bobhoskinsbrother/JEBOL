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

# This machine's system and architecture, not fixed ones: an arm64 binary will
# not run on an Intel Mac and the compiler cross-compiles happily without
# complaining.
case $(uname -s) in
  Darwin) os=macos; release_os=macos ;;
  Linux)  os=linux; release_os=linux ;;
  *) echo "$0 builds on macOS and Linux, not $(uname -s)" >&2; exit 1 ;;
esac
case $(uname -m) in
  arm64|aarch64) arch=arm64; rebol_arch=arm64; release_arch=arm64 ;;
  x86_64)        arch=x86_64; rebol_arch=x86_64; release_arch=x64 ;;
  *) echo "$0 builds for arm64 and x86_64, not $(uname -m)" >&2; exit 1 ;;
esac

if [[ ! -x $bootstrap ]]; then
  cat >&2 <<WHY
no ./r3-head to build with: the pre-make step is a Rebol script.

To get the first one, fetch a release of the same fork and let it build its
replacement from the checkout. A release is older than rebol3-source/ and must
not be consulted for an answer -- that is what .gitignore's note is about -- but
bootstrapping is the one job it is right for, because what it produces is the
checkout's own build. For this machine:

    curl -sL -o /tmp/r3.gz https://github.com/Oldes/Rebol3/releases/download/3.22.1/rebol3-bulk-$release_os-$release_arch.gz
    gunzip -c /tmp/r3.gz > ./r3-head && chmod +x ./r3-head
    scripts/build-r3.sh          # overwrites ./r3-head with the checkout's
    rm /tmp/r3.gz

JEBOL cannot bootstrap itself yet: scripts/resolve-nest.r3 does not run on it.
WHY
  exit 1
fi

mkdir -p ${spec:h}
$bootstrap $here/resolve-nest.r3 $rebol/make/ $product $spec $rebol_arch $os

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
common_defines=(-DENDIAN_LITTLE -DREB_EXE -DUNICODE
  -D_FILE_OFFSET_BITS=64 -D__LP64__
  -DREBOL_OPTIONS_FILE='"gen-config.h"')
includes=(-Isrc/include -Isrc/core/lz4 -Isrc/include/brotli)

if [[ $os == macos ]]; then
  clang -O2 -w \
    -arch $arch -mmacosx-version-min=10.9 \
    -DTO_MACOS -DUSE_OLD_PIPE $common_defines $includes \
    ${=sources} src/os/host-main.c \
    -framework AppKit -framework Foundation -framework CoreFoundation \
    -framework CoreGraphics -framework CoreImage -framework ImageIO \
    -framework CoreServices -framework CoreMIDI -framework AudioToolbox \
    -lm -liconv \
    -o $part_built
else
  # target-linux and common in rebol3.nest: TO_LINUX, -fPIC, libm and libdl
  gcc -O2 -w -fPIC \
    -DTO_LINUX $common_defines $includes \
    ${=sources} src/os/host-main.c \
    -lm -ldl -lpthread \
    -o $part_built
fi

mv $part_built $out
echo "built $out"
$out --version | head -1
