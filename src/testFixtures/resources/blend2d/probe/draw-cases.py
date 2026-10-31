import sys

ops = ["source-over", "source-copy", "source-in", "source-out", "source-atop", "destination-over",
       "destination-copy", "destination-in", "destination-out", "destination-atop", "xor", "clear",
       "plus", "minus", "modulate", "multiply", "screen", "overlay", "darken", "lighten", "color-dodge",
       "color-burn", "linear-burn", "linear-light", "pin-light", "hard-light", "soft-light", "difference",
       "exclusion"]

cases = []

def add(image, block):
    cases.append((image, block))

boxes = [
    ("noise 24x16 11", "box 3.25x2.5 0.5x5"),
    ("noise 24x16 12", "box 2x1.5 10x7.25"),
    ("noise 24x16 13", "box 1.25x1.75 9.5x8.5"),
    ("noise 40x10 14", "box 0.75x0.25 30.5x6.5"),
    ("noise 40x10 15", "box 6.4x1.3 25.2x7.1"),
    ("noise 24x16 16", "box 2.25x3.25 6x0.5"),
    ("noise 24x16 17", "box 2.1x3.6 7.3x1.2"),
    ("noise 24x16 18", "box 2.5x2.999 9x4.002"),
    ("noise 24x16 19", "box 4x4 8x8"),
    ("noise 24x16 20", "box 3.3x0.3 0.3x9.9"),
    ("noise 48x8 21", "box 1.5x1.5 44.25x5"),
    ("noise 48x8 22", "box 9.6x0.4 33.3x7.2"),
]
colours = ["200.60.10.140", "30.200.90", "255.255.255.0", "10.20.30.255"]
alphas = ["1", "0.6", "0.25", "0.999"]
n = 0
for image, box in boxes:
    for op in ops:
        n += 1
        colour = colours[n % len(colours)]
        alpha = alphas[n % len(alphas)]
        add(image, "fill %s alpha %s blend %s %s" % (colour, alpha, op, box))

for op in ops:
    add("noise 16x8 30", "fill 200.100.50.180 alpha 0.5 blend %s fill-all" % op)
    add("noise 16x8 31", "fill 200.100.50.180 blend %s clip 2.5x1.25 13.75x6.5 fill-all" % op)
    add("noise 16x8 32", "fill 90.10.250 alpha 0.7 blend %s clip 2x1 14x7 fill-all clip off box 0.5x0.5 3x3" % op)

for scale in ["2", "0.5", "1.5x0.75", "-1x1", "1x-1", "-2x-0.5"]:
    add("noise 24x16 40", "fill 10.200.100.200 translate 12x8 scale %s box -3.3x-2.2 5.5x3.75" % scale)
for move in ["0.5x0.25", "-0.75x0.5", "3x2", "0.001x0.999"]:
    add("noise 24x16 41", "fill 250.0.0.120 translate %s box 2x2 10x6 translate %s box 1x1 4x4" % (move, move))
add("noise 24x16 42", "fill 250.0.0.120 scale 0 0 box 0x0 20x10")
add("noise 24x16 43", "fill 250.0.0.120 scale 2 reset-matrix box 1x1 4x4")
add("noise 24x16 44", "fill 0.0.250 clip 3.5x2.5 18.25x11.75 clear-all")
add("noise 24x16 45", "fill 0.0.250 alpha 0.3 clear 2.5x2.5 9.25x7 clear 12x3 20x9")
add("noise 24x16 46", "clip 2x2 10x10 clip 6x6 20x14 fill 255.255.0 fill-all clip none fill 0.0.255.100 box 0x0 4x4")
add("noise 24x16 47", "scale 2 clip 1.25x1.25 5x4 fill 255.0.255.200 fill-all")
add("noise 24x16 48", "clip 30x30 40x40 fill 255.0.0 fill-all box 0x0 5x5 clip off fill-all")

