sys/make-scheme [
    title: "System Port"
    name: 'system
    awake: func [
        sport "System port (State block holds events)"
        ports "Port list (Copy of block passed to WAIT)"
        /only
        /local event event-list n-event port waked
    ][
        waked: sport/data ; The wake list (pending awakes)

        if only [
            unless block? ports [return none] ;short cut for a pause
        ]

        ; Process all events (even if no awake ports).
        n-event: 0
        event-list: sport/state
        while [not empty? event-list][
            if n-event > 8 [break] ; Do only 8 events at a time (to prevent polling lockout).
            event: first event-list
            port: event/port
            either any [
                none? only
                find ports port
            ][
                remove event-list ;avoid event overflow caused by wake-up recursively calling into wait
                if wake-up port event [
                    ; Add port to wake list:
                    unless find waked port [append waked port]
                ]
                ++ n-event
            ][
                event-list: next event-list
            ]
        ]

        ; No wake ports (just a timer), return now.
        unless block? ports [return none]

        ; Are any of the requested ports awake?
        forall ports [
            if find waked first ports [return true]
        ]

        either zero? n-event [
            none ;events are ignored
        ][
            false ; keep waiting
        ]
    ]
    init: func [port] [
        port/data: copy [] ; The port wake list
    ]
]
