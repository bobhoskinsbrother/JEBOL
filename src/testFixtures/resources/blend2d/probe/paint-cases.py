import sys

PATTERN = "opaque 5x4 9"

cases = []


def add(image, pattern, block):
    cases.append((image, pattern, block))


stops = [
    "255.0.0 0 0.0.255 1",
    "255.0.0 0 0.255.0 0.5 0.0.255 1",
    "255.0.0.255 0 0.0.255.0 1",
    "20.200.40 0.25 250.250.10.120 0.75",
    "0.0.0 0 255.255.255 0 0.0.255 1",
    "255.0.0 -1 0.0.255 2",
]
for at, stop in enumerate(stops):
    add("noise 24x16 %d" % (10 + at), "", "fill linear %s 0x0 24x0 fill-all" % stop)
    add("noise 24x16 %d" % (20 + at), "", "fill linear %s 3x2 20x14 box 2x2 20x12" % stop)
    add("noise 24x16 %d" % (30 + at), "", "fill radial %s 12x8 12x8 9 fill-all" % stop)
    add("noise 24x16 %d" % (40 + at), "", "fill radial %s 12x8 8x6 9 circle 12x8 7" % stop)
    add("noise 24x16 %d" % (50 + at), "", "fill conical %s 12x8 fill-all" % stop)
add("noise 24x16 60", "", "fill conical 255.0.0 0 0.0.255 1 12x8 1.2 fill-all")
add("noise 24x16 61", "", "fill conical 255.0.0 0 0.0.255 1 12x8 0 3 fill-all")
add("noise 24x16 62", "", "fill linear 255.0.0 0 0.0.255 1 0x0 0x16 fill-all")
add("noise 24x16 63", "", "fill linear 255.0.0 0 0.0.255 1 5x5 5x5 fill-all")
add("noise 24x16 64", "", "fill linear 0x0 24x0 fill-all")
add("noise 24x16 65", "", "rotate 30 12x8 fill linear 255.0.0 0 0.0.255 1 0x0 24x0 box 4x2 16x12")
add("noise 24x16 66", "", "fill linear 255.0.0 0 0.0.255 1 0x0 24x0 rotate 30 12x8 box 4x2 16x12")
add("noise 24x16 67", "", "scale 0.5 fill radial 255.255.0 0 0.0.0 1 24x16 24x16 20 fill-all")
add("noise 24x16 68", "", "fill linear 255.0.0.200 0 0.0.255.90 1 0x0 24x16 alpha 0.6 blend multiply fill-all")
add("noise 24x16 69", "", "fill linear 255.0.0 0 0.0.255 1 0x0 24x0 pen 0.0.0 line-width 2 polygon 2x2 22x4 12x14")
for at, mode in enumerate(["", "pad ", "tile ", "flip ", "tile-y ", "flip-y ", "tile-x ", "tile-x-flip-y ",
                           "flip-x ", "flip-x-tile-y "]):
    add("noise 24x16 %d" % (70 + at), PATTERN, "fill PATTERN %sfill-all" % mode)
    add("noise 24x16 %d" % (80 + at), PATTERN, "translate 3x2 fill PATTERN %sbox 1x1 20x12" % mode)
add("noise 24x16 90", PATTERN, "translate 2.5x1.25 fill PATTERN fill-all")
add("noise 24x16 91", PATTERN, "scale 2 fill PATTERN fill-all")
add("noise 24x16 92", PATTERN, "rotate 20 12x8 fill PATTERN fill-all")
add("noise 24x16 93", PATTERN, "fill PATTERN alpha 0.5 blend screen circle 12x8 7")
add("noise 24x16 94", PATTERN, "pen PATTERN fill-all")

with open(sys.argv[1], "w") as script, open(sys.argv[2], "w") as table:
    for at, (image, pattern, block) in enumerate(cases):
        kind, size, seed = image.split()
        probe_block = block.replace("PATTERN", "<%s>" % pattern) if pattern else block
        script.write("c%d: draw <noise %s %s> [%s]\nprint c%d\n" % (at, size, seed, probe_block, at))
        table.write("%s\t%s\t%s\n" % (image, pattern, block))
