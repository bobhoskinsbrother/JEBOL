Rebol [
    Title: "Arithmetic at the boundaries of each datatype"
    Purpose: {
        The every-pair probe uses one value per datatype, so it pins which
        pairings are allowed and says nothing about the numbers. This asks
        what happens at the edges: zero, negative, the widest whole number,
        an octet either side of 255, infinity and a negative zero.
    }
]

say: func [subject outcome] [
    print rejoin [pad subject 44 "| " outcome]
]

outcome-of: func [text] [
    answer: try [do load text]
    either error? answer [rejoin ["!" answer/id]] [
        rejoin [mold answer "  " mold type? answer]
    ]
]

ask-each: func [sources] [
    foreach source sources [say source outcome-of source]
]

print "=== whole numbers at the width of the word ==="
ask-each [
    {9223372036854775807 + 1}   {9223372036854775807 + 0}
    {-9223372036854775808 - 1}  {-9223372036854775808 + 0}
    {9223372036854775807 * 2}   {9223372036854775807 * 1}
    {-9223372036854775808 * -1} {-9223372036854775808 / -1}
    {9223372036854775807 + 9223372036854775807}
    {-9223372036854775808 - 9223372036854775807}
]

print ""
print "=== zero, and dividing by it ==="
ask-each [
    {0 + 0} {0 - 0} {0 * 0} {1 / 0} {0 / 0} {1 % 0} {0 % 1}
    {1.0 / 0} {0.0 / 0.0} {1.0 % 0} {$1 / 0} {0:00:01 / 0}
    {1x1 / 0} {1x1 / 0x1} {1.1.1 / 0} {1.1.1 / 0.0.0}
    {0 ** 0} {0 ** 1} {1 ** 0}
]

print ""
print "=== negatives and the sign of a remainder ==="
ask-each [
    {-7 % 3} {7 % -3} {-7 % -3} {-7 / 3} {-7.0 % 3.0}
    {negate 0} {negate -0.0} {negate 1x-2} {negate -$1} {negate 0:00:00}
    {absolute -7} {absolute -7.0} {absolute -1x-2} {absolute -$1}
    {absolute -0:00:07} {absolute 0} {absolute -9223372036854775808}
]

print ""
print "=== an octet either side of the ends of a tuple ==="
ask-each [
    {255.255.255 + 1} {255.255.255 + 0} {254.254.254 + 1}
    {0.0.0 - 1} {0.0.0 - 0} {1.1.1 - 1}
    {255.255.255 * 2} {128.128.128 * 2} {0.0.0 * 5}
    {1.2.3.4.5.6.7.8.9.10.11.12 + 1}
    {1.2.3 + 1.2.3.4} {1.2.3.4 + 1.2.3}
]

print ""
print "=== decimals that leave the range, and the negative zero ==="
ask-each [
    {1e308 * 10} {-1e308 * 10} {1e-308 / 1e308}
    {-0.0 + 0.0} {0.0 - 0.0} {-0.0 * 1} {1 / -0.0} {-0.0 = 0.0}
    {1.0 + 0.0000000000000001}
]

print ""
print "=== the ends of a character ==="
ask-each [
    {#"^(00)" - 1} {#"^(00)" + 0} {#"^(10FFFF)" + 1} {#"^(10FFFF)" + 0}
    {#"^(D7FF)" + 1} {#"A" - #"A"} {#"A" * 0}
]

print ""
print "=== money keeps its scale and its width ==="
ask-each [
    {$0 + $0} {$1.50 + $0} {$1.50 + $0.50} {-$1 + $1}
    {$1 / 3} {$0.01 * 100} {$1 + 0.1}
]

print ""
print "=== a time either side of a day ==="
ask-each [
    {0:00:00 + 0} {23:59:59 + 0:00:01} {-0:00:01 + 0:00:01}
    {0:00:00 - 0:00:01} {0:00:01 * 0} {0:00:01 / 2} {0:00:00 % 1}
]
