import struct
import sys
import zlib

cases_path, output_path, table_path = sys.argv[1:4]
cases = [line.rstrip("\n").split("\t") for line in open(cases_path)]
lines = open(output_path).read().split("\n")
results = []
at = 0
while at < len(lines):
    if lines[at].startswith("image "):
        wide, high = map(int, lines[at].split()[1].split("x"))
        words = []
        for row in range(high):
            words += [int(word, 16) for word in lines[at + 1 + row].split()]
        assert len(words) == wide * high
        results.append((wide, high, zlib.crc32(struct.pack("<%dI" % len(words), *words))))
        at += 1 + high
    else:
        at += 1
assert len(results) == len(cases), (len(results), len(cases))
with open(table_path, "w") as table:
    table.write("image\tblock\tsize\tpixel crc32\n")
    for (image, block), (wide, high, crc) in zip(cases, results):
        table.write("%s\t%s\t%dx%d\t%08x\n" % (image, block, wide, high, crc))
print(len(results))
