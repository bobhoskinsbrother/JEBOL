"""Ask a real Rebol what every error carries in ARG1 to ARG3, and write the answers as a TSV.

    python3 scripts/error-arguments-sweep.py --out src/testFixtures/resources/org/jebol/error-arguments-as-r3-answers.tsv

The cases are two sets. The first is every refusal `near-and-where-sweep.py`
wrote: a native or an action called with an argument its spec refuses, so
the type checks before a call. The second is `RAISED_INSIDE` below, written by
hand, one source for each place inside a native's body that raises -- which a
refusal never reaches, and which is where JEBOL used to put English sentences
where R3 puts values (goal 1g).

A third set is generated: every parse keyword alone, and followed by one
rule, over a string and over a block. And a fourth, `END_MARKER`, follows the
end marker R3 leaves in a few parse errors through the places a script can
take it: every native refuses it as an argument, an assignment refuses it,
and a block holding one molds only as far as it. What a case molds is
recorded by raising it, `cause-error 'user 'message` with the text as ARG1.

Each answer comes from `./r3-head`, one case per process: the id, then
`mold/flat` of ARG1, ARG2 and ARG3. An argument `mold` refuses as an end
marker is written `#(end)`, as `mold` writes it inside an error; any other
it refuses is `<unmoldable>`. A case that raises nothing is written as
`no-error` with three empty columns. The debug build asserts on its way out
after some cases, so a case is judged by what it printed rather than by how
the process ended, and one R3 does not finish in ten seconds is left out.
"""
import argparse
import os
import re
import subprocess
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REFUSALS = os.path.join(REPO, "src", "testFixtures", "resources", "org", "jebol",
                        "near-and-where-as-r3-answers.tsv")
SCRATCH = os.path.join(REPO, "build", "error-arguments-sweep")

PROBE = """Rebol []
e: try [{case}]
either error? :e [
    molded: func [field /local m] [
        m: try [mold/flat get/any in e field]
        either error? m [
            either all [m/id = 'expect-arg m/arg3 = end!] ["#(end)"] ["<unmoldable>"]
        ] [m]
    ]
    print e/id
    print molded 'arg1
    print molded 'arg2
    print molded 'arg3
] [
    print "no-error"
]
"""

