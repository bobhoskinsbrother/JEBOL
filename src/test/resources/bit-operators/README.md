# What AND, OR and XOR take on each side

`every-pair.r3` asks a running interpreter 452 questions about the two-operand
bit operators, and prints one line per answer. `every-pair.recorded` is what
Rebol/Bulk 3.22.5 printed for it. `BitOperatorsEveryPairEndToEndTest` runs the
same script through JEBOL's command line and compares the two line for line.

## Why a script and a recording rather than assertions in Java

The script started as a scratchpad probe. Every reading of the C in
`spec/natives.allium` under "What AND, OR and XOR take on each side" came out
of running it against `./r3-head` and reading the column, and two of those
readings were the opposite of what the C looked like it said. Keeping it means
the evidence and the test are the same artefact: changing what JEBOL answers
changes the diff, and re-reading the reference is one command rather than an
afternoon.

It also covers what a table of Java assertions would not. A crash is a line
that never prints, and the diff says which one; an operand quietly mutated by
the operation shows up as a later line changing. Both have happened here.

## What it asks

- **Every pair of the ten datatypes the declaration accepts**, for all three
  operators: 300 lines. Sixteen of the hundred pairs work; the other
  eighty-four raise, and which of five failures they raise depends on the
  left operand, not on what is wrong.
- **A datatype outside the ten**, on the right, on the left, and on both: all
  `expect-arg`, from the argument check. Eighteen outsiders, including the
  absent value, an empty block, an empty string and an unset.
- **The answer's datatype is the left operand's**, for all sixteen working
  pairs and all three operators.
- **Neither operand is changed** by the operation, for the five datatypes that
  carry their contents in a shareable series.
- **Degenerate operands**: an empty binary, bitset, typeset and vector; zero;
  minus one; the null character.
- **Every spelling**: `and~` and `and` and `&`, `or~` and `or` and `|`,
  `xor~` and `xor`.

## Re-recording it

    ./r3-head src/test/resources/bit-operators/every-pair.r3 \
        > src/test/resources/bit-operators/every-pair.recorded

`r3-head` is built from `rebol3-source/` by `scripts/build-r3.sh`, so the
recording and the C are one authority and cannot disagree. Re-record only when
the script changes or the reference does, and say which in the commit.
