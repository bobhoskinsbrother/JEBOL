# Things about REBOL 3 that only the binary knows

A running note, kept while porting REBOL 3 to the JVM.

Each entry is behaviour that is not written down anywhere we could find, and
that we learned by asking a real interpreter. Some are surprising. A few
look like inconsistencies rather than decisions, and those are flagged as
questions rather than as complaints - a reimplementation is not evidence
that the original is wrong.

The interpreter used throughout is Oldes Rebol 3.22.1, invoked as `./r3`.
Every claim below has the command that produced it, so it can be rerun
against a different build and disagreed with.

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

---

## How this list is used

Wherever JEBOL matches the binary, the finding is also a corpus entry under
`corpus/` or a test under `src/test/`, so the claim is rechecked on every
build rather than believed. Entries 3 to 16 are pinned that way.

Entry 1 is not, because JEBOL does not match yet: its contexts nest, so an
object reaches its parent and can place a word the object itself has not
got. That is recorded as an open question in `spec/natives.allium` rather
than quietly implemented one way or the other. Entry 2 is pinned by
`BindingNamesTheHolderTest` instead of by the corpus, because what it
asserts is about where a definition lands rather than about a value.
Entry 17 is pinned by `WordCharactersTest` and by the corpus. Entries 18
and 19 are pinned by `ProtectByNameTest` and `ProtectedObjectTest`, and
entry 20 by `ConversionFamilyTest`, which carries the list of forty-five
names read out of the binary rather than reasoned about.

An entry with neither should be read as a recollection, not a finding.