walks = [
    "10 fill 0.255.0 fill-all",
    "10 20 fill 0.255.0 fill-all",
    "10 alpha 0.5 fill 0.255.0 fill-all",
    "fill 0.255.0 10 fill 0.0.255 fill-all",
    "fill 0.255.0 bogus 1 2 fill-all",
    "fill 1x1 fill-all",
    "fill 0.0.255 fill off fill-all",
    "fill 0.0.255 fill on fill-all",
    "fill 0.0.255 blend miter fill-all",
    "fill 0.0.255.100 blend 15 fill-all",
    "fill 0.0.255.100 blend 'multiply fill-all",
    "fill 0.0.255.100 blend multiply blend off fill-all",
    "fill 0.0.255.100 blend multiply blend none fill-all",
    "fill 0.0.255.100 blend 99 fill-all",
    "fill 0.0.255.100 composite xor fill-all",
    "fill 0.0.255.100 alpha 50% fill-all",
    "fill 0.0.255.100 alpha 3 fill-all",
    "fill 0.0.255.100 alpha -1 fill-all",
    "fill 0.0.255.100 alpha 0.001 fill-all",
    "fill 0.0.255.100 alpha 1x1 fill-all",
    "fill 0.0.255 line 1x1 2x2 fill-all",
    "fill 0.0.255 line-width 0 line 1x1 2x2 fill-all",
    "fill 0.0.255 line-join 5x5 fill-all",
    "fill 0.0.255 line-join miter bevel fill-all",
    "fill 0.0.255 line-cap 9 fill-all",
    "fill 0.0.255 line-cap 1 2 fill-all",
    "fill 0.0.255 polygon 1x1 2x2 3x1 fill-all",
    "polygon 1x1 2x2 3x1 fill 0.0.255 box 1x1 3x3",
    "circle 5x5 3 fill 0.0.255 box 1x1 3x3",
    "circle 5x5 fill 0.0.255 box 1x1 3x3",
    "ellipse 1x1 4x4 fill 0.0.255 box 1x1 3x3",
    "arc 5x5 3x3 0 90 pie fill 0.0.255 box 1x1 3x3",
    "triangle 1x1 3x1 2x3 fill 0.0.255 box 1x1 3x3",
    "point 1x1 2x2 fill 0.0.255 box 1x1 3x3",
    "point-size 3 fill 0.0.255 box 1x1 3x3",
    "cubic 1x1 2x2 3x3 4x4 fill 0.0.255 box 1x1 3x3",
    "text 1x1 {hello} fill 0.0.255 box 1x1 3x3",
    "text 1x1 12 {hello} fill 0.0.255 box 1x1 3x3",
    "shape [move 1x1 line 3x3] fill 0.0.255 box 1x1 3x3",
    "fill 0.0.255 box 1x1",
    "fill 0.0.255 box 1x1 3x3 2 fill 255.0.0 box 5x5 2x2",
    "fill 0.0.255 rotate 0 box 1x1 3x3",
    "fill 0.0.255 rotate 0 4x4 box 1x1 3x3",
    "fill 0.0.255 scale 2x1 box 1x1 3x3",
    "fill 0.0.255 translate 1 box 1x1 3x3 fill-all",
    "fill 0.0.255 clip 1x1 fill-all",
    "fill 0.0.255 clip on fill-all",
    "fill 0.0.255 move 1x1 fill-all",
    "fill 0.0.255 close fill-all",
    "fill 10.20.30 blend",
    "fill-all blend multiply",
    "pen off fill 0.0.255 box 1x1 3x3",
    "fill 0.0.255",
    "fill 1.2.3.4.5 fill-all",
    "fill 9.8.7.6 fill-all",
]
for walk in walks:
    add("noise 12x8 50", walk)
add("pair 8x6", "fill 255.0.0 box 2x1 6x4")
add("pair 5x3", "fill 0.0.255.128 fill-all")
add("pair 4.6x2.4", "fill 0.255.0 box 1x0 1x1")

with open(sys.argv[1], "w") as script, open(sys.argv[2], "w") as table:
    for at, (image, block) in enumerate(cases):
        kind, *rest = image.split()
        if kind == "noise":
            literal = "<noise %s %s>" % (rest[0], rest[1])
        else:
            literal = rest[0]
        script.write("c%d: draw %s [%s]\nprint c%d\n" % (at, literal, block, at))
        table.write("%s\t%s\n" % (image, block))
