# Things about REBOL 3 that are not written down

A frozen note, kept while porting REBOL 3 to the JVM.

Each entry is behaviour that is not written down anywhere we could find. Some
are surprising. A few look like inconsistencies rather than decisions, and those
are flagged as questions rather than as complaints - a reimplementation is not
evidence that the original is wrong.

**This is a record, not a method.** Every claim below was obtained by asking a
running Oldes Rebol 3.22.1, and that binary has been deleted on purpose: it
answered what one build of one fork did on one machine, and it was a fork rather
than R3-Alpha itself. The commands are left in place so a claim can be traced,
not so it can be rerun.

**A new finding is read out of the C.** `~/Code/personal/rebol3-source` is the
authority, and `docs/porting-guide.md` says where in it to look. A claim traced to a line of C
explains itself as well as settling the question, which no probe does.

An entry earns its place by being *unobvious*: something a careful reader of
the documentation would get wrong. Behaviour that is merely undocumented but
obvious once seen is not interesting enough to list.

---

## 1. BIND is strict for an object and lenient for a word

Given an object as the target, `bind` raises when the word is not in it:

```rebol
b: 10
o: make object! [a: 1]
e: try [bind (quote b) o]
print either error? e [ajoin ["error " e/id]] [mold e]
; error not-in-context
```

Given a *word* as the target, naming the context that word lives in, the
same unplaceable word comes back unchanged and no error is raised:

```rebol
print mold bind (quote nope-not-here) (quote append)
; nope-not-here
```

Both are BIND, both are being asked to place a word in a context that has no
slot for it, and they answer differently. We could find nothing saying which
is intended.

It matters to a reimplementation because the two answers demand different
call sites: a caller of the first must be ready to catch, a caller of the
second must be ready to check whether the word came back bound.

**Question for the Rebol team:** is the difference deliberate? If so, what
is the rule a caller should hold in their head?

## 2. The boot code depends on BIND naming the holder, not the target

`base-defs.reb` generates the six reflector functions - `spec-of`,
`body-of`, `words-of`, `values-of`, `types-of`, `title-of` - inside a `use`
block. The scope that `use` creates is discarded the moment it ends, and yet
the six functions outlive it. They survive because of this line:

```rebol
word: bind/new word 'reflect
```

which asks where `reflect` lives and hangs the new name *there*, rather than
in the `use` scope the code is running in.

This is not stated anywhere near the code, and it is load-bearing. An
implementation whose contexts nest - ours does, REBOL's do not - will
naturally bind a word to the scope it was bound *through*, and then all six
functions are defined in a scope that is about to vanish. The symptom is
`words-of` silently holding `none`, several files later, with nothing
pointing back at the cause.

Worth a comment in the original, we think. It cost us most of a day.

## 3. SIN and SINE are different functions

Not aliases. `sine` takes degrees, `sin` takes radians, and they differ in
what they accept and return. Comparing `body-of` tells you nothing, because
both are natives.

We nearly wrote them as aliases. That would have been wrong by a factor of
57 for every angle, and no test in Rebol's own suite would have caught it -
the differential harness did.

## 4. A time raises for an unknown path part; a date answers none

```rebol
d: now/date
t: now/time
e: try [d/nonsense]
print ["date ->" either error? e [ajoin ["error " e/id]] [mold e]]
; date -> _

f: try [t/nonsense]
print ["time ->" either error? f [ajoin ["error " f/id]] [mold f]]
; time -> error invalid-path
```

Same shape of expression, same kind of mistake by the caller, two different
answers: the date shrugs and the time raises `invalid-path`. We wrote it as
one rule and it cost four assertions in Rebol's own suite before we
noticed.

## 5. Loose equality ignores case for every string-like type

Not only `string!`. `%A.txt = %a.txt` is true and `<A> = <a>` is true, while
`%A.txt == %a.txt` is false. Confirmed for files and tags together:
`print [%A.txt = %a.txt  <A> = <a>  %A.txt == %a.txt]` gives
`true true false`. Easy to implement as a special case for
`string!` and be quietly wrong for files, tags, URLs and emails.

## 6. ROUND/TO rounds to a multiple, not to a number of places

`round/to 1.234 0.01` is `1.23` and `round/to 17 5` is `15`, both confirmed.
One rule covers both. An implementation that reads `/to` as "decimal places" gets the first
right and the second badly wrong.

## 7. Binding is a snapshot, not a subscription

Binding a block to an object binds the words the object knows *at that
moment*. Extending the object afterwards does not make an already-bound word
see the new slot. Demonstrated by binding `[a b]` to an object holding only
`a`, extending it with `b`, and finding the bound `b` still has no value.

```rebol
o: make object! [a: 1]
blk: bind [a b] o
append o [b: 99]
print mold try [get second blk]
; raises -- the bound B still has no slot, despite O now having one
```

This is the right design and we kept it. It is worth stating explicitly,
because "bind" reads like a live connection and is not one.

## 8. Five names for a remainder, grouped the wrong way round

`%`, `mod` and `remainder` are truncated. `%%` and `modulo` are Euclidean
and never negative. With -7 and 3:

```rebol
print ["%        " mold reduce [-7 % 3    7 % -3    -7 % -3    7 % 3]]
print ["%%       " mold reduce [-7 %% 3   7 %% -3   -7 %% -3   7 %% 3]]
print ["mod      " mold reduce [mod -7 3  mod 7 -3  mod -7 -3  mod 7 3]]
print ["modulo   " mold reduce [modulo -7 3 modulo 7 -3 modulo -7 -3 modulo 7 3]]
print ["remainder" mold reduce [remainder -7 3 remainder 7 -3 remainder -7 -3 remainder 7 3]]
; %         [-1 1 -1 1]
; %%        [2 1 2 1]
; mod       [-1 1 -1 1]
; modulo    [2 1 2 1]
; remainder [-1 1 -1 1]
```

So the operator called modulo in most other languages is `remainder` here,
and `mod` - the first three letters of `modulo` - is the other one. Only
`%%` and `modulo` go together.

A second axis crosses the first. `mod` and `modulo` keep the dividend's
datatype, so `mod 7 2.5` is the integer 2, while `remainder 7 2.5` is 2.0.

Every one of the five agrees on every pair of positive operands, which is
most code and nearly every test, so getting it wrong is silent.

## 9. The two equalities disagree about NaN, and the wrong way round

```rebol
print [mold 1.#NaN =  1.#NaN   mold 1.#NaN == 1.#NaN   mold same? 1.#NaN 1.#NaN]
; #(true) #(false) #(true)
```

Everywhere else the loose `=` is the forgiving comparison and `==` the
strict one. Here loose means "the same value" and strict means "whatever
IEEE 754 says". `same?` sides with loose.

Ordering is stranger still: a comparison against NaN is true rather than
false, on either side.

```rebol
print [mold 1.#NaN < 1   mold 1 < 1.#NaN   mold 1.#NaN > 1]
; #(true) #(true) #(false)
```

So the ordering answers "less than" whenever it cannot order. A JVM's own
compare sorts NaN above everything instead, which is a silent divergence
for any implementation that reaches for it.

## 10. Dividing by zero raises only for whole numbers

```rebol
print [mold try [1 / 0]]      ; error zero-divide
print [mold 1.0 / 0]          ; 1.#INF
print [mold 1 / 0.0]          ; 1.#INF
print [mold 0.0 / 0.0]        ; 1.#NaN
```

The decimal side follows the hardware and the integer side does not, so
which a caller gets depends on how the operands happen to be written.

Pairs, money and time all still raise, whichever way the zero is written,
so the exception belongs to plain decimals alone rather than to division.

## 11. PICK shrugs where POKE refuses

Given the same out-of-range index, reading has an answer and writing does
not.

```rebol
print [mold pick [1 2 3] 4]              ; _
print [mold try [poke b: [1 2 3] 4 9]]   ; error out-of-range
```

`at` is a third answer again: it clamps, so `at [1 2 3] 4` is the empty
tail. Three natives, the same bad number, three behaviours. Each is right
for what it is being asked and none of it is guessable.

## 12. SELECT's record width only says where to look

The value SELECT answers is always the one straight after the match,
whatever `/skip` is set to. The width decides only which positions are
candidates.

```rebol
print [mold select/skip [1 2 3 4 5 6] 4 3]   ; 5, not 6
print [mold select/skip [1 2 3 4 5 6] 5 2]   ; 6
print [mold select [1 2 3 4 5 6] 2]          ; 3
```

Reading it as "the last field of the matched record" agrees at a width of
two and disagrees at every other width. Two is the only width most code
uses.

`find/skip` refuses a width below one, but `find/reverse/skip` accepts a
negative one and answers none for zero. A search already heading for the
head is not contradicted by a negative width.

## 13. ++ answers the old value, and works on positions

```rebol
a: 1
print mold reduce [++ a a]     ; [1 2]

s: [1 2 3]
print mold reduce [++ s s]     ; [[1 2 3] [2 3]]
```

It answers what the word held *before* the change. On a series it steps
the position rather than the contents, so one operation covers counting
and walking - which follows from a position being an ordinary value.

## 14. TO BINARY! of an integer is eight bytes

```rebol
print [mold to binary! 65]        ; #{0000000000000041}
print [mold to binary! [1 2 3]]   ; #{010203}
print [mold to binary! "ab"]      ; #{6162}
```

The whole machine width, not the fewest bytes that hold the number. The
block form gives one byte per number, so the two agree for anything under
256 written as a block.

`to file!` inserts nothing between a block's parts: `to file! [a b]` is
`%ab`, not `%a/b`.

## 15. An unnamed CATCH is not a catch-all

```rebol
print [mold catch [throw/name 5 'foo]]              ; does not catch
print [mold catch/name [throw/name 5 'foo] 'bar]    ; does not catch
print [mold catch/all [throw/name 5 'foo]]          ; 5
```

Strict in both directions, which is what makes naming worth anything: a
throw addressed to an outer handler travels past an inner one that was not
expecting it.

`try/all` is the counterpart on the error side - it widens TRY to catch
throws, breaks and returns as well, turning each into an error value whose
type is `Throw`. That category is the one not numbered in hundreds:
`try/all [throw 5]` has code 2.

## 16. A plain BREAK leaves the loop answering unset

Not none. `break/return none` is a thing a script can write, and the two
must stay distinguishable.

```rebol
print [mold repeat i 3 [break]]              ; #(unset)
print [mold repeat i 3 [break/return none]]  ; _
```

## 17. An angle bracket ends a number but poisons a word

Three rules working at once, and dropping any one of them breaks a case
the other two get right. A word may not contain `<` or `>`. A run made
only of symbols is a word whatever it holds, which is how `<`, `<=` and
`-->` are all legal names. And a number followed by a symbol run splits
into two values.

```rebol
probe load "1<"              ; [1 <]
probe load "19-Jan-2010<"    ; [19-Jan-2010 <]
probe try [load "a<"]        ; invalid, not the word a and the word <
probe try [load "1<2"]       ; invalid, not [1 < 2]
```

So `<` does not simply begin a tag, or `a<` would read. It is not simply
absorbed into the word either, or `1<` would not split. The number case
splits and the letter case refuses, and the only way to get both is to
classify the lexeme first and then decide.

A real R3 also refuses `%`, `#`, `$`, `\` and a comma inside a word.
JEBOL refuses only the angle brackets so far, because a hash is how a
based number and a based binary are written: `2#01` and `64#{...}`.
Refusing one on the raw text turns `64#{` into the integer 64 and breaks
a source file that reads perfectly well, so that rule has to run after
those forms are recognised. Recorded as an open question in
`spec/load.allium` rather than half-done.

## 18. PROTECT of a path is not PROTECT of the words in it

A path is a block whose items are words, which makes it very easy to
write a PROTECT that reads `'o/o` as the two names `o` and `o` and
protects whatever each is bound to. That protects the enclosing word,
not the field.

```rebol
o: object [a: 1 o: object [a: 2]]
protect/words/deep 'o/o
probe protected? 'o/a         ; false -- the neighbouring field is free
probe protected? 'o/o         ; true  -- the field the path names
probe protected? 'o/o/a       ; true  -- and its contents, from /deep
o: 5                          ; still fine; the word was never protected
```

The failure this causes is nowhere near its cause. Every later
`o: something` raises locked-word, so the tests after it quietly run
against an object nobody meant to keep. Six assertions in the suite's
UNPROTECT group failed on that one line, twenty lines earlier, and each
of them looked like a bug in UNPROTECT.

A path that names nothing does nothing and raises nothing.
`protect 'o/missing` and `protect 'o/a/deeper`, where `o/a` is a number
and cannot be walked into, both answer the path and change no state.

## 19. PUT on an object is a change to the container, not to the word

PUT goes with APPEND rather than with the assignments, and it asks one
question: is the object open to new names. Whether the word is already
there makes no difference.

```rebol
o: unprotect/words protect/deep object [a: 10]
o/a: 0                          ; fine -- UNPROTECT/WORDS freed the words
put o to-set-word 'a 0          ; refused: protected
```

EXTEND is written in terms of PUT, so a PUT that asks the wrong question
lets EXTEND straight past a protected object.

## 20. The TO-X family is generated, and which datatypes get one is a list

Forty-five of R3's fifty-eight datatypes have a TO-X function and thirteen
do not, each one exactly `to <type>! :value`. The thirteen without are
END, UNSET and NONE, which hold a single value each and so have nothing to
convert to, and the ten the interpreter keeps to itself: NATIVE, ACTION,
REBCODE, OP, FRAME, TASK, HANDLE, STRUCT, LIBRARY and UTYPE.

Eleven further TO-X names exist and are not conversions in this sense at
all -- TO-DEGREES, TO-RADIANS, TO-HEX, TO-IDATE, TO-ITIME, TO-JSON,
TO-LOCAL-FILE, TO-REAL-FILE, TO-REBOL-FILE, TO-RELATIVE-FILE and TO-VALUE
-- so the family is not simply every word beginning "to-".

Some of the conversions are worth knowing on their own:

```rebol
probe to integer! 1-Jan-2000    ; 946684800 -- seconds from 1970
probe to integer! #{01}         ; 1 -- a binary read as one whole number
probe to decimal! #{...}        ; the raw bits of a double, the other way
probe to logic! 0               ; true -- truthiness, not zero-ness
probe to tuple! []              ; 0.0.0 -- shown as three, keeping none
probe to tuple! [1 2]           ; 1.2.0 -- shown as three, keeping two
probe to block! none            ; [_] -- a block holding none
probe to word! ""               ; error: invalid-chars
```

Two of those were written down wrongly here for a while, and both were
corrected by reading the C rather than by probing again.

A tuple keeps a length of its own and shows a minimum of three, so
`to tuple! []` and `to tuple! [1 2]` are different values that print the
same as `0.0.0` and `1.2.0` would if written out. The difference shows
only under `==`, which asks about the kept length, and under REVERSE,
which turns round the kept octets and leaves the shown zeros alone.

`to word! ""` raises **invalid-chars** and not too-short. `Scan_Word` runs
the scanner over the text and refuses it unless the whole of it comes back
as one word; an empty string does not, so it fails the same way `"a b"`
does rather than having a shortness of its own. The earlier note recorded
JEBOL's own answer as though it were R3's.

## 21. A date is stored in UTC, and the zone is only kept for writing it back

`20-Sep-2021/12:00+2:00` prints as it was written and its time and zone read
back as they were written. `/utc` is the one that gives the game away:

```rebol
d: 20-Sep-2021/12:00+2:00
probe reduce [d/time d/zone d/utc]
; [12:00 2:00 20-Sep-2021/10:00]
```

The stored time is the 10:00, not the 12:00. `Emit_Date` calls
`Adjust_Date_Zone(value, FALSE)` before it writes anything out, and the path
accessor calls it the other way round after it reads, so the zone is added on
at both edges and the middle of the interpreter never sees it.

That is what makes two dates with different zones compare as one moment.
`Cmp_Date` compares the stored times as they are, with no mention of a zone
anywhere in it, and gets the right answer because the zone has already been
taken off:

```rebol
probe 20-Sep-2021/12:00+2:00 = 20-Sep-2021/10:00     ; true
probe 20-Sep-2021/12:00+2:00 == 20-Sep-2021/10:00    ; false
```

`==` says false on the same pair, because strict equality reads
`VAL_DATE(a).bits`, and the zone is packed into those bits alongside the year,
month and day. So `=` asks which moment and `==` asks which writing of it, and
a date that never carried a zone is a date whose zone is zero: there is no
third state, which is why `20-Sep-2021/12:00 == 20-Sep-2021/12:00+0:00` is
true.

Subtraction is a third answer again, and does not agree with either. `-`
between two dates is `Diff_Date`, which counts whole days and never looks at
the time at all:

```rebol
probe 1-Jan-2000/23:00 - 1-Jan-2000/1:00   ; 0
probe 1-Jan-2000/23:00 < 2-Jan-2000/1:00   ; true
probe 2-Jan-2000/1:00  - 1-Jan-2000/23:00  ; 1
```

Two hours apart is a difference of zero and a comparison of less-than, and
those are both right: one is counting days and the other is ordering instants.

JEBOL keeps the written time and the zone beside it instead, which is the same
information stored the other way round. Everything above still has to hold, so
`DateValue.moment` takes the zone off at the point of comparison rather than at
the point of reading, and `Comparison.compareForSorting` orders on that.

The binary dialect is where storing it the other way round costs something. The
MS-DOS date and clock are sixteen bits each with no room for an offset, so
`u-bincode.c` reads the year, month, day and time straight out of the struct --
and that struct already holds UTC, so the offset resolves itself with nothing
written to do it:

```rebol
b: binary 8  binary/write b [msdos-datetime 14-Mar-2019/00:33:18+1:00]
probe binary/read b 'MSDOS-DATETIME     ; 13-Mar-2019/23:33:18
```

Half past midnight an hour ahead goes in as half past eleven the evening
before, day included. JEBOL has to ask for that explicitly, which is what
`DateValue.asStoredInUtc` is for, and any other reader of the raw fields has to
do the same. It is also the right answer for the format: a ZIP written in
Berlin and a ZIP written in London at the same moment carry the same two bytes.

Two things about those fields bite either way. The year counts from 1980 in
seven bits, so it wraps at both ends rather than raising -- 1979 is 127 and
2108 is nought -- and the offset alone can reach below the epoch, since half
past midnight on the first day of 1980 an hour ahead is the last evening of
1979. And `MSDOS-DATETIME` given a bare time is accepted by the C and then
reads a year, a month and a day out of a struct holding a time, so what it
writes is whatever those bits happened to be. JEBOL refuses it; there is
nothing there worth copying and REBOL's own suite never asks for it.

## 22. Three units wear the same plus sign beside a date

Adding a number to a date is days. Adding a *decimal* to the same date is a
fraction of a day, so the two spellings of what looks like one number mean
different things:

```rebol
probe 20-Sep-2021/12:00 + 1     ; 21-Sep-2021/12:00 -- one day on, same clock
probe 20-Sep-2021/12:00 + 1.0   ; 21-Sep-2021/12:00 -- the same, by coincidence
probe 20-Sep-2021/12:00 + 1.9   ; 22-Sep-2021/9:36  -- one day and 21.6 hours
```

`1.9` does not round to two days and it does not truncate to one. It is a
duration, and `T_Date` reaches it through its own arm: `secs += (REBI64)(dec *
TIME_IN_DAY)`, where the integer arm two lines above says `day += num`. The
third unit is a time, which is also a duration and carries into the day when it
runs past midnight:

```rebol
probe 20-Sep-2021/23:00 + 2:00  ; 21-Sep-2021/1:00
probe 20-Sep-2021 + 1:00        ; 20-Sep-2021/1:00 -- a bare day gains a clock
```

That last one is `if (secs == NO_TIME) secs = 0` running before the addition,
so a date with no time is treated as its midnight and comes back carrying a
time it did not have.

Every one of these arms ends at `Normalize_Date(day, month, year, tz)`, and the
`tz` is the one read off the original value at the top of the function. So the
zone survives all three, and so does the clock wherever the arm did not
deliberately move it.

Subtraction between two dates is a fourth thing again and is covered in entry
21: it counts whole days and never looks at the clock.

---

## 23. COPY on a bitset carries the bits and drops the "not"

A complemented set means everything its bits do *not* name. Copy one and the
complement is gone, so the copy means the exact opposite of the original:

```rebol
b: complement charset "a"
probe b                  ; #(bitset! not #{...40})
probe copy b             ; #(bitset! #{...40})  -- the "not" is gone
probe find b #"a"        ; false
probe find copy b #"a"   ; true
```

The copy arm in `t-bitset.c` is one line, `VAL_SERIES(value) =
Copy_Series_Value(value)`, and a fresh series carries no flag. Two arms above
it, COMPLEMENT sets `BITS_NOT(ser)` by hand because it has to; the copy arm
never does. `make bitset! <another bitset>` is the same line and loses it the
same way.

This reads as an oversight rather than a decision, and it is what a real
3.22.1 does either way. It matters more than most oversights because the
wrong reading is the one that looks right: carrying the flag over is what
anybody would write, and a script that copies a complemented set then gets
the opposite answer to every question it asks.

REBOL's own library builds about twenty sets this way, all of them bound to a
word once when the file loads and then used as parse rules. Dropping the flag
on copy leaves the whole gate green, so nothing in the library copies one and
depends on the copy still meaning "everything except". That is presumably why
nobody has hit it.

---

## 24. Fifteen codecs are not there until something asks for them

`system/codecs` on a real 3.22.5 Bulk holds twenty-three entries. The build
compiled thirty-eight codec files in, and fifteen of them are not among the
twenty-three:

```rebol
probe true? find codecs 'wav     ; false
import 'wav
probe true? find codecs 'wav     ; true
```

The fifteen carry `Options: [delay]` in their header, and `make-boot.reb` turns
that into `sys/load-module/delay <the molded source>`. The module is registered
and its body is never evaluated, so the `register-codec` inside it has not run
and the catalogue has no entry. Importing it evaluates the body and the entry
appears. bbcode, braille, csv, html-entities, ico, mime-field, mime-types, pdb,
pdf, plist, quoted-printable, srt, swf, wav and xml are the fifteen.

JEBOL evaluates them at boot instead, so its catalogue holds forty-one entries
from the start and `find codecs 'wav` answers true where a real Rebol answers
false. That is a divergence in one word's value rather than in any behaviour
underneath it: import the module in Rebol and the two agree on everything the
codec then does.

It is also why one of Rebol's own tests has been wrong for years without
anybody noticing. `codecs-test.r3` guards its WAV block on `if find codecs
'wav` and, unlike every other delayed codec it tests, never imports the module
first. So the block has not run since version 0.2.0 of the codec changed the
sound data from a raw binary to a vector, and the two CRC-24 checksums it asks
for still describe the old shape. A real 3.22.5 with the module imported
answers 14119576 and 5445824 where the file asks for 3097828 and 4283614 --
which is what JEBOL answers too, so the two lines sit on
`fails-on-rebol-too.txt` and `WavCodecFromTheSourceTest` carries the real
numbers.

---

## 25. Emptying a series past a second name for it molds differently per type

`Mold_Block` resets an out-of-range index before writing it, and the C's own
comment gives the example: `a: [1 2] b: tail a clear a mold b`. Without the
reset the mold would be `#(path! [] 4)` - the fourth of nothing - which LOAD
cannot read back.

The reset is a line in `Mold_Block` and text goes nowhere near it, so the same
emptying done to a string really does mold as `#(string! "" 9)`. Blocks and
paths are tidied; strings are not, and the two datatypes disagree in a real
3.22 exactly as they do in JEBOL.

## 26. A decimal molds to fifteen significant digits, and always keeps its point

Not the shortest form that reads back. `0.1 + 0.2` molds as `0.3`, and `10 / 3`
as `3.33333333333333`. Java's `Double.toString` gives seventeen digits, and the
extra two are precisely the ones that make floating point look broken to whoever
is reading the output.

A pair's halves print to seven - `mold->digits / 2` in `s-mold.c`, where
`mold->digits` is fifteen. That is also about what a single-precision half can
carry, so the two agree by design rather than by accident. A half that is a
whole number drops its point, which is why `1x2` reads back as `1x2` and not as
`1.0x2.0`, and why the halves being decimals at all is invisible until you take
one out.

## 27. An infinite percent molds without its percent sign

`Emit_Decimal` writes the four characters of `#INF` or `#NaN` and then jumps past
the end of the function, so the per-cent sign at the bottom of it is never
reached. An infinite percent molds as `1.#INF`. A hundredth of infinity is still
infinity, and there is nothing for the sign to mean.

## 28. MOLD/PART cuts the value before deciding how to lay it out

`CHECK_MOLD_LIMIT` reduces the count of bytes or pixels to what the limit could
possibly need, and it does so *before* the code that decides whether to break
lines. So `mold/part` of a huge binary with a limit of eight is `#{FFFFFF` and
not `#{` followed by a newline: the limit made it a short binary, and a short
binary stays on one line. Cutting the finished text instead leaves the newline
in, which is the one character a caller asking for eight characters is least
likely to want.

The pixel count is cut against the characters left rather than the pixels left -
a generous cut, since a pixel costs six characters, but the one the C makes. What
is already written comes off first: `mold/part/all img 30` has spent twenty-one
characters on `#(image! 3840x2160 #{` before a pixel is reached, so nine are
left, so nine pixels at most, so fewer than ten and no line break at all.

## 29. MOLD/FLAT is a flag on the mold, not a different way of molding

`MOPT_INDENT` is set on the mold state and read by a binary and an image before
each decides how to lay its digits out. It therefore combines with the other
refinements rather than competing with them: `mold/flat/all` is the construct
form on one line.

It cannot be done by flattening the finished text. The line breaks a binary
writes carry no indent to strip, so replacing every newline with a space turns
`#{AAAA\nBBBB}` into a binary with spaces through the middle of it, which is not
a binary at all.

`MOPT_MOLD_ALL` works the same way - every value below reads it and writes itself
differently for it. A date writes ISO, a typeset writes its construct form, and a
path that has to fall back to a construct sets the flag for its own contents
whether or not the caller asked for it.

## 30. Which literal form a value molds in, and when it gives up and writes a construct

A mold is only correct if LOAD reads it back as the same value, so each of these
types has a rule for when its literal form cannot carry the value and the
construct form is written instead.

**A plain string** uses quotes only when the text holds no quote, fewer than
three newlines, and no more than fifty characters. Otherwise braces, where a
quote and a newline stand for themselves and only an unbalanced brace is
escaped. MOLD/PART decides the form from the part that will be shown, not from
the whole - so a string with a quote at its end is written in braces, but its
first three characters are not.

**A file** escapes the lexer's delimiters, the control range and space, and the
percent and colon, as `%XX`. Without that a space truncates the path and a
control character vanishes. A file with no name at all is written `%""`, because
a bare percent sign is the modulo operator and would read back as a word.

**A ref** keeps only letters and digits. Anything below decimal twenty-one, any
space, any lexer delimiter and a second at-sign send the whole thing to
`#(ref! "...")`. Twenty-one is the C's own number and not a rounding of the
control range - `if (c < 21 || ...)` lets the four characters from twenty-one to
twenty-four through where the word class would not. Nothing spells a ref with
one, and JEBOL writes the same boundary rather than a tidier one that would
disagree.

**A url or an email** is emitted bare only when the lexer would read it back as
the same value, so an empty one, one missing its colon or at-sign, one holding a
delimiter, and the other shapes the scanner refuses all fall back to
`#(url! "...")`.

**A map** is the one thing whose construct form is not the plain form with a
datatype name added: `#[a: 1]` plainly and `#(map! [a: 1])` under MOLD/ALL, so
the brackets change shape as well as gaining a name. FORM of a map skips the
brackets and the indent and puts a bare newline between pairs, with none after
the last - so an empty map forms as nothing at all. Each key and each value is
still *molded* either way, so a text key keeps its quotes where FORM of a string
would have dropped them.

**A struct** plainly writes the identifier Rebol filed the layout under -
`#(struct! 749277710 [a: 0.0])`. That number is a hash of the layout block and
every struct built from the same layout shares it, so a reader that has seen one
can recognise the rest. Under MOLD/ALL the layout itself is written instead,
which is the form that reads back.

**MOLD/ALL prints a decimal to seventeen digits**, which is every one a double
has - `if (GET_MOPT(mold, MOPT_MOLD_ALL)) len = MAX_DIGITS`. So `mold/all 0.1` is
`0.10000000000000001` where `mold 0.1` is `0.1`: the first is the number and the
second is what a person meant by it.

**A pair's half is molded minimally**, which drops the point rather than putting
a zero after it - a decimal keeps its point or it would read back as an integer,
and a pair half has the `x` to say what it is. With seven digits rather than
fifteen, `2147483647x1` molds as `2.147484e9x1`. A negative zero keeps its sign,
because the sign is written from the value rather than from the digits, which is
how `-32767x-32767 % -32767` molds as `-0x-0` while still equalling `0x0`.

## 31. Where a mold breaks its lines, and the four places the numbers disagree

**A block breaks before a flagged value, not after one.** The flag says "this
value begins a line". The indent goes up once, at the first break, and comes
down once, before the closing bracket - so a block laid out over ten lines is
indented by one level, not by ten. The closing bracket goes on its own line
exactly when the indent went up.

The first value is the exception: the line flag is false until one value has
been written, so a break for a flagged first value needs either that flag or a
bracket to write against. A block therefore breaks before its first value and
MOLD/ONLY, which writes no brackets, does not - `mold/only load "[1^/2]"` is
`1^/2` with nothing in front of the one.

A newline before the closing bracket in the source does not put one in the mold:
`mold load "[1 2^/]"` is `[1 2]`, because the scanner drops a line feed with no
value after it.

**A path has nowhere to put a break** - its items are between slashes, not
brackets - so a path carrying one still molds on a single line.

**An image's ten is doing two jobs.** `if (size < 10) indented = FALSE`, with the
C's comment saying why: "use `flat` result for images with less than 10 pixels
(looks better in console)". So the same number sets the width of a line and the
point at which lines start. The break is written before each tenth pixel rather
than after, so the digits start on the line below the opening brace and the
closing one stands alone. The alpha binary appears only when some pixel needs it,
and that is decided by walking the pixels rather than by reading a flag.

**A vector fits ten numbers to a line and only bothers when there are more than
ten**, so a vector of exactly ten stays on its line.

**Each binary base has its own two numbers and they do not agree.** Base sixteen
breaks at thirty-two bytes, base two at eight, base sixty-four at sixty-four -
but base sixty-four's *runs* are forty-eight bytes long. The two come from
different places in `Mold_Binary`, and a binary of between forty-nine and
sixty-four bytes is where they disagree.

Base sixty-four is also the one base whose breaks are written as it goes rather
than counted over the finished digits: `Encode_Base64` breaks on a byte boundary
inside the loop over whole groups, and the two or three leftover bytes are
written after the loop has stopped breaking. A run that ends exactly where the
leftovers begin therefore carries no newline.

**Base two drops its last digit at exactly eight bytes.** `if (len == 8) --p` in
`Encode_Base2` was written to remove the newline a run of eight would have left -
but at exactly eight bytes there is no newline to remove, because the break is
only written above eight. So what it removes is a digit. `mold #{FFAAFFAAFFAAFFAA}`
comes back a bit short on a real 3.22, and JEBOL does it too rather than disagree.

**`system/options/binary-base` is read at the moment of writing**, so which
notation a binary molds in is a property of the interpreter's state and not of
the call. FORM does not read it - `form #{FFAA}` is `FFAA` whatever the option
says - and FORM writes the digits bare and unbroken, because FIND forms its
needle before looking for it and a newline in the middle would stop it matching.

**MOLD/PART decides a string's form from the part that will be shown**, and the
cut is one less than the limit because the opening delimiter is the first
character of the output. A seven-character string ending in a quote still molds
quoted at a limit of seven, and turns to braces at eight.

## 32. A path molds as a construct under two conditions, and IS_WORD is not the typeset

`if (VAL_TAIL <= 1 || !IS_WORD(VAL_BLK_DATA(value)))`.

A path of one item cannot be written with slashes at all, because a slash needs
something either side of it - so `a` on its own is `#(path! [a])` even though it
is the very word a path may start with.

The first item must be a **plain** word, and the strictness is the whole point. A
set-word, a get-word, a lit-word, a refinement and an issue are all any-word! and
none of them may open a path: `a:/b` would read back as a set-path, `/a/b` as a
refinement, `#a/b` as an issue. `IS_WORD` is one datatype, not the typeset, and
reading it as the typeset puts five kinds of path into a form that does not read
back. What comes after the first item may be anything - `a/1` and `a/b/c` both
write plainly.

The two conditions ask about different things. The length is the whole series
(`VAL_TAIL`), while the first item is `VAL_BLK_DATA`, the one at the index. Using
the remaining count for both makes `mold next 'a/b` a construct where it is the
plain `"b"`: a path standing at its second of two is not a path of one.

An empty path writes **nothing at all** - not even the colon a set-path would
carry - from the line above: `if (!MOLD_ALL && VAL_TAIL == VAL_INDEX) return;`.
So `make set-path! 4` molds as the empty string, where writing the colon alone
would read back as something else. Under MOLD/ALL that line does not fire and it
falls to `#(path! [])`, the only writing of it LOAD reads back. Nothing is not a
path.

## 33. Control characters are spelled with a caret and a letter, not with hex

Below a space, a code point molds as a caret and the letter sixty-four above it:
0 is `^@`, 1 is `^A`, 31 is `^_`. That is an escape a reader has to know, not a
decoration - it is how REBOL has always written control characters, and the hex
form is only for what has no letter. From a space to 126 the character stands for
itself, and from 127 up it is `^(7F)` with upper-case hex. Tab and newline have
their own spellings, `^-` and `^/`.

## 34. An object's fields are molded even when the object is formed

`Form_Object` emits `"N: V\n"` for each field and then takes the last newline
off, so there is no `make object!` and no brackets - `form make object! [a: 1 b: 2]`
is the two lines and nothing else. But the value is *molded*, which is the part
that cannot be guessed: `form make object! [a: "x"]` keeps the quotes around the
x. A map forms the same way, molding each key and each value.

Molded, `Mold_Object` writes an indented line before every field and once more at
the end, so an object with no fields at all is still two lines. `self` is left
out - it refers to the object being molded, so writing it would recurse for ever.
A word field is quoted, because the body is read back as a spec and a bare word
there would be evaluated: without the quote an object holding the word NONE molds
as `b: none` and reads back holding the none value, which is a different object.

A port and a module are written the same way but **name themselves** rather than
saying object, because `Mold_Object` writes `VAL_TYPE(value)` and not a fixed
word. Writing `make object!` for all three means a molded port does not read back
as a port.

`Pre_Mold` writes `#(type! ` under MOLD/ALL and `make type! ` without it, and
`End_Mold` closes the bracket only in the first case. That pair is what makes
`mold/all` of an object something LOAD reads back, where the MAKE form needs
evaluating.

**An error molds as its eight fields, not as its summary.** `Mold_Error` hands
straight over to `Mold_Object` when molding rather than forming. The one-line
summary is what FORM gives, and the two are different jobs: one is for reading
back and one is for reading.

**A typeset formed is bare names.** `Mold_Typeset` writes the brackets and the
`#(typeset!` only when molding; formed, it emits each name followed by a space and
trims the last off, so an empty typeset forms as nothing at all.

**A function molds as one pair of brackets holding both blocks** - `func [a][print a]`
molds as `make function! [[a][print a]]` - and FORM gives the same thing, because
there is no shorter way to say what a function is. Both blocks are molded
whichever way the function is written, since `Mold_Block_Series` always writes its
brackets; forming them drops the brackets and leaves `make function! [a print a]`,
which is a spec of three words and no body. A closure names itself, because the
two are separate datatypes to the reader.

**A gob molds as the spec block that would remake it**, and which fields appear is
not "the ones that were set": offset and size always, the alpha only when the gob
is see-through, and the one content field it has.

