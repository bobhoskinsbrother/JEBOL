#!/usr/bin/env python3
"""Audit JEBOL's Java against the functions Rebol implements in C.

Three sources, and each answers a different question.

`src/boot/natives.reb` and `src/boot/actions.reb` in Rebol's own tree say
*which* functions Rebol writes in C. Those are the ones that must be Java
here, by the layer rule in TODO: a function Rebol writes in REBOL is
imported from its file instead, and one written in Java would be a fork.

`src/test/resources/r3/c-surface.txt` holds the same declarations in the same
shape, written from Rebol's source by `scripts/c-surface.py`. That is what a
JEBOL surface is compared against.

It used to be compared against a dump of a running 3.22.1 instead. Two things
were wrong with that beyond being unrepeatable. A function the C declares and
that build had not exposed was skipped silently, so the audit had a blind spot
it did not report. And nothing in the dump could be checked: where it and the
source disagreed there was no way to tell which was right, except that the
source always was.

`build/jebol-surface.txt`, written by SurfaceReportTest, says the same for
JEBOL's native registry -- which is to say, for the Java.

`build/jebol-library.txt`, written by PortingBacklogTest, says which names a
booted JEBOL answers to at all, whether the answer comes from Java, the
prelude or a borrowed Rebol file. Both are needed to tell a function that is
absent from one that is present in the wrong layer.

What it prints, per C function, is one of five verdicts:

    MISSING      Rebol has it in C and JEBOL does not have it at all.
    WRONG LAYER  Rebol has it in C and JEBOL has it in REBOL. A fork by the
                 layer rule, and a place where Rebol's own file cannot be
                 borrowed over the top because there is nothing to borrow.
    REFINEMENTS  JEBOL has the Java, and the set of refinements differs.
    ARGUMENTS    JEBOL has the Java, and the arguments differ in number or
                 name.
    TYPES        JEBOL has the Java, and a parameter accepts a different set
                 of datatypes -- narrower is a call Rebol takes and JEBOL
                 refuses, wider is one JEBOL takes and should not.

Usage:
    ./gradlew test --tests 'org.jebol.suite.SurfaceReportTest' \
                   --tests 'org.jebol.suite.PortingBacklogTest'
    python3 scripts/c-parity.py            # the whole audit
    python3 scripts/c-parity.py --summary  # counts only
"""

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
REBOL_SOURCE = Path.home() / "Code" / "personal" / "rebol3-source" / "src" / "boot"
C_DECLARATIONS_KINDS = ("ACTION ", "NATIVE ")
C_SURFACE = REPO / "src" / "test" / "resources" / "r3" / "c-surface.txt"
JEBOL_SURFACE = REPO / "build" / "jebol-surface.txt"
JEBOL_LIBRARY = REPO / "build" / "jebol-library.txt"

# A name that is a word in the C's spec block but not an argument.
SPEC_NOISE = re.compile(r'^(return|throw|catch|local)$')


def c_implemented():
    """Every function Rebol implements in C, by name, with where it is declared.

    Two places, and reading only the first is what made this report's
    MISSING: 0 untrustworthy for months.

    `boot/natives.reb` and `boot/actions.reb` declare 164 natives and 60
    actions. The C ships 54 natives more, each carrying its own spec in a
    comment above the function -- `//\tclamp: native [...]` -- and mentioned in
    no boot file. `binary` is one of them, and it was the word `prot-tls.reb`
    stopped on while this report said nothing was missing.

    The C symbol is no guide to the REBOL name: `REBNATIVE(asciiq)` is
    `ascii?`. The comment is the declaration, so the comment is what is read.
    """
    found = {}
    for file, kind in (("natives.reb", "native"), ("actions.reb", "action")):
        text = (REBOL_SOURCE / file).read_text()
        for name in re.findall(
                r'^([A-Za-z0-9?!*+\-=<>/&|~%]+):\s*' + kind + r'\b', text, re.M):
            found[name] = kind
    for source in sorted((REBOL_SOURCE.parent / "core").glob("*.c")):
        text = source.read_text(errors="replace")
        for name in re.findall(
                r'^//\t([A-Za-z0-9?!*+\-=<>/&|~%]+):\s*native\s*\[', text, re.M):
            found.setdefault(name, "native")
    return found


def typesets():
    """Each of Rebol's typesets as the set of datatypes it holds.

    Read from the TYPESET lines of `c-surface.txt`, which scripts/c-surface.py
    builds from the last column of `boot/types.reb`. A typeset is a name for a
    set, and the two surfaces write the same acceptance two ways: Rebol declares
    `value<number!>` where JEBOL's registry enumerates integer!, decimal! and
    percent!. Comparing the names reports differences that are not differences,
    and there are enough of those to bury the real findings.
    """
    expansions = {}
    if not C_SURFACE.exists():
        return expansions
    for line in C_SURFACE.read_text().splitlines():
        if not line.startswith("TYPESET ") or " |" not in line:
            continue
        name, members = line[len("TYPESET "):].split(" |", 1)
        expansions[name.strip()] = set(members.split())
    # A typeset may name another. Two passes settle every one R3 has.
    for _ in range(3):
        for name, members in expansions.items():
            widened = set()
            for member in members:
                widened |= expansions.get(member, {member})
            expansions[name] = widened
    return expansions


TYPESETS = typesets()


def datatypes(names):
    """A declared set of datatypes with every typeset name expanded."""
    expanded = set()
    for name in names:
        expanded |= TYPESETS.get(name, {name})
    return expanded


