system/options/modules: attempt [
    all [
        exists? system/options/data
        make-dir/deep join system/options/data %modules/
    ]
]
