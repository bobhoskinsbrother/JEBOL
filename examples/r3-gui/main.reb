Rebol []

sliders-layout: last load %sliders.r
survey-layout: last load %survey.r

screen: gui-metric 'screen-size
place: func [window at] [
    window/offset: either zero? screen/x [at] [max 0x0 min at screen - window/size]
]
windows: either any [zero? screen/x 'expanded = size-class? none] [
    slider-window: view/no-wait sliders-layout
    place slider-window 20x20
    survey-window: view/no-wait survey-layout
    place survey-window 620x20
    reduce [slider-window survey-window]
] [
    reduce [view/no-wait append copy sliders-layout survey-layout]
]
show system/view/screen-gob

the-slider: none
the-field: none
the-scroller: none
walk-window: func [gob at /local here] [
    foreach sub any [gob/pane []] [
        here: at + sub/offset
        if object? sub/data [
            case [
                all ['slider = select sub/data 'style none? the-slider] [the-slider: sub/data]
                all ['field = select sub/data 'style none? the-field] [the-field: sub/data]
                all ['scroller = select sub/data 'style none? the-scroller] [the-scroller: sub/data]
            ]
        ]
        walk-window sub here
    ]
]
foreach window windows [walk-window window window/offset]

middle-on-screen: func [face] [second map-gob-offset/reverse face/gob face/gob/size / 2]
print-places: does [
    print rejoin ["scroller-at " middle-on-screen the-scroller]
    print rejoin ["slider-at " middle-on-screen the-slider]
    print rejoin ["field-at " middle-on-screen the-field]
]
places: does [reduce [middle-on-screen the-scroller middle-on-screen the-slider middle-on-screen the-field]]

loop 4 [wait [gui-event-port 0]]
print-places
print "open"
was-placed: places
was-slider: get-face the-slider
was-field: copy get-face the-field
was-scroller: get-face the-scroller
print rejoin ["slider " was-slider]
print rejoin ["field " was-field]
print rejoin ["scroller " was-scroller]
forever [
    wait [gui-event-port 0:00:00.2]
    value: get-face the-slider
    if value <> was-slider [was-slider: value  print rejoin ["slider " value]]
    value: copy get-face the-field
    if value <> was-field [was-field: value  print rejoin ["field " value]]
    value: get-face the-scroller
    if value <> was-scroller [was-scroller: value  print rejoin ["scroller " value]]
    value: places
    if value <> was-placed [was-placed: value  print-places]
]
