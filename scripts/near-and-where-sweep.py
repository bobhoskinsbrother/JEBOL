"""Ask a real Rebol where every refusal is located, and write the answers as a TSV.

    python3 scripts/near-and-where-sweep.py --out src/testFixtures/resources/org/jebol/near-and-where-as-r3-answers.tsv

Every native and action named in `rebol3-source`, and every infix operator
whose symbol `boot/ops.reb` gives it, is called once per typed argument: the
arguments before the chosen one are given a value their datatype accepts, the
chosen one is given a value its datatype refuses, written by hand and again
made by a paren. A refusal is found before the call begins, so the native is
never run and no case writes, waits or quits.

The cases come from `c-surface.txt`, which is Rebol's own source read by
`scripts/c-surface.py`, and each answer comes from `./r3-head`, one case per
process. A case whose process does not exit cleanly is reported on standard
error and left out, because a raise from a body the case never meant to reach
would otherwise sit in the file as an answer.

The answer for each case is the same three lines `NearAndWhereAnswerAsR3DoesTest`
reads out of JEBOL: the id, the near molded flat, and the where cut at its try
-- the calls a script wrote, without the boot's own walk under them.
"""
import argparse
import glob
import os
import re
import shutil
import subprocess
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
C_SURFACE = os.path.join(REPO, "src", "testFixtures", "resources", "r3", "c-surface.txt")
OPS = os.path.join(REPO, "rebol3-source", "src", "boot", "ops.reb")
SCRATCH = os.path.join(REPO, "build", "near-and-where-sweep")

PROBE = """Rebol []
e: try [{case}]
w: either all [block? e/where p: find e/where 'try] [copy/part e/where next p] [e/where]
print e/id
print mold/flat e/near
print mold/flat w
"""

PARAMETER = re.compile(r"([^\s/<>]+)<([^>]*)>|(/[^\s]+)")

# A value of each datatype, written as source. The first one whose datatype a
# parameter accepts stands before it; the first one whose datatype a parameter
# refuses is the refusal. Datatypes no line can name are simply left out, so a
# parameter that only wants one of those is skipped rather than guessed at.
VALUES = [
    ("1", "integer!"),
    ("1.5", "decimal!"),
    ("1%", "percent!"),
    ("$1", "money!"),
    ('#"a"', "char!"),
    ("1x2", "pair!"),
    ("1.2.3", "tuple!"),
    ("1:00", "time!"),
    ("1-Jan-2000", "date!"),
    ("true", "logic!"),
    ("{x}", "string!"),
    ("%f", "file!"),
    ("http://x", "url!"),
    ("a@b", "email!"),
    ("<a>", "tag!"),
    ("@a", "ref!"),
    ("#a", "issue!"),
    ("#{01}", "binary!"),
    ("[]", "block!"),
    ("()", "paren!"),
    ("'x", "word!"),
    ("x:", "set-word!"),
    (":x", "get-word!"),
    ("'x", "lit-word!"),
    ("integer!", "datatype!"),
    ("number!", "typeset!"),
    ("make vector! 3", "vector!"),
    ("make image! 1x1", "image!"),
    ("make bitset! 8", "bitset!"),
    ("make map! []", "map!"),
    ("make object! []", "object!"),
    ("none", "none!"),
]

# A parameter typed any of these accepts every value, so nothing refuses it.
ACCEPTS_EVERYTHING = {"any-type!"}


def read_typesets():
    typesets = {}
    in_section = False
    for line in open(C_SURFACE):
        line = line.strip()
        if line.startswith("# TYPESET"):
            in_section = True
            continue
        if line.startswith("#"):
            in_section = False
            continue
        if in_section and line.startswith("TYPESET "):
            name, members = line[len("TYPESET "):].split(" | ", 1)
            typesets[name] = set(members.split())
    return typesets


def read_functions():
    functions = {}
    for line in open(C_SURFACE):
        line = line.strip()
        match = re.match(r"^(ACTION|NATIVE) (\S+) \| (.*)$", line)
        if match:
            functions[match.group(2)] = match.group(3)
    return functions


def read_ops(functions):
    ops = []
    for line in open(OPS):
        line = line.strip()
        if not line or line.startswith(";") or " " not in line:
            continue
        parts = line.split()
        if len(parts) == 2 and parts[1] in functions:
            ops.append((parts[0], parts[1]))
    return ops


def main_arguments(spec, typesets):
    arguments = []
    for name, types, refinement in PARAMETER.findall(spec):
        if refinement:
            break
        if not name:
            continue
        expanded = set()
        for part in types.split():
            expanded |= typesets.get(part, {part})
        arguments.append(expanded)
    return arguments


def value_accepted_by(accepted):
    for literal, datatype in VALUES:
        if datatype in accepted:
            return literal
    return None


def value_refused_by(accepted):
    if accepted & ACCEPTS_EVERYTHING:
        return None
    for literal, datatype in VALUES:
        if datatype not in accepted:
            return literal
    return None


def cases_for(name, spec, typesets, infix=False):
    arguments = main_arguments(spec, typesets)
    cases = []
    for at, accepted in enumerate(arguments):
        refusal = value_refused_by(accepted)
        if refusal is None:
            continue
        before = [value_accepted_by(earlier) for earlier in arguments[:at]]
        if any(each is None for each in before):
            continue
        if infix:
            if len(before) != 1:
                continue
            cases.append("%s %s %s" % (before[0], name, refusal))
            cases.append("%s %s (%s)" % (before[0], name, refusal))
            continue
        written = " ".join([name] + before + [refusal])
        from_paren = " ".join([name] + before + ["(" + refusal + ")"])
        cases.append(written)
        cases.append(from_paren)
    return cases


def answer_for(case):
    path = os.path.join(SCRATCH, "case.r3")
    open(path, "w").write(PROBE.format(case=case))
    run = subprocess.run([os.path.join(REPO, "r3-head"), "--quiet", path],
                         capture_output=True, text=True, cwd=REPO, timeout=60)
    if run.returncode != 0:
        return None
    lines = run.stdout.strip("\n").split("\n")
    if len(lines) < 3:
        return None
    return [line.replace("\t", " ") for line in lines[:3]]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", help="write the TSV here instead of printing it")
    parser.add_argument("--only", help="sweep only the functions whose names contain this")
    arguments = parser.parse_args()

    os.makedirs(SCRATCH, exist_ok=True)
    typesets = read_typesets()
    functions = read_functions()
    ops = read_ops(functions)

    cases = []
    for name, spec in functions.items():
        if arguments.only and arguments.only not in name:
            continue
        cases.extend(cases_for(name, spec, typesets))
    for symbol, name in ops:
        if arguments.only and arguments.only not in name:
            continue
        cases.extend(cases_for(symbol, functions[name], typesets, infix=True))

    seen = set()
    rows = []
    for case in cases:
        if case in seen:
            continue
        seen.add(case)
        answer = answer_for(case)
        if answer is None:
            print("no answer: " + case, file=sys.stderr)
            continue
        rows.append(case + "\t" + "\t".join(answer))

    written = "\n".join(rows) + "\n"
    if arguments.out:
        with open(arguments.out, "w") as handle:
            handle.write(written)
        print("%d rows written to %s" % (len(rows), arguments.out))
    else:
        sys.stdout.write(written)


main()
