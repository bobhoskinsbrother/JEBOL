#!/usr/bin/env python3
"""Record Rebol's C surface, read from Rebol's own source.

Everything here comes out of `~/Code/personal/rebol3-source`. No running
binary is involved: a probe answers what one build does on one machine, and
the source says what the language is. Where the two disagree the source wins,
and the source is also the thing that explains itself.

Four tables, and each answers a question the others cannot.

`src/boot/types.reb` is the datatype table. Its columns give each datatype's
typeclass -- which `T_` dispatcher serves it -- whether it has a path handler,
whether MAKE can build one, and which typesets it belongs to. The typeclass
column is the one that matters most here: seven datatypes share `T_String`, so
an arm written once in `REBTYPE(String)` serves all seven, and a port here that
covers only `string!` is six datatypes short.

`src/boot/actions.reb` and `src/boot/natives.reb` are the declared specs, with
every argument's datatypes and every refinement. 60 actions and 164 natives.

`src/core/t-*.c` holds the arms themselves: every `case A_XXX:` inside a
`REBTYPE(Name)` block. A declaration says APPEND takes a `map!`; only the arm
says whether anything happens.

Writes `src/test/resources/r3/c-surface.txt`, which is checked in so that a
change to it shows up in a diff and so the audit runs without Rebol's tree on
disk.

Usage:
    python3 scripts/c-surface.py
    python3 scripts/c-surface.py --print
"""

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
REBOL = Path.home() / "Code" / "personal" / "rebol3-source" / "src"
OUT = REPO / "src" / "test" / "resources" / "r3" / "c-surface.txt"

# A word in a spec block that is not an argument.
NOT_AN_ARGUMENT = {"return:", "local"}

# types.reb names a typeset by its bare word and the specs name it with the
# any- prefix: the column says `string`, `append` declares `any-string!`. Six
# of the nine differ that way and three do not.
TYPESET_SPELLING = {
    "string": "any-string!",
    "block": "any-block!",
    "word": "any-word!",
    "object": "any-object!",
    "function": "any-function!",
    "path": "any-path!",
    "series": "series!",
    "number": "number!",
    "scalar": "scalar!",
}


def read(path):
    """A source file's text. Some of Rebol's C is Latin-1, not UTF-8."""
    return path.read_bytes().decode("utf-8", errors="replace")


def datatypes():
    """The datatype table: name, typeclass, path, make, typesets.

    The table is columns of words with a comment header, and the file says
    what each column means. Rows before the header and blank or commented
    lines are not rows.
    """
    rows = {}
    text = read(REBOL / "boot" / "types.reb")
    body = text[text.index(";   Datatype"):]
    for line in body.splitlines():
        line = line.split(";")[0].strip() if not line.strip().startswith(";") else ""
        if not line:
            continue
        # The last column is a typeset, a block of them, or a dash.
        block = re.search(r'\[([^\]]*)\]\s*$', line)
        if block:
            typesets = block.group(1).split()
            columns = line[:block.start()].split()
        else:
            columns = line.split()
            typesets = [] if columns[-1] == "-" else [columns[-1]]
            columns = columns[:-1]
        if len(columns) < 6:
            continue
        name, evaluator, typeclass, mold, form, path = columns[:6]
        make = columns[6] if len(columns) > 6 else "-"
        rows[name] = {
            "typeclass": typeclass,
            "path": path,
            "make": make,
            "typesets": typesets,
        }
    return rows


def specs(file, kind):
    """Every `name: kind [...]` in a boot file, as name to declared spec.

    The spec is kept as the list of (argument, datatypes) and the list of
    (refinement, its arguments), which is what a surface comparison needs.
    """
    text = read(REBOL / "boot" / file)
    found = {}
    for match in re.finditer(
            r'^([A-Za-z0-9?!*+\-=<>/&|~%]+):\s*' + kind + r'\s*\[', text, re.M):
        name = match.group(1)
        depth = 1
        at = match.end()
        while at < len(text) and depth:
            if text[at] == "[":
                depth += 1
            elif text[at] == "]":
                depth -= 1
            at += 1
        found[name] = parse_spec(text[match.end():at - 1])
    return found


