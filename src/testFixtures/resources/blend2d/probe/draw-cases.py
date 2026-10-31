import math
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
polygons = [
    "polygon 3.3x2.1 35.7x6.4 20.2x27.9",
    "polygon 2x2 38x2 38x28 2x28",
    "polygon 2.5x2.5 37.5x2.5 37.5x27.5 2.5x27.5",
    "polygon 20x1 39x29 1x29",
    "polygon 1x15 39x14 39x16",
    "polygon 19.9x0.5 20.1x0.5 20.3x29.5 19.7x29.5",
    "polygon 0.5x10.25 39.5x10.75 39.5x11 0.5x10.5",
    "polygon 3x3 37x3 20x15 37x27 3x27 20x15",
    "polygon 5x5 35x25 35x5 5x25",
    "polygon 20x1 26x28 4x10 36x10 14x28",
    "polygon 4x4 36x4 36x26 4x26 4x8 30x8 30x22 10x22 10x8 4x8",
    "polygon -10x-10 50x-5 45x40 -5x35",
    "polygon -20x5 10x2 12x28 -20x25",
    "polygon 30x5 60x2 62x28 30x25",
    "polygon 5x-20 35x-15 30x10 8x12",
    "polygon 5x20 35x18 30x45 8x50",
    "polygon -5x-5 45x-5 45x35 -5x35",
    "polygon 50x50 60x50 55x60",
    "polygon 1x1",
    "polygon 1x1 30x20",
    "polygon 0.001x0.001 39.999x0.001 39.999x29.999",
    "polygon 7.875x3.125 32.0625x3.9375 31.5x26.5 8.25x25.75",
    "polygon 3.999x4.001 4.001x25.999 36.5x14",
    "polygon 0x0 40x30 0x30 40x0",
    "polygon 39x1 1x2 38x3 2x4 37x5 3x6 36x7 4x8",
    "polygon 10x2 11x28 12x2 13x28 14x2 15x28 16x2",
]
n = 0
for polygon in polygons:
    for colour, extra in [("200.60.10.140", ""), ("30.200.90", "alpha 0.6 "), ("10.20.250.255", "blend multiply ")]:
        n += 1
        add("noise 40x30 %d" % (60 + n), "fill %s %s%s" % (colour, extra, polygon))

shape_ops = ["source-over", "source-copy", "destination-in", "xor", "clear", "plus", "multiply", "screen",
             "overlay", "color-dodge", "color-burn", "soft-light", "difference"]
for op in shape_ops:
    add("noise 40x30 101", "fill 200.100.50.180 alpha 0.75 blend %s polygon 3.3x2.1 35.7x6.4 20.2x27.9" % op)
    add("noise 40x30 102", "fill 30.200.90 blend %s polygon 3x3 37x3 20x15 37x27 3x27 20x15" % op)

for angle in ["17", "-30", "45", "90", "180", "0.5", "359.9", "123.456"]:
    add("noise 40x30 120", "fill 250.20.20.200 translate 20x15 rotate %s box -12x-7 24x14" % angle)
    add("noise 40x30 121", "fill 20.250.20 rotate %s 20x15 box 8x4 24x22" % angle)
add("noise 40x30 122", "fill 250.20.20 rotate 33 clear 10x2 25x20")
add("noise 40x30 123", "fill 250.20.20 alpha 0.4 rotate 10 box -5x-5 60x60")
add("noise 40x30 124", "fill 250.20.20 scale 1.5x0.5 rotate 25 box 4x4 20x30")
add("noise 40x30 125", "fill 250.20.20 rotate 15 clip 5x5 35x25 box 0x0 40x30")
add("noise 40x30 126", "fill 250.20.20 clip 5.5x5.25 34.75x24.5 polygon -5x-5 45x10 20x40")
add("noise 40x30 127", "fill 250.20.20 clip 12x8 28x22 polygon 3.3x2.1 35.7x6.4 20.2x27.9")
add("noise 40x30 128", "fill 250.20.20 translate 3.5x-2.25 scale 0.75 polygon 3.3x2.1 35.7x6.4 20.2x27.9")
add("noise 40x30 129", "fill 250.20.20 scale -1x1 translate -40x0 polygon 3.3x2.1 35.7x6.4 20.2x27.9")
add("noise 40x30 130", "fill 250.20.20 scale 0 0 polygon 3.3x2.1 35.7x6.4 20.2x27.9")
add("noise 40x30 131", "fill 250.20.20 rotate 90 translate 0x-40 polygon 3.3x2.1 35.7x6.4 20.2x27.9")
add("noise 40x30 132", "fill 250.20.20 triangle 3.3x2.1 35.7x6.4 20.2x27.9")
add("noise 40x30 133", "fill 250.20.20 alpha 0.5 triangle 1x1 39x1 20x29 2x28 38x28 20x2")
add("noise 40x30 134", "fill 250.20.20 triangle 1x1 39x1 20x29 2x28")
add("noise 40x30 135", "triangle 1x1 39x1 20x29 fill 0.0.255 box 1x1 3x3")
add("noise 300x200 136", "fill 90.10.250.200 polygon 0x0 300x10 5x199 290x190 150x-20")
add("noise 300x200 137", "fill 90.10.250 blend multiply polygon 10x10 290x30 150x195")
add("noise 300x200 138", "fill 90.10.250 rotate 7 box 20x15 260x170")
add("noise 1100x70 139", "fill 90.10.250 polygon 5x5 1095x30 550x69")
add("noise 9x150 140", "fill 90.10.250 polygon 1x1 8x149 1x149")

