#!/bin/bash
# Writes probes.txt: what ./r3-head prints for each probe in probes.r3, run in an empty folder.
#   generate.sh
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(git -C "$HERE" rev-parse --show-toplevel)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
(cd "$WORK" && "$ROOT/r3-head" -qs "$HERE/probes.r3") > "$HERE/probes.txt"
wc -l "$HERE/probes.txt"