def parse_spec(body):
    """A spec block as (arguments, refinements).

    `arguments` is a list of (name, datatypes) for the head of the spec, and
    `refinements` a list of (name, its own arguments). Position is what decides
    which: everything after a refinement belongs to it until the next one, which
    is how `/part range [number!]` reads as one refinement taking one argument.
    """
    # Strip the documentation strings, braced and quoted, so what is left is
    # words, refinements and datatype blocks.
    body = re.sub(r'\{[^{}]*\}', " ", body)
    body = re.sub(r'"[^"]*"', " ", body)
    # And then the comments, which several specs use to keep a refinement Rebol
    # decided against: PARSE's `/all`, TRACE's `/stack`, and the `/as` that both
    # READ and WRITE carry commented out with its `encoding` argument under it.
    # Reading those made the audit demand four refinements and two arguments that
    # no Rebol has. Strings first and comments second, so a semicolon inside a
    # doc string is text rather than the start of a comment.
    body = re.sub(r";[^\n]*", " ", body)
    arguments = []
    refinements = []
    for token in re.findall(r"/[A-Za-z0-9?!\-]+|\[[^\]]*\]|[A-Za-z0-9?!\-\']+:?", body):
        if token.startswith("/"):
            refinements.append((token[1:], []))
            continue
        into = refinements[-1][1] if refinements else arguments
        if token.startswith("["):
            if into:
                into[-1] = (into[-1][0], token[1:-1].split())
            continue
        if token in NOT_AN_ARGUMENT:
            continue
        into.append((token, []))
    return arguments, refinements


SHARED_SERIES = "SharedSeries"


def armed_cases(body):
    """The action constants a dispatcher body actually implements.

    A run of `case` labels shares one body, so every label in the run counts.
    Two kinds of run are the opposite of an arm and are dropped.

    A run whose whole body is `Trap_Action(...)` is there to refuse the action
    with a proper error. `Do_Series_Action` ends with one, which is how a string
    reports that it cannot be raised to a power.

    A run that falls through into such a refusal is a refusal too. The four
    arithmetic actions are written that way: their body is
    `if (IS_VECTOR(value)) return -1;` and then they fall into the trap, so a
    vector is handled by its own dispatcher and a string is refused. Counting
    the labels alone would say a block can be multiplied.
    """
    runs = []
    for run in re.finditer(
            r'((?:case\s+A_\w+\s*:\s*)+)(.*?)(?=\bcase\s+A_|\bdefault\s*:|\Z)',
            body, re.S):
        labels = re.findall(r'case\s+(A_\w+)\s*:', run.group(1))
        statements = run.group(2).strip()
        refuses = bool(re.fullmatch(r'Trap_Action\([^;]*\);?', statements))
        # A run that ends without leaving the switch falls into the next one.
        falls_through = not re.search(
            r'\b(break|goto|return|DS_RET|Trap0|Trap1|Trap2|Trap3|Trap_Arg)\b',
            statements.replace("return -1", ""))
        runs.append([labels, refuses, falls_through])
    for at in range(len(runs) - 2, -1, -1):
        if runs[at][2] and runs[at + 1][1]:
            runs[at][1] = True
    return [label for labels, refuses, _ in runs if not refuses for label in labels]


