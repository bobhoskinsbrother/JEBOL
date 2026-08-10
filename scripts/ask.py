#!/usr/bin/env python3
"""Ask a real REBOL what an expression does, in corpus shape.

The other direction from differential.py. That one takes entries we already
believe and checks them; this one takes expressions we have no opinion about
and writes down what REBOL says, ready to paste into a .corpus file.

This is what makes a boundary sweep affordable. Writing thirty entries by
hand means guessing thirty answers and being wrong at some rate; generating
them means the answers are observations, and the work left is deciding which
boundaries to probe and explaining why each one matters.

Two things it deliberately does not do. It does not write the note lines,
because a note says why an entry exists and only a person knows that. And it
does not add anything to the corpus itself: every entry gets read before it
is kept, because a generated entry is evidence about this build and not yet
a decision about JEBOL.

Usage:
    python3 scripts/ask.py --prefix series 'first [1 2 3]' 'first []'
    python3 scripts/ask.py --prefix series --file cases.txt
    python3 scripts/ask.py --types --prefix loading '10x20' '1.2.3'
"""

import argparse
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from differential import (RESULT_MARKER, ask_rebol, run_script,  # noqa: E402
                          types_script_for)

REPO = Path(__file__).resolve().parent.parent


def slug(expression, already_used):
    """A readable id derived from the expression itself.

    Meant to be renamed. A generated id says what was typed and an entry's
    id should say what is being tested, so `first-of-an-empty-block` beats
    `first` every time. The counter only stops two cases silently sharing
    an id while nobody has got round to it.
    """
    words = re.findall(r"[a-zA-Z]+|\d+", expression)
    stem = "-".join(words).lower()[:60] or "expression"
    if stem not in already_used:
        already_used.add(stem)
        return stem
    for suffix in range(2, 99):
        candidate = f"{stem}-{suffix}"
        if candidate not in already_used:
            already_used.add(candidate)
            return candidate
    return stem


def outcome_lines(expression, as_types):
    """The claim fields for one expression, as the corpus writes them."""
    if as_types:
        raw = run_script(types_script_for(expression))
        kinds = re.sub(r"#\(([\w-]+)!\)", r"\1", raw)
        if not kinds.startswith("TYPES"):
            return ["--- SKIPPED: no types came back"]
        return ["--- types", kinds[len("TYPES"):].strip()]

    printed, result = ask_rebol(expression)
    lines = []
    if printed:
        lines += ["--- prints", printed]
    if result.startswith("ERROR "):
        _, category, identifier = result.split(None, 2)
        lines += [f"--- error {category.lower()} {identifier}"]
    elif result.startswith("VALUE "):
        # No normalising. The corpus writes what this build molds, so
        # rewriting #(true) to true here would generate entries that
        # disagree with the binary on purpose.
        molded = result[len("VALUE "):]
        if molded == "#(unset)":
            lines += ["--- unset"]
        else:
            lines += ["--- result", molded]
    else:
        lines += [f"--- SKIPPED: {result or 'nothing came back'}"]
    return lines


def entry_for(expression, prefix, as_types, already_used):
    """One corpus entry, minus the notes a person still has to write."""
    return "\n".join([
        f"--- id {prefix}/{slug(expression, already_used)}",
        "--- origin confirmed against R3 3.22.1",
        "--- code",
        expression,
        *outcome_lines(expression, as_types),
    ])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("expressions", nargs="*")
    parser.add_argument("--prefix", default="ask", help="the id prefix to use")
    parser.add_argument("--types", action="store_true",
                        help="ask what datatypes the code loads as, not what it evaluates to")
    parser.add_argument("--file", help="read expressions from a file, one per line")
    options = parser.parse_args()

    expressions = list(options.expressions)
    if options.file:
        expressions += [line for line in Path(options.file).read_text().splitlines()
                        if line.strip() and not line.startswith("#")]
    if not expressions:
        parser.error("give at least one expression, or --file")

    if not (REPO / "r3").exists():
        print("no ./r3 here. See docs/allium-checker-notes.md for where to get one.")
        return 2

    already_used = set()
    for expression in expressions:
        print(entry_for(expression, options.prefix, options.types, already_used))
        print()
    return 0


if __name__ == "__main__":
    sys.exit(main())
