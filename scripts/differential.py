#!/usr/bin/env python3
"""Run the corpus against a real REBOL and report where it disagrees.

The corpus was written from published documentation. Documentation is a
description of an implementation and not the implementation, so every entry
is a claim about REBOL that nobody has checked against REBOL. This checks
them.

A disagreement means one of three things, and the whole point is that it
says which:

  - the corpus is wrong, and so is JEBOL, because JEBOL was built to match it
  - the corpus is right about the docs and the docs are wrong about REBOL
  - JEBOL diverges on purpose, and the entry should say so

Usage:  python3 scripts/differential.py [--all] [pattern]
"""

import argparse
import glob
import re
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
REBOL = REPO / "r3"

# Entries whose origin is JEBOL itself are decisions rather than claims
# about REBOL, so a disagreement there is expected rather than interesting.
#
# Nothing is excluded for being a fork difference any more: ./r3 is the
# target, not a stand-in for one. See docs/decisions.md item 10.
#
# "confirmed against R3" does not belong here. An entry that was checked
# once is exactly the entry worth checking again, because that is what
# catches it being edited back. Skipping them hid seventeen of the parse
# entries on the run that was meant to verify them.
OUR_OWN = re.compile(r"^JEBOL\b|diverged from deliberately")

# Nothing in REBOL/Core corresponds to these, so running them proves nothing.
# Objects are not among them: `make object!` is ordinary REBOL and those
# twenty entries were being skipped for no reason. Rendering genuinely has
# no counterpart, because the target is markup rather than a window.
NOT_IN_CORE = ("layout", "view")

RESULT_MARKER = "---jebol-result---"


def entries():
    """Every corpus entry, as a dict of its fields."""
    for path in sorted(glob.glob(str(REPO / "corpus" / "*.corpus"))):
        for chunk in Path(path).read_text(encoding="utf-8").split("--- id ")[1:]:
            lines = chunk.split("\n")
            entry = {"id": lines[0].strip(), "notes": [], "requires": []}
            field = None
            body = []
            for line in lines[1:]:
                if line == "#" or line.startswith("# "):
                    # A section banner ends whatever field was open, or it
                    # gets swallowed into the last one and every entry before
                    # a banner looks like a disagreement.
                    if field:
                        entry[field] = "\n".join(body).strip()
                        body = []
                        field = None
                    continue
                if line.startswith("--- "):
                    if field:
                        entry[field] = "\n".join(body).strip()
                        body = []
                    rest = line[4:]
                    name, _, inline = rest.partition(" ")
                    if name in ("origin",):
                        entry[name] = inline.strip()
                        field = None
                    elif name == "note":
                        entry["notes"].append(inline.strip())
                        field = None
                    elif name == "requires":
                        entry["requires"] += inline.split()
                        field = None
                    else:
                        field = name
                        if inline.strip():
                            body.append(inline.strip())
                elif field:
                    body.append(line)
            if field:
                entry[field] = "\n".join(body).strip()
            yield entry


def worth_running(entry):
    """Whether asking REBOL about this entry tells us anything."""
    if not any(claim in entry for claim in ("result", "error", "prints", "types")):
        return False
    if OUR_OWN.search(entry.get("origin", "")):
        return False
    if any(marker in entry["id"] for marker in NOT_IN_CORE):
        return False
    return True


def types_script_for(code):
    """A REBOL script that prints the datatype of each value the code loads.

    The source is handed over as hex rather than as a quoted string. REBOL
    reads \'x\' as a lit-word and {} as a string, so quoting the corpus code
    into the script means escaping in two dialects at once; encoding it
    sidesteps the question entirely.
    """
    encoded = code.encode("utf-8").hex()
    return f"""REBOL []
jebol-source: to string! #{{{encoded}}}
jebol-loaded: load/all jebol-source
unless block? jebol-loaded [jebol-loaded: reduce [jebol-loaded]]
jebol-kinds: copy ""
while [not tail? jebol-loaded] [
    append jebol-kinds mold type? :jebol-loaded/1
    append jebol-kinds " "
    jebol-loaded: next jebol-loaded
]
print "{RESULT_MARKER}"
print rejoin ["TYPES " trim jebol-kinds]
"""