curves = [
    "circle 20x15 11.3",
    "circle 20x15 0.4",
    "circle 20x15 1",
    "circle 20.5x15.5 7.75",
    "circle 0x0 12",
    "circle 40x30 18",
    "circle 20x15 40",
    "circle -30x15 8",
    "circle 20x15 -6",
    "circle 20x15 0",
    "circle 20x15 14 6",
    "circle 20x15 3 13.5",
    "circle 20x15 10 -4",
    "ellipse 2x3 36x24",
    "ellipse 2.25x3.75 35.5x22.125",
    "ellipse -10x-10 30x20",
    "ellipse 30x20 30x20",
    "ellipse 5x5 0x10",
    "box 3x3 34x24 6",
    "box 3.5x2.25 33x25.5 6 3",
    "box 3x3 34x24 30",
    "box 3x3 34x24 0.5",
    "box 3x3 34x24 0",
    "box -5x-5 50x40 12",
    "box 3x3 34x24 -5 4",
    "arc 20x15 12x9 0 90",
    "arc 20x15 12x9 30 120 pie",
    "arc 20x15 12x9 30 120 chord",
    "arc 20x15 12x9 -45 270 pie",
    "arc 20x15 12x9 0 360",
    "arc 20x15 12x9 10 359.99 pie",
    "arc 20x15 12x9 90 -135 pie",
    "arc 20x15 12x12 0 180 chord",
    "arc 20x15 12x9 0 1 pie",
    "arc 20x15 12x9 200 45",
    "arc 20x15 12x9 0 720 pie",
    "arc 0x0 30x25 0 90 pie",
    "cubic 2x28 10x-10 30x40 38x2",
    "cubic 2x2 38x2 2x28 38x28",
    "cubic 5x25 15x0 25x30 35x5 38x28 10x29 5x25",
    "cubic 20x2 50x15 -10x15 20x28",
    "cubic 2x15 15x2 15x28 2x15",
    "cubic 20x2 20x2 20x28 20x28",
    "cubic -20x-20 60x10 -20x40 60x50",
    "cubic 2x2 38x28",
]
n = 0
for curve in curves:
    for colour, extra in [("200.60.10.140", ""), ("30.200.90", "alpha 0.6 "), ("10.20.250.255", "blend multiply ")]:
        n += 1
        add("noise 40x30 %d" % (200 + n), "fill %s %s%s" % (colour, extra, curve))
for op in shape_ops:
    add("noise 40x30 301", "fill 200.100.50.180 alpha 0.75 blend %s circle 20x15 11.3" % op)
    add("noise 40x30 302", "fill 30.200.90 blend %s arc 20x15 14x11 20 250 pie" % op)
for angle in ["17", "45", "90", "123.456"]:
    add("noise 40x30 310", "fill 250.20.20.200 rotate %s 20x15 ellipse 6x8 28x14" % angle)
    add("noise 40x30 311", "fill 20.250.20 rotate %s 20x15 box 6x6 28x18 5 3" % angle)
