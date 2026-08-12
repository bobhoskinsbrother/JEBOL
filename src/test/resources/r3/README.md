# Rebol's surface, read from Rebol's source

`c-surface.txt` is what Rebol is, taken from `~/Code/personal/rebol3-source`
by `scripts/c-surface.py`. It is checked in so a change to it shows up in a
diff and so the audit runs without Rebol's tree on disk.

**There is no binary and no record of one.** `./r3` was deleted on purpose, and
so was `surface.txt`, the dump of a running 3.22.1's `system/contexts/lib` that
used to sit beside this file. A running build answers what one build does on one
machine; the source says what the language is, and it explains itself as well.
Every place the two disagreed during the port, the source was right.

The dump was not merely weaker evidence. It was wrong about the thing it was
being asked, and quietly: it listed every top-level word of every file that
build had loaded, and most of those files are modules whose words no script can
reach. Forty functions in `prot-tls.reb`, forty more in `codec-swf.reb`, none of
them callable. Measured against it the porting backlog read 134 of 580; measured
against the source it is 30 of 353. Four years of work pointed at functions
nobody can call.

## What each kind of line says

- `TYPESET` -- each typeset and the datatypes in it, from the last column of
  `boot/types.reb`.
- `DATATYPE` -- each datatype's typeclass, whether it has a path handler and
  whether MAKE can build one, from the same table.
- `ARMS` -- which actions each typeclass implements, read from the
  `case A_XXX:` labels inside every `REBTYPE(Name)` block in `core/t-*.c`, with
  the refusal-only arms left out.
- `ACTION` and `NATIVE` -- the declared specs from `boot/actions.reb` and
  `boot/natives.reb`, with every argument's datatypes and every refinement. 60
  and 164.
- `LIBRARY` -- every function Rebol's own REBOL files publish, and the file that
  defines it. Which is not every function they define: a file bound into the
  base, sys or lib context publishes what it defines at the top level, and
  everything else is a module that publishes what its `exports:` block names.
  `sys-start.reb` is where that split is decided -- `do bind-lib boot-mezz` for
  the one, `foreach [spec body] boot-prot [module spec body]` for the other.

## Who reads it

`ActionParityTest` multiplies the datatype table by the arms table and calls
every pair, which is how a missing arm is found. `PortingBacklogTest` compares
the ACTION, NATIVE and LIBRARY names against a booted JEBOL, which is how a
missing function is found. `scripts/c-parity.py` compares the declared specs
against JEBOL's registry, which is how a missing refinement or a narrowed
parameter is found.
