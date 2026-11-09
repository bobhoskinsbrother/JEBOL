#!/usr/bin/env python3
"""Write src/main/java/org/jebol/adapter/host/Uts46Tables.java from Unicode's own data.

Usage: make-uts46-tables.py <folder holding Unicode 16.0.0's files>

The folder holds, fetched from https://www.unicode.org/Public/:
    idna/16.0.0/IdnaMappingTable.txt
    16.0.0/ucd/extracted/DerivedBidiClass.txt
    16.0.0/ucd/extracted/DerivedJoiningType.txt
    16.0.0/ucd/extracted/DerivedCombiningClass.txt

16.0.0 is the Unicode version JDK 25's Character and Normalizer answer to, and
the version of src/testFixtures/resources/unicode/IdnaTestV2.txt, which checks
the result. Taking a newer version means taking all five files and the JDK
that matches them together.
"""

import base64
import pathlib
import re
import sys
import zlib

VERSION = "16.0.0"
STATUS = {"valid": 0, "deviation": 0, "ignored": 1, "mapped": 2, "disallowed": 3}
BIDI = ["L", "R", "AL", "EN", "ES", "ET", "AN", "CS", "NSM", "BN", "B", "S", "WS", "ON",
        "LRE", "LRO", "RLE", "RLO", "PDF", "LRI", "RLI", "FSI", "PDI"]
JOINING = ["U", "C", "D", "L", "R", "T"]
NO_MAPPING = 0xFFFF
CHUNK = 60000

OUT = pathlib.Path(__file__).resolve().parent.parent / "src/main/java/org/jebol/adapter/host/Uts46Tables.java"


def data_lines(path):
    text = path.read_text(encoding="utf-8")
    if f"Version: {VERSION}" not in text and f"-{VERSION}.txt" not in text:
        sys.exit(f"{path.name} is not Unicode {VERSION}")
    for line in text.splitlines():
        content = line.split("#", 1)[0].strip()
        if content:
            yield [field.strip() for field in content.split(";")]


def span(field):
    first, _, last = field.partition("..")
    return int(first, 16), int(last or first, 16)


def mapping_ranges(folder):
    ranges = []
    for fields in data_lines(folder / "IdnaMappingTable.txt"):
        first, last = span(fields[0])
        status = STATUS[fields[1]]
        mapping = None
        if fields[1] == "mapped":
            mapping = tuple(int(each, 16) for each in fields[2].split())
        ranges.append((first, last, status, mapping))
    ranges.sort()
    covered = 0
    for first, last, _, _ in ranges:
        if first != covered:
            sys.exit(f"the mapping table leaves a gap at {covered:04X}")
        covered = last + 1
    if covered != 0x110000:
        sys.exit("the mapping table stops short of U+10FFFF")
    return ranges


def property_ranges(path, names, default):
    values = [default] * 0x110000
    for fields in data_lines(path):
        first, last = span(fields[0])
        values[first:last + 1] = [names.index(fields[1])] * (last - first + 1)
    return values


def viramas(folder):
    found = []
    for fields in data_lines(folder / "DerivedCombiningClass.txt"):
        if fields[1] == "9":
            first, last = span(fields[0])
            found.extend(range(first, last + 1))
    return found


def three(value):
    return value.to_bytes(3, "little")


def pack_mapping(ranges):
    pool = []
    pool_index = {}
    entries = []
    for first, _, status, mapping in ranges:
        index = NO_MAPPING
        if mapping is not None:
            if mapping not in pool_index:
                pool_index[mapping] = len(pool)
                pool.append(mapping)
            index = pool_index[mapping]
        if entries and status != 2 and entries[-1][1] == status:
            continue
        entries.append((first, status, index))
    if len(pool) >= NO_MAPPING:
        sys.exit("the mapping pool outgrew two bytes")
    packed = bytearray(len(entries).to_bytes(4, "little"))
    for first, status, index in entries:
        packed += three(first) + bytes([status]) + index.to_bytes(2, "little")
    packed += len(pool).to_bytes(4, "little")
    for mapping in pool:
        packed += bytes([len(mapping)]) + b"".join(three(each) for each in mapping)
    return packed


def pack_runs(values):
    runs = []
    for code_point, value in enumerate(values):
        if not runs or runs[-1][1] != value:
            runs.append((code_point, value))
    packed = bytearray(len(runs).to_bytes(4, "little"))
    for first, value in runs:
        packed += three(first) + bytes([value])
    return packed


def pack_list(code_points):
    packed = bytearray(len(code_points).to_bytes(4, "little"))
    for each in code_points:
        packed += three(each)
    return packed


def java_chunks(name, raw):
    encoded = base64.b64encode(zlib.compress(raw, 9)).decode("ascii")
    pieces = [encoded[at:at + CHUNK] for at in range(0, len(encoded), CHUNK)]
    joined = ",\n".join(f'            "{piece}"' for piece in pieces)
    return f"    static final String[] {name} = {{\n{joined}\n    }};\n"


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    folder = pathlib.Path(sys.argv[1])
    tables = {
        "MAPPING": pack_mapping(mapping_ranges(folder)),
        "BIDI_CLASSES": pack_runs(property_ranges(folder / "DerivedBidiClass.txt", BIDI, BIDI.index("L"))),
        "JOINING_TYPES": pack_runs(property_ranges(folder / "DerivedJoiningType.txt", JOINING, JOINING.index("U"))),
        "VIRAMAS": pack_list(viramas(folder)),
    }
    body = "".join(java_chunks(name, raw) for name, raw in tables.items())
    OUT.write_text(f"""package org.jebol.adapter.host;

final class Uts46Tables {{

    static final String UNICODE_VERSION = "{VERSION}";

{body}
    private Uts46Tables() {{
    }}
}}
""", encoding="utf-8")
    sizes = ", ".join(f"{name} {len(raw)} bytes" for name, raw in tables.items())
    print(f"wrote {OUT.name}: {sizes}")


if __name__ == "__main__":
    main()
