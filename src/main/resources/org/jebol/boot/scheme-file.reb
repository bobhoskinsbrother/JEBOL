sys/make-scheme [
    title: {File Access}
    name: 'file
    info: system/standard/file-info
    init: func [port /local path] [
        if url? port/spec/ref [
            parse port/spec/ref [thru #":" 0 2 slash path:]
            append port/spec compose [path: (to file! path)]
        ]
    ]
]
