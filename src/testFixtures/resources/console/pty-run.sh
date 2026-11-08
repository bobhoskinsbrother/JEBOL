#!/bin/bash
# Runs a command at a terminal of its own, or through pipes, the same way for ./r3-head and for JEBOL.
#   pty-run.sh terminal|pipe <folder> <command> [arguments...]
# What the command prints goes to stdout, and what is typed is read from stdin.
# <folder> receives stderr, the exit code, and at a terminal `stty -g` before and after.
set -u
MODE=$1
FOLDER=$2
shift 2
COMMAND=$(printf '%q ' "$@")
INSIDE="stty -g > '$FOLDER/before'; $COMMAND 2> '$FOLDER/stderr'; echo \$? > '$FOLDER/exit'; stty -g > '$FOLDER/after'"

if [ "$MODE" = pipe ]; then
    "$@" 2> "$FOLDER/stderr"
    echo $? > "$FOLDER/exit"
elif [ "$(uname)" = Darwin ]; then
    exec script -q /dev/null /bin/sh -c "$INSIDE"
else
    exec script -q -f -E always -c "$INSIDE" /dev/null
fi
