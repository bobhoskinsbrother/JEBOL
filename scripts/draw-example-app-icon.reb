REBOL [
    title: "Draw the example app's icon"
    purpose: "Writes examples/an-app-of-ones-own/icon.png, the 1024 pixel icon goal 2e's packager makes every size from"
]

import blend2d

icon: make image! [1024x1024 0.0.0.0]
draw icon [
    pen off
    fill-pen 0.120.215
    box 0x0 1024x1024 180
    fill-pen 255.255.255
    box 232x212 560x600 48
    fill-pen 0.120.215
    box 312x332 400x56 20
    box 312x452 400x56 20
    box 312x572 260x56 20
]
save %../examples/an-app-of-ones-own/icon.png icon