RAISED_INSIDE = [
    # no-value, not-defined, locked-word, not-in-context, hidden
    "get first [zork]",
    "zork",
    "zork/a",
    "reduce [zork]",
    "do reduce [to word! {qq}]",
    "set to word! {qq} 1",
    "get to word! {qq}",
    "locked: 1 protect 'locked locked: 2",
    "locked: 1 protect 'locked set 'locked 2",
    "bind 'zz make object! [a: 1]",
    "bind [a] to word! {q}",
    "hiding: make object! [p: 1] protect/hide in hiding 'p set hiding [1]",
    "hiding: make object! [a: 1] protect/hide in hiding 'a append hiding [a 3]",
    "hiding: make object! [a: 1] protect/hide in hiding 'a hiding/a: 2",
    # not-related
    "1x2 + 1.2.3",
    "1 and true",
    "1 xor 1x2",
    "1.2.3 and true",
    "1x2 and 1.2.3",
    # invalid-path and bad-path-set, read and written
    "block: [1 2] block/zz: 3",
    "object: make object! [p: 1] object/zz",
    "object: make object! [p: 1] object/zz: 3",
    "object: make object! [p: 1] object/1",
    "object: make object! [p: 1] set 'object/zz 1",
    "set 'nowhere/zz 1",
    "date: 1-Jan-2000 date/zz: 3",
    "date: 1-Jan-2000 date/weekday: 1",
    "date: 1-Jan-2000 date/julian: 1",
    "date: 1-Jan-2000 date/zone: 16:00",
    "make date! [1 1 2000 0:00 16:00]",
    "time: 1:00 time/zz",
    "time: 1:00 time/zz: 1",
    "time: 1:00 time/(1.5): 1",
    "pick 1:00 'zz",
    "pair: 1x2 pair/zz",
    "pair: 1x2 pair/3",
    "pair: 1x2 pair/zz: 3",
    "pick 1x2 'zz",
    "pick 1x2 3",
    "tuple: 1.2.3 tuple/zz",
    "tuple: 1.2.3 tuple/zz: 3",
    "tuple: 1.2.3 tuple/20: 3",
    "pick 1.2.3 'zz",
    "error: make error! {x} error/zz",
    "error: make error! {x} error/zz: 1",
    "text: {s} text/user",
    "image: make image! 2x2 image/zz",
    "image: make image! 2x2 image/zz: 1",
    "image: make image! 2x2 image/9: 1.2.3",
    "image: make image! 2x2 image/size: 0x2",
    "image: make image! 2x2 image/rgb: {x}",
    "image: make image! 2x2 image/rgb: 300",
    "pick make image! 2x2 'zz",
    "vector: make vector! 3 vector/zz",
    "vector: make vector! 3 vector/zz: 1",
    "vector: make vector! 3 vector/9: 1",
    "vector: make vector! 3 vector/1: {x}",
    "vector: make vector! 3 vector/(none)",
    "pick make vector! 3 'zz",
    "gob: make gob! [] gob/zz",
    "gob: make gob! [] gob/(none)",
    "gob: make gob! [] gob/pane: 3",
    "gob: make gob! [] gob/pane: [3]",
    "gob: make gob! [] gob/1: 3",
    "gob: make gob! [] gob/3: make gob! []",
    "gob: make gob! [] append gob 3",
    "gob: make gob! [] append/part gob make gob! [] 1",
    "gob: make gob! [] change/part gob make gob! [] 1",
    "pick make gob! [] 'zz",
    "event: make event! [type: 'down] event/zz",
    "event: make event! [type: 'down] event/(1)",
    "event: make event! [type: 'down] event/zz: 1",
    "event: make event! [type: 'key] event/key: 9999999",
    "make event! [type: 'zzz]",
    "make event! [type: 'down offset: 99999x1]",
    "make event! [type: 'key key: 9999999]",
    "make handle! 0",
    "struct: make struct! [a [uint8!]] struct/zz",
    "struct: make struct! [a [uint8!]] struct/1",
    "struct: make struct! [a [uint8!]] struct/a: {x}",
    "struct: make struct! [a [struct! [b [uint8!]] [2]]] struct/a/3: 1",
    "struct: make struct! [a [uint8!]] change struct 1",
    "binary: #{00} binary/1: -1",
    "binary: #{00} binary/1: 256",
    "append/1 [] 1",
    # refinements that do not go together, or say nothing
    "copy/deep make struct! [a [uint8!]]",
    "copy/part make object! [] 1",
    "sort/all/compare/skip [1 2] 1 2",
    "remove/key/part make bitset! 8 1 1",
    "append/dup make map! [] [a 1] 2",
    "protect/hide [1]",
    "sort/skip make vector! 3 1",
    "binary/read/into binary #{0102} 2 []",
    # out-of-range
    "find/skip [1 2] 1 0",
    "select/skip [1 2] 1 0",
    "sort/skip [1 2 3] 2",
    "unique/skip [1 2] 0",
    "factorial -1",
    "poke #{00} 1 #\"Ā\"",
    "poke #{00} 1 256",
    "append #{} 256",
    "find #{00} 256",
    "reverse/part 1.2.3 -1",
    # invalid-arg
    "put make object! [] 1 2",
    "sort/compare [1 2] 1",
    "sort/compare/skip [1 2] 3 2",
    "sort/skip/compare [1 2 3 4] 2 [a]",
    "sort/skip/compare [1 2 3 4] 2 [3]",
    "sort/skip/compare [1 2 3 4] 2 [-1]",
    "poke #{00} 1 -1",
    "poke {a} 1 1.5",
    "append #{} 1.5",
    "remove/part make bitset! 8 1.5",
    "func [a [1]] []",
    "func [a [zz!]] []",
    "append make map! [] 1",
    "make map! [a]",
    "foreach [a 1] [] []",
    "set [#a] [1]",
    "set [/a] [1]",
    "assert/type [aa 1]",
    "binary/write #{} [BYTES 1]",
    # past-end, missing-arg, not-same-type, vector-not-compatible
    "charset [#\"z\" - #\"a\"]",
    "remove make bitset! 8",
    "(make vector! [integer! 8 1]) < make vector! [decimal! 64 1]",
    "(make vector! [integer! 8 1]) = make vector! [decimal! 64 1]",
    "(make vector! [integer! 8 1]) + make vector! [integer! 16 1]",
    # math
    "(make vector! [1]) / 0",
    "1.2.3 / 0",
    "9223372036854775807 + 1",
    "absolute -9223372036854775808",
    "to integer! 1e19",
    "shift 1 64",
    "shift 2 62",
    "for i 9223372036854775806 9223372036854775807 2 []",
    # expect-val, cannot-use
    "reverse make image! 2x2",
    # parse
    "parse [a] [zork]",
    "parse {a} [set 1]",
    "parse {a} [copy 1]",
    "parse [a] [set 1]",
    "parse {a} [set 1 skip]",
    "parse {a} [set x]",
    "parse {a} [change {b}]",
    "parse {a} [change only {a} {b}]",
    "parse {a} [into]",
    "parse [a] [into 1]",
    "parse {a} [keep]",
    "parse {a} [limit]",
    "parse {a} [to]",
    "parse {a} [thru 1.5]",
    # not-related names the action and the left operand's datatype
    "1x2 + $1",
    "1x2 * 1:00",
    "1x2 - #\"a\"",
    "1x2 / 1-Jan-2000",
    "1.2.3 + 1x2",
    "1.2.3 + $1",
    "1.2.3 * 1:00",
    "$1 + 1x2",
    "#\"a\" + 1x2",
    "#\"a\" and 1x2",
    "1.2.3 or 1x2",
    "remainder 1x2 1.2.3",
    "1 - 1x2",
    "1.5 - 1.2.3",
    "1:00 + 1.2.3",
    "1:00 + 1x2",
    "1:00 / 1-Jan-2000",
    "$1 + 1:00",
    "$1 + 1-Jan-2000",
    "1 - 1-Jan-2000",
    "1.5 + 1-Jan-2000",
    "(make vector! [decimal! 64 [1.5]]) and 1",
    "1 - make vector! [1]",
    "1x2 - make vector! [1]",
    # cannot-use names the action and the datatype it was given
    "1-Jan-2000 + 1.2.3",
    "1-Jan-2000 * 2",
    "1-Jan-2000 - $1",
    "1-Jan-2000 + 1-Jan-2000",
    "integer! and integer!",
    "integer! or 1",
    "(make vector! [1 2]) + 1x2",
    "reflect make struct! [a [uint8!]] 'zz",
    "take/part make image! 2x2 2",
    "sort make image! 2x2",
    "select make image! 2x2 1",
    "find make vector! [1] 1",
    "to handle! 0",
    # set operations refuse two datatypes that differ
    "union make bitset! 8 [a]",
    "union [a] make bitset! 8",
    "union make typeset! [integer!] [a]",
    "intersect [a] {a}",
    "intersect {a} [a]",
    "difference {a} [a]",
    "exclude make bitset! 8 {a}",
    "union [#\"a\"] {b}",
    "union [a] make map! [a 1]",
    "union make map! [a 1] [b 2]",
    "exclude [a] make typeset! [integer!]",
    "unique/skip [1 2] -1",
    # type-mismatch names both values
    "lerp 1.2.3 1x2 0.5",
    "lerp 1 1x2 0.5",
    "clamp 1 1.5 2",
    "clamp 1 1 2.5",
    "clamp 1.5 1 2",
    # pick through the path rules
    "pick 1x2 0",
    "pick 1x2 'x",
    "pick 1x2 1.5",
    "pick 1x2 3.5",
    "pick 1x2 {a}",
    "third 1x2",
    "pick 1.2.3 {a}",
    "pick 1:00 'hour",
    "pick 1:00 {a}",
    "pick make image! 2x2 'size",
    "pick make image! 2x2 {a}",
    "pick make vector! 3 'size",
    "pick make vector! 3 {a}",
    # gobs
    "gob: make gob! [] append gob make gob! [] gob/1: 3 length? gob",
    "gob: make gob! [] gob/1: make gob! [] length? gob",
    "gob: make gob! [] append gob make gob! [] poke gob 1 3",
    "gob: make gob! [] poke gob 1 make gob! []",
    "gob: make gob! [] append gob make gob! [] change/part gob make gob! [] 1",
    "gob: make gob! [] append gob make gob! [] change gob 3",
    "gob: make gob! [] insert/only gob make gob! []",
    "gob: make gob! [] append gob [3]",
    "gob: make gob! [] insert gob 3",
    # the rest
    "struct: make struct! [a [uint8!]] change/part struct #{05} 1 struct/a",
    "collect-words/as [a] integer!",
    "factorial 171",
    "foreach [a b c] make object! [x: 1 y: 2] [a]",
    "foreach [a b c d] make map! [x 1 y 2 z 3] [a]",
    "foreach [a b c] make map! [x 1] [a]",
    "append make map! [] make map! [q 1]",
    "append make map! [x 1] 1",
    "make map! [a b c]",
    "append/dup [] 1 1x2",
    "protect/hide [zzq]",
    # bytes that are never UTF-8 scan as a control character
    "load #{41C342}",
    "load #{41F642}",
    "load #{41FE42}",
    # raising nothing where JEBOL raised
    "load #{FF}",
    "load #{41FF42}",
    "load #{41C042}",
    "load #{41F542}",
    "for i 1 3 0 []",
    "foreach [a b c] make map! [] []",
    "foreach [a b c] make object! [] []",
]

