#!/bin/bash
# Writes this directory's transcripts from Rebol-SQLite's own C, loaded into ./r3-head.
#   generate.sh
set -e
TAG=3.46.0.0
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(git -C "$HERE" rev-parse --show-toplevel)
R3="$ROOT/r3-head"
WORK=$(mktemp -d)
git clone -q --depth 1 --branch "$TAG" https://github.com/Siskin-framework/Rebol-SQLite.git "$WORK/Rebol-SQLite" 2> /dev/null
SOURCE="$WORK/Rebol-SQLite"
(cd "$SOURCE/src" && "$R3" -qs sqlite-rebol-extension.r3)
gcc -O2 -shared -fPIC -fpermissive -w -pthread \
    -DENDIAN_LITTLE -DTO_LINUX -DTO_LINUX_X64 -D_FILE_OFFSET_BITS=64 -D__LP64__ \
    -I"$SOURCE/src" -I"$SOURCE/sqlite" -o "$WORK/sqlite-x64.rebx" \
    "$SOURCE/sqlite/sqlite3.c" "$SOURCE"/src/*.c
REBX="$WORK/sqlite-x64.rebx"
SCHEME="$SOURCE/sqlite-scheme.reb"

run_in_a_fresh_folder() {
    local script=$1; shift
    local folder
    folder=$(mktemp -d -p "$WORK")
    cp "$HERE/$script" "$folder/"
    (cd "$folder" && "$R3" -qs "$script" "$@" 2>&1)
}

for script in module-commands module-files module-trace module-secure; do
    run_in_a_fresh_folder "$script.r3" "$REBX" | sed -E 's/PROFILE: [0-9]+ns/PROFILE: <n>ns/' > "$HERE/$script.txt"
done
run_in_a_fresh_folder module-scheme.r3 "$REBX" "$SCHEME" > "$HERE/module-scheme.txt"

CASES=$(run_in_a_fresh_folder module-one-case.r3 "$REBX" 0)
: > "$HERE/module-one-case.txt"
for index in $(seq 1 "$CASES"); do
    if answer=$(run_in_a_fresh_folder module-one-case.r3 "$REBX" "$index" 2> /dev/null); then
        echo "$answer" >> "$HERE/module-one-case.txt"
    else
        echo "${answer}crashed" >> "$HERE/module-one-case.txt"
    fi
done

rm -rf "$WORK"
wc -l "$HERE"/module-*.txt
