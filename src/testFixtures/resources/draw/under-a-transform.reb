held: make image! [40x40 255.100.40]
shoot: func [size draw-block /local g] [
    g: make gob! [] g/size: size g/draw: draw-block
    to image! g
]
inked-columns: func [shot /local left right at] [
    left: none right: none
    repeat down shot/size/y [
        repeat across shot/size/x [
            if 128 > first pick shot as-pair across down [
                left: either left [min left across] [across]
                right: either right [max right across] [across]
            ]
        ]
    ]
    reduce [left right]
]

parent: make gob! [size: 600x500 color: 0.0.0]
child: make gob! [offset: 10x300 size: 150x100]
child/draw: compose [scale 0.5 0.5 image (held) 40x40 120x120]
append parent child
shot: to image! parent
print ["image under scale in an offset gob:" pick shot 41x331]

shot: shoot 300x200 [scale 0.5 0.5 clip 60x60 240x140 pen off fill-pen 0.255.0 box 0x0 300x200]
print ["clip under scale:" pick shot 101x51 pick shot 126x51 pick shot 51x76]

shot: shoot 150x100 [scale 0.5 0.5 pen off grad-pen conic normal 150x100 0 120 0 1 1 [255.0.0 0.0.255] circle 150x100 85]
print ["conic under scale, both sides drawn:" (pick shot 41x51) <> 255.255.255.255 (pick shot 111x51) <> 255.255.255.255]

vectorial: inked-columns shoot 300x80 [scale 0.5 0.5 pen 0.0.0 text vectorial 0x0 600x160 [40 {MMMM}]]
raster: inked-columns shoot 300x80 [scale 0.5 0.5 pen 0.0.0 text 0x0 600x160 [40 {MMMM}]]
print ["vectorial text is scaled, raster text is not:" vectorial/2 < (raster/2 * 3 / 4)]

moved: inked-columns shoot 300x80 [scale 2 2 pen 0.0.0 text 30x0 200x40 [20 {M}]]
print ["raster text's offset goes through the transform:" moved/1 > 58]
print "done"
