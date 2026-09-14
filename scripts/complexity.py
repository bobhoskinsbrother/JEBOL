#!/usr/bin/env python3
"""How much deciding each method does, which is goal 11's measure.

Cyclomatic complexity is one plus the branch points: every if, while, for,
case, catch, &&, ||, ternary and switch guard. It is a crude number and a
useful one here, because the thing goal 11 removes -- a registration table
deciding what an action means for each datatype -- is made of exactly those.

It moves where the work happens and nowhere else. Migrating APPEND, INSERT,
CLEAR, REMOVE and LENGTH? into the datatypes took defineSeries from 263 to
227, while defineSet stayed at 162 because nothing in it had been touched.
Counting `case` arms saw neither, because an if-chain is dispatch too.

    python3 scripts/complexity.py            the twenty worst
    python3 scripts/complexity.py 60         the sixty worst
    python3 scripts/complexity.py --ceilings check nothing has got worse

Three things are deliberately not measured away and should stay high:
BrotliDictionaryMatches.findAll carries "Do not tidy it" because its shape is
the C's, converted mechanically; LzmaEncoder and the other compressors are the
same kind of port; and SymmetryPartitionSort is Rebol's own sort.
"""

import glob
import os
import re
import sys

BRANCH = re.compile(r'\b(if|while|for|case|catch)\b|&&|\|\||\?(?!\.)|\bwhen\b')

SIGNATURE = re.compile(
    r'^\s{4}(?:@\w+\s+)*'
    r'(?:(?:public|private|protected|static|final|abstract|default'
    r'|synchronized|native)\s+)*'
    r'[\w<>,\[\]\.\? ]+\s+(\w+)\s*\(')

CEILINGS = 'scripts/complexity-ceilings.txt'


def blanked(match):
    """Keeps the newlines, so every line number stays where it was."""
    return '\n' * match.group(0).count('\n')


def scrubbed(text):
    """A brace inside a literal or a comment is not a brace.

    Every one of these was found by the counter getting an answer wrong: a
    text block holding REBOL source, `case '{' ->` in the reader, and
    `{@code ...}` in javadoc each made a method look hundreds of lines long.
    """
    text = re.sub(r'"""(.|\n)*?"""', blanked, text)
    text = re.sub(r"'(\\.|[^'\\])'", "''", text)
    text = re.sub(r'"(\\.|[^"\\\n])*"', '""', text)
    text = re.sub(r'/\*(.|\n)*?\*/', blanked, text)
    return re.sub(r'//[^\n]*', '', text)


def methodsIn(path):
    raw = open(path).read()
    lines = scrubbed(raw).split('\n')
    if len(lines) != len(raw.split('\n')):
        raise AssertionError(f'{path}: scrubbing moved the line numbers')
    at = 0
    while at < len(lines):
        found = SIGNATURE.match(lines[at])
        if not found or lines[at].rstrip().endswith(';'):
            at += 1
            continue
        depth, opened, end = 0, False, at
        while end < len(lines):
            depth += lines[end].count('{') - lines[end].count('}')
            opened = opened or '{' in lines[end]
            if opened and depth <= 0:
                break
            end += 1
        if end >= len(lines):
            at += 1
            continue
        body = '\n'.join(lines[at:end + 1])
        yield (1 + len(BRANCH.findall(body)),
               os.path.basename(path), found.group(1), at + 1, end - at + 1)
        at = end + 1


def everyMethod():
    measured = []
    for path in sorted(glob.glob('src/main/java/**/*.java', recursive=True)):
        measured.extend(methodsIn(path))
    return measured


def report(measured, howMany):
    print(f'{len(measured)} methods measured')
    for score, where, name, line, span in sorted(measured, reverse=True)[:howMany]:
        print(f'  cc={score:4}  {span:5} lines  {where}:{line}  {name}')
    for limit in (10, 15, 25, 40):
        print(f'over {limit}: {len([m for m in measured if m[0] > limit])}')


def checkCeilings(measured):
    """Fails when a method has got worse than it was recorded at.

    A ratchet rather than a limit: nothing has to come down, but nothing may
    go up, so a refactor that stalls cannot quietly undo itself.
    """
    if not os.path.exists(CEILINGS):
        print(f'no {CEILINGS} yet -- writing what is there now')
        writeCeilings(measured)
        return 0
    recorded = {}
    for row in open(CEILINGS):
        if row.strip() and not row.startswith('#'):
            score, name = row.split(None, 1)
            recorded[name.strip()] = int(score)
    risen = []
    for score, where, name, line, _ in measured:
        was = recorded.get(f'{where}:{name}')
        if was is not None and score > was:
            risen.append((f'{where}:{name}', was, score, line))
    for name, was, now, line in sorted(risen, key=lambda r: r[1] - r[2]):
        print(f'  {name}  was {was}, now {now}  (line {line})')
    if risen:
        print(f'\n{len(risen)} method(s) do more deciding than they used to.')
        return 1
    print('nothing has got worse')
    return 0


def writeCeilings(measured):
    worst = {}
    for score, where, name, _, _ in measured:
        key = f'{where}:{name}'
        worst[key] = max(worst.get(key, 0), score)
    with open(CEILINGS, 'w') as written:
        written.write('# cyclomatic complexity ceilings -- see scripts/complexity.py\n')
        written.write('# a method may not do more deciding than it does here.\n')
        for key in sorted(worst, key=lambda k: (-worst[k], k)):
            if worst[key] > 5:
                written.write(f'{worst[key]}\t{key}\n')
    print(f'wrote {CEILINGS}')


if __name__ == '__main__':
    everything = everyMethod()
    if '--ceilings' in sys.argv:
        sys.exit(checkCeilings(everything))
    if '--write-ceilings' in sys.argv:
        writeCeilings(everything)
        sys.exit(0)
    asked = [a for a in sys.argv[1:] if a.isdigit()]
    report(everything, int(asked[0]) if asked else 20)