PARSE_KEYWORDS = ("any while some opt to thru end skip into quote set copy collect keep "
                  "and ahead not reject break accept return then if remove change insert "
                  "case no-case fail limit").split()


def parse_keyword_cases():
    for keyword in PARSE_KEYWORDS:
        yield "parse {a} [%s]" % keyword
        yield "parse [a] [%s]" % keyword
        yield "parse {a} [%s {a}]" % keyword
        yield "parse [a] [%s 'a]" % keyword


HOLDING = "p: try [parse {a} [set]] "


def molding(source):
    return HOLDING + "cause-error 'user 'message reduce [%s]" % source


END_MARKER = [
    "parse [a] [copy]",
    "parse {a} [change {a}]",
    "parse [a] [change 'a]",
    "parse {a} [x: change x]",
    "parse [a] [x: change x]",
    "load {#(end)}",
    "make end! none",
    "to end! 1",
    "make end! 1",
    HOLDING + "type? p/arg1",
    HOLDING + "type? get/any in p 'arg1",
    HOLDING + "type? select p 'arg1",
    HOLDING + "v: p/arg1",
    HOLDING + "set/any 'v p/arg1",
    HOLDING + "set 'v p/arg1",
    HOLDING + "p/arg1: p/arg1",
    HOLDING + "make object! [z: p/arg1]",
    HOLDING + "make error! compose [type: 'Script id: 'parse-end arg1: (p/arg1)]",
    HOLDING + "get 'p/arg1",
    HOLDING + "get/any 'p/arg1",
    HOLDING + "p/arg1 + 1",
    HOLDING + "1 + p/arg1",
    HOLDING + "p/arg1 = p/arg1",
    HOLDING + "f: func [x [any-type!]] [x] f p/arg1",
    HOLDING + "f: func [x [unset!]] [x] f p/arg1",
    HOLDING + "f: func ['x] [x] f p/arg1",
    HOLDING + "unset? get/any in p 'arg1",
    HOLDING + "value? get/any in p 'arg1",
    HOLDING + "print p/arg1",
    HOLDING + "probe p/arg1",
    HOLDING + "either p/arg1 [1] [2]",
    HOLDING + "if true [p/arg1]",
    HOLDING + "type? do [p/arg1]",
    HOLDING + "type? (p/arg1)",
    HOLDING + "type? catch [p/arg1]",
    HOLDING + "type? try [p/arg1]",
    HOLDING + "type? any [p/arg1 1]",
    HOLDING + "type? all [p/arg1]",
    HOLDING + "mold p/arg1",
    HOLDING + "form p/arg1",
    HOLDING + "type? pick values-of p 4",
    HOLDING + "type? first at to block! p 8",
    HOLDING + "type? second compose [a (p/arg1) b]",
    HOLDING + "foreach x reduce [p/arg1] [type? x]",
    HOLDING + "append [] p/arg1",
    HOLDING + "append o: make object! [x: 1] compose [y: (p/arg1)] type? get/any in o 'y",
    molding("mold p"),
    molding("mold/all p"),
    molding("form p"),
    molding("mold compose [a (p/arg1) b]"),
    molding("mold/all compose [a (p/arg1) b]"),
    molding("mold/only compose [a (p/arg1) b]"),
    molding("form compose [a (p/arg1) b]"),
    molding("mold compose/deep [x [a (p/arg1) b] y]"),
    molding("mold compose [(p/arg1) a]"),
    molding("length? compose [a (p/arg1) b]"),
    molding("mold copy compose [a (p/arg1) b]"),
    molding("mold reverse compose [a (p/arg1) b]"),
    molding("mold find compose [a (p/arg1) b] 'b"),
    molding("mold next next compose [a (p/arg1) b]"),
    molding("index? find compose [a (p/arg1) b] 'b"),
    molding("n: 0 foreach x compose [a (p/arg1) b] [n: n + 1] n"),
    molding("mold values-of p"),
    molding("length? values-of p"),
    molding("mold to block! p"),
    molding("length? to block! p"),
    molding("mold body-of p"),
    molding("mold words-of p"),
    molding("mold reduce [p/arg1]"),
    molding("length? reduce [p/arg1]"),
    molding("equal? compose [a (p/arg1)] compose [a (p/arg1)]"),
    molding("mold body-of try [1 / 0]"),
    molding("mold append o: make object! [x: 1] compose [y: (p/arg1)]"),
]


def refusals():
    with open(REFUSALS) as rows:
        return [row.split("\t")[0] for row in rows.read().splitlines() if row]


def answer_for(case):
    path = os.path.join(SCRATCH, "case.r3")
    with open(path, "w") as script:
        script.write(PROBE.format(case=case))
    try:
        run = subprocess.run([os.path.join(REPO, "r3-head"), "--quiet", path],
                             capture_output=True, text=True, cwd=SCRATCH, timeout=10)
    except subprocess.TimeoutExpired:
        return None
    lines = run.stdout.split("\n")
    if lines and lines[0] == "no-error":
        return ["no-error", "", "", ""]
    if len(lines) < 4 or not re.fullmatch(r"[a-z][a-z0-9-]*", lines[0]):
        return None
    return [line.replace("\t", " ") for line in lines[:4]]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", help="write the TSV here instead of printing it")
    arguments = parser.parse_args()

    os.makedirs(SCRATCH, exist_ok=True)
    seen = set()
    rows = []
    for case in refusals() + RAISED_INSIDE + list(parse_keyword_cases()) + END_MARKER:
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


if __name__ == "__main__":
    main()
