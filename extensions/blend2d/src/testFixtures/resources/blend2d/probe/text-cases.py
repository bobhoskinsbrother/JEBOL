import sys

FONT = "%NotoSans-Regular.ttf"

cases = []


def add(image, setup, block, kept="exact"):
    cases.append((image, setup, block, kept))


for at, text in enumerate(["A", "g", "i", "@", "%", "Q", "&", "W", "8", "é", " ", " ", ""]):
    add("pair 24x20", "", "fill 0.0.0 font %s text 3x15 20 {%s}" % (FONT, text))
for at, text in enumerate(["AV", "To", "Wa", "LT", "Yo", "AVAVAV", "fi", "fl", "ffi", "office", "Æsir"]):
    add("pair 64x20", "", "fill 0.0.0 font %s text 2x15 16 {%s}" % (FONT, text))
add("pair 160x16", "", "fill 0.0.0 font %s text 2x12 {The quick brown fox jumps over the lazy dog.}" % FONT)
for at, size in enumerate(["6", "10", "13.5", "24", "1", "0.5", "7.3"]):
    add("pair 48x32", "", "fill 0.0.0 font %s text 2x26 %s {Ag}" % (FONT, size))
for at, corner in enumerate(["2.5x12.25", "0.3x14.7", "-3x12", "40x12", "10x40", "1x1"]):
    add("pair 48x20", "", "fill 0.0.0 font %s text %s 14 {Hm}" % (FONT, corner))
add("noise 48x24 700", "", "fill 200.40.30 font %s text 3x18 18 {Kx}" % FONT)
add("noise 48x24 701", "", "fill 200.40.30.120 font %s text 3x18 18 {Kx}" % FONT)
add("noise 48x24 702", "", "alpha 0.5 blend multiply fill 30.90.200 font %s text 3x18 18 {Kx}" % FONT)
add("noise 48x24 703", "", "fill linear 255.0.0 0 0.0.255 1 0x0 48x0 font %s text 3x18 18 {Kx}" % FONT, "close")
add("pair 48x48", "", "fill 0.0.0 rotate 20 font %s text 6x20 16 {Ry}" % FONT)
add("pair 48x48", "", "fill 0.0.0 scale 2 font %s text 2x10 8 {Ry}" % FONT)
add("pair 48x48", "", "fill 0.0.0 scale -1 1 translate -46x0 font %s text 2x20 16 {Ry}" % FONT)
add("pair 48x48", "", "fill 0.0.0 scale 1.5 0.75 font %s text 2x40 16 {Ry}" % FONT)
add("pair 48x48", "", "fill 0.0.0 clip 4x4 30x30 font %s text 2x30 24 {Bd}" % FONT)
add("pair 48x48", "", "fill 0.0.0 scale 0 0 font %s text 2x30 24 {Bd}" % FONT)
add("pair 48x20", "", "fill 0.0.0 font %s text 2x15 14 {Ωπ€}" % FONT)
add("pair 48x20", "", "fill 0.0.0 font %s text 2x15 14 {Абв}" % FONT)
add("pair 48x20", "", "fill 0.0.0 font %s text 2x15 14 {中}" % FONT)
add("pair 48x20", "", "fill 0.0.0 font %s text 2x15 14 {été ā}" % FONT)
add("pair 48x20", "F: font %s" % FONT, "fill 0.0.0 font F text 2x15 14 {Ok}")
add("pair 48x20", "", "fill 0.0.0 font {NotoSans-Regular.ttf} text 2x15 14 {Ok}")
add("pair 48x20", "", "fill 0.0.0 text 2x15 14 {Ok}")
add("pair 48x20", "", "fill 0.0.0 font %missing.ttf text 2x15 14 {Ok}")
add("pair 48x20", "", "fill 0.0.0 font %s font %%missing.ttf text 2x15 {Ok}" % FONT)
add("pair 48x20", "", "fill 0.0.0 font %s font %%missing.ttf text 2x15 12 {Ok}" % FONT)
add("pair 48x20", "", "fill 0.0.0 font %s font 3 text 2x15 {Ok}" % FONT)
add("pair 48x20", "", "fill 0.0.0 font %s font 3 text 2x15 12 {Ok}" % FONT)
add("pair 48x20", "", "fill 0.0.0 font %s text 2x15 {Ok} text 24x15 {Ok}" % FONT)
add("pair 48x20", "", "fill 0.0.0 font %s text 2x15 14 {Ok} text 24x15 {Ok}" % FONT)
add("pair 48x20", "", "fill 0.0.0 font %s text 2x15 0 {Ok} text 24x15 -4 {Ok}" % FONT)
add("pair 48x20", "", "font %s text 2x15 14 {Ok} fill 0.0.0 text 24x15 {Ok}" % FONT)
add("pair 48x20", "", "fill 0.0.0 font %s text 2x15 14 3 fill-all" % FONT)
add("pair 48x20", "", "fill 0.0.0 font %s text 14 {Ok} box 0x0 3x3" % FONT)
add("pair 48x20", "", "fill 0.0.0 font %s text 2x15 14 bogus box 0x0 3x3" % FONT)
add("pair 48x20", "P: path [move 0x0]", "fill 0.0.0 font P text 2x15 14 {Ok} box 0x0 3x3")
add("pair 48x20", "", "fill off pen 0.0.0 font %s text 2x15 14 {Ok}" % FONT)