**An event's word fields are quoted** - `if (IS_WORD(&val)) Append_Byte(mold->series, '\'')` -
so the mold reads back as the event it molded, which is not true of every datatype.

## 35. The arithmetic natives carry four numeric allowances, and they are different numbers

**A tangent is infinite at a right angle, and "at a right angle" has an
allowance.** `if (Eq_Decimal(fabs(dval), pi1 / 2.0))` answers the infinity, and
`Eq_Decimal` allows ten steps of the representation. So
`tangent 89.99999999999987` is `1.#INF` and not the very large finite number the
hardware computes.

**MODULO calls a remainder zero when it makes no difference to its own
operands.** `if (almost_equal(a, a - m, 10) || almost_equal(b, b + m, 10)) m = 0.0;` -
the question is not whether the answer is small but whether it is visible at the
scale of the numbers it came from. So `modulo 562949953421311.25 1` is `0.0` even
though `0.25` is not small in absolute terms. MOD does not ask, which is how the
two are told apart: `mod 562949953421311.25 1` is `0.25`.

That allowance is ten steps, not the twenty-one EQUAL? allows. The C passes the
number by hand here rather than taking `Eq_Decimal`'s default, so the two are
separate numbers that happen to have been the same once.

**SINE and COSINE clamp to zero below one step at 1.0** - `if (fabs(dval) < DBL_EPSILON) dval = 0.0;`
in both natives. That is the smallest difference a double can tell from nothing,
so anything below it is nothing.

**The angle conversion uses the C's own pi, not the platform's.** `Trig_Value`
converts by hand rather than calling the library, and the range reduction it does
first has no effect on the answer for an ordinary angle. What matters is the
constant: `pi1`.

## 36. A bitset literal in sysobj.reb is a hundred and twenty-eight bits, and the bound is the point

Every unescaped set in the catalogue is written as sixteen bytes, and that bound
is what makes percent encoding do its job: a byte the set cannot hold is a byte
that gets escaped. Building the quoted-printable set to 255 instead sends every
accented letter into the output as a raw byte - exactly what the encoding exists
to prevent - and also refuses a colon and a full stop, which Rebol allows.

## 37. SHIFT is two different operations and the plain one spells out four cases

Without /LOGICAL it keeps the sign and loses no bit off the top. Shifting left, a
count of sixty-four or more raises unless the value is already zero; below
sixty-four it raises when the magnitude would not fit. Shifting right, a count of
sixty-four or more repeats the sign bit - so -1 for a negative value and 0 for
anything else, not zero for both; below sixty-four it is an ordinary signed shift.

The one exception in the overflow check is the most negative whole number, which
is reachable: it is the only value with no positive counterpart, so it is the only
value the exception can be about. ABS has the same edge - the most negative whole
number overflows rather than wrapping to itself - and leaves a negative zero
alone, because the sign of a zero is not part of its magnitude.

/LOGICAL answers everywhere the plain form raises, which is why the two
refinements of one native need separate code. A count of sixty-four or more
answers zero from either end, because the bits have all gone.

## 38. RANDOM is eight different functions wearing one name, and three of them are biased

**Every datatype that accepts `/seed` decides for itself what sixty-four bits to
hand `Set_Random`, and no two agree.** A number seeds with itself. A decimal seeds
with its IEEE bit pattern rather than its value, so `random/seed 1.5` and
`random/seed 1` start different sequences. A string, a binary and a tuple seed
with a twenty-four bit checksum of their bytes. A time seeds with its nanoseconds;
a date packs its year, its day of the year and its time into one number. A pair
seeds with the raw bits of its two single-precision halves side by side, which is
what the C's union makes `VAL_INT64` read. Logic is the odd one: true seeds from
the clock and false seeds with one, so `random/seed true` is how a script asks for
a sequence nobody can predict. A block and a vector have no arm at all and answer
`bad-refines`, which is the C declining rather than failing.

**`random/only` on a string is biased towards multi-byte characters.** The pick is
`index += Random_Int(secure) % (tail - index)` followed by a step back to a
character boundary, and both halves are byte offsets into the UTF-8 the string is
stored as. Every *byte* is equally likely to be landed on, so a character written
in three bytes comes up three times as often as a one-byte one. Measured on a real
Rebol before this was ported: six thousand picks out of `"aéb"` gave the two
one-byte letters about fifteen hundred each and the two-byte letter about three
thousand.

**`random/only` on a binary shares that arm and its quirk.** The step back to a
character boundary is in the same `case` label with nothing to say a binary is not
text, so an octet between `80` and `BF` is never the answer whenever an octet
below it could be stepped back to. `random/only #{4180}` on a real Rebol answers
65 six thousand times out of six thousand.

**`Random_Range` rejects and redraws, and the rejection is load-bearing.** Taking
the remainder of a number drawn from nought up to two-to-the-sixty-second leans
towards the low end whenever the limit does not divide that range evenly, and the
lean is large - over a limit two thirds of the range, the bottom half of the
answers come up twice as often as the top half. So the C throws away every draw
above the last exact multiple of the limit and draws again. Rebol's own test asks
for ten thousand numbers under such a limit and asserts half land in the top half,
which is a statement that the rejection is there. A limit past
two-to-the-sixty-second is larger than the generator's whole range, so no rejection
limit exists and the C answers `overflow`.

**A random date draws its month and day over their whole ranges**, not against the
date it was asked about: `year = Random_Range(year, num); month = Random_Range(12, num); day = Random_Range(31, num);`.
Drawing them against the original's own month and day means an August date can
never come back in September, and a real Rebol answers February for one readily.
The C then falls into `Normalize_Date`, which carries a day past the end of its
month into the next one - and the month and day it carries are counted from zero
where the drawn numbers start at one, so the first of January plus that many months
and that many days is the same walk and can only land on a date that exists. Without
the carry, a February drawn together with a thirtieth is an out-of-range failure.

## 39. Arithmetic: what each datatype will meet, and what it refuses

**Two binaries combine octet by octet as long as the longer, wrapping the shorter.**
`Xandor_Binary` walks the longer and wraps its index into the shorter -
`if (i == mt) i = 0` - so one octet against four is that octet four times, not one
octet and three zeros. Which side was written first makes no difference: the C
picks the longer to walk before it looks at anything else, so all three operations
come out the same either way round.

**A pair meets a pair or a plain number and nothing else.** `REBTYPE(Pair)` names
three datatypes for the other side - pair, integer, and decimal or percent - and
traps anything else. A money and a time are the two that look like they ought to
work: both are numbers elsewhere in the language, and neither is a number here.
Where one side is a single number it applies to *both* halves, so `1x2 + 1` is
`2x3`. That is the only place in the language where an operand is spread across a
value rather than widened to meet it.

**A number on the left only reaches a vector for ADD and MULTIPLY.** The other
operations never get there, because a number's own arm is what dispatches and it
forwards only the two whose answer does not depend on which side is which. `10 - v`
is therefore not "v subtracted from ten" but no operation at all.

**Character arithmetic converts the right operand first, then refuses an invalid
result.** A character gives its codepoint, a whole number itself, a decimal its
truncation, anything else is refused. The answer must be a character, so
`IS_INVALID_CHAR` refuses a result past the last codepoint or inside the surrogate
range rather than wrapping it. `random` on a character loops for the same reason -
the surrogate range sits inside the span being picked from.

**A failure about a codepoint names the number, not a sentence.** `err/arg1 = 55349`.
A script catching it compares against the number it passed, which a sentence will
never equal.

## 40. A tuple clamps where everything else wraps, and LERP casts where everything else rounds

`255.255.255 + 1` is unchanged, not `0.0.0`. An implementation that wraps is right
for every value except the ones at the edge - which are exactly the ones a colour
or a version number reaches. Nothing in tuple arithmetic ever raises for range.

A single number applies to every octet, as it does for a pair. Where the other side
is a tuple, the longer of the two lengths wins and the shorter contributes zeros.
Two guards in `REBTYPE(Tuple)` are not obvious: a zero octet is left alone by a
multiplication whatever the factor, and a factor above 255 saturates *before* being
multiplied out rather than after, so no intermediate leaves the range of a machine
integer.

REVERSE turns round every kept octet, so a tuple made from the string "1" keeps
`1.0.0` and reverses to `0.0.1`. The zeros behind the kept ones take no part, which
is what stops a short tuple growing when it is reversed.

**LERP is not built out of that arithmetic.** `REBNATIVE(lerp)` casts each octet to
a byte rather than rounding it, so a walk that lands on 83.5 gives 83. The fraction
is clamped to nought and one, so LERP never walks past either end however far the
fraction reaches, and both ends must be the same kind of thing - a tuple against a
pair is a type mismatch rather than something to widen.

**`random` on a pair reads each half as a machine integer first.** An infinite half
becomes a number that way rather than refusing, which is the only reason
`random as-pair 1e300 -1e300` can be a pair at all - and why Rebol's own assertion
about it asks only that the answer is finite.

## 41. A time is a signed count of nanoseconds, and going through a double loses it

A bare number meeting a time is **seconds** - not minutes and not hours. Scaling is
the exception: `1:00 * 2` doubles the duration rather than adding two seconds,
because there is nothing else multiplying by a count could mean. A time may be
negative; it is a duration rather than a clock reading, so there is no floor at
midnight.

The C adds two times as sixty-four bit integers. Going through a double loses the
low digits of any duration past about a hundred days, because a double holds
fifteen or so significant figures and a duration that long needs nineteen - so
`-1.0 + -596523:14:07.999999999` comes back rounded to the second with the nine
nines gone. The other side is turned into nanoseconds *before* the sum, which is
the C's own order: `(i64)(dec * SEC_SEC)` first, then an integer add.

The same applies to TO TIME!. Nine thousand million seconds is a nineteen-digit
count of nanoseconds - fine for an integer, five digits past what a double holds,
which had been answering two and a half thousand hours plus half a microsecond
nobody asked for. A fractional number is **rounded**, not truncated.

**Overflow is refused, not clamped.** `Add_Max` traps for a time -
`if (type) Trap1(RE_TYPE_LIMIT, ...)` - and only clamps where the caller passed no
type to name. Clamping instead answers the biggest time there is for every number
above it, so a calculation that went wrong by a factor of a thousand comes back
looking like an answer; and for a negative one it comes back as `--2562047:-47:-16`,
which is not a time at all.

The two limits are different numbers. TO TIME! bounds a count of whole *seconds*;
arithmetic bounds whole *hours* (`MAX_HOUR * HR_SEC`, rounding down to 2,562,047
hours). So a duration can be made that arithmetic will then refuse to add to.

**A number on the left agrees on less than it looks.** The C dispatches on the left,
so those are the integer and decimal handlers, not the time one. Adding works either
way and so does multiplying. Subtracting works from a whole number and not from a
fraction. Dividing works from neither - there is no reading of "two divided by ten
hours" that answers a duration.

## 42. Time and money meet each other as an hourly rate, and only for two operations

**Dividing two durations changes the datatype.** Adding, subtracting and the
remainder of two durations give a duration; dividing answers how many times the
second goes into the first, which is a plain number the C sets explicitly -
`VAL_SET(DS_RETURN, REB_DECIMAL)`. Multiplying two durations means nothing and is
refused.

**A time against a money is an hourly rate.** The time counts as *hours* -
`secs * NANO / 3600.0` - so an hour and a half at five pounds an hour is seven
pounds fifty, and a hundred pounds over four hours is twenty-five an hour. Reading
it as seconds gives `$5 * 1:30:0` = `$27000`: a wage calculation wrong by a factor
of 3600, and wrong quietly. Adding a duration to an amount of money means nothing
and is refused rather than widened.

**A time against a percentage is multiplication only**, and the C says why in a
comment of its own: "support for actions like A_ADD does not make sense, so only
MULTIPLY is supported". Half of ten hours is five hours; ten hours plus fifty per
cent of nothing in particular is not a question with an answer.

**Money arithmetic always answers money.** `REBTYPE(Money)` widens the right side
and does the sum in `deci`; there is no branch out of the switch that changes the
datatype. `$4 / $4` is `$1`, not the plain `1` the division suggests. Four
datatypes widen - integer, decimal, percent, money - and a time is the special
case, taken by multiplication and refused by the other four
(`IS_TIME(arg) && action == A_MULTIPLY`).

## 43. A date's right-hand side names three different units

A **whole number** is days and moves the calendar alone, so the clock and the zone
come through untouched. A **time** is a duration and moves the clock, carrying into
the day when it runs past midnight. A **decimal** is a fraction of a day and moves
the clock as well - which is why `20-Sep-2021/12:00 + 1.9` lands two days later at
9:36 rather than one day later at noon.

The C reaches each through a separate `type ==` arm and ends every one at
`Normalize_Date(day, month, year, tz)` with the `tz` it read off the original.
Losing the time and the zone turns `+ 1` into a bare day; reading a time as a count
of days puts `20-Sep-2021 + 1:00` five years out.

A date with no time counts as its midnight and comes back carrying one -
`if (secs == NO_TIME) secs = 0` - so adding an hour to a bare day gives
`20-Sep-2021/1:00` rather than the day back unchanged.

**Subtracting two dates answers a whole number**, because the difference of two
moments is a span. `Diff_Date` counts days and never looks at the clock, so two
dates two hours apart differ by nothing at all.

**DIFFERENCE on two dates is the odd one in the set operations** - not about
membership at all. It reads the other way round from subtraction
(`difference 1-Jan 2-Jan` is minus a day where `2-Jan - 1-Jan` is one) and it
answers a `time!` rather than a count of days.

## 44. ROUND takes its datatype from the scale, not from the subject

Plain ROUND with no scale keeps the *subject's* datatype: `round $1.5` is a money,
`round 50.5%` is a percent. Each datatype's `A_ROUND` ends at its own `setDec` or
`SET_TYPE`, so that is the rule rather than the exception.

With a scale it is the other way round, and it is the same in all three of
`t-money.c`, `t-decimal.c` and `t-integer.c`: the answer takes the **scale's**
datatype. `round/to $1.333 .01` is the decimal `1.33`, not a money.
`round/to $0.5 1` is the integer `1`. A money scale pulls it back:
`round/to 0.5 $1` is a money. Reading it as "keep the subject's datatype and let
the scale say how far" is the natural guess and it disagrees on every mixed call.

Dividing by the scale and multiplying back puts noise in the low bits, so
`round/to $1.333 .01` computes `1.3299999999999999` and has to be trimmed to the
fifteen digits MOLD would show before anything compares it.

**A time rounds in nanoseconds and the scale decides the answer's type.**
`round/to 12:34:56 0:1:1` lands on a multiple of sixty-one seconds; an integer or
decimal scale answers a *count of seconds* (`VAL_SET(arg, REB_INTEGER)` in the C's
own branch) where a time scale answers a time. With no scale a time rounds to whole
seconds, and to the nearest whichever way the refinements point -
`Get_Round_Flags(ds) | 1` sets the to-nearest bit over whatever was asked.

## 45. FIND's walk, and what a binary is searched for

`case A_FIND: case A_SELECT:` is one arm in `t-block.c` and again in
`t-string.c`, so the whole search is shared and the two part company only in what
they do with the answer.

/REVERSE and /LAST both make the step negative. /LAST starts at `end - len` and
walks back to the position; /REVERSE starts one *before* the position and walks
back to the head, so it is the only search that may answer a place the series has
already passed. /MATCH breaks out of the loop after the first item, so it asks
whether the needle is *here* rather than anywhere ahead. The needle's width is
computed before the search, because a run of three cannot start in the last two
places; /ONLY makes any needle one item. A /PART limit is counted from the
position rather than from the head, so the same range asks for less of a series
already walked into.

**A binary holds bytes and no characters**, so looking for text in one means
looking for the bytes that spell it. Taking the code point straight - or the
UTF-16 units of a string - works for ASCII and nothing else:
`find (to binary! "ačb") #"b"` works and `find (to binary! "ačb") #"č"` finds
nothing at all.

But a char up to 255 is *that one byte*, not its encoding. `find #{00FF} #"^(ff)"`
finds the byte FF and does not go looking for the two bytes UTF-8 would spell it
with - a binary of arbitrary bytes is the commoner thing to search, and a caller
writing a char that fits in a byte means the byte. Only above 255, where no single
byte will do, does the encoding come into it.

## 46. COMPOSE/DEEP copies a path whole, and sharing one is invisible until something binds it

`else { DS_PUSH(value); if (ANY_BLOCK(value)) VAL_SERIES(DS_TOP) = Copy_Block(...); }`.
A block and a map are rebuilt, because that is what descending into them means; a
path and the rest of the any-block family are *copied*, so two composes of one
template share nothing.

The sharing shows only once something binds one of the answers, and then it reaches
into the other. Two functions made from one template through
`compose/deep [print a/1 (c)]` shared the path, so binding the second one's body
unbound the first one's argument.

A paren producing a block has its contents spliced in rather than the block itself,
which is what makes COMPOSE useful for building a block out of pieces. In a map,
splicing is always suppressed and keys are pushed raw.

## 47. SORT's comparator is handed its arguments backwards, and the sort cannot be the JVM's

**The two values go in the other way round.** `Compare_Call` hands the comparator
asking about the pair (first, second) the pair (second, first), so a comparator
written `[a > b]` counts *down*.

**The answer starts at -1 and stays there** unless the comparator gave a true logic
or a number at or above zero. So false means "the one on the left comes first"
rather than "these two are equal", and only a numeric zero is a tie. That is what
makes a plain strict predicate stable: two equal items answer false, which says
leave the pair as it is.

**`stable_sort` has to be written out rather than handed to the JVM.** The JVM's
sort is stable for a comparator that behaves like one, and a REBOL comparator need
not: a plain predicate such as `[a < b]` answers "left first" for a pair either way
round, which is a contradiction as far as a sort is concerned. The C's merge takes
from the left run whenever the comparison is at or below zero, so a contradiction
of that shape leaves the order alone. TimSort reads the same contradiction as a
descending run and turns it round. The whole difference shows up as records with
equal keys coming back shuffled - not obviously a defect until something sorts
twice to order by two keys.

**The C lends the same two blocks to every comparison and locks them**, because a
comparator that grew one would corrupt the next call. JEBOL builds a fresh block
each time and still locks it, so a comparator written against a real R3 fails here
the same way rather than quietly working.

A whole record is lent in the shape the C lends it: a binary series lends a binary,
a string a string, anything else a block - so a comparator asking `binary? x` of a
sorted binary sees a binary and not a block of byte numbers. Without /ALL a record
is ordered by its first element alone, so `sort/skip [4 3 4 1] 2` leaves both
records where they were. An integer /compare names a *column* rather than being a
function, and a block names several to try in turn; without /skip there are no
records, so a column number means nothing and saying so beats picking one.

A line-break mark belongs to the value it precedes, so a sorted block is laid out
the way its values were rather than the way its positions were. Which record came
from where is read off the list objects themselves - sorting reorders them and does
not replace them - because two equal records hold equal values and cannot be told
apart by what they hold.

## 48. FIND/ANY has two wildcards and /WITH renames them one at a time

`c_some` and `c_one` in `Find_Str_Str_Any`: the first stands for any run of
characters including none, the second for exactly one, and /ANY is what turns them
on at all. /WITH names them itself - the only way to search for a needle holding a
star of its own, because renaming the run character leaves the star an ordinary
letter again. The C reads the first character as the run one and the second as the
single one, each behind its own bounds check, so a one-character /WITH renames the
star and leaves the question mark standing, and an empty one changes nothing.

A wildcard match may not run past where the search stops - `while (n < len && pos < tail)` -
and a star at the end of the pattern takes exactly as far as it:
`pos = (skip > 0) ? tail : start;`. A plain needle is bounded by its own length
instead, so that one may run past the range and still match. A star tries its
shortest length first, so the end reported is the earliest that works - and the end
has to be reported separately, because /tail cannot land a fixed distance along the
way it can for a plain needle.

**/SAME is identity, not equality.** `Compare_Values(value, val, 3)`, where 3 is
"same (identical bits)" by the C's own comment. Two objects holding the same fields
are equal and are not the same object, so /SAME finds the one that was handed in
and not the copy beside it. On numbers the two part company by datatype: `1.0`
equals `1` and is not the same as it, so in `[1.0 3 1 3 1.0 2.0 1 2]` a loose search
for `[1 2]` finds the decimals at position five and a same search finds the integers
at seven.

## 49. A bitset spec says more than a list of characters, and MAKE reads a number differently from APPEND

**The word NOT at the head of a block complements the set**, and a binary inside
the block supplies the octets whole rather than naming code points one at a time.
Rebol's own JSON codec uses both at once:
`to bitset! [not #{FFFFFFFF2000000000000008}]` is every character except the
control codes, the double quote and the backslash. Drop either and that set comes
out empty, so every character of a string is escaped as a code point.

`Set_Bits` walks the block turning bits on as it goes, so **every spec adds to what
came before** rather than replacing it: `[1 - 3 #{80}]` is the range and the byte
together. Five shapes are understood - a char or a number names one bit, a dash
between two of them names the run, a string names one bit per character, a binary
supplies octets whole, and the word `bits` in front of a binary says the same thing
out loud. Anything else is an invalid argument, which is what makes a *trailing*
`not` an error: the word only means the complement at the head.

A run's far end has to be the same kind as its opening. The C asks twice, each time
about one type, and either failing is `Trap_Arg` - so `[#"a" - 5]` is an invalid
argument rather than the run from `a` to five, and a dash with nothing after it is
the same error against the end of the block.

**One arm differs between MAKE and APPEND, and only one.** A number given to
`make bitset!` asks for *room* - `make bitset! 8` is eight bits of nothing - while
the same number given to `append` names the bit to turn on. `Make_Bitset` sizes and
stops; the C says so in as many words, "nothing more to do". Reading both through
one helper makes `alter bs 1` report that it added a bit and leave the set exactly
as it was.

**PICK and FIND on a bitset share one arm and take five shapes.** A char and an
integer each name one code point - which is why `pick charset "a" 97` is true. A
string or a binary asks about every character in it. A block asks about every one it
names, ranges included, read by the same walk that builds a set. All must be held
unless /ANY was given. Answering only a char leaves every other form quietly false,
which reads as "the set does not hold it" rather than as a question never asked.

A char FIND matches either case unless /CASE was given, and a *number* naming the
same code point never does - `IS_CHAR(arg) && action == A_FIND && !D_REF(ARG_FIND_CASE)`
spells out all three conditions at once. So `find charset [#"A"] #"a"` is true and
`find charset [#"A"] 97` is false.

## 50. ZERO? is a range over the datatype table, and a bitset is not a comparison

`if (type >= REB_INTEGER && type <= REB_TIME)` takes in the char, the pair and the
tuple as well as the four numbers: a zero pair and a zero tuple are zero, and so is
the null character. Everything else answers *no* rather than being refused, which is
why the declared argument is a bare `value` and a string simply says false - taking
numbers only makes ZERO? raise on the datatype the question was written for.

A bitset is the odd one. `Is_Zero_Bitset` asks whether every byte is what an empty
set would hold - nought, or `0xFF` where the set is written as a complement - so
`complement make bitset! #{FF}` is zero because it holds nothing, while a
complemented charset is not.

## 51. A binary offered to a vector is truncated, not refused

`src_len /= bpv; if (src_len == 0) Trap1(RE_INVALID_DATA, src_val);`. The source
length becomes a count of whole numbers and the remainder goes with it, so three
bytes offered to a vector of sixteen-bit numbers give one number and drop the odd
byte - no failure and no warning. Only a run too short to spell a single number is
refused, and the failure hands back the binary itself so a caller catching it can
see what was offered. "The binary must divide evenly" is the reading a careful
implementation arrives at, and it is not what the C does.

/PART counts what the *source* offers, and for a binary it counts bytes rather than
elements: `append/part v #{0304} 1` takes one byte, which is one number in an
`int8!` vector and not enough for one in an `int16!`. CHANGE is the exception - its
/PART counts what to remove from the *target*, which the C makes in a single line:
`Partial1((action == A_CHANGE) ? value : arg, ...)`.

A count that is not a series takes no limit at all - the C's last branch writes one
value and never looks at the length - so `append/part v 3 0` still adds the 3. A
negative count reaches back from where the source stands, and the refusal then names
the binary at the position it was moved to.

## 52. APPLY reads its block against the function's words in order, not by name

A word takes the next value as its argument, and a **refinement** takes the next
value as a *logic* saying whether it is used. A refinement's arguments are read
whether or not it was asked for, so nothing after them shifts:
`apply :copy [[1 2 3] false 3]` reads the 3 into /part's slot and ignores it.
Values run out rather than being an error - what is left is none, and a refinement
nobody mentioned is not asked for.

The matching is **positional**, not by name. Rebol's declared parameter names and a
port's registered ones are not always the same word - UPPERCASE takes `string`
there and `text` here - so matching on the name hands every one of those a none.
Taking the first N positionally and ignoring the refinements is the other failure:
`apply :copy [[1 2 3 4 5] true 3]` then answers the whole series with no error.

WORDS-OF keeps the words in the order the spec wrote them, which is the whole
reason the spec is read rather than rebuilt: `/part range /only /dup count` cannot
be recovered from a set of refinement names and a list of parameters. A parameter
keeps the **sigil** it was declared with, because the sigil is how it takes its
argument - `words-of :++` is `['word]`, and a plain `word` there would say the
argument is evaluated when it is not.

The datatype tests are generated rather than declared, so they appear in none of
the declaration files and all read identically:
`["Returns TRUE if it is this type." value [any-type!]]`.

## 53. DO of a file is not written in C either

`n-control.c` sends `REB_FILE`, `REB_URL`, `REB_STRING` and `REB_BINARY` to
`Do_Sys_Func(SYS_CTX_DO_P, ...)`, which is `sys/do*` in the borrowed
`sys-base.reb`. It loads the file with its header, runs its NEEDS, interns it, and
evaluates it with the working directory moved to the file's own and put back
afterwards.

Only the two name-like types go there. A string and a binary keep their own routes:
a string is *source* rather than a script, and reaching `do*` with one would give
it a header and a NEEDS pass it has never had. Without the split a file matches the
string case - a file *is* one - and the file's own name is evaluated as source, so
`do %units/files/unset.r3` raises `no-value` on the word `units`.

## 54. Three walks that must go forwards, and one that must count code points

**REMOVE-EACH walks forwards.** Walking backwards so that removing an item cannot
disturb the indexes still to come works for the removing and is wrong for
everything else, because the body runs in that order too. Rebol's own test appends
each character to a string as it goes and then checks what it collected - walked
backwards, the answer comes back reversed, and nothing about REMOVE-EACH says it
would. Deciding first and rewriting afterwards keeps the walk forwards and the
indexes still.

**REVERSE on a string turns characters round, not Java's sixteen-bit units.**
Reversing by those splits anything above the basic plane into its two halves and
puts them back the wrong way round, which is not a character at all.

**RANDOM's shuffle is Rebol's, not the JVM's.** Both are Fisher-Yates and they are
not the same shuffle: Rebol walks down from the end taking `Random_Int % n` each
time, and `Collections.shuffle` draws differently and consumes a different number
of values. With the generator matching, this is the other half of making
`random/seed 1` reproduce Rebol's own answers. A string is shuffled *in place* like
a block - building a new one leaves the caller's string untouched, which is the
whole point, and skips the refusal a protected string is owed.

**A /PART limit may be a number or a position**, and a position means "up to here".
Reading only the number turns `insert/part output a b` into an insert of everything
from `a` onwards - which is how REWORD over a binary came to answer its whole
template with the substitutions appended.

A position stranded past the tail is brought back to the tail before every series
action - `if (index > tail) VAL_INDEX(value) = index = tail;` - so a change or an
insert there appends instead of failing.

## 55. COPY's /TYPES and /DEEP are two questions, not one

`if (deep) types |= CP_DEEP | (types_given ? types : TS_DEEP_COPIED);`. /TYPES says
which datatypes are *duplicated rather than shared*; /DEEP says whether to go on
doing it inside whatever was duplicated. Asking for types alone reaches one level
down and no further.

So `copy/types m string!` gives a map whose strings are its own and whose nested
map is still shared - and every string inside that nested map is untouched, because
the map was not copied and there was nothing to go into. `copy/types m object!`
gives a new object holding the *original* inner object; `copy/deep/types m object!`
gives both new. A plain COPY names no datatypes at all, which is how it stays
shallow without a separate branch saying so.

**Naming any set replaces the standard one rather than adding to it** - that is the
whole point of the refinement: `copy/deep/types b string!` reaches every level and
copies only the strings it finds.

The standard set is `TS_DEEP_COPIED`: every series and a map, less the four the C
names as not copied - `TYPESET(REB_IMAGE) | TYPESET(REB_VECTOR) | TYPESET(REB_TASK) | TYPESET(REB_PORT)`.
An image is shared because copying one is expensive, and a port because two ports
on one connection would be two ways to close it.

## 56. TAIL? and PARSE each have a written-out typeset, and both lists matter

`tail?` admits `series [series! gob! port! bitset! typeset! map!]`. NONE and OBJECT
are deliberately absent: EMPTY? is *the same action* under a wider spec -
`mezz-series.reb` writes `make :tail? [...]` - so the shared body answers for an
object while the list turns one away at the word TAIL? itself.

PARSE's `series!` is narrower still. A map, a bitset and a typeset all carry
contents and hold a position, and none of them is parseable. A real 3.22.5 answers
`make typeset! [binary! string! file! email! ref! url! tag! image! vector! block! paren! path! set-path! get-path! lit-path! hash!]`.
Declaring PARSE's input with no typeset at all lets `parse 1 [end]` run and answer
false - and a rule that never matched and a value that could not be matched are
different facts.

**A /SKIP record width below one is `out-of-range`, not clamped.** Clamping turns
`union/skip [2 1] [2 1] -2` into an ordinary call over single items, so a caller who
worked the number out wrongly is told nothing.

**REMOVE-EACH over a map halves its count** - `SET_INTEGER(DS_RETURN, IS_MAP(value) ? index / 2 : index);` -
for the same reason APPEND/PART is halved. The keys are gathered before any are
taken out, because removing while walking reads a map that is changing underneath
and the result would depend on where in its storage each key happened to sit.

## 57. A file port is a series, and the line between what its position governs is not obvious

A file port has a position, so **LENGTH? counts what is left** rather than what
there is, SIZE? counts the whole file whatever the position, INDEX? is one more
than the position, and EMPTY? and TAIL? both ask whether there is nothing left.
That is why `write p "a"` leaves `length? p` at nothing and `size? p` at one.

**Moving a port moves the port.** A series answers a new value at the new position
and leaves the old one where it was; a port has one position. So `skip p 2` and `p`
are the same port afterwards, which is what `index? head p` being one and
`index? skip p 2` being three in the same breath depends on.

**CLEAR cuts the file off at the position** - it does not empty it. `clear` on any
series throws away what is from the position onwards and keeps what is before it,
and a file port is a series. Clearing at the head empties it; clearing at the tail
does nothing.

**On a closed port, the questions about the position raise and the others do not.**
INDEX?, LENGTH?, TAIL? and every move raise `not-open`. SIZE? does not, being about
the file rather than the port. Neither do READ and WRITE, which open it again for
the one call - `if (!IS_OPEN(port)) ...` in `File_Actor`, which is why the suite can
read the same closed port three times and get the whole file each time.

**Only `open/read` cannot make a file.** The device carries `O_CREAT` for anything
that may write, and a bare OPEN fills in *both* modes -
`if (!(args & (AM_OPEN_READ | AM_OPEN_WRITE))) args |= (AM_OPEN_READ | AM_OPEN_WRITE);`
is the first line of `A_OPEN`. So the truncation has to ask the filled-in modes
rather than what the caller wrote: a bare OPEN reads, so it does not empty the file.
`open/new` is the one that creates, truncating whatever was there.

**Opening a directory pattern raises where reading it answers empty.** The C reads
the directory as it opens and raises when that fails -
`if (result < 0) Trap_Port(RE_CANNOT_OPEN, port, dir->error);` - where READ of the
same pattern answers an empty block. Opening asks for a thing and reading asks a
question: no matches is an answer to the second and not to the first.

**CHANGE-DIR writes PWD.** `OS_Set_Current_Dir` does both in three lines, and the
C's comment names the issue it was written for: "directory changed... update PWD".
So `pwd = to-rebol-file get-env "PWD"` holds before a move and after one, which is
what Rebol's own port test asserts on both sides of a `change-dir %../`. It is
written *after* the move, so a refused CHANGE-DIR leaves both where they were - and
only where the host granted an environment to write to, because moving does not
depend on the writing.

## 58. IF, UNLESS and EITHER take any value as a branch, and only a block means "do this"

`if (IS_BLOCK(D_ARG(2)) && !D_REF(3)) { DO_BLK(...); } else return R_ARG2;`. That is
what lets `if false "text"` stand where a value is wanted, and why
`reduce [{abc} if false {def} {ghi}]` is three items rather than an error about a
string where a block was expected.

**REDUCE/NO-SET keeps the set-word and reduces only what follows it**, assigning
nothing: `[x: 1 + 2]` becomes `[x: 3]`. Plain REDUCE performs the assignment and
drops the set-word, leaving `[3]`.

**`remove/key` only matches an odd place.** `remove/key [a b b c] 'c` finds nothing -
the c there is a value; `'b` finds the pair at the third place and leaves `[a b]`.
The key is matched exactly, so `'B` does not find `b`, which is the one place a word
does not fold case.

**PROTECT/HIDE/WORDS takes a block of bound words**, not bare ones. The words carry
their own bindings, so the block says which context as well as which names. That is
how a module hides the fields its body marked HIDDEN -
`if block? hidden [protect/hide/words hidden]` in `sys-base.reb`. Refusing anything
but a bare word makes every module with a HIDDEN in it fail to build.

## 59. A gob's pane is searched backwards and its rectangle is half-open

`Map_Gob_Inner` walks `gop = GOB_HEAD(gob) + len - 1` and then `gop--`, so where two
children overlap **the one added last wins** - which is what "topmost" means on a
screen. And the rectangle is half-open: `xo >= x + GOB_X` together with
`xo < x + GOB_X + GOB_W`, so a point on a gob's left edge is inside it and a point
on its right edge belongs to whatever is next along.

The /REVERSE arm is a plain climb - `xo += GOB_X(gob); gob = GOB_PARENT(gob);` -
which also stops at a gob flagged as a window. Nothing a script can do sets that
flag: `GOBF_WINDOW` is not one of the nine words `Gob_Flag_Words` accepts, so only
a host's windowing code raises it, and until one does the climb always reaches the
root.

Both directions carry `REBINT max_depth = 1000; // avoid infinite loops`. A gob
tree can hold itself - nothing stops a script appending a gob to its own descendant
- so the count is the only thing between MAP-GOB-OFFSET and a hang.

Rewriting an event to name the gob under its point needs two conditions -
`if (gob && GET_FLAG(VAL_EVENT_FLAGS(val), EVF_HAS_XY))` - so an event with no gob
and a key event with no offset both go straight through. `ROUND_TO_INT` puts the
walk's floating result back into an event's two shorts, and a gob's offset is a
float pair, so a child at 1.6x1.6 moves the point by 2 and not by 1.

**A gob spec is checked twice per pair.** The name must be a set-word, and the value
must be there and must not be another set-word - which is what catches
`[data: size: 10x10]`: a spec that reads as two fields is one field with no value.
APPEND, INSERT and CHANGE on a gob refuse /PART, /ONLY and /DUP with `not-done`,
which Rebol glosses as "reserved for future use (or not yet implemented)" - the C
saying it never got round to them rather than that they mean nothing.

## 60. PICK is not always a question about a position

Four datatypes read PICK as a question about a *field*, each in its own dispatcher.
A **bitset** asks whether it holds the value - `case A_PICK: case A_FIND:` share one
arm. A **map** asks what a key holds, and the C comments the case "same as SELECT
for MAP! datatype". A **date** and a **time** send it to `Pick_Path`, the same field
selection a path does - so `pick 1-Jan-2000 'year` and `1-Jan-2000/year` are one
question, and a time's seconds turn decimal once there is a fraction.

A position given to POKE goes through `Get_Num_Arg`: an integer, a decimal it
truncates, or a none it reads as zero. Anything else is `invalid-arg` - the error a
caller gets for `poke gob 'offset 1x1`.

## 61. PROTECT/WORDS takes paths too, and skipping them breaks DO