def arms():
    """Each typeclass to the actions its REBTYPE block implements.

    Including the ones it reaches through `Do_Series_Action`, the shared
    navigation set in `f-series.c`. A block's dispatcher has no `case A_HEAD`
    of its own and a block certainly has HEAD.
    """
    by_constant = {}
    for name in specs("actions.reb", "action"):
        by_constant["A_" + name.upper().replace("-", "_").replace("?", "Q")] = name
    shared = read(REBOL / "core" / "f-series.c")
    shared = shared[shared.index("Do_Series_Action"):]
    shared = shared[:shared.index("\n/*****", 200)]
    found = {SHARED_SERIES: {by_constant[c] for c in armed_cases(shared)
                             if c in by_constant}}
    for file in sorted((REBOL / "core").glob("t-*.c")):
        text = read(file)
        for match in re.finditer(r'REBTYPE\((\w+)\)', text):
            body = text[match.end():]
            end = body.find("\n/*****")
            if end > 0:
                body = body[:end]
            got = {by_constant[c] for c in armed_cases(body) if c in by_constant}
            # A dispatcher whose whole body forwards is served by the other one
            # entirely -- `REBTYPE(Paren) { return T_Block(ds, action); }` -- and
            # that is the only forwarding that means "every arm". A forward
            # inside a case group serves those labels alone: REBTYPE(Word) sends
            # OPEN, READ, WRITE and QUERY to T_Port so that `read 'clipboard`
            # works, and a word has none of the rest of a port's arms. Reading
            # the second as the first gave a word APPEND, FIND, PICK and SELECT.
            if not re.search(r'case\s+A_\w+', body):
                for other in re.findall(r'return\s+T_(\w+)\(', body):
                    got.add("=" + other)
            # Three dispatchers reach the shared navigation arms through a
            # helper rather than a case of their own: `len =
            # Do_Series_Action(action, value, arg);`. Without this a block
            # would look as though it had no HEAD, NEXT or LENGTH?.
            if "Do_Series_Action(" in body:
                got.add("=" + SHARED_SERIES)
            if got:
                found.setdefault(match.group(1), set()).update(got)
    for _ in range(4):
        for name, got in found.items():
            for reference in [a for a in got if a.startswith("=")]:
                got.discard(reference)
                got.update(a for a in found.get(reference[1:], set())
                           if not a.startswith("="))
    return found


def written(arguments, refinements):
    """A spec in the one-line shape the audit compares."""
    pieces = []
    for name, kinds in arguments:
        pieces.append(name + ("<" + " ".join(kinds) + ">" if kinds else ""))
    for name, own in refinements:
        pieces.append("/" + name)
        for argument, kinds in own:
            pieces.append(argument + ("<" + " ".join(kinds) + ">" if kinds else ""))
    return " ".join(pieces)


MAKES_A_FUNCTION = re.compile(
    r"^([A-Za-z0-9?!*+=<>~|-]+):\s*"
    r"(?:func|funct|funco|function|closure|clos|does|has)\b")


def files_bound_into_a_context():
    """The REBOL files whose own words become the library's.

    `mezz/boot-files.reb` lists what Rebol boots, in four blocks: base, sys, lib
    and protocols. The first three are bound into a context -- `do bind-lib
    boot-mezz` in `sys-start.reb` -- so what they define at the top level is
    reachable by name afterwards.

    The fourth is not. `foreach [spec body] boot-prot [module spec body]` makes
    each protocol file a module, so its own words are its own however its header
    reads. That is why `prot-tls.reb` publishes nothing: forty functions, none of
    them callable from a script.
    """
    listed = read(REBOL / "mezz" / "boot-files.reb")
    protocols = listed.find(";-- protocols:")
    if protocols >= 0:
        listed = listed[:protocols]
    return {name for name in re.findall(r"^\s*%([a-z0-9-]+\.reb)", listed, re.M)}


