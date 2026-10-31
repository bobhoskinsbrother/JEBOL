Rebol [Title: "What DRAW draws"]

held: make image! [40x40 255.100.40]
keyed: make image! [4x4 255.100.40]
keyed/2: 58.140.208
keyed/7: 58.140.208

tiles: compose/deep [
    "box, rounded" [
        fill-pen 58.140.208 pen 255.255.255 line-width 2 box 30x40 270x160 20
    ]
    "circle and ellipse" [
        fill-pen 96.198.128 pen off circle 90x100 60
        fill-pen 244.196.66 ellipse 170x50 110x100
    ]
    "polygon" [
        fill-pen 232.93.74 pen 255.255.255 line-width 2
        polygon 150x20 270x90 230x180 70x180 30x90
    ]
    "triangle, shaded" [
        pen off triangle 40x170 270x170 155x30 232.93.74 96.198.128 58.140.208 1
    ]
    "arc and pie" [
        pen 244.196.66 line-width 4 fill-pen off arc 90x110 60x60 0 270
        fill-pen 58.140.208 pen off arc 210x110 60x60 30 300 closed
    ]
    "curve" [
        pen 244.196.66 line-width 4 fill-pen off curve 30x150 110x30 200x180 270x60
    ]
    "spline" [
        fill-pen 58.140.208 pen 255.255.255 line-width 2
        spline 20 closed 60x140 150x40 250x140 150x180
    ]
    "shape" [
        fill-pen 232.93.74 pen 255.255.255 line-width 2
        shape [move 40x160 line 100x40 curve 160x20 220x180 270x60 line 250x170 close]
    ]
    "line joins and caps" [
        fill-pen off pen 255.255.255 line-width 14
        line-join miter line 30x150 70x50 110x150
        line-join round line 120x150 160x50 200x150
        line-cap round line 230x60 230x150
    ]
    "dashes and arrows" [
        pen 232.93.74 line-width 6 fill-pen off
        line-pattern 96.198.128 16 10 line 30x60 270x60
        pen 244.196.66 line-width 4 fill-pen 244.196.66
        arrow 244.196.66 1x1 line 40x170 260x100
    ]
    "linear gradient" [
        pen off grad-pen linear normal 40x40 0 220 35 1 1
        [232.93.74 244.196.66 96.198.128] box 30x40 270x160
    ]
    "radial gradient" [
        pen off grad-pen radial normal 150x100 0 90 0 1 1
        [244.196.66 58.140.208] circle 150x100 85
    ]
    "conic gradient" [
        pen off grad-pen conic normal 150x100 0 120 0 1 1
        [232.93.74 244.196.66 96.198.128 58.140.208] circle 150x100 85
    ]
    "diamond gradient" [
        pen off grad-pen diamond normal 150x100 0 110 0 1 1
        [244.196.66 58.140.208] box 30x20 270x180
    ]
    "transparency" [
        pen off fill-pen 232.93.74.200 circle 120x90 65
        fill-pen 58.140.208.140 circle 180x90 65
        fill-pen 96.198.128.100 circle 150x140 65
    ]
    "transform" [
        transform 30 150x100 1 1 0x0
        fill-pen 244.196.66 pen 255.255.255 line-width 2 box 60x50 240x150
    ]
    "push" [
        fill-pen 96.198.128 pen off
        push [rotate 15 box 60x40 160x100]
        box 160x110 260x170
    ]
    "clip" [
        clip 60x60 240x140 fill-pen 96.198.128 pen off circle 150x100 90
    ]
    "image, scaled and turned" [
        image (held) 30x40 130x160
        rotate 20 image (held) 170x30 260x140
    ]
    "image, keyed" [
        image (keyed) 255.100.40 30x40 270x160
    ]
    "gamma" [
        gamma 2.2 fill-pen 120.60.30 pen off box 30x30 270x170
    ]
    "text" [
        pen 244.196.66 text vectorial 30x70 260x60
        [20 bold {Bold} /bold 96.198.128 { then green}]
    ]
]

columns: 6
tile-size: 150x100
drawn-at: 0.5
gap: 10
caption-height: 18
cell: tile-size + (as-pair gap gap) + (as-pair 0 caption-height)
count: to integer! (length? tiles) / 2
rows: to integer! (count + columns - 1) / columns

window: make gob! [color: 24.28.38]
window/size: (as-pair columns * cell/x rows * cell/y) + (as-pair gap gap)

at: 0
foreach [caption drawing] tiles [
    column: remainder at columns
    row: at // columns
    corner: (as-pair column * cell/x row * cell/y) + (as-pair gap gap)
    heading: make gob! []
    heading/offset: corner
    heading/size: as-pair tile-size/x caption-height
    heading/draw: compose/deep [
        pen 220.220.220
        text vectorial 4x2 (as-pair (tile-size/x - 8) caption-height) [(caption)]
    ]
    append window heading
    tile: make gob! []
    tile/offset: corner + (as-pair 0 caption-height)
    tile/size: tile-size
    tile/draw: compose [
        pen off fill-pen 40.46.60 box 0x0 (tile-size)
        scale (drawn-at) (drawn-at)
        (drawing)
    ]
    append window tile
    at: at + 1
]

view/options window [title: "What DRAW draws"]