`natives.reb` says `/words "Process list as words (and path words)"`, and the
parenthetical is the part that matters: an entry may be `o/a` as well as `a`. A path
resolves to the **field it names** rather than to the word holding the object, so
`protect/words [o/a]` refuses `o/a: 11` and still allows `o: 12`.

Skipping paths is silent, because the call answers the block it was given whatever
it did with it. Rebol's own `protect-system` protects every word of SYSTEM and then
hands back the few a script must write with `unprotect/words [system/script]` - with
that ignored, `sys/do*` cannot record the script it is running and DO of a file
raises `locked-word`.

/VALUES and /WORDS are complements and neither does the other's job. /VALUES
protects what each word *holds*, so changing the series raises `protected` and
reassigning the word is fine. /WORDS protects the *slots*, so reassigning raises
`locked-word` and changing the series is fine.

A path that names nothing protects nothing and raises nothing - a missing field, or
a segment that is a number and so cannot be walked into, both leave the state alone.

**A /PART position is a span, not a direction.** The two positions may be given
either way round and the span runs from whichever comes first, so
`take/part s skip s 1` and `take/part skip s 1 s` are the same request and the same
position twice is a span of nothing. That is why the count can never be negative
even when the argument is behind the series.

## 62. WAIT is one call into REBOL, and an AWAKE answer must be a logic

`Wait_Ports` is a loop over `Awake_System`, and `Awake_System` is one call into
REBOL: the system port's own AWAKE, written in `sys-ports.reb`. It takes each event
off the queue, calls WAKE-UP on the port the event names, collects the ports that
said they were finished, and answers true when one of those is a port the caller
named. So the loop is `if ((result = Awake_System(ports, only)) > 0) return TRUE;`
and nothing else.

Doing the dispatch in Java is a second copy of a decision REBOL already states once,
and one that can only reach the ports an event arrived on - TLS waits on a port
whose events come from the TCP port underneath it, and moves itself along by putting
an event of its own on the queue.

**A port's AWAKE answer has to be a logic *and* be true**:
`if (!(IS_LOGIC(val) && VAL_LOGIC(val))) return R_FALSE;`. A truthy non-logic does
not count, which a reading of "if the awake function says so" gets wrong. The port's
UPDATE action runs first, and only when its actor is a native -
`if (IS_NATIVE(val)) Do_Port_Action(D_ARG(1), A_UPDATE);` - whose comment says why:
"makes the port object fully consistent with internal native structures". Every
actor a scheme installs here is REBOL rather than C, so nothing takes that step, and
replicating the *condition* rather than the body is the faithful thing.

**The timeout is picked out by datatype, not by position.** `wait [connection timeout]`
mixes the two kinds in one block and the C takes the first number:
`if (IS_INTEGER(val) || IS_DECIMAL(val) || IS_TIME(val)) break;`. Nothing says it
comes last.

`Sieve_Ports` does two jobs at once: it strikes out of the waited-on block every port
not on the wake list, **empties the list**, and WAIT answers the head of what is
left. Emptying matters as much as answering - a port left on it ends the next wait
before anything has happened.

## 63. Set operations keep their order, and DIFFERENCE on typesets is symmetric

The set operations keep the order they found things in rather than sorting, because
a block is ordered and the answer should be too. The answer keeps the datatype of
the first argument, so a set operation on two files answers a file, and characters
are compared without regard to case unless /CASE was asked for.

A map's members are its keys - `set1 [block! string! bitset! typeset! map!]` - so
the pairs come back with the keys the operation kept.

**On typesets, DIFFERENCE is the symmetric one** (`^=`): it keeps what is in one set
or the other but not both. EXCLUDE is the asymmetric one (`&= ~`). The two agree
whenever the second set is contained in the first, which is why a test written with
either passes and the distinction stays hidden.

**A binary is not a set operand.** Leaving it in the declaration lets one through to
a body that casts to a block, so `difference #{01} #{02}` comes out of the
interpreter as a Java class-cast rather than an error a script can catch. The
declaration decides that, not the body.

**Complementing a typeset covers every datatype the build knows**, not only the ones
mentioned so far - `VAL_TYPESET(val) = ~VAL_TYPESET(val)` is one line. So
`find complement make typeset! [block!] integer!` is true.

With /SKIP, **the first field decides whether two records are the same one** and the
rest are carried along: `union/skip [1 2 1 3] [1 2] 2` is `[1 2]`, because `[1 3]`
has a key already kept. Comparing whole records instead keeps both, which is the
answer for a plain UNION and not for this one. A short record at the end is kept
rather than dropped.

## 64. An image copy takes whole rows, and DO-CODEC's declaration is wider than any arm

`h = len / w` and then `memcpy(..., w * h * 4)` - the height is worked out first and
only that many pixels are taken, so a copy that would come to three rows and a spare
comes to three rows and the spare pixels are dropped. Fewer pixels than the picture
is wide makes a single row of exactly that many, so copying three pixels out of a
picture four wide is a three-wide picture rather than a four-wide one with a gap.

**DO-CODEC checks the action word before the data**, and each of the three wants
something different. IDENTIFY and DECODE both want a binary - they share
`if (!IS_BINARY(val)) Trap1(RE_INVALID_ARG, val);` by falling through. ENCODE wants
an image and nothing else. So a string is on the declared list and is refused by
every arm: the spec was widened for a codec that could take one, and none of them
does.

## 65. SET with an object on both sides is matched by name, and two refinements are two skips

The only shape of SET where position plays no part. Each word of the target takes
the value the source holds for that same word; a word the source has not got is
left as it was, and a word only the source has is ignored.

```
tmp = Find_Word_Value(VAL_OBJ_FRAME(val), VAL_WORD_SYM(word));
if (tmp) {
    if (IS_UNSET(tmp) && not_any) goto next_obj_val;
    if (ref_some && VAL_TYPE(obj_val) > REB_NONE
            && VAL_TYPE(tmp) <= REB_NONE) goto next_obj_val;
    *obj_val = *tmp;
}
```

Without /ANY an unset in the source is **passed over** rather than copied, so the
target keeps a real value instead of losing it to nothing. With /SOME a source value
of none is passed over when the target already holds something more than none -
which is what makes /SOME "fill in the gaps".

## 66. Six rounding modes, and the JVM's default is not REBOL's

Each was measured against a real R3 rather than reasoned about, because they
disagree in more places than the names suggest. /DOWN and /FLOOR agree on positives
and part company on negatives; /HALF-DOWN and /HALF-CEILING agree everywhere except
on a half. The default is **half away from zero**, which is what REBOL does and what
a JVM does not.

## 67. CLOAK overrides /WITH for an integer key, and a tag's name is its text

An integer key is spelled out in decimal and then **always** hashed, whatever /WITH
said - `INT_TO_STR(VAL_INT64(val), dst); ... as_is = FALSE;`. The C overrides the
refinement rather than honouring it, because the digits are not bytes a caller
chose.

A character set named by a tag uses the tag's own text: `<utf8>` names utf8, and the
angle brackets are how it was written rather than part of the name. Going through
FORM keeps them and refuses every tag.

The same distinction runs through ENBASE and its kin: `VAL_BIN_DATA` reaches the
series, and a tag's angle brackets and a file's percent sign are punctuation the
molder adds rather than content the series has - so `enbase <ab> 16` is `"6162"` and
not the four bytes of `<ab>`.

An environment variable may be named either way - `get-env "HOME"` and
`get-env 'HOME` are the same question - and a word keeps the spelling it was written
with rather than its canonical form, since the environment minds case and REBOL
words do not.

**A compression method has two refusals, because they are two answers.** A name
nobody has heard of is `invalid-arg`. A method REBOL really has and this build was
not compiled with is `feature-na`, which tells a caller to look for another build
rather than for a typo.

**ENHEX's default set follows the datatype**, which the C states in the spec itself:
"By default it is URI bitset when value is file or url, else URI-Component".

## 68. CHECKSUM/WITH 'hash is a table index, not a digest

It answers `Hash_Value(value) % size`. It is the only method /WITH is required for,
and the only one whose spec must be a number, because that number is the size of the
table the answer indexes into. A size below one is read as one, so the answer is
always a slot that exists. The size is counted in **thirty-two bits**, so a bigger
number wraps before it divides and a table of 4,294,967,296 slots is a table of none
- which leaves the hash whole rather than dividing by nothing.

`Hash_Value` mixes differently by datatype: a binary four bytes at a time and case
sensitively; a string one byte at a time with each byte's case folded, then carrying
its own datatype into the answer so that the same letters as a file and as a url
land in different slots.

**A negative /PART count is turned round, not refused.** `Partial1` moves the
position back by that many and makes the count positive, so the span always runs
forwards from wherever it lands, clamped to what is really behind; at the head
nothing is behind and the answer is empty. The count is in whatever the series holds
- characters for a string, not bytes.

**`index?/xy` is ordinary tail arithmetic.** `index % VAL_IMAGE_WIDE(value)` across
and `index / VAL_IMAGE_WIDE(value)` down, both from the zero-based index, with
INDEX? adding one to each where INDEXZ? does not. So the tail of a picture two
across and three down is `1x4` - the first column of a row that is not there. /XY
means nothing to any other series, and R3 ignores it there rather than refusing.

## 69. The binary dialect is a mutable thing with two cursors, and a write pokes

A protocol is a sequence of fields of stated widths, and writing one by hand means
shifting and masking at every field. The dialect says the widths instead, which is
why `prot-tls.reb` is built on it. The context can be an object made earlier, a
binary to work on directly, or a number of bytes to make room for.

**The context is a thing, not a value.** Every refinement changes the one it was
handed rather than answering a new one - a protocol writes a header, works out a
length, writes that, and reads the reply, all through the same `b`. Answering a
fresh context each time means every step after the first is written into something
nobody is holding, so `b/buffer` stays empty however much is written to it.

**Two cursors over one series**, because reading and writing move independently.
`buffer` is where the next read starts and `buffer-write` is where the next write
lands, so a context can be filled and then read from the beginning without either
cursor disturbing the other.

**A write pokes rather than appends.** `binary #{01020304}` leaves the write cursor
at the head, so writing a byte replaces the first one; the series only grows where
the cursor has reached the end. That is what makes a header writable twice, once
with a placeholder length and once with the real one. And a binary given straight to
BINARY/WRITE is written *into*: `binary/write b: #{} [UI8 255 PAD 4 UI8 255]` leaves
`b` holding all five bytes, so the caller's own storage has to change.

**A get-word is looked up by the dialect, not before it.** The block arrives
unevaluated, which is what lets a code be named rather than computed; the get sigil
is how a caller reaches a value it has in hand - `[UI16 :length]` writes the number
LENGTH holds, where `[UI16 length]` is an error because LENGTH is not a code. When
the word is looked up decides what it holds, so a block that names a length on one
code and spends it on the next needs the lookup at the second code.

And through **the binding the word already carries**. `Get_Var` takes the word and
follows it; rebinding finds a word of the same name wherever this happens to be
looking instead. That shows only when a caller picks a name the borrowed library
also uses - it hid until Rebol's own ZIP encoder, which keeps the directory it is
building in a word called DIR and writes it with `BYTES :dir/buffer`. Rebound, that
found the library's directory-listing function and no archive could be written.

**A number where a code was expected is a count of bytes**, and it never reaches
the dialect: `if (IS_INTEGER(val_read)) { ... return R_RET; }` is the first thing
`Do_Bincode`'s read does. So `binary/read b 2` is two bytes and `binary/read b [2]`
is a bad spec. /INTO is refused rather than ignored - `Trap0(RE_FEATURE_NA)` -
because there is no list of values for it to insert.

**The answer's shape follows the asking.** A block of codes answers a block; a
single word answers that one value. `binary/read ctx 'UI16` is a caller saying "one
number, please", and `prot-tls.reb` reads a field that way inside a loop and appends
the answer straight into a list, where a block of one would quietly nest.

**/INTO answers the caller's block standing after the values**, not the values:
`Insert_Series(blk, VAL_INDEX(val_into), temp, 1); VAL_INDEX(val_into)++;` once per
value, then `if (ref_into) *ret = *val_into;`. That is what makes
`binary/read/into bin [BYTES :size] tail data` the way a decoder appends - no
intermediate block, and the next call carries on where this one left off.

**CROP moves both cursors.** The bytes that went were in front of both, so the read
cursor lands at the head and the write cursor moves back by however many left -
never past the head, which is what `MAX(0, ...)` says. And the bit position is kept
on the context between calls, because the bit codes are meant to be used one at a
time: `binary/read bin 'BIT` twice running has to give two different bits.

## 70. A crypt port checks its algorithm twice, and blanks the key it was given

`Crypt_Open` copies the key and the starting vector into its own context and then
blanks both fields, with a comment saying why: "as we have a copy, make it invisible
from the spec". A specification is an ordinary object a script can read, mold or
pass on, so a key that stayed in it would travel everywhere the port did.

The algorithm is checked in OPEN as well as in the scheme's INIT, for the same
reason: INIT runs when the port is made, and a specification is an ordinary object a
script can write to afterwards, so what INIT approved is not what OPEN gets. Without
the second look the port opens with no cipher behind it and the next write reaches
for one that is not there.

Every action on a closed cipher port is refused, **asking whether it is open
included** - which reads as wrong until you see where the check sits: the actor
looks for its cipher above the switch on what was asked, so a closed port has
nothing left to answer the question with.

**A scheme is a promise.** A script reads `system/schemes` to find what it can open,
so registering one that leads nowhere is worse than leaving it out.

## 71. Two crypto natives in 3.22.1 are unfinished, and one reads uninitialised memory

**GENERATE answers a single zero byte, whatever curve was named.** The C shows why
on its own lines: `mbedtls_ecdsa_genkey` is commented out, the group is loaded as
SECP192R1 whichever curve was asked for, and the point written out is one nobody
set - so a real 3.22.1 answers the point at infinity for every curve in the
catalogue.

Finishing it would be wrong rather than generous. The declaration answers **one**
binary, with nowhere in it for a private key, so a GENERATE that really made a pair
would hand back the public half and discard the private half - an answer nothing can
use. ECDH/INIT already makes a usable elliptic-curve key. The curve name is still
checked, which is the part of it that works.

**DH neither publishes nor agrees.** The C reaches `return R_RET` having never
written the return slot, so a real 3.22.1 hands back whatever that memory held - a
binary of nothing in particular, and a crash when two contexts were built in one
expression.

## 72. IMAGE is where four of Rebol's own codecs live

Rebol's `codec-image.reb` writes every entry of `system/codecs` for png, jpeg, gif
and bmp as a call to the IMAGE native. So refusing there does not leave the codec
family to supply one - it leaves four codecs in the catalogue that cannot do
anything. Asked for nothing IMAGE answers unset, because the C's branches are all on
refinements and it falls out of the bottom; with no platform codec at all it is
`feature-na`, which is what the C answers where `INCLUDE_IMAGE_OS_CODEC` is
undefined.

`Trap0(RE_FEATURE_NA)` is the **first** thing the native does, so an interpreter
with no codec has to refuse for that reason rather than for whatever it would trip
over first - reading a file that is not there, or being handed something that is not
an image, would otherwise report those and hide the real answer. A word /AS names
that the codec has not got is `Trap1(RE_BAD_FUNC_ARG, val_type)`, which is a
different failure from bytes it cannot make sense of.

**/SAVE brings two arguments** - where the bytes go, and which image - so counting
one apiece puts every argument after it one place early. IMAGE has no required
arguments at all, so the first refinement's argument is the first there is.

A **binary** handed in as the destination is written into, and it is that very binary
that comes back: a caller passing one has a hold on it and means to read the bytes
from there. Handing back a fresh binary and leaving theirs empty looks as though it
worked and quietly does nothing. From the position, and everything after it goes -
the bytes are one whole file, and half of a previous one behind them would not be.

**RESIZE by a whole number keeps the shape.** "integer value is used as width" says
the declaration, and the height follows - a resize that squashed a photograph
because only one number was given would surprise everyone. A pair with one side at
nought asks the same question and says which side is being given. The derived side
is not brought up to one: a width so small that the height works out at nothing is a
tenth of a row, and there is no such picture, so the caller hears about it.

Which /FILTER runs changes how a shrunken photograph looks and does not change what
RESIZE is, so a build that samples one way for all fifteen is still RESIZE.
Accepting a *name that means nothing* is a different matter.

## 73. The crypto natives divide their refusals between raising and answering none

**RSA.** Naming two actions, or a padding refinement with no action, is
`Trap0(RE_BAD_REFINES)` and raises before anything is looked at. A handle of another
type raises too. But a public-only context asked to decrypt or sign **answers none**,
as RSA-INIT answers none for numbers that are not a key - so a caller has to test the
answer rather than trust that no error meant success.

**ECDSA/VERIFY answers TRUE or NONE**, not TRUE or FALSE, though the declaration says
"returns true or false". It matters because `if ecdsa/verify ...` reads the same
either way and a comparison against FALSE does not. Signing is what a call with
neither refinement does.

A handle carrying the wrong kind of key answers none rather than raising - the C
declines and doubts itself in the same line:
`return R_NONE; //or? Trap0(RE_INVALID_HANDLE);`

**ECDH names exactly one action**, which is why the count is taken before anything
else is looked at, and /INIT is apart from the other three because it is the only one
that makes a context. The peer's point sits after the key *and* after /INIT's curve
name when that was asked for as well - reading index two unconditionally costs four
tests that had a perfectly good secret to agree on.

**RC4 enciphers in place and answers the same binary.**
`RC4_crypt(ctx, data, data, len)` reads and writes one buffer and
`DS_RET_VALUE(val_data)` hands back the argument, so a caller holding it sees it
change and there is no copy to compare against.

## 74. An object identifier's first byte holds two arcs

`oid[0] / 40` and `oid[0] % 40` in `n-oid.c`. That is why every identifier a script
meets begins 0, 1 or 2: a first arc of 3 would need a byte of 120 or more, and 2 is
as far as one byte reaches before the division carries past what the registry allots.
Every byte after is base 128, seven bits at a time, with the high bit set on all but
the last of its group.

A group whose last byte never arrives contributes nothing - the accumulator is
written out only when a byte turns up with its high bit clear - so a truncated
identifier reads as a shorter whole one rather than refusing.

## 75. EVOKE's chants are assembled by the preprocessor, and a release build refuses six

All six watch chants sit inside `#ifdef DEBUG`, and the `#else` gives them one line
between them: `Trap0(RE_FEATURE_NA)`. So a released 3.22.1 refuses them by name
rather than pretending to have done something, and the help text a release build
prints lists `stack-size` and the two numbered checks and not the watch chants.

`stack-size` takes the value after it. The C steps over it without counting it, and
then reads one value past the end of the block - the stepping is the behaviour and
the overrun is not.

**Nine of `system/standard/stats`' thirteen fields name Rebol's own series pool** -
series-made, series-freed, series-expanded, series-bytes, series-recycled,
made-blocks, made-objects, recycles, collisions - and a JVM port has no such pool.
Zero there is the absence of a figure, and it reads the same as one, which is worth
knowing before trusting a profile taken from it.

**`system/version` is the REBOL version, not the port's own.** A script reads it to
decide which of the language's features it may use, and `struct-test.r3` wraps all
188 of its assertions in `if system/version >= 3.19.1`. Answering a number below
every guard in the suite makes those blocks skip, which reads as a passing file that
ran nothing.

**TO OBJECT! is not MAKE OBJECT! and takes only an error.** An error is an object
with eight fields, and this is how a script reaches them without the error being an
error any more. Everything else is bad-make-arg - `Trap_Make(type, arg)` is the line
the branch falls to, with nothing above it but the error case.

**TO MODULE! evaluates neither half**, unlike MAKE MODULE!, which is why the block is
written with REDUCE at the call site. An empty block or a non-block is bad-make-arg;
a block whose first two values are not both objects is invalid-arg.

## 76. MAKE IMAGE! refuses the same size two ways for two different reasons

`make image! -1x-1` gives an empty picture where `make image! [-1x-1]` is refused. A
bare pair goes through the code that makes a blank picture of a size and brings an
impossible one down to the nearest possible (`w = MAX(w, 0)`); a *specification* is
something somebody wrote out and got wrong, so `if (w < 0 || h < 0) return 0;` and
the caller turns that into `malconstruct`.

A side too wide goes the same way and for the same reason. On its own it is
`size-limit` - `if (w > 0xFFFF || h > 0xFFFF)` - because the number is the thing that
is wrong; in a specification the maker is handed nothing but a no and refuses the
whole block. Whatever was wrong, the value named in the failure is **the whole
specification** rather than the part that could not be read.

A pair makes an image that is **opaque white**: `CLEAR_IMAGE` is a memset of 0xFF and
the comment beside it says so.

`Create_Image` reads its parts in one fixed order - a binary of RGB triples, then a
binary of alpha bytes, then a starting index; or a tuple to fill with and an alpha to
fill with; or a block of tuples - and refuses the whole specification the moment a
part it cannot read is left over. That is also how **a block of colours comes to be
refused**: the branch that reads one never steps past it, so the leftover check fires
on the very block it has just used and the branch is unreachable. Bytes are the only
way to give a picture a list of colours, and that is what a real 3.22.5 does.

A starting index is `Int32s(block, 1)` - "a whole number of at least one" - so nought
and below is `out-of-range` rather than malconstruct: the shape was right and the
number was not. Past the end is not refused at all; the picture is still there and
taking its head gives it back.

**TO IMAGE! of a binary is four bytes a pixel at a width the C picks**, not the
caller: as many pixels as there are up to a hundred, a hundred to a row up to ten
thousand, five hundred beyond. `Trap_Make` when there is not one whole pixel there,
so `to image! #{000000}` is bad-make-arg rather than an empty picture. The last row
can be short, and the pixels nobody supplied stay the opaque white.

**A colour part rounds where every other decimal-to-integer conversion truncates.**
`arg_to_byte` reads an integer as itself, a decimal *rounded*, and a percent as a
fraction of 255, then clamps at both ends. Writing through a colour pointer reaches
the first three octets and stops, so a tuple keeps its length and everything past the
third octet is handed back untouched - which is what makes these usable on a pixel: a
fourth octet is an alpha and has no business being read as a hue.

**A pair position only means anything to an image.** `diff = ((y - 1) * wide + x)`
for AT, and without the 1 for SKIP and ATZ, because one counts from one and the
others from zero.

## 77. An operator takes exactly two arguments, counted up to the first refinement

`if (IS_REFINEMENT(args)) break;` is the C's own loop. What follows a refinement is
only ever supplied by a call that named it, and an operator has no way to name one -
so a function of two taking refinements after them can be made into one, and
`(abs a - b) <= (abs a * 0.01)` with a `/p` nobody uses is a fair operator. One
argument or three is refused.

A function or an action given to MAKE OP! is taken as it stands, **sharing** its
spec, its body and its arguments rather than being copied. The only thing the
operator adds is where its first argument comes from.

**`make :f [...]` reads up to two things, either of which may be left out.** Four
shapes and one rule - take what was given, keep what was not:

- `make :f []` is a copy, both halves kept.
- `make :f [[x]]` is a new interface over the same body.
- `make :f [[x] [y]]` is a new function that happens to have been written beside an
  old one.
- `make :f [* [y]]` is a new body under the same interface, and the star is what says
  "this half stays".

The body is bound to whichever arguments the *new* specification declares, which is
what the second shape is for. A word that was an argument and no longer is falls back
to what it meant outside: Rebol's own test makes a function whose body reads `a`,
gives it a specification without one, and reads the `a` that was already there.

The `*` is the multiplication operator's own word used as a placeholder, which reads
oddly and is unambiguous - nothing else could be meant by a bare star where a block
of arguments belongs.

## 78. MAKE BLOCK! and TO BLOCK! are different operations, and `hash!` is one row out

TO **wraps** whatever it is given: `to block! #"a"` is `[#"a"]` and `to block! "1 2"`
is a one-item block holding that string. MAKE takes a list of shapes and refuses the
rest: `make block! #"a"` is an error, `make block! 4.0` is an *empty* block because a
number is room rather than a value, and `make block! "1 2"` reads the text as source
and answers `[1 2]`.

Four shapes answer the same to both, because a block is what they already are
underneath: another block, a map, an object and a vector.

`ANY_BLOCK_TYPE` is a **range test** over the datatype table - block to lit-path -
and hash sits one past the end of it. So hash is an any-block! for the typeset and is
not one here, and that single row of the table is the whole of the difference:
`to hash! 4` falls through every arm and is refused, where only MAKE reaches the
room, source-text and pair arms.

**A nought byte ends source text.** `Scan_Source` is handed bytes and stops at one,
which is the C's own convention for where a string finishes. So
`make block! #{31 00 32}` is `[1]` and not `[1 2]`, and it holds for text as much as
for bytes because both reach the scanner the same way. Reading past it makes the
nought a character in its own right, so an empty source comes back as a block holding
one of them.

**A block carries a line break per item and MOLD honours it.** `Make_Object_Block`
sets the line flag on every set-word it writes, so `to block! make object! [a: 1]`
molds over three lines where the same block written by hand is one. That is a
property of the block, not of how it is later printed - and it is why `to block!` of
a map reads as a list of pairs. SELF is slot zero and the C starts counting at one,
so it is left out of BODY-OF, the block a conversion answers, and the spec an error
is built from.

## 79. A date's first number is the year when it is over ninety-nine

`make date! [2000 1 1]` and `make date! [1 1 2000]` are the same day and neither is
ambiguous. `MT_Date` reads day, month, year - except for that rule.

**A date will not take the twenty-fourth hour even though a `time!` will.**
`if (hour > 23 || minute >= 60 || second >= 60.0) return FALSE;`. That is the whole
difference between `make time! [24 0 0]`, a day's worth of hours, and
`make date! [2000 2 1 24 0 0]`, which is no date at all. The seconds may be
fractional; the other two may not.

A block may hold **two** times, and the second is the zone rather than another clock.
Anything left after it is a refusal - `if (!IS_END(arg)) return FALSE;` - because a
part the grammar cannot account for is not something to step over.

**A time block's hours carry the sign for the whole span**, so `[-1 30 0]` is minus
an hour and a half rather than an hour less thirty minutes.

**A Unix timestamp is decoded in microseconds, and the C's comment says why**: a
decimal count of seconds multiplied out to nanoseconds does not land where it should.
The remainder is scaled back up, so the time is exact to a microsecond and zero below
that. The zone is zero - a timestamp names an instant and not a place.

## 80. A spec block's words are resolved, but nothing is called

`Get_Simple_Value`: "Does easy lookup, else just returns the value as is." A word or
a get-word becomes what it holds and a path becomes what it reads; everything else is
left alone. Both spec walkers that build a datatype from set-word pairs call it -
`Set_GOB_Vars` and `Set_Event_Vars` - and without it a spec can only carry values the
source spelled out. Rebol's own gob test relies on it:
`g2: make gob! [size: g1/size]`.

**Easy** is the operative word. A function is answered rather than called and a paren
is left as a paren, so a spec block is data with names in it rather than code.

**CHANGE on a struct is two different operations.** A block names or lists fields and
goes through the same initialiser MAKE uses, so it can write a word or a decimal into
a field of the right kind. A binary is copied over the bytes as far as the shorter of
the two reaches - so changing a two-byte struct with three bytes writes two and drops
the third. A struct carrying a live REBOL value refuses the binary form outright: the
C keeps such a value in the bytes themselves and will not let arbitrary data land on
one.

`A_REFLECT` on a struct takes WORDS, VALUES, BODY and SPEC and refuses everything
else, so KEYS-OF reaches it as WORDS and there is no fifth question. **MAKE STRUCT!
cannot confuse its two shapes**: one block is a layout on its own, two blocks are a
layout and its starting values, and a layout can never itself be two blocks because
after its optional attributes it must be a word and then a block.

## 81. MAKE and TO are two operations, and a handful of datatypes tell them apart

MAKE **builds**, so it reads a number as room for values and a logic as one or zero.
TO **converts**, so it wraps whatever it is given and refuses a logic outright -
`T_Integer` says why in as many words: no integer is uniquely representative of true.
The C carries the distinction as the `make` flag it hands `Make_Block_Type` and as
the `action != A_MAKE` it tests in the scalar arms.

The C leaves a note where it decides truth, and it is the clearest statement anywhere
of what separates them. TO falls in line with the rest of the interpreter, where
everything not none and not false is true, so `to logic! 0` is **true**. MAKE takes
more liberties with the meaning of its argument and lets a zero be false, so
`make logic! 0` is **false**.

**Nothing is not an empty something.** `make string! none` is an error where
`make string! 0` is an empty string, and Rebol's own suite asserts that for all
fifty-seven datatypes in one go. Three answer rather than refuse: UNSET and NONE
answer their own single value, and LOGIC reads none as false. The block shapes are
their own case - MAKE refuses none there as `invalid-arg` rather than
`bad-make-arg`, and TO does not refuse it at all: `to block! none` is `[#(none)]`.

**A series cannot be made with room for less than nothing.** Clamping with
`max(0, ...)` makes `make block! -1` an empty block and the caller never learns it
asked for something impossible. R3 raises `out-of-range`.

**The first argument is "the datatype or example value"**, and MAKE says the same. So
`to "" #{6162}` is `to string!` and `to 1x1 [2 3]` is `to pair!` - the example is
read for its type and thrown away. Rebol's own quoted-printable codec ends on
`to data output` where DATA is whatever the caller passed in, which is the whole
point of the form: it hands back the kind of thing it was given.

## 82. TO BINARY! is a list of datatypes, not a rule

Reading it as a rule is the trap. Anything with bytes underneath looks convertible,
and four datatypes that *have* bytes are not on the list: a **percent**, a **paren**,
a **path** and an **issue** are all refused where the decimal, block, string and word
they resemble are taken. The C says so by naming its cases and giving everything else
`ser = 0`, which becomes `Trap_Arg`.

A tuple keeps its **own** length rather than the three it shows, so `to binary! 1.1.1`
is three bytes and `1.2.3.4.5` is five. A bitset written as a complement answers the
complement of its bytes - it keeps the bytes of what it leaves out plus a flag saying
to read them the other way round, so the turning has to happen at conversion or
`to binary! complement charset "a"` answers the set it is the complement of. An image
answers four bytes a pixel.

**A binary read as a double is right-aligned.** Shorter than eight is padded at the
front, so `#{01}` is the smallest subnormal rather than the number one; longer keeps
the last eight.

## 83. TO DECIMAL! and TO PERCENT! are one switch, and which group a source is in is not guessable

The two part company only at the end, and only for some sources. A number-like one
reaches `setDec` and is taken as the value itself, so `to percent! 4` is **400%**.
The rest fall through `if (type == REB_PERCENT) d1 /= 100.0` and are taken as a count
of hundredths, so ten hours is 36,000 seconds and **36000%** rather than a hundred
times that. Which group a source belongs to is read off the `goto` it ends on.

A logic is MAKE's alone. Only a **plain** string is read as text - a file, a tag or a
url is refused, which is the difference between `case REB_STRING` and `ANY_STR`.

## 84. Line endings: not a replacement of CRLF with LF

Rebol's comment says what it is - "converts any combination of CR and LF line endings
to the internal REBOL line ending" - and `Replace_CRLF_to_LF_Bytes` is six lines:

```
if ((c = *cp++) == LF) { if (*cp == CR) cp++; }
else if (c == CR)      { c = LF; if (*cp == LF) cp++; }
*tp++ = c;
```

So a lone carriage return converts; a line feed *followed by* a return is one ending
rather than two; and a return, a return and a line feed are **two** endings rather
than one, because the first return stands alone and the second takes the line feed
with it. Rebol's own port test asserts all three.

**Splitting into lines drops exactly one trailing empty line.** Java's own split does
one of two wrong things: with no limit it drops every trailing empty line, so two
blank lines come back as none; with a limit of -1 it keeps the one after the last
ending, so a file that ends properly gains a line it has not got. Nothing at all is
no lines rather than one empty one.

**`make email! [aaa bbb cc]` is `aaa@bbb.cc`** - the first item is the whole of the
user and everything after it is a label of the host, so two items give no dot and
three give one. **`make url! [http]` is `http://`** - the scheme takes the two
slashes whether or not anything follows, and every item after it is one path segment.

**`#(date! 1)` does not read where `make date! 1` does.** `MT_Date` takes a date or a
block of parts and nothing else; the count of seconds since 1970 is in the MAKE arm
one level above it. Construction syntax goes through `Make_Dispatch` rather than
MAKE, and the date is the one place the two differ.

**A port specification has six spellings and one reader.** `MT_Port` hands them all
to `sys/make-port*`, which is REBOL rather than C, and the six differ only in where
the scheme's name is found. What is not one of the six is refused *before* the call,
because the borrowed function answers none for anything else and none is not a port -
and the two refusals say different things: a number is no specification at all, while
a block naming a scheme nothing serves is a good specification about a doorway that
is not there.

**A struct's second block evaluates nothing.** That is why
`#(struct! [a [uint8!]] [random 10])` is a malconstruct rather than a struct holding
a random number: RANDOM arrives as a word, and a word cannot go in a `uint8!` field.
Only MAKE on an existing struct reduces first, with its set-words left standing - so
`make proto! [3 * 10 4 * 10]` writes thirty and forty while `[b: 3 * 10]` still names
a field.

## 85. TO INTEGER! and TO DECIMAL! read the same binary two different ways

A binary is **one big-endian whole number** to TO INTEGER!, so `#{01}` is 1 - the
opposite of TO DECIMAL!, which reads the same bytes as the raw bits of a double. A
time is its seconds and a date is its instant, both from the start of 1970; a date
with no time of day counts as its midnight, and a fraction of a second **rounds**
rather than truncating, so `12:46:41.7` is the second after `12:46:41`.

**A decimal out of range overflows before the cast, not after.**
`if (VAL_DECIMAL(val) < MIN_D64 || VAL_DECIMAL(val) >= MAX_D64 || isnan(...)) Trap0(RE_OVERFLOW);`
- so a not-a-number overflows as surely as an endless one does. Casting first
saturates in silence, turning an infinity into the largest whole number there is and a
not-a-number into nothing at all. The two bounds are **not** a mirror image: below the
floor is out and *at* the ceiling is out, so the most negative whole number converts
and the most positive does not.

**`to integer! #FF` is 255.** `Scan_Hex` scans while the characters are valid and
fails if there are more than will fit, so seventeen digits is an error rather than the
first sixteen, and a character that is not a digit fails wherever it appears -
`#-1` fails on the minus before it reaches the one. Sixteen digits fill the number and
run past the top of it: `#FFFFFFFFFFFFFFFF` is minus one, not an overflow.

**`make decimal! [1 2]` is a hundred.** The C multiplies and divides by ten in a loop
rather than raising a power, and its own comment calls that funky. It is kept because
the two do not agree in the last bits, and because the loop stops while the exponent
is still between minus one and one - which quietly truncates a fractional exponent
toward zero.

Anything with no number in it fails as **bad-make-arg**, not expect-arg. The
distinction is not cosmetic: expect-arg says the caller passed the wrong kind of thing
to a function, and a script catching it would be catching a different mistake.

## 86. What counts as a blank differs between the two ends of a word

`Qualify_String` in three steps: skip the leading blanks, take the word up to the next
blank, and require everything after it to be blank as well. Nothing taken is
`too-short` - there was no name in it, as opposed to a bad one.

**Skipping uses the lexer's own test, which a control character passes** - its default
class is written `LEX_DEFAULT (LEX_DELIMIT|LEX_DELIMIT_SPACE)` with the comment
"control chars = spaces". The trailing check uses `IS_SPACE`, which only a space and a
tab pass. So `make issue! "^(01)a"` is `#a` and `make issue! "a^(01)"` is refused,
from the same two characters in the other order. A line feed and a carriage return are
blanks at neither end: they have a lexer entry of their own where the other control
characters have none.

Neither refusal carries the text back - `Trap0` takes no argument, and a caller handed
the string would print a control character into whatever it logged with.

