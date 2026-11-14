#!/bin/bash
# Writes probes-fixed.txt: what an R3 built from rebol3-source/ with fixes.patch applied prints
# for each probe in probes.r3, run in an empty folder. The build happens in a scratch copy;
# rebol3-source/ and ./r3-head are left as they are.
#   generate-fixed.sh
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(git -C "$HERE" rev-parse --show-toplevel)
SCRATCH=$(mktemp -d)
trap 'rm -rf "$SCRATCH"' EXIT
mkdir -p "$SCRATCH/scripts" "$SCRATCH/work"
cp -r "$ROOT/rebol3-source" "$SCRATCH/"
cp "$ROOT/scripts/build-r3.sh" "$ROOT/scripts/resolve-nest.r3" "$SCRATCH/scripts/"
cp "$ROOT/r3-head" "$SCRATCH/"
(cd "$SCRATCH" && patch -p1 --quiet < "$HERE/fixes.patch")
"$SCRATCH/scripts/build-r3.sh" bulk "$SCRATCH/r3-fixed" > /dev/null
cp "$HERE/probes.r3" "$SCRATCH/work/"
(cd "$SCRATCH/work" && "$SCRATCH/r3-fixed" -qs probes.r3) > "$HERE/probes-fixed.txt"
wc -l "$HERE/probes-fixed.txt"
