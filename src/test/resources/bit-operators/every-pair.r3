Rebol [
    Title: "What AND, OR and XOR take on each side"
    Purpose: {
        Prints one line per fact about the two-operand bit operators, so a
        JEBOL run and a real 3.22.5 run can be diffed line for line. See the
        README beside this file.
    }
]

say: func [subject outcome] [
    print rejoin [pad subject 44 "| " outcome]
]

outcome-of: func [code] [
    answer: try code
    either error? answer [rejoin ["!" answer/id]] [mold answer]
]

named: func [source] [
    rejoin [either block? source [mold source] [source]]
]

try-source: func [text] [
    outcome-of compose [do load (text)]
]

lefts: [
    {12}
    {true}
    {12x10}
    {12.10.6}
    {#"L"}
    {#{0F10}}
    {charset "ab"}
    {any-string!}
    {integer!}
    {make vector! [integer! 8 [12 10 6]]}
]

rights: [
    {10}
    {false}
    {10x12}
    {10.12.6}
    {#"J"}
    {#{33}}
    {charset "bc"}
    {any-block!}
    {integer!}
    {make vector! [integer! 8 [10 12 6]]}
]

operators: [{and~} {or~} {xor~}]

print "=== every pair of the ten datatypes the declaration accepts ==="
foreach operator operators [
    foreach left lefts [
        foreach right rights [
            say
                rejoin [operator " (" left ") (" right ")"]
                try-source rejoin [operator " (" left ") (" right ")"]
        ]
    ]
]

print ""
print "=== a datatype outside the ten, on the right ==="
outsiders: [
    {1.5} {50%} {$5} {0:00:01} {1-Jan-2020} {"ab"} {%a} {<t>} {#i}
    {[1 2]} {[]} {""} {'a} {quote a:} {none} {make object! []}
    {try [1 / 0]} {()}
]
foreach outsider outsiders [
    say
        rejoin ["and~ 12 (" outsider ")"]
        try-source rejoin ["and~ 12 (" outsider ")"]
]

print ""
print "=== a datatype outside the ten, on the left ==="
foreach outsider outsiders [
    say
        rejoin ["and~ (" outsider ") 10"]
        try-source rejoin ["and~ (" outsider ") 10"]
]

print ""
print "=== outside on both sides, and the gate closing first ==="
say "and~ 1.5 2.5" try-source "and~ 1.5 2.5"
say {and~ "ab" none} try-source {and~ "ab" none}
say "and~ integer! 1.5" try-source "and~ integer! 1.5"
say "and~ 1.5 integer!" try-source "and~ 1.5 integer!"

print ""
print "=== the answer's datatype is the left operand's ==="
pairs-that-work: [
    {12} {10}
    {12} {#"J"}
    {#"L"} {10}
    {#"L"} {#"J"}
    {true} {false}
    {12x10} {10}
    {12x10} {10x12}
    {12.10.6} {10}
    {12.10.6} {10.12.6}
    {#{0F10}} {#{33}}
    {charset "ab"} {#{33}}
    {charset "ab"} {charset "bc"}
    {any-string!} {any-block!}
    {any-string!} {integer!}
    {make vector! [integer! 8 [12 10 6]]} {10}
    {make vector! [integer! 8 [12 10 6]]} {make vector! [integer! 8 [10 12 6]]}
]
foreach operator operators [
    foreach [left right] pairs-that-work [
        say
            rejoin ["type? " operator " (" left ") (" right ")"]
            try-source rejoin ["type? " operator " (" left ") (" right ")"]
    ]
]

print ""
print "=== neither operand is changed ==="
b1: charset "ab"
b2: charset "bc"
and~ b1 b2
say "the bitset on the left, afterwards" mold b1
say "the bitset on the right, afterwards" mold b2

v1: make vector! [integer! 8 [12 10 6]]
v2: make vector! [integer! 8 [10 12 6]]
and~ v1 v2
say "the vector on the left, afterwards" mold v1
say "the vector on the right, afterwards" mold v2

t1: 12.10.6
and~ t1 10
say "the tuple on the left, afterwards" mold t1

y1: #{0F10}
y2: #{33}
and~ y1 y2
say "the binary on the left, afterwards" mold y1
say "the binary on the right, afterwards" mold y2

s1: any-string!
s2: any-block!
and~ s1 s2
say "the typeset on the left, afterwards" mold s1
say "the typeset on the right, afterwards" mold s2

p1: 12x10
and~ p1 10
say "the pair on the left, afterwards" mold p1

print ""
print "=== degenerate operands are ordinary operands ==="
degenerate: [
    {and~ #{} #{0F10}}
    {and~ #{0F10} #{}}
    {and~ #{} #{}}
    {and~ (make bitset! #{}) (charset "ab")}
    {and~ (charset "ab") (make bitset! #{})}
    {and~ (make typeset! []) any-string!}
    {and~ any-string! (make typeset! [])}
    {and~ (make vector! [integer! 8 []]) 10}
    {and~ 0 0}
    {and~ 0x0 0}
    {and~ 0.0.0 0}
    {and~ 0.0.0 0.0.0}
    {and~ -1 -1}
    {or~ -1 0}
    {xor~ -1 -1}
    {and~ 0 12}
    {and~ 12 0}
    {and~ (to char! 0) (to char! 0)}
    {and~ false false}
    {or~ false false}
]
foreach source degenerate [
    say source try-source source
]

print ""
print "=== every spelling reaches the same code ==="
spellings: [
    {and~ 12 10}
    {12 and 10}
    {12 & 10}
    {or~ 12 10}
    {12 or 10}
    {12 | 10}
    {xor~ 12 10}
    {12 xor 10}
    {and~ 12x10 10x12}
    {12x10 and 10x12}
    {12x10 & 10x12}
    {or~ 12x10 10x12}
    {12x10 or 10x12}
    {12x10 | 10x12}
    {xor~ 12x10 10x12}
    {12x10 xor 10x12}
    {and~ 12 10x12}
    {12 and 10x12}
    {12 & 10x12}
]
foreach source spellings [
    say source try-source source
]
