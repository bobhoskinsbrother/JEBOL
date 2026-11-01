import sys

ops = ["source-over", "source-copy", "source-in", "source-out", "source-atop", "destination-over",
       "destination-copy", "destination-in", "destination-out", "destination-atop", "xor", "clear",
       "plus", "minus", "modulate", "multiply", "screen", "overlay", "darken", "lighten", "color-dodge",
       "color-burn", "linear-burn", "linear-light", "pin-light", "hard-light", "soft-light", "difference",
       "exclusion"]

OPAQUE = "opaque 5x4 9"
PREMULTIPLIED = "premul 5x4 9"

cases = []


def exact(image, source, setup, block):
    cases.append((image, source, setup, block, "exact"))


def close(image, source, setup, block):
    cases.append((image, source, setup, block, "close"))


for at, corner in enumerate(["0x0", "3x2", "-2x-1", "22x14", "30x30", "-5x0", "0x-4", "19x0"]):
    exact("noise 24x16 %d" % (100 + at), OPAQUE, "", "image SOURCE %s" % corner)
    exact("noise 24x16 %d" % (110 + at), PREMULTIPLIED, "", "image SOURCE %s" % corner)
for at, op in enumerate(ops):
    exact("noise 24x16 %d" % (120 + at), PREMULTIPLIED, "", "blend %s image SOURCE 2x1" % op)
    exact("noise 24x16 %d" % (160 + at), OPAQUE, "", "alpha 0.6 blend %s image SOURCE 2x1" % op)
for at, corner in enumerate(["2.5x1.25", "0.3x0.7", "1x0.5", "0.5x3", "-0.25x-0.75", "21.5x13.5"]):
    close("noise 24x16 %d" % (200 + at), OPAQUE, "", "image SOURCE %s" % corner)
    close("noise 24x16 %d" % (210 + at), PREMULTIPLIED, "", "image SOURCE %s" % corner)
exact("noise 24x16 220", OPAQUE, "", "translate 3x2 image SOURCE 1x1")
close("noise 24x16 221", OPAQUE, "", "translate 0.5x0.5 image SOURCE 1x1")
close("noise 24x16 222", OPAQUE, "", "scale 2 image SOURCE 1x1")
close("noise 24x16 223", OPAQUE, "", "scale 1.5 0.75 image SOURCE 1x1")
exact("noise 24x16 224", OPAQUE, "", "scale -1 1 image SOURCE -10x2")
close("noise 24x16 225", OPAQUE, "", "rotate 30 image SOURCE 6x1")
close("noise 24x16 226", PREMULTIPLIED, "", "rotate 45 12x8 image SOURCE 9x6")
exact("noise 24x16 227", OPAQUE, "", "scale 0 0 image SOURCE 1x1")
close("noise 24x16 228", OPAQUE, "", "image SOURCE 2x2 10x8")
close("noise 24x16 229", OPAQUE, "", "image SOURCE 2x2 3x3")
exact("noise 24x16 230", OPAQUE, "", "image SOURCE 2x2 5x4")
close("noise 24x16 231", OPAQUE, "", "image SOURCE 2.5x2.5 5x4")
close("noise 24x16 232", OPAQUE, "", "image SOURCE 12x8 -10x-8")
exact("noise 24x16 233", OPAQUE, "", "image SOURCE 2x2 0x8")
close("noise 24x16 234", PREMULTIPLIED, "", "image SOURCE 1x1 22x14")
close("noise 24x16 235", OPAQUE, "", "rotate 20 image SOURCE 4x2 12x9")
close("noise 24x16 236", OPAQUE, "", "translate 2x1 scale 2 image SOURCE 1x1 6x4")
close("noise 24x16 237", OPAQUE, "", "clip 2x2 12x10 image SOURCE 0x0 20x14")
exact("noise 24x16 238", OPAQUE, "", "clip 2.5x2.5 10.25x9.75 image SOURCE 1x1")
exact("noise 24x16 239", OPAQUE, "", "clip 30x30 40x40 image SOURCE 1x1")
exact("noise 24x16 240", OPAQUE, "", "alpha 0.5 image SOURCE 1x1")
exact("noise 24x16 241", OPAQUE, "", "alpha 0 image SOURCE 1x1")
close("noise 24x16 242", OPAQUE, "", "fill 255.0.0 image SOURCE 1x1 10x8 fill-all box 0x0 3x3")
exact("noise 24x16 243", OPAQUE, "", "fill 255.0.0 image none 1x1 fill-all")
exact("noise 24x16 244", OPAQUE, "", "fill 255.0.0 image SOURCE fill-all")
exact("noise 24x16 245", OPAQUE, "", "fill 255.0.0 image 3x3 1x1 fill-all")
exact("noise 24x16 246", OPAQUE, "H: image SOURCE", "image H 4x3")
close("noise 24x16 247", OPAQUE, "H: image SOURCE", "image H 4x3 12x8")
exact("noise 24x16 248", OPAQUE, "H: image SOURCE", "image H 4x3 image SOURCE 14x9")
exact("noise 24x16 249", OPAQUE, "P: path [move 0x0]", "fill 255.0.0 image P 1x1 box 0x0 3x3")
exact("noise 24x16 250", "noise 5x4 9", "", "blend source-copy image SOURCE 2x2")

files = ["rgba.png", "rgb.png", "grey.png", "grey-alpha.png", "palette.png", "rgb.bmp", "alpha.bmp",
         "colour.jpg", "grey.jpg", "rgba.qoi", "rgb.qoi"]
for at, name in enumerate(files):
    kept = close if name.endswith(".jpg") else exact
    kept("noise 24x16 %d" % (260 + at), "", "", "blend source-copy image %%%s 1x1" % name)
    kept("noise 24x16 %d" % (280 + at), "", "", "image %%%s 1x1" % name)
    kept("noise 24x16 %d" % (300 + at), "", "H: image %%%s" % name, "fill H fill-all")
for at, name in enumerate(["refused.gif", "refused.txt", "refused-v5.bmp", "missing.png"]):
    exact("noise 24x16 %d" % (320 + at), "", "", "fill 255.0.0 image %%%s 1x1 fill-all" % name)

with open(sys.argv[1], "w") as script, open(sys.argv[2], "w") as table:
    for at, (image, source, setup, block, kept) in enumerate(cases):
        if source:
            script.write("SOURCE: <%s>\n" % source)
        if setup:
            script.write(setup + "\n")
        kind, size, seed = image.split()
        script.write("c%d: draw <%s %s %s> [%s]\nprint c%d\n" % (at, kind, size, seed, block, at))
        table.write("%s\t%s\t%s\t%s\t%s\n" % (image, source, setup, block, kept))
