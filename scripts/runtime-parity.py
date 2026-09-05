#!/usr/bin/env python3
"""Ask a running JEBOL and a running Rebol the same questions about every
function, and print where they disagree.

`c-parity.py` reads declarations: Rebol's boot files against a dump of
JEBOL's native registry. That is the right measure for "is this function
here, with these arguments", and it is blind by construction to everything a
declaration does not say. It reported 279 of 279 matching while a booted
JEBOL answered `native!` where Rebol answers `action!` for all sixty of
Rebol's actions, returned none from `words-of` on every function, and
dropped every refinement from `spec-of`. None of that can show up in a
comparison of two files, because neither file is wrong.

So this compares two interpreters instead. It asks each function three
questions that only a running one can answer:

    type?      what datatype the function reports itself as
    words-of   the words it says it takes, refinements included
    spec-of    how much of its own specification it can produce

The function list comes from `./r3-head`, so it is Rebol's list rather than
JEBOL's, and a function JEBOL does not have at all shows up as ABSENT rather
than silently dropping out of both sides.

Usage:
    ./gradlew compileTestJava
    python3 scripts/runtime-parity.py            # a summary and the groups
    python3 scripts/runtime-parity.py --all      # every differing function
"""

import os
import subprocess
import sys
import collections

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCRATCH = os.path.join(REPO, "build", "runtime-parity")
JAVA = os.path.expanduser(
    "~/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.4+7/Contents/Home/bin/java")

ASKING = r"""
ask: func [w [word!] /local v t s p] [
    either not value? w [
        print [mold w "|ABSENT|ABSENT|ABSENT"]
    ][
        v: get w
        t: either error? t: try [mold type? :v] ["?"] [t]
        s: either error? s: try [mold words-of :v] ["?"] [s]
        p: either error? p: try [length? spec-of :v] ["?"] [mold p]
        print [mold w "|" t "|" s "|" p]
    ]
]
"""


def run(command, out_path):
    with open(out_path, "w") as writing:
        subprocess.run(command, stdout=writing, stderr=subprocess.STDOUT, cwd=REPO)
    return open(out_path, errors="replace").read()


def rebol_function_names():
    script = os.path.join(SCRATCH, "names.r3")
    open(script, "w").write(
        "Rebol []\n"
        "foreach w sort words-of lib [\n"
        "    if all [value? w any-function? get w] [print mold w]\n"
        "]\n")
    text = run([os.path.join(REPO, "r3-head"), script],
               os.path.join(SCRATCH, "names.out"))
    return [line.strip() for line in text.splitlines()
            if line.strip() and not line.startswith("**")]


def answers_from(text):
    found = collections.OrderedDict()
    for line in text.splitlines():
        parts = [piece.strip() for piece in line.split("|")]
        if len(parts) == 4 and parts[0]:
            found[parts[0]] = tuple(parts[1:])
    return found


def main():
    os.makedirs(SCRATCH, exist_ok=True)
    names = rebol_function_names()
    if not names:
        print("no function names came back from r3-head")
        return

    body = ASKING + "".join("ask '%s\n" % name for name in names)
    probe = os.path.join(SCRATCH, "probe.r3")
    open(probe, "w").write("Rebol []\n" + body)

    rebol = answers_from(run([os.path.join(REPO, "r3-head"), probe],
                             os.path.join(SCRATCH, "r3.out")))
    jebol = answers_from(run(
        [JAVA, "-cp",
         "build/classes/java/main:build/classes/java/test:"
         "build/resources/main:build/resources/test",
         "org.jebol.suite.SweepRunner", probe],
        os.path.join(SCRATCH, "jebol.out")))

    absent, kinds, words, specs = [], [], [], []
    for name in names:
        left, right = rebol.get(name), jebol.get(name)
        if right is None or right[0] == "ABSENT":
            absent.append(name)
            continue
        if left is None:
            continue
        if left[0] != right[0]:
            kinds.append((name, left[0], right[0]))
        if left[1] != right[1]:
            words.append((name, left[1], right[1]))
        if left[2] != right[2]:
            specs.append((name, left[2], right[2]))

    print("%d functions asked, from r3-head's own lib" % len(names))
    print("  %4d absent from JEBOL" % len(absent))
    print("  %4d report a different datatype" % len(kinds))
    print("  %4d answer words-of differently" % len(words))
    print("  %4d answer a different spec length" % len(specs))

    show = len(names) if "--all" in sys.argv else 8
    for title, rows in (("datatype", kinds), ("words-of", words),
                        ("spec-of length", specs)):
        if not rows:
            continue
        print("\n--- %s (%d)" % (title, len(rows)))
        for name, left, right in rows[:show]:
            print("  %-22s r3: %-28s jebol: %s"
                  % (name, left[:28], right[:40]))
        if len(rows) > show:
            print("  ... and %d more (--all to see them)" % (len(rows) - show))
    if absent:
        print("\n--- absent (%d)" % len(absent))
        print("  " + ", ".join(absent[:show]))


main()
