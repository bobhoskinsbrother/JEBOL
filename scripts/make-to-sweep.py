"""Ask a real Rebol every MAKE and TO, and write the answers as a TSV.

    python3 scripts/make-to-sweep.py --out src/testFixtures/resources/org/jebol/make-and-to-as-r3-answers.tsv

The grid is every target datatype against every source value, for MAKE and
for TO. Each case is run through `./r3-head` in its own process, because a
few of them crash a real Rebol -- `make event! [[x] [x]]` is a segmentation
fault -- and one crash in a shared process would lose every answer behind
it. A case whose process does not exit cleanly is reported on standard error
and left out of the file.

The answer for each case is the same expression
`MakeAndToAnswerAsR3DoesTest` runs JEBOL through: the id and ARG1 when it
raises, the molded value otherwise, with newlines turned into spaces and cut
to fifty or eighty characters. So the file is directly the test's expected
answers, and regenerating it is how a difference is seen.
"""
import argparse
import subprocess
import sys

REPO = "/Users/benhoskins/Code/personal/JEBOL"

TARGETS = [
    "end!", "unset!", "none!", "logic!", "integer!", "decimal!", "percent!",
    "money!", "char!", "pair!", "tuple!", "time!", "date!", "binary!",
    "string!", "file!", "email!", "ref!", "url!", "tag!", "bitset!", "image!",
    "vector!", "block!", "paren!", "path!", "set-path!", "get-path!",
    "lit-path!", "hash!", "map!", "datatype!", "typeset!", "word!",
    "set-word!", "get-word!", "lit-word!", "refinement!", "issue!", "native!",
    "action!", "rebcode!", "command!", "op!", "closure!", "function!",
    "frame!", "object!", "module!", "error!", "port!", "gob!", "event!",
    "handle!", "struct!", "library!", "utype!",
]

SOURCES = [
    "[]", "1", "-1", "1.5", "{12}", "{a b}", "none", "[1 2]", "#{01020304}",
    "'a", '#"a"', "1x2", "10%", "1.2.3", "12:00", "$1", "true", "%f",
    "[a: 1]", "[[x] [x]]", "integer!", "#abc", "a@b", "http://x",
]

# Two rows are left out because the interpreter `MakeAndToAnswerAsR3DoesTest`
# runs grants no services: `make port! %f` and `make port! http://x` build a
# port in a real R3 and need the files and http services here. The file-port
# build has its own test under a granting interpreter, in
# `MakingAPortFromTheSourceTest`.
UNASKABLE = {
    ("make", "port!", "%f"),
    ("make", "port!", "http://x"),
}

ANSWER = ("set/any 'r try [{case}] either error? :r ["
          "rejoin [{{E }} r/id {{ }} "
          "copy/part replace/all mold/all :r/arg1 newline {{ }} 50]] ["
          "copy/part replace/all mold/all :r newline {{ }} 80]")


def answer_for(case):
    script = "print (" + ANSWER.format(case=case) + ")"
    run = subprocess.run([REPO + "/r3-head", "--do", script, "--quiet"],
                         capture_output=True, text=True, cwd=REPO, timeout=60)
    if run.returncode != 0:
        return None
    return run.stdout.strip()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", help="write the TSV here instead of printing it")
    arguments = parser.parse_args()

    rows = []
    for verb in ("make", "to"):
        for target in TARGETS:
            for source in SOURCES:
                if (verb, target, source) in UNASKABLE:
                    continue
                case = "%s %s %s" % (verb, target, source)
                answer = answer_for(case)
                if answer is None:
                    print("no answer: " + case, file=sys.stderr)
                    continue
                rows.append(case + "\t" + answer)

    written = "\n".join(rows) + "\n"
    if arguments.out:
        with open(arguments.out, "w") as handle:
            handle.write(written)
        print("%d rows written to %s" % (len(rows), arguments.out))
    else:
        sys.stdout.write(written)


main()
