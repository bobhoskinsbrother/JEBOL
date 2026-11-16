#!/bin/bash
# Writes this directory's transcripts and table from Rebol-Brotli's and Brotli's own C.
#   generate.sh
set -e
TAG=0.1.0
FIXED_BROTLI=v1.2.1
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(git -C "$HERE" rev-parse --show-toplevel)
R3="$ROOT/r3-head"
CORE_BROTLI="$ROOT/rebol3-source/src/core/brotli"
CORE_HEADERS="$ROOT/rebol3-source/src/include/brotli"
WORK=$(mktemp -d)

git clone -q --depth 1 --branch "$TAG" https://github.com/Oldes/Rebol-Brotli.git "$WORK/Rebol-Brotli" 2> /dev/null
git clone -q --depth 1 --branch "$FIXED_BROTLI" https://github.com/google/brotli.git "$WORK/brotli-fixed" 2> /dev/null

mkdir -p "$WORK/include"
cp -r "$CORE_HEADERS" "$WORK/include/"
(cd "$WORK/Rebol-Brotli/src" && "$R3" -qs brotli-rebol-extension.r3)
gcc -O2 -shared -fPIC -w -DENDIAN_LITTLE -DTO_LINUX -DTO_LINUX_X64 -D__LP64__ \
    -I"$WORK/include" -I"$WORK/Rebol-Brotli/src" -I"$CORE_BROTLI" -o "$WORK/brotli-x64.rebx" \
    "$WORK"/Rebol-Brotli/src/*.c \
    "$CORE_BROTLI"/common/{constants,context,transform,dictionary,shared_dictionary}.c \
    "$CORE_BROTLI"/dec/*.c "$CORE_BROTLI"/enc/*.c -lm
REBX="$WORK/brotli-x64.rebx"

mkdir -p "$WORK/table/include"
cp -r "$CORE_BROTLI"/* "$WORK/table/"
cp -r "$CORE_HEADERS" "$WORK/table/include/"
cp "$WORK/brotli-fixed/c/dec/decode.c" "$WORK/table/dec/decode.c"
cp "$WORK/brotli-fixed/c/include/brotli/decode.h" "$WORK/table/include/brotli/decode.h"
gcc -O2 -w -I"$WORK/table/include" -I"$WORK/table" -o "$WORK/stream-table" "$HERE/stream-table.c" \
    "$WORK"/table/common/{constants,context,transform,dictionary,shared_dictionary,platform}.c \
    "$WORK"/table/dec/*.c "$WORK"/table/enc/*.c -lm

run_in_a_fresh_folder() {
    local script=$1; shift
    local folder
    folder=$(mktemp -d -p "$WORK")
    cp "$HERE/$script" "$folder/"
    (cd "$folder" && "$R3" -qs "$script" "$@" 2>&1)
}

run_in_a_fresh_folder module-answers.r3 "$REBX" > "$HERE/module-answers.txt"

SCENARIOS=$(run_in_a_fresh_folder module-streams.r3 "$REBX" 0)
: > "$HERE/module-streams.txt"
for index in $(seq 1 "$SCENARIOS"); do
    if ! run_in_a_fresh_folder module-streams.r3 "$REBX" "$index" >> "$HERE/module-streams.txt" 2> /dev/null; then
        echo "crashed" >> "$HERE/module-streams.txt"
    fi
done

CASES=$(run_in_a_fresh_folder module-stream-case.r3 "$REBX" 0)
: > "$HERE/module-stream-case.txt"
for index in $(seq 1 "$CASES"); do
    if answer=$(run_in_a_fresh_folder module-stream-case.r3 "$REBX" "$index" 2> /dev/null); then
        echo "$answer" >> "$HERE/module-stream-case.txt"
    else
        echo "${answer}crashed" >> "$HERE/module-stream-case.txt"
    fi
done

"$WORK/stream-table" "$HERE/stream-cases.txt" "$HERE/../mapsdatazrh-first-30k.bin" > "$HERE/stream-table.tsv"

rm -rf "$WORK"
wc -l "$HERE"/module-*.txt "$HERE/stream-table.tsv"