add("noise 40x30 312", "fill 250.20.20 scale 2x0.5 circle 10x30 9")
add("noise 40x30 313", "fill 250.20.20 clip 8.5x5.25 31.75x24.5 circle 20x15 13")
add("noise 40x30 314", "fill 250.20.20 clip 20x0 40x30 arc 20x15 15x12 0 360 pie")
add("noise 40x30 315", "fill 250.20.20 translate 0.3x0.7 circle 20x15 9.9")
add("noise 300x200 316", "fill 90.10.250.200 circle 150x100 97")
add("noise 300x200 317", "fill 90.10.250 blend screen ellipse 5x5 290x190")
add("noise 300x200 318", "fill 90.10.250 box 10x10 280x180 60")
add("noise 9x150 319", "fill 90.10.250 ellipse 1x1 7x148")

strokes = [
    "line 2x2 38x28",
    "line 2.5x15.5 37.5x15.5",
    "line 20x2 20x28",
    "line 2x28 20x2 38x28",
    "line 2x15 38x15 2x16",
    "line 2x2 38x2 38x28 2x28 2x2",
    "line 5x5 35x5 35x25 5x25 5x5.0000001",
    "line 2x15 20x15 38x15",
    "line 2x15 20x15.00000000001 38x15",
    "line 10x10 10x10 30x20",
    "line 3x27 20x3 37x27 3x27",
    "line [5x5 35x8 10x25 30x28]",
    "line 0x0 40x30 0x30 40x0",
    "line -10x15 50x15",
    "line 20x-10 20x40",
    "line 5x15 6x15.5 7x15 8x15.5",
    "polygon 3.3x2.1 35.7x6.4 20.2x27.9",
    "polygon 20x1 26x28 4x10 36x10 14x28",
    "triangle 3x27 20x3 37x27",
    "box 5x5 30x20",
    "box 5.5x5.5 29x19",
    "box 5x5 30x20 6",
    "circle 20x15 10",
    "circle 20x15 10 5",
    "ellipse 4x4 32x22",
    "arc 20x15 12x9 30 120",
    "arc 20x15 12x9 30 120 pie",
    "arc 20x15 12x9 30 250 chord",
    "cubic 2x28 10x-10 30x40 38x2",
    "cubic 2x15 15x2 15x28 2x15",
    "cubic 5x25 15x0 25x30 35x5",
    "cubic 2x2 38x28 2x28 38x2",
    "cubic 20x2 20x2 20x28 20x28",
    "cubic 5x15 20x15 25x15 35x15",
    "cubic 5x15 30x15 10x15 35x15",
]
widths = ["", "line-width 0.5 ", "line-width 4 ", "line-width 9.5 "]
joins = ["", "line-join miter ", "line-join miter bevel ", "line-join miter round ", "line-join bevel ",
         "line-join round "]
caps = ["", "line-cap 1 ", "line-cap 2 ", "line-cap 3 ", "line-cap 4 ", "line-cap 5 ", "line-cap 2 4 ",
        "line-cap 5 1 "]
n = 0
for stroke in strokes:
    for width in widths:
        n += 1
        join = joins[n % len(joins)]
        cap = caps[n % len(caps)]
        add("noise 40x30 %d" % (400 + n % 50), "pen 200.30.40.220 %s%s%s%s" % (width, join, cap, stroke))
for join in joins:
    for angle_line in ["line 4x26 20x4 36x26", "line 4x10 36x15 4x20", "line 4x4 36x15 4x26 36x26",
                       "line 4x15 36x15 4x15.5"]:
        add("noise 40x30 460", "pen 30.40.200 line-width 6 %s%s" % (join, angle_line))
for cap in caps:
    add("noise 40x30 461", "pen 30.200.40 line-width 8 %sline 8x15 32x15" % cap)
    add("noise 40x30 462", "pen 30.200.40 line-width 8 %sline 8x8 32x22 14x24" % cap)
for op in shape_ops:
    add("noise 40x30 470", "pen 200.100.50.180 line-width 3 alpha 0.75 blend %s line 3x27 20x3 37x27" % op)
for degrees in range(20, 41):
    turn = math.radians(degrees)
    tip_x, tip_y = 30.0, 15.0
    end_x = tip_x - 24.0 * math.cos(turn)
    end_y = tip_y + 24.0 * math.sin(turn)
    for join in ["line-join miter ", "line-join miter bevel ", "line-join miter round "]:
        add("noise 40x30 465", "pen 30.40.200 line-width 3 %sline 6x15 %.4fx%.4f %.4fx%.4f"
            % (join, tip_x, tip_y, end_x, end_y))
