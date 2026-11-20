Rebol [Title: "Two places"]

shown: make gob! [size: 4000x4000 color: 40.160.60]
view/no-wait/options shown [offset: 0x0 title: "Two places"]
handle-events [
    name: 'moves
    priority: 100
    handler: func [event] [
        switch event/type [
            down [
                go 'second
                shown/color: 40.60.200
                show shown
            ]
            back [
                shown/color: 40.160.60
                show shown
            ]
        ]
        event
    ]
]
do-events
