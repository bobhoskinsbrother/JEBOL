#!/usr/bin/env python3
"""Audit the error ids JEBOL can raise against the ones Rebol names.

`src/boot/errors.reb` in Rebol's own tree is the catalogue: every id a real
R3 can put in `e/id`, grouped under the category that decides `e/type`. It is
the same file JEBOL's own catalogue was written from, so the two are directly
comparable and nothing has to be run to compare them.

JEBOL spells an id in two places and both are read. `SyntaxFailure` and
`EvaluationFailure` declare most of them; the rest are written where they are
thrown, beside an explicit `ErrorCategory`. Only those two shapes count. A
loose scan for any quoted lowercase word answers 781, because it takes a word
name and a message fragment for an id, and every one of those overstates what
JEBOL can actually raise.

Why this exists: `too-long` was missing and was found by needing it, which is
no way to find things. A script that answers "which ids can JEBOL not raise"
turns that into a number, the way `c-parity.py` did for the C functions.

An id JEBOL cannot raise is not automatically a gap. Thirty-two of them are
ports, files, network and security, which JEBOL reaches through the
host-grant system instead, and eight are memory and stack limits the JVM does
not let a program ask about. The Script and Syntax columns are the ones that
name behaviour JEBOL already implements, and those are worth reading the C
for one at a time.

Usage:
    scripts/error-parity.py            # the counts and the missing ids
    scripts/error-parity.py --quiet    # the counts alone
"""

import re
import sys
from collections import Counter
from pathlib import Path

PROJECT = Path(__file__).resolve().parent.parent
CATALOGUE = PROJECT / "rebol3-source" / "src" / "boot" / "errors.reb"
JAVA = PROJECT / "src" / "main" / "java"

CATEGORY_HEADING = re.compile(r"^([A-Z][A-Za-z]*):\s*\[")
ERROR_ENTRY = re.compile(r"^([a-z][a-z0-9-]*):\s+[\{\"\[]")
NOT_AN_ERROR = {"code", "type"}

SYNTAX_FAILURE = JAVA / "org" / "jebol" / "domain" / "read" / "SyntaxFailure.java"

DECLARED_WITH_ID_FIRST = re.compile(
    r'^\s*[A-Z][A-Z0-9_]*\(\s*"([a-z][a-z0-9-]*)"', re.M)
BESIDE_A_CATEGORY = re.compile(
    r'ErrorCategory\.[A-Z_]+\s*,\s*"([a-z][a-z0-9-]*)"')


def ids_rebol_names():
    """Every id in the catalogue, against the category it is filed under."""
    named = {}
    category = None
    for line in CATALOGUE.read_text().splitlines():
        heading = CATEGORY_HEADING.match(line)
        if heading:
            category = heading.group(1)
            continue
        entry = ERROR_ENTRY.match(line.strip())
        if entry and category and entry.group(1) not in NOT_AN_ERROR:
            named[entry.group(1)] = category
    return named


def ids_jebol_raises():
    """Every id JEBOL can put in `e/id`, from the two places it spells one.

    `SyntaxFailure` puts the id first and carries no category. Everything
    else writes the category and then the id, whether that is a declaration
    in `EvaluationFailure` or an error built where it is thrown -- THROW's
    four, which are control flow rather than failures, and USER's two.
    Reading only the enums misses those and undercounts by six.
    """
    raised = set(DECLARED_WITH_ID_FIRST.findall(SYNTAX_FAILURE.read_text()))
    for source in JAVA.rglob("*.java"):
        text = source.read_text(errors="replace")
        if "ErrorCategory." in text:
            raised |= set(BESIDE_A_CATEGORY.findall(text))
    return raised


def main():
    quiet = "--quiet" in sys.argv
    if not CATALOGUE.exists():
        print(f"no catalogue at {CATALOGUE} -- is rebol3-source linked?")
        return 1

    named = ids_rebol_names()
    raised = ids_jebol_raises()
    missing = sorted((named[one], one) for one in named if one not in raised)

    print(f"Rebol names {len(named)} error ids. "
          f"JEBOL can raise {len(named) - len(missing)}, "
          f"and cannot raise {len(missing)}.")
    for category, count in sorted(Counter(c for c, _ in missing).items(),
                                  key=lambda pair: -pair[1]):
        print(f"  {count:4}  {category}")

    if not quiet and missing:
        print()
        for category, one in missing:
            print(f"   {category:10} {one}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
