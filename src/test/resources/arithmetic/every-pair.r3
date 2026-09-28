Rebol [
    Title: "What arithmetic takes on each side"
    Purpose: {
        Prints one line per fact about the arithmetic operators, so a JEBOL
        run and a real 3.22.5 run can be diffed line for line. See the README
        beside this file.
    }
]

say: func [subject outcome] [
    print rejoin [pad subject 46 "| " outcome]
]

lefts: [
    {2} {2.0} {200%} {$2} {#"B"} {0:00:02} {1-Jan-2020} {2x2} {2.2.2}
]

rights: [
    {3} {3.0} {300%} {$3} {#"C"} {0:00:03} {2-Jan-2020} {3x3} {3.3.3}
]

operators: [{add} {subtract} {multiply} {divide} {remainder} {power}]

outcome-of: func [text] [
    answer: try [do load text]
    either error? answer [rejoin ["!" answer/id]] [
        rejoin [mold answer "  " mold type? answer]
    ]
]

foreach operator operators [
    foreach left lefts [
        foreach right rights [
            source: rejoin [operator " (" left ") (" right ")"]
            say source outcome-of source
        ]
    ]
]

print ""
print "=== the same pairs written as operators ==="
foreach [operator symbol] [add + subtract - multiply * divide /] [
    foreach left lefts [
        foreach right rights [
            source: rejoin ["(" left ") " symbol " (" right ")"]
            say source outcome-of source
        ]
    ]
]

print ""
print "=== an operand outside the declared types, named and as an operator ==="
outsiders: [{"a"} {none} {[1]} {true} {#a} {%f} {<t>}]
foreach outsider outsiders [
    foreach [operator symbol] [add + subtract - multiply * divide /] [
        left: rejoin [operator " (" outsider ") (3)"]
        say left outcome-of left
        say rejoin ["(" outsider ") " symbol " (3)"]
            outcome-of rejoin ["(" outsider ") " symbol " (3)"]
        right: rejoin [operator " (2) (" outsider ")"]
        say right outcome-of right
        say rejoin ["(2) " symbol " (" outsider ")"]
            outcome-of rejoin ["(2) " symbol " (" outsider ")"]
    ]
]