A word of another kind is simply **retyped** and keeps its spelling, which is how code
builds an assignment it did not spell out. Text is run past the reader and refused
unless the whole of it comes back as a single word; without that, `to word! "a b"`
builds a word no reader can load again and the mistake shows up in a file that will
not read back. An issue takes a laxer rule for what it may hold - `Scan_Issue` rather
than `Scan_Word` - which is what lets one carry a version number with dots and pluses.

## 87. TO TUPLE! has five sources and no two agree on length

Another tuple, a string, a block, an issue and a binary. **A number is not one of
them**, which surprises callers more than anything else here.

- A **string** is padded up to three: `Scan_Tuple` counts the dots to decide the
  length, then raises that length to three whatever it counted.
- A **block** keeps exactly what it holds, so `[1]` gives a tuple keeping one octet -
  it shows as `1.0.0` and is not *strictly* equal to a written `1.0.0`, which keeps
  three. Each item must be a whole octet already: outside 0 to 255 is refused rather
  than clamped, which is the opposite of what writing through a path does. A decimal
  rounds half away from zero, so `[0.5]` gives 1.
- A **binary** longer than twelve is **cut short** - the only over-long source that
  does not raise.
- An **issue** longer than twelve **raises**, and is read as pairs of hexadecimal
  digits, so `#010203` is `1.2.3` and an odd count of digits raises.

The last two sit next to each other in the C and still disagree.

## 88. The 2,048 surrogates are not characters, and the range test must come first

`D800` to `DFFF` are reserved for writing a large code point as a pair, so a real
Rebol refuses all of them. The range test has to happen before any narrowing to a
char, because narrowing truncates: `0x1D800` would keep only its low half and look
like a surrogate when it is an ordinary character well past them.

A code point outside what Unicode defines fails as an **ACCESS** error rather than a
script one, which is not where you would look for it. A string gives its **first**
character rather than being refused for having more than one, and a decimal truncates.

**A lone `#{80}` is code point 128 and a lone `#{81}` is refused.** `t-char.c` tests
`*bp > 0x80` rather than `>= 0x80`, so the first byte falls through to the plain
reading and the second is treated as a continuation byte with nothing in front of it.

**A word has to be called something.** Building one from empty text answers
`too-short`.

**The word carries its exclamation mark**, so `'integer!` names a datatype and
`'integer` does not.

## 89. Qualifying text for a number has four outcomes, in order

Nothing at all is `too-short` rather than a scan that failed. More than the limit is
`too-long` **before** anything tries to read it. A letter that needs more than one
byte is `invalid-chars`. Only then does the scan run. Four outcomes a script can tell
apart, decided by the text rather than by the caller.

**A number may have a line feed in front of it and not behind it.** What may come
before is `IS_LEX_SPACE` - whether the character has no entry in the lexer's map at
all, which the control characters do not - and what may come after is `IS_SPACE`,
which is space and tab alone. A line feed and a carriage return *do* have a lexer
entry, so they are the two below space a number may not sit behind. Rebol's own suite
measures that difference by building every one-character suffix that will go on the
end of a "1".

**The two length limits differ by one.** `MAX_INT_LEN` is 25 and the decimal arm
passes a literal 24, so they cannot share a constant - and the odd one out matters:
`"9223372036854775807"` with separators is twenty-five characters.

## 90. MAKE MONEY! takes seven datatypes, and the eighth was deliberately removed

A money is handed straight back. An integer, a decimal and a percent become the amount
they name - and a percent names its **fraction** rather than its printed number, so
`make money! 100%` is `$1`. A string goes through the reader. A binary is the
twelve-byte `deci` form. A logic is `$1` or `$0`, and is MAKE's alone.

**An issue is refused, and the refusal is a decision rather than a gap**: `t-money.c`
carries the case label commented out with the issue number that removed it. Writing a
money in hexadecimal reads like the obvious use for an issue, and Rebol decided
against it.

**The currency mark may follow a sign and may not precede one**, so `"-$1"` reads and
`"$-1"` does not. `Scan_Money` allows one mark and strips it, and what is left has to
be a number - which is why the conversion moves the sign across rather than looking
for a mark wherever it sits. Putting a mark on unconditionally turns `to money! "$1"`
into `"$$1"`, which lexes as nothing at all. A money's text is qualified the same way
a decimal's is, and Rebol's own suite measures that character set for both and gets
the same answer twice.

A `deci` holds twenty-six significant digits and a power of ten inside a signed byte;
past that is `overflow`.

## 91. Three more places where the obvious reading is wrong

**`make pair! 4%` is refused where `make pair! 4.0` is four by four.** Four per cent
is a proportion of something, and a coordinate is not a proportion of anything. The
same goes for a paren, which is a block by shape and a piece of unevaluated code by
meaning. A single number fills both halves; a block must hold exactly two - one is
refused rather than filled in, three refused rather than trimmed.

**`make map!` takes five things and no others** - a block, a paren, another map, an
object (turned into a block of its fields first) and a number for room. Everything
else is `Trap_Make`, which matters: a string walked one character at a time would make
`make map! "ab"` into a map of a to b, and a caller who passed the wrong thing would
never find out.

**`make typeset! [1 2]` is an invalid argument, not an empty typeset.** A block
literal holds the *words* `integer!` and `string!`, not datatype values - the reader
gives words and only evaluation turns them into datatypes - so filtering for datatype
values finds none. And an item that names no datatype has to be refused rather than
stepped over: stepping over it makes a typeset of nothing that still answers
`typeset?`, and an empty typeset is a thing a caller can legitimately ask for.

**NOW answers ten questions and only one may be asked.**
`Assert_Max_Refines(ds, D_REF(9) ? 2 : 1); // prevent too many refines like: now/year/month`.
/PRECISE is exempt because it says *how* to read the clock rather than which part to
answer. Every part is read from the **local** date rather than from the instant
underneath - `Adjust_Date_Zone(ret, FALSE)` - so within an hour of midnight the day it
answers is the local day and not the UTC one.

**A number reaching a binary is refused, not truncated**:
`if (VAL_INT64(arg) < 0 || VAL_INT64(arg) > 255) Trap_Range(arg);`. Truncating is the
silent kind of wrong - writing 300 stores 44 and answers 300, so the caller is told
the write happened as asked.

**A pair half read as a whole number rounds half *up*, not away from zero.**
`ROUND_TO_INT` is `(REBINT)(floor(d + 0.5))`, so 2.5 becomes 3 and -2.5 becomes -2.
AND, OR, XOR, EVEN? and ODD? all go through it, so there is one rule and not five.

**FIND over an image compares a pixel three ways.** A tuple of three names a colour
whatever its alpha; a tuple of four matches the alpha too; a whole number matches
nothing but the alpha. /ONLY drops the alpha from the comparison.

**Writing one image into another with a /PART that is not a shape writes nothing.** A
count cannot say which pixels of a rectangle it meant, and the C does not refuse it -
which is the one thing here nobody would have guessed.

**`bad-make-arg` carries two values**: `arg1` is the type asked for and `arg2` is what
was offered. Rebol's own suite reads the second - `e/arg2 = #{C5}` after
`to char! #{C5}` - so a caller can see the bytes rather than only being told they were
wrong.

**A set-word handed to DO on its own is an invalid argument** - there is nothing after
it to assign, so the caller has handed over half an expression.

## 92. `Scan_Decimal` is not `Double.parseDouble`, and three differences earn the port

A **comma** is a decimal point, so `"1,5"` is 1.5. An **apostrophe** is a digit
separator and is dropped, so `"1'000"` is a thousand. And a trailing **percent sign**
is allowed only when a percent is being read - that is the `dec_only` flag, and it is
the whole of why `to decimal! "50%"` is refused while `to percent! "50%"` is fifty
per cent. Only the *first* comma becomes a point; a second is then a second point,
which the grammar refuses.

An exponent may carry **no digits at all** - `"1e"` is one, because the C copies the E
into its buffer and lets `strtod` stop there.

Rebol's own suite pins the accepted characters exactly: it builds every one-character
suffix `to-decimal` will take and asserts the set is tab, space, apostrophe, comma,
full stop, the ten digits and the two spellings of E. It measures the same set for
money separately and gets the same answer twice.

**`"1#INF"` is infinity rather than a failure.** Whatever came before the hash is
thrown away - the C has already copied those digits into its buffer and abandons them
where it meets the hash. Only the sign of the very first character survives.

**`to integer! "1e5"` is refused while `to integer! "1.5e3"` is 1500.** `Scan_Integer`
fails on a whole number too large for a machine word, and the decimal scan that
follows needs a point. Odd, and it is the rule. Refusing a number outside the range
rather than saturating is the point: saturating gives a result that is a number, is in
range, and is not the number the text said.

## 93. EVEN? and ODD? disagree between a decimal and a money

A decimal **rounds half away from zero**, the same rule ROUND uses, so 1.5 is even and
2.5 is odd. Truncating instead agrees on every whole decimal and disagrees on every
half, which makes it the dangerous wrong answer rather than the obvious one.

A money **truncates**, because `A_EVENQ` in `t-money.c` reads it through `deci_to_int`
and that throws the fraction away. The two datatypes disagree on a half, and each
follows its own C.

## 94. /PART takes a position and it must be into the same series

`Partial1` decides it in three lines, and the last one is why the source has to be
passed in:

```
if (is_ser && VAL_TYPE(sval) == VAL_TYPE(lval) && VAL_SERIES(sval) == VAL_SERIES(lval))
    len = (REBINT)VAL_INDEX(lval) - (REBINT)VAL_INDEX(sval);
else
    Trap1(RE_INVALID_PART, lval);
```

A position into some other series names no length at all and is refused rather than
guessed at. Rebol's own JSON codec copies a matched run this way -
`mark1: some normal-chars mark2: (append/part output mark1 mark2)` - so refusing a
string where a count was declared stops TO-JSON on every string it is given.

**A negative count reaches back from the position**, so `remove/part tail s -2` takes
the last two characters and `uppercase/part tail s -2` raises them. The count is still
how many; only the direction changed, and the run is clipped at the head. From the
tail there is nothing ahead, so counting forward answers nothing at all.

**The count sits at a different place in each native's argument list** - APPEND takes
a value before it and REMOVE does not - so the caller says where rather than the
helper guessing.

**A /PART on a series is counted in the series' own units**, so a string is bounded in
*characters* and the UTF-8 encoding happens afterwards. Bounding the bytes instead
takes too little wherever a character needs more than one - eight characters of Czech
are ten bytes - and a count landing mid-character encodes a lead byte with nothing
following. Rebol's own MIME header encoder cuts its input into runs of seventeen
characters for the line limit and loses the last letter of every accented subject line.

**A map's /PART counts pairs**, and the halving is one line:
`len >>= 1; // part must be number of key/value pairs`. So a /PART of one asks for
half a pair and gets nothing, and a /PART of three adds as much as a /PART of two. An
odd count loses its last value for the same reason the loop drops a trailing key:
`NOT_END(val) && NOT_END(val+1)` needs both halves before it will take a step.

**A protected map refuses a call before anyone looks at what it was adding.** The C
says why on the line above: "Check must be in this order (to avoid checking a
non-series value)".

## 95. TO-HEX cuts a tuple from the left and a number from the right

A tuple is a run of bytes in the order they were written, so the size says how many
digits to keep **from the left**; a number is right-aligned and keeps its low digits.

The tuple's ceiling is twice its **own** length rather than sixteen:
`if (len > 2 * MAX_TUPLE || len > 2 * VAL_TUPLE_LEN(arg)) len = 2 * VAL_TUPLE_LEN(arg);`.
A size wider than the tuple does not pad it out - there is nothing to pad with that
would not be a different colour. A number is capped at sixteen digits whatever was
asked for.

The size is refused **before** the branch on what is being converted -
`if (VAL_INT64(D_ARG(3)) <= 0 || VAL_UNT64(D_ARG(3)) > MAX_U32) Trap_Arg(D_ARG(3));`
is the native's first line - which is why a char and a tuple are refused the same way
a number is. Nought has to be named rather than assumed: a width of nought leaves an
issue with no spelling, and there is no such value.

## 96. TO-STRING is not FORM

The two agree on every value that is not a series, which is how they come to be
conflated: `to-string [1 2 3]` is `"123"` and `form [1 2 3]` is `"1 2 3"`. Nesting
makes no difference to the running together, so `to-string [1 [2 3]]` is also `"123"`.

A **path** keeps its slashes, because it is a block underneath and the block arm would
otherwise run its segments together: `a/b` is `"a/b"` and not `"ab"`.

## 97. QUERY answers in the shape it was asked, and a field of NONE asks a different question

Four shapes, and the one that reads as wrong is the one Rebol's suite asks three
times. A **word** asks for one fact and gets it bare. A **block** asks for several and
gets a block. **`object!`** asks for everything and gets an object. **None** asks
*what may be asked* and gets the names rather than the facts.

The C splits the last two before `Ret_Query_File` is reached -
`if (IS_NONE(D_ARG(ARG_QUERY_FIELD))) { Ret_File_Modes(port, D_RET); return R_RET; }` -
in both the file arm and the directory arm.

**Whether a fact is labelled depends on how its word was written, per word rather
than per block.** A plain word puts itself in the answer as a set-word before its
value; a get-word contributes the value alone. So `query %a [type size]` is
`[type: file size: 5]` and `query %a [:type :size]` is `[file 5]`. Rebol's own
LIST-DIR asks the second form and reads the answer **by position**, so reading the
block as a plain list of field names breaks it.

A path with nothing at it answers none for every shape but the names - "there is
nothing there" is an answer a script acts on - and the names are a fact about the
*port* rather than about the file, so they come back either way. They are read off
`system/standard/file-info` at the moment of asking, which is what `Ret_File_Modes`
does in its one line; a list written out in Java could drift from the object a script
compares the answer against, and comparing them is exactly what the suite does.

## 98. ASSERT and ASSERT/TYPE are two functions under one name

Plain ASSERT evaluates the block and refuses a false result; /TYPE reads the block as
**pairs** and never evaluates it as code. The C splits on the refinement before it
looks at the block at all. Declaring /TYPE and ignoring it makes
`assert/type [x string!]` evaluate `x string!`, see a datatype at the end and pass -
and every caller of it is then unguarded, MAKE-MODULE*'s header check among them:
eight fields it is supposed to reject and does not.

**Plain ASSERT checks every expression, not just the last.**
`while (index < SERIES_TAIL(block)) { index = Do_Next(...); if (IS_FALSE(ds)) ... }` -
so `assert [true 1 + 3 = 2 true]` fails on the middle expression where evaluating the
whole block and reading its value passes on the last. What it complains with is **the
block itself**, copied from the position it started at, so a script can read back what
did not hold.

`Is_Of_Type` accepts four spellings - a datatype, a word, a block of either, or a
typeset.

## 99. A separately-encoded surrogate pair is joined, and a lone one is still an error

`#{EDA0B4EDB4A2}` is U+D834 and U+DD22 written as two three-byte sequences, which is
how a good many systems encode a character above the basic plane and is what Rebol
reads back as `𝄢`. Strict UTF-8 refuses it - a surrogate is not a character - so the
pair is **joined** and the strictness kept for everything else.

Joining rather than decoding loosely, because the two are not the same: `#{EDA0B4}` on
its own is still an error, and it would stop being one if the decoder simply allowed
surrogates through.

## 100. Three more shapes of answer

**A connection that answers an empty binary has finished and closed** - that is how a
reader knows to stop rather than waiting for ever. Bytes that arrive are **added** to
the port's DATA rather than replacing it, because a protocol reads a header and then a
body out of the same buffer and decides for itself when it has enough.

**A name that stands for no address answers none rather than failing.** "There is no
such host" is a true answer a script has to act on and will meet often; the refusal is
for the service not being granted, and that has already happened by then.

**A line that is not there answers none**, and a script must be able to tell that from
an empty line.

**IN reads an object, an error and a port alike**, because all three are a context
underneath - `IS_ERROR(val) ? VAL_ERR_OBJECT(val) : VAL_OBJ_FRAME(val)`, and the C's
comment on the argument says "object, error, port, block". Given a block it answers
the first item holding the word, reading each through `Get_Simple_Value` so that a
word naming an object counts as the object.

**A bundled module's name is the spec's HOST and nothing else.** `bundled://github.reb`
decodes to `[scheme: 'bundled host: "github.reb"]`, where a name with a slash in it
decodes to a host, a path and a target - so a spec holding either of those is asking
for something inside a directory, and this scheme has no directories. Refusing them is
what stops a name climbing out. It is refused the way a missing file is rather than
answered empty, because a caller that cannot tell "no such module" from "an empty
module" writes the second to disk, and DOWNLOAD-EXTENSION is exactly that caller.

## 101. A bad-UTF-8 refusal names the start of the sequence, not the byte that gave it away

`UTF8_Check` answers `acc + 1`: one past the last **whole** character it accepted. So
a two-byte sequence with a bad second byte is reported at its lead. An unfinished
sequence at the end counts as a failure - the C's loop ends with the decoder part way
through a character and the line after answers the position regardless.

The refusal carries **what is left from where the decoding stopped**, not the bad
bytes alone: `VAL_INDEX(arg) = err` moves the original binary to the offset, so
`to string! #{C5A1C500}` says `#{C500}` - the lead byte and everything after it. That
needs the decoder driven a buffer at a time, because the one-shot form throws away
where it stopped.

Bytes that are not text have no text form - `if (!ser) Trap1(RE_INVALID_UTF, arg)` -
and answering the replacement character instead makes a round trip through a binary
lossy without saying so.

**A byte order mark says which encoding, and the four-byte marks must be tested
first.** `FF FE 00 00` is UTF-32 little-endian and its first two bytes are the UTF-16
little-endian mark, so the wrong order reads a UTF-32 file as UTF-16 and finds a null
after every character. Text arriving from a file or a wire is as likely to be UTF-16
as UTF-8; reading it as UTF-8 regardless turns every such file into a refusal, which
is what `issue-2186` in Rebol's own tests is about.

The C's lead-byte table refuses three ranges outright: a continuation byte with
nothing in front of it; C0 and C1, which could only ever be an overlong spelling of an
ASCII character; and F5 upwards, which would decode above the last codepoint.

## 102. TO-LOCAL-FILE and TO-REBOL-FILE, and one place the C is wrong

`To_REBOL_Path` treats **both** characters as separators and neither test is guarded
on the platform - `if (c == '\\' || c == '/')`. So a Windows path converts on a
machine that has never seen Windows, which is the point of having the function: the
path came from somewhere else. Replacing only the separator *this* machine uses is the
plausible implementation and is wrong everywhere but Windows, where it happens to
agree. A second separator in a row is dropped, which is what turns the two leading
backslashes of a Windows share name into the one leading slash meaning "from the root".

`To_Local_Path` always changes the separator and collapses runs of slashes. The dots
are read **only** when /FULL asked for them: a single dot, alone or with a slash after
it, is dropped; a double dot backs out of the directory built so far and leaves a
separator behind it, so the answer ends with one.

**One divergence, and it is the C that is wrong.** A segment such as `..x` falls
through the double-dot branch into a line that writes the character it looked ahead at
and then copies the segment anyway, so the C answers `x..x`. JEBOL copies it as it
stands.

## 103. RESOLVE's /ONLY has two shapes and both are marks

The C keeps this in a bind table both contexts share, and a mark of -1 means "named,
and the source has not got it". Both shapes are marks, which is why one rule covers
them: **a marked word the source lacks is unset in the target**, where an unmarked one
is left as it was.

A block names the words outright. An **integer** is a position in the target - "an
index to tail" - and marks its words from there on, which is how the C resolves the
words a binding has just added: it records the context's length, binds, and resolves
from that length. `if (i == 0) i = 1;`, and a position past the end is nothing to do
rather than a failure.

## 104. FIND on an object is narrower than reading a name through it

Three things make it narrower, each a line of the C. Only a **plain word** asks -
`if (IS_WORD(arg))` - so a set-word or a lit-word spelled the same finds nothing. A
**hidden** field is not there:
`return (!always && VAL_GET_OPT(word, OPTS_HIDE)) ? 0 : n;`. And **SELF** is not
there either, because the search starts one slot past it:
`word = FRM_WORDS(frame) + 1;`.

An object, an error, a port and a module are all one frame of words and values, which
is why one arm of `t-object.c` answers for all of them - `types.reb` puts every one in
the `object` typeset, and that is what makes SELECT, FIND and IN work the same way on
all four.

**A sys helper is reached as `sys/make-module*`, not by a bare name.** MAKE-MODULE*
and MAKE-PORT* are not standard functions and a script has no business calling either
directly; loading the sys files into their own context is what makes the qualified
name the right way to reach them.

**Java calls Rebol's own REBOL in four places**, which is the same seam the C uses:
MAKE PORT!, MAKE MODULE!, DO of a file, and the boot. Building a port needs the scheme
registry and the URL parser, and both of those are REBOL - so OPEN cannot do its own
work.

**`/PART` accepts a count *or* a position**, and the position form is the one Rebol's
own code leans on. Declaring the argument as an integer refuses a string before the
body ever sees it, so the position form cannot be reached at all. DECOMPRESS and
SWAP-ENDIAN declare `[number! series!]` where the REMOVE family also declares a pair -
a whole datatype's worth of arguments a real Rebol turns away before the body runs.

## 105. A port whose actor is written in REBOL is not a way out of the interpreter

`Do_Port_Action` sorts three cases. **None** means the port does nothing at all. A
**word** means a built-in actor, which is a way out and needs its service granted
first. An **object of functions** is a scheme somebody wrote in REBOL, and it reaches
nothing by itself - whatever it wants, it asks for by calling ordinary words, and each
of those asks the host for itself. So a REBOL actor never reaches the service check.

Three schemes reach nothing outside and are granted unconditionally. **Checksum** and
**cipher** sum or transform bytes the script is already holding - there is no service
to ask for, and refusing them would refuse arithmetic. **Console** is the narrower
case: *opening* it reaches nothing and all it answers is how wide a terminal is, which
it gives as eighty whether or not there is one. Reading and writing through it still
ask for the grant, in the READ and WRITE natives where the data actually moves -
refusing to *open* it refuses HELP, which asks the width on its first line, to every
interpreter that was not handed a console. The **system** and **callback** ports are
queues of events inside the interpreter, and whatever put an event on one already asked
the host; refusing the system port would refuse WAIT itself.

**`Redo_Func` hands the actor's function the same arguments the action was called
with**, so PICK's key and POKE's value arrive as they were written. But the two
layouts differ in one respect that matters for REMOVE: a native hands over its
refinement arguments as ordinary positions and says separately which refinements were
asked for, while a REBOL function has the refinement *itself* as a parameter with its
arguments after it. So `remove/key store 'greeting` arrives as three values and one
name and has to leave as five.

A refinement nobody asked for brings **no** argument with it, which has to be counted
rather than assumed: the native hands over one value per refinement that *was* asked
for and nothing for the others, so the key is the second value and not the fourth.

**A url reaches an action by opening a port on the way.** `write checksum:md5 data` is
a port opened, written and left, and the C gets there because WRITE is an action - a
URL reaches `Make_Port` on its way to the actor rather than being refused before it
starts. Refusing it means a URL can never be written at all, and the error says "no
service" where the real answer is about the data.

**`file://a.txt` and `%a.txt` are the same file**, and the scheme's own INIT is what
says so: it works the path out of the url and leaves it in the port's spec.

**A socket lives in STATE, not EXTRA.** `sysobj.reb` calls STATE "internal state values
(private)" - exactly what a socket is - and EXTRA "user-defined storage of local data",
which belongs to whoever wrote the script. Rebol's own TLS keeps its entire protocol
context in EXTRA and would overwrite a socket hidden there.

**A pattern read of a directory answers bare names, not paths.** `Read_Pattern` cuts
the answer back to the last part - `dir->clen = end + 1`, "so only files are returned
and not complete paths" - and Rebol's own ZIP encoder relies on that shape. Matching
nothing is an empty block, and so is a directory that is not there, because `p-dir.c`
will not raise on a failure to open when the path held a wildcard: a caller asking for
a file by name and not finding it has made a mistake, while a caller asking which files
match has asked a question, and none of them is an answer.

**A failed open reports reason 3.** `RFE_OPEN_FAIL` is 3 and `Trap_Port` pushes it, so
the catalogue's `"reason:" :arg2` reduces to the number.

**COPY names eight datatypes and a gob is not one of them**, so `copy make gob! []` is
the wrong argument rather than an operation a gob does not support. **INDEX? names
`series! gob! port! none!`**, and declaring it matters because the declaration and the
arm refuse differently: `indexz? 5` is `expect-arg` because an integer never reaches an
arm, while NONE *is* on the list and the none arm answers for INDEX? and falls through
to `cannot-use` for INDEXZ?. GOB has to be named rather than arriving with the series,
because `boot/types.reb` gives a gob no typeset - `series? make gob! []` is false while
`index?` still answers.

**MAKE-MODULE* answers none for a header it will not accept**, and none is not a
module - `if (IS_NONE(value)) Trap1(RE_INVALID_SPEC, spec);`.

**RESOLVE walks the target**, not the source, and asks the bind table where each of its
words sits - which is why a word the source has not got is ordinarily left as it was.
A protected slot is skipped without complaint and a hidden source word is not a source
of anything. /EXTEND then adds the source words the target has not got at all, limited
the same way.

## 106. LAYOUT is defined nowhere, and `view-funcs.reb` calls it anyway

`layout` appears in neither `src/mezz` nor `src/boot`, so a real 3.22.1 has no such
function - and `view-funcs.reb:117` calls it. A block handed to VIEW therefore fails
on a word with no value, in Rebol and in the port alike, until VID is written. A
native that answers its own argument is worse than the failure it hides: a VID program
then runs, reports success and draws nothing.

**A GUI metric no host serves is refused, not answered none**, because a metric is a
number the caller is about to compute with. A none reaching
`screen/size - window/size / 2` fails somewhere else entirely and blames the
subtraction rather than the misspelling. `virtual-screen-size` proves that is not
hypothetical: it is in the word list `boot/window.reb` hands the host, so it reads as
supported, and neither host has a branch for it.

Eleven of the twelve keywords measure and answer a pair. **SCREENS counts and answers
an integer**, which is why the C writes it into the frame and returns before reaching
the code that makes a pair.

**SHOW answers what it was given**, which is the C returning `RXR_VALUE` without
touching the frame slot - and VIEW depends on it. Showing a none does nothing and
answers none, which UNVIEW depends on under a comment reading "none ok".

**INIT-TOP-WINDOW writes the screen's size onto the root gob.** VIEW centres a window
with `screen/size - window/size / 2`, so a root of the wrong size puts every centred
window in the wrong place.

## 107. CLAMP refuses mixed types and answers the lower bound when they are the wrong way round

The bounds must be the same datatype as the value and are **not converted** -
`Trap2(RE_TYPE_MISMATCH, val, vmin)` before anything else happens. So
`clamp 5 1.0 3` is refused rather than quietly treating 1.0 as 1, which is the choice
worth having: a caller who mixed them almost certainly meant one type throughout.

Bounds written the wrong way round answer the **lower** one, and that is not a check
anybody wrote - it falls out of the order the two are applied in: the inner minimum
pulls the value down to the maximum and the outer maximum pushes it back up to the
minimum.

**A tuple bound shorter than the value clamps the rest to zero**, not leaves it alone:
`REBYTE lo = i < VAL_TUPLE_LEN(vmin) ? b1[i] : 0`. A trap for anybody writing
`clamp 200.100.50 0.0.0 128.128`.

**FACTORIAL has three ranges and the C draws both lines deliberately.** Up to twenty it
fits a whole number; up to a hundred and seventy it fits a double; the next one is over
a double's largest and would silently be infinity, so it is refused until there is a
bignum rather than answered wrongly.

**DISTANCE is always a decimal**, even when the answer is whole, because a distance is
a measurement rather than a count. /TAXICAB is the sum of the two absolute differences.

## 108. The system, event and callback ports are one actor three times over

`Init_Event_Scheme` registers one actor for all of them -
`Register_Scheme(SYM_SYSTEM, 0, Event_Actor)` and the same for EVENT and CALLBACK - so
what they hold and what may be done to them is the same thing three times.

`Event_Actor` serves the block actions by pointing them at the STATE field and running
them there - `*D_ARG(1) = *state; result = T_Block(ds, action);` - so INSERT on the
system port is INSERT on its queue. **The port comes back as the answer, not the
block**, because the C saves it first and puts it back before returning. The queue is
made on demand. `if (!IS_EVENT(arg)) Trap_Arg(arg);` guards INSERT and APPEND both, so
a protocol cannot leave a note to itself among the events.

**What marks a port open is the actor's own storage** - a socket, a cipher, a position
in a file, a queue of events - so a scheme with none of those needs a plain mark, and a
scheme that has one must not have it written over. The console and checksum ports are
the two that need the mark. A *directory* port looks as though it should and does not:
opening one writes its position into STATE, so there is already something there.

**CLOSE must hand the socket back**, not merely forget it - a socket only forgotten
stays open at the far end until the process ends. A port that was never opened, or has
been closed, refuses rather than answering no bytes, which a caller cannot tell from a
quiet connection.

**A host that granted a service and supplied nothing behind it is `not_present`**, and
the reason goes into ARG1 rather than only into the message: a script that has to read
the reason out of prose cannot tell the three refusals apart, and telling them apart is
the whole point of having three. A host that granted the screen and supplied none can
be fixed by supplying one; a host that granted nothing cannot.

**PRINT reduces a block first** and joins the results with spaces, which is why
`print ["count:" count]` shows the number rather than the word.

## 109. Rebol 3.x replaced construction syntax rather than adding to it

R3-Alpha wrote it all with square brackets, as `#[none]`. Rebol 3.x moved to
parentheses and now **refuses** the bracket spelling, which is now the map literal.
Reading both accepts source a real Rebol rejects - and the bracket form is why
seventeen of the twenty-two vendored test files would not parse.

Three shapes: a word naming a value, a datatype on its own producing the datatype
value, and a datatype followed by contents to build from. Only self-contained values
can be read back - something referring to a live thing, such as a native or a host
object, cannot be reconstructed by a reader with no context to resolve it against, and
having no context is deliberate.

**Fifteen datatypes cannot be written as a construction at all**, straight off the
Make column of `types.reb`, whose header says what it is for: "Make -- It can be made
with #(datatype) method". Handing those to MAKE instead reads things Rebol refuses -
`#(char! 97)`, `#(money! 1)` and `#(integer! 5)` all become values that way.

**`#(int32! ...)` names a kind of element rather than a datatype**, so nothing that
looks the word up in the datatype table can read one, and a kind name alone is an
empty vector of that kind. `vector!` is different in both halves: it *is* a real
datatype name, so `#(vector!)` alone stays the datatype value the way `#(integer!)`
does, and it only starts a vector when a kind name follows it.

## 110. Two words are spelled out of percent signs, and a raw string is closed by a run of them

`%` is the file sigil, so a filename has to follow it. With nothing after it there is
no filename and it is **the word the modulo operator is bound to** - reading that as an
empty file makes `7 % 0` answer 0 rather than dividing by zero, because the operator
never gets a chance to be one. `%%` is likewise the word Euclidean modulo is bound to,
and reading it as a file named "%" makes `-7 %% 3` answer 3.

Past that the two part company: a name after a lone percent is a file, and a name after
two is neither - R3 refuses `%%a` as a malformed file rather than reading a word. Unless
the two characters after are **hex**, because then the second percent opens an escape:
`%%40b` is the file `@b`. A slash gets no such allowance - `%%/x` is invalid.

**A percent word may be followed by a colon or a slash**, making it a set-word or a
path segment: `o/%` and `%: 1` are both legal, so "does the word end here" is not the
same question as "can a filename begin here".

`Scan_Raw_String`'s summary is the whole point: "Scan a raw string (without any
modifications). Eliminates need of double escaping and allowes unmatched braces." A
caret is a caret, a lone brace is a brace, and a line ending is whatever the source
had. The **run of percent signs** is what closes it, which is what lets a raw string
hold the closing sequence of a shorter one: `%%{ %{^}% }%%` is one string holding
another. A closing brace followed by a *longer* run than the one that opened is a
mistake rather than content - `if (n > num) return 0;` - so the reader refuses it
rather than reading to the end of the file looking for its own terminator.

**`@bob` is a `ref!`**, a datatype Rebol 3.x added, string-like as file! and email!
are. Reading it as a word is the quiet kind of reader bug: it parses into the wrong
thing rather than failing, so nothing notices until something compares a ref against a
word. An `@` on its own is an empty ref rather than an error.

## 111. A line-feed mark belongs to the value after it, and the last one is dropped

`case TOKEN_LINE: line = TRUE;` in `Scan_Block`, and the *next* value read carries the
flag. That is what makes MOLD write a block back the shape its author wrote it.

A line feed with **no value after it is forgotten**. The C sets the flag on the last
value it emitted and then copies the block without it -
`//!!!! if (value) VAL_OPTS(BLK_TAIL(block)) = VAL_OPTS(value); // save NEWLINE marker`,
commented out and left there. So `mold load "[1 2^/]"` is `[1 2]`, and the newline a
block does write before its closing bracket comes from its *first* value having started
a line rather than from its last one ending one.

Each open level keeps its own marks, since a line feed inside a nested block says
nothing about the block it is nested in. A construct and a map literal read their
contents through the enclosing block's loop, so they have to save and restore the
pending line feed - otherwise the line feed before `#(none)` is spent on the first
thing inside the construct, which then never begins a line *and* marks position one of
the block around it.

**The reader keeps open blocks on a stack of its own rather than recursing.** Nesting
comes from the source, so it is as deep as whoever wrote the source made it; recursing
turns deeply nested input into a `StackOverflowError`, which is not something a script
could catch.

## 112. Rebol keeps no list of which datatypes have construction syntax

`Construct_Value` skips the datatype word and calls `Make_Dispatch[type]` on what is
left, so **a type has the syntax exactly when it has a maker**. Writing a list instead
and refusing the default turns away fourteen types a real Rebol reads - which stops ten
of Rebol's own test files dead, make-test.r3 at 216 of its 1,029 assertions.

Three datatypes need special handling and each for its own reason.

**A time reads one loose value where the others read the whole block.** The C hands a
maker a pointer into the block and lets it decide how far to read, so the difference
does not need saying there. `Make_Time` takes a bare integer as a count of seconds and
only reads hours, minutes and seconds from a block - which makes `#(time! 1 2 3)` one
second where `make time! [1 2 3]` is an hour, two minutes and three seconds.

**An image always reads a block**, even for a single value. `MT_Image` calls
`Create_Image` and nothing else, so a written image is always a *specification* - and
a specification refuses a size that cannot exist. Handing the maker a bare pair
instead reaches the code that makes a blank picture of a size and brings an impossible
one down, so `#(image! 1x-1)` quietly reads as a picture one wide and none tall. It
also has to hold off the generic "a series and where it stands" branch, or
`#(image! 2x2 3)` reads as a picture with a position where a real Rebol refuses the
whole construct.

**A text or bytes construct takes the value and at most a position**, and `MT_String`
says so in one condition before it builds anything:
`if (!(ANY_BINSTR(data) && (IS_END(data+1) || (IS_INTEGER(data+1) && IS_END(data+2))))) return FALSE;`.
Reading what it can instead is quiet and plausible and wrong: MAKE STRING! of a block
joins what it is given, so `#(string! "ab" 2 x)` comes back as the string `"ab2x"`.

**A construct's position clips at the bottom to the *tail*, not the head.** The C
subtracts one and compares the result as a *count* -
`REBCNT i = Int32(data) - 1; if (i > VAL_TAIL(out)) i = VAL_TAIL(out);` - so nought
becomes minus one, wraps round to something enormous, and is clipped to the tail
exactly as a number past the end is. Clamping at the head makes `#(string! "ab" 0)`
the whole string where a real Rebol gives the empty tail.

## 113. A file literal is checked character by character, and the caret is on the list

`Scan_File`'s first line is `const REBYTE *invalid = cb_cast(":;()[]\"^");` - eight
characters, and the **caret** is the surprising one, because it is an escape everywhere
else in the language. A quoted file drops five of the eight - `invalid = cb_cast(":;\"");`
- which is the point of the form: a name holding a space or a bracket has to be
spellable somehow. The caret comes off with them and becomes an escape again.

