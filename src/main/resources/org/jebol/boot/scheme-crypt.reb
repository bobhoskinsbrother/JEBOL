sys/make-scheme [
    title: {Crypt}
    spec: system/standard/port-spec-crypt
    name: 'crypt
    init: function [port [port!]][
        spec: port/spec
        algorithm: any [
            select spec 'algorithm
            select spec 'target
            select spec 'host
        ]
        direction: any [
            select spec 'fragment
            select spec 'direction
        ]
        if any [
            error? try [spec/algorithm: to word! :algorithm]
            not find system/catalog/ciphers spec/algorithm
        ][
            cause-error 'access 'invalid-spec :algorithm
        ]
        if any [
            error? try [spec/direction: to word! :direction]
            not find [encrypt decrypt] spec/direction
        ][
            cause-error 'access 'invalid-spec :direction
        ]
        set port/spec: copy system/standard/port-spec-crypt spec
        if block? port/spec/ref [
            port/spec/ref: as url! ajoin [
                {crypt://} :algorithm #"#" :direction
            ]
        ]
    ]
]
