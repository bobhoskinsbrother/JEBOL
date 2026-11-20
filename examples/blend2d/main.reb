Rebol [Title: "Blend2D's blend modes, on a shaded box, a pie, a star, a triangle, a turned box, an image, a leaf and a line"]

b2d: import 'blend2d

operators: [
    source-over source-copy source-in source-out source-atop
    destination-over destination-copy destination-in destination-out destination-atop
    xor clear plus minus modulate multiply screen overlay darken lighten
    color-dodge color-burn linear-burn linear-light pin-light hard-light soft-light
    difference exclusion
]

tile-size: 120x72
caption-height: 16
gap: 10
cell: tile-size + (as-pair gap gap) + (as-pair 0 caption-height)
screen: gui-metric 'screen-size
unmeasured: zero? screen/x
columns: either unmeasured [6] [
    min select [compact 2 medium 3 expanded 6] size-class? none
        max 1 to integer! (screen/x - gap) / cell/x
]
rows: to integer! ((length? operators) + columns - 1) / columns

badge: b2d/draw 12x12 [
    fill radial 255.250.220 0 200.60.40 0.7 40.20.60 1 4x4 5x5 7 fill-all
]

paint-tile: func [operator] [
    b2d/draw tile-size compose [
        fill 30.110.200 box 0x0 120x36
        fill 240.190.40 box 0x36 120x36
        clear 0x0 30x72
        fill 255.255.255.90 box 0x31.5 120x9
        blend (operator)
        alpha 0.85
        fill linear 230.50.70.200 0 120.60.220.200 1 18x14 88x58
        box 18.5x14.5 70x44 9
        fill 255.150.40.230 arc 15x56 11x11 35 290 pie
        fill 90.200.120 polygon 84x4 91.6x26.5 70.5x12.6 97.5x12.6 76.4x26.5
        fill 250.240.90.220 triangle 34x66 52x46 60x68
        fill 160.90.230 rotate 30 96x50 box 82x42 28x16
        reset-matrix
        image badge 100x2 18x18
        fill 255.255.255 point-size 3 point 103x27 109x27 115x27
        fill 120.220.255.210
        shape [move 4x27 qcurve 4x5 26x5 qcurve 26x27 4x27 close]
        fill off
        pen 255.255.255.220 line-width 3 line-cap 2 line-join round
        line 36x8 46x16 56x8 66x16
    ]
]

sheet: make gob! [color: 24.28.38]
sheet/size: (as-pair columns * cell/x rows * cell/y) + (as-pair gap gap)
window: make gob! [color: 24.28.38]
window/size: as-pair sheet/size/x either unmeasured [sheet/size/y] [min sheet/size/y screen/y]
append window sheet

at: 0
foreach operator operators [
    column: remainder at columns
    row: at // columns
    corner: (as-pair column * cell/x row * cell/y) + (as-pair gap gap)
    heading: make gob! []
    heading/offset: corner
    heading/size: as-pair tile-size/x caption-height
    heading/draw: compose/deep [
        pen 220.220.220
        text vectorial 2x0 (as-pair tile-size/x caption-height) [(form operator)]
    ]
    append sheet heading
    tile: make gob! []
    tile/offset: corner + (as-pair 0 caption-height)
    tile/size: tile-size
    tile/image: paint-tile operator
    append sheet tile
    at: at + 1
]

held: none
scroll-sheet: func [by] [
    sheet/offset: as-pair 0 max window/size/y - sheet/size/y min 0 sheet/offset/y + by
    show window
]
handle-events [
    name: 'scrolling-the-tiles
    priority: 50
    handler: func [event] [
        switch event/type [
            down [held: event/offset]
            move [if held [scroll-sheet event/offset/y - held/y  held: event/offset]]
            up [held: none]
            scroll-line [scroll-sheet event/offset/y]
        ]
        event
    ]
]

view/options window [title: "Blend2D's blend modes"]
