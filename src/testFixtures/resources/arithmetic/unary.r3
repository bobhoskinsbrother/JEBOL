Rebol [
    Title: "The arithmetic natives that take one value"
    Purpose: {
        NEGATE, ABSOLUTE and the questions asked of a single number, against
        every datatype and at the edges. The pairing probe covers none of
        these, and NEGATE was wrong on a pair until the gate caught it.
    }
]

say: func [subject outcome] [
    print rejoin [pad subject 40 "| " outcome]
]

outcome-of: func [text] [
    answer: try [do load text]
    either error? answer [rejoin ["!" answer/id]] [
        rejoin [mold answer "  " mold type? answer]
    ]
]

subjects: [
    {2} {-2} {0} {2.5} {-2.5} {0.0} {-0.0} {200%} {-200%}
    {$2} {-$2} {$0} {#"B"} {0:00:02} {-0:00:02} {1-Jan-2020}
    {2x3} {-2x-3} {2.2.2} {0.0.0} {255.255.255}
    {9223372036854775807} {-9223372036854775808}
    {1e308} {"a"} {none} {[1]} {true}
]

foreach native [negate absolute even? odd? sign? zero? positive? negative?] [
    foreach subject subjects [
        source: rejoin [form native " (" subject ")"]
        say source outcome-of source
    ]
    print ""
]