CFF = "%NotoSans-Regular.otf"
for at, text in enumerate(["A", "g", "&", "@", "%", "Q", "8", "W", "é", "€", "Ω", "Ж"]):
    add("pair 24x20", "", "fill 0.0.0 font %s text 3x15 20 {%s}" % (CFF, text))
for at, text in enumerate(["AV", "To", "fi", "office", "Wa"]):
    add("pair 64x20", "", "fill 0.0.0 font %s text 2x15 16 {%s}" % (CFF, text))
add("pair 160x16", "", "fill 0.0.0 font %s text 2x12 {The quick brown fox jumps over the lazy dog.}" % CFF)
for at, size in enumerate(["7", "24", "1", "13.5"]):
    add("pair 48x32", "", "fill 0.0.0 font %s text 2x26 %s {Ag}" % (CFF, size))
add("pair 48x48", "", "fill 0.0.0 rotate 20 font %s text 6x20 16 {Ry}" % CFF)
add("pair 48x48", "", "fill 0.0.0 scale 1.5 0.75 font %s text 2x40 16 {Ry}" % CFF)
add("noise 48x24 704", "", "alpha 0.5 blend multiply fill 30.90.200 font %s text 3x18 18 {Kx}" % CFF)
add("pair 48x20", "G: font %s" % CFF, "fill 0.0.0 font G text 2x15 14 {Ok}")
add("pair 48x20", "", "fill 0.0.0 font %s text 2x15 14 {Ok} font %s text 24x15 {Ok}" % (FONT, CFF))

for name in ["kern-windows", "kern-unsorted", "kern-classes", "kern-mac"]:
    for text in ["AVToWaYeLT", "LTAV", "vykrAo", "WaYe"]:
        add("pair 120x20", "", "fill 0.0.0 font %%%s.ttf text 2x15 16 {%s}" % (name, text))

with open(sys.argv[1], "w", encoding="utf-8") as script, open(sys.argv[2], "w", encoding="utf-8") as table:
    for at, (image, setup, block, kept) in enumerate(cases):
        if setup:
            script.write(setup + "\n")
        kind, *rest = image.split()
        literal = "<noise %s %s>" % (rest[0], rest[1]) if kind == "noise" else rest[0]
        script.write("c%d: draw %s [%s]\nprint c%d\n" % (at, literal, block, at))
        table.write("%s\t\t%s\t%s\t%s\n" % (image, setup, block, kept))