def script_for(code):
    """A REBOL script that prints what the code did, however it went."""
    return f"""REBOL []
jebol-outcome: try [
{code}
]
print "{RESULT_MARKER}"
either error? jebol-outcome [
    print rejoin ["ERROR " jebol-outcome/type " " jebol-outcome/id]
][
    print rejoin ["VALUE " mold :jebol-outcome]
]
"""


def ask_rebol(code):
    """What REBOL printed, and what the code produced."""
    output = run_script(script_for(code), whole=True)
    if RESULT_MARKER not in output:
        return output.strip(), "NO RESULT"
    printed, _, outcome = output.partition(RESULT_MARKER + "\n")
    return printed.strip(), outcome.strip()


def run_script(source, whole=False):
    """Runs a script and returns its output, or everything after the marker."""
    with tempfile.NamedTemporaryFile("w", suffix=".r", delete=False) as handle:
        handle.write(source)
        written = handle.name
    try:
        finished = subprocess.run(
            [str(REBOL), "--quiet", written],
            capture_output=True, text=True, timeout=15)
    except subprocess.TimeoutExpired:
        return "TIMEOUT"
    finally:
        Path(written).unlink(missing_ok=True)

    output = finished.stdout
    if whole:
        return output
    _, _, after = output.partition(RESULT_MARKER + "\n")
    return after.strip()


def expected_of(entry):
    """What the corpus claims, in the shape REBOL will report it."""
    if "types" in entry:
        return "TYPES " + " ".join(entry["types"].split())
    if "error" in entry:
        category, _, identifier = entry["error"].partition(" ")
        return f"ERROR {category} {identifier}"
    return "VALUE " + entry["result"] if "result" in entry else None


def compare(entry):
    """Agree, disagree or not-applicable, with the detail either way."""
    if "types" in entry:
        return compare_types(entry)

    printed, outcome = ask_rebol(entry["code"])

    if "prints" in entry and entry["prints"].strip() != printed:
        return "DIFFERS", f"printed [{printed}] wanted [{entry['prints'].strip()}]"

    expected = expected_of(entry)
    if expected is None:
        return "AGREES", ""

    # Only the error category is normalised now. The corpus used to write
    # logics and datatypes as bare words while this build molds them as
    # construction syntax; the corpus writes what the build writes, so
    # normalising that away would manufacture a disagreement.
    normalised = re.sub(r"^ERROR (\w+)", lambda m: "ERROR " + m.group(1).lower(), outcome)

    if normalised == expected:
        return "AGREES", ""
    return "DIFFERS", f"r3 said [{normalised}] corpus says [{expected}]"


def compare_types(entry):
    """A loading entry claims datatypes rather than a value."""
    outcome = run_script(types_script_for(entry["code"]))
    normalised = re.sub(r"#\(([\w-]+)!\)", r"\1", outcome)
    expected = expected_of(entry)
    if normalised == expected:
        return "AGREES", ""
    return "DIFFERS", f"r3 said [{normalised}] corpus says [{expected}]"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("pattern", nargs="?", default="")
    parser.add_argument("--all", action="store_true",
                        help="include entries whose origin is JEBOL's own decisions")
    options = parser.parse_args()

    if not REBOL.exists():
        print("no ./r3 here. See docs/allium-checker-notes.md for where to get one.")
        return 2

    agreed, differed, skipped = 0, [], 0
    for entry in entries():
        if options.pattern and options.pattern not in entry["id"]:
            continue
        if not options.all and not worth_running(entry):
            skipped += 1
            continue
        verdict, detail = compare(entry)
        if verdict == "AGREES":
            agreed += 1
        else:
            differed.append((entry["id"], detail))

    print(f"\n{agreed} agree, {len(differed)} differ, {skipped} not asked\n")
    for identifier, detail in differed:
        print(f"  {identifier}\n      {detail}")
    return 1 if differed else 0


if __name__ == "__main__":
    sys.exit(main())
