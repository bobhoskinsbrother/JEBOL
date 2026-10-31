Rebol [Title: "Blend2D's blend modes"]

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
columns: 6
cell: tile-size + (as-pair gap gap) + (as-pair 0 caption-height)
rows: to integer! ((length? operators) + columns - 1) / columns

paint-tile: func [operator] [
    b2d/draw tile-size compose [
        fill 30.110.200 box 0x0 120x36
        fill 240.190.40 box 0x36 120x36
        clear 0x0 30x72
        fill 255.255.255.90 box 0x31.5 120x9
        blend (operator)
        alpha 0.85
        fill 220.50.70.190 box 18.5x14.5 70x44
        fill 90.200.120 box 58.25x20.75 44.5x30
    ]
]

window: make gob! [color: 24.28.38]
window/size: (as-pair columns * cell/x rows * cell/y) + (as-pair gap gap)

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
    append window heading
    tile: make gob! []
    tile/offset: corner + (as-pair 0 caption-height)
    tile/size: tile-size
    tile/image: paint-tile operator
    append window tile
    at: at + 1
]

view/options window [title: "Blend2D's blend modes"]
