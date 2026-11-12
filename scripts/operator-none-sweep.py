"""Ask a real Rebol what every infix operator answers with NONE on its left, and write the answers as a TSV.

    python3 scripts/operator-none-sweep.py --out src/testFixtures/resources/org/jebol/operator-none-as-r3-answers.tsv

An operator further into its series than its head takes NONE as its left
operand (goal 1f, `AnOperatorFurtherInTakesNoneOnItsLeft` in `spec/eval.allium`),
so every operator's action meets NONE. Each operator `boot/ops.reb` names is
written as `none <op> 1`, `none <op> none` and `none <op> {a}`, and each case is
run on `./r3-head`, one case per process.

The answer for each case is the four lines `OperatorWithNoneOnItsLeftAnswersAsR3DoesTest`
reads out of JEBOL: the error's id and its three arguments molded flat, or
`value` and the molded result with two empty lines when the case raises nothing.
"""
import argparse
import os
import subprocess
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OPS = os.path.join(REPO, "rebol3-source", "src", "boot", "ops.reb")
SCRATCH = os.path.join(REPO, "build", "operator-none-sweep")

PROBE = """Rebol []
set/any 'r try [{case}]
either error? :r [
    print r/id
    print mold/flat r/arg1
    print mold/flat r/arg2
    print mold/flat r/arg3
] [
    print "value"
    print mold/flat :r
    print ""
    print ""
]
"""

RIGHT_OPERANDS = ["1", "none", "{a}"]


def read_operators():
    operators = []
    in_table = False
    for line in open(OPS):
        line = line.strip()
        if line == "]":
            in_table = True
            continue
        if not in_table or not line or line.startswith(";"):
            continue
        operators.append(line.split()[0])
    return operators


def answer_for(case):
    path = os.path.join(SCRATCH, "case.r3")
    open(path, "w").write(PROBE.format(case=case))
    run = subprocess.run([os.path.join(REPO, "r3-head"), "--quiet", path],
                         capture_output=True, text=True, cwd=REPO, timeout=60)
    if run.returncode != 0:
        return None
    lines = run.stdout.split("\n")
    if len(lines) < 4:
        return None
    return [line.replace("\t", " ") for line in lines[:4]]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", help="write the TSV here instead of printing it")
    arguments = parser.parse_args()

    os.makedirs(SCRATCH, exist_ok=True)
    rows = []
    for operator in read_operators():
        for right in RIGHT_OPERANDS:
            case = "none %s %s" % (operator, right)
            answer = answer_for(case)
            if answer is None:
                print("no clean answer: " + case, file=sys.stderr)
                continue
            rows.append("\t".join([case] + answer))
    text = "\n".join(rows) + "\n"
    if arguments.out:
        open(os.path.join(REPO, arguments.out), "w").write(text)
    else:
        sys.stdout.write(text)


if __name__ == "__main__":
    main()