add("noise 40x30 471", "fill 30.200.90 pen 0.0.0 line-width 2 circle 20x15 11")
add("noise 40x30 472", "fill 30.200.90 pen 200.0.0 line-width 3 polygon 3x3 37x3 20x27")
add("noise 40x30 473", "pen 200.0.0 line-width 3 rotate 30 20x15 box 8x8 24x14")
add("noise 40x30 474", "pen 200.0.0 line-width 2 scale 2x0.5 circle 10x30 8")
add("noise 40x30 475", "pen 200.0.0 line-width 5 clip 6.5x5.25 33.75x24.5 polygon 2x2 38x5 20x28")
add("noise 40x30 476", "pen 200.0.0 line-width 4 translate 0.3x0.7 line-join round line 3x27 20x3 37x27")
add("noise 40x30 477", "pen off line-width 3 line 2x2 38x28 pen 0.0.200 line 2x28 38x2")
add("noise 40x30 478", "pen 0.0.200 line-width 0 line 2x2 38x28 line-width 2 line 2x28 38x2")
add("noise 40x30 479", "pen 0.0.200 line-width 3 line-cap 9 line 2x2 38x28")
add("noise 40x30 480", "pen 0.0.200 line-width 3 line-cap 2 9 line 2x2 38x28")
add("noise 300x200 481", "pen 90.10.250.200 line-width 12 line-join round line-cap 2 line 10x190 150x10 290x190 20x100")
add("noise 300x200 482", "pen 90.10.250 line-width 7 circle 150x100 90")
add("noise 9x150 483", "pen 90.10.250 line-width 3 line 4x2 4x148")

shapes = [
    "[move 3x3 line 37x3 37x27 3x27 close]",
    "[move 3x3 line 37x3 line 20x27 close move 10x10 line 30x10 20x20 close]",
    "[move 5x15 curve 10x-5 30x35 35x15]",
    "[move 5x15 curve 10x0 20x0 20x15 curv 30x30 35x15]",
    "[move 5x15 curv 20x0 35x15]",
    "[move 5x25 qcurve 20x-5 35x25]",
    "[move 3x25 qcurve 10x5 17x25 qcurv 31x25 37x5]",
    "[move 3x25 qcurv 20x5]",
    "[move 5x5 hline 35 vline 25 hline 5 close]",
    "[hline 20 vline 20 line 30x5]",
    "[move 5x15 arc 35x15 15 10 0]",
    "[move 5x15 arc 35x15 15 10 0 sweep]",
    "[move 5x15 arc 35x15 15 10 0 large]",
    "[move 5x15 arc 35x15 15 10 0 sweep large]",
    "[move 5x15 arc 35x15 18 9 30 sweep]",
    "[move 5x15 arc 35x15 3 3 0]",
    "[move 5x15 arc 35x15 0 10 0]",
    "[move 5x15 arc 5x15 10 10 0]",
    "[move 10x8 arc 30x22 12 8 45 large]",
    "[move 10x8 arc 30x22 12 8 -70 sweep large close]",
    "[move 5x5 line 35x5 bogus 20x25 close]",
    "[move 5x5 line 35x5 fill 20x25 line 20x25 close]",
    "[3x3 move 5x5 line 35x5 20x25 close]",
    "[move 5x5 line 35x5 20x25 close close move 8x8 line 12x8 10x12]",
    "[line 35x5 20x25 close]",
    "[move 5x5 line 35x5 20x25 close hline 30 vline 28]",
    "[move 5x5 curve 35x5 35x25]",
    "[move -10x-10 curve 60x0 -20x40 50x45 close]",
    "[]",
]
n = 0
for shape in shapes:
    for draw in ["fill 200.60.10.140 ", "pen 30.40.200 line-width 2 ", "fill 30.200.90 pen 0.0.0 line-join round "]:
        n += 1
        add("noise 40x30 %d" % (500 + n % 40), "%sshape %s" % (draw, shape))
add("noise 40x30 590", "fill 200.60.10 rotate 25 20x15 shape [move 5x15 arc 35x15 15 10 0 sweep close]")
add("noise 40x30 591", "fill 200.60.10 alpha 0.5 blend multiply shape [move 3x25 qcurve 10x5 17x25 qcurv 31x25 37x5 close]")
add("noise 40x30 592", "pen 200.60.10 line-width 5 line-cap 2 shape [move 3x25 qcurve 10x5 17x25 qcurv 31x25 37x5]")
add("noise 40x30 593", "fill 200.60.10 shape 12")
add("noise 40x30 594", "fill 200.60.10 shape bogus fill-all")

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