Everything else lives in `Scan_Item`: a control character is refused, a backslash
quietly becomes a forward slash, a percent sign wants two hex digits after it, and
anything in the refused set ends the read with a failure rather than a file. The order
decides the answer - the control check comes first and catches a raw tab before the
refused set is consulted, and the backslash is rewritten before the escapes are looked
for.

Taking everything up to the next space instead reads `%a^b` and `%a%2h` as files and
lets a typo become a filename.

**A run starting with `<` that holds nothing but symbol characters is a word however
it ends**, so `<>`, `<=` and `<-->` are all words. Closing with `>` is *not* the test,
and using it makes `<-->` a tag - Rebol's own lexer-test.r3 asserts that case on line
338, and getting it wrong costs the 444 assertions in that file.

**A based binary takes only 2, 16 and 64, written plainly** - a sign or a leading zero
is refused, and a real R3 complains about the *integer* rather than about the binary
for all of them, which is the clue that it reads the base as a number before it looks
at the braces. Whitespace is ignored wherever it falls and a semicolon starts a comment
to the end of the line. A body that does not fill its last byte is **padded** rather
than refused, so `2#{000}` and `16#{0}` are both one zero byte.

## 114. A quote ends a lexeme, which cuts a path in two unless three forms are held together

A character literal used as a path segment: `b/#"a"` reads as the path `b/#` and a
string beside it, where Rebol reads one path. **The same number of assertions either
way**, which is why counting them could never have found it.

A quoted file has the same shape and the same answer: `a/%"b"/c` otherwise reads as
the path `a/%`, a string, and a second path `/c`. Quoting is what puts a file in the
*middle* of a path at all - an unquoted one runs to the end, because a slash is an
ordinary character in a file name, so `a/%b/c` is a path of two.

A parenthesised group counts depth rather than stopping at the first close, because a
segment may hold a paren of its own. Judging its contents by the word's rules
truncates the lexeme at the offending character and re-reads from there, so `m/(<A>)`
becomes the path `m/(`, then a tag, then a stray close bracket.

**A date carrying a time has to be matched before the path reader sees it.** The
separator between the day and the time is a slash, so `1-Jan-2000/12:00` otherwise
reads as a path of a date and a time - and that path **molds identically to the date**,
which is how it goes unnoticed: the answer looks right, is of the wrong datatype, and
every date field read off it is none.

The offset needs its colon. A real R3 reads `+2` and `Z` as *no* offset rather than as
two hours or as Zulu. ISO 8601 is the same thing spelled differently and is a date
literal rather than a string a codec parses - a T stands where the slash does, so
`2000-01-01T10:00+02:00` is the value `1-Jan-2000/10:00+2:00` is. Its offset has no
colon and *does* count, because four digits are an hour and a minute run together.

A negative year has to be matched only to be **refused**: without that the lexeme
falls through to the path reader and comes back as `1/11/0` - a path of three numbers
that looks like the date the writer meant and is not one.

## 115. `12:34.5` is twelve minutes, and `12:34` is twelve hours

`Scan_Time` lists four shapes:

```
//    HH:MM       as part1:part2
//    HH:MM:SS    as part1:part2:part3
//    HH:MM:SS.DD as part1:part2:part3.part4
//    MM:SS.DD    as part1:part2.part4
```

A two-part time **with a fraction** is the last of those: `12:34.5` is twelve *minutes*
and 34.5 seconds.

**A percent is a decimal with a `%` after it, exponent included.** Rebol scans the
number and then looks at what follows, so anything that reads as a decimal reads as a
percent. Spelling the number out instead leaves the exponent off, and `1e18%` is
refused while `1e18` and `50%` are both fine - line 18 of Rebol's own
percent-test.r3, hiding the other thirty-four assertions in the file.

**`-$1` is one value**, not the word `-` followed by money, and the sign has to be
noticed before the lexeme reader runs because the dollar sign ends a lexeme. A money
failure names the token kind in ARG1 and its *text* in ARG2, and Rebol's money group
compares the second: `e/arg2 = "$1*$2"`. All four spellings it asserts are a money
literal run into an operator with no space, each one token as far as the reader is
concerned and none of them a number.

**`%2h` is not a file.** `Scan_Hex2` wants exactly two digits, and anything else is a
failure rather than a literal percent sign.

## 116. An angle bracket ends a number and spoils a word, and the last path segment decides which

**A number simply ends.** A path is assembled from separate tokens, so the last segment
of `a/3<` is scanned as a number and a number stops at any character that is not a
digit - which is why `a/3<` loads as `[a/3 <]`. The same without a path: `1<`, `1.0<a>`
and `1.#INF<` all end at the bracket.

**A word obeys `scanword`**, whose comment states it outright: "Allow word&lt;tag&gt;
and word&lt;/tag&gt; but not word&lt; word&lt;= word&lt;&gt; etc."

```
if (cp[1] == '<' || cp[1] == '>' || cp[1] == '=' ||
    IS_LEX_SPACE(cp[1]) || (cp[1] != '/' && IS_LEX_DELIMIT(cp[1])))
    return -type;
```

So the character *after* the bracket decides: a name or a slash means a tag or an arrow
word is beginning and the word is finished; another bracket, an equals, a space or the
end of input means somebody wrote an operator hard against a name, and that is a
mistake rather than two values. `a/3<` and `a/b<` are the same path shape and the same
bracket, and the last segment is the whole difference - Rebol's own lexer test asserts
the pair side by side.

**A hash may not be refused inside a word as a general rule**, even though a real R3
refuses `%`, `#`, `$`, `\` and a comma there. A hash is how a based number and a based
binary are written - `2#01`, `64#{...}` - so the rule has to run *after* those forms
have been recognised. Refusing it first turns `64#{` into the integer 64.

## 117. An email's escapes are bytes, and it must carry exactly one at-sign

`Scan_Email` writes out the percent rule rather than sharing `Scan_Item`, and adds one
of its own: `if (*cp == '@') { if (at) return 0; at = TRUE; }` on the way through and
`if (!at) return 0;` at the end, so **two at-signs are as wrong as none**. Nothing else
is refused - an email is not a file and shares none of the eight characters a file
turns away.

The escapes are **bytes**. `Scan_Email` writes each one into a byte buffer beside the
unescaped text and reads the whole buffer back as UTF-8 at the end, so `a@%C5%A1` is
two bytes spelling one letter. Reading each escape as a character of its own gives
`a@Å¡` - that letter's two halves each shown as though it were a letter.

## 118. Nine sigil placements are syntax failures, not lit-words

`Scan_Token` answers a **negative** token for each, and a negative token is a syntax
failure:

```
case LEX_SPECIAL_TICK:
    if (IS_LEX_NUMBER(cp[1])) return -TOKEN_LIT;   // no '2nd
    if (cp[1] == ':') return -TOKEN_LIT;           // no ':X
    if (cp[1] == '_' && IS_LEX_DELIMIT(cp[2])) return -TOKEN_LIT;   // no '_
    if ((*cp == '-' || *cp == '+') && IS_LEX_NUMBER(cp[1])) return -TOKEN_WORD;
    if (*cp == '\'') return -TOKEN_LIT;            // no ''foo

case LEX_SPECIAL_COLON:
    if (cp[1] == '_' && IS_LEX_DELIMIT(cp[2])) return -TOKEN_GET;   // no :_
    if (cp[1] == '\'' || cp[1] == ':') return -TOKEN_WORD; // no :'foo ::foo

case LEX_DELIMIT_SLASH:
    if (*(scan_state->end - 1) == ':') return -type;   // no /a:
```

None is arbitrary. A sigil names a word, and each of these asks for a word that cannot
exist: one starting with a digit, one that is itself a sigil, one that is the none
literal, one already carrying a sigil at the other end.

## 119. A date's order is decided by digit count, not by what the numbers could mean

`if (size >= 4) year = num; else if (size) day = num;`. So `2000-01-01` is the first
of January and `1-1-2000` is as well. The **last** part is read by digit count too:
three or more digits is the year as written, which makes `1-Feb-0003` the year three
rather than 2003. Two digits or fewer is a shorthand the C resolves against the year it
is *running in*, keeping inside fifty years either way - so the century a bare `96`
means is not a constant and cannot be written as one.

**A date's time must be a clock of the day.** A time on its own may be any length -
`30:00` is thirty hours and a perfectly good duration - but `3-Jan-2010/30:00` and
`3-Jan-2010/-10:00` are invalid lexemes.

**An offset written without a time gives midnight and a zone of zero.**
`1-Jan-2000+2:00` molds as `1-Jan-2000/0:00` and reads its zone as 0:00 - the offset is
consumed and not kept, because there is nothing yet to offset when it arrives.

**A zone is stored in quarter-hours, so a minute that is not one is lost, not refused.**
`+20` is twenty minutes past the hour and comes back as `0:15`; `+5` comes back as
nothing at all. And the ceiling is on the **digits** rather than on the offset they
mean: anything above 1500 is refused, so `+1545` is an invalid lexeme although
`+15:45` written with its colon is a real zone. Two spellings, two limits.

**A fraction of zero does not make a two-part time minutes and seconds.**
`if (part3 >= 0 || part4 < 0)` chooses HH:MM, and `Grab_Int_Scale` is followed by
`if (part4 == 0) part4 = -1;` - so `12:34.0` is twelve hours and thirty-four minutes.

## 120. A slash inside a file name is not a path separator

A slash is what a directory is made of, so `a/%b/c` is **two** segments and the second
is the file `%b/c` - not three segments with a file called `b`. Once a segment begins
with a percent sign the rest of the path belongs to it, unless the file is written in
quotes: `%"b"` ends at the closing quote and whatever follows is a segment of its own.
A paren holds its own the same way.

**A percent sign on its own is the remainder operator**, so `a/%` is a path whose second
segment is that word and `a/%/b` is three segments with the word in the middle. A *run*
of them is a word too, so the question is what follows the run rather than the first
sign. Getting this wrong in the generous direction turns `'%/` from the malformed path
it is into a file called `%/` - and a malformed path that quietly reads is worse than
one that is refused.

**A path segment that reads as nothing may fall back to a word only if it could be
one.** `2013/11/08T17:01Z0100` otherwise becomes the path `[2013 11 08T17:01Z0100]` with
a word on the end, where a real 3.22.1 refuses the whole lexeme - no word may start with
a digit. The same text with hyphens is already refused; only the slash sends it down
that road. A segment that reads as *several* values is a different thing: `a/3<` is the
path `a/3` and then a word.

**A based number is read unsigned and then taken as signed**, so sixty-four ones in
base two is minus one rather than an overflow. A digit the base does not have is refused
rather than quietly ending the number early.

## 121. A colonless offset rounds and a colonned one refuses

Where `+20` is lost down to `0:15`, `+5:50` is an **invalid lexeme** rather than five
and three quarters - because a caller who wrote the minutes out meant them. The
furthest either way is 15:45, which is what seven signed bits of quarter-hours reach.

**`_` is how none is written**, so it is not a word and cannot take a sigil: `'_`, `:_`
and `_:` are each a mistake rather than a quoted, read or assigned none. A real R3
reports them as invalid and names *which of the three* was being read.

**A sign and a colon make a token a time**, so `--1:23` is a malformed time rather than
the word it looks like - `Scan_Time` calls it a hole in its own comment:
`if (*cp == '-' || *cp == '+') return 0; // small hole: --1:23`.

**A carriage return followed by a line feed ends one line, and the line feed is the
character that ends it** - `LEX_DELIMIT_RETURN` steps over the line feed with
`if (cp[1] == LF) cp++` before letting the count rise once. The order matters only one
way round: a line feed followed by a carriage return is **two** lines, because only the
return looks ahead for a partner.

**A syntax error carries three fields from three places.** `Scan_Error` fills ARG1 with
the token *kind* ("word-lit", "tag", "end-of-script"), ARG2 with the token's own
*text*, and NEAR with the whole *line* it sat on. A script catching one reads those
rather than the message, and Rebol's own suite asserts on them - the money group
compares ARG2. NEAR is the whole line rather than the offending token, because that is
what a person reading the error needs: `(line 2) 1d` says where to look.

## 122. `do 'f` calls F and `do [f]` answers it

`VAL_SET_OPT(value, OPTS_REVAL)` is set on four of DO's arms and not the rest: a
function value handed over directly, a path, a word, and a get-word. So `do 'f` calls
F taking its arguments from after the DO, while `do [f]` evaluates the block and
answers whatever came out - even when that is a function value. It is the whole of why
`do 'a` where A is a function of no arguments answers "OK" and not the function.

**A call no word named clears the name first**: `if (!word) word = ROOT_NONAME;`, and
STACK/WORD answers none for such a frame. Left as it was, the frame reports whichever
word was called before it.

**A misspelled refinement must raise, in a REBOL function as much as in a native.**
Needing no *lookup* is not needing no *check*: a refinement that is not one of a
function's parameters is not a parameter it can fill. Letting those run quietly gives
`f/nope 1` answering 1 and `pad/left "ab" 5` padding on the right - and that is every
function in the borrowed library and every function a script writes. (A native with
refinements is genuinely a different native - `copy/part` takes two arguments where
`copy` takes one - so the refined form is looked up by its own name.)

## 123. A path index counts back from the position, and there is no nought

A series carries a position and a negative index counts back from *it*:
`s: tail "ab"` makes `s/-1` the last character and `s/-2` the one before. Counting runs
...-2, -1, 1, 2... so the negative side is one shorter than it looks. PICK does this
too, and a path that does not makes `pick s -2` and `s/-2` disagree about the same
series - with the path being the form a caller reaches for first.

**Two path failures live one line apart and say different things.** A value whose
datatype *can* be selected from, asked for a part it has not got, is `invalid-path` -
the path was a fair question and the answer is no. A value whose datatype has **no**
parts - a number, a word, a logic - is `bad-path-type`, because the question could
never have had an answer. Which datatypes those are is the Path column of `types.reb`,
where a dash means no handler at all; answering invalid-path for both makes `1/1`
report a missing part of an integer rather than that an integer has no parts.

**A map goes the series way, not the object way**: a key it has not got gives none,
where a name an object does not have raises. A map is asked about keys it may not
have, which is the whole difference.

**Storing under a key of none stores nothing, and the caller is not told.** Two lines
of the C, one in each layer: `if (IS_NONE(pvs->select)) return PE_NONE;` in the path
handler and `if (IS_NONE(key)) return NOT_FOUND;` in the lookup underneath. `PE_NONE`
is also what a read of a missing key answers, and nothing downstream looks at whether
the write happened - so the only evidence is that the map is the length it was.

**Writing a pair's half refuses twice, both `bad-path-set`.** The segment has to be a
half rather than the derived AREA, and the value has to be an integer or a decimal -
so a pair cannot be written into a pair's half. `bad-path-set` rather than
`invalid-path`, because the path is fine and the write is not.

**`s/field/2: other` on an array of structs has to copy bytes.** Reading such a field
gives a block, because no vector holds structs, and replacing a slot would leave the
parent unchanged while appearing to have worked. `PD_Struct` does it in its
`STRUCT_TYPE_STRUCT` arm, where it can see both that the block came from a struct field
and that a struct is being written.

**An object, a module, an error and a port share one path handler** in
`boot/types.reb`, so all four read and write their fields the same way.

## 124. A closure's body is copied and bound per call; a function's is bound once

A function's body words are bound to its declared words **once, when the function is
made**, so the outermost call is what turns them from naming nothing into naming a
slot. An inner call points the frame above it at its own, which is how a recursion
reads the innermost values without anything walking the chain at each word - the C
does walk it, `while (frame != VAL_WORD_FRAME(DSF_WORD(dsf))) dsf = PRIOR_DSF(dsf);`
in `Get_Var`.

A **closure's** frame outlives the call that made it, so its words cannot be bound to
a context that is lent and handed back. Copying per call is what keeps the names of a
call alive after it.

A function's locals context is a child of the one the function was **defined** in, so
a word the function does not name falls through to where it was written rather than to
where it was called. That is what makes a function mean the same thing wherever it is
passed.

**A call's name is read back out of the block rather than threaded through**, because
the name is a fact about the call *site* and not about the function: the same function
reached through two words is two names, and an anonymous one has none.

**MAKE OP! wraps whatever it was given**, so an operator made at runtime dispatches to
an ordinary REBOL function - the only difference from a native-backed one is where the
first argument came from, and that is decided before the dispatch.

## 125. `t/100` is nothing and `t/hours` is a mistake

`PD_Time` takes the two kinds of selector down different roads and they end
differently. A **word** that is not one of the three parts is `PE_BAD_SELECT`, which
reads as `invalid-path`; a **number** outside the three is `PE_NONE`, which reads as
none. A time's seconds are a whole number only while they are whole - once there is a
fraction the answer is a decimal.

**Writing a tuple's octet is the one place a value out of range gets in.** The set
branch of `PD_Tuple` clamps rather than refusing, so writing 300 stores 255 and writing
-10 stores 0 - every other way of building a tuple refuses the same numbers. Writing
past the end **lengthens** the tuple, and the octets skipped over were already zeros,
so setting the fifth octet of `1.2.3` gives `1.2.3.0.5`. Writing NONE cuts the tuple
short at that position and zeros what followed - the only way to shorten one.

**Writing a byte elsewhere refuses twice and on purpose.** A number too big is
`Trap_Range` - out of range. A negative one never reaches that line, fails the check
above it, and comes back `PE_BAD_SET` - the value is the wrong *thing for the place*
rather than a byte that is too large.

## 126. A path segment on a file or a string joins rather than selects

The Path column of `boot/types.reb` names a handler of their own for exactly two
datatypes: `file` for a file and a URL, and `*` - the string typeclass - for a string,
an email and a tag.

`PD_File` puts a slash in between unless the left side already ends with one, and an
empty left side gets one too, so joining onto nothing gives a **rooted** path. One
leading slash or backslash on the segment is dropped, which keeps a double slash out of
the middle. The answer takes its datatype from the left, so a URL stays a URL. A
segment that is not text is **molded**, so a number joins as its digits and a word as
its spelling - which is why `%a/length` is a file named length: a file has no path form
that asks about its own text.

**Only an email answers `/user` and `/host`** - `if (!IS_EMAIL(pvs->value)) return PE_BAD_SELECT;`.
A host half that is not there answers none; a user half that is not there is the whole
string. Writing one rewrites the storage **in place**, so every other name for the same
address sees the new one - and setting the host of an address with no `@` adds one,
which is how `e/host: %rebol.tech` turns a bare word into an address.

**A padded field is measured in terminal columns, not codepoints.** An East Asian wide
character takes two and a combining mark takes none, which is what makes a field line
up when the text is not Latin.

## 127. APPLY's refinements hold logic, never the value passed for them

```
if (IS_REFINEMENT(args)) {
    if (IS_FALSE(val)) {
        SET_NONE(val);
        while (TRUE) {          // and none out the args that follow
            val++; args++;
            if (IS_END(args) || IS_REFINEMENT(args)) break;
            SET_NONE(val);
        }
        continue;
    }
    SET_TRUE(val);
}
```

A refinement that is **off makes its own arguments none**. An ordinary argument nobody
supplied holds **unset**, which is a value the body can test - and giving an unsupplied
refinement unset instead is what stops Rebol's IMPORT: LOAD opens with
`assert/type [local none!]`, and `/local` then reads unset.

**A call rebinds a function's own names and nothing else** - its arguments, its
refinements and its locals. Every other word in the body keeps the binding it was
written with.

## 128. The binary dialect's float codes default the other way from its integers

Integer codes are **big-endian** by default, because that is the order every wire
protocol uses, and only an explicit `LE` suffix reverses one. The float codes default
to **little**-endian: `float 0.5` is `#{0000003F}` and `f32be 0.5` is `#{3F000000}`, so
a caller writing a wire protocol has to say `f32be` for the order the rest of the
dialect assumes. They also have to be named one at a time rather than by suffix,
because `double` ends in the letters that mean little-endian everywhere else and would
be taken apart as `doub`.

**A code the dialect does not know must raise, not be skipped.** A dialect that
silently ignores what it does not understand writes a message of the wrong length, and
the reader at the far end is left to discover it.

**The two variable-width schemes are not the same.** The first is seven bits a byte,
least significant first, with the top bit set on every byte but the last. VINT counts
the leading noughts of its *first* byte to say how many follow, most significant first,
and is what EBML and Matroska carry. A hundred and twenty-eight is two bytes either way
and a different two.

**Five datatypes laid in the dialect on their own mean their own bytes**, and the C
lists them in one fall-through reaching one `memcpy`: `REB_BINARY`, `REB_STRING`,
`REB_FILE`, `REB_URL`, `REB_EMAIL`. So `binary/write b [ui8 1 #{FFFF} ui8 2]` puts the
two bytes between the two. A **tag** and an **issue** are *not* among them although
they are strings, and neither is a char or a number - reading the list as "any string"
takes four types the C refuses.

**The write side resolves get-words in its outer switch**, before it has decided
whether the item is a code, a number or its own bytes - `case REB_GET_WORD: data = Get_Var(value)`.
So `:width` may name the **code** as well as the value on the write side, which the
read side does not allow.

**A set-word in a write dialect names where the writing has got to and produces
nothing**, which is how a caller writes a placeholder length, writes the body, and goes
back to fill the length in.

**A read that runs out names the code, not the byte**: `ASSERT_READ_SIZE(value, cp, ep, n)`
takes the code as its first argument, so it says `out-of-range UI8`. Rebol's own tests
compare `e/arg1` against the word.

**CROP is the only read code that changes the buffer** rather than walking it - a
protocol reading a stream keeps the buffer from growing without bound by dropping what
it has finished with.

## 129. A signed dialect field is symmetric, and an unsigned one has no floor

`ASSERT_SI_RANGE(next, 0x7F)` refuses anything outside **-127 to 127**, so `SI8 -128`
is an error although a byte holds it. Rebol's own suite asserts that for all four
widths.

`ASSERT_UI_RANGE` is a **signed** comparison against the maximum, so a negative passes
straight through and is written as its two's complement - which is why `UI8 -1` is 255
rather than a refusal. Only `UI32` has a floor, from the second half of
`ASSERT_U32_RANGE`, and it is the mirror of its ceiling rather than anything a 32-bit
word would suggest. The 64-bit codes are checked by neither, having no room left to
overflow into.

A field of a stated width silently losing its top bits is the worst failure a protocol
can have, because the message goes out well-formed and wrong - so the check happens
before the write.

**A set-word in a read dialect takes the next value *produced*, not the next code.**
`[x: AT 1 UI8]` puts the byte in `x`, because AT moves the cursor and produces nothing
to take. A set-word with nothing produced after it leaves its word exactly as it was.
The value goes into the **answer as well as** into the word - the set-word is a tap on
the way past rather than a diversion, which is what lets a caller read a length into a
word and keep reading in the same call. Several set-words in a row all take the one
value, because the C pushes them onto the stack and empties it against the value with
`while (DSP > ssp) Set_Var(DS_TOP, temp)`.

**BYTES takes an optional count and anything else there is a bad spec**, not the next
code: `if (!IS_INTEGER(next)) Trap1(RE_INVALID_SPEC);`. Reading BYTES as never taking
one makes `BYTES 2` take the whole buffer and then try to read the two as a code.

**The same run of bytes reads three ways.** A binary is the bytes; a **string** is the
bytes up to the first nought, which is how a fixed-width field in a C struct carries a
shorter name; an **octal** number is those digits in base eight, which is how a tar
header carries a file size. And ten codes spell out length-prefixed bytes - `UI8BYTES`,
`UI16LEBYTES` and so on through four widths and both orders - because a length-prefixed
field is the commonest shape in a binary protocol.

**A float field writes a float, not the number given**: `float 0.1` gets the nearest
single, and reading it back gives that rather than a tenth. That is what the field is.

**Signed bits sign-extend from the run's own width**, not the machine's -
`u = (u ^ m) - m` with `m = 1 << (nbits - 1)` - so three bits of `110` are -2 rather
than 6.

**FB is a fixed-point number with sixteen bits after the point** - `(double)u / 65536.0`
- which is what SWF and a good many other formats store an angle or a scale as.

## 130. The write side and the read side of BINARY report a bad code differently

A write reaches `Trap_Word(RE_DIALECT, SYM_BINCODE, value)` and names **the dialect**;
a read reaches `default: Trap1(RE_INVALID_SPEC, value)` and names **only the value**.
So `binary/write b [FOO 1]` is `dialect` and `binary/read b [FOO]` is `invalid-spec`,
for the same word. The same split applies when the block runs out: a write that has no
value left is `dialect`, a read is `invalid-spec` naming the code left hanging - and
Rebol's own suite pins the read side three times over, checking `e/arg1` is the word as
written (`AT`, `ATz`, `SKIP`, unfolded).

A code the dialect has no meaning for is the *same* error as an unknown one, because
the C makes no distinction: an unknown code falls off the end of its switch to the same
`goto error` a bad type reaches. Answering `feature-na` instead says the port has not
got round to it, where the truth is that no REBOL has it.

**`LENGTH` and `LENGTH?` are two different codes.** `LENGTH` reads a
certificate-style prefix - a first byte up to and including 128 is the length itself,
anything above has its low seven bits saying how many bytes carry the number, most
significant first, so `05` is five and `82 09 18` is 2328 and consumes three bytes.
`LENGTH?` consumes nothing and answers how many bytes are left. Treating them as one
code puts every field of every DER structure at the wrong offset.

**A read past the end must raise, not pad.** `ASSERT_READ_SIZE` raises `out-of-range`
rather than padding with noughts, because a field that is not all there is not the
number it would look like. The length-prefixed runs need the same check, and a real
3.22.1 **does not have it** - `binary/read #{02CA} 'UI8BYTES` answers `#{CA00}` there,
reading whatever the allocator left after the tail. The C's own `ep` is the tail rather
than the capacity, so the check is the intent and the missing one is the slip.

A count **below** nothing is past the end too. The C reaches that by arithmetic rather
than by a test - `n` is a `REBCNT`, so minus one arrives as four thousand million and
fails the same comparison - and the answer is right however it got there.

**A negative number has no VINT form, and the C hangs rather than saying so.**
`while (value >= (1ULL << (7 * count))) count++` shifts by seventy once the count
reaches ten, which is undefined in C and in practice never leaves the loop. Refusing is
the only answer that terminates.

**SKIP's count is unsigned, so a negative is not a step backwards** - it is a step of
four thousand million bytes, and it runs off the end.

**AT counts from one and `AT 0` raises.** `ASSERT_INDEX_RANGE` refuses rather than
settling for the head, because a caller who wrote 0 has muddled the two conventions and
clamping writes their bytes in the wrong place. Past the end is *not* an error - the
series grows to meet it.

**A binary laid in a read dialect is a test, not a field.** `[#{0bad} #{F00D}]`
answers true then false and has moved by **two**, not four - the failed match leaves
the cursor where it was for the next rule to try.

**ALIGN on a byte boundary does nothing**, so ALIGN twice running is ALIGN once.

## 131. MS-DOS packed a clock into sixteen bits, and ZIP still carries it

Five bits of hour, six of minute, five of seconds - which is why the seconds are
**halved**: thirty-two values have to cover sixty. The format has a two-second
resolution, so 21:23:55 comes back as 21:23:54.

The date is seven bits of year counted from **1980**, four of month, five of day. That
is the whole of why a ZIP written before 1980 cannot say so, and why 2107 is the last
year with a spelling of its own - 2108 is written the same as 1980, and there is
nowhere else for the eighth bit to go.

Neither field has room for an offset, so **a date carrying one is written as the
instant it names**: half past midnight an hour ahead goes in as half past eleven the
evening before, day included. Rebol gets there without a line in the dialect because it
stores a date in UTC already.

`ENCODEDU32` differs from `ENCODEDU64` in one line - `ASSERT_U32_RANGE(next)` then
`u = (u64)VAL_UNT32(next)` - and the range is **symmetric about nought** rather than a
32-bit word's, because the second half of the check is against `(i64)0xFFFFFFFF00000001`,
which is -4294967295. So -1 is the largest number the code can carry and -4294967295 is
one.

## 132. The three power-of-two bases pad differently, and base 64 will not

**Base sixteen** primes its accumulator on an odd length - `if (len & 1) count = 1;` -
so `debase "123" 16` is `#{0123}` rather than an error. **Base two** pads with leading
zero bits - `count = len & 7; if (count) count = 8 - count;` - so `debase "01" 2` is
`#{01}` and nine bits is `#{0002}`.

For both, **the length tested is the whole input, spaces included**, and the spaces are
then stepped over without counting. That is why `debase "12 34" 16` *fails*: five
characters is an odd length and only four are digits, so the last nibble has no
partner. The skipped characters are exactly four - space, line feed, carriage return
and the end-of-file byte - which is `lex > LEX_DELIMIT_RETURN`; anything else that is
not a digit stops the decode.

**Base sixty-four will not decode a partial group.** Four digits are three bytes and
there is no padding rule that lets three stand for two, so `debase "YWJ" 64` is an
error where `debase "123" 16` is a number. The equals signs are how a short last group
is written: one after three digits ends with two bytes, two after two digits end with
one. A single equals after *two* digits is refused, because the C looks ahead for the
second.

**URL-safe decoding is the exception the C makes for itself.** It is allowed to end a
group short, and meeting a `-` or a `_` while reading the plain alphabet switches to
the safe alphabet and **starts the whole decode again**.

**Base 36 and base 85 are one big number**, not fixed groups - the digits are its
remainders. A leading zero octet would be lost that way, so the count of them is
written first as a digit of its own.

**A space under /URI becomes a plus, unless the escape character is an equals sign**,
in which case an underscore.

**`dehex "100%"` is `"100%"`.** Two hexadecimal digits are required, and an escape
character with anything else after it stands for itself - a URL that was never encoded
has to survive being decoded.

**Percent encoding is octets in and octets out, never codepoints.** A character taking
two bytes in UTF-8 takes two escapes, because what a URL carries is bytes - and an
octet the set allows through is written back as itself, which only bytes can hold.
Building a string instead reads every kept octet as Latin-1, so a set wide enough to
pass an accented letter turns it into two.

## 133. A string and the binary holding its bytes hash differently

`Hash_String_Value` mixes **one byte at a time, lowering each byte on its own** as
though it were a whole character - so only the letters that encode to a single byte fold
their case at all. That is why a real Rebol answers differently for `"é"` and `"É"`
while answering the same for `"a"` and `"A"`, and why `"é" = "É"` is false there.

`Hash_Binary` goes **four bytes at a time** and is case sensitive, because a binary has
no cases to fold. So the same bytes mix to different numbers depending on which
datatype holds them.

**Which hashes exist is the host's business**, which is why R3 fills
`system/catalog/checksums` from `Init_Crypt` rather than writing it in `sysobj.reb`.

## 134. `system/catalog/compressions` is where the method names come from

A method REBOL has and a build has not is **`feature-na`**, not `invalid-arg` - a name
nobody has heard of is a bad argument, a real method the build was not compiled with is
a feature that is not available, and REBOL has an id that says exactly that. Rebol's own
suite is written for both: each group opens with
`either error? e: try [compress "test" 'lzw]` and accepts `feature-na` as the whole
answer, because a build without the algorithm is an ordinary build rather than a broken
one.

The names have to be the catalogue's. Brotli is **`br`** there, not `brotli` - getting
that wrong makes the one method the suite asks about by name answer `invalid-arg` where
the suite was waiting to be told the build has not got it.

**Three methods take empty input as empty output; the rest refuse.** CRUSH and LZW both
open with a header - a length, a symbol width - so nothing at all is data that ends
before it starts, and both say `bad-press`. The deflate family has no header to be
missing, and `decompress #{} 'zlib` is `#{}`.

**DECOMPRESS/SIZE is taken two ways.** CRUSH applies the limit *as it decodes*, because
its header says how long the answer will be; the deflate family has no such header, so
the answer is cut afterwards.

**The deflate level clamps rather than refusing**, and a call with no /LEVEL arrives as
the same out-of-range value: `if (level > 12) level = 12;` over an *unsigned* level, so
a negative one and a huge one both come out as the slowest.

**Gzip's ninth header byte is decided by the level**: `if (level < 2) xfl |= FASTEST; else if (level >= 8) xfl |= SLOWEST;`.

## 135. CLOAK is three steps and the order is the whole algorithm

The C's own summary: "Simple data scrambler. Quality depends on the key length." It is
not presented as a strong cipher.

Decoding runs the chain **backwards first**; both directions then flip the first byte
against a sum of all the others; encoding runs the chain **forwards last**. That middle
step is why a one-byte binary still changes.

The real key is **twenty bytes**: the SHA-1 of the given key, cycled to twenty. So a
one-byte key and a twenty-byte key are equally long by the time the scrambling starts -
"quality depends on the key length" is about the entropy, not the byte count.

## 136. ICONV takes a Windows codepage number as readily as a name

`src/core/u-iconv.c` carries a 372-row table because `iconv data 28592` is ISO 8859-2
and `iconv data 65001` is UTF-8. A number is a name the JVM can never resolve on its
own, and it is the form most of Rebol's own tests use.

Rebol's table lists a hundred and thirty-five character sets no JVM ships, mostly
EBCDIC and the Mac scripts. Those stay unresolvable: a host that has not got an encoding
should say so rather than guess a near one.

**An encoding named outright keeps a leading `FEFF`.** Naming the encoding says which
way round the bytes are, so a mark at the front is a zero-width space rather than
something to obey and drop. Rebol keeps it and the JVM's UTF-32 decoders throw it away -
a character's difference in the length of every such string.

## 137. PNG's Paeth tie-breaks are ordered, and the order is observable

`if ((pa <= pb) && (pa <= pc)) return a; else if (pb <= pc) return b; return c;` - left
first, then above, then above-left. **Equal distances are common in flat colour**, so
reordering the comparisons passes a careless test and corrupts a real image.

The line above the first is treated as zeros, which is what makes the first line encode
as itself, and every subtraction is modulo 256, which is what makes it reversible
without carrying a sign.

With a **named** filter the lines are bare. **Without** one, each line opens with a byte
naming its own filter, which is how a PNG stores it - so the two forms take different
widths and the type byte is the difference.

**SWAP-ENDIAN leaves a short tail alone** rather than partly reversing it, because half
a swap is not a smaller swap. Two, four or eight and nothing else.

---

## 138. A crypt port pads with noughts and nothing takes them off again

`Crypt_Actor` in `p-crypt.c`. Bytes go in a write at a time and come out when there are
enough of them, because a block cipher cannot answer until it has a whole block. What is
left over waits inside the port for the next write to complete it, so a caller reading a
stream never has to know the cipher's block size.

**UPDATE is how a caller says there is no more input coming**: it completes a
half-finished block by padding it with noughts. **Noughts and not a counted padding**, so
the padding cannot be told from data and nothing takes it off again - eight bytes through
and back are those eight bytes followed by eight noughts.

**READ empties the port.** The opposite of the checksum port, which answers the same
digest every time because a sum has no length. A crypt port is a conveyor: what has been
read has left.

**The catalogue's order is a choice.** `codec-safe.reb` falls back to
`first system/catalog/ciphers` when none of the four it prefers is there, so the first
name matters. Forty-two ciphers, in the order a real 3.22.5 lists them.

---

## 139. Two crypt fields are set without restarting the cipher, and every other one restarts it

The C assigns `ctx->tag_len` and `ctx->aad_len` and restarts nothing, where every other
field sets the state back to needing initialisation. So **a tag length set between two
writes keeps both blocks, and a starting vector set between them throws the first away.**

Starting again is the point of it: a chaining mode carries state from block to block, and
that state has to be thrown away between messages or the second message decrypts to
nothing. Rebol's own test resets the vector between messages and says why - "must reset
IV, because it was changed internally".

**A value the field cannot hold answers false and changes nothing**, which is what lets a
script offer a cipher and fall back when the build has not got it. **A key may be text
where a starting vector may not**, because the key's arm in the C accepts a string and the
vector's accepts only a binary.

**A cipher that would not run is remembered rather than raised.** `A_READ` checks
`ctx->error` before it answers anything - `if (ctx->state != CRYPT_PORT_HAS_DATA ||
ctx->error) return R_NONE`. A caller reading a stream is already looping until something
comes out, and a cipher that cannot run is a stream that never produces.

