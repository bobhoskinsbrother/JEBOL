import hashlib
import os
import struct
import sys

source, table_folder, font_folder = sys.argv[1:4]
font = bytearray(open(source, "rb").read())


def u16(at):
    return struct.unpack(">H", font[at:at + 2])[0]


def u32(at):
    return struct.unpack(">I", font[at:at + 4])[0]


records = {bytes(font[12 + 16 * i:16 + 16 * i]): 12 + 16 * i for i in range(u16(4))}


def glyph(code):
    table = u32(records[b"cmap"] + 8)
    for encoding in range(u16(table + 2)):
        record = table + 4 + 8 * encoding
        if u16(record) == 3 and u16(record + 2) == 1:
            sub = table + u32(record + 4)
            segments = u16(sub + 6) // 2
            for k in range(segments):
                end = u16(sub + 14 + 2 * k)
                start = u16(sub + 16 + 2 * segments + 2 * k)
                if start <= code <= end:
                    delta = u16(sub + 16 + 4 * segments + 2 * k)
                    offset = u16(sub + 16 + 6 * segments + 2 * k)
                    if offset == 0:
                        return (code + delta) & 0xFFFF
                    found = u16(sub + 16 + 6 * segments + 2 * k + offset + 2 * (code - start))
                    return (found + delta) & 0xFFFF if found else 0
    return 0


G = {c: glyph(ord(c)) for c in "AVToWaYeLTvykr"}
GLYPH_COUNT = u16(u32(records[b"maxp"] + 8) + 4)


def format0(pairs):
    body = struct.pack(">HHHH", len(pairs), 0, 0, 0)
    for left, right, value in pairs:
        body += struct.pack(">HHh", G[left], G[right], value)
    return body


def windows_subtable(fmt, coverage, body):
    return struct.pack(">HHBB", 0, 6 + len(body), fmt, coverage) + body


def mac_subtable(fmt, coverage, body):
    return struct.pack(">IBBH", 8 + len(body), coverage, fmt, 0) + body


def windows_kern(subtables):
    return struct.pack(">HH", 0, len(subtables)) + b"".join(subtables)


def mac_kern(subtables):
    return struct.pack(">HHI", 1, 0, len(subtables)) + b"".join(subtables)


def format2(classes_left, classes_right, values):
    row_width = 2 * (max(classes_right.values()) + 1)
    header = 8
    left_at = header
    left_first = min(G[c] for c in classes_left)
    left_count = max(G[c] for c in classes_left) - left_first + 1
    right_at = left_at + 4 + 2 * left_count
    right_first = min(G[c] for c in classes_right)
    right_count = max(G[c] for c in classes_right) - right_first + 1
    array_at = right_at + 4 + 2 * right_count
    rows = max(classes_left.values()) + 1
    left = [0] * left_count
    for c, row in classes_left.items():
        left[G[c] - left_first] = 6 + array_at + row * row_width
    right = [0] * right_count
    for c, column in classes_right.items():
        right[G[c] - right_first] = column * 2
    kerning = [0] * (rows * (row_width // 2))
    for (row, column), value in values.items():
        kerning[row * (row_width // 2) + column] = value
    body = struct.pack(">HHHH", row_width, 6 + left_at, 6 + right_at, 6 + array_at)
    body += struct.pack(">HH", left_first, left_count) + struct.pack(">%dH" % left_count, *left)
    body += struct.pack(">HH", right_first, right_count) + struct.pack(">%dH" % right_count, *right)
    body += struct.pack(">%dh" % len(kerning), *kerning)
    return body


def format3(values, left_classes, right_classes, index):
    body = struct.pack(">HBBBB", GLYPH_COUNT, len(values), max(left_classes.values()) + 1,
                       max(right_classes.values()) + 1, 0)
    body += struct.pack(">%dh" % len(values), *values)
    left = bytearray(GLYPH_COUNT)
    right = bytearray(GLYPH_COUNT)
    for c, k in left_classes.items():
        left[G[c]] = k
    for c, k in right_classes.items():
        right[G[c]] = k
    body += bytes(left) + bytes(right) + bytes(index)
    return body


def unsorted_pairs():
    pairs = []
    letters = "AVToWaYeLTvykr"
    for left in letters:
        for right in letters:
            if left != right and len(pairs) < 70:
                pairs.append((left, right, -(len(pairs) % 9) * 10 - 10))
    pairs.sort(key=lambda p: (G[p[0]] << 16) | G[p[1]])
    pairs[5], pairs[50] = pairs[50], pairs[5]
    return pairs


SORTED = sorted([("A", "V", -80), ("T", "o", -60), ("W", "a", -50), ("Y", "e", -70), ("L", "T", -90)],
                key=lambda p: (G[p[0]] << 16) | G[p[1]])

kerns = {
    "kern-windows.ttf": windows_kern([windows_subtable(0, 0x01, format0(SORTED))]),
    "kern-unsorted.ttf": windows_kern([windows_subtable(0, 0x01, format0(unsorted_pairs()))]),
    "kern-classes.ttf": windows_kern([
        windows_subtable(2, 0x01, format2({"A": 1, "L": 2}, {"V": 1, "T": 2}, {(1, 1): -100, (2, 2): -40})),
        windows_subtable(0, 0x09, format0(sorted([("A", "V", -30)], key=lambda p: (G[p[0]] << 16) | G[p[1]]))),
        windows_subtable(0, 0x03, format0(sorted([("L", "T", -200)], key=lambda p: (G[p[0]] << 16) | G[p[1]]))),
        windows_subtable(0, 0x00, format0(SORTED))]),
    "kern-mac.ttf": mac_kern([
        mac_subtable(0, 0x00, format0(SORTED)),
        mac_subtable(3, 0x00, format3([0, -120, 45], {"W": 1, "Y": 2}, {"a": 1, "e": 1},
                                      [0, 0, 0, 1, 0, 2])),
        mac_subtable(0, 0x80, format0(SORTED))]),
}

for name, kern in kerns.items():
    open(os.path.join(table_folder, name.replace(".ttf", ".kern")), "wb").write(kern)
    out = bytearray(font)
    out[records[b"GPOS"]:records[b"GPOS"] + 4] = b"gpos"
    stat = records[b"STAT"]
    while len(out) % 4:
        out.append(0)
    offset = len(out)
    out += kern
    out += bytes(65536)
    out[stat:stat + 4] = b"kern"
    out[stat + 8:stat + 16] = struct.pack(">II", offset, len(kern))
    open(os.path.join(font_folder, name), "wb").write(bytes(out))
    print(name, hashlib.sha256(bytes(out)).hexdigest())
