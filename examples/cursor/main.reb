Rebol []

print "Move the pointer into the window. Closing the window ends this."

window: view/no-wait make gob! [size: 640x360 color: 230.230.230]

picture: make image! 32x32
repeat at 32 * 32 [
    picture/:at: either even? to integer! at - 1 / 256 [255.0.0.255] [0.0.255.255]
]

steps: reduce [
    "arrow" 32512  "i-shape" 32513  "wait" 32514  "cross" 32515  "up-arrow" 32516
    "size-nwse" 32642  "size-nesw" 32643  "size-we" 32644  "size-ns" 32645  "size-all" 32646
    "no" 32648  "hand" 32649  "app-start" 32650  "help" 32651
    "hidden: none" none  "hidden: unknown id 99" 99  "picture: red and blue bars" picture
]

is-window-open: does [not empty? system/view/screen-gob]

while [is-window-open] [
    foreach [name chosen] steps [
        if is-window-open [
            window/text: join "Cursor: " name
            show window
            print name
            cursor chosen
            wait [system/ports/event 2.5]
        ]
    ]
]

print "window closed"