---

## 140. ChaCha20 holds everything below a block and then takes all of it

A block cipher takes whole blocks and holds the rest. **ChaCha20 is a stream cipher REBOL
gives a block of sixteen anyway**, and the C returns early while `len < blk`, then its
ChaCha20 arm consumes the whole input rather than a whole number of blocks.

So four bytes written and taken come back as sixteen, and **twenty-one bytes come back as
twenty-one, not as sixteen and a remainder.**

---

## 141. The three authenticated modes each take their header differently

**Galois counter mode and counter with CBC-MAC take the header off the front of one
write**, told apart by a length the caller set beforehand:
`if (ctx->state == CRYPT_PORT_NO_DATA && ctx->aad_len)` acts only while nothing has been
enciphered yet. **A write shorter than the header is discarded whole**, with no error a
caller can see. That loses data, and it is what a real 3.22.5 does.

**ChaCha20 with Poly1305 takes its header as a whole write of its own.** The first write
*is* the header, it produces nothing, and its first eight bytes are folded into the tail of
the starting vector - which in TLS is a record's sequence number giving that record a nonce
of its own. **Reading puts the port back to wanting a header**, so a second record can go
through the same port; taking the tag does not, because a tag belongs to the message that
has just ended.

**Counter with CBC-MAC answers everything at the write, tag included** - "The tag is
computed immediatelly, so no need to finish CCM", and `Crypt_Update` returns without doing
anything. So TAKE and READ answer the same thing here. It also checks the tag while
deciphering rather than handing it back, so a message whose tag disagrees leaves the port
with nothing at all instead of plain text nobody vouched for.

**A Galois tag is appended, not put in the buffer's place.** `Extend_Series(bin,
ctx->tag_len)` and then `SERIES_TAIL(bin) += ctx->tag_len`. Which only shows when nothing
read first: Rebol's own test reads and then takes, so the cipher text has already left and
the take answers a tag alone. A port that replaced the buffer would pass that test and lose
the message for anybody who only took.

**A tag length outside four to sixteen is a port with no answer**, not a shorter or a
longer tag: `mbedtls_gcm_finish` refuses it, the failure is remembered, and reading answers
nothing from then on. **A tag length of nought computes nothing and leaves the port exactly
as it was**, so a TAKE straight after a READ answers none rather than an empty run of
bytes - the one place an empty append would be wrong, because a WRITE in this mode marks
the port as having data whether or not any bytes came of it, and an empty message really
does read as `#{}`.

---

## 142. Rebol runs two comparison functions, and they approve different pairings

`Compare_Values` in `n-math.c` is what the ten comparison natives reach. `Cmp_Value` in
`f-series.c` is what FIND, SELECT, SWITCH, SORT, UNIQUE and the object field walk reach.
**They do not agree, in both directions.**

`Compare_Values` approves five pairings before it compares anything: the numbers with each
other, a time with the non-money numbers, a character with an integer, any word with any
word, any string with any string. `Cmp_Value` approves two, on one line:
`if ((ANY_NUMBER(s) && ANY_NUMBER(t)) || (ANY_WORD(s) && ANY_WORD(t)))`.

So the same two values get different answers depending on which one asked:

```rebol
print [mold equal? "a" %a      mold equal? ["a"] [%a]]    ; #(true) #(false)
print [mold equal? 0:0:1 1     mold equal? [0:0:1] [1]]   ; #(true) #(false)
```

Both confirmed by running them. **A time is a number to `Compare_Values` and not to
`Cmp_Value`**, which is the whole of the second line.

**The decimal allowances differ too, and there are three of them.** `CT_Decimal` allows
`=` twenty-one steps of the floating point representation, with the C's own comment beside
it: "there was 10, but 21 is the minimum to have: (100% // 3% = 1%) == true". An allowance
of ten passes every other decimal assertion in Rebol's suite and fails that one, which is
how the wrong number survives being tested. `Cmp_Value` sends both decimals to
`Eq_Decimal`, which is `almost_equal(a, b, 10)`, **on every path** - its decimal branch does
not read the one flag it is told.

So ten steps is what a decimal inside a block gets whether EQUAL?, EQUIV? or `==` asked,
and it is also what FIND is looking for. **The nested answer disagrees with the plain one in
both directions**: `[1.0] == [1.0000000000000022]` is true where
`1.0 == 1.0000000000000022` is false, and `=` is tighter inside a block than outside it.
Deriving either from the other is wrong whichever way round it is derived.

---

## 143. The coercion table is not symmetric, and `<` is worked out as "not >="

The C's switch is on the **left** value's datatype. A character against an integer takes the
character's branch and folds both sides' case; an integer against a character takes the
integer's branch and folds nothing.

```rebol
print [mold #"A" = 97    mold 97 = #"A"]    ; #(true) #(false)
```

Both confirmed by running them. Making the table symmetric breaks this.

**Only two of the four ordering natives ask a question of their own.** `>` asks strictness
-2 and `<=` negates the same answer; `>=` asks strictness -1 and `<` negates it. So `a < b`
is worked out as "not (a >= b)", **which is why `<` raises on the pairings `>` raises on.**

**A failed coercion parts company on one line.** Asking whether a character equals a string
answers false; asking whether it is below one raises `invalid-compare`. The C tests
`strictness > 1` to decide whether to coerce at all and `strictness < 0` to decide whether a
failed coercion raises, so the numbering of the six strictnesses is the behaviour rather
than decoration.

**The second way into `invalid-compare` needs no coercion at all**: a datatype whose `CT_`
function ends in `return -1` refuses the ordering question. Read from the typeclass column
of `src/boot/types.reb` and then from those functions: unset, end, none, logic, bitset,
map, typeset, object, module, error, port, task, frame, image, and every function-like
datatype. An error is an object as far as that table is concerned, and so is a module and a
port. **Two logic values reach it**, which surprises people.

**A number meeting a money takes the money's currency.** Rebol's `deci` holds no currency
at all, so a comparison cannot see one, and `USD$1 = 1` is true.

---

## 144. A pair orders on x first, and its halves are compared by subtracting

`Cmp_Pair` does it in two lines: order on the first half, break the tie on the second. So
`<` is a total order over pairs after all - **`1x2 < 2x1` is true because the x halves
decide it before the y halves are looked at.** Comparing both halves and requiring both to
agree gives false, and was what JEBOL did until the C was read.

**Each half is compared by subtracting and taking the sign**, not by an IEEE compare, and
the difference is not academic:

- Subtracting makes a negative zero equal to a zero, so `-32767x-32767 % -32767` equals
  `0x0` although it molds as `-0x-0`. An IEEE compare puts -0.0 below 0.0 and answers that
  they are two different pairs.
- It makes two infinite halves equal, because the difference is a NaN and a NaN is neither
  above nor below zero. That is what lets `p = p` hold for a pair built out of 1e300.

**A block is ordered by its items, not by its text.** `Cmp_Block` walks the pairs with
`Cmp_Value` and returns the first difference; the block that runs out first is the lesser
one. Molding both and comparing the strings looks like the same thing and is not: a decimal
molds to fifteen significant figures, so `[1.0]` and `[1.0000000000000024]` come out as the
same text and neither one is above the other.

**`[1] >= [1.0]` holds**, because the ten-step allowance sits in front of the ordering
inside a series: two numbers either of which is a decimal are in no order at all while they
sit within ten steps of each other.

**A counting vector against a measuring one is refused rather than answered false.** There
is no reading of `#(i64! [1]) = #(f64! [1.0])` that is not a guess, so `Compare_Vector`
makes the caller choose.

---

## 145. Objects, maps and dates each compare by something other than their contents

**Two objects are equal when they declare the same fields holding equal values, and the
hidden fields are counted rather than compared.** A hidden field has no name and no value to
compare and is still there, so two objects with the same visible fields and different hidden
ones are not equal.

`Equal_Object` compares each field with `Cmp_Value` and not with `Compare_Values` - the C
carries a comment saying it ought to. The difference shows on exactly one case, because
`Cmp_Value` reads a money's first eight bytes as a whole number and gets the right answer
for `$1` by luck rather than by rule. Above mode one, `Equal_Object` is handed `mode > 1` as
its case flag and `Cmp_Value` reads that flag as "stop coercing".

**Two maps are equal by keys and values, and order does not count**, because a map is not a
series and the pairs came out of a hash. The keys are already the same whichever sigil they
were written with - a map stores `c:`, `c` and `'c` as one key. EQUAL? does not mind case,
so a map holding `"a"` equals one holding `"A"`; reaching for the host language's own map
equality minds it, and made two maps unequal that a real 3.22.1 calls equal.

**`CT_Date` above mode one compares the packed date word and the time, and the packed word
carries the zone.** So the zone counts for `==` where it does not for ordinary equality, and
a date written without one is at zero rather than at no zone at all.

**SAME? answers by where, not by what.** `VAL_SERIES(a) == VAL_SERIES(b)` is the whole of
mode three for a series, and a map and a bitset are series underneath. An equality that
reads the contents instead makes a map the same value as its own copy.

**Three comparisons disagree about decimals and no two of them agree on both cases**, all
read from `CT_Decimal`: loose `=` asks whether two values are the same number, so both zeroes
are equal and so are two NaNs; `==` compares the bits and excludes a NaN by name, so the
zeroes are not equal and neither are the NaNs; SAME? compares the bits with no exclusion,
which makes two NaNs the same value and the two zeroes different ones.

**SORT's default order is numbers by size, dates by the instant they name, everything else
by its text**, with case folded unless `/case` was asked for. Dates need their own line
because their written form does not sort into their order at all: as text, `9-Jan-2000`
comes after `10-Jan-2000` and `1-Jan-2000` comes before `2-Feb-1999`.

**`VAL_CHAR` reads the low bits of whichever value it is handed**, so an integer beside a
character is simply read as a code point with no conversion and no range check.

---

## 146. A pixel always reads back as four parts, and what you write decides which half survives

An image takes more kinds of selector than any other series. A number or a pair names a
pixel; a word names either the shape or a channel read out as a binary. The pixel forms are
the same idea as a block's position and the word forms are not, which is why the image's
handler runs before the general series one.

**Writing an integer to a pixel sets the alpha and keeps the colour**:
`*dp = (*dp & 0xffffff) | (n << 24)`. **Writing a tuple sets the colour, and sets the alpha
to opaque unless the tuple carried a fourth part** - `Set_Pixel_Tuple` ends
`if (VAL_TUPLE_LEN(tuple) > 3) dp[C_A] = tup[3]; else dp[C_A] = 0xff;`, so a three-part
colour written over a half-transparent pixel makes it solid. A value that is neither an
integer nor a character below 256 is `invalid-arg` rather than a bad path set - the path is
fine and the value is not.

**A pixel always reads back as four parts, alpha included.** `Set_Tuple_Pixel` writes
`VAL_TUPLE_LEN(tuple) = 4` before it writes a byte, so a white pixel is `255.255.255.255`
rather than `255.255.255`, and a script comparing `img/1` against a three-part colour never
matches. This looks like a bug and is not.

**A pixel's own bytes can be written by number** - `img/1/2: 100` is the green byte. The C
added it for one reported issue and guards it tightly:
`if (!IS_END(pvs->path+1) || n < 1 || n > 4) return PE_BAD_SET;` - so a third path segment
is a bad set too. **The value is checked before the channel is**, and a value that is not a
byte is `PE_BAD_ARGUMENT` where a channel outside one to four is `PE_BAD_SET` - two
different errors from one line apart. JEBOL raises a bad path set for both, which is a known
divergence.

**A pixel outside the image reads as none and writes as a bad set.**
`if (val) return PE_BAD_SET;` is what the C says when the index is out of range and there is
something to write.

**A pair selector is a coordinate**: `n = ((y - 1) * wide + (x - 1)) + 1`. A decimal is
truncated and a logic answers the first pixel or the second, the same rule every series
position follows.

---

## 147. An image's word fields read from the position and each fill differently

Twenty-one words, in three groups. **SIZE, WIDTH and HEIGHT describe the whole image**
rather than what is left from the position. The colour words answer a binary of the
**remaining** pixels - `Color_To_Bin(QUAD_HEAD(nser), src, len, sym)` where `src` is
`VAL_IMAGE_DATA`, from the position and not from the head. Anything else is `PE_BAD_SELECT`,
which reads as `invalid-path`.

**RGB and COLOR are the same setter and leave the alpha where it was.**
`Fill_Line(..., only)` with `only` true masks it off, so a four-part tuple written to either
of them loses its fourth part. The four-letter spellings - RGBA, BGRA, ARGB, ABGR and their
O-spellings - set all four.

**A binary or a single-byte vector written to a colour field is a picture's worth of
colours, not one colour**, laid over the pixels in the order the field names, stopping at
whichever of the two runs out first. The C tests for the two together:
`(IS_VECTOR(val) && VAL_VEC_WIDTH(val) == 1) || IS_BINARY(val)`.

**LUMINOSITY and GRAY from a number write all four channels including the alpha** -
`Fill_Line(..., TO_PIXEL_COLOR(n, n, n, n), FALSE)` - where the same field written from a
run of bytes gives each pixel its own grey and leaves the alpha alone.

**Reading them back, LUMINOSITY and GRAY are two different sums**, not two names for one:
LUMINOSITY weights the channels the way the eye reads them and GRAY simply averages them.
Both ignore the alpha and both truncate. The C knows a third, LUMA, weighted for the older
television standard, but only the encoder can reach it and a path saying `/luma` is an
invalid path.

**OPACITY is the opposite of ALPHA, not another name for it.** `*bin++ = 255 - rgba[C_A]`
reading and `rgba[C_A] = 255 - *bin++` writing. A fully opaque pixel has an alpha of 255 and
an opacity of nothing, which reads backwards until you notice R3 stores the byte as
transparency.

**COLOR read is every remaining pixel averaged into one.** `Average_Image_Color` sums each
channel over the pixels from the position and divides by how many there were - integer
division, no rounding. **An image with no pixels left answers transparent black**, which the
C's own comment is unsure about and does anyway.

**SIZE set reshapes without moving a byte**, the height being however many whole rows the
pixels there make.

---

## 148. A date has fourteen parts, and three of them are not the datatype you would guess

`PD_Date` in `t-date.c`. Fourteen names, and a number names one of them by position:
`sym = SYM_YEAR + Int32(arg) - 1`, checked against `SYM_YEAR .. SYM_JULIAN`. The order is
year, month, day, time, date, zone, hour, minute, second, weekday, yearday, timezone, utc,
julian. **`week` and `isoweek` sit between YEARDAY and TIMEZONE in `words.reb` and are
commented out there**, so they take no positions and the numbering runs straight past them.

