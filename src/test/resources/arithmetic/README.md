# What arithmetic takes on each side

`every-pair.r3` asks a running interpreter 810 questions about the arithmetic
operators, and prints the answer and its datatype for each.
`every-pair.recorded` is what Rebol/Bulk 3.22.5 printed for it.
`ArithmeticEveryPairEndToEndTest` runs the same script through JEBOL's command
line and compares the two line for line.

## Why this exists before the port rather than after

The dispatch it covers is about to move from a scanned list of handler classes
onto the values themselves, and the table is intricate enough that no amount of
reading the C would have found the edges. Three of them, all measured rather
than guessed:

- **Addition and multiplication swap their arguments; subtraction and division
  do not.** `2 + 2x2` is a pair and `2 - 2x2` is not-related. The C says so in
  `t-integer.c`: for ADD and MULTIPLY it swaps the two and dispatches again on
  the new left, and for the rest it only widens to decimal, money, time or
  date.
- **A percent survives only when both sides are percents.** `200% + 300%` is a
  percent; `200% + 3.0` and `200% + 3` are both decimals.
- **A character takes a whole number or a decimal and nothing else.**
  `#"B" + 3.0` is a character, `#"B" + 300%` is not-related.

810 of the 812 lines are a pairing; the other two are section headings.

## What it asks

Nine datatypes on each side -- integer, decimal, percent, money, char, time,
date, pair and tuple -- against six operators: ADD, SUBTRACT, MULTIPLY, DIVIDE,
REMAINDER and POWER. Then the four that have symbols are asked again as
`+`, `-`, `*` and `/`, so a difference between the prefix name and the operator
would show.

## Re-recording it

    ./r3-head src/test/resources/arithmetic/every-pair.r3 \
        > src/test/resources/arithmetic/every-pair.recorded

`r3-head` is built from `rebol3-source/` by `scripts/build-r3.sh`, so the
recording and the C are one authority and cannot disagree. Re-record only when
the script changes or the reference does, and say which in the commit.
