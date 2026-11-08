#!/bin/bash
# Writes transcripts.txt: what ./r3-head prints, at a terminal and through pipes, for every case in cases.txt.
#   generate.sh
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(git -C "$HERE" rev-parse --show-toplevel)
python3 -I "$HERE/drive.py" "$HERE/cases.txt" "$ROOT/r3-head" > "$HERE/transcripts.txt"
grep -c '^== ' "$HERE/transcripts.txt"
if grep -q '^verdict: ' "$HERE/transcripts.txt"; then
    grep -B5 '^verdict: ' "$HERE/transcripts.txt"
    exit 1
fi
