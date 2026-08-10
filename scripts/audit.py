#!/usr/bin/env python3
"""Run the same expressions through a real REBOL and through JEBOL.

differential.py checks corpus entries we have already written. This runs
expressions nobody has an opinion about yet, through both implementations,
and reports where they part company. It is the tool for sweeping a feature
at its boundaries: write the cases, run them, and read the disagreements.

Both sides are asked the same way, `mold try [...]`, so a failure comes back
as a value rather than as output on a stream. That makes an error and a
result comparable without parsing two different console formats, and it
means the error id is in the answer.

JEBOL needs building first:

    ./gradlew installDist

Usage:
    python3 scripts/audit.py 'first []' 'pick [a b c] 0'
    python3 scripts/audit.py --file cases.txt
    python3 scripts/audit.py --file cases.txt --quiet   # only disagreements
"""

import argparse
import re
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
REBOL = REPO / "r3"
JEBOL = REPO / "build" / "install" / "jebol" / "bin" / "jebol"

MARKER = "---audit---"


def jebol_home():
    """The JDK the build used, which is not the one on the path."""
    candidates = sorted(Path.home().glob(".gradle/jdks/*-25-*/jdk-*/Contents/Home"))
    return str(candidates[-1]) if candidates else None


def ask_jebol(expressions):
    """What JEBOL makes of each expression, in order."""
    # PRINT, not the REPL's own echo. The echo molds whatever it is given,
    # so asking for `mold x` and letting the REPL show it molds twice and a
    # character comes back as #^"a^" instead of #"a". Printing it once puts
    # both sides on the same footing.
    script = "".join(f'print "{MARKER}"\nprint mold try [{e}]\n' for e in expressions)
    environment = {"PATH": "/usr/bin:/bin"}
    home = jebol_home()
    if home:
        environment["JAVA_HOME"] = home
    try:
        finished = subprocess.run(
            [str(JEBOL)], input=script, capture_output=True, text=True,
            timeout=120, env=environment)
    except subprocess.TimeoutExpired:
        return ["TIMEOUT"] * len(expressions)
    return [read_jebol_answer(chunk)
            for chunk in finished.stdout.split(MARKER)[1:]] or ["NO OUTPUT"]


def read_jebol_answer(chunk):
    """The molded outcome out of one REPL exchange."""
    for line in chunk.splitlines():
        line = line.replace(">>", "").strip()
        if not line or line.startswith("=="):
            continue
        return line
    return "NO ANSWER"


def ask_rebol(expression):
    """What a real REBOL makes of one expression."""
    encoded = f"mold try [{expression}]".encode("utf-8").hex()
    script = f'REBOL []\nprint do to string! #{{{encoded}}}\n'
    with tempfile.NamedTemporaryFile("w", suffix=".r", delete=False) as handle:
        handle.write(script)
        written = handle.name
    try:
        finished = subprocess.run(
            [str(REBOL), "--quiet", written],
            capture_output=True, text=True, timeout=20)
    except subprocess.TimeoutExpired:
        return "TIMEOUT"
    finally:
        Path(written).unlink(missing_ok=True)
    # An error molds over several lines, so the whole of the output is the
    # answer. Taking the last line quietly turned every error into "]]".
    return " ".join(finished.stdout.split()) or "NO ANSWER"


def normalise(answer):
    """The spellings the two builds differ on for reasons that are not behaviour."""
    answer = answer.replace("#(true)", "true").replace("#(false)", "false")
    answer = answer.replace("#(none)", "none").replace("#[none]", "none")
    answer = re.sub(r"^_$", "none", answer)
    answer = answer.replace("#(unset)", "unset").replace("#[unset!]", "unset")
    # An error molds as a whole object in one build and as #[error! id] in
    # the other. The id is the part that carries meaning, so compare on that
    # alone: R3 writes it as `id: 'zero-divide` inside the object, JEBOL puts
    # it straight after the datatype.
    found = re.search(r"id:\s+'([a-z0-9-]+)", answer)
    if not found:
        found = re.search(r"error!\s+([a-z0-9-]+)", answer)
    if found:
        return "error " + found.group(1)
    if "make error!" in answer or answer.startswith("**"):
        return "error " + re.sub(r"\s+", " ", answer)[:60]
    return re.sub(r"\s+", " ", answer).strip()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("expressions", nargs="*")
    parser.add_argument("--file", help="read expressions from a file, one per line")
    parser.add_argument("--quiet", action="store_true", help="print only disagreements")
    options = parser.parse_args()

    expressions = list(options.expressions)
    if options.file:
        expressions += [line for line in Path(options.file).read_text().splitlines()
                        if line.strip() and not line.lstrip().startswith("#")]
    if not expressions:
        parser.error("give at least one expression, or --file")
    if not JEBOL.exists():
        print("no built JEBOL. Run ./gradlew installDist first.")
        return 2

    ours = ask_jebol(expressions)
    agreed, differed = 0, []

    for expression, mine in zip(expressions, ours):
        theirs = ask_rebol(expression)
        if normalise(mine) == normalise(theirs):
            agreed += 1
            if not options.quiet:
                print(f"  ok   {expression}  ->  {normalise(theirs)}")
        else:
            differed.append((expression, normalise(theirs), normalise(mine)))

    print(f"\n{agreed} agree, {len(differed)} differ\n")
    for expression, theirs, mine in differed:
        print(f"  {expression}\n      r3 [{theirs}]  jebol [{mine}]")
    return 1 if differed else 0


if __name__ == "__main__":
    sys.exit(main())
