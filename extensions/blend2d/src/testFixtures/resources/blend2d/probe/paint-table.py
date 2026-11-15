import sys

cases_path, output_path, table_path = sys.argv[1:4]
cases = [line.rstrip("\n").split("\t") for line in open(cases_path)]
lines = open(output_path).read().split("\n")
images = []
at = 0
while at < len(lines):
    if lines[at].startswith("image "):
        wide, high = map(int, lines[at].split()[1].split("x"))
        words = []
        for row in range(high):
            words += lines[at + 1 + row].split()
        assert len(words) == wide * high
        images.append(("%dx%d" % (wide, high), " ".join(words)))
        at += 1 + high
    else:
        at += 1
assert len(images) == len(cases), (len(images), len(cases))
with open(table_path, "w") as table:
    table.write("image\tpattern\tblock\tsize\tpixels\n")
    for (image, pattern, block), (size, pixels) in zip(cases, images):
        table.write("%s\t%s\t%s\t%s\t%s\n" % (image, pattern, block, size, pixels))
print(len(images))