def c_declared_surfaces():
    """Rebol's declared specs, in the shape a comparison needs.

    The ACTION and NATIVE lines of `c-surface.txt`, which is written from
    `boot/actions.reb` and `boot/natives.reb`. Every function the C declares is
    here by construction, so nothing can be skipped for being unreachable.
    """
    lines = [line[line.index(" ") + 1:]
             for line in C_SURFACE.read_text().splitlines()
             if line.startswith(C_DECLARATIONS_KINDS)]
    return parse_shapes(lines)


def parse_surface(path):
    """A surface file as {name: (arguments, refinements, types)}."""
    return parse_shapes(path.read_text().splitlines())


def parse_shapes(lines):
    """Lines of `name | shape` as {name: (arguments, refinements, types)}.

    `arguments` is the ordered list of argument names, including those that
    belong to a refinement. `refinements` is the set of refinement names.
    `types` maps an argument name to the set of datatype spellings it takes,
    and an argument that takes everything is simply absent from it.
    """
    surfaces = {}
    for line in lines:
        if " |" not in line:
            continue
        name, shape = line.split(" |", 1)
        name = name.strip()
        arguments = []
        refinements = set()
        types = {}
        # A datatype block holds spaces -- `value<integer! decimal!>` -- so the
        # shape cannot be split on whitespace. Each item is a name, optionally
        # a slash in front of it, optionally a block of datatypes after it.
        #
        # The name pattern deliberately excludes < and >, which the function
        # names `<` and `<=` contain: the name has already been split off, and
        # allowing them here made `value1<scalar!` match as one argument.
        # /local is a refinement by spelling only. Its words are the
        # function's own working names: stack slots the C fills itself --
        # `*DS_ARG(4)` in Loop_All is FORSKIP's `orig` -- and locals in a
        # JEBOL function. Neither language expects a caller to supply them,
        # so the refinement and everything after it is left out of the
        # comparison. Counting them made FORSKIP look short of two arguments
        # it has no use for.
        in_locals = False
        for slash, argument, inside in re.findall(
                r"(/?)([A-Za-z0-9?!*+\-']+)(?:<([^>]*)>)?", shape):
            if slash:
                in_locals = argument == "local"
                if not in_locals:
                    refinements.add(argument)
                continue
            if in_locals or SPEC_NOISE.match(argument):
                continue
            arguments.append(argument)
            if inside:
                types[argument] = datatypes(inside.split())
        surfaces[name] = (arguments, refinements, types)
    return surfaces


def verdicts(name, theirs, ours):
    """Every way the two surfaces differ, as a list of lines."""
    their_args, their_refinements, their_types = theirs
    our_args, our_refinements, our_types = ours
    lines = []

    missing_refinements = sorted(their_refinements - our_refinements)
    extra_refinements = sorted(our_refinements - their_refinements)
    if missing_refinements or extra_refinements:
        piece = []
        if missing_refinements:
            piece.append("missing /" + " /".join(missing_refinements))
        if extra_refinements:
            piece.append("extra /" + " /".join(extra_refinements))
        lines.append(f"REFINEMENTS  {name}: " + "; ".join(piece))

    if len(their_args) != len(our_args):
        lines.append(
            f"ARGUMENTS    {name}: R3 takes {len(their_args)} "
            f"({' '.join(their_args)}), JEBOL takes {len(our_args)} "
            f"({' '.join(our_args)})")

    for argument in their_args:
        if argument not in our_types or argument not in their_types:
            continue
        narrower = their_types[argument] - our_types[argument]
        wider = our_types[argument] - their_types[argument]
        if narrower:
            lines.append(
                f"TYPES        {name}/{argument}: JEBOL refuses "
                + " ".join(sorted(narrower)))
        if wider:
            lines.append(
                f"TYPES        {name}/{argument}: JEBOL accepts "
                + " ".join(sorted(wider)) + " and R3 does not")
    return lines


def main():
    summary_only = "--summary" in sys.argv
    if not JEBOL_SURFACE.exists():
        sys.exit("run ./gradlew test --tests 'org.jebol.suite.SurfaceReportTest' first")

    in_c = c_implemented()
    theirs = c_declared_surfaces()
    ours = parse_surface(JEBOL_SURFACE)
    reachable = set(JEBOL_LIBRARY.read_text().split()) if JEBOL_LIBRARY.exists() else set()

    missing = []
    wrong_layer = []
    differences = []
    matching = []
    for name in sorted(in_c):
        if name not in ours:
            (wrong_layer if name in reachable else missing).append(name)
            continue
        found = verdicts(name, theirs[name], ours[name])
        (differences if found else matching).append(name)
        if found and not summary_only:
            differences_lines.extend(found)

    print(f"C FUNCTIONS: {len(in_c)} "
          f"({sum(1 for k in in_c.values() if k == 'native')} natives, "
          f"{sum(1 for k in in_c.values() if k == 'action')} actions)")
    print(f"  in Java and matching R3's surface: {len(matching)}")
    print(f"  in Java with a surface difference: {len(differences)}")
    print(f"  in REBOL here, not Java:           {len(wrong_layer)}")
    print(f"  not there at all:                  {len(missing)}")

    if missing:
        print("\nMISSING -- Rebol writes these in C and JEBOL has not got them:")
        for at in range(0, len(missing), 6):
            print("    " + "  ".join(missing[at:at + 6]))

    if wrong_layer:
        print("\nWRONG LAYER -- Rebol writes these in C and JEBOL writes them in REBOL:")
        for at in range(0, len(wrong_layer), 6):
            print("    " + "  ".join(wrong_layer[at:at + 6]))

    if not summary_only and differences_lines:
        print("\nSURFACE DIFFERENCES, most serious first:")
        for kind in ("REFINEMENTS", "ARGUMENTS", "TYPES"):
            for line in differences_lines:
                if line.startswith(kind):
                    print("  " + line)


differences_lines = []

if __name__ == "__main__":
    main()
