#!/usr/bin/env python3
"""Audit the error ids JEBOL can raise against the ones Rebol names.

`src/boot/errors.reb` in Rebol's own tree is the catalogue: every id a real
R3 can put in `e/id`, grouped under the category that decides `e/type`. It is
the same file JEBOL's own catalogue was written from, so the two are directly
comparable and nothing has to be run to compare them.

JEBOL spells an id in four places and all four are read. `SyntaxFailure`
and `EvaluationFailure` declare most of them; some are written where they are
thrown, beside an explicit `ErrorCategory`; Rebol's own library, which JEBOL
carries and runs, raises more with `cause-error` or by building an error
object; and `sys-load.reb` names two of them a fourth way, by returning the id
as a word for a later `cause-error` to raise through a variable. Only those
shapes count. A loose scan for any quoted lowercase word answers 781, because
it takes a word name and a message fragment for an id, and every one of those
overstates what JEBOL can actually raise.

Reading only the Java undercounts, and the undercount is the misleading kind:
`bad-checksum` read as missing for as long as this script had two places to
look, while `load {REBOL [checksum: #{00}] 1}` raised it. The fourth shape was
the same mistake one layer down -- `bad-header` and `bad-checksum` both leave
`sys-load` as returned words, so no `cause-error` line spells either of them.
Both were confirmed by running them here and on `./r3-head`, and they are the
only two ids the vendored library returns this way.

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
VENDORED = PROJECT / "src" / "main" / "resources" / "org" / "jebol"

DECLARED_WITH_ID_FIRST = re.compile(
    r'^\s*[A-Z][A-Z0-9_]*\(\s*"([a-z][a-z0-9-]*)"', re.M)
BESIDE_A_CATEGORY = re.compile(
    r'ErrorCategory\.[A-Z_]+\s*,\s*"([a-z][a-z0-9-]*)"')
CATEGORY_AND_ID = re.compile(
    r'ErrorCategory\.([A-Z_]+)\s*,\s*"([a-z][a-z0-9-]*)"')

CAUSED_IN_REBOL = re.compile(
    r"cause-error\s+'[A-Za-z]+\s+'([a-z][a-z0-9-]*)")
BUILT_IN_REBOL = re.compile(r"\bid:\s*'([a-z][a-z0-9-]*)")
RETURNED_IN_REBOL = re.compile(r"\breturn\s+'([a-z][a-z0-9-]*)")


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


def ids_jebol_raises(catalogued):
    """Every id JEBOL can put in `e/id`, from the two places it spells one.

    `SyntaxFailure` puts the id first and carries no category. Everything
    else writes the category and then the id, whether that is a declaration
    in `EvaluationFailure` or an error built where it is thrown -- THROW's
    four, which are control flow rather than failures, and USER's two.
    Reading only the enums misses those and undercounts by six.
    """
    return ids_the_java_declares() | ids_the_vendored_library_raises(catalogued)


def ids_the_java_declares():
    """Every id spelled in JEBOL's own Java, which is where one can be made up.

    Read on its own as well as with the rest, because an id JEBOL raises and
    Rebol has never heard of is the mirror of an id Rebol names and JEBOL
    cannot raise, and the worse of the two: a script catching by id meets a
    name that is in no catalogue and no documentation. `too-deep` and
    `nesting-too-deep` were both of those, and both are gone -- a real
    3.22.5 answers `stack-overflow` for what either of them reported.
    """
    declared = set(DECLARED_WITH_ID_FIRST.findall(SYNTAX_FAILURE.read_text()))
    for source in JAVA.rglob("*.java"):
        text = source.read_text(errors="replace")
        if "ErrorCategory." in text:
            declared |= set(BESIDE_A_CATEGORY.findall(text))
    return declared


def categories_jebol_files_ids_under():
    """Every id JEBOL raises beside a category, against that category.

    An id filed under the wrong category answers the wrong `e/type`, which
    a script reads as readily as `e/id`: `past-end` is Script in the
    catalogue, and JEBOL answering Syntax for it is the same class of
    defect as not raising it at all. The same id can be raised in more than
    one place, so every spelling is kept and compared.
    """
    filed = {one: {"Syntax"} for one
             in DECLARED_WITH_ID_FIRST.findall(SYNTAX_FAILURE.read_text())}
    for source in JAVA.rglob("*.java"):
        text = source.read_text(errors="replace")
        if "ErrorCategory." not in text:
            continue
        for category, one in CATEGORY_AND_ID.findall(text):
            filed.setdefault(one, set()).add(category.capitalize())
    return filed


def ids_the_vendored_library_raises(catalogued):
    """Every id Rebol's own library raises, out of the copy JEBOL runs.

    A commented line does not raise anything, and `mezz-help.reb` alone has
    four `; either err/id = 'protocol` lines that would otherwise count.

    A returned word counts because `sys-load` says so in its own header --
    "Syntax errors are returned as words" -- and its caller hands that word
    straight to `cause-error 'syntax`. Only ids the catalogue names are kept,
    so an ordinary word a function returns is not mistaken for one.
    """
    raised = set()
    for source in VENDORED.rglob("*.reb"):
        for line in source.read_text(errors="replace").splitlines():
            if line.lstrip().startswith(";"):
                continue
            raised |= set(CAUSED_IN_REBOL.findall(line))
            raised |= set(BUILT_IN_REBOL.findall(line))
            raised |= set(RETURNED_IN_REBOL.findall(line)) & catalogued
    return raised


def main():
    quiet = "--quiet" in sys.argv
    if not CATALOGUE.exists():
        print(f"no catalogue at {CATALOGUE} -- is rebol3-source linked?")
        return 1

    named = ids_rebol_names()
    raised = ids_jebol_raises(set(named))
    missing = sorted((named[one], one) for one in named if one not in raised)

    print(f"Rebol names {len(named)} error ids. "
          f"JEBOL can raise {len(named) - len(missing)}, "
          f"and cannot raise {len(missing)}.")
    for category, count in sorted(Counter(c for c, _ in missing).items(),
                                  key=lambda pair: -pair[1]):
        print(f"  {count:4}  {category}")

    invented = sorted(one for one in ids_the_java_declares()
                      if one not in named)
    print(f"Ids JEBOL raises that Rebol does not name: {len(invented)}.")

    misfiled = sorted(
        (one, sorted(under - {named[one]}), named[one])
        for one, under in categories_jebol_files_ids_under().items()
        if one in named and under - {named[one]})
    print(f"Of the ids JEBOL files under a category, {len(misfiled)} "
          f"disagree with the catalogue.")

    if not quiet and invented:
        print()
        for one in invented:
            print(f"   invented   {one}")

    if not quiet and missing:
        print()
        for category, one in missing:
            print(f"   {category:10} {one}")
    if not quiet and misfiled:
        print()
        for one, under, wanted in misfiled:
            print(f"   {one:20} JEBOL {'/'.join(under):10} "
                  f"catalogue {wanted}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
