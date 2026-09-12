sys/make-scheme [
    title: {Checksum port}
    info: {Possible methods are in `system/catalog/checksums`}
    spec: system/standard/port-spec-checksum
    name: 'checksum
    init: function [port [port!]][
        spec: port/spec
        method: any [
            select spec 'method
            select spec 'target
            select spec 'host
            'md5
        ]
        if any [
            error? try [spec/method: to word! method]
            not find system/catalog/checksums spec/method
        ][
            cause-error 'access 'invalid-spec method
        ]
        set port/spec: copy system/standard/port-spec-checksum spec
    ]
]
