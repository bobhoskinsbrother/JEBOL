#!/bin/bash
# Writes probes.txt: what ./r3-head prints for each probe in probes.r3.
#   generate.sh
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(git -C "$HERE" rev-parse --show-toplevel)
(cd "$HERE" && "$ROOT/r3-head" -qs probes.r3) > "$HERE/probes.txt"
wc -l "$HERE/probes.txt"
