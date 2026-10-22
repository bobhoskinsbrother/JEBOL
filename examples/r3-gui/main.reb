Rebol []

sliders-layout: last load %sliders.r
slider-window: view/no-wait sliders-layout
slider-window/offset: 20x20
survey-layout: last load %survey.r
survey-window: view/no-wait survey-layout
survey-window/offset: 620x20
show system/view/screen-gob
the-slider: none
the-field: none
the-scroller: none
walk-window: func [gob at /local here] [
    foreach sub any [gob/pane []] [
        here: at + sub/offset
        if object? sub/data [
            case [
                'slider = select sub/data 'style [
                    the-slider: sub/data
                    print rejoin ["slider-at " here + to pair! sub/size / 2]
                ]
                all ['field = select sub/data 'style none? the-field] [
                    the-field: sub/data
                    print rejoin ["field-at " here + to pair! sub/size / 2]
                ]
                all ['scroller = select sub/data 'style none? the-scroller] [
                    the-scroller: sub/data
                    print rejoin ["scroller-at " here + to pair! sub/size / 2]
                ]
            ]
        ]
        walk-window sub here
    ]
]
walk-window slider-window slider-window/offset
walk-window survey-window survey-window/offset
print "open"
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
]
