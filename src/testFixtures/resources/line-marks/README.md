# Where a line mark goes when its value moves

`generate.sh` runs `probes.r3` through `./r3-head`, built here from
`rebol3-source/`, and writes `probes.txt`: one line a probe, the label, then
what the probe answered molded inside a block, with each line break shown as
`\n`. `LineMarksTravelWithTheirValuesFromTheSourceTest` runs the same file
through JEBOL and compares.

**The mark is a flag in the value's cell** (`VALUE_FLAG_LINE`), and every copy
of a cell copies it. So a value read out of a block at a marked position keeps
its mark through a variable, an argument, a function's result, `pick`,
`second`, a path, `select`, `foreach`, `parse`'s `set` and `copy`, and lands
marked wherever it is put: `append`, `insert`, `compose`, `change`, `poke`, a
set-path, an object's field, `reduce`. `poke` and `change` over a marked
position put the new value's cell there, mark and all, so an unmarked value
takes the mark away.

**A value made afresh has no mark**: the result of arithmetic, `to word!`,
`to lit-word!`, a lit-word evaluated, `make map!`, `unique`'s block. `quote x`
answers the word as it stands in the code, which is unmarked there.

**The block's tail mark** (`[a` then a line break then `]`) is not molded.
