# What the Allium CLI does and doesn't catch

Notes from getting `spec/` to check clean, so nobody has to rediscover
them. Verified against `allium` on 2026-08-08 with minimal test specs, not
guessed from behaviour on the real files.

## Things that look like spec bugs but aren't

**A type used only inside a `variant` body is reported unused.** The
checker's usage analysis doesn't walk variant bodies. `values.allium`
gets eight of these warnings for `StringDatatype`, `BlockDatatype`,
`WordDatatype`, `ErrorCategory`, `Parameter`, `BlockStorage`,
`StringStorage` and `BinaryStorage`, all of which are referenced by the
`Value` variants. Move the same reference to the base entity and the
warning disappears, which is how it was confirmed.

`scripts/check-spec.sh` prints these. Filter with
`grep -v "definition.unused\|entity.unused"` when you want the real
output.

**`allium model` doesn't export variants at all.** Only `allium parse`
shows them. If you're checking whether a sum type parsed, use `parse`.

## Things that are real, and how to write around them

**Status assignment is only tracked on the entity the trigger binds.**
Writing `other.status = done` where `other` was reached by navigation, or
bound with `let`, leaves the checker reporting the status as never
assigned and the entity as deadlocked. Retrigger the rule on the entity
whose status is changing, usually via a derived condition on it. This is
why `LoadRequest` has `read_succeeded` and `read_failed` rather than the
load rules firing off the transcode's transition.

**Conflict detection ignores `requires`.** Two rules writing different
statuses from the same source state are always reported as conflicting,
even with literally negated guards (`requires: x` against
`requires: not x`). Guards will not fix it.

Two ways out. Enum comparisons on different values are treated as
disjoint, so one rule per `datatype` value is fine. Booleans are not, so
mutually exclusive boolean outcomes must collapse into a single rule with
an `if`/`else if`/`else` chain. That is why `eval.allium` has one rule per
decision point rather than one per outcome — which reads better anyway,
because the branch order states the precedence.

Black box predicates are treated as unknown rather than overlapping, so
they are never reported. Don't take that as a clean bill of health.

**A derived value used in a `requires` gets mistaken for a settable
field.** Declaring `input_datatype: input.datatype` and then writing
`requires: step.input_datatype = word` produces `missing_producer` and
`dead_transition` findings saying nothing establishes it. Navigate
directly in the requires (`step.input.datatype = word`) and keep derived
aliases for `ensures` bodies only.


## Checking a rule against Rebol

**Read the C. Do not run a build.** A binary was kept here for a while and has
been deleted: it answered what one build of one fork did on one machine, and it
was a fork rather than R3-Alpha itself, so every answer was evidence rather
than proof. The C says what the language is and explains itself as well.

Where to look is in TODO.md under "The C is the authority". For a rule in a
spec, the trace is to a line of `~/Code/personal/rebol3-source/src/core/*.c` or
to a declaration in `src/boot/`. A rule that cannot be traced there is a guess
wearing a spec's clothing, and four of ours were -- RECYCLE, STATS, STACK and
the map's key spelling all agreed with themselves and disagreed with Rebol.
