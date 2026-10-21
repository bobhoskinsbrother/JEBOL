"""Audit the names in src/main/java against the rule in CLAUDE.md.

    python3 scripts/names.py             # every offence, by kind, with a suggestion
    python3 scripts/names.py --count     # totals only
    python3 scripts/names.py --sites     # declaration sites, to drive the renames

A method name is a message sent to the receiver, not a sentence about the
code: no articles, no hedges, no metaphors, no clauses. This finds the
articles (`readTheItemAt`, `makeATupleOfOctets`, `THE_JVM_HOST`) and the
`may` hedges. Names carrying `Of` are marked for review rather than given a
mechanical suggestion, because the tighter message usually wants a different
preposition (`readTheOctetOf` -> `readOctetAt`).

The test tree and the spec are outside the rule -- they name behaviour, not
messages -- so only `src/main/java` is walked. Comments and string literals
are stripped first, so prose that mentions a name is not counted. `ACCESS`,
`AES` and `Aria` are not articles and are not flagged: the article has to be
a word of the identifier followed by a capitalised word.

This script is scaffolding for the sweep and is deleted when the sweep is
done.
"""
import argparse
import collections
import os
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent / "src" / "main" / "java"

# A predicate that asks a kind question keeps its article: "is a file" is the
# question, and the article is the question.
KIND_QUESTIONS = {
    "isAFile", "isANumber", "isALocation", "isASpace", "isASeries",
    "isAnAsciiDigit", "isAnHttpsUrl", "isATerminal", "isASeparator",
    "isAPlainNumber", "isAnIssue", "isAName", "isALoneSurrogate",
    "isAnEventPort", "isALoopFrame", "isADroppedFile",
}

WORD = re.compile(r"[A-Z]+(?![a-z])|[A-Z][a-z0-9]*|[a-z0-9]+")

DECLARATION = re.compile(
    r"^\s*(?:(?:public|private|protected|static|final|default|abstract"
    r"|synchronized|sealed|non-sealed|native|strictfp|transient|volatile)\s+)*"
    r"(?:[A-Za-z_][A-Za-z0-9_]*\s*[.<>\[\],?]*\s+)?"
    r"([a-z][A-Za-z0-9]*)\s*\([^;{}]*\)\s*[{;]\s*$")

TYPE_DECLARATION = re.compile(
    r"\b(?:class|interface|record|enum)\s+((?:A|An|The)[A-Z][A-Za-z0-9]*)")

CONTROL_WORDS = {
    "if", "for", "while", "switch", "catch", "synchronized", "return", "new",
    "do", "else", "assert",
}


def without_comments_or_strings(text):
    out = []
    at = 0
    while at < len(text):
        c = text[at]
        if c == "/" and text[at:at + 2] == "//":
            end = text.find("\n", at)
            end = len(text) if end < 0 else end
            out.append(" " * (end - at))
            at = end
        elif c == "/" and text[at:at + 2] == "/*":
            end = text.find("*/", at + 2)
            end = len(text) if end < 0 else end + 2
            out.append("".join(ch if ch == "\n" else " " for ch in text[at:end]))
            at = end
        elif text[at:at + 3] == '"""':
            end = text.find('"""', at + 3)
            end = len(text) if end < 0 else end + 3
            out.append("".join(ch if ch == "\n" else " " for ch in text[at:end]))
            at = end
        elif c in "\"'":
            end = at + 1
            while end < len(text):
                if text[end] == "\\":
                    end += 2
                    continue
                if text[end] == c or text[end] == "\n":
                    break
                end += 1
            out.append("".join(ch if ch == "\n" else " " for ch in text[at:end + 1]))
            at = end + 1
        else:
            out.append(c)
            at += 1
    return "".join(out)


def words_of(identifier):
    return WORD.findall(identifier)


CLAUSE_WORDS = {"Which", "That", "Whose", "How", "What", "When", "Where", "Why"}


def suggestion_for(name):
    if name in KIND_QUESTIONS:
        return None
    if re.match(r"^may[A-Z]", name):
        return "can" + name[3:]
    if is_constant(name):
        words = name.split("_")
        kept = [word for word in words if word not in ("A", "AN", "THE")]
        if kept == words:
            return None
        needs_review = "OF" in kept or any(
            word in ("WHICH", "THAT", "WHOSE", "HOW", "WHAT") for word in kept)
        return "_".join(kept) + (" [review]" if needs_review else "")
    words = words_of(name)
    kept = [word for word in words if word not in ("A", "An", "The")]
    if kept == words:
        return None
    tightened = "".join(kept)
    needs_review = "Of" in tightened or any(word in CLAUSE_WORDS for word in kept)
    return tightened + (" [review]" if needs_review else "")


def is_constant(name):
    return name.isupper() and "_" in name


def find_offences():
    methods = collections.defaultdict(set)
    constants = collections.defaultdict(set)
    types = collections.defaultdict(set)
    variables = collections.defaultdict(set)
    for path in sorted(ROOT.rglob("*.java")):
        relative = path.relative_to(ROOT.parent.parent.parent)
        stripped = without_comments_or_strings(path.read_text())
        for at, line in enumerate(stripped.split("\n"), start=1):
            for match in TYPE_DECLARATION.finditer(line):
                name = match.group(1)
                if suggestion_for(name) is not None:
                    types[name].add((str(relative), at, match.start(1) + 1))
            declaration = DECLARATION.match(line)
            if declaration and declaration.group(1) not in CONTROL_WORDS:
                name = declaration.group(1)
                if suggestion_for(name) is not None:
                    methods[name].add((str(relative), at, declaration.start(1) + 1))
        for match in re.finditer(r"[A-Za-z_$][A-Za-z0-9_$]*", stripped):
            name = match.group(0)
            if suggestion_for(name) is None:
                continue
            line = stripped.count("\n", 0, match.start()) + 1
            column = match.start() - stripped.rfind("\n", 0, match.start())
            if is_constant(name):
                constants[name].add((str(relative), line, column))
            elif name not in methods and name not in types:
                variables[name].add((str(relative), line, column))
    return {"method": methods, "constant": constants, "type": types,
            "variable": variables}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--count", action="store_true")
    parser.add_argument("--sites", action="store_true")
    arguments = parser.parse_args()

    offences = find_offences()
    if arguments.count:
        total = 0
        for kind, found in offences.items():
            total += len(found)
            print("%-9s %4d names" % (kind, len(found)))
        print("%-9s %4d names" % ("total", total))
        return

    for kind, found in offences.items():
        if arguments.sites and kind == "variable":
            continue
        for name in sorted(found):
            sites = sorted(found[name])
            if arguments.sites:
                for site in sites:
                    print("%s\t%d\t%d\t%s\t%s" % (
                        site[0], site[1], site[2], name, suggestion_for(name)))
            else:
                print("%-9s %-60s %3d  %s" % (
                    kind, name, len(sites), suggestion_for(name)))

    if not arguments.sites:
        for kind, found in offences.items():
            print("%s: %d names" % (kind, len(found)), file=sys.stderr)


if __name__ == "__main__":
    try:
        main()
    except BrokenPipeError:
        os.dup2(os.open(os.devnull, os.O_WRONLY), sys.stdout.fileno())
        sys.exit(0)
