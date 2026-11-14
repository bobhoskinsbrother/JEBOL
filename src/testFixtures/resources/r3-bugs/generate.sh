#!/bin/bash
# Writes probes.txt: what ./r3-head prints for each probe in probes.r3, run in an empty folder.
# R3 changes into the folder that holds the script, so the script is copied into that folder.
#   generate.sh
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(git -C "$HERE" rev-parse --show-toplevel)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
cp "$HERE/probes.r3" "$WORK/"
(cd "$WORK" && "$ROOT/r3-head" -qs probes.r3) > "$HERE/probes.txt"
wc -l "$HERE/probes.txt"
