# Diagnosed suite-cluster fixes (parallel diagnosis, 2026-08-12)

From a 9-agent workflow over series/evaluation/parse-test failures. Each is a
root-cause hypothesis read from the C, to verify by one probe then implement.
The five FORM/AJOIN/REDUCE-into/FIND-same/POKE-binary items are DONE.

## [high] series:join-reduce-form / FORM (assert #66, series-test.r3 line 92) -- DONE
**Cause:** FORM of an any-word keeps the word's sigil. JEBOL renders a lit-word as 'a in both mold and form, so form ['a 'b 3] produces "'a 'b 3" instead of "a b 3". The C prints the bare symbol name for a lit-word, set-word, get-word, refinement and issue whenever the value is FORMed (not molded); only when molded does it add the tick/colon/slash/hash.

**Where:** src/main/java/org/jebol/domain/value/Molder.java, render(), the `case WordValue word` branch (around line 118). It currently returns word.toString() for both mold and form.

**Fix:** Change the branch to `case WordValue word -> forReading ? word.toString() : word.spelling();` so FORM drops the sigil while MOLD keeps it. word.spelling() is the bare symbol; this matches the C for every any-word datatype, so it is why only the lit-word assert (#66) fails while the string/tag asserts pass.

**C:** Mold_Value in rebol3-source/src/core/s-mold.c, cases REB_SET_WORD/REB_GET_WORD/REB_LIT_WORD/REB_REFINEMENT/REB_ISSUE (lines 1412-1450): each does Append_UTF8(name) when !molded, and only Emit("'W" / "W:" / ":W" ...) when molded.

## [high] series:join-reduce-form / AJOIN/with (asserts #53-55, series-test.r3 lines 75-77) -- DONE
**Cause:** AJOIN drops NONE but not UNSET when /all is absent. The C's Form_Reduce drops a value when its type is <= REB_NONE and !all, which covers BOTH unset and none. JEBOL's filter only removes NoneValue, so an unset piece survives; without a separator it forms to "" and the plain-ajoin asserts pass by accident, but with /with the separator makes the surviving empty piece visible: ajoin/with ["a" #(unset) 3] #"/" gives "a//3" instead of "a/3". Under /all the unset must be kept and formed to "" (that path is already correct: ajoin/all/with expects "a//3").

**Where:** src/main/java/org/jebol/domain/eval/Natives.java, the ajoin native (define("ajoin", ...) around line 4835), the .filter on the reduced pieces.

**Fix:** Widen the filter so that when /all is absent it drops BOTH none and unset: `.filter(piece -> refinements.contains("all") || (!(piece instanceof NoneValue) && !(piece instanceof UnsetValue)))`. Keeping unset under /all is already right because runTogether/Molder.form of an unset is the empty string.

**C:** Form_Reduce in rebol3-source/src/core/s-mold.c line 1603/1619: `if (VAL_TYPE(DS_TOP) <= REB_NONE && !all) DS_DROP` - REB_UNSET and REB_NONE both sort <= REB_NONE, so both are dropped unless /all.

## [high] series:join-reduce-form / REDUCE - reduce block! (#394) and reduce paren! (#398) -- DONE
**Cause:** REDUCE/INTO refuses a path target. The /into target parameter is typed Set.of(Datatype.BLOCK), but the C declares it any-block!, which includes path. make path! 3 yields a PATH, and the argument type check raises expect-arg before the insert runs, so both reduce tests fail on their reduce/into-into-a-path assert. The insert logic itself already works on a path (a path is a BlockValue with datatype PATH and passes the `target instanceof BlockValue` guard). COMPOSE already gets this right by using Typeset.ANY_BLOCK.members() for its own /into.

**Where:** src/main/java/org/jebol/domain/eval/Natives.java, the reduce native (define("reduce", ...) line 6875), the Parameter.belongingTo("into", "target", Set.of(Datatype.BLOCK)) declaration on line 6876.

**Fix:** Replace Set.of(Datatype.BLOCK) with Typeset.ANY_BLOCK.members() so the /into target accepts path, paren, set-path etc, matching how compose declares its /into on line 6940.

**C:** REDUCE native spec: /into target [any-block!] (rebol3-source/src/boot/natives.reb); Copy_Stack_Values inserts into any-block target in rebol3-source/src/core/n-data.c.

## [medium] series:join-reduce-form / JOIN (asserts #14-16, series-test.r3 lines 26-28)
**Cause:** A parameter with no type block accepts an unset value, so nothing raises. Real Rebol treats a bare parameter (no datatype block) as every type EXCEPT unset, so passing #(unset) as join's `rest` argument raises expect-arg during argument binding. JEBOL's Parameter.accepts returns true for any datatype when the accepted-type set is empty, so join binds rest = unset, then `append (copy "a") reduce :rest` forms the unset to "" and returns "a" with no error, so error? is false. JEBOL cannot currently tell 'any-type! (includes unset)' from 'no type block (excludes unset)' - both collapse to the empty set.

**Where:** src/main/java/org/jebol/domain/value/Parameter.java accepts() (lines 89-90) plus the FunctionSpec parsing that assigns the empty typeset; natives that genuinely accept unset (form, mold, type?, unset?, get, set, same?, equal?, quote, reduce/only) must then declare Datatype.UNSET explicitly.

**Fix:** Distinguish 'accepts any value except unset' (the default for a no-type parameter, which must reject UNSET) from 'any-type! including unset'. Make accepts() on an empty set return false for Datatype.UNSET, and add an explicit any-including-unset typeset for the natives that need it. This is cross-cutting, so audit every Set.of()/takes() native that must still accept an unset argument before flipping the default.

**C:** Type_Check / Do_Args in rebol3-source/src/core/c-do.c raise RE_EXPECT_ARG; a bare param's typeset excludes unset (TS_VALUE), set in Make_Paramlist. join itself is mezz: rebol3-source/src/mezz/base-series.reb line 33 (append either series? :value [copy value][form :value] reduce :rest).

## [high] series:find-select-sort / FIND & SELECT — FIND block! block! -- DONE
**Cause:** FIND/SAME with a block needle compares the run by content equality, not series identity. blk = reduce ["a" "b" a b] holds two fresh strings at 1-2 and the same string objects a,b at 3-4; find/same reduce [a b] must skip the fresh copies (identity) and land at index 3, but JEBOL matches index 1. matchesHere routes a block needle to runMatchesAt with mindingIdentity=true, and runMatchesAt (Natives.java ~line 7586) then calls Comparison.identicallyEqual, which is `same datatype && strictlyEqual` — i.e. content equality — so two distinct-but-equal strings compare true. It must ask series-storage identity instead.

**Where:** Natives.java, runMatchesAt (~line 7578-7593): when mindingIdentity is true use Comparison.isSameValue(...) in place of Comparison.identicallyEqual(...).

**Fix:** In runMatchesAt replace the mindingIdentity branch `Comparison.identicallyEqual(...)` with `Comparison.isSameValue(...)`. isSameValue already checks sharesStorageWith()+index for series and content-equality for integers, so the numeric same cases (find/same blk [1 2] -> 7) still pass while the string-identity cases pass. Matches C Find_Block's `Compare_Values(value, val, 3)` (mode 3 = same series node), t-block.c line 150.

**C:** t-block.c Find_Block, block-vs-block branch, line 149-150 (AM_FIND_SAME -> Compare_Values mode 3).

## [high] series:find-select-sort / FIND & SELECT — FIND with negative skip / SELECT/skip/last -- DONE
**Cause:** When /reverse or /last is combined with /skip (or a negative skip forces the record path), JEBOL steps backward by the record width and starts from the wrong index, but the C forces a single-element step (skip=-1) for /reverse and /last and ignores the skip width entirely. positionOfMatchInRecords (Natives.java ~7626) sets width=abs(stride) and from=index-2 for every backward search. For find/skip/reverse tail "acd000cde" "cd" -3 it checks positions 8,5,2 (step 3) and misses the 'cd' at index 6 -> none instead of "cde". For select/skip/last [a b a c] 'a 2 (index 1 at head) from=index-2=-1 so the loop never runs -> none instead of 'c'. The /last case also needs the start position end-needleWidth, which the non-record path positionOfMatch already computes correctly.

**Where:** Natives.java: positionOfMatchInRecords (~7626-7645) — when refinements contain "reverse" or "last", use a step of 1 and the same start positions as positionOfMatch (reverse: at=index-1 walking to head; last: at=end-needleWidth walking to the original index). Simplest: in positionSearched (~7337) route any search with reverse or last to positionOfMatch regardless of stride, since the C overrides skip to -1 for both.

**Fix:** Mirror positionOfMatch's reverse/last setup (lines 7410-7418) or short-circuit in positionSearched: `if (refinements.contains("reverse") || refinements.contains("last")) return positionOfMatch(...)`. This fixes #279,#280,#305 and keeps the currently-passing bitset reverse case (line 394) working, because C forces skip=-1 there too.

**C:** t-block.c Find_Block lines 112-122 and t-string.c find_string lines 132-142: `if (flags & (AM_FIND_REVERSE|AM_FIND_LAST)) { skip=-1; if LAST index=end-len else index-- }`.

## [high] series:find-select-sort / FIND & SELECT — FIND with negative skip (forward) -- DONE
**Cause:** find/skip and select/skip raise out-of-range for ANY series when the width is < 1 and /reverse is absent, but that is only correct for block series. On a string/binary the C accepts a zero or negative forward skip: skip 0 returns none, and a negative forward skip runs the loop `index += skip` with an unsigned index that wraps below the head and yields NOT_FOUND (none). So `find/skip "acdcde" "cd" -3` must be none, but JEBOL throws OUT_OF_RANGE. (The block case `find/skip [1 2 3 4 5 6] 5 -4` -> out-of-range at lines 435-437 must stay.)

**Where:** Natives.java: the guard in the find native (~line 6446) and the matching guard in select (~line 5303). Scope the out-of-range raise to block series; for a string/binary with width < 1 (and not reverse/last) return NoneValue.none() instead of raising.

**Fix:** Change `if (refinements.contains("skip") && stride < 1 && !reverse)` to raise only when `series instanceof BlockValue`; otherwise, for a string/binary, `return NoneValue.none()` when stride < 1. Faithful to t-string.c which uses Int32 (not Int32s) and `if(!ret) return R_NONE`, versus t-block.c which uses Int32s(...,1) (traps < 1).

**C:** t-string.c lines 1017-1021 (Int32, skip 0 -> R_NONE) vs t-block.c line 933 (Int32s(...,1) traps range).

## [high] series:find-select-sort / FIND & SELECT — issue-88 -- DONE
**Cause:** `make bitset!` from a block silently drops string (and file/email/url/tag/ref) members, so charset ["c"] builds an EMPTY bitset and find "abc" (empty set) returns none instead of "c". codePointsIn (Natives.java ~8270) only adds a code point when the block item is a CharacterValue or IntegerValue (or a char/char range); a StringValue element falls through and contributes nothing. The C's Set_Bits sets a bit for every character of a string member via Set_Bit_Str.

**Where:** Natives.java: codePointsIn (~8270-8291) / bitsetFromBlock (~8245). Add a branch that, for a StringValue (any-string) member, appends all of its code points.

**Fix:** In codePointsIn, after the range check, handle `items.get(at) instanceof StringValue text` by adding `text.text().codePoints()` to the points list (and ideally the other any-string types file!/url!/email!/tag!/ref!, matching the C's REB_STRING..REB_REF cases). Keep the existing char/integer/range handling.

**C:** t-bitset.c Set_Bits, cases REB_STRING/REB_FILE/REB_EMAIL/REB_URL/REB_TAG/REB_REF -> Set_Bit_Str (lines 414-422).

## [high] series:find-select-sort / FIND & SELECT — SELECT/skip -- DONE
**Cause:** SELECT/skip (and FIND/skip) with a BLOCK key never matches, because the record-scanning matcher has no block-run branch. positionOfMatchInRecords calls matchesAtRecord (Natives.java ~7654), which handles /any, bitset and string needles and then falls to `matches(items.get(at), wanted, case)` — comparing one item against the whole block [b b], always false. So select/skip tbl [b b] 3 returns none instead of 'y'. The non-record matcher matchesHere does have this branch (line 7485-7488), which is why select/only tbl [b b] (stride 1) works.

**Where:** Natives.java: matchesAtRecord (~7654). Add a block-run branch mirroring matchesHere: when wanted is a BlockValue of datatype BLOCK and not /only, delegate to runMatchesAt(items, at, run.remaining(), refinements.contains("same")).

**Fix:** Insert, before the string-needle branch in matchesAtRecord, `if (wanted instanceof BlockValue run && run.datatype()==Datatype.BLOCK && !refinements.contains("only")) return runMatchesAt(items, at, run.remaining(), refinements.contains("same"));`. matchLength already returns the block length for the /skip advance, so found+len lands select on 'y.

**C:** t-block.c Find_Block, ANY_BLOCK(target) && !AM_FIND_ONLY branch (lines 144-160).

## [medium] series:find-select-sort / SORT — SORT/compare binary! -- DONE
**Cause:** When sorting a binary (or string) with a /compare function, JEBOL hands the comparator the wrong value type. Under /all it lends a BlockValue of IntegerValue bytes (lentRecord always builds a block), where the C hands a BINARY sub-series of the record — so `binary? x` fails. Without /all it passes the raw first element, an IntegerValue byte, where the C hands a CHAR whose code point is the byte — so `char? x` fails. compareRecords/lentRecord (Natives.java ~7966/8038) and sorted (~7921) are series-type-blind. (The sorted-byte results happen to still match here because `<=` on blocks-of-ints and on ints orders the same as on binaries/chars, but the value type handed to the comparator is wrong and is what these assertions check.)

**Where:** Natives.java: sorted (~7921), compareRecords (~7966), lentRecord (~8038). For a BinaryValue series lend each /all record as a BinaryValue and present each single element as a CharacterValue; for a StringValue series lend /all records as a StringValue (single elements are already CharacterValue).

**Fix:** Make lentRecord/askComparator series-aware: when the series is binary, build a BinaryValue from the record bytes for /all and wrap single bytes as CharacterValue.of(byte); when the series is a string, build a StringValue for /all. This satisfies binary? x and char? x and matches Compare_Call in t-string.c (SET_CHAR for count==1; Set_String + VAL_TYPE=REB_BINARY for count>1).

**C:** t-string.c Compare_Call lines 523-546 (count==1 -> SET_CHAR; count>1 -> Set_String, SORT_FLAG_BINARY -> REB_BINARY) and Sort_String lines 649-653.

## [medium] series:find-select-sort / SORT — SORT infinite loop case
**Cause:** /unstable is treated as a no-op — JEBOL always runs the stable merge sort and returns the stable order, so sort/unstable/compare produces the same permutation as sort/compare. But the C runs a genuinely different algorithm for /unstable (Symmetry Partition Sort, unstable_sort), which yields a different ordering of equal-key elements. The stable assert at line 1980 passes (JEBOL's stable merge reproduces it exactly), but line 1981 expects the unstable permutation and JEBOL returns the stable one. The sort native's comment even states /unstable 'changes nothing', which is the defect.

**Where:** Natives.java: sort native (~6767) and sorted (~7921). /unstable must select the C's unstable (Symmetry-Partition) ordering rather than the stable merge; the two must diverge for equal keys.

**Fix:** Route the /unstable path (and binary series, which the C always sorts unstable) to an implementation of Adp_SymPSort / the C's unstable_sort so equal-key groups come out in that algorithm's order. This is a faithful-algorithm port: the exact permutation at line 1981 is pinned to the Symmetry Partition Sort, so a stable sort cannot reproduce it. Root cause is certain; reproducing the exact order requires porting f-adp-symmetry-psort.c.

**C:** t-string.c/t-block.c: `if (uns) unstable_sort(...) else stable_sort(...)`; f-adp-symmetry-psort.c (#define Adp_SymPSort unstable_sort); binaries forced unstable via `(D_REF(11) || IS_BINARY(value))` t-string.c line 1250.

## [high] series:trim-replace-change / TRIM / trim string!
**Cause:** Default TRIM (no /head, /tail, /all, /with, /auto, /lines) only strips the whole string's leading and trailing whitespace. Rebol's default trim also trims the leading indentation and trailing whitespace of EACH interior line, drops leading/trailing blank lines, and leaves one trailing line-feed. On the single-line str1 the two agree, so only the multi-line mstr case fails.

**Where:** Natives.java, the `define("trim", ...)` lambda at line 11582 (the `!oneEndOnly` path that returns `indented.strip()` around lines 11634-11636).

**Fix:** Distinguish the true default (no head AND no tail) from head+tail-together. For the default, do per-line trimming: split on line-feed, stripLeading+stripTrailing each line, drop empty lines at head and tail, re-join with line-feed and re-append one trailing line-feed if the original tail region had one. Keep the plain strip() only for the head+tail-together case (which in C hits the else branch at s-trim.c:250 and does whole-string strip with no inner-line work).

**C:** trim_head_tail() in rebol3-source/src/core/s-trim.c, the `if (!h && !t)` branch (lines 226-249), reached from Trim_String() line 297 with h=0,t=0.

## [high] series:trim-replace-change / TRIM / trim string!
## DONE
**Cause:** TRIM/WITH with an integer molds/forms the integer to its decimal digits. `Molder.form(97)` yields the two-character string "97", so trim removes the digit characters '9' and '7' instead of the single character with codepoint 97 ('a'). It only passes for #"a" (line 611) because a char forms to "a"; the integer form is wrong. mstr happens to contain no 9 or 7, so JEBOL leaves the string unchanged while the expected result has every 'a' removed.

**Where:** Natives.java, the `define("trim", ...)` lambda at line 11582, the `refinements.contains("with")` branch (lines 11614-11620).

**Fix:** Build the set of unwanted codepoints by type, not by Molder.form: a CharacterValue contributes its codepoint, an IntegerValue contributes its magnitude as one codepoint, a string/binary contributes each of its characters, none contributes the default whitespace set. Then filter. Do not form an integer to its digits.

**C:** replace_with() in rebol3-source/src/core/s-trim.c lines 65-72: IS_CHAR uses VAL_CHAR, IS_INTEGER uses Int32s(with,0) as the single codepoint to strip.

## [high] series:trim-replace-change / TRIM / trim string!
**Cause:** withoutCommonIndent() keeps leading blank lines. For input "\n\tone\n\t\ttwo  \n" it returns "\none\n\ttwo  \n" (leading "\n" retained). Standalone TRIM/AUTO masks this because the lambda then calls strip(); but TRIM/AUTO/TAIL takes the one-end path and only stripTrailing()s, leaving the leading "\n" so the result is "\none\n\ttwo" instead of "one\n\ttwo". This is why line 613 (trim/tail applied to trim/auto) passes but line 614 (combined /auto/tail) fails.

**Where:** Natives.java withoutCommonIndent() at line 8718 (and the /auto handling in the trim lambda at lines 11631-11633).

**Fix:** Make withoutCommonIndent skip leading blank lines the way trim_auto does: advance past leading whitespace, take the indent from the first content line, and do not emit the skipped blank lines. Then TRIM/AUTO/TAIL's stripTrailing yields "one\n\ttwo", and standalone TRIM/AUTO still works via its strip(). Optionally stop the standalone /auto path from stripping trailing per-line whitespace, since real trim/auto leaves it (no test exercises that here).

**C:** trim_auto() in rebol3-source/src/core/s-trim.c lines 117-122 skip all leading whitespace (including blank lines) before measuring indent and emitting; Trim_String() lines 280-290 then applies the /tail trailing strip.

## [high] series:trim-replace-change / TRIM / trim binary!
## DONE
**Cause:** trimmedBinary computes `fromHead = !refinements.contains("tail")` and `fromTail = !refinements.contains("head")`. When BOTH /head and /tail are given, both flags become false, so neither end is trimmed and the binary is returned unchanged (#{0011001100}) instead of #{110011}. The default (neither refinement), /head-only, and /tail-only cases work; only head+tail-together is broken. The string path is safe here because it uses `oneEndOnly = head != tail` and strips both ends when both are set (which is why the equivalent trim/head/tail string assertion on line 604 passes).

**Where:** Natives.java trimmedBinary() at lines 5852-5853 (the same pattern also lurks in trimmedBlock() at lines 5789-5790, currently untested).

**Fix:** Set fromHead = contains("head") || (!contains("head") && !contains("tail")) and fromTail = contains("tail") || (!contains("head") && !contains("tail")). Equivalently: trim the head when /head is set or neither end is named, and likewise the tail. Apply the same correction to trimmedBlock.

**C:** Trim_Binary() in rebol3-source/src/core/s-trim.c lines 323 and 333: `if (!flags || flags & AM_TRIM_HEAD)` and `if (!flags || flags & AM_TRIM_TAIL)` - each end is trimmed when its own flag is set, independently, so head+tail trims both.

## [high] series:trim-replace-change / CHANGE string! -- DONE
**Cause:** The non-/part string branch of CHANGE (Natives.java lines 6674-6682) is wrong two ways that this test exposes. (1) It is guarded by `!text.atTail()`, so a change into an empty string (make string! 5) is skipped and falls through to raiseCannotUse - change/dup mem "x" 5 raises instead of growing mem, so loop 10 errors and #460 never reaches "xxxxx". It also never grows the string when the replacement is longer than what remains, capping the write at the tail. (2) It returns text.head() rather than the position just past the written characters, so `change at s 1 skip s 1` returns the whole "23455" instead of "5", failing #463 (the data assertion s="23455" on line 749 passes because Molder.form snapshots the source before writing).

**Where:** Natives.java, the `define("change", ...)` lambda, the StringValue branch at lines 6674-6682 (inside defineSeries()).

**Fix:** Rewrite the branch to mirror Modify_String: form the replacement text up front (a snapshot), remove min(replacement-length-or-part-count, remaining) characters, then insert the replacement (growing the storage past the tail when needed, and handling the empty/at-tail target by inserting), and return text.atIndex(text.index() + replacementLength) instead of text.head(). Remove the `!text.atTail()` guard.

**C:** Modify_String() in rebol3-source/src/core/f-modify.c: line 129 clamps dst_idx to tail (so a tail/empty change inserts), lines 219-222 Expand_Series/Remove_Series to grow or shrink to fit, line 233 advances dst_idx by src_len, line 242 returns dst_idx (position past the change) for CHANGE.

## [high] series:trim-replace-change / REPLACE string! -- DONE
**Cause:** REPLACE is the mezz function, which calls `change/part pos :value len`. JEBOL's /part branch of CHANGE (Natives.java lines 6631-6644) removes the target characters FIRST and only then calls insertInto, which reads the replacement via Molder.form at insertion time. When the replacement aliases the target series - here the replacement is `skip s 1`, a live view into the same string being changed - removing s's first four chars destroys what the view points at, so nothing (or the wrong text) is inserted and the result is "e" instead of "bcdee". The source is never cloned before the destructive remove.

**Where:** Natives.java, the `define("change", ...)` lambda, the /part branch at lines 6631-6644, and by extension insertInto() at line 8512 which forms the value lazily.

**Fix:** Before the removeOneAt loop, snapshot the replacement value's contents into an independent value (copy the characters/bytes/items out of the series view now), then remove and insert from the snapshot. Simplest: capture Molder.form(value) (or a Copy_Series_Part equivalent for blocks/binaries) up front and insert that, rather than passing the live arguments.get(1) to insertInto after mutating the target.

**C:** Modify_String() in rebol3-source/src/core/f-modify.c lines 192-198: `if (dst_ser == src_ser) { src_ser = Copy_Series_Part(...); src_idx = 0; }` - the source is cloned whenever it is the same series as the destination, before any expand/remove/copy.

## [high] series:take-insert-remove-part / TAKE take/deep/part block! (#602-604,#607) and take/deep/part block with string! (#613,#615) -- DONE
**Cause:** When TAKE has /part, the taken items come back straight from takeSeveral with no copying, and /deep is never applied. /deep is only honoured on the non-/part single-item branch (line 4788). So `take/deep/part b 1` hands back the very same nested series that was in the block: `same? a c/1` is true and mutating `append c/1/2 3` also mutates the original, where the suite requires `not same?` and an untouched original.

**Where:** Natives.java, the `take` native's /part branch (lines ~4792-4829): the series-position exit (4793), the /last exit (4827) and the plain count exit (4829) each return the takeSeveral result without regard to /deep.

**Fix:** When refinements.contains("deep"), deep-copy the block that takeSeveral produced before returning it - e.g. wrap each exit's result in copied(result, result instanceof BlockValue), matching the non-/part branch which already calls copied(taken, taken instanceof BlockValue). This clones nested series so the caller no longer shares storage with the series TAKE emptied.

**C:** t-block.c A_TAKE, lines 884-900: with /part set it chooses Copy_Block_Values(ser, 0, len, CP_DEEP | TS_DEEP_COPIED) when /deep is present versus the shallow Copy_Block_Len otherwise (t-string.c mirrors this for string elements).

## [high] series:take-insert-remove-part / INSERT insert/part (#730,#732-735) -- DONE
**Cause:** The BlockValue arm of INSERT splices the whole inserted block (`added.remaining()`) and never consults /part, so `insert/part a b 2` inserts all of b ([5 6 7 8 9]) instead of the first two ([5 6]). It also never handles a negative count (backward from b's position, e.g. -2 -> [3 4]) nor the int32 range check, so a /part beyond int32 (2147483648 / -2147483649) is silently clamped instead of raising out-of-range.

**Where:** Natives.java, the `insert` native BlockValue arm (lines 6482-6495). The binary arm already routes through partCountFor/octetsContributedBy, but the block arm bypasses /part entirely.

**Fix:** Slice the inserted block to the /part count before splicing: reuse partOf(insertedBlock, arguments, refinements) (positive count clamped to what is ahead, negative count counting backward from b's index) and insert only that sublist. Separately add an Int32-style range guard on the /part integer: if its magnitude exceeds Integer.MAX_VALUE / MIN_VALUE, raise OUT_OF_RANGE before clamping (partCountFor's `(int) wanted.magnitude()` cast and howManyWanted's long silently wrap/clamp today).

**C:** t-block.c A_INSERT line 959 `len = Partial1(arg, AN_LENGTH)` then Modify_Block(...,len,...); Partial (f-stubs.c 784-844) uses the inserted value as the reference and moves its index for a negative count; Int32 (f-stubs.c 121-138) traps RE_OUT_OF_RANGE outside int32.

## [high] series:take-insert-remove-part / REMOVE remove-blk-5/6 (#743-745) and remove-str-5/6 (#754-756) -- DONE
**Cause:** REMOVE calls the 3-arg howManyWanted(arguments, refinements, 1), which passes NoneValue as the source. When /part is a series position into the same series (`remove/part a next a`), howManyWanted's SeriesValue branch checks `source instanceof SeriesValue` first, finds NoneValue, and throws INVALID_PART. So a same-series position, which should give a length from the index difference, errors instead of removing that many items.

**Where:** Natives.java, the `remove` native, line 6570: `long howMany = howManyWanted(arguments, refinements, 1).orElse(1L);`

**Fix:** Pass the series being removed from as the source: `howManyWanted(series, arguments, refinements, 1)`. Then the SeriesValue branch computes `upTo.index() - series.index()` - `remove/part a a` -> 0 (removes nothing, [1 2 3]) and `remove/part a next a` -> 1 (removes one, [2 3]).

**C:** f-stubs.c Partial1 lines 727-733: for a non-integer lval it takes `len = VAL_INDEX(lval) - VAL_INDEX(sval)` when the position is into the same series, else traps RE_INVALID_PART.

## [high] series:take-insert-remove-part / POKE poke into binary (#684) and POKEZ pokez into binary (#697) -- DONE
**Cause:** The binary arm of POKE requires `arguments.get(2) instanceof IntegerValue`. A CharacterValue such as #"x" does not match, so control falls past the binary arm to the BlockValue check and then raiseCannotUse - `poke s 1 #"x"` raises an error instead of writing byte 120 and returning #"x". POKEZ into binary delegates to POKE and fails identically.

**Where:** Natives.java, the `poke` native binary arm (lines 4942-4947).

**Fix:** Broaden the binary arm to accept a CharacterValue as well: take its codepoint, raise OUT_OF_RANGE if it exceeds 0xff, otherwise write the byte at `bytes.index() + at - 1` and return arguments.get(2). Mirror the C, which accepts either a char or an integer <= MAX_CHAR and traps range for a binary when the value exceeds 0xff.

**C:** t-string.c A_POKE lines 1072-1090: `if (IS_CHAR(arg)) c = VAL_CHAR(arg); else if (IS_INTEGER(arg) && VAL_UNT64(arg) <= MAX_CHAR) c = VAL_INT32(arg); else Trap_Arg(arg);` then for a binary `if (c > 0xff) Trap_Range(arg); BIN_HEAD(ser)[index] = (REBYTE)c;`

## [high] series:index-past-tail / Red's test (strings) and Red's test (blocks) - series index past its tail (series-test.r3 lines 1632-1671)
**Cause:** When a series value is left stranded past the tail (c: skip a 6 then remove/part a 4 leaves c at index 7 in a length-4 storage), JEBOL never clamps that past-tail index to the tail before a modifying action. Real Rebol clamps it uniformly for every action, so CHANGE/INSERT/CHANGE-PART at a past-tail position append at the tail. JEBOL instead throws or refuses: (1) `change c 1` on a string hits the guard `text.atTail()` at Natives.java line 6674, falls through to the block branch and raiseCannotUse at line 6684 - it should append '1' giving "56781"; (2) `change c 1` on a block hits the `block.atTail()` guard at line 6683 and raiseCannotUse - should give [5 6 7 8 1]; (3) `insert c 1` on a string runs insert case StringValue lines 6496-6501 with text.index()=7, calling CodepointBuffer.insertAt(zeroBased 6) into a length-4 buffer whose System.arraycopy length is 4-6=-2, throwing IndexOutOfBoundsException - should append giving "56781"; (4) `insert c 1` on a block calls BlockStorage.insertAt(index 7) = items.add(6,..) on a 4-element list, throwing IndexOutOfBoundsException - should give [5 6 7 8 1]; (5) `change/part c 99 -1` (lines 6631-6644) calls insertInto (line 8512) at the past-tail index which throws the same IndexOutOfBoundsException, and additionally never implements the negative /part: the C first clamps index to tail then Partial1 moves the start back |1| element and sets len=1, so the trailing '8' is the replaced range giving "56799" (blocks: [5 6 7 99]). NOTE on the remove asserts (#790/#798): JEBOL's `remove` loop is guarded by `!series.atTail()` (line 6571) which is already true at the tail, so remove correctly no-ops and static analysis shows a="5678"/[5 6 7 8] matching the expected result - I cannot reproduce a divergence there; clear (line 6728-6733), last (line 6239, pick at length 0 -> none) and take/last also already return the clamped/none answer. The provable breakers are change, change/part and insert; they all share the single missing tail-clamp.

**Where:** src/main/java/org/jebol/domain/eval/Natives.java - the `change` native (define at line 6626), the `insert` native (define at line 6471) and the shared `insertInto` helper (line 8512); plus the `change/part` branch at lines 6631-6644 for the negative-part case.

**Fix:** Mirror the C's common action setup. At entry to the series-modifying branches of `change` and `insert` (and inside `insertInto`), normalise the working position with the tail clamp: if series.index() > series.storageLength()+1, operate on series.atIndex(series.storageLength()+1). Then remove the refusal guards that currently block an at-tail change - drop `&& !text.atTail()` at line 6674 (string change) and drop the `|| block.atTail()` disjunct at line 6683 (block change) so an at-or-past-tail change appends by growing the storage, exactly as INSERT/APPEND do (f-modify.c size>dst_len -> Expand_Series path). This alone fixes change c, insert c and the throw in change/part. For the negative /part specifically (change/part c 99 -1): after clamping index to tail, when countUpTo returns a negative value move the start index back by that many (index = tail - |part|) and treat those trailing elements as the replaced range, matching Partial1 in t-string.c/t-block.c and giving "56799"/[5 6 7 99]. The cleanest single seam is a helper that returns series.atIndex(Math.min(series.index(), series.storageLength()+1)) applied to the series argument before insert/change proceed.

**C:** t-string.c lines 949-955 and t-block.c lines 793-797 (common setup: `if (index > tail) VAL_INDEX(value) = index = tail;`); f-modify.c Modify_String line 129 and Modify_Block line 57 (`if (action == A_APPEND || dst_idx > tail) dst_idx = tail;`); Partial1 (t-string.c:969, t-block.c:294) for the negative /part moving the index back.

## [high] eval:do-next-set-stack / do/next (#23-28, #30) and do needs (#41)
**Cause:** The DO native only implements /NEXT and script-loading for BLOCK values. It never runs DO of a loadable source (string/binary/file/url) through the load-header-needs-step pipeline that Rebol's sys do* provides. So: (a) `do/next {1 2} 'n` evaluates the WHOLE string via evaluateSource returning 2 instead of loading to [1 2], stepping one value (1) and setting n to the continuation [2]; (b) `do/next 2 'n` returns 2 correctly via the switch default but never sets n, so n keeps its old value instead of becoming none; (c) `do to binary! "rebol[needs: 255.8.5]"` falls to the switch default (a binary is not a StringValue) and returns the binary with no error, instead of loading the header and running DO-NEEDS which raises id 'needs.

**Where:** src/main/java/org/jebol/domain/eval/Natives.java, the `do` native defined at line 2498 (the /next guard at lines 2511-2519 and the value switch at 2520-2561).

**Fix:** In the /next handling: when the value is a string/binary/file/url, load it to a block (load/all for a plain string) and delegate to the existing block /next path so it returns the first value and sets the var to the continuation block; when /next is set and the value is neither a block nor a loadable source, set the var to none (the C fallback at line 706) and return the value. Separately, route DO of a binary/string/file/url that carries a Rebol header through load/header + do-needs so an unmet `needs:` raises 'needs (delegate to the sys do* mezzanine rather than evaluateSource, which skips the header).

**C:** rebol3-source/src/core/n-control.c REBNATIVE(do): BLOCK/PAREN /next at lines 634-642, STRING/BINARY/URL/FILE routed to Do_Sys_Func at lines 685-691, and the /next fallback `Set_Var(D_ARG(5), NONE_VALUE)` at line 706; plus rebol3-source/src/mezz/sys-base.reb do* (string branch loads via load/all and runs `do/next body mark`, script branch runs do-needs header).

## [medium] eval:do-next-set-stack / issue-903 (#35)
**Cause:** When a word resolves to an OP with nothing on its left, JEBOL always raises 'no-op-arg. Rebol only raises 'no-op-arg when the operator sits at the very head of the series being evaluated (absolute block index 0); an operator at a non-zero index with no legitimate left operand instead grabs a bogus stack value and the resulting stack imbalance surfaces as 'missing-arg. `do "<> 0"` loads a fresh block with the op at index 0 -> 'no-op-arg (JEBOL already matches, #34 passes), but `do next [1 <> 0]` evaluates the block at index 1 -> should be 'missing-arg, and JEBOL wrongly gives 'no-op-arg.

**Where:** src/main/java/org/jebol/domain/eval/Evaluator.java, evaluateWord at lines 860-879 (the OP branch throwing NO_OP_ARG at lines 867-869).

**Fix:** Give evaluateWord access to the current frame's position in the underlying series. When a word bound to an OP has no left operand, raise NO_OP_ARG only if it is at the head of the series being evaluated (absolute index 0); otherwise raise MISSING_ARG. The distinguishing input is whether `next` advanced the block off its head before DO ran it.

**C:** rebol3-source/src/core/c-do.c Do_Next: `if (DSP <= 0 || index == 0) Trap1(RE_NO_OP_ARG, word);` at line 997 (only fires at head), and Do_Blk `if (start != DSP || tos != DS_GET(start+1)) Trap0(RE_MISSING_ARG);` at line 1114 (stack imbalance from an op with no left operand).

## [medium] eval:do-next-set-stack / set-12 / set-13 (#215-218)
**Cause:** The SET native has no branch for a PATH target. Its setup line `set/any 'o2/b ()` passes the path o2/b, which in JEBOL is a BlockValue with datatype PATH, so it falls into `case BlockValue words -> words.remaining()` and is treated as a two-word block [o2 b], setting the top-level words o2 and b to unset instead of assigning unset into field b of object o2. This corrupts o2 before the assertions run, so `set o1 o2` no longer sees an object. (The object-to-object helper setFieldsFromObject at line 10594 is itself correct: it already skips an unset source field unless /any, which is exactly what set-12 vs set-13 require.)

**Where:** src/main/java/org/jebol/domain/eval/Natives.java, the `set` native at line 4663 - it handles WordValue (line 4674) then falls straight into the block/object switch at line 4681 with no PATH case.

**Fix:** Before the block/object switch, detect a path-shaped target (datatype PATH/SET_PATH/GET_PATH/LIT_PATH) and assign the value through the path - reuse the same path-assignment machinery the evaluator uses for set-path (write into the object field named by the final segment) rather than letting a path fall into the block-of-words branch.

**C:** rebol3-source/src/core/n-data.c REBNATIVE(set): `if (ANY_PATH(word)) { Do_Path(&word, val); return R_ARG2; }` at lines 636-639, placed before the object/block handling.

## [high] eval:do-next-set-stack / set path / set path 2 (#238-239)
**Cause:** evaluateSetPath resolves the whole left-hand path - including parenthesised segments - BEFORE the right-hand value is evaluated. It calls `select(allButLast, ...)` and `selectorFor(lastSegment, ...)` at the top of the method, then returns waiting() and only consumes the RHS afterwards. Rebol evaluates the RHS first, then walks the path. For `obj/(var): (some-func 30)` the left paren (var) is read while var is still 'x (some-func hasn't run), so it targets obj/x instead of obj/y. For `b/(c: 2): c + 1` the left paren sets c=2 before the RHS, so `c + 1` computes 3 and returns 3 instead of computing 1 with c=0 and returning 1.

**Where:** src/main/java/org/jebol/domain/eval/Evaluator.java, evaluateSetPath at lines 1240-1391 (target resolution at line 1252 and every selectorFor call happen before the RHS is produced).

**Fix:** Restructure evaluateSetPath so the RHS is evaluated first: push a pendingCall that, when handed the RHS value, THEN walks the left-hand path (running any parenthesised segments via select/selectorFor at that moment) and performs the assignment. Move the path-resolution work out of the method body and into the callback that fires after the value is known.

**C:** rebol3-source/src/core/c-do.c Do_Next ET_SET_PATH: `index = Do_Next(block, index+1, 0);` evaluates the RHS first, then `Do_Path(&word, DS_TOP);` walks the path, at lines 1009-1013.

## [medium] eval:do-next-set-stack / STACK issue-1623 (#240-242)
**Cause:** Two gaps. (1) `stack <n>` with no refinement returns NoneValue (Natives.java line 11301) instead of a backtrace block of frame-name words, so #240 (block?) and #241 (first b) fail. (2) JEBOL's frame accounting only records calls that have a Rebol body (Evaluator.push pushes to functionsBeingRun only when `being != null`, line 718); native calls like `stack` itself open no frame. Rebol's DSF counts every call including the native, so offset 0 names the stack native's own frame (word 'stack). In JEBOL offset 0 lands on stack's caller, so /word 0 and the backtrace head are not 'stack.

**Where:** src/main/java/org/jebol/domain/eval/Natives.java, the `stack` native at line 11265 (add the no-refinement backtrace branch; correct the offset-0 meaning); and the frame model in Evaluator.push at lines 708-724 / functionBeingRun at line 242.

**Fix:** Add a no-refinement branch that builds a block whose elements are the frame-name words from `offset` inward-to-outward. Make offset 0 correspond to the stack native's own frame named 'stack - since JEBOL does not push a frame for a native, have the stack native treat itself as the innermost frame (prepend its own call word, captured from lastWordCalledThrough at dispatch) so both /word 0 and the backtrace head return 'stack.

**C:** rebol3-source/src/core/n-system.c REBNATIVE(stack) no-refinement branch `Set_Block(D_RET, Make_Backtrace(index));` at lines 383-384 and /word `Init_Word(D_RET, VAL_WORD_SYM(sp+2))` at line 366; rebol3-source/src/core/c-error.c Make_Backtrace at lines 241-260 (block of frame words innermost-first); rebol3-source/src/core/c-do.c Stack_Frame at lines 155-166 (offset 0 = current DSF).

## [high] eval:do-next-set-stack / unset unbind (#182)
**Cause:** UNBIND is a no-op stub: `define("unbind", ... arguments -> arguments.get(0))` returns the word unchanged and never clears its binding (WordValue is immutable, so it must return an unbound copy). So `unbind 'x` yields an 'x that is still bound to the user context; `unset` then finds a slot via slotOf and silently sets it to unset instead of erroring. Rebol's UNBIND actually strips the binding, after which UNSET of an unbound word raises 'not-defined - which is what the test's `error? try [...]` expects. (slotOf at line 5923 already throws NOT_DEFINED for a genuinely unbound word, so unset is correct; unbind is the defect.)

**Where:** src/main/java/org/jebol/domain/eval/Natives.java, the `unbind` native at lines 3566-3568.

**Fix:** Make unbind return an unbound word: for a WordValue, return a copy with its binding cleared; for a block, unbind each word it holds, recursing when /deep is given. This restores the invariant that unset of the result raises 'not-defined.

**C:** rebol3-source/src/core/n-data.c REBNATIVE(unbind) `UNBIND(word)` at line 409 (and Unbind_Block for a block, /deep at line 412); REBNATIVE(unset) `else Trap1(RE_NOT_DEFINED, word);` at line 764 when the word has no frame.

## [high] eval:compose-catch / compose map (#165-171, #173) -- DONE
**Cause:** COMPOSE evaluates paren values in the interpreter's system context, not the caller's context. The `compose` native lambda receives `context` but calls `composed(...)`, which is static and evaluates every paren with `evaluator.systemContext()`. So a paren that names the caller's words - the function locals a/b in `f1: func[b][compose #[num: (a + b)]]`, `v`/`val` in the use/apply cases, the object fields one+two in `compose/deep obj/tmp`, or the top-level words zero/now/red/white in m1 - cannot be resolved (systemContext holds only natives), so nearly every compose-map assertion fails. The one that passes (#172, `compose #[num: ([one two three])]`) is the only case whose paren returns a literal block and needs no caller binding. Secondary defect on the two /deep asserts (#166,#167): `composed` only recurses into nested BLOCKs under /deep, never nested MAPs, so `m2/d/k == white` for the nested map `d: #[k: (white)]` is left as an un-composed paren.

**Where:** src/main/java/org/jebol/domain/eval/Natives.java - the `compose` native (~L6939) and its `composed(...)` helper (~L7105/L7124).

**Fix:** Thread the native's `context` into `composed` and evaluate parens with `evaluator.evaluateEachOrRaise(paren.as(BLOCK), context)` instead of `evaluator.systemContext()`. In `composed`, add a /deep branch for MapValue that composes it as a map (flatten key/value pairs, compose values with splicing off, rebuild MapValue), mirroring the C's `if (IS_BLOCK(value) || IS_MAP(value)) Compose_Block(value, TRUE, only, 0)`.

**C:** rebol3-source/src/core/c-do.c Compose_Block (L1367-1427), esp. L1412-1413 (deep recurses into IS_MAP too); n-control.c REBNATIVE(compose) L571.

## [high] eval:compose-catch / nested catch (#297-298, L870-871) -- DONE
**Cause:** catch/quit wrongly catches a plain (unnamed) throw. The catch native uses one try/catch; its ThrownSignal handler fires for catch/quit as well. For catch/quit (no /name, no /all) `expectedNames` returns an empty set, and `answersTo` maps an unnamed throw to `expected.isEmpty()` = true, so the throw is caught. In `catch [++ a catch/quit [++ a throw 'x a: 0] a: a * 2 quit 'x ...]` the inner catch/quit swallows `throw 'x` instead of letting it reach the outer catch, so the answer and the counter `a` are both wrong. The C's catch/quit path only traps quit/halt and otherwise returns the block result via `if (!D_REF(ARG_CATCH_NAME)) return R_TOS1;` - it never catches a throw.

**Where:** src/main/java/org/jebol/domain/eval/Natives.java - catch native ThrownSignal branch (~L2915-2919).

**Fix:** When /quit is set but neither /name nor /all is, re-throw the ThrownSignal (do not catch it). Only catch/all, or catch/name/catch/quit/name with a matching name, may take a throw; a bare catch/quit catches quit and halt only.

**C:** rebol3-source/src/core/n-control.c REBNATIVE(catch), the ARG_CATCH_QUIT branch (returns R_TOS1 for non-name quit, bypassing the throw-processing code).

## [high] eval:compose-catch / catch/with function! (#317, L920-921) -- DONE
**Cause:** For a function handler, catch/with overwrites system/state/last-result with the handler's return value. Line ~L2968 sets last-result = answered unconditionally after running the handler, for both block and function handlers. The C only reassigns last_result for a BLOCK handler (`*last_result = *DO_BLK(&callback)`); for a function handler it leaves last_result as the caught value. So `catch/with [throw 'x] :on-catch` (on-catch returns mold 'x = "x") leaves last-result as "x", and the assertion `'x = system/state/last-result` fails (it wanted the pre-handler value 'x).

**Where:** src/main/java/org/jebol/domain/eval/Natives.java - catch native /with branch (~L2964-2968).

**Fix:** Only set runState last-result = answered when the handler is a BlockValue. For a function handler, leave last-result as the caught value already stored at ~L2952 (return the function result but do not re-store it into last-result).

**C:** rebol3-source/src/core/n-control.c catch: block branch does `*last_result = *DO_BLK`; function branch calls Apply_Func and returns R_TOS1 with no last_result update.

## [high] eval:compose-catch / catch/with function! argument validation (#320-321, #324) -- DONE
**Cause:** catch/with invokes the handler via the generic applyFunction, which does no type checking and pads missing args with unset. Two consequences: (1) a handler whose value/name parameter has a type constraint the caught value/name violates raises nothing instead of expect-arg, so `catch/with [throw 1] func[v [word!]][]` and `catch/all/with [throw/name 1 'foo] func[v n [integer!]][]` produce no error (#320,#321 want e/id = 'expect-arg). (2) a handler with more params than value+name gets the surplus filled with UnsetValue (bindArgumentsPositionally L2134), so `func[a b c]` reduces an unset and errors/mismatches instead of yielding [1 #(none) #(none)] (#324). The C's catch explicitly TYPE_CHECKs the handler's first param against the value and second against the name (Trap3 RE_EXPECT_ARG) before Apply_Func, and Apply_Func fills surplus params with none.

**Where:** src/main/java/org/jebol/domain/eval/Natives.java - catch native /with function branch (~L2966); optionally Evaluator.bindArgumentsPositionally (~L2130-2134) for the pad-with-none behaviour.

**Fix:** Before applying a function handler: if it has a first parameter with a non-empty typeset that rejects the caught value, raise EXPECT_ARG; likewise check the second parameter against the name. Build the argument list padded to the handler's arity with NoneValue (not unset) so surplus params default to none.

**C:** rebol3-source/src/core/n-control.c catch: the `TYPE_CHECK(args, VAL_TYPE(last_result))` / `Trap3(RE_EXPECT_ARG,...)` block, then `Apply_Func(0, &callback, last_result, &name, 0)`.

## [high] eval:compose-catch / throw from path evaluation (#340, L987) -- DONE
**Cause:** A set-path into an object whose LAST segment is a paren never evaluates the paren, so a throw inside it never fires. evaluateSetPath's object branch matches only `lastSegment instanceof WordValue field`; a paren last segment skips every branch and falls through to raise INVALID_PATH. So `catch [foo/(throw "ok" 'bar): 3]` raises invalid-path (uncaught by catch) instead of the paren throwing "ok". The other three assertions in this test work because they route the paren through selectorFor: the get-path forms `foo/(throw...)` and `foo/(throw...)/xx` evaluate it in select/selectWith, and the trailing-segment set-path `foo/(throw...)/xx: 3` evaluates it while computing the target of allButLast.

**Where:** src/main/java/org/jebol/domain/eval/Evaluator.java - evaluateSetPath object branch (~L1331-1339).

**Fix:** Resolve the last segment through selectorFor(lastSegment, context) before matching the object branch, so a paren is evaluated (firing any throw) and the resulting word is used as the field name. Branch on the resolved selector's type, not on the raw segment being a WordValue.

**C:** rebol3-source/src/core/c-do.c Do_Path/Next_Path evaluate paren path segments as the walk proceeds; PD_Object receives an already-evaluated select word.

## [medium] eval:compose-catch / catch/quit [quit] (#293-294, L863-864) -- NOT ACTIONABLE (environment gap: CALL needs a PROCESSES host and a runnable Rebol binary the suite lacks; CATCH/QUIT control flow itself already passes)
**Cause:** The two failing assertions in this test are the ones that shell out: `call/shell/wait append to-local-file system/options/boot { --do "quit"}` and the quit/return variant. CALL requires the PROCESSES host service (`requireService(HostService.PROCESSES)` in the call native) which the suite interpreter does not provide, and there is no external Rebol boot binary to run, so they raise a no-service error instead of returning 0/100. The in-process assertions of this test (`unset? catch/quit [++ a quit ++ a]`, `100 = catch/quit [quit/return 100 ...]`, and the counter checks) work: quit throws QuitRequested and catch/quit catches it. This is an environment/capability gap, not a defect in CATCH/QUIT.

**Where:** Not a CATCH/QUIT code change. src/main/java/org/jebol/domain/eval/Natives.java call native (~L13508) needs a PROCESSES service; the suite harness would also need an executable Rebol binary.

**Fix:** Realistically unsupportable in-process: either provide a PROCESSES adapter and a runnable Rebol binary to the suite interpreter, or accept these two assertions stay red and record the reason. No change to CATCH/QUIT is warranted - the control-flow behaviour it exercises already passes on the in-process assertions.

**C:** n/a (host process execution; JEBOL is a guest interpreter).

## [high] eval:for-repeat / FOR -- DONE
**Cause:** FOR over integers runs on double arithmetic and never detects integer overflow. steppedLoop converts start/end/step to double and does `at += stepBy`, which (a) cannot represent Long.MAX_VALUE exactly and (b) never overflows, so `for i 9223372036854775807 9223372036854775807 1 [...]` runs the body, increments harmlessly, runs a second pass and hits break/return false instead of raising. The suite expects the body to run exactly once (num=1) and then the post-body increment to overflow with e/id = 'overflow.

**Where:** src/main/java/org/jebol/domain/eval/Natives.java, steppedLoop (around line 4017); the `for` native at line 3809 dispatches here.

**Fix:** Add a whole-number branch taken when start, end AND step are all IntegerValue (C requires all three: IS_INTEGER(start)&&IS_INTEGER(end)&&IS_INTEGER(incr)). Iterate with long values: loop while (incr>0 ? start<=end : start>=end), set the counter, run the body, then compute start = Math.addExact(start, incr) and on ArithmeticException throw Raised.of(EvaluationFailure.OVERFLOW, ...). This mirrors Loop_Integer where REB_I64_ADD_OF(start, incr) traps RE_OVERFLOW *after* the body has run, so num is 1 before the overflow. Note the current `wholeNumbers` flag also wrongly omits the end-is-integer check.

**C:** Loop_Integer in rebol3-source/src/core/n-loop.c line 148; REBNATIVE(for) integer branch line 608.

## [high] eval:for-repeat / FOR -- DONE
**Cause:** FOR has no series-start case at all. When start is a series ("abcde"), the `for` native still routes through steppedLoop, which calls Comparison.asDouble on the string and fails rather than walking series positions. Expected: `for x "abcde" 3 1 [append out x]` sets x to successive tail positions of the series ("abcde","bcde","cde") giving "abcdebcdecde"; the end may be a number (index, minus one for 0-based) or another series (its index).

**Where:** src/main/java/org/jebol/domain/eval/Natives.java, the `for` native at line 3809 / steppedLoop at 4017 - add a series branch before the numeric one.

**Fix:** When start is a SeriesValue, iterate like Loop_Series: end index ei = (end is a series) ? end.index() : (Int32(end) - 1); clamp ei to [0, tail]; step ii = Int32(step). Loop si from start.index() while (ii>0 ? si<=ei : si>=ei), each pass set the counter to the series positioned at si, run the body, re-read the counter's index (the body may move it), then step. For UTF-8 strings step by codepoint position, not byte index. Both suite lines expect "abcdebcdecde".

**C:** Loop_Series in rebol3-source/src/core/n-loop.c line 107; REBNATIVE(for) series branch line 611-614 (ANY_SERIES(end) ? VAL_INDEX(end) : Int32s(end,1)-1).

## [high] eval:for-repeat / REPEAT -- DONE (pair added; series and decimal count still refused - open edge)
**Cause:** REPEAT only accepts an integer count. The native declares `count` as Set.of(Datatype.INTEGER) and countedLoop only counts integers, so `repeat x 2x2 [...]` is rejected on the type check. Expected: a pair count walks a 2D grid - x inner from 1 to X, y outer from 1 to Y - giving [1x1 2x1 1x2 2x2].

**Where:** src/main/java/org/jebol/domain/eval/Natives.java, the `repeat` native at line 3757.

**Fix:** Widen the count parameter to accept pair! (and, to match C fully, any-series! and decimal!/percent!, truncating a decimal to integer). Dispatch on the value: integer -> existing 1..N countedLoop; pair -> a Loop_Pair-style nested walk producing PairValue(x,y) with x inner (1..X) and y outer (1..Y), both stepping by 1; series -> Loop_Series over count from its index to tail-1. Both suite asserts (append and collect/keep) expect [1x1 2x1 1x2 2x2].

**C:** REBNATIVE(repeat) IS_PAIR branch in rebol3-source/src/core/n-loop.c line 770 -> Loop_Pair line 172 (Loop_Pair(var, body, 1., 1., X, Y, 1., 1.)).

## [medium] eval:for-repeat / FOR / REPEAT
**Cause:** Loop bodies run in an ordinary child context, not a selfless internal frame. `for self 1 1 1 [context? 'self]` binds the loop variable `self` into that child context and context? 'self returns it wrapped as an ObjectValue (truthy) instead of none. In C the loop frame is created selfless and marked as an internal series (inaccessible), so context? on a word bound into it answers none. This one cause covers both the FOR test (#350) and the REPEAT test (#354).

**Where:** src/main/java/org/jebol/domain/eval/Natives.java, steppedLoop (line 4017), countedLoop (line 3993) and forEachLoop (line 4068) build the loop context via Context.childOf; plus Context.java to carry an 'internal/inaccessible' flag, and the context? native (Natives.java line 3227) to answer none for a word bound into such a frame.

**Fix:** Mark the loop's child context as selfless and internal so context? treats it as inaccessible: either give Context an 'internal' flag that context? honours (returning NoneValue when word.binding() is internal), or ensure the frame does not expose a self field and is not returned as an object. The literal-word assert (`same? 'self for i 1 1 1 ['self]`) is already satisfied once the loop frame does not introduce its own self binding; the failing assert is the `for self`/`repeat self` selfless one.

**C:** Init_Loop in rebol3-source/src/core/n-loop.c line 44: SET_SELFLESS(frame) line 64 and INT_SERIES(frame) line 70 ("Mark the frame as internal series so it is not accessible", issue 2531).

## [low] eval:for-repeat / delta-profile
**Cause:** The three profile counters JEBOL actually measures - evals (valuesWalked), eval-natives (nativesCalled), eval-functions (functionsCalled) - do not difference to exactly zero for an empty block. delta-profile takes an 'adjust' reading around `do []` and a real reading around `do block`, then computes real-minus-adjust; the nine series/made fields stay 0 and cancel, but the measured three do not cancel because the two measurement windows are not counted identically (`do []` with a literal empty block versus `do block` with a word argument, and/or the point at which filledInProfile samples the monotonic counters differs from the C snapshot). The other nine asserts pass because those fields are always 0.

**Where:** src/main/java/org/jebol/domain/eval/Natives.java, filledInProfile (line 11446) and the evaluator counters valuesWalked()/nativesCalled()/functionsCalled(); the mezz driver is delta-profile in src/main/resources/org/jebol/mezz/mezz-debug.reb line 26.

**Fix:** Make the empty-block adjustment truly cancel the measured run: sample the counters at the same phase the C reads system/standard/stats (before the profiling native's own work is counted) so that `do []` and `do block` produce identical evals/eval-natives/eval-functions deltas. Verify the shared-object refresh in filledInProfile matches C's `*ds = *stats` snapshot semantics so copy/difference lands on zero. Confidence is low without running - the mechanism (which of the three, and the exact miscount) needs a trace.

**C:** stats = Get_System(SYS_STANDARD, STD_STATS); *ds = *stats in n-system.c (STATS native); mezz DELTA-PROFILE in rebol3-source/src/mezz/mezz-debug.reb.

## [low] eval:for-repeat / Dynamic refinements
**Cause:** A truthy dynamic refinement forwarded through repend to native append does not take effect: `only: yes` then `repend/:only s [1 + 2 3 * 4]` should give s == [[3 12]] (the reduced block appended as one element), but the /only is not applied, so s ends up [3 12]. dyn-ref-2 (only: no) passes because the false path degrades to plain append correctly. repend's body forwards three refinements at once - `append/:part/:only/:dup :series reduce :value :length :count` - with two declined refinements (part, dup) still carrying block arguments (:length, :count). Native argument gathering handles declined-refinement arguments differently from user functions: PendingCall.arityOf uses the granted-only NativeValue.arity() for natives but the full named list for FunctionValue, so the mix of granted /only with declined /part and /dup (and their trailing arguments) misaligns and /only is dropped.

**Where:** src/main/java/org/jebol/domain/eval/PendingCall.java, arityOf (line 201) and arrivingParametersOf (line 85) - reconcile the native path with the function path so declined-but-named refinements' arguments are consumed and the granted refinement is still applied; cross-check refined() and select() in Evaluator.java (lines 1220, 1510). repend is defined in src/main/resources/org/jebol/mezz/base-series.reb line 20.

**Fix:** Treat a native like a function for arity/argument-alignment when named refinements exceed granted ones: consume the argument slots of every NAMED refinement (dropping the declined ones) as C's Do_Args does, while applying only the granted refinements' behaviour, so `append/:part/:only/:dup series (reduce value) length count` still reaches append with /only active and appends the reduced block whole. Low confidence: static tracing suggested the current path could still land on [[3 12]], so the exact misstep needs a trace to confirm.

**C:** Do_Args in rebol3-source/src/core/c-do.c ("refinement out of sequence, resequence arg order"); repend forwards to native append.

## [high] parse:to-thru-not-return / Basic string based parsing - "changing input" (#7) -- DONE
**Cause:** A get-word in a rule whose value is a *different* series must switch the parse input to that series (reset the text and position to the new series' head), then keep matching. JEBOL's string parser only supports a get-word that seeks back to a mark inside the SAME storage; when the get-word names another series it raises a parse-rule error instead of switching input. So `parse "test" ["test" :b "this"]` throws where R3 switches to "this", matches "this", and answers true.

**Where:** StringParser.matchOne, the GET_WORD branch (lines 309-330). The final/derived `source` and `text` fields (lines 54, 45, 90-96) need to become re-pointable so the walker can adopt a new input series.

**Fix:** When the get-word resolves to a series that does not share storage with the current source (currently the `!marked.sharesStorageWith(source)` case that throws at 320-326), instead re-point the parse at that series: set source to the resolved series, recompute text via textOfSeries, and set position to (resolvedSeries.index - resolvedSeries.index) i.e. the new series' head (position 0 relative to its own index). Keep the existing same-storage case as the mark-seek. Same-storage keeps current seek; different-storage adopts the new input. (The block Parser has the same gap in seekToMark at lines 194-209 but the failing test is a string parse.)

**C:** u-parse.c lines 1131-1159 (the IS_GET_WORD branch: Get_Var then Set_Parse_Series resets series+index; CureCode #1263). Note line 1137 only requires ANY_SERIES.

## [high] parse:to-thru-not-return / TO/THRU with bitset! (#10) -- DONE (AHEAD lookahead now uses matchOne + ruleSpan)
**Cause:** AHEAD (spelled AND) in the string parser matches only a single value via matchValue and always reports a fixed span of 2 items. It therefore cannot look ahead at a compound rule such as `thru a`: matchValue(word `thru`) just text-matches the literal "thru" against the input and fails, so `and thru a skip` fails where R3 returns true. The other assertions in this group pass only by coincidence (the charset is named "a" and holds "a", so text-matching the word happens to work).

**Where:** StringParser.lookahead (lines 265-273).

**Fix:** Rewrite lookahead like the block parser: remember position, call matchOne(rules, at+1) (not matchValue), restore position, and return `2 + ruleSpan(rules, at+1) - 1` i.e. 1 + ruleSpan(rules, at+1) on match, NO_MATCH otherwise, so a multi-item follow rule (thru a, a datatype, a counted rule) is consumed correctly. Latent related bug worth fixing at the same time: StringParser.seek (766-821) never resolves a word that names a bitset/charset to its value, so `thru digit` for a general charset would text-match the word "digit"; it works here only because the charset name equals its content.

**C:** u-parse.c lines 928-931 (SYM_AND/SYM_AHEAD set PF_AND and continue, so the following rule is matched by the normal iterated matcher and then index is reset to begin at line 1463). Contrast JEBOL's own block Parser.lookahead (Parser.java 372-378) which correctly uses matchOne + ruleSpan.

## [high] parse:to-thru-not-return / TO/THRU end (#30-31) -- DONE
**Cause:** When TO/THRU is followed by another parse command word (to, thru), R3 raises a parse-rule error: Get_Parse_Value returns command words unchanged, and Parse_To then rejects any target that is not an integer, END, block, char, string, tag, binary or bitset. JEBOL's string parser instead treats the command word as a literal to search for - `textOf(word "to")` = "to", indexOf returns -1, so seek returns NO_MATCH and the parse quietly answers false. try then catches nothing, so `error? try [...]` is false where the suite expects true.

**Where:** StringParser.seek (lines 766-821). Same defect exists in Parser.seek (Parser.java 810-830) but these are string parses.

**Fix:** Before falling through to `textOf(wanted)`, resolve the target the way Get_Parse_Value does: a non-command word to its bound value, and if the target is (or resolves to) a parse command word other than `end` - or to any value that is not a searchable target - raise Raised.of(PARSE_RULE, ...). Add command words (to, thru, skip, etc.) to the rejection alongside the existing DecimalValue/REPEATING_KEYWORDS check at 787-794. This also fixes the latent charset-word resolution noted above.

**C:** u-parse.c lines 1235-1241 (SYM_TO/THRU call Get_Parse_Value then Parse_To), Get_Parse_Value 134-142 (command words pass through un-resolved), and Parse_To 542-574 (the else branch raises Trap1(RE_PARSE_RULE) for an unrecognised target).

## [high] parse:to-thru-not-return / NOT - "not not" (#32-33) -- DONE
**Cause:** The NOT parse keyword is not implemented at all in the string parser. `not` is not in matchOne's keyword switch, so it falls to matchNamedRule("not") and either fails or matches the NOT native's value - never negating the following rule. So `parse "1" [not not "1" "1"]` is false where R3 is true.

**Where:** StringParser.matchOne keyword switch (lines 333-368); add a `not` case. The block Parser (Parser.java matchKeyword 330-361) needs the same for parity though these tests are string parses.

**Fix:** Add a NOT keyword that negates the following rule as a zero-width assertion: record position, run matchOne(rules, at+1) (which recurses for `not not`), restore position, and return 1 + ruleSpan(rules, at+1) when the inner rule did NOT match, NO_MATCH when it did - consuming no input either way. Recursion reproduces the C's PF_NOT2 toggle: `not not X` = inner not fails so outer not succeeds at begin. Add ruleSpan entries so `not` spans 1 + its operand.

**C:** u-parse.c lines 923-926 (SYM_NOT sets PF_NOT and toggles PF_NOT2) and 1350-1353 (post-processing: with PF_NOT2 set, a match becomes NOT_FOUND; otherwise index is reset to begin - a zero-width assertion, and stacked NOTs toggle PF_NOT2 so `not not` collapses to a no-op that consumes nothing).

## [medium] parse:to-thru-not-return / THEN - "then" (#54) -- DONE (minimal: THEN consumes nothing and succeeds; the commit-away-from-alternatives part is an open edge)
**Cause:** The THEN parse keyword is not implemented in the string parser, so `then` falls through to matchNamedRule and fails as a rule. That makes the first alternative `"a" then "b"` fail after matching "a", and since "c" cannot match "ab" the whole parse is false where R3 returns true.

**Where:** StringParser.matchOne keyword switch (lines 333-368) and ruleSpan (417-447); the block Parser needs parity.

**Fix:** Minimum to pass both assertions: recognise `then` as a keyword that consumes nothing and succeeds (return 1), so `"a" then "b"` matches straight through. Faithful behaviour additionally makes the following rule's failure commit the current alternative (skip the remaining `| ...` branches) per PF_THEN; JEBOL's matchSequence iterates alternatives, so a full implementation would need a THEN flag that, on failure of the rule after THEN, stops matchSequence from trying later alternatives.

**C:** u-parse.c lines 933-935 (SYM_THEN sets PF_THEN and continues) and 1356-1359 (only on failure of the following rule does THEN skip to the next bar and past it, committing away from later alternatives).

## [high] parse:to-thru-not-return / LIMIT - "limit" (#56) -- DONE
**Cause:** LIMIT is a reserved-but-unimplemented parse command that R3 always answers by raising the 'not-done error. JEBOL does not recognise `limit` as a keyword, so with `limit` bound to the block ["123"] the word resolves as a named rule to that block, parses "123" against "123", and answers true. No error is raised, so `error? e` is false and `e/id = 'not-done` never holds. JEBOL also has no 'not-done error id.

**Where:** StringParser.matchOne keyword switch (lines 333-368) and Parser.matchKeyword for parity; plus a new EvaluationFailure.NOT_DONE entry (EvaluationFailure.java, near FEATURE_NA at line 90).

**Fix:** Add NOT_DONE(ErrorCategory.INTERNAL, "not-done", "reserved for future use (or not yet implemented)") to EvaluationFailure, then add a `limit` keyword case that immediately throws Raised.of(EvaluationFailure.NOT_DONE, ...).

**C:** u-parse.c lines 1100-1101 (case SYM_LIMIT: Trap0(RE_NOT_DONE)); errors.reb line 253 (not-done, in the Internal category: "reserved for future use (or not yet implemented)").

## [high] parse:to-thru-not-return / REJECT - "reject" (#61) -- DONE
**Cause:** REJECT is not implemented. In R3 it immediately fails the current block/group and returns NOT_FOUND WITHOUT trying the remaining `|` alternatives of that group. JEBOL treats `reject` as an unknown named rule that simply fails the current alternative, so in `[#"a" reject | "aabb"]` the failure falls through to the `| "aabb"` branch, which matches, making the block succeed. So `not parse "aabb" [[#"a" reject | "aabb"]]` is false where the suite expects true.

**Where:** StringParser.matchOne keyword switch (lines 333-368); it already has the RepeatEnded pattern for BREAK (350-352, 710-716) to copy. Block Parser needs parity.

**Fix:** Add a `reject` keyword that throws a new RejectSignal (like RepeatEnded but caught at the *block* boundary, not the repeat). Catch it in matchValue's nested-BLOCK branch (matchSequence call around line 893-895) and in the top-level answer/matches, returning false for that block WITHOUT iterating its remaining alternatives - the throw unwinds past matchSequence's alternative loop, which is exactly the difference from an ordinary match failure. The failure then propagates as NO_MATCH to the enclosing level, which may still try its own alternatives (matching `[[#"a" reject] | "aabb"]` staying true).

**C:** u-parse.c lines 1081-1083 (case SYM_REJECT: parse->result = 0; return NOT_FOUND - unwinds the current Parse_Rules_Loop immediately) and 1281-1286 (the enclosing rule sees the block fail). Contrast SYM_BREAK/ACCEPT at 1076-1079 which return success.

## [high] parse:to-thru-not-return / RETURN - "parse return" (#63-65) -- DONE
**Cause:** RETURN is not implemented in the block parser. `return` is a native, so matchNamedRule("return") fetches the RETURN function value and tries to match it against the input item, which fails - so each `... return ...` rule fails and parse answers false. `[1 2] = parse ...` etc. therefore compare against false. R3's RETURN short-circuits the whole parse and yields a value: `return (paren)` yields the paren's value; `return rule` matches the rule and yields the matched slice.

**Where:** Parser.matchKeyword (Parser.java lines 330-361) and Parser.answer (129-143), which currently returns only a LogicValue or the gathered block. StringParser needs the same for parity though these tests are block parses.

**Fix:** Add a `return` keyword. If the following item is a paren, evaluate it and throw a new ReturnSignal carrying the value. Otherwise match the following rule (matchOne), and on success wrap input.subList(begin, position) as a BlockValue and throw ReturnSignal with it. Catch ReturnSignal in answer() and return its value instead of the logic/gathered result (a third answer path alongside `gathered`).

**C:** u-parse.c lines 1068-1074 (SYM_RETURN: a paren is evaluated and thrown immediately via Throw_Return_Value; otherwise PF_RETURN is set), 1415-1420 (on success, Copy_Block_Len(series, begin, count) is thrown via Throw_Return_Series), and 1677-1680 (the top level catches RE_RETURN and returns that value as the parse result).


## [wrong-type gap, found 2026-08-12] TRIM/WITH rejects none, and /all owns the whitespace default
**Cause:** TRIM's `/with str` argument type is `[char! string! binary! integer!]` (actions.reb:358); `none!` is not in the list, so `trim/with s none` must raise a type error. JEBOL runs it and returns the string unchanged (unwantedCodePoints falls to its `default -> Set.of()` arm). The C's `IS_NONE(with)` whitespace branch in replace_with (s-trim.c:61-64) is reached only through `/all`, where `/with` is absent and its argument defaults to none - never through a user passing none to `/with`, because the argument type check rejects it first. `trim/all` already works in JEBOL (a separate branch), so no whitespace-default change is needed there.

**Where:** Natives.java, the `define("trim", ...)` lambda, the `refinements.contains("with")` branch; the /with argument is not type-checked against [char! string! binary! integer!].

**Fix (later wrong-type batch):** Validate the /with argument type at the boundary and raise the expected-type error for none (and any other non-listed type), matching every other action's argument checking. This is a wrong-type gap, not a suite-named failure - do it with the other action argument-type checks, not inside a behavior batch.