**SECOND is a whole number until there is a fraction, and then it is a decimal**:
`if (time.n == 0) num = time.s; else SET_DECIMAL(val, (REBDEC)time.s + (time.n * NANO));`.
Code comparing it against a whole number is right until the first fractional second reaches
it. (The same is true of a time's own seconds - entry 125.)

**JULIAN is always a decimal and it counts from noon.** `Gregorian_To_Julian_Date` gives a
date carrying no time twelve hours before it starts -
`if (secs == NO_TIME) { time.h = 12; // Julian date is counted from noon }` - and the
conversion then adds twelve again, so a bare day comes out a whole number. Where there **is**
a time it is converted to universal time first, so the answer moves with the offset while
the date part of the same value does not.

**Every clock part of a date that carries no time reads as none rather than zero**, because
a day names no instant to read a clock off. And `return (val) ? PE_BAD_SELECT : PE_NONE;` -
**a read answers none and only a write refuses**, so asking a date for a part it may not have
is an ordinary question.

---

## 149. ZONE and TIMEZONE name the same offset and mean opposite things

A date is a value and not a series, so `d/zone: 2` replaces what the word holds rather than
changing a date in place.

**ZONE keeps the clock and changes what it is an offset from.** `1-Jan-2000` becomes
`1-Jan-2000/0:00+2:00` - midnight, in a place two hours ahead.

**TIMEZONE keeps the instant and moves the clock to suit.** The same date read in a place
four hours ahead is `1-Jan-2000/2:00+4:00`. It moves the clock by the **difference** between
the offsets, so the two agree only where that difference is nothing - setting the offset a
date already has. On a date with no offset they still differ, because going from none to two
hours is a change of two.

**A number naming an offset has always meant hours**: `d/zone: 2` is two hours and
`d/zone: 2:30` is two and a half. **The ceiling is fifteen hours and three quarters either
way**, which is what seven signed bits of quarter-hours reach and the same ceiling the lexer
applies to a written offset - so `d/timezone: 16` is `out-of-range` rather than wrapping
round to the other side of the world.

**Writing a clock part to a bare date starts the clock rather than refusing**:
`if (secs == NO_TIME && ((sym >= SYM_HOUR && sym <= SYM_SECOND) || sym == SYM_TIME ||
sym == SYM_ZONE)) { time.h = 0; ... }`, so `d/hour: 2` on a bare date makes it two in the
morning.

**`d/time: none` takes the zone away with it** - `if (IS_NONE(val)) { secs = NO_TIME; tz = 0; }`
- a date without a clock naming no instant to offset. **`d/utc:` takes the whole date and
calls its zone nothing**, and **`d/date:` takes the day from another date and keeps this
one's clock**.

**A month or a day outside its range rolls into the next one** rather than failing, which is
`Normalize_Time` and `Date_Of_Days` running over the numbers the C has just written - so
`d/month: 13` is January of the year after.

**A part that exists but cannot be written is `bad-field-set`; a word that is no part at all
is `invalid-path`.** Two errors because they are two different mistakes - one is asking for
something impossible, the other is a typo.

---

## 150. DELECT places arguments by type, not by order, and four of its rules are the opposite guess

`u-dialect.c`. DRAW, EFFECT, TEXT and REBCODE are all read this way, because
`system/dialects` holds one object per dialect and each object's fields are its commands.
Writing a reader for DRAW alone would have been writing the first of four.

**A command declares the types of its arguments rather than their order**, and each argument
goes to whichever slot will take it. `cmd 3 a@b` answers `[cmd a@b 3]` - neither argument
moved to where it was written, both went to where they fit. That is what lets a dialect read
as a description rather than as a call.

Four rules are the opposite of the obvious guess, and every one was settled against a real
3.22.1 rather than reasoned about:

- **The dialect's first field is its default command whatever it is named.** The C reads
  `FRM_WORD_SYM(dialect, 1)` rather than looking for a word, so `system/dialects/draw`
  defaults to `type-spec`.
- **A fraction in a whole number's slot is cut down, not rounded.** A slot makes exactly two
  conversions - integer where a decimal was written and decimal where an integer was - and
  no others. They are what make a dialect writable by hand: nobody typing `line-width 2`
  wants to be told it should have been `2.0`.
- **The answer is padded to whichever is longer, the slot count or the number of arguments
  written.** `if (dia->len > size) size = dia->len;` sizes the output by whichever is bigger
  before any of it is filled in, so writing more than a command takes makes a longer answer
  rather than an error.
- **An argument no slot will take stops the command where it stands without raising**,
  leaving the input pointing at it.

**A command runs until the next command, not until its slots are full**, which is why a
dialect needs no punctuation between commands. **A keyword does not end one** - a keyword is
a field holding none rather than a block, its index comes back negative, and the sign is what
stops it ending the command before it. So `spline 1x1 2x2 closed` finishes with its option
word.

**Three kinds of slot.** A plain slot holds one value of a type. A repeater holds as many of
its type as were written in a row. A named slot holds one word and only its own word, and
reaching one moves the search past itself, which is how a dialect's option words are told
from its ordinary ones. Every other kind moves the search on only when it was the one being
looked at, which lets an out-of-order argument reach a later slot without closing the ones
before it.

**A word the dialect does not know stands for whatever it holds; a word it does know is left
alone**, or every option word would have to be a defined variable. A paren is evaluated and a
path is followed, so a dialect can be written with variables and computed values. **A word
naming nothing stops the command rather than raising.** The lookup follows the word's own
binding first - `Get_Var_No_Trap(val)` - which matters for a draw block, since that is a
gob's content and may be read long after and far from wherever it was written.

**A slot may be declared with a typeset somebody made themselves**, not only with the ones
the language ships: a word that is neither a datatype nor a typeset literal is looked up,
because that is what a typeset name is - `any-string!` is a word bound to a typeset value
rather than a spelling anybody parses. The C does the same, `Get_Var_No_Trap(fargs)` then
`IS_TYPESET(temp)`. Rebol's own test needs it: its one command takes `any-string!` and
expects a string, a tag, a url and an email all to land there.

---

## 151. The four whole-image operations ignore the position, and the blur reads past the picture

`n-image.c`. PREMULTIPLY, BLUR, RESIZE and the image DIFFERENCE all work on the whole image
rather than from its position, which the C flags in a comment of its own: "All pixels are
modified even when the input image is not at its head!" So an image standing at its third
pixel is still blurred, premultiplied and compared from its first.

**Three of the four change the image they were given and answer it back**, so a caller
holding the value sees the change. RESIZE is the exception, because the old image is the
wrong size to hold the answer.

**They reach width times height pixels, not however many the image holds.** An image whose
last row is partly filled has pixels past its final whole row - three in a picture two wide
is a row and a spare - and the spare is a real pixel that reads back and can be changed.
None of the four reach it, because each walks the rectangle rather than the run.

**A blur radius of zero or less does nothing at all** - `if (radius > 0) BlurImage(...)` - so
a caller passing a computed radius that came out negative gets its image back untouched
rather than an error. **A radius wider than the picture is brought down to half its shorter
side**, so no radius is ever too large to ask for: a hundred thousand gives the most blurred
the picture can be.

`u-image-blur.c` is Ivan Kuckir's three-box approximation. A box blur replaces each pixel by
the plain average of its neighbours in a line, which is cheap and looks wrong; three in a row
look almost exactly like a Gaussian and cost the same. The three box widths are not all
equal - the ideal width is rarely a whole odd number, so some boxes take the odd number below
it and the rest the one above.

**The C runs off the end of every row it blurs at the widest radius it allows.** It writes
one pixel more per row than the row holds, because the radius is brought down to half the
width rather than to half of one less than the width. On a picture with an even width that is
one pixel too many, and on the last row it is past the picture altogether. What it reads
there is whatever the allocator left, which on a picture of any size is zeros - **and the
blurred pixels near the bottom right are darker for it.** That is part of the answer rather
than a safety margin.

---

## 152. Image DIFFERENCE ignores alpha, rounds to reach exactly 100%, and answers nought for a rectangle off the edge

The measure is the redmean approximation, not a plain distance in red, green and blue: equal
steps in those numbers do not look equal. Green carries most of what the eye reads as
brightness, and how much red and blue matter depends on how red the pair already is - so the
red and blue weights slide with the mean of the two reds while green's stays at four.
`https://www.compuphase.com/cmetric.htm`, which the C cites.

**Its shifts must be kept rather than turned into division.** `((512+rmean)*r*r)>>8`
truncates where a divide by 256 would, and the percentages come out a fraction different if
it does not.

**Alpha takes no part.** Two images differing only in transparency are nought per cent apart,
checked against a real 3.22.1, and not what a reader of "weighted RGB distance" would assume.

**The mean distance is rounded to a whole number of picounits before it is divided**, which
is the whole reason black against white reads as exactly a hundred per cent. The C says so in
a comment above the line - "used rounding to have nice 100% when completely different" - and
without it the answer comes out as 99.9999999999999%, which is true and reads as a mistake.

**Only the overlap is compared when the sizes differ**: "If sizes of the input images are not
same... then only the smaller part is compared!"

For the rectangle form, **the corner is counted from nought** - "Zero based top-left corner",
says the declaration - because it is a coordinate into a picture rather than a position in a
series. **A negative size reaches back from the corner**, which is how a caller names a region
by its far corner: the corner moves by the negative amount and the size becomes positive. A
corner before the picture starts is brought up to nought and takes that much off the size
with it.

**A rectangle reaching past the right or bottom edge gets a negative width or height rather
than a clipped one**, because the C subtracts the size a second time where clipping would
subtract only the corner. Nothing is then compared and the answer is nought per cent - which
says two pictures are identical when the pixels it was pointed at differ. Rebol's own test
asserts that nought twice, so it is behaviour rather than an accident to be tidied away.

**A rectangle of no pixels at all is a different thing and gets a different answer**:
dividing nothing by nothing is not a number, and that is what comes back. Nought per cent
would be tidier and would be a lie, because it claims two pictures were compared and found
identical when none of them was looked at.

---

## 153. Appending an alpha to an image leaves a white pixel, and CHANGE lays an image as a shape

`t-image.c`. `QUAD_SKIP` turns a pixel index into a byte offset and nothing else about
navigating an image is special, so APPEND, INSERT, CHANGE and FIND mean on an image what they
mean on a block.

**The height is never set.** It is how many whole rows the pixels make, worked out after
every change - so three pixels in an image two wide are a row and a spare, and the next pixel
appended completes the row and lifts the height.

**Not every write touches a whole pixel.** A tuple of three is a colour that is wholly
opaque; a tuple of four carries its own alpha; **a whole number on its own is an alpha and
reaches nothing else** - `Fill_Channel_Line` writes that one byte and steps over the other
three. /ONLY asks for the opposite, a colour with the alpha left as it was.

**A run of bytes reads four to a pixel here, which is the other way round from building an
image.** `make image! [1x1 #{FFFFFF}]` reads three at a time because the alpha arrives in a
run of its own; a binary written at an image reads four because there is nowhere else for the
alpha to come from.

**Pixels are refused as a whole if any one of them is not a pixel**, because a half-applied
APPEND is worse than a refused one. Rebol's own test writes a string at an image and then
checks the length has not moved.

**`append img 7` adds an almost-invisible white pixel, and nothing anywhere decided that.**
`Expand_Series`, then `CLEAR_IMAGE`, then the fill: the C makes room, whitens it, and only
then writes whichever channels were named. An alpha on its own therefore leaves white behind
rather than black. It falls out of the order.

**CHANGE never lengthens an image.** Its width is fixed, so a longer one would be a different
shape, and pixels past the end are dropped.

**One image written into another is the one thing CHANGE lays down as a shape rather than a
run.** `Copy_Rect_Data`: the rectangle's top-left corner goes where the position points and
each of its rows lands on one row of the target, so what will not fit on a row is dropped
rather than spilled onto the next.

- **The step taken afterwards is one pixel, not the size of what was written** -
  `index + dup * part` with `part` left at one, because one image is one thing however many
  pixels it carries. **/DUP multiplies that step and nothing else**, so the rectangle goes in
  once however many times it was asked for.
- **/PART says how big the rectangle is rather than how many pixels to write**, which is the
  only reading a shape allows: two across and two down is four pixels, and "four" would not
  say which four. **A count given where a shape belongs writes nothing at all** - the C reads
  it into the variable holding how many things were given and leaves the rectangle at nought
  by nought.

**COPY/PART with a shape takes a rectangle out**, clipped to what is left of the row and of
the picture below it - so a rectangle asked for at the last column comes back one wide, and
one asked for larger than the picture comes back as the picture.

**FIND matches a tuple of three against the colour whatever the alpha**, a tuple of four
against the alpha too, and a whole number against nothing but the alpha. **/ONLY drops the
alpha from the comparison even where a tuple of four named one**, which reads oddly until you
notice what /ONLY means everywhere else: look at the thing itself and not into it. A pixel's
colour is the thing; its alpha is how much of it shows. **/MATCH asks a different question** -
whether the pixels *at* the position are the ones named - so it never looks past where it
started.

---

## 154. A codec's error code is the inverted result, and MARKUP's identify can never say yes

`Init_Codecs` in `b-init.c` registers two - `Register_Codec("text", Codec_Text)` and the same
for markup - and `Init_QOI_Codec` in `u-qoi.c` registers a third from the same place. Each
puts a handle of type `codec` into `system/codecs`, and **registering them is what makes the
handle datatype reachable at all**: nothing else in a build without the crypto family or an
extension API produces a handle. Every other codec in the C - png, gif, jpeg, bmp, wav - sits
behind an `#ifdef INCLUDE_*_CODEC`, so these three are what a stock build always has.

**A non-zero error is `bad-media` unless the answer was CHECK, in which case it is the
inverted result.** "error code is inverted result", says `reb-codec.h` - so identify says yes
by setting no error.

- **TEXT identifies anything**, because any bytes are text as far as it is concerned:
  `if (codi->action == CODI_IDENTIFY) return CODI_CHECK;` with the error left at zero. Its
  encode answers an empty binary, and the C's own comment says why that does not matter -
  "this does not happen as in n-system.c only image is allowed to be encoded!".
- **MARKUP identifies nothing.** It sets `codi->error = 1` and returns CODI_CHECK, with the
  comment "never identified... would require markup validator". It is the one codec whose
  identify cannot say yes. Its encode is `CODI_ERR_NA`, which DO-CODEC turns into
  `bad-media`.
- **QOI identifies by the magic and nothing else.**

**The QOI encoder writes blue first under the format's heading "red".** The C's image data is
already in that order, so its encoder copies without looking; the swap is invisible there and
has to be done deliberately anywhere a pixel is held red first. It happens on the way back
too, so a picture is itself again and only the file disagrees with the published format.

**`Load_Markup` is not a parser.** It looks for a less-than sign and takes what follows as a
tag only if the next character could start one - a word character, a slash, a question mark
or an exclamation mark:
`if (!IS_LEX_WORD(cp[1]) && cp[1] != '/' && cp[1] != '?' && cp[1] != '!') { cp++; len--; continue; }`.
Anything else and the sign is ordinary text, **which is what lets `a < b` through unharmed**.

Past that: **a comment runs to `-->` rather than to the first `>`**, a quoted attribute value
can hold a `>` without ending the tag, and **a tag that never closes is text** - the loop runs
out and the trailing `if (cp != bp)` appends what is left as a string.

---

## 155. A gob's five content fields share one slot, and three fields read and write asymmetrically

`PD_Gob`, `Get_GOB_Var` and `Set_GOB_Var`. A word names a field of the gob; a number names a
child in its pane, counted from where the gob stands.

**`image`, `draw`, `text`, `effect` and `color` share one slot and one type tag**, so writing
any of them takes away whatever was there, and reading the one it has not got answers none.
**`data` looks like a sixth and is not** - it has a slot and a tag of its own, which is why a
gob can carry a draw block and a block of data at the same time. `data` holds five things,
each with its own `GOBD` tag: an object, a binary, an integer, a block or a string.

**Three fields break the symmetry between reading and writing.** `parent` can be read and not
written, because being in a pane is what sets it. `owner` can be written and not read, because
`Get_GOB_Var` has no case for it. And `flags` answers a block built from a fixed table, so it
comes back in the table's order whatever order it was written in.

**A gob is the one series where a negative position does not reach behind where it stands.**
`index += Int32(pvs->select) - 1; if (index >= tail) return PE_NONE;` with `index` unsigned,
so a zero or a negative count wraps to something enormous and fails that test.

**Two arms of `Set_GOB_Var` have no `return FALSE` and so accept anything.** A colour that is
not a tuple, and a flags value that is neither a word nor a block, are quietly ignored - that
arm ends in a plain `break` where every other content arm ends in `return FALSE`. It reads
like an oversight in the C and it is what a script sees.

**A lone number written to `offset` or `size` is both halves**:
`pair->x = pair->y = (REBD32)VAL_INT64(val)`, so `size: 7` is seven square.

**Writing an image also sets the gob's shape**: `GOB_W(gob) = VAL_IMAGE_WIDE(val)`.

**A colour is kept as a pixel, so the alpha a script never wrote is the alpha it reads.**
`Set_Pixel_Tuple` writes four bytes and puts 0xFF in the fourth when the tuple was shorter,
and `Set_Tuple_Pixel` reads all four back.

**Writing `pane` replaces the children rather than adding to them** -
`if (GOB_PANE(gob)) Clear_Series(GOB_PANE(gob));` first. **Writing `flags` as a block starts
from nothing** - `gob->flags = 0;` before the loop - **where a lone word adds to what is
there**, and `Set_Gob_Flag` walks a table of nine and stops at the end, so a word that is not
a flag does nothing at all.

**POKE and CHANGE on a pane insert rather than replace.** The C has the replacing code beside
it and commented out, so `poke g 1 child` makes the pane one longer.

---

## 156. A call consumes what the path *named*, not what was granted, and the arguments arrive out of order

**A declined refinement's argument is still written and still taken.** The C takes each one
and drops it - `if (useArgs) DS_Base[ds] = *DS_POP; else DS_DROP` - so what a call consumes
depends on what was written and not on what was granted. `append/:part s v 1` with part
declined appends and swallows the 1.

What counting only the granted ones cost here: REPEND is
`append/:part/:only/:dup :series reduce :value :length :count`, and with all three declined
the two spare words were left standing in the block, evaluated separately, making the last of
them the function's answer. `repend [1 2] [3 4]` came back as NONE, and **every port opened by
URL failed with no-scheme**, because `make-port*` decodes a URL through REPEND.

**The values arrive in the order the path wrote its refinements, not the order the function
declares them.** `sort/compare/skip s 1 3` hands over the comparator first and the record size
second, and SORT declares the size first. `Do_Args` in `c-do.c` does the same thing from the
other end: when the path names a refinement that is not the next one in the spec, it restarts
the spec walk at that refinement and fills its arguments from the stream, under a comment
reading "refinement out of sequence, resequence arg order".

**Getting that wrong is subtle rather than loud.** A quoted parameter belonging to a
refinement written out of order was matched against whichever parameter sat at that position
in the declaration, so `f/two/one "a" x y 1` evaluated X instead of taking it as written, and
failed on a word nobody had set.

**An argument belonging to a refinement is only counted when that refinement was asked for**,
so a call's arity depends on the call site rather than on the function. Counting them all
means `f: func [a /into b] [...]` demands two arguments from every caller, and Rebol's own
COLLECT cannot be written.

**A soft-quoted parameter is the `'word` sigil, not `:word`.** A paren, a get-word or a
get-path at the call site says "evaluate this one after all", which is what makes it soft: the
function declares the default and the caller keeps a way out. A hard-quoted parameter always
takes the next value as written, which is why `repeat count 3` works - the counter is a word
the loop is about to bind, so looking it up first would fail on a word nobody has set.

**A block that ends where a quoted parameter was wanted has given it unset, not too few
arguments.** The parameter's own typecheck then accepts or refuses. That is how `try [su]`
releases the user: SU's name is quoted and accepts `unset!`, so a block with nothing after it
means "no name". An **evaluated** parameter is a different question - there is no expression
to evaluate and no value to make up, so the block really did end a call short.

---

## 157. An event is twelve bytes, so reading and writing its fields are not symmetrical

`Get_Event_Var` and `Set_Event_Var`. An event is a value cell rather than a container, so
every field is packed into or unpacked out of twelve bytes, and the places reading and writing
part company are the interesting ones.

**`e/date` is an error and `e/data` is none.** An unknown name is `PE_BAD_SELECT`, which is
`invalid-path` - so a field the event has not got raises where an empty field it does have
answers none.

**`port` takes a port, an object or none going in, and none means "this belongs to the GUI"
rather than "nothing".** Coming out it answers whatever the model names: seven models, five
answers. A GUI event, a callback and a console event each name a field of `system/ports`; a
port event and a MIDI event hold the port itself; an object event holds the object; and a
device event has to reach through an I/O request, which is the host's structure, so it answers
none.

**`key` takes a character or a key word and puts a number in the data field. Coming out, the
*type* decides how that number is read** - a character for a key event, a word for a control
event, and none for anything else - though **`code` hands over the number whatever the type.**
A character is stored as its codepoint; a key word is stored as its position plus one, shifted
sixteen bits up, which is what keeps a named key from colliding with a character.

**Writing `key` sets the model and may set the type before it looks at the value.**
`VAL_EVENT_MODEL(value) = EVM_GUI; if(!VAL_EVENT_TYPE(value)) VAL_EVENT_TYPE(value) = EVT_KEY;`
run first, so a key of the wrong datatype is refused **after two fields have already changed** -
visible to a caller that catches the error and carries on.

**`flags` can be read and not written**: nothing in `Set_Event_Var` touches them, because they
say what a window system observed. **`data` is the same, and its two guards mean a script
always reads none for it** - `if (!GET_FLAG(..., EVF_HAS_DATA)) goto is_none;` and the type
must be `drop-file`, and nothing in `Set_Event_Var` raises HAS_DATA. That is the answer rather
than a gap.

**`window`/`gob` is guarded three times**: the model must be GUI, the HAS_DATA flag must be
down (a dropped file uses the same slot for a string), and there must be something in the slot.

**An offset out of range raises rather than truncating.**
`if (fabs(f) > (REBD32)(0x7FFF)) { ... Trap_Range(DS_TOP); }` - a coordinate a window system
could not have produced is a mistake in the caller, and a truncated one would be a click in
the wrong place.

**A type word the catalogue has not got is `invalid-arg`, not `bad-field-set`.** `Trap_Arg(val)`
raises from inside the setter, so it is the same error either way in - where something that is
not a word at all is a plain `return FALSE` and becomes the caller's error.

**`make event! [port:]` works and `make event! [type:]` fails.** `Set_Event_Vars` walks the
spec in pairs and reads a set-word with nothing after it as none -
`if (IS_END(val)) val = NONE_VALUE;` - which is the opposite of what a gob does with the same
shape. None is a legal port and not a legal type.

**`make some-event [offset: 0x0]` is not a copy of that event with an offset.** MAKE and TO are
one arm: an event alone answers that very event, and anything with a block starts from a
**cleared** event and fills it. It is the one thing about this the name gets wrong.

---

## 158. TRACE's level is a limit, and the indentation stops at ten while the count keeps going

`c-do.c`. Three hooks, each where the thing it reports happens: `Trace_Line` **before** a value
is evaluated, `Trace_Func` as a call is made, `Trace_Return` as one answers. **So a trace shows
what the evaluator is about to do rather than what it did.**

The output format is Rebol's own, from the `trace` block of `boot/strings.reb`:

```
"%-02d: %50r"    the position and the value
" : %50r"        what a word holds
" : %s %50m"     what a word holds, when it is a function
" : %s"          anything else a word holds
"--> %s"         a call being made
"<-- %s =="      a call answering
```

**The level is a limit and not a switch.** `trace 3` shows three levels of nesting and nothing
deeper, and **`trace on` is the level 100000** rather than a flag of its own:
`Trace_Level = IS_TRUE(arg) ? 100000 : 0;`.

**The indentation stops growing at ten while the cutoff keeps counting** -
`if (depth > 10) depth = 10;` - so deep output stays readable without pretending to be shallow.

**The depth TRACE was called at becomes the zero the indentation counts from**, minus one for
TRACE's own frame: `Trace_Depth = Eval_Depth() - 1;`, taken at the moment the level is set.
Without it every line would be indented by however deep the caller happened to be.

**/FUNCTION silences the value hook entirely**, and a function value is never reported there
anyway because the call hook reports it instead:
`if (GET_FLAG(Trace_Flags, 1)) return; // function` and `if (ANY_FUNC(value)) return;`.

**Asking for the backtrace is also how tracing is turned off.** `trace/back 5` prints the last
five and stops: `Trace_Flags = 0; Display_Backtrace(Int32(arg));`. A caller that wanted both
has to ask for the level again afterwards. The buffer is a ring of a hundred lines.

---

## 159. QOI's run chunk caps at sixty-two, and a short stream is padded rather than refused

`sys-qoi.h`, the reference implementation Rebol vendors. Fourteen header bytes, then a chunk
for every run of pixels, then eight bytes that end it. Five chunk kinds and a sixty-four entry
table of colours seen before, in about a hundred and fifty lines, which is the point of the
format.

**A run chunk carries sixty-two pixels, not sixty-four.** The two highest counts would spell
the eight-bit tags, so a longer stretch of one colour breaks into a second chunk.

**A stream must be long enough to hold the header and the end marker before anything is read
out of it** - `size < QOI_HEADER_SIZE + sizeof(qoi_padding)` - so a header with nothing after
it is refused rather than answering a picture of whatever the buffer was made with.

**A stream that runs out early is not refused.** The C stops when the bytes do and leaves the
rest of the buffer as it was made, which is opaque white - so a header claiming more than it
delivers gives a picture of the size it said, padded.

**The difference chunks carry a step as the wraparound the format states**: "1 - 2 will result
in 255, while 255 + 1 will result in 0", so a step of 255 is a step of minus one. Four chunk
sizes in order: two bits a channel where nothing moved by more than one; six bits for the
middle channel and four each for the other two where the step is small and mostly shared; all
three channels whole where it is not; and all four where the alpha moved too.

(The channel order QOI writes is covered in entry 154.)

---

## 160. Two of Rebol's curves publish one coordinate and cannot sign, and TLS asks for one of them first

Elliptic-curve Diffie-Hellman is the same idea as the modular kind with different arithmetic:
instead of raising a generator to a private power in a field of integers, each side multiplies
a point on a curve by a private number. The published value is a point and the secret is one
coordinate of the point both sides reach. Everything else follows - a curve has to be named, a
context remembers which one, the secret is one coordinate wide, and two contexts on different
curves cannot agree on anything.

**Two of the curves are built for the exchange and for nothing else.** Their arithmetic never
needs a point's second coordinate, so they publish the first on its own - one coordinate,
little-endian, with nothing in front of it - where the others send both coordinates behind an
uncompressed-point lead byte. **A key on one of those curves is a number to multiply a point
by and not a signing key**, so it signs nothing.

**That is not a detail of the encoding.** Rebol's own TLS asks for curve25519 first - the
scheme's `supported-groups` begins with it and the client hello does
`curve: first supported-groups` - so a build that serves only the older family cannot write a
hello at all, and `read https://` stops before a byte leaves.

**The catalogue names thirteen curves and a JDK's default provider has fewer**: the Brainpool
family is absent, and the narrower NIST and Koblitz curves were withdrawn. Asking for one of
those answers none, which is the shape the C already uses for a curve a build has not got.

**Signing is over a hash the caller has already made.** The argument is named `hash` in the
declaration and the C signs it as it stands, so the signature algorithm takes no digest of its
own. Each signature draws a fresh random number, so two signatures over one hash differ and
both hold.

---

## 161. Reading a checksum port must not end the sum, and opening an open one restarts it

`Checksum_Actor` in `p-checksum.c`. The digest is built up across writes rather than computed
in one go, which is the whole point: a file too large to hold in memory can be summed a block
at a time. The method comes off the spec, which the scheme's own INIT fills from any of three
places - `checksum:md5`, `checksum://md5` or a spec block - and defaults to MD5.

**Reading must not end the sum.** The C copies the context onto the stack and finishes the
copy, with a comment saying why: "using copy so READ will not destroy intermediate context
state by calling *_Finish". So `read port` twice running gives the same answer, and writing
more afterwards carries on from where the writes had got to rather than from nothing.

**Opening a port that is already open throws away what has been written to it.**
`Checksum_Open` clears the context whether or not one was there. Rebol's own suite says so in
a comment beside the assertion - "opening already opened port restarts computation" - and
builds a sum twice from the same port to prove it.

**READ and UPDATE both answer none on a closed port rather than raising**:
`if (!IS_OPEN(req)) return R_NONE`.

**Both READ and UPDATE fill `port/data`; only READ hands the digest back**, which is why
`update port` answers the port and the sum is then found at `port/data`.

**A negative `/part` reaches backwards**, so `write/part port tail bin -2` sums the two bytes
before the tail rather than nothing at all. `/seek` moves the start and is clamped to the
series; `/part` counts from there. **A window that ends up empty is not an error** - the C
returns the port untouched.

---

## 162. A function spec is read by datatype, and every name in it is counted once

A spec is itself a block of ordinary values, which is why a function can be built at runtime
from a block someone assembled. What each value means depends on its **datatype** rather than
on its position: a word is an argument that gets evaluated, a lit-word is one taken
unevaluated, a get-word is one fetched without being called, a refinement is a switch and the
words after it belong to it, a block after any of those restricts the datatypes it accepts,
and a string is documentation that contributes nothing.

**`/local` is a refinement by spelling and not by behaviour**: its words are the function's
own working names, not arguments a caller supplies.

**A word repeated anywhere in the spec is a duplicate**, locals included.
`Collect_Frame(BIND_ALL | BIND_NO_DUP | ...)` walks the whole block before anything is
validated - so between two arguments, between two locals, or **between an argument and a
local**. Skipping everything after `/local` lets `func [a /local a][]` through with two names
for one slot.

**The duplicate failure names the word as it was written rather than as it was spelled**, so
a repeated refinement reports `/x` and a repeated argument reports `x`.
`Trap1(RE_DUP_VARS, value)` hands over the value from the block itself.

**A malformed spec names the whole block, not the part that was wrong.** "Report full invalid
function spec block in the error", says the C above the line - and it is the more useful of
the two, because a stray set-word means little without the specification around it.

**One set-word is allowed: `return:` followed by a block naming what the function answers.**
Red writes a function that way and the C allows it so the same definition reads in both - "It
will be ignored while evaluating", says the comment, and it is. **One only**: a second
`return:` is a malformed definition like any other set-word.

**A type block holds words, not datatypes.** `integer!` is a word the system context binds to
a datatype, and the reader hands it over as written, so the name is resolved when the spec is
read.

---

## 163. LZW's codes are adjusted-binary and its dictionary is recycled, not cleared

`u-lzw.c` carries David Bryant's variant, and two things make it unlike the textbook
algorithm. Both change the bytes.

**The codes are written in adjusted binary.** A dictionary holding 257 strings would normally
spend nine bits on every code; here the codes below a threshold spend eight and only the ones
above it spend nine, the threshold moving as the dictionary grows. **So the width of a code
depends on how many strings exist when it is written**, and a decoder that counted differently
would read the whole stream wrong from that point on. The extra bit is written after the rest,
so a reader can take the narrow form first and widen only when it has to.

**The dictionary is never simply cleared when it fills.** Entries that nothing longer is built
on are recycled one at a time, and the encoder keeps a count of how many remain; it starts
over only when too few are left or when a decaying average of the compression ratio says it
has stopped paying. That average is why there are two counters seeded at 65536 and shaved by a
two-hundred-and-fifty-sixth each round.

**The first byte of the stream is the maximum symbol width less nine**, so the decoder can
size its own tables. **COMPRESS/LEVEL picks that width, and it reads backwards**: level one to
seven give nine to fifteen bits and anything else gives sixteen, so level zero is the
narrowest and level eight and above the widest - because level zero is spelled as "less than
one".

`if (level >= 1 && level <= 7) maxbits = 8 + level; else if (level < 1) maxbits = 9;` and
sixteen otherwise, **over an unsigned level**. COMPRESS with no /LEVEL passes `UNKNOWN`, which
is minus one written into an unsigned, so the comparison sees four thousand million and the
answer is sixteen. Reading the level as signed makes the default the narrowest width instead
of the widest, and every byte after the first comes out differently.

---

## 164. Numbers that are not an RSA key answer none, which the rest of the crypto family does not do

`n-crypt.c` builds the key in RSA-INIT and uses it in RSA, and the split is about cost and
about where a caller learns the numbers were wrong. The key arrives as raw numbers, each a
binary, and turning those into something that can encipher means checking they really are a
key: that the modulus and exponent agree, and for a private key that the primes multiply back
to the modulus. Doing that per operation would repeat the expensive part of every call.

**`mbedtls_rsa_check_pubkey` failing is `return R_NONE`**, and the native turns that into
none - the opposite of what the rest of the family does. An even modulus, a zero modulus and
an exponent with no relation to it all have to come back as nothing rather than as a key that
fails later.

**The primes are checked against the modulus rather than trusted.**
`mbedtls_rsa_check_privkey` is what makes a wrong private exponent answer none instead of
enciphering nonsense. **They may arrive either way round**, because p times q is q times p and
the key is the same key.

**The C imports five numbers and derives the rest.** The modulus, both primes, the private
exponent and the public exponent, then `mbedtls_rsa_complete`. A host library wanting eight
should derive the other three for the same reason mbedtls does: a caller has the five and
should not have to supply eight.

**The consistency check is against the Carmichael function, not Euler's totient.** A
generator is free to choose the private exponent modulo either, and a JDK's own chooses the
smaller - so checking `e*d` against `(p-1)(q-1)` turns away perfectly good keys, including
every key a JDK generates. mbedtls checks the key is *consistent* rather than assuming which
was used.

**The padding is PKCS#1 v1.5 unless /OAEP**, and **the signature digest is SHA-256 unless
/HASH named another**. Where a host library offers a bare "OAEPPadding" or a bare PSS, the
digest, mask function and salt length have to be stated rather than left to a provider
default - **salt as long as the digest** is what makes a signature interoperable with the
mbedtls the C uses. A scheme whose parameters depend on which provider answered is not one a
caller can interoperate with.

---

## 165. A hue is a byte, not a degree, and a negative one wraps

The colour arithmetic of `n-image.c` and `t-image.c`. Every one of these is a formula over
three or six bytes, and a formula that is one off is a colour that fails a comparison and
nothing else - so each has to be the C's arithmetic exactly, including where it truncates
rather than rounds and where it does the reverse.

**Hue, saturation and value live in the same three bytes as red, green and blue.** `REBCLR`
aliases them, so **a hue is a byte rather than a degree**. The C's comment says why: "255
because we have just one byte! Else it should be 360!". Six sectors over 255 values is lossy,
**and the round trip only survives for colours that land on a sector boundary.**

**The hue is cast to a byte, so a negative hue wraps rather than clamping.** That wrap is the
behaviour: red with more blue than green has a hue near 255. The value is the largest part,
the saturation is how far the smallest falls below it, and the hue is which part is largest
plus how far the other two lean, at 42.5 a sector.

**`Grayscale` is `(r + g + b) / 3`, integer division and all.** **`Luminosity` is BT.709 by
default and BT.601 under /LUMA, cast to a byte rather than rounded, so it truncates.**

**`weighted_rgb_color_distance` is a perception distance, not a Euclidean one.** Green is
weighted four flat while red and blue are weighted by the mean red of the two colours, which
is what makes two greens further apart than two blues of the same numeric difference.

**TINT is written as two cases rather than one interpolation**, because the C is
`(r1 >= r2) ? r2 + ((r1 - r2) * amount1) : r1 + ((r2 - r1) * amount0)`. The two agree at every
amount; following the C keeps the rounding in the same place.

**HSV to RGB takes no arithmetic at all when the saturation is zero** - that is the C's first
branch, and the answer is a grey.

---

## 166. CRUSH is Rebol's own compressor, built with Red's constants rather than the original's

`u-crush.c`, ported from Ilya Muravyov's public-domain original. LZ77 with the matches written
out in a bit code of its own: a length in one of six brackets and then a distance in one of
sixteen slots, packed least significant bit first. **The first four bytes are the uncompressed
length, little endian**, which is how DECOMPRESS knows how much to make before it starts.

**Rebol builds it with the constants Red uses rather than the ones the original shipped
with** - a smaller window and smaller hash tables, which the comment beside them says give
better results on Rebol-like data. **The two sets are not compatible**, so anything that does
not compile with the same ones will not read the bytes back.

**The three levels differ in one number**: how far down a hash chain the compressor will look
for a longer match, and whether it looks ahead one byte to see if waiting would pay. **Level
two is the only one that looks ahead.**

**A match twice as far off has to be a byte longer to be preferred**, and so on by eights.
That penalty is what stops the compressor trading a short near match for a slightly longer one
on the far side of the window, whose distance costs more bits than the extra length saves.

**The writer has to flush seven zero bits at the end**, or the last partial byte never reaches
the output.

**`if (limit && size > limit) size = limit`** - DECOMPRESS/SIZE asks for the first so many
bytes and stops there, which is how a script reads the front of something without the whole of
it.

---

## 167. `random/seed 1` then `random "stesti"` is `"sistte"`, and only one generator gives that

Knuth's lagged Fibonacci generator from `src/core/f-random.c` - his published `ran_array` with
one change Rebol carries: **the modulus is 2^62 rather than 2^30**, so the numbers are 62 bits
wide.

That assertion is in Rebol's own tests, and it is a statement about this exact sequence of
numbers. **No other generator produces it**, however well distributed it is, so a port that
uses a different one cannot agree with Rebol about anything a script seeds.

**The C seeds with 314159 when a script asks for a number and never seeded.**

**`ran_start` squares and multiplies its way to a state seventy streams away from the seed**,
so that two nearby seeds do not give two similar sequences. **The ten warm-up rounds at the
end are Knuth's too, and leaving them out changes every number that follows.**

**The end-of-buffer marker is written as a negative number in the buffer**, which is why every
real value has to be non-negative and why the modulus is what it is.

**The shuffles and the pickers narrow to thirty-two bits before taking the remainder.**
`(REBCNT)Random_Int(secure) % n`, and the cast is not decoration: the sixty-two-bit number is
cut down first and the answer is not the one the full number would give. Taking the remainder
of the whole thing produces a perfectly good shuffle that disagrees with Rebol's on every seed.

**A tuple's octets come from different arithmetic.** `REBTYPE(Tuple)` writes
`Random_Int(...) % (1 + *vp)` with no cast in front of it.

---

## 168. Every datatype answers `==` its own way, and six of them surprise

**A word's binding is not part of its equality.** From the bindology reference: two words are
the *same* if and only if they have strict equal spelling and equal binding, while *equal*
words need not have equal binding. So equality asks what a word says and SAME? asks which word
it is. **This matters beyond pedantry**: MOLD does not print bindings, because a binding is not
syntax, so if equality counted binding then no bound block could survive a round trip through
MOLD - and blocks are bound the moment they are about to be evaluated.

**A bitset's complement flag counts as much as its octets do.** A set and its complement share
every byte and mean opposite things, so comparing the octets alone makes `b` equal
`complement b` - which Rebol's own suite asks about four ways over, by EQUIV?, EQUAL?,
STRICT-EQUAL? and SAME?, and expects false from all four. **Length counts too**:
`make bitset! 1` and `make bitset! 9` hold no bits between them and are still not equal,
because one has room for eight and the other for sixteen.

**A tuple is equal when the octets agree out to twelve, whatever the lengths.** `Cmp_Tuple`
compares over the longer of the two and the octets behind each are zeros, so it is the same
question as comparing both padded to twelve. **The length is asked about only by `==` and by
SAME?**, which is `CT_Tuple` mode 2 and above.

**Two events are equal when their model, type and data agree** - `Cmp_Event` compares exactly
those three. **Not the flags and not the thing in the slot**, so two events holding different
ports are equal as long as both hold one. That is the C's decision rather than an omission: an
event is identified by what happened and where, and the port is who is being told.

**Two gobs are the same gob or they are not equal.** Identity rather than contents, because a
gob is a thing on a screen with a parent and a pane: two gobs with the same fields are two
objects, and comparing their panes would recurse through a tree that can hold itself.

**`$1.50` equals `$1.5`, under the loose and the strict operator alike** - confirmed against a
real R3, which answers true to both while still molding `$1.50` with its trailing zero. The
scale is kept for printing and ignored for comparing.

**Two objects are equal when they hold the same fields with equal values, whatever contexts
they were built in**, and **`self` is left out**, as it is left out of molding: every object
has one and it points back at the object, so counting it would recurse for ever. Two modules
are equal when their words and their headers are.

**Two typesets are equal when they hold the same datatypes, named or not** -
`(to typeset! [integer!]) = integer-only-set` must not depend on how either side was built.

---

## 169. Three molds are not what the value looks like it should print

**A typeset molds its members, never its name.** `Mold_Typeset` walks the bits and writes what
it finds, and it has no way to know a set was asked for by name - a typeset is its members and
nothing else. So `mold number!` is `make typeset! [integer! decimal! percent!]` and not the
word that fetched it.

**A tuple's mold pads out to three.** `Emit_Tuple` writes each kept octet and then keeps
writing "0." until it has written three, so a tuple keeping one octet shows as `1.0.0` and one
keeping none shows as `0.0.0`.

**A date's offset of zero is written as nothing at all.** `1-Jan-2000/12:00+0:00` molds as
`1-Jan-2000/12:00`, so **the written form does not distinguish an offset of zero from a date
that never carried one**, and reading either back gives a zone of 0:00.

**A time writes its fraction to as many digits as it has and no more**: a tenth is `0:00:00.1`
and a nanosecond is `0:00:00.000000001`. Dropping the fraction makes a time that had one mold
as a time that had not - **and a molded value that does not read back as itself is a value that
cannot be saved.**

**A closure is its own datatype, not a function with a flag set.** `types.reb` gives `closure!`
a row of its own, so `type? :c` is `closure!` and `function? :c` is false where
`any-function? :c` is true. Answering `function!` for both kinds makes `closure?` false for
every closure ever written - while MOLD still says `make closure!`, because the molder reads
the flag and nothing else does.

**An action and a native look identical from every angle but their name.** Both are
host-language functions; the sixty Rebol declares in `actions.reb` are actions and the rest are
natives, and the name is the only thing that carries it.

---

## 170. A map stores every word as a set-word, and its two ends disagree about case

**Any kind of word is stored as the set-word it names.**
`if (ANY_WORD(key) && VAL_TYPE(key) != REB_SET_WORD) ... VAL_SET(set, REB_SET_WORD);` So
`#[a: 1]` and `make map! [a 1]` hold one key, and so do a lit-word, a get-word and a refinement
of the same spelling: **a map is keyed by what a word names rather than by how it was
written.**

**The direction matters as much as the fact.** Storing the set-word is what makes a molded map
read back as an equal map - the colon comes from the key and not from the molder, **which is
why a map keyed by an integer molds as `#[1 2]` with no colon anywhere**. KEYS-OF turns them
back, and so does the walk; nothing else does.

**`Find_Entry` takes a `cased` flag and its callers do not agree about it.** A path read,
SELECT, FIND, PUT and POKE pass false; MAKE and REMOVE/KEY pass true. So the two ends of a map
behave differently on purpose - **building one keeps `"k"` and `"K"` apart, and looking one up
does not** - which is what lets a caller use whatever case is to hand while the map can still
hold both.

**The answer is whichever was stored first, not the exact one.** A map holding both `<a>` and
`<A>` answers the first of them to either spelling, so `select m <A>` is the value under `<a>`.
Preferring the exact key reads as the friendlier answer, is not what a real 3.22.1 gives, and
makes an uncased SELECT indistinguishable from SELECT/CASE on exactly the maps where the
difference matters.

**A text key is copied and locked; a block key is not.** A key is what the map is hashed on, so
a caller who kept hold of it and appended to it would move the entry out from under its own
hash - hence `append first keys-of #["key" 1] "x"` is a `protected` error, while appending to
the block the keys came from is not, the two no longer being the same series. Rebol's own suite
says the rest in a comment - "note that keys are not implicitly protected!" - and then shows
what follows: poking with a block a caller still holds, then emptying it, gives a map with two
keys that are both `[b]`. **Copying every key would have been the tidier rule and is not the
one REBOL has.**

**A binary key has no case, although it holds the same bytes a string would.** `#{61}` and
`#{41}` are two keys however they are asked for, because a binary is bytes rather than letters
and nothing says which of them stand for text.

**`TRAP_PROTECT(VAL_SERIES(value))` is the first line of every writing branch of `MT_Map`**,
and the error it raises is `protected`. Keeping the flag and letting every write through makes
PROTECT on a map a word that does nothing.

---

## 171. A path segment on a block can be a name, and the answer is the item after it

`PD_Block` in `t-block.c`. Three kinds of selector meaning three different things. **A number
is a position. A word is a name to look up, and the answer is the item *after* the word** - so
`[a 1 b 2]/a` is 1, which makes a block a usable lookup table without being a map or an object.
Anything else is searched for by value, and again the answer is the item after it.

**The word form is the one worth knowing.** Rebol's own code leans on it constantly - a block
of settings read as `defs/types` - and a path that refuses it stops `prot-mysql.reb` and
Rebol's own URL parser.

**A read answers none for anything it cannot find and only a write refuses.**
`if (n < 0 || (REBCNT)n >= VAL_TAIL(pvs->value)) { if (pvs->setval) return PE_BAD_SELECT; return PE_NONE; }`
- one line covering a position past the tail, a name the block has not got, and a name sitting
at the tail with no value after it.

**Position zero is the exception, and it quietly does nothing.**
`if (i == 0) return PE_NONE; // like in case: path/0` comes *before* the write check, so a
write through position zero is not refused the way a write through a missing name is. Nothing
downstream looks at whether it happened, so the only evidence is that the block is as it was.

**The search starts where the block is rather than at its head**, so a block stepped past its
first pair finds the second: `Find_Word` and `Find_Block_Simple` both take the index as their
starting point.

---

## 172. A Diffie-Hellman prime is 64 to 512 bytes, and the published value is padded

Two parties who have never met need a shared secret over a line anyone can read. Each makes a
private number, publishes something derived from it, and combines the other's published value
with their own private one. **An RSA context holds a key the caller supplied; a Diffie-Hellman
context holds a key the interpreter generated**, and the only things a caller can do with it are
publish and agree.

**`if (n < 64 || n > 512) goto error;`** - below the lower bound the exchange is not worth
performing, and above the upper one it is slower than anything reasonable wants.

**The published value is padded to the width of the field prime**, because the peer reads it as
a fixed-width number: a leading zero byte dropped gives a value one byte short every few
exchanges and a secret that does not agree.

---

## 173. xxHash sits in the checksum catalogue and is not a cryptographic digest

`system/catalog/checksums` lists the thirty-two and sixty-four bit forms beside MD5 and the SHA
family, **and they are not one of them**: xxHash is built to be fast over a lot of bytes and
makes no claim to be hard to reverse. A caller reaches for it to tell whether two blocks differ,
not to keep a secret.

**Both forms are the same shape.** Four accumulators, each taking every fourth word of the
input; the accumulators folded into one; the length added and the tail eaten a word and a byte
at a time; then a final scramble of shifts and multiplies to spread the bits. Only the
constants, the word width and the rotations differ.

**Little-endian throughout, and the answer is written big-endian**, which is what makes
`checksum "" 'xxh32` the four bytes `02CC5D05` rather than their reverse.

**The final scramble is what makes a one-bit change to the input change roughly half the
answer.** Without it the accumulators leave their high bits barely mixed and two inputs
differing only near the end come out close together - exactly what a hash is for avoiding. Each
accumulator is stirred once more before it is folded in, so a lane that happened to end near
nought does not simply leave the running value alone.

---

## 174. Vector arithmetic truncates before it starts, and the two divide-by-zero guards differ

**A number applied to a whole vector is reduced to the vector's own kind first**, which is why
multiplying an `int8!` vector by 2.4 doubles it: the C truncates the decimal to an integer
before the loop starts, so the four tenths are gone before any element sees them.

**"The same kind" is not "the same size".** The C compares the whole four-bit encoding, so an
`int8!` and a `uint8!` vector are as incompatible as an `int8!` and a `float64!` one.

**Dividing and taking the remainder guard differently, on purpose.** Dividing tests
`i == 0 && bits <= VTUI64`, so **a measuring vector divided by zero is left to the machine and
answers infinity**. The remainder tests `i == 0` alone and refuses whatever the vector holds.
**Both read the divisor already truncated to a whole number, which is why dividing by a half is
dividing by zero.**

**`VEC_OP_LOOP_NO_ZERO` tests each element of the divisor at its own type**, so a measuring
vector *is* guarded element by element where a plain zero divisor is not - the loop is written
once and used for every kind.

**The widest unsigned kind is compared as unsigned by minimum and maximum and as signed by the
statistics.** The difference only shows on a `uint64!` holding more than a signed long can.

**Only the sum and the range come back in the vector's own terms.** The C computes every
statistic as a double and then, at its `return_number` label, turns the answer back into an
integer when the vector counts - **a mean is a decimal whatever the vector holds.** The spread
is Welford's method, one pass, so the sum of squares does not grow until it loses the small
differences it is measuring.

**A vector's older spec spelling is an optional sign word, then a datatype word, then a
width**, all three in that order, refused outright rather than guessed: `unsigned decimal!` is
no kind, and eight or sixteen bits is a width only whole numbers have. What follows the kind is
a count, then data, then a position, all optional and all in that order.

**Data shorter than the stated size leaves zeros behind it and data longer is ignored**,
because the C writes into a series whose tail was already set from the size: the extra values
land in slack the vector does not count.

---

## 175. A bitset's complement is a flag, and a line break is a property of a position

**A complemented bitset keeps a flag rather than flipping every bit.** Flipping them gives a
set that answers the same questions and molds as a wall of FF, and loses the fact that a caller
asked for a complement - which COMPLEMENT? has to answer and MOLD has to print.

**A bitset is protected the way a series is.** `IS_BITSET(value)` sits in the same line of
`Protect_Value` as the series, and every mutation asks first.

**A line break is a property of a position rather than a value in the block**, which is why
NEW-LINE and NEW-LINE? are natives of their own rather than something you could write by
inserting a value. It survives MOLD and is what makes a molded block keep its shape.

**Protection belongs to the storage, not the value.** Two series values sharing storage are two
views of one thing and cannot disagree about whether it can change: PROTECT of either protects
both, which is what makes protection worth anything.

**`29-Feb-2001` is `invalid` rather than a date.** `Month_Length` in `t-date.c`, leap rule
included - checking the day against a flat 31 accepts it.

**A year below 1000 is written four digits wide**: `1-Feb-0003` rather than `1-Feb-3`. The
padding is not decoration - a molded date has to read back as the same date, and `1-Feb-3`
reads back as 2003, because a year of one or two digits is the shorthand form.

**A money's significand is capped at ten to the 26th** (what eighty-seven bits will hold) and
**its exponent is a signed byte**, so it runs from -128 to 127.

---

## 176. BREAK is control flow, not an error, and CATCH does not intercept it

BREAK travels out of a loop body through the evaluator's own control flow rather than through
the error mechanism, and is caught by whichever loop native is nearest.

**Keeping the two apart matters.** A loop is not a failure, so `try [loop 3 [break]]` must not
hand back an error, and **a BREAK that escapes its loop entirely is a mistake in its own right**
rather than something a script should be able to catch as though it were one.

**BREAK/RETURN carries a value, which the loop then answers.** That is how a search loop reports
what it found without a variable outside the loop to put it in. **A plain BREAK answers unset,
not none** - because BREAK/RETURN NONE is a thing a script can write and the two must not look
the same.

---

## 177. RC4 is state, and running ciphertext back through the same context does not undo it

`RC4_setup` mixes a key into a 256-byte permutation once, and `RC4_crypt` advances that
permutation for every byte it enciphers. **So the same key applied to two halves of a message
gives a different answer from the key applied to the whole**, which is exactly what makes it a
stream cipher and why the native hands a caller a context to hold.

**Enciphering and deciphering are one operation**, because the cipher works by exclusive-or
against a keystream. Running ciphertext back through a **fresh** context made from the same key
gives the plaintext; **running it through the same context does not**, because the permutation
has moved on.

**An empty key is accepted rather than refused.** The mixing loop reads the key modulo its
length, and with no bytes to read it leaves the permutation as it found it - which is a
permutation like any other, and a thing a caller can legitimately ask for.

---

## 178. Rebol's LZMA frame is not anybody else's, and nine bytes is past-end rather than bad-press

`CompressLzma` in `u-compress.c` writes the five property bytes the SDK produces, then the
stream, then **the uncompressed length in four bytes little endian at the end**:

```c
*error = LzmaEncode(dest + headerSize, &size, input, len, &props, dest, &headerSize, 0, ...);
SERIES_TAIL(*output) = size;
REBCNT_To_Bytes(out_size, (REBCNT)len); // Tag the size to the end.
Append_Series(*output, (REBYTE*)out_size, sizeof(REBCNT));
```

**A file written by 7-Zip has an eight-byte length in the header instead and nothing at the
end**, so the two are not interchangeable even though the bytes between are the same. The
trailer is what DECOMPRESS reads to know how much to make, unless DECOMPRESS/SIZE said so first.

**Nine bytes is the floor, and a shorter binary is `past-end` rather than `bad-press`.**
`if (len < 9) Trap0(RE_PAST_END);` comes before anything else in `DecompressLzma`: five for the
properties and four for the length leaves nothing for a stream, so it is a question about how
much data there is rather than about whether it is LZMA.

---

## 179. THROW, RETURN and BREAK each stop at a different place, and none of them is an error

- **THROW crosses as many functions and loops as it needs to**, out to the nearest CATCH.
- **RETURN and EXIT unwind to the nearest function and no further** - a helper that returns must
  not also return from whatever called it.
- **BREAK stops at one loop** (entry 176), and **CONTINUE stops only the round**, so the two are
  caught in different places: a break around the whole walk, a continue around each turn of it.

**None of them is an error.** A throw is a decision and an error is a failure, so **CATCH must
not swallow errors and TRY must not swallow throws**, or each would quietly take the other's
work.

**An unnamed CATCH is not a catch-all.** A throw may carry a name, and then only a CATCH
expecting that name takes it: a throw addressed to an outer handler must travel past an inner
one that was not expecting it, or the naming would buy nothing.

---

## 180. Five smaller rules that each cost a defect

**PROTECT/HIDE conceals a field rather than locking it.** The object stops listing it, molding
it and answering for it, and a path to it fails as though there were no such field - **but code
written inside the object still reaches it**, which is the whole point: it is how an object
keeps something to itself. PROTECT/LOCK is the one that makes protection permanent.

**`Reset_Height` runs only after something changes the pixel count.**
`VAL_IMAGE_HIGH(value) = w ? (VAL_TAIL(value) / w) : 0` - so **a zero-width image made as
`-2x2` keeps the height it was given**, because nothing has recomputed it. `CLEAR_IMAGE` is a
memset of 0xFF: white, and opaque with it.

**A category name is matched without minding its case.** `errors.reb` writes them capitalised
and a caller has whatever a script wrote; the C finds the category by symbol in an object, so
matching the exact letters makes `make error! [type: 'Throw id: 'halt]` look like a category
nobody has - and it is the one category whose codes are all below a hundred. R3 numbers its
failures in hundreds by category, so code 400 for a maths error is what a script compares
against.

**A pair's half is a `REBD32`: rounded to single precision, and infinite where it was too large
for one.** Refusing the infinity on the grounds that it would not mold back is wrong twice - it
molds back perfectly well as `1.#INF`, and refusing it makes `as-pair 1e300 -1e300` fail where
Rebol answers a pair.

**A struct's type word is rewritten in the spec block itself.**
`VAL_WORD_SYM(val) = Normalize_Vector_Type_Symbol(...)`, so a struct declared with `float!`
reports `float32!` ever after and `spec-of` shows the settled spelling rather than the one that
was typed. **An inner struct is left exactly as written**, which is why a field declared
`[struct! pair8!]` still names `pair8!` in the spec while the value that field answers molds its
whole layout.

**A number reaches a struct field the way it reaches a vector.** `assign_scalar` converts
through both an integer and a double and then picks: an integer written to a float field goes in
as the number, and a decimal written to an integer field is truncated towards zero. **A whole
array field written from a vector is a copy of bytes**, taken only when the vector's element
width matches the field's and there are exactly as many - **a vector of the right length but the
wrong width is refused rather than converted.**

**Binding copies, and a copy that drops the line-break flags molds every script's blocks on one
line.** The flags shift with the copy, because a bound block starts at its head where the one it
came from may not. **A map's values are replaced rather than written over**, because a map is
not a series and cannot be walked by position - `compose/deep #[num: (val)]` is how a map
literal inside a body reaches an argument, and leaving it alone leaves VAL with no word to
resolve.

**A count of characters is a count of code points**, the same thing LENGTH? answers - not of
sixteen-bit units. **Half a surrogate pair is not a character**: encoding it gives a question
mark, so a count of one over a string beginning with an emoji answers the bytes of `?`.

**SORT/UNSTABLE is Jingchao Chen's Adaptive Symmetry Partition Sort**, and the exact permutation
it leaves equal keys in is what Rebol's own suite pins - so a faithful port is the only
implementation that answers it.

**Three separate things carry protection**: a word's slot, an object's fields and a series'
storage. **Protecting the word that holds a block does not protect the block**, which is what
makes `protect b` and `protect 'b` different requests.

---

## 181. What `system/catalog` lists, and why each list is the length it is

**`reflectors` comes from `sysobj.reb` and `base-defs.reb` generates SPEC-OF, BODY-OF and the
rest straight from it**, so a datatype left out of that list is a datatype the generated
function refuses. That is how WORDS-OF came to turn away the handle whose only readable field
is its type.

**`structs` is filled by REGISTER rather than at boot.** `sysobj.reb` declares it
`make map! []` with the comment "filled using `register` native function", so an empty map is
the finished state and not a gap.

**`actions` and `natives` are Rebol's declaration rather than a fact about any implementation.**
Both halves are host-language functions; `actions.reb` is the authority for which name is
which.

**`boot-flags` is what a boot flag may be, not what was passed.** `sysobj.reb` says so on the
line above it: "Official list of system/options/flags that can appear".

**A catalogue that lies about what asking for a name does is worse than a short one.** The
cipher list names only what the build can actually run; the filter list names all fifteen
sampling filters even where the sampling is the same, because naming them is honest and
accepting a name that means nothing is not. A catalogue is how a caller checks before asking.

**`system/locale` holds the month and weekday words, and the week begins at Monday there**,
which is what DATE's WEEKDAY counts from.

**`system/console` holds the line being edited and the ones already entered**, both staying as
they are until a console adapter fills them - the state a program with no console is in.

**`system/contexts/root` is declared and none**, which is what a real 3.22.1 answers. Worth
having as a field rather than absent: code walking the contexts finds three names, one of them
holding nothing, instead of a path that fails.

---

## 182. Eleven natives whose rule is the opposite of the obvious one

**A tuple takes a tuple or a plain number and nothing else**, and it refuses by naming the two
datatypes that do not go together - `Trap_Math_Args` is the same call every datatype makes for
the same reason. **A time on either side is the one that looks as though it ought to work**: a
duration is a number everywhere else in the language and is not one here.

**REPEAT takes `[number! series! pair! none!]`.** A number is how many times, **a series is what
to walk, and the counter is set to the series at each position rather than to what is there** -
so the body reads `x/1` for the value and `index? x` for where it is, exactly as FORSKIP does.
None runs nothing at all.

**/SKIP means the same thing for all four set operations**: the members are records of that
width rather than single items. A walk that compares item by item makes
`difference/skip "ač" "čbš" 2` answer the difference of six characters instead of three records.

**A handle publishes its type and nothing else**, so that is the whole of what WORDS-OF and
VALUES-OF find on one, and `PD_Handle` serves the same single field through a path.

**Every reflector on an operator asks the function it wraps, not the wrapper.**
`type = VAL_GET_EXT(value); goto of_type;` - the C reads the datatype the operator was made
from and starts the same switch again, so **an operator made from an action answers none for its
body and one made from a function answers the block it was written with.**

**`round/to 1 0` raises and `round/to 1.5 0` answers 1.** A whole-number scale of nothing is a
division by it; a decimal one is a scale too small to move anything.

**A needle's length is counted in characters, not in the host language's storage units.** A
string's position is a character index, so a needle holding anything outside the basic plane -
an emoji, most of them - makes /TAIL land one place too far for every such character in it. The
same applies to /PART: cutting by a UTF-16 length takes half of a non-basic-plane character, so
`insert/part s "🙂" 1` puts in a lone surrogate that reads back as a question mark.

**A REBOL word is case-insensitive, so `Domain` and `domain` are one word and one slot.**
Collecting locals by comparing the spelling gathers both, which gives the borrowed SET-COOKIES
three locals R3 does not list. **The first spelling seen is the one kept.**

**In a set operation a key already kept is left as it was rather than written over**, which is
what decides whose value survives. Without /CASE a map holding both `a` and `A` answers the
first of them for either spelling, so the pair collapses to one and keeps the earlier value -
and **`union m1 m2` keeps the left's value for every key the two share**, because the left went
in first.

**DIFFERENCE is symmetric** - what is in one or the other and not in both - **so the second
block contributes as well.** EXCLUDE is the asymmetric one. The two agree whenever the second
set is contained in the first, which is how a block DIFFERENCE that only ever looked at the
first block goes unnoticed.

**`make typeset! some-typeset` is that typeset.** Every other datatype answers itself for its
own MAKE, and a typeset that refused was the one value TYPESET! would not take.

**A percentage is not room for anything.** It is a number everywhere else and a proportion here,
and a map cannot be four per cent large - so it is refused where the plain number beside it is
taken.

---

## How this list is used

Wherever JEBOL matches, the finding is also a corpus entry under `corpus/` or a
test under `src/test/`, so the claim is rechecked on every build rather than
believed. Entries 3 to 16 are pinned that way. That pinning is what keeps the
findings useful now the binary is gone: the record cannot be retaken, but the
tests fail if JEBOL drifts away from it.

Entry 1 is not, because JEBOL does not match yet: its contexts nest, so an
object reaches its parent and can place a word the object itself has not
got. That is recorded as an open question in `spec/natives.allium` rather
than quietly implemented one way or the other. Entry 2 is pinned by
`BindingNamesTheHolderTest` instead of by the corpus, because what it
asserts is about where a definition lands rather than about a value.
Entry 17 is pinned by `WordCharactersTest` and by the corpus. Entries 18
and 19 are pinned by `ProtectByNameTest` and `ProtectedObjectTest`, and
entry 20 by `ConversionFamilyTest`, which carries the list of forty-five names
taken from Rebol rather than reasoned about. Entries 21 and 22 are pinned by
`MakeAndToFromTheSourceTest`, whose date expectations were each run against a
Rebol built from `scripts/build-r3.sh` before they were written down. Entry 23
is pinned by `BitsetConstructionFromTheSourceTest`, which asserts the copy's
answer as well as its mold, because the mold alone would not show that the
copy answers the opposite question.

Both of those came out of running the same seventy-five date expressions
through JEBOL and through that Rebol and diffing the two lists. Entry 22 was
not a suspicion anybody had; it was four lines that did not match. A sweep of
one datatype's whole surface is cheap and finds what reading the C for a
particular question does not.

An entry with neither should be read as a recollection, not a finding.

## 183. Rebol's checksum port computes xxh32 and xxh64 from an uninitialised context

**`XXH32_Starts` and `XXH64_Starts` in `u-xxhash.c` never touch the context they
were given.** Both are written the same way:

```c
void XXH32_Starts( XXH32_state_t *ctx )
{
    ctx = XXH32_createState();   /* overwrites the local parameter */
    XXH32_reset(ctx, 0);         /* resets the new one, then leaks it */
}
```

The assignment rebinds the parameter, so the caller's buffer -- the series
`p-checksum.c` allocated for the port -- is left exactly as it was found, and
every `XXH32_Update` after it accumulates into that. The freshly created state
is leaked once per port opened.

**`XXH3_Starts` and `XXH128_Starts`, four lines away, are written correctly** as
`XXH3_64bits_reset(ctx)` and `XXH3_128bits_reset(ctx)`. That is the whole of why
those two agree between a port and a one-shot and the other two do not.

**It is invisible below sixteen bytes, which is why nothing has caught it.** A
zeroed XXH32 state has the wrong accumulators and the right `total_len` and
`memsize`, and `XXH32_digest` only reads the accumulators once sixteen bytes have
arrived; under that it takes the seed path, and the seed field of a zeroed state
is nought, which is the seed a correct reset would have used. Rebol's own
`checksum-test.r3` writes two bytes and four bytes to the port, so its assertion
that the port and the one-shot agree passes on a real 3.22.5. At forty-three
bytes the same assertion fails there.

    checksum #{0BAD} 'xxh32         == port answer   -- agree, 2 bytes
    checksum <43 bytes> 'xxh32      #{E85EA4DE}      -- the published vector
    the same bytes through a port    #{A84939F9}     -- a real 3.22.5

**JEBOL keeps the published answer**, so its port and its one-shot agree at every
length and both match the xxHash vectors. Copying the other answer would mean
implementing a hash that is XXH32 with its accumulators zeroed, which is not
XXH32 and which upstream would call broken -- and the reference contradicts
itself here, so there is no single C answer to agree with. It reaches a script
through `file-checksum`, which sums through a port, so `checksum %file 'xxh32`
is where the two differ.

Pinned by `ChecksumOfAFileFromTheSourceTest`, which asserts a port and a one-shot
agree for all four xxHash methods at forty-three bytes.

## 184. CHECKSUM hands a file to FILE-CHECKSUM, and only for a digest

**`n-strings.c`'s CHECKSUM has a branch nobody would guess from the name.** Its
declared data type is `[binary! string! file!]`, and a file is not hashed: it is
handed to the REBOL function `file-checksum`, which opens the file and sums it
through a checksum port in 256 kB chunks.

```c
if (IS_BINARY(data) || IS_STRING(data)) { bin = VAL_BIN_DATA(data); }
else {
    REBVAL *func = Find_Word_Value(Lib_Context, SYM_FILE_CHECKSUM);
    if (func && IS_FUNCTION(func) && sym > SYM_CRC32 && sym <= SYM_SHA3_512) {
        if (D_REF(ARG_CHECKSUM_WITH) || D_REF(ARG_CHECKSUM_PART))
            Trap0(RE_BAD_REFINES);
        /* evaluate [file-checksum data method] */
    }
    Trap0(RE_FEATURE_NA);
}
```

**`sym > SYM_CRC32 && sym <= SYM_SHA3_512` is a range over the symbol table and
not over the catalogue**, and the two are ordered differently. Symbols 217 to
232 are md4, md5, sha1, sha224, sha256, sha384, sha512, ripemd160, xxh3, xxh32,
xxh64, xxh128, sha3-224, sha3-256, sha3-384, sha3-512 -- sixteen digests, with
the xxHash family sitting in the middle of the SHA ones. Below the range are
hash, adler32, crc24 and crc32; above it is everything else including tcp. So
`checksum %file 'adler32` is `feature-na` although ADLER32 is a catalogue
method, and `checksum %file 'xxh3` reads the file although the catalogue lists
xxh3 after sha3-512.

**The refinement check sits inside the range test, so the two refusals cannot be
reordered.** `checksum/part %file 'md5 1` is `bad-refines`; `checksum/part %file
'adler32 1` is `feature-na`, though both complaints are true of it.

    checksum %f 'md5        #{6F78...}    -- the file's digest
    checksum/part %f 'md5 1 bad-refines
    checksum %f 'adler32    feature-na
    checksum/part %f 'adler32 1  feature-na
    checksum %nothing 'md5  cannot-open

**Getting this wrong is silent.** JEBOL hashed the file's *path* as text, so
every call answered a well-formed digest of the wrong thing, the same width and
shape as the right one, and a missing file answered a digest rather than
failing. Pinned by `ChecksumOfAFileFromTheSourceTest`.

## 185. r3-head stops early or segfaults on a long base36-and-base85 script

**A single script doing a lot of base36 and base85 work does not finish.** The
first run of one such probe exited 139 -- SIGSEGV -- after printing sixteen
lines; the second run of the same file exited 0 and stopped silently at the same
place, having never printed the four lines after it.

Every call in it answers correctly on its own, and short scripts that pair the
suspicious calls survive:

    debase "BE" 85              #{68}          -- alone, fine
    debase "u" 85               invalid-data   -- alone, fine
    debase "z"/"zz"/"!!"/"BE"   all four, one script, fine
    enbase <9 bytes> 36         out-of-range, then base85 still works

So it is cumulative rather than triggered by one expression, and it is not
deterministic between runs of the same bytes.

**What this costs a porter:** a long probe script comparing the two
implementations will appear to show JEBOL producing answers where Rebol produces
none, for every assertion after the point it quietly stopped. That reads as a
divergence and is not one.

**Measure base36 and base85 in small invocations, one or a few expressions at a
time.** It is slower and it is the only way to trust the answers.

This is the third place the canonical reference has been caught being
unreliable, after `checksum-test.r3` failing thirteen assertions in roughly one
run in eight, and `open/new` on a missing directory answering differently on
consecutive runs.

## 186. ECDH/INIT refills the handle it was given rather than making a new one

**`ecdh/init k curve` answers the same handle back**, with fresh key material in
it. Rebol's own `dh-test.r3` depends on it and never says so:

    --assert true? release k-Alice
    --assert handle? ecdh/init k-Alice ecurve   ;- the answer is thrown away
    --assert binary? pub-Alice: ecdh/public k-Alice

Nothing assigns the result of the second line, so the third only works if the
word `k-Alice` still names a usable key -- which it does because the handle it
holds was refilled in place. A build that answers a *new* handle leaves
`k-Alice` released, `ecdh/public` answers none, and the next line raises "ecdh
does not allow none! for its public-key argument".

**RELEASE has to be real for that to be observable.** In JEBOL it answered true
and freed nothing, so a released key went on publishing. Both halves were hidden
behind the same stop: the loop over `system/catalog/elliptic-curves` never
reached the re-initialisation because it failed on the first curve.

Pinned by `EveryCurveInTheCatalogueFromTheSourceTest`, which asserts
`same? alice ecdh/init alice 'curve` and that a released key publishes nothing.

## 187. Eight of Rebol's thirteen curves are not in a modern JDK, explicit parameters included

**`secp192r1`, `secp224r1`, `secp192k1`, `secp224k1`, `secp256k1` and the three
Brainpool curves were withdrawn from the JDK's own provider.** What makes it
awkward is that the refusal is by *name*, so handing the parameters over
explicitly does not get round it:

    KeyPairGenerator.getInstance("EC").initialize(explicitSecp192r1Parameters);
    -> InvalidAlgorithmParameterException:
       Curve not supported: secp192r1 [NIST P-192,X9.62 prime192v1]

So there are two options and no third: carry the curve parameters and the point
arithmetic, or answer none for eight of thirteen curves while the catalogue
names all thirteen. JEBOL carries them, in `WeierstrassCurve`.

**Two checks are worth more than any amount of reading.** The first is that each
generator is on its own curve and has the order written beside it, which is what
a mistyped parameter fails and nothing else does. The second is interoperation
in both directions: a real 3.22.5 verifying an ECDSA signature this build
produced, and this build verifying one it produced. The second caught a defect
the first could not -- `bp512r1` signatures were rejected because its DER
SEQUENCE runs past 127 bytes and needs the long-form length, `30 81 84`, which
is the only curve of the eight where that happens.

## 188. UNBIND loosens the block it was handed, and MODULE depends on that

**`Unbind_Block(VAL_BLK_DATA(word), D_REF(2) != 0); return R_ARG1`** is the whole
of the UNBIND native. The walk writes the binding fields of the values where they
stand, and what comes back is the argument. The declaration in `natives.reb` says
so out loud: `word [block! any-word!] {A word or block (modified) (returned)}`.

**Answering a copy instead is invisible until a caller keeps the block.** MODULE
is one line -- `make module! unbind/deep reduce pick [[spec body] [spec body
words]] not mixin` -- and the body inside that REDUCE is the caller's own block.
Every later step of building the module happens to whatever UNBIND handed back:
the EXPORT keywords are parsed out of it with `remove skip`, the hidden names are
collected, the body is bound to the new context. With a copy in the middle, all
of that happens to a block nobody outside can reach, and Rebol's own test for it
fails:

    >> body: [export 'b]  module [] body  body
    == ['b]            ; r3-head 3.22.5, and now JEBOL
    == [export 'b]     ; JEBOL before, because UNBIND copied

**Protection does not stop it.** A protected block unbinds without complaint on
r3-head, because a word's binding is not part of the series holding it. So the
in-place write reaches past the protection check that a real change to the series
would hit.

## 189. BIND/ONLY is the only refinement that takes depth away, and /SET follows it

**`flags = D_REF(4) ? 0 : BIND_DEEP` is the first line of the C's BIND.** Every
other refinement adds a flag; /ONLY takes the deep flag off. So the loop's
`ANY_BLOCK_OR_MAP` arm never runs and a block standing inside the block being
bound is stepped over rather than walked into.

**/SET and /NEW are branches inside that same loop, so they inherit the depth**:

    if ((mode & BIND_ALL) || ((mode & BIND_SET) && (IS_SET_WORD(value))))
        Append_Frame(frame, value, 0);

Which is the whole of what makes a module's private names private.
`make-module*` collects the module's variables with one call, and the comment
beside it is the rule:

    bind/only/set body context
    ; Only top level defined words are module variables.

So a module body writing `attempt [z1: 12345] z1` gets no Z1 of its own, the
set-word stays unbound, and the read after it raises not-defined -- which is
exactly what `module-test.r3` asserts four times over. Collecting deeply instead
makes the module quietly answer 12345, and the four assertions were the only
thing that would ever have said so.

## 190. The user context opens with REBOL and LIB-LOCAL, and a bare LIB-LOCAL reads it

**One line of `sys-start.reb` puts both there before any user code runs:**

    tmp: make object! 320
    append tmp reduce ['REBOL :system 'lib-local :tmp]
    system/contexts/user: tmp

REBOL holding the system object is a convenience. LIB-LOCAL holding the context
itself is the interesting half, and naming a context after itself is not a trick.
Every module gets a LIB-LOCAL holding what that module imported --
`context/lib-local: any [mixins make object! 0]` in `make-module*` -- so
`lib-local` inside a module is the module's own import library. Code at the top
level of a script is not inside a module and what it imported went into the user
context, so the honest answer to the same question there is the user context.
That is what lets one piece of code be either a script or a module without the
word meaning something different.

    >> same? lib-local system/contexts/user
    == true

## 191. A child interpreter must be told where the application data is, not only where the root is

**Rebol needs no such arrangement and JEBOL does.** In a real Rebol a parent and
a child each work the data directory out from the operating system and each get
the same answer. Here an embedding host is entitled to move `system/options/data`,
and a confined host must, because the operator's own hidden folder is outside the
directory the script was given.

So a child that works it out for itself gets a different answer from its parent.
The modules directory follows the data directory, so the child then looks for
modules where its parent did not put them, and `import 'whatever` fails with
"module not found" on a module the parent installed a moment earlier. Rebol's own
`module-test.r3` does exactly that: it writes two modules into
`system/options/modules` and then runs a second interpreter through
`system/options/boot` that imports them by name.

Which is the same fact as the root, one level down. Confinement is not only what
a script may reach; it is what the paths mean. The launcher therefore carries
`--data` beside `--root`, and `followTheApplicationDataDirectory()` rewrites it
whenever the host moves the directory.

## 192. SORT and `<` are one comparison in the C, and a pair or a tuple sorted by its text is a loop that never ends

**`Cmp_Value` is reached from `Compare_Values`, which is what both the sort's
default comparator and the ordering natives call.** So a datatype cannot be
ordered one way by `<` and another way by SORT, and a default that compares the
written form is wrong for every value whose text does not rise with it:

    >> sort [3x0 20x0 100x0]
    == [3x0 20x0 100x0]        ; r3-head 3.22.5
    == [100x0 20x0 3x0]        ; JEBOL before, because "100" is below "20"

    >> sort [255.0.0 16.0.0 2.0.0]
    == [2.0.0 16.0.0 255.0.0]  ; r3-head 3.22.5
    == [16.0.0 2.0.0 255.0.0]  ; JEBOL before

`Cmp_Pair` settles it on the x halves and looks at the y halves only to break a
tie. `Cmp_Tuple` walks the parts to `MAX(len1, len2)` over a zero-filled byte
array, so `1.2.3` is below `1.2.3.4` and equal to `1.2.3.0` -- which is what
`=` says of them and not what `==` does, because strict equality compares the
lengths as well.

**What a wrong order costs is not a wrong answer that gets printed.** Rebol's
own PDF encoder sorts its cross-reference table with `sort/skip xref 2` and
then walks it in runs of consecutive object numbers:

    while [not tail? xref][
        i: to integer! xref/1/1
        n: get-xref-count xref i
        ...
        while [i < n] [ ... xref: skip xref 2 ++ i ]
    ]

With the table out of order, `get-xref-count` answers a count below the index
the outer loop is standing on, the inner loop never runs, the cursor never
advances, and the encoder never finishes. Saving a PDF whose object numbers
have gaps in them hung for ever where a real Rebol takes a millisecond.

## 193. MAKE TIME! from a string is a scanner of its own, and it takes a leading sign

**`Make_Time` hands a string straight to `Scan_Time`**, the same function the
reader calls for a time written in source, and the C's own comment lists what
it takes:

    HH:MM       as part1:part2
    HH:MM:SS    as part1:part2:part3
    HH:MM:SS.DD as part1:part2:part3.part4
    MM:SS.DD    as part1:part2.part4

So a two-part time **with a fraction** is minutes and seconds:
`to time! "12:34.5"` is `0:12:34.5` where `to time! "12:34"` is twelve hours.
A fraction may be written with a comma. An AM or PM suffix moves the hour, with
`12:00AM` midnight and `12:00PM` noon, and an hour above twelve with a suffix
is refused.

**A leading sign is read before any of that, and the plus is accepted rather
than merely tolerated**: `if (*cp == '-') {cp++; neg = TRUE;} else if (*cp ==
'+') cp++`. Two signs in a row is a mistake, which the C says in a comment
beside the line that refuses it -- `// small hole: --1:23`.

That is not a corner. A PDF date carries its offset as `+02'00'`, and Rebol's
own PDF codec turns it into a zone with `to time! rejoin [z hour ":" minute]`,
sign still on the front. A MAKE that refuses the plus loses the whole object
the date was in -- here, the document information dictionary of every PDF whose
dates live in a compressed object stream.

**The string is qualified before the scanner sees it**, by `Qualify_String`
with a maximum of thirty characters and no UTF-8 allowed, and its three checks
each have an error id of their own rather than a general refusal. Space and tab
are the only whitespace it steps over -- `IS_LEX_SPACE` and `IS_SPACE` are both
that pair and nothing else -- so a leading newline is not skipped and
`to time! "^/2:00"` is bad-make-arg, while `to time! "2:00^/x"` is 2:00 because
the newline never ends the run and the scanner ignores what it has not reached.

    >> to time! "2:00 am"
    ** Script error: invalid-chars     ; a second run after the content
    >> to time! "   "
    ** Script error: too-short
    >> to time! (30 characters or fewer)   -- fine; thirty-one is too-long

**And nothing guards the arithmetic once the parts are read.** The hour is
checked against `MAX_HOUR`, which is `MAX_SECONDS / 3600` or 2562047, and the
minutes and seconds are then added to it in signed sixty-four bit nanoseconds
with no check at all:

    >> to time! "2562047:00"
    == 2562047:00
    >> to time! "2562047:59:59"
    == -2562047:34:34.709551616

Reproduced rather than corrected, because the same arithmetic in the same width
gives it for nothing and it is what a script comparing against a real Rebol
gets.

**The block arm is its own small dialect**: up to three numbers filled from the
left, only the last of which may be fractional and only the first of which may
be negative, with every prefix sum checked against the widest time as it is
built. `to time! [1]` is one hour; `to time! [1 -2 3]` is refused.

## 194. A refused MAKE names the datatype, not a word spelling it

**`Trap_Make` puts the datatype value in ARG1**, so a script reading the error
gets something it can compare against `time!` and test with `datatype?`:

    >> e: try [make module! 10]  reduce [e/arg1  type? e/arg1]
    == [#(module!) #(datatype!)]     ; r3-head 3.22.5
    == [module! #(word!)]            ; JEBOL before

One helper answered all sixty-eight bad-make-arg refusals in JEBOL and all
sixty-eight carried a word. Nothing in JEBOL's own tests asserted on it -- they
check the id -- which is how it survived, and Rebol's own `evaluation-test.r3`
turned out to be measuring it after all.

## 195. FIRST+ moves the word, not the series, and a CSV encoder is where the difference shows

**`first+` takes a word rather than a value** -- `first+: native ['word [word!]]`
-- and the C changes the index held in the variable, leaving the series behind
it untouched:

    *D_ARG(1) = *value;
    index = VAL_INDEX(value);
    if (index < tail) VAL_INDEX(value) += ...;
    return Do_Ordinal(ds, 1);

So the answer is the first item of the position the word named a moment ago,
and the word now names one position further along. At the tail there is nothing
to answer and nowhere to go: the guard is `if (index < tail)`, and FIRST asks
the ordinary question and gets none.

**Removing the item instead reads identically at the call site.** Both answer
the first item; both leave the word naming a series with one fewer item in
front of it. The difference is the *head*, and only a caller who kept a name
for the series ever looks there. Rebol's own CSV encoder is such a caller:

    output: make block! 2 * length? data
    unless empty? data [append output format-field first+ data]
    foreach x data [append append output delimiter format-field :x]

With a FIRST+ that removes, `to-csv ["x x"]` empties the caller's block and
answers the right string, so the only thing that notices is the assertion right
after it -- which Rebol's suite writes, with the comment "we need to make sure
original was not modified" beside it.

## 196. NUMBER? is the one native whose declaration spells out `unset!`

`value [any-type! unset!]` -- and both halves are needed, because `any-type!`
does not include the unset:

    >> find to block! any-type! #(unset!)
    == none            ; r3-head 3.22.5

Every other native with an untyped parameter refuses an unset argument, which
is the right default. NUMBER? is asked *whether there is a number there*, and a
word holding nothing is a perfectly good no; refusing turns the question into
an error the caller then has to guard, which is the guard they were asking
NUMBER? to be.

    >> number? ()
    == false

The other false is a NaN, by the same reading -- `if (!isnan(VAL_DECIMAL(...)))
result = TRUE` -- so the one decimal that is not a number answers truthfully.

## 197. COPY of an error is COPY of an object, and /PART of either is refused

An error is an object with eight fixed fields underneath, so the same arm
answers both and `copy try [1 / 0]` is a second error carrying the same code,
type and id. Writing a field of the copy leaves the original alone; the field
*values* stay shared until /DEEP says otherwise.

Rebol's own copy-test walks every datatype it calls copyable and asserts
`not same? :c :x` of each, and the error is on that list. A copy that is the
same error is the quiet kind of wrong: a caller who takes one so as to annotate
it is annotating the one the raiser still holds.

**/PART on anything with no order is refused rather than ignored** --
`if (D_REF(2)) Trap0(RE_BAD_REFINES);` at the top of the arm. "The first so
many" of an object names nothing. A bitset and a map are not in that group and
accept /PART without complaint on r3-head, which is worth knowing before
widening the refusal.

## 198. A percent is written from its own digits, with the point moved twice

`Emit_Decimal` writes both decimals and percents, and the percent flag changes
exactly two things. The digits come from the stored value and then `e += 2`
moves where the point goes, which is exact. And a radix point with nothing
after it is dropped rather than given a zero:

    if (*(cp - 1) == point) { if (trim) cp--; else *cp++ = '0'; }

So `mold 3e34%` is "3e34%" where a decimal of the same size is "3.0e34".

**Multiplying by a hundred first is a rounding step the C does not take**, and
it moves the last digit either way:

    >> mold/all 12.345%
    == "12.345%"                 ; r3-head; multiplying gives 12.345000000000001%
    >> mold/all 1.5%
    == "1.4999999999999999%"     ; r3-head; multiplying gives the tidier 1.5%

Neither is more correct in the abstract. One of them is what a real Rebol
writes.

## 199. POKE on a bitset answers the set, and on everything else the value

The arm sets the bits and leaves the switch -- `if (Set_Bits(...)) break;` --
so the function answers its first argument rather than its third. Every other
POKE reaches the line that answers the value written.

It reads as an inconsistency and it is the only answer available. A bitset has
no slot to have written a value into: what went in was a request to turn bits
on or off, and a character, a string, a block or a range may say so in one
call. There is no "the value at that position" to hand back.

## 200. PROTECT-SYSTEM never ran here, because CLEAN-PATH stopped mezz-tail two lines earlier

`mezz-tail.reb` is the last file Rebol boots and it ends with three lines:

    if system/options/boot [system/options/boot: clean-path system/options/boot]
    protect-system
    unset 'protect-system

JEBOL set `system/options/boot` before the library loaded, so the guard was
true; CLEAN-PATH consults the working directory even for an absolute path, and
an interpreter whose filesystem is installed *after* the boot cannot answer
that. The file stopped on that line, and the two lines it stopped before are
the ones that seal the system object.

Nothing said so. `borrowedLoadFailures()` was empty, every word mezz-tail
defines was defined, and the only visible symptom was that
`system/catalog/errors: none` quietly worked -- three assertions in Rebol's own
error-test, and no other measure in the build.

**The launcher is now named after the library has loaded**, which is the only
ordering that works here: nothing in the library reads the field, and
CLEAN-PATH cannot run before a filesystem exists.

**And BIND had to stop refusing a protected block for it.** Sealing the
catalogue turned `bind system/catalog/errors/(e/type)/(e/id) e` from an answer
into a refusal, where a real Rebol binds it happily. Same reasoning as UNBIND
in finding 188: a word's binding is not part of the series holding it, so
`protect` on the series does not cover it. APPEND to the same block is refused
in both.

## 201. LAST takes a series, a tuple or a gob, and the typeset is where the error comes from

`value [series! tuple! gob!]`, so `last 1.2.3` is 3 and `last 1.2.3.0` is the
nought that was written rather than one of the zeros a shorter tuple is padded
with.

What the typeset buys is the shape of the refusal. Anything else fails the
declaration and reports expect-arg naming the function, the *parameter* and the
datatype; refusing inside the body reports cannot-use, which names the function
and the value and leaves ARG2 none. A script reading `e/arg2` to find out which
argument it got wrong gets nothing from the second shape, and Rebol's own
error-test asks exactly that of `last :some-function`.

## 202. A date's parts are written by the same numbers they are read by

`sym = SYM_YEAR + Int32(arg) - 1` runs in `PD_Date` before the read and the
write arms part company, so `d/1: 2020` is `d/year: 2020` and a number past
fourteen is invalid-path on both sides. Reading by number worked here and
writing by number did not, which is the sort of half that nothing notices until
a caller loops over the parts -- and Rebol's own date-test does exactly that:
`repeat i 15 [try [d/:i: i]]`, then asserts on the date that comes out.

**Three parts take a value that is not a number, and each takes one kind:**

    d/utc: 27-Nov-2020/18:15:57+1:00   -> 27-Nov-2020/17:15:57
    d/julian: 2415020.5                -> 1-Jan-1900/0:00
    d/date: 5-May-2005                 -> the day, and its offset with it

UTC takes a date and leaves the instant it names with no offset at all, which
is the same moment written the other way. JULIAN takes a decimal and nothing
else -- `if (!IS_DECIMAL(val)) return PE_BAD_SET_TYPE;` -- and a count outside
what `Normalize_Date` accepts is type-limit rather than a date in the year
minus four thousand. DATE assigns the whole packed date field, `date =
VAL_DATE(val)`, and the offset is *in* that field, so writing a bare day into a
date that had an offset leaves it with none.

**WEEKDAY can be read and not written, and the refusal has an id of its own.**
It falls to `default: return PE_BAD_SET;` where the parts that refuse a
datatype answer PE_BAD_SET_TYPE, so a script can tell "this part is not
writable" from "this part will not take that".

## 203. Subtracting a date from something that is not a date is refused

    if (action == A_SUBTRACT) {
        if (!IS_DATE(val)) Trap_Math_Args(VAL_TYPE(val), A_SUBTRACT);
        num = Diff_Date(date, VAL_DATE(arg));
    }

The only subtraction a date on the right takes part in is one where the left is
a date too, and the answer is the number of days between them. A date on the
*left* is a different question and takes an integer, a decimal, a time or a
date. So `1-Jan-2000 - 0` is a date and `0 - 1-Jan-2000` is not a number of
days in the other direction; it is not-related, naming the datatype that was on
the left.

Subtraction is not symmetric here because the two sides mean different things:
a date minus a count is a date, and a count minus a date is nothing.

## 204. AJOIN evaluated its block twice, and only a side effect could tell

The C reduces into a buffer and then walks the buffer -- one `Reduce_Block`,
no second one. JEBOL reduced once to build the pieces and again to read the
first value's datatype off, and the answer was right every time:

    >> counted: 0
    >> bump: does [counted: counted + 1  ajoin ["#" counted]]
    >> ajoin ["<" bump ">" bump "!"]
    == "<#1>#2!"       ; correct in both
    >> counted
    == 2               ; r3-head.  JEBOL said 4.

Nothing in the text says so. What noticed was Rebol's own BBCode codec: its
table emitter writes `ajoin [{<} datatag get-col-width {>} data ...]`, and
GET-COL-WIDTH counts the column it is on. The second pass walked the counter
past the end of the widths, so the second column of a `[csv widths='100 20 *']`
table came out with no width -- one wrong attribute in one of sixty test cases,
and the only visible trace of a function being called twice everywhere.

## 205. A comma ends nothing, and is the other decimal point

`Lex_Map` gives `,` LEX_SPECIAL, not LEX_DELIMIT, so it never ends a lexeme.
What it means depends on what the lexeme turns out to be, and there are three
answers.

**In a number it is the point.** `Scan_Decimal` steps over one, written either
way -- `if (*cp == ',' || *cp == '.') cp++;` -- and the whole lexeme has to be
consumed, so there is exactly one and it may not be spelt both ways:

    1,2   -> 1.2        1,2,3 -> invalid
    1,    -> 1.0        1.2,3 -> invalid
    ,1    -> 0.1        ,.5   -> invalid
    $1,5  -> $1.5       1,5%  -> 1.5%       1:2,5 -> 0:01:02.5

A tuple is dots only, so `1.2.3` is a tuple and `1,2,3` is nothing.

**In a file, url, email or issue it is an ordinary character**, because those
run to the next delimiter.

**And in a word it is forbidden.** `LEX_SPECIAL_COMMA` is in `LEX_WORD_FLAGS`
beside at, percent, backslash, pound, dollar and colon, so `a,b`, `a,` and a
comma standing alone are each invalid rather than two values.

JEBOL treated it as a delimiter in three places at once: `endsLexeme`,
`skipIgnorable`, and nowhere in the number patterns. Taking it out of the first
two without the third turns `1,2` into a NumberFormatException from
`Double.parseDouble`.

## 206. A colon before a slash settles a lexeme as a url before anything else asks

`scanword` tests the colon flag first and returns TOKEN_URL from inside that
arm, so the characters a word may not contain never get a say. The body then
runs past the delimiters that stopped the word:

    cp = scan_state->end;
    while (*cp == '/') {
        cp++;
        while (IS_LEX_AT_LEAST_SPECIAL(*cp) || *cp == '/' || *cp == 0x7F) cp++;
    }

So `a:/x<y`, `a:/x,y` and a url with a control character in it are each one
value. JEBOL classified the lexeme as a url and then let the angle-bracket
splitting -- the machinery that makes `word<tag>` two values -- override it.

**And Java's `.` does not match a line terminator.** The url pattern
`[a-zA-Z][a-zA-Z0-9+.-]*:.+` refused `a:/` followed by U+0085, because NEL is
one of the five characters Java's regex counts as ending a line. The pattern
needs DOTALL; nothing else about the character is unusual, and `Lex_Map` makes
everything from 0x80 up an ordinary word character.

## 207. A gob's position is an unsigned count, and stepping back past the head wraps

Every other series stops at its head. `VAL_GOB_INDEX` is a `REBCNT` and no arm
of the C guards the arithmetic, so a gob's position runs off both ends:

    >> g: make gob! []  append g make gob! 1x1   ; three children
    >> index? skip g -2
    == 4294967295
    >> index? skip g -1
    == 0
    >> index? skip g 5
    == 6

INDEX? adds one in the same width, which is why stepping back one answers zero
rather than the largest number: the wrap happens twice.

**The arms that then read or write a child clamp to the pane, and a wrapped
position is a huge number rather than a small one, so it clamps to the TAIL.**
That is the whole of why MOVE works backwards. MOVE is `take/part source
length` and then `insert (skip source offset) part`, so `move g -1` takes the
child off the front and inserts it at a position that resolves to the back.

Reproduced rather than corrected. Rebol's own gob-test asserts the arrangement
MOVE leaves, and an implementation that clamped at the head -- which is what a
series does and what JEBOL did -- answers a different pane for the same two
lines.