def library():
    """Every function Rebol's own REBOL files publish, by name.

    The other half of Rebol's library: `boot/natives.reb` and
    `boot/actions.reb` declare the third written in C, and this is the rest.

    One rule with two halves, and both are Rebol's rather than ours.

    A file bound into the base, sys or lib context publishes the functions it
    defines at the top level. Only at the top level: a definition inside a block
    belongs to whatever holds the block, and `context [...]` keeps its helpers to
    itself.

    Everything else is a module and publishes what its header's `exports:` block
    names, which for most of them is nothing at all. That covers the codecs, the
    protocols, and any file whose own header says `Type: module` however it is
    loaded. `codec-swf.reb` gives none of its forty `read-*` functions and
    `prot-http.reb` gives two out of forty, because that is what a script can
    reach.

    This replaces what used to be read from a frozen dump of a running binary.
    The dump answered what one build had loaded; this answers what the source
    defines, and it is the only one of the two that can be checked.
    """
    booted = files_bound_into_a_context()
    found = {}
    for file in sorted((REBOL / "mezz").glob("*.reb")):
        text = read(file)
        is_module = re.search(r"^\s*Type:\s*module\b", text, re.M | re.I)
        if is_module or file.name not in booted:
            exported = re.search(r"^\s*exports:\s*\[([^]]*)]", text, re.M)
            if exported:
                for name in exported.group(1).split():
                    found.setdefault(name, file.name)
            continue
        depth = 0
        for line in text.splitlines():
            if depth == 0:
                named = MAKES_A_FUNCTION.match(line)
                if named:
                    found.setdefault(named.group(1), file.name)
            depth += brackets_opened_by(line)
    return found


def brackets_opened_by(line):
    """How much deeper a line leaves the block nesting.

    Rough on purpose and exact where it matters: a comment is dropped, and a
    bracket inside a string or a character literal is not a bracket. The header
    block every file opens with is what makes the count start at one and stay
    there until the header closes, which is also why a definition cannot be
    mistaken for one inside it.
    """
    without_strings = re.sub(r'"(?:[^"\\]|\\.)*"', "", line)
    without_strings = re.sub(r"#\"(?:[^\"\\]|\\.)*\"", "", without_strings)
    code = without_strings.split(";", 1)[0]
    return code.count("[") - code.count("]")


def main():
    table = datatypes()
    actions = specs("actions.reb", "action")
    natives = specs("natives.reb", "native")
    typeclasses = arms()
    rebol_side = library()

    # Invert the typesets column: a typeset is the datatypes that name it.
    typesets = {}
    for name, row in table.items():
        for typeset in row["typesets"]:
            spelling = TYPESET_SPELLING.get(typeset, typeset + "!")
            typesets.setdefault(spelling, set()).add(name + "!")

    lines = ["# Rebol's C surface, read from its own source. Written by",
             "# scripts/c-surface.py. No running binary is involved.",
             "",
             "# TYPESET name | the datatypes that belong to it"]
    for name in sorted(typesets):
        lines.append(f"TYPESET {name} | " + " ".join(sorted(typesets[name])))
    lines += ["", "# DATATYPE name | typeclass path make"]
    for name in sorted(table):
        row = table[name]
        lines.append(f"DATATYPE {name}! | {row['typeclass']} "
                     f"{row['path']} {row['make']}")
    lines += ["", "# ARMS typeclass | the actions its REBTYPE block implements"]
    for name in sorted(typeclasses):
        if name == SHARED_SERIES:
            continue
        got = sorted(a for a in typeclasses[name] if not a.startswith("="))
        lines.append(f"ARMS {name} | " + " ".join(got))
    lines += ["", "# ACTION name | declared spec"]
    for name in sorted(actions):
        lines.append(f"ACTION {name} | " + written(*actions[name]))
    lines += ["", "# NATIVE name | declared spec"]
    for name in sorted(natives):
        lines.append(f"NATIVE {name} | " + written(*natives[name]))
    lines += ["", "# LIBRARY name | the REBOL file that defines it"]
    for name in sorted(rebol_side):
        lines.append(f"LIBRARY {name} | {rebol_side[name]}")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(lines) + "\n")
    print(f"wrote {OUT.relative_to(REPO)}: {len(typesets)} typesets, "
          f"{len(table)} datatypes, {len(typeclasses)} typeclasses, "
          f"{len(actions)} actions, {len(natives)} natives, "
          f"{len(rebol_side)} REBOL functions")
    if "--print" in sys.argv:
        print("\n".join(lines))


if __name__ == "__main__":
    main()
