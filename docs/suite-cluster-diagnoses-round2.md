# Second diagnosis round - 2026-08-12 evening (nine clusters, 168-failure snapshot)

Findings from the nine-agent workflow over the previously undiagnosed clusters.

## [high] mold-test.r3 / "mold url!" (test "url with construction syntax needed") and "mold-all" (test "mold/all series") - asserts #52, #53, #54, #55, #56, #61, #62, and via mold/all fall-through #80, #81, #82, #83
**Cause:** JEBOL molds url!/email! as bare text unconditionally: Molder.renderString has `case URL, EMAIL -> text;`. The C (s-mold.c Mold_Value REB_EMAIL/REB_URL case, lines 1327-1339) instead emits construction syntax #(url! "...") whenever the text would not re-load as a url: series empty (tail==0), no required char (':' for url, '@' for email) anywhere in the series, or first char at the current position is '%'; otherwise Mold_Url (lines 567-604) scans the remaining chars and still falls to construct syntax when it meets a lexer delimiter (any char <= 0x20, or one of ( ) [ ] { } " ; and for email also /; url allows /), the required char at the first position, a second '@' in an email, a second ':' at relative position 1, no required char at all in the remaining text, or the required char as the last char. Construct output is Mold_All_Constr_String (lines 524-541): '#(' + type + ' ' + the WHOLE series from head molded as a plain string (quoted/braced) + (' ' + 1-based index if not at head) + ')'. So: mold clear ftp:// must give {#(url! "")} (#52), mold as url! "a" -> {#(url! "a")} (#53, no colon), "a:" -> construct (colon last, #54), ":a" -> construct (colon first, #55), "::a" -> construct (#56), mold as url! next "a:a" -> {#(url! "a:a" 2)} (colon at head of remaining; note whole text plus index, #61), "%aa" -> {#(url! "%aa")} (leading %, #62). mold/all #(email! "")/#(email! "a")/#(url! "")/#(url! "a") (#80-83) reach the same code because Molder.moldAll falls through to render when index()==1, and the C gives the identical construct text for them. JEBOL currently returns the bare text (empty string for the empty ones), failing every one.

**Where:** /Users/benhoskins/Code/personal/JEBOL/src/main/java/org/jebol/domain/value/Molder.java, renderString, line 353 (`case URL, EMAIL -> text;`)

**Fix:** Replace the URL/EMAIL arm (forReading==true only; FORM keeps bare text): compute required = string.datatype()==EMAIL ? '@' : ':'; String remaining = string.text(); String full = string.head() text (StringValue.head().text() or storage.textFrom(1)). Decide needsConstruct: true if remaining.isEmpty() || full.isEmpty() || remaining.charAt(0)=='%'; otherwise scan remaining: for each char c at 0-based position p: if c<=0x20 || c==0x7F || "()[]{}\";".indexOf(c)>=0 || (c=='/' && required=='@') -> construct; if c==required: if p==0 -> construct; if already found: if required=='@' || p==1 -> construct, else skip; else found=p. After the scan: construct if never found or found==remaining.length()-1. If needsConstruct, return "#(" + string.datatype().literalSpelling() + " " + moldedText(full) + (string.index()>1 ? " "+string.index() : "") + ")"; else return remaining as today. moldedText already matches Mold_String_Series (quotes vs braces).

**C:** rebol3-source/src/core/s-mold.c lines 1327-1339 (REB_EMAIL/REB_URL case in Mold_Value), 567-604 (Mold_Url), 524-541 (Mold_All_Constr_String); delimiter set from rebol3-source/src/include/sys-scan.h lines 122-137 and 201 (control chars are delimiters)

## [high] mold-test.r3 / "mold url!" (test "url with construction syntax needed") and "mold-all" (test "mold/all block at tail") - asserts #60, #78
**Cause:** Molder.moldAll (lines 53-59) builds the positioned construct form as "#(type " + mold(series.head()) + " index)", i.e. it molds the content in the value's OWN literal notation. The C molds the content in the construct-body notation instead: Mold_All_String (s-mold.c 506-521) sets VAL_INDEX to 0 and VAL_SET(&val, REB_STRING), so any positioned any-string molds its full text as a quoted string - mold/all as url! next "aa:a" must be {#(url! "aa:a" 2)}, but JEBOL emits #(url! aa:a 2) because mold of a url head is bare text (#60). For paths, Mold_Block (804-841) with all=TRUE emits Pre_Mold "#(path! " then Mold_Block_Series(series, 0, 0) which uses bracket separators - so mold/all next next 'p/p must be "#(path! [p p] 3)", but JEBOL emits #(path! p/p 3) because mold of a path head uses slash notation via joinPath (#78). Plain blocks pass by accident (mold of a block head happens to be "[a b]").

**Where:** /Users/benhoskins/Code/personal/JEBOL/src/main/java/org/jebol/domain/value/Molder.java, moldAll, lines 53-59

**Fix:** In moldAll's index()>1 branch, choose the content by shape instead of calling mold(series.head()): for StringValue (every any-string - string, file, url, email, tag, ref) use moldedText(full text from head); for BinaryValue keep the current #{...}-from-head (already right); for BlockValue of any datatype (block, paren, path, set-path, get-path, lit-path, hash) use "[" + head items each rendered with mold, joined by spaces + "]" - no parens, no slashes, no sigils, matching Mold_Block_Series with NULL sep. Keep the existing " " + series.index() + ")" tail (VAL_INDEX+1 in the C equals JEBOL's 1-based index()).

**C:** rebol3-source/src/core/s-mold.c lines 506-521 (Mold_All_String forces REB_STRING and index 0), 225-236 (Post_Mold appends VAL_INDEX+1), 804-841 (Mold_Block all-branch: Pre_Mold + Mold_Block_Series(series, 0, 0) + Post_Mold, bracketed even for paths)

## [high] mold-test.r3 / "mold-all" (test "mold/all series") - asserts #84
**Cause:** mold/all #(tag! "") must give {#(tag! "")}: the C's REB_TAG case (s-mold.c 1341-1347) routes to Mold_All_String when MOLD_ALL is set and (index != 0 || tail == 0), so an EMPTY tag under /all gets construction syntax with its text as a quoted string. JEBOL's moldAll only triggers on index()>1 and otherwise renders normally, so an empty tag molds as "<>". (Plain mold of an empty tag stays "<>" - the branch is /all-only, unlike url/email where even plain mold constructs.)

**Where:** /Users/benhoskins/Code/personal/JEBOL/src/main/java/org/jebol/domain/value/Molder.java, moldAll, lines 53-59

**Fix:** In moldAll, before the plain-render fall-through, add: if the value is a StringValue with datatype TAG and empty storage (storageLength() == 0), return "#(tag! \"\")" - generally "#(" + literalSpelling + " " + moldedText("") + ")" with no index since it is at head. Do not extend this to file! (the C's /all top branch covers only types >= REB_EMAIL plus the tag case; an empty file under /all still molds %""), and url/email empties are already covered by the renderString fix.

**C:** rebol3-source/src/core/s-mold.c lines 1341-1347 (REB_TAG case), 1227-1248 (the /all top branch excluding tag and its type >= REB_EMAIL empty-series condition)

## [high] protect-test.r3 === Checks if protected data are really protected === (--test-- "delect") - asserts #20
**Cause:** JEBOL has no DELECT native at all. The only mention of 'delect' in Natives.java is inside the EVOKE chant handler (line 11623). So `try [delect dialect inp out]` raises a no-value/undefined-word error whose id is not 'protected, and the assert fails. The C's REBNATIVE(delect) (u-dialect.c) checks the output block's protection as its first act: `if (IS_PROTECT_SERIES(dia.out)) Trap0(RE_PROTECTED);` (line 514), before any parsing.

**Where:** src/main/java/org/jebol/domain/eval/Natives.java - add a new define("delect", ...) near the other block natives; no existing method to change

**Fix:** Define the native: `delect: native [dialect [object!] input [block!] output [block!] /in where [block!] /all]` returning the (position-updated) input block. First statement: requireChangeable(arguments.get(2)) so a protected output block raises PROTECTED before anything else - that alone turns assert #20 green. Then port Do_Dia from u-dialect.c for the actual dialect parse (returns none when input is at its end, answers the input advanced past the parsed command, appends the command word and reordered argument values to output). The suite only exercises the protection gate, but a stub that does nothing else would be dishonest - port the parse or at minimum raise feature-na after the protection check and record the gap.

**C:** rebol3-source/src/core/u-dialect.c REBNATIVE(delect), lines 489-548; protection gate at line 514

## [high] protect-test.r3 === Checks if protected data are really protected === (--test-- "protect bitset") - asserts #21-22
**Cause:** Bitsets cannot carry protection in JEBOL. (a) `protect charset "^- "` hits setProtection's default branch (Natives.java:9515) and raises cannot-use, because there is no BitsetValue case - the C's Protect_Value protects bitsets like series: `if (ANY_SERIES(value) || IS_MAP(value) || IS_BITSET(value)) Protect_Series(value, flags)` (n-control.c:81). (b) CLEAR on a bitset (Natives.java:6855) calls members.clear() with no protection check - the C traps CLEAR via the `action >= A_TAKE && action <= A_SORT && IS_PROTECT_SERIES` guard (t-bitset.c:656). (c) The set-path write `ws/1: true` never checks protection, and Evaluator.java:1438 only accepts a CharacterValue selector, so an IntegerValue selector falls through to a non-protected error; the C's Set_Bits traps protection first (`if(IS_PROTECT_SERIES(bset)) Trap0(RE_PROTECTED)` at t-bitset.c:354) and accepts IS_CHAR and IS_INTEGER selectors (t-bitset.c:356-367).

**Where:** src/main/java/org/jebol/domain/value/BitsetValue.java (new flag); src/main/java/org/jebol/domain/eval/Natives.java setProtection ~9472 and requireChangeable ~9520 and clear ~6855; src/main/java/org/jebol/domain/eval/Evaluator.java set-path bitset branch ~1438

**Fix:** 1) Give BitsetValue a boolean protection flag with protectFromChange(boolean) and isProtected(), the way MapValue holds it. 2) setProtection: add `case BitsetValue members -> members.protectFromChange(protectedNow);` beside the MapValue case (no deep walk - Protect_Series only recurses for blocks). 3) requireChangeable: add `case BitsetValue members -> members.isProtected();`. 4) CLEAR's BitsetValue case: call requireChangeable(members) before members.clear(). 5) Evaluator's bitset set-path branch: widen the selector to accept IntegerValue as well as CharacterValue (integer means the bit number, same as a codepoint), and throw Raised.of(EvaluationFailure.PROTECTED, ...) when set.isProtected() before pushing the assignment. Also add the protection check to any other in-place bitset mutators (APPEND/INSERT/REMOVE bitset cases call holdAll/clearAllDirectly at Natives.java 6343/6616/13547 - the C traps all of A_TAKE..A_SORT).

**C:** rebol3-source/src/core/n-control.c:81-82 (Protect_Value includes IS_BITSET); rebol3-source/src/core/t-bitset.c:354 (Set_Bits protection trap), :361-367 (integer selector), :656-657 (action-range protection guard covering CLEAR)

## [high] protect-test.r3 === Checks if protected data are really protected === (--test-- "protect inside an object" and --test-- "EXTEND object!") - asserts #26-27, #42-45
**Cause:** PROTECT/DEEP on a word target never reaches the word's value. Two sites drop the /deep. (a) setProtection's WordValue case (Natives.java:9508-9514) only locks the slot and ignores the `deeply` parameter, so `protect/deep 'a` and `protect/deep 'obj` leave the object completely unprotected: obj/a: 2 succeeds (assert #42 wanted locked-word), and put/append see isClosedToNewNames()==false so no PROTECTED is raised (asserts #43-45), and insert a/b "x" succeeds (assert #26). (b) protectNamed's word loop (Natives.java:9414-9441) handles /words but ignores /deep, so `protect/deep/words 'a` locks the slot and stops (assert #27). The C's Protect_Word_Value (n-control.c:157-187) always follows PROT_DEEP into the value: `if (GET_FLAG(flags, PROT_DEEP)) { val = Get_Var(word); if(!IS_SCALAR(val)) { Protect_Value(val, flags); ... } }` - and Protect_Object then protects the frame (blocking put/append/resolve with RE_PROTECTED), locks every word (locked-word on obj/a: 2), and with deep protects every field value. The PUT and APPEND sides in JEBOL already ask isClosedToNewNames() and raise PROTECTED (Natives.java:5294, 13570), so only the protecting side is broken.

**Where:** src/main/java/org/jebol/domain/eval/Natives.java setProtection WordValue case ~9508, and protectNamed word loop ~9428

**Fix:** In setProtection's WordValue case, after the slot lock/unlock, add: if (deeply) { Value held = slotOf(word).value(); if (held instanceof SeriesValue || held instanceof ObjectValue || held instanceof MapValue || held instanceof BitsetValue) setProtection(held, protectedNow, true, onlyTheWords); } (the instanceof guard mirrors the C's !IS_SCALAR plus Protect_Value's own type filter, and keeps the default cannot-use branch from firing on `protect/deep 'x` where x is 5). In protectNamed's loop, after the words-branch lock, add the same deep walk when refinements contains "deep": setProtection(slot.value(), protectedNow, true, words). While there, align the object branch of setProtection with Protect_Object's PROT_WORDS handling: closeToNewNames should be skipped when onlyTheWords is true on the protecting side too (C: `if (!GET_FLAG(flags, PROT_WORDS)) PROTECT_SERIES(series)`), i.e. change `if (protectedNow || !onlyTheWords)` to `if (!onlyTheWords)` - not needed for these asserts but it is what the C does.

**C:** rebol3-source/src/core/n-control.c:157-187 (Protect_Word_Value deep walk), :121-152 (Protect_Object: frame PROTECT_SERIES unless PROT_WORDS, word locks, deep into values), :217-220 (word dispatch)

## [high] protect-test.r3 === Checks if protected data are really protected === (--test-- "protect paths") - asserts #50-51
**Cause:** PROTECT/VALUES with a path argument must protect the path value itself as a series, leaving the field it names alone. The C's dispatch is `if (IS_WORD(val) || (ANY_PATH(val) && !D_REF(4)))` - a path WITH /values skips Protect_Word_Value, is not IS_BLOCK, and falls through to `Protect_Value(val, flags)` which protects the path's own series storage. So after `protect/values p`, `o/a: none` succeeds and `append p 'x` raises protected. JEBOL calls protectFieldNamedBy first regardless of refinements (Natives.java:4716), which locks the o/a slot (so `o/a: none` raises locked-word, failing `none? try [o/a: none]`) and returns true so the path storage is never protected (so `append p 'x` does not raise protected). Both inner conditions of asserts #50 and #51 fail. A second trap: a path is a BlockValue in JEBOL, so if protectFieldNamedBy merely stepped aside, protectNamed's `case BlockValue named` would wrongly treat the path segments [o a] as a list of words to protect.

**Where:** src/main/java/org/jebol/domain/eval/Natives.java protectFieldNamedBy ~9329, protectNamed ~9406 (both used by protect at 4716-4719 and unprotect at 4737-4740)

**Fix:** In protectFieldNamedBy, return false (unhandled) when refinements.contains("values") and the target is a path, so it falls through. In protectNamed's switch, restrict the block case to plain blocks: `case BlockValue named when !isAPath(named) -> named.remaining();` so a path never reads as a word list (the C tests IS_BLOCK, which excludes path shapes). The path then reaches setProtection's BlockValue case, which does storage().protectFromChange(true) - exactly Protect_Series on the path. This covers both #50 (path from `'o/a`) and #51 (get-path from `quote :o/a`), since isAPath already includes GET_PATH and setProtection's BlockValue case covers every path shape. Apply the same to UNPROTECT (it shares both helpers).

**C:** rebol3-source/src/core/n-control.c:217-236 (Protect dispatch: `IS_WORD(val) || (ANY_PATH(val) && !D_REF(4))`, then IS_BLOCK for /words //values, else fall-through), :240 (Protect_Value on the path value itself)

## [high] protect-test.r3 === protect/hide === (--test-- "protect/hide on series") - asserts #57
**Cause:** PROTECT/HIDE on anything that is not a word, path, or word-list block must raise bad-refines. The C reaches `if (GET_FLAG(flags, PROT_HIDE)) Trap0(RE_BAD_REFINES);` (n-control.c:238) after the word/path/block dispatch and before Protect_Value. JEBOL's protect native only special-cases /hide for a WordValue target (Natives.java:4711-4715) and for paths inside protectFieldNamedBy; a string target with /hide falls through to setProtection, which quietly protects the string's storage and returns it - no error at all, so `error? e: try [protect/hide :a]` is false and assert #57 fails.

**Where:** src/main/java/org/jebol/domain/eval/Natives.java protect native body ~4716-4720

**Fix:** In the protect native, after protectFieldNamedBy and protectNamed have both declined (the `if (!protectFieldNamedBy(...))` branch, once protectNamed has also returned false), and before calling setProtection, add: if (refinements.contains("hide")) throw Raised.of(EvaluationFailure.BAD_REFINES, "protect/hide needs a word"); EvaluationFailure.BAD_REFINES already exists (EvaluationFailure.java:53, id 'bad-refines). Note the C never gives UNPROTECT a /hide at all, and JEBOL already matches that, so the guard belongs only in protect.

**C:** rebol3-source/src/core/n-control.c:238 (`if (GET_FLAG(flags, PROT_HIDE)) Trap0(RE_BAD_REFINES);`)

## [high] parse-test.r3 / COLLECT/KEEP (subgroups "block collect set" and "string collect set") - asserts #109, #150
**Cause:** Nested `collect set a [collect set a keep skip]` must leave a = [1] (block) / [#"1"] (string). The C assigns the word a brand-new block at COLLECT start (Make_Block + Set_Var_Series, before the rule runs), so the inner collect's assignment lands second and stays; keeps then mutate that already-assigned series. JEBOL assigns only after the rule finishes (`assign(into, BlockValue.block(mine))`), so the outer collect's final assignment of its own (empty) collection overwrites the inner one, leaving a = [].

**Where:** src/main/java/org/jebol/domain/parse/Parser.java collect() ~lines 640-666 (the `into != null` branch), and identically src/main/java/org/jebol/domain/parse/StringParser.java collect() ~lines 546-555

**Fix:** In both collect() methods, when the SET form is used: build the destination BlockValue before matching (`BlockValue destination = BlockValue.block(new ArrayList<>()); assign(into, destination);` placed just before `collecting.push(...)`), then after `List<Value> mine = collecting.pop();` fill it instead of reassigning: `for (Value item : mine) destination.storage().insertAt(destination.storageLength() + 1, item);`. Delete the old `assign(into, BlockValue.block(mine))` line. BlockStorage copies its constructor list, so the fill must go through the destination's storage, not through `mine`. This preserves the passing different-word case (#110/#151: a = [], b = [collected]) because each collect fills only its own destination.

**C:** rebol3-source/src/core/u-parse.c lines 972-988 (SYM_COLLECT, wrd == SYM_SET: Make_Block then Set_Var_Series before the rule runs; keeps append into collect->block, and nothing reassigns the word at collect end - Parse_Collect_End lines 679-691 only restores the outer target)

## [high] parse-test.r3 / COLLECT/KEEP (subgroups "string collect copy" and "binary collect copy") - asserts #136, #137, #138
**Cause:** `parse "ab" [collect some [keep copy __ skip]]` must keep the copied series each round - ["a" "b"] for a string, [@a @b] for a ref, [#{01} #{02}] for a binary. In the C, PF_KEEP with PF_COPY set keeps the series COPY produced (`ser`), typed as the input's type, even for a one-item match. JEBOL's StringParser.keep has no COPY branch: it falls through to the generic path and keeps `oneOrSliceFrom(before)`, which turns a single-character match into a char! (#"a") or a single byte into an integer, not the one-character string/ref/one-byte binary the copy produced. (Parser.java already has keepTheCapture for block input; StringParser lacks the equivalent.)

**Where:** src/main/java/org/jebol/domain/parse/StringParser.java keep() ~lines 595-608: add a copy branch after the pick check, or make the final add conditional

**Fix:** In StringParser.keep(), before the generic fall-through, detect `rules.get(at + 1)` being the word "copy" (WORD datatype, canonical "copy"). Then run the existing fall-through (matchOne on at+1 already performs the capture and assigns __ the whole slice via capture(wholeSlice=true)), but keep `sliceFrom(before)` instead of `oneOrSliceFrom(before)`: e.g. `boolean keptViaCopy = ...; collecting.peek().add(keptViaCopy ? sliceFrom(before) : oneOrSliceFrom(before));`. sliceFrom already answers a StringValue carrying the source's datatype (so ref stays ref) and a BinaryValue when walking bytes, matching the C's typed copy.

**C:** rebol3-source/src/core/u-parse.c lines 1393-1410 (post-match: `if (ser && GET_FLAG(flags, PF_COPY))` appends the copied series to collect->block as parse->type - Set_Binary for binary, VAL_SET(val, parse->type) for strings/refs)

## [high] parse-test.r3 / COLLECT/KEEP (subgroup "binary collect keep pick") - asserts #143
**Cause:** `parse #{0102} [collect [keep pick 2 skip]]` must answer [1 2] - integers, one per byte. The C's Parse_Keep, when the input is a binary and PF_PICK is set, appends each byte with SET_INTEGER. JEBOL's StringParser.keepIndividually always appends `CharacterValue.of(text.charAt(character))`, so a binary parse keeps char! values (the suite's [1 2] comparison then fails; the string and ref cases #140/#141 pass by accident of the shared code path).

**Where:** src/main/java/org/jebol/domain/parse/StringParser.java keepIndividually() ~line 619

**Fix:** In the loop, branch on walkingBytes: `collecting.peek().add(walkingBytes ? IntegerValue.of(text.charAt(character)) : CharacterValue.of(text.charAt(character)));`. (text.charAt for a binary is already the byte's value 0-255 via textOfSeries.)

**C:** rebol3-source/src/core/u-parse.c lines 774-780 (Parse_Keep, binary input with pick: `SET_INTEGER(val, BIN_HEAD(series)[i])` per byte, versus SET_CHAR for string input)

## [high] parse-test.r3 / COLLECT/KEEP (subgroup "string collect into/after compatibility test") - asserts #170
**Cause:** `a: #{} parse #{01} [collect into a keep skip]` must leave a = #{01}. The C allows a binary INTO target for a binary parse and inserts the kept bytes into it (Insert_String on the target series). JEBOL's StringParser.deliver switches only on BlockValue and StringValue; a BinaryValue target falls into the `default -> { }` arm and the collection is silently discarded, so a stays #{}. (refuseWrongIntoTarget already admits the binary-into-binary pairing, so no error is raised either - the delivery just does nothing.)

**Where:** src/main/java/org/jebol/domain/parse/StringParser.java deliver() ~lines 694-710: add a `case BinaryValue existing` arm before `default`

**Fix:** Add `case BinaryValue existing -> { int where = past ? existing.storageLength() + 1 : existing.index(); ... }` that flattens the gathered values to bytes and inserts them at `where` (iterate the byte list in reverse, `existing.storage().insertAt(where, byte)`, mirroring the StringValue arm). Flattening: an IntegerValue contributes its magnitude as one byte (that is what `keep skip` on a binary collects, via oneOrSliceFrom); a BinaryValue slice contributes each of its bytes in order.

**C:** rebol3-source/src/core/u-parse.c lines 999-1006 (SYM_INTO admits `IS_BINARY(val) && parse->type == REB_BINARY`) and lines 734-738 (Parse_Keep: `ANY_BINSTR(parse->collect->value)` target takes the kept bytes via `Insert_String(block, index, series, begin, count, FALSE)`)

## [medium] parse-test.r3 / COLLECT/KEEP (subgroup "string collect complex") - asserts #175
**Cause:** The html rule recurses through `opt tags`, where tags is a word naming the whole rule block - the recursion is what nests each element's collect inside its parent's. JEBOL's StringParser.optional() calls `matchValue(rules.get(at + 1))` directly, and matchValue does not resolve a word to the rule it names: the word `tags` falls through to `textOf(rule)` and is matched as the literal text "tags", which never appears in the input. So `opt tags` silently matches nothing, no recursion happens, every tag name and text keeps into the single outer collect, and the first `</...>` reached fires the `break` that ends the one and only `any` loop - JEBOL answers a flat prefix like [html head title "Test"] instead of the nested [html [head [title ["Test"]] body ...]]. The C resolves word rules everywhere (Get_Var in the item-processing path), so opt over a named rule recurses. Confidence medium only because the rule is large enough that a second, masked defect could sit behind this one; this defect alone is certain to make the assert fail.

**Where:** src/main/java/org/jebol/domain/parse/StringParser.java optional() ~lines 795-801

**Fix:** Route optional through matchOne so the rule after OPT gets full treatment: `int before = position; if (matchOne(rules, at + 1) == NO_MATCH) { position = before; } return 1 + ruleSpan(rules, at + 1);` (guard `at + 1 >= rules.size()` with a PARSE_END raise first, matching the C's Trap on a dangling opt). matchOne's default arm already resolves a word via matchNamedRule, which runs the tags block through matchSequence and gives the nested collect.

**C:** rebol3-source/src/core/u-parse.c lines 909-911 (SYM_OPT just sets mincount = 0; the following item then goes down the ordinary path) and lines 1173-1177 (`if (IS_WORD(item)) item = Get_Var(item);` - a word rule is resolved to what it names before matching)

## [high] parse-test.r3 / COLLECT/KEEP (subgroup "collect/keep expression") - asserts #178, #180
**Cause:** `parse [] [collect keep pick (1)]` must answer [1] and `collect keep pick ([1])` must answer [[1]] - KEEP PICK followed by a paren behaves exactly like plain KEEP of the expression. In the C, SYM_KEEP consumes PICK (sets PF_PICK, rules++), then finds IS_PAREN and calls Parse_Keep_Expression, so the pick flag never matters for a paren. JEBOL's Parser.keep checks paren before pick, so `keep pick (...)` takes the pick branch: keepIndividually matches the paren as a rule (evaluated, consumes no input) and then adds `input.subList(before, position)` - nothing, because the input is empty and the position never moved. The collection stays empty and parse [] answers [].

**Where:** src/main/java/org/jebol/domain/parse/Parser.java keep() ~lines 709-712 (the pick branch); mirror the same fix in src/main/java/org/jebol/domain/parse/StringParser.java keep() ~lines 595-598 for string input parity

**Fix:** In the pick branch of keep(), before delegating to keepIndividually, check whether the value after PICK is a paren: `if (at + 2 < rules.size() && rules.get(at + 2) instanceof BlockValue paren2 && paren2.datatype() == Datatype.PAREN) { Value produced = evaluator.evaluateOrRaise(paren2.as(Datatype.BLOCK), context); collecting.peek().add(produced); return 3; }` - i.e. identical to the existing keep-paren branch but consuming the extra PICK word (span 3, not 2). The produced value goes in whole, so ([1]) keeps the block [1] as one item, matching #180's [[1]].

**C:** rebol3-source/src/core/u-parse.c lines 1049-1063 (SYM_KEEP: PICK sets PF_PICK and advances, then `if (IS_PAREN(rules)) { Parse_Keep_Expression(parse, rules); ... }` - the paren path runs regardless of pick) and lines 807-835 (Parse_Keep_Expression appends the evaluated item as a single value)

## [high] parse-test.r3 / DO (asserts 309-310) and Other parse issues (315, 370) - asserts #309, #310, #315, #370
**Cause:** A rule item that is, or resolves to, an unset or a function value must raise a parse-rule error; JEBOL matches it literally and answers false. The C refuses any resolved rule item with VAL_TYPE <= REB_UNSET or >= REB_NATIVE (u-parse.c:1205 'if (VAL_TYPE(item) <= REB_UNSET || VAL_TYPE(item) >= REB_NATIVE) goto bad_rule' -> Trap1(RE_PARSE_RULE, rules-1), arg1 = the item as written). This covers three shapes here: (a) #309/#310 'parse [1 + 1] [set result do integer!]' - DO is a parse command word (boot/words.reb lines 147-184 puts 'do' in the SYM_OR_BAR..SYM_END command range) whose implementation is compiled out (USE_DO_PARSE_RULE is never defined anywhere in rebol3-source), so it hits the iterated-section default -> bad_rule -> parse-rule; JEBOL instead resolves 'do' via matchNamedRule to the DO native and compares it literally against the input, answering false, so 'error? err: try [...]' is false. (b) #370 'parse "abc" [huh "b"]' - huh is defined-but-unset (the harness defines fresh words); Get_Var (c-frame.c:1203) returns the unset slot without trapping, then the <= REB_UNSET check traps parse-rule with arg1 = 'huh; JEBOL's matchNamedRule hands the unset value to matchValue and answers false. (c) #315 'attempt [parse [#(unset)][#(unset)]]' - a literal unset in the rule hits the same check and errors, so attempt yields none; JEBOL's matchesLiteral compares unset = unset, matches, and answers true.

**Where:** src/main/java/org/jebol/domain/parse/Parser.java matchNamedRule (~line 921-930) and matchOne literal fallback (~line 247); src/main/java/org/jebol/domain/parse/StringParser.java matchNamedRule (~line 718-727) and matchOne fallback (~line 415)

**Fix:** In both parsers' matchNamedRule: after fetching the named value, if it is an UnsetValue or its datatype().isAnyFunction(), throw new Raised(ErrorValue.about(EvaluationFailure.PARSE_RULE category/id/description, wordAsWritten)) where wordAsWritten is the plain WordValue from the rule (so e/arg1 = 'huh and e/id = 'parse-rule hold; #309 needs only the id). In both parsers' matchOne, before the final matchValue fallback, raise the same error when the literal rule item is an UnsetValue (or any-function value). Do not refuse none! or logic! (REB_NONE > REB_UNSET in the C's ordering; asserts #312-#314 rely on them matching).

**C:** rebol3-source/src/core/u-parse.c:1205 and 1487-1488 (bad_rule), rebol3-source/src/core/c-frame.c:1203-1226 (Get_Var returns unset without trapping), rebol3-source/src/boot/words.reb:147-184 (do is a parse command; end must be last)

## [high] parse-test.r3 / Other parse issues / issue-529 - asserts #322
**Cause:** 'a: context [b: string!]  parse ["test"] [a/b]' - a path in a rule must be evaluated and its result used as the rule value (string! datatype matches "test"). The C routes any path through Do_Parse_Path/Get_Parse_Value (evaluate plain path; set-path assigns the current position; get-path resets the input series). JEBOL's Parser.matchOne has no path branch: a PATH BlockValue falls to matchValue's default, matchesLiteral compares the path itself against the input item, and the parse answers false. StringParser is worse for string input: a path BlockValue falls into the 'nested block' branch and is walked as a sub-rule.

**Where:** src/main/java/org/jebol/domain/parse/Parser.java matchOne (add a branch before the matchValue fallback at ~line 247); src/main/java/org/jebol/domain/parse/StringParser.java matchOne (~line 415) and matchValue (~line 937, which must not treat a PATH as a sub-rule block)

**Fix:** In both matchOne methods, add: if the rule item is a BlockValue with datatype PATH, evaluate it (evaluator.evaluateOrRaise(BlockValue.block(List.of(path)), context) - the same recipe valueToPutIn already uses) and match the result via matchValue, returning 1 or NO_MATCH. For completeness per Do_Parse_Path: a SET_PATH rule item assigns the input series at the current position and consumes nothing; a GET_PATH evaluates and re-targets the input (raise parse-series if the result is not a series). The plain-path case alone discharges #322.

**C:** rebol3-source/src/core/u-parse.c:134-150 (Get_Parse_Value), 155-185 (Do_Parse_Path), 1170-1174 (ANY_PATH dispatch)

## [high] parse-test.r3 / Other parse issues / issue-591 - asserts #323
**Cause:** 'parse " " [0]' must raise parse-end with e/arg1 = 0: the C does Trap1(RE_PARSE_END, rules-2) where rules-2 is the integer rule item itself. JEBOL's StringParser.countedRepeat raises PARSE_END via Raised.of(failure, String), whose arg1 becomes a word coined from the whole prose message ('a repeat count has no rule after it to repeat'), so e/id = 'parse-end passes but e/arg1 = 0 fails.

**Where:** src/main/java/org/jebol/domain/parse/StringParser.java countedRepeat (~line 422-424, reached from the IntegerValue branch of matchOne at ~405-414); mirror in Parser.java matchCountedRule (~line 268-270)

**Fix:** Pass the integer rule value (rules.get(at), or the second integer when a range) into the raise and throw new Raised(ErrorValue.about(PARSE_END's category, "parse-end", message, thatIntegerValue)) so arg1 is the integer 0. Add a Raised.of(EvaluationFailure, Value) single-subject overload in Raised.java to carry it (the existing overloads take a String or two Values).

**C:** rebol3-source/src/core/u-parse.c:1183-1192 (integer counter setup: 'item = Get_Parse_Value(rules++); if (IS_END(item)) Trap1(RE_PARSE_END, rules-2)')

## [high] parse-test.r3 / Other parse issues / issue-2141 - asserts #344
**Cause:** 'parse "xy" [some thru [x | y]]' - StringParser.repeat returns a hardcoded 2 as the number of rule items consumed, but 'thru [x | y]' spans 2 items so 'some thru [x | y]' spans 3. After the repeat succeeds, matchAllOf resumes on the [x | y] block, matches it as a fresh sub-rule at the end of the input, fails, and the whole parse answers false. The block Parser.repeat already returns 1 + ruleSpan and is correct, which is why the block-input siblings pass.

**Where:** src/main/java/org/jebol/domain/parse/StringParser.java repeat, line 790 ('return matched >= leastNeeded ? 2 : NO_MATCH')

**Fix:** Return 'matched >= leastNeeded ? 1 + ruleSpan(rules, at + 1) : NO_MATCH' exactly as Parser.repeat does (ruleSpan already knows thru spans itself plus its target). Note StringParser.optional (lines 795-801) has the same hardcoded 2 and also bypasses matchOne by calling matchValue directly - same defect class, not covered by this cluster's asserts but worth fixing in the same pass.

**C:** rebol3-source/src/core/u-parse.c:1236-1241 (TO/THRU consume rulen=1 extra rule item; 'rules += rulen' at 1338 keeps the walk aligned)

## [high] parse-test.r3 / Other parse issues / issue-297 - asserts #347
**Cause:** 'parse "" [some [(a: true)]]' must be true: in the C's iterated loop, a round that matches without advancing is counted first (count++ at u-parse.c:1301) and only then hits the no-progress break (1305-1316), where count(1) >= mincount(1) keeps the index and the rule succeeds. JEBOL's repeat treats a no-progress round as a failed round: the condition '(position == before && text.length() == wasLong)' breaks before matched++ runs, so matched stays 0 < 1 and SOME fails (a is still set because the paren ran).

**Where:** src/main/java/org/jebol/domain/parse/StringParser.java repeat lines 783-788; same structure in Parser.java repeat lines 804-810

**Fix:** Split the two break conditions in both repeat methods: if consumed == NO_MATCH, restore position and break (round does not count); otherwise matched++ first, and then if nothing progressed (position unchanged and input length unchanged) break while keeping the position. BREAK handling (RepeatEnded) and the counted-repeat loop stay as they are.

**C:** rebol3-source/src/core/u-parse.c:1300-1317 (count++ precedes the 'If input did not advance' break; count < mincount is only checked inside it)

## [high] parse-test.r3 / Other parse issues / issue-2130 - asserts #351, #352
**Cause:** 'not parse ser: "foo" [copy val pos: skip]' must leave val = "f": in the C, SYM_COPY sets PF_COPY and the target word, then the following set-word 'pos:' is processed in the pre-rule section (Set_Var_Series, u-parse.c:1126-1129) and continues WITHOUT clearing the pending flags, so the copy applies to the next real match ('skip') and captures the span from before the mark to after it. JEBOL's capture() hands the set-word itself to matchOne as the captured rule: the mark is assigned, zero characters are consumed, and val becomes "" instead of "f". #352 ('copy val: pos: skip') is the same path with a set-word capture target, which capture() already accepts.

**Where:** src/main/java/org/jebol/domain/parse/StringParser.java capture lines 482-504; the block-input twin Parser.java capture lines 868-888 has the identical latent defect (no current assert catches it there because #330/#331/#350 never read val)

**Fix:** In capture, after reading the target word: let ruleAt = at + 2; while rules.get(ruleAt) is a WordValue with datatype SET_WORD, assign it the input series at the current position (same expression the set-word branch of matchOne uses) and ruleAt++ (raising PARSE_END if the rules run out). Then matchOne(rules, ruleAt), capture the span from the position before the marks, and return (ruleAt - at) + ruleSpan(rules, ruleAt) so the walk resumes after the captured rule. Keep the existing get-word refusal, applied at ruleAt.

**C:** rebol3-source/src/core/u-parse.c:913-921 (SYM_COPY/SET take the word and continue), 1126-1129 (set-word processed without touching flags), 1364-1371 (PF_COPY copies begin..index at post-match)

## [high] parse-test.r3 / Other parse issues / get-word use - asserts #356
**Cause:** 'parse s [x: "ab" thru :s "abcd"]' must raise parse-rule: a get-word is not a matchable TO/THRU target, and the C's Parse_To string branch has no case for it, falling through to Trap1(RE_PARSE_RULE, item - 1). JEBOL's StringParser.seek only refuses decimals and command words, so the get-word falls to textOf(:s) = "s", indexOf fails, and the parse answers false instead of erroring - 'error? e: try [...]' is false.

**Where:** src/main/java/org/jebol/domain/parse/StringParser.java seek, the refusal branch at lines 831-838

**Fix:** Extend the refusal: a WordValue whose datatype is GET_WORD or SET_WORD (any non-plain word) thrown as PARSE_RULE, alongside the existing decimal and command-word cases. Truer to the C is a whitelist - for string input only char, any-string (string/file/url/email/ref/tag), binary, bitset, integer, block and the word END are matchable, everything else raises PARSE_RULE - but the get-word/set-word addition alone discharges #356 (only e/id is asserted).

**C:** rebol3-source/src/core/u-parse.c:554-574 (Parse_To string branch: the else after string/char/bitset/tag is Trap1(RE_PARSE_RULE, ...)); 1238 (thru's target passes through Get_Parse_Value, which leaves get-words as-is)

## [high] parse-test.r3 / Other parse issues / get-word use - asserts #359
**Cause:** After 'parse s ["ab" p: "c" :p set x to end]' the suite wants x = #"c": on string input the C's SET assigns only the FIRST character of the matched span (GET_UTF8_CHAR(series, begin), u-parse.c:1383-1388; an integer for binary input, NONE when the count is 0) however many characters the rule consumed. JEBOL's StringParser.capture non-copy branch calls oneOrSliceFrom(before), which answers the whole slice "cd" when more than one character matched, so x = "cd".

**Where:** src/main/java/org/jebol/domain/parse/StringParser.java capture line 502 (the wholeSlice=false arm)

**Fix:** In capture with wholeSlice false: if position == before assign NoneValue.none(); else assign CharacterValue.of(text.charAt(before)) - IntegerValue.of(text.charAt(before)) when walkingBytes - never a slice. Leave oneOrSliceFrom in place for KEEP, whose C counterpart (Parse_Keep) genuinely keeps multi-character spans as strings.

**C:** rebol3-source/src/core/u-parse.c:1373-1390 (PF_SET_OR_COPY without PF_COPY: count==0 -> SET_NONE, else the single char/integer at begin)

## [high] parse-test.r3 / Other parse issues / get-word use (360) and invalid rule error message (371) - asserts #360, #371
**Cause:** 'parse "abcd" [x: "ab" copy y :s thru "abcd"]' and 'parse data [some "a" copy var :pos]' must raise parse-rule with e/arg1 = the get-word itself (quote :s / quote :pos): the C hits 'if (flags != 0) Trap1(RE_PARSE_RULE, rules-1)' at u-parse.c:1153, arg1 being the get-word rule item. JEBOL's StringParser.capture already refuses the get-word with PARSE_RULE (so e/id passes) but builds it through Raised.of(failure, String), which coins arg1 as a word made from the whole prose message - so the e/arg1 comparison fails.

**Where:** src/main/java/org/jebol/domain/parse/StringParser.java capture, the throw at lines 491-495; plus a new Raised.of(EvaluationFailure, Value) overload in src/main/java/org/jebol/domain/eval/Raised.java

**Fix:** Change the throw to carry the get-word value as the error subject: throw new Raised(ErrorValue.about(PARSE_RULE.category(), PARSE_RULE.errorId(), message, asRule)) - asRule is the WordValue with datatype GET_WORD, which compares equal to what 'quote :s' evaluates to. Add the single-Value Raised.of overload (the class has String and two-Value forms only). If finding 6's set-word-skipping fix moves this guard to ruleAt, keep the refusal itself and its new arg1.

**C:** rebol3-source/src/core/u-parse.c:1142-1153 (get-word while capture flags are pending: 'don't allow code like: [copy x :pos integer!]' -> Trap1(RE_PARSE_RULE, rules-1))

## [high] parse-test.r3 / CHANGE (subgroup "CHANGE with position") - asserts #246, #247, #249, #250, #251, #252
**Cause:** CHANGE followed by a position argument is not recognised. In the C, when the item after CHANGE is a get-word, or a plain word whose value is a series sharing the parse input's storage, PARSE does not match a rule at all: it seeks to that position, swaps so begin < index, sets count = index - begin, and jumps to do_modify, which changes exactly the span between the current position and the mark (working in either direction, so `(p: back tail b) change p` changes forwards from the current position to the mark). JEBOL instead: (a) for a plain word, Parser.changeMatched calls matchOne -> matchNamedRule, which treats the position value as a sub-rule (matchSequence over its remaining values) and fails, so parse answers false (#246, #251, and string-side #249, #252 where matchValue compares the mark's text against the input and fails); (b) for a get-word, matchOne -> seekToMark/get-word branch moves position backwards and returns success, after which the removal loop `for (taken = position; taken > before; ...)` does nothing because position < before, and the replacement is inserted at the old position, producing [1 2 3 x 4 5] / "123x45" instead of [1 x 4 5] / "1x45" (#247, #250). Note #253/#254 (get-word mark past the current position) pass today only by accident through path (b); the fix must keep them green, which the min/max span does.

**Where:** Parser.changeMatched (src/main/java/org/jebol/domain/parse/Parser.java ~line 407) and StringParser.changeMatched (src/main/java/org/jebol/domain/parse/StringParser.java ~line 213)

**Fix:** In both changeMatched methods, before calling matchOne on the rule, intercept a position argument: if rules.get(at+1) is a GET_WORD, or a plain WORD that is not a parse keyword, look its value up in its binding (word.isBound() ? word.binding() : context); if that value is a series sharing storage with `source` (BlockValue for Parser, StringValue/BinaryValue for StringParser), take it as a position. Compute markOffset = mark.index() - source.index(); begin = min(position, markOffset); count = abs(position - markOffset). Block parser: read the replacement from rules.get(at+2) (honouring an ONLY before it exactly as the existing code below does), resolve it with valueToPutIn, then remove `count` items at begin from both `input` and `source.storage()` (storage index source.index()+begin, 1-based), insert the (possibly spread) replacement at begin in both, set position = begin + putting.size(), and return (replacementSlot + 1) - at (3 for `change p ('x)`, 4 with ONLY). String parser: remove the characters in storage positions [source.index()+begin, source.index()+begin+count), insert the codepoints of replacementFor(rules.get(at+2)) at source.index()+begin, refresh `text = textOfSeries(source)`, set position = begin + insertedLength, return 3. When the looked-up word's value is not a same-storage series, fall through to the existing rule-matching path unchanged.

**C:** rebol3-source/src/core/u-parse.c: get-word branch with PF_CHANGE at lines 1131-1160 (label reset_input line 1138, swap+count and `goto do_modify` lines 1142-1149), plain-word same-series test at lines 1162-1166, do_modify at lines 1430-1463

## [high] parse-test.r3 / REMOVE (subgroup "remove using series' index") - asserts #275, #276, #277, #278
**Cause:** REMOVE followed by a word holding a position in the same series is not recognised. The C's SYM_REMOVE branch checks whether the next rule item is a word whose value is a series over the same storage as the parse input; if so it consumes the word, computes begin/count spanning from the mark's index to the current position (whichever direction), and jumps straight to do_remove: Remove_Series(begin, count), index = begin, PF_ADVANCE - no rule is matched at all, and it works both when the mark is behind (#275, #277: mark at 1, position 3, removes 2 items) and ahead (#276, #278: mark at tail, removes from the current position to the tail). JEBOL's removeMatched in both parsers instead matches the word as a named rule: the block parser runs the mark's remaining values as a literal sub-rule (fails for #277; matches the empty tail without consuming for #278, so nothing is removed and the parse ends short of the input's end), and the string parser compares the mark's text against the input at the current position (fails for #275 and #276). All four asserts answer false.

**Where:** StringParser.removeMatched (src/main/java/org/jebol/domain/parse/StringParser.java ~line 253) and Parser.removeMatched (src/main/java/org/jebol/domain/parse/Parser.java ~line 546)

**Fix:** At the top of both removeMatched methods: if rules.get(at+1) is a plain WORD (the C accepts only IS_WORD here, not a get-word) that is not a parse keyword, look its value up in its binding; if the value is a series sharing storage with `source`, compute markOffset = mark.index() - source.index(), begin = min(position, markOffset), count = abs(position - markOffset); remove `count` items starting at begin (string parser: storage positions source.index()+begin+count-1 down to source.index()+begin, then `text = textOfSeriesSource()`; block parser: input.remove and source.storage().removeAt in the same descending loop the method already uses); set position = begin and return 2 - even when count is 0, the C still succeeds and sets index = begin. Otherwise fall through to the existing rule-matching path.

**C:** rebol3-source/src/core/u-parse.c: SYM_REMOVE word-position branch at lines 936-955 (`item = Get_Var(rules); if (VAL_SERIES(item) == parse->series) { ... goto do_remove; }`), do_remove at lines 1421-1429

## [high] parse-test.r3 / REMOVE (subgroup "remove") - asserts #269, #270, #271, #272, #273, #274
**Cause:** Both parsers walk a snapshot of the input, and a paren that mutates the series through a native (here `remove/part` inside `(e: remove/part s e)` / `(s: remove/part s 2)`) leaves the snapshot stale. The C reads the live series and clamps the index to the tail after every paren, so after the paren shrinks the input the `:e`/`:s` get-word seek lands correctly and the ANY/SOME loop sees the shrink (plus PF_ADVANCE from the seek, consumed at lines 1305-1312) and keeps iterating until the input is consumed. In JEBOL: StringParser evaluates the paren but never refreshes its `text` field (refresh happens only in its own insert/change/remove keyword paths), and Parser copies the input into a private ArrayList at construction and never re-reads the source, so after the paren the repeat's progress test (`position == before && input.size()/text.length() unchanged`) sees no movement and breaks the loop. #269 then fails atEnd (position 0 against a stale 2-char text) leaving v = "b" (#270); #271/#273 break out of SOME after zero/one rounds against the stale 6-item list, answering false and leaving v = ["a" #L 2 "b"] (#272, #274).

**Where:** Parser.matchValue paren branch (src/main/java/org/jebol/domain/parse/Parser.java ~line 935) and StringParser.matchValue paren branch (src/main/java/org/jebol/domain/parse/StringParser.java ~line 933)

**Fix:** After evaluating a paren rule item, re-read the input from the source series and clamp the position, mirroring the C's `if (index > series->tail) index = series->tail` after every paren. StringParser: in the matchValue paren branch, after evaluateOrRaise do `text = textOfSeries(source); position = Math.min(position, text.length());`. Parser: make `input` refreshable (drop the final modifier or refresh in place): when source != null, after evaluateOrRaise do `input.clear(); input.addAll(source.remaining()); position = Math.min(position, input.size());`. That makes the shrink visible to the existing repeat progress tests (`text.length() != wasLong` / `input.size() != wasLong`), which then count the round as progress - no separate PF_ADVANCE analogue is needed for these six asserts, because every seek here coincides with a shrink. The get-word seek bounds checks (seekToMark and the string get-word branch) then also compute against fresh sizes.

**C:** rebol3-source/src/core/u-parse.c: paren evaluation with tail clamp at lines 1176-1180; PF_ADVANCE set on get-word seek at line 1157 and consumed by the repeat loop at lines 1305-1312

## [high] parse-test.r3 / CHANGE (subgroup "CHANGE undefined") - asserts #238
**Cause:** The string parser never looks up a word written in CHANGE's replacement slot. The C's do_modify resolves the replacement with Get_Parse_Value (a non-keyword word is fetched from its context) and then traps: `if (IS_UNSET(item)) Trap1(RE_NO_VALUE, rules-1)`, so `parse "abc" ["a" change skip undefined-word]` raises an error with id 'no-value. JEBOL's StringParser.replacementFor evaluates only a paren and otherwise calls textOf on the value as written, so the word `undefined-word` becomes the literal fourteen-character replacement text "undefined-word": no error is raised, the parse just answers false, `error? e: try [...]` is false, and the assert fails. (The block parser already does this correctly via valueToPutIn, which is why only the string-input assert fails.)

**Where:** StringParser.replacementFor (src/main/java/org/jebol/domain/parse/StringParser.java ~line 245); the same gap exists in StringParser.insertValue (~line 898) which also takes the value as written

**Fix:** Mirror Parser.valueToPutIn in StringParser.replacementFor (and use it from insertValue too): a paren is evaluated as now; a LIT_WORD drops its tick (textOf of the word); a plain WORD is resolved from its binding (word.isBound() ? word.binding() : context) - if the context does not know the word or its slot holds unset, throw Raised.of(EvaluationFailure.NO_VALUE, word.spelling()), which carries id "no-value" (EvaluationFailure.java line 7), otherwise textOf the fetched value; anything else stands as written. Resolve through the context slot rather than evaluator.evaluateOrRaise so a word bound to a function is not invoked, matching Get_Parse_Value's Get_Var semantics.

**C:** rebol3-source/src/core/u-parse.c: do_modify replacement resolution at lines 1434-1448, unset trap `if (IS_UNSET(item)) Trap1(RE_NO_VALUE, rules-1)` at line 1448; Get_Parse_Value at lines 132-150

## [high] object-test.r3 ===Set OBJECT=== "set OBJECT OBJECT" - asserts #2, #3, #5
**Cause:** JEBOL's setFieldsFromObject copies each matching source value into the target slot by reference and stops there. The C, after the name-matched copy, deep-clones every value in the target frame (Copy_Deep_Values with TS_CLONE: all series, functions/closures, maps) and then rebinds any word bound to the SOURCE object's frame to the target frame, function bodies included (Rebind_Block with REBIND_FUNC | REBIND_TABLE). So in JEBOL new/ser shares data's string (appending "a" corrupts data/ser, failing #5) and new/fce is data's function still bound to data's context (new/fce doubles data/int not new/int, failing #2 and #3).

**Where:** src/main/java/org/jebol/domain/eval/Natives.java, setFieldsFromObject (approx line 10803; the file is shifting under concurrent edits - find by method name)

**Fix:** After the existing per-slot copy loop, walk every non-self slot of `into` and replace its value with a clone: deep-copy blocks/parens/paths (new storage, recursing), copy strings/binaries/maps, and clone functions (new FunctionValue with a deep-copied body). During the clone, rebind every word whose binding == from.context() to into.context(); leave words bound anywhere else (globals) untouched. Suggest one shared helper clonedAndRebound(Value, Context from, Context into) since the make-clone finding below needs the identical operation.

**C:** rebol3-source/src/core/n-data.c REBNATIVE(set) lines 657-681, especially 678 Copy_Deep_Values(obj,1,tail,TS_CLONE) and 679 Rebind_Block(VAL_OBJ_FRAME(val), obj, ..., REBIND_FUNC|REBIND_TABLE); TS_CLONE in include/sys-core.h:172

## [high] object-test.r3 ===EXTEND object=== "append/part object!" - asserts #40 (suite line 113: append/part make object! [] b: [a 1 b 2 c 3] find b 'c)
**Cause:** When /part is a series position rather than an integer, JEBOL raises invalid-part. objectGainingFields calls partOf(added, ...), and partOf calls the two-argument howManyWanted overload which passes NoneValue as the source; the SeriesValue branch of howManyWanted then finds the source is not a series sharing storage with the /part position and throws INVALID_PART. The C computes the length as the index difference between the /part position and the argument block (Partial1), so `find b 'c` means 4 items = 2 pairs. Integer /part counts (suite lines 109-112, 114-115) already work, which is why only this one assert fails.

**Where:** src/main/java/org/jebol/domain/eval/Natives.java, partOf (approx line 13378): change `howManyWanted(arguments, refinements, 2)` to the source-carrying overload

**Fix:** In partOf, call howManyWanted(block, arguments, refinements, 2) - passing the block being appended as the source - so a series-valued /part resolves to upTo.index() - block.index(). The negative and integer paths already behave correctly.

**C:** rebol3-source/src/core/t-object.c line 471: Append_Obj(VAL_OBJ_FRAME(value), arg, Partial1(arg, D_ARG(AN_LENGTH))) - Partial1 takes the argument block itself as the series the position is measured against

## [high] object-test.r3 ===EXTEND object=== "bind to object" - asserts #46 (suite line 124: all [error? e: try [bind 's o] e/id = 'not-in-context])
**Cause:** JEBOL's BIND, given a single word the target does not hold and no /new or /set, silently binds the word to the target context and returns it (`word.boundTo(target.knows(...) ? holderOf : target)`). The C raises: Bind_Word searches only the frame's own words, and on failure without BIND_ALL it does Trap1(RE_NOT_IN_CONTEXT, arg). So the try answers a word, not an error.

**Where:** src/main/java/org/jebol/domain/eval/Natives.java, define("bind", ...) single-word branch (approx line 3628-3640)

**Fix:** In the WordValue branch, when neither /new nor /set is asked for and the word is not found (at minimum when !target.knows(word.canonical()); the C checks the object's own frame only, so !target.holds(...) is the faithful reading - verify borrowed mezz files still load if own-slots-only is used), throw a new EvaluationFailure.NOT_IN_CONTEXT (ErrorCategory.SCRIPT, id "not-in-context") carrying the word as arg1. Add the constant to EvaluationFailure.java beside NOT_DEFINED; the catalogue entry already exists (errors.reb: `not-in-context: [:arg1 {is not in the specified context}]`, and ErrorCatalogue.java already lists the id).

**C:** rebol3-source/src/core/n-data.c REBNATIVE(bind) lines 335-347: `if (!Bind_Word(frame, arg)) { if (flags & BIND_ALL) Append_Frame(...); else Trap1(RE_NOT_IN_CONTEXT, arg); }`

## [high] object-test.r3 ===MAKE object=== "issue-1874" - asserts #69, #70 (suite lines 202-203: not same? a/b c/b, not same? a/b d/b)
**Cause:** Cloning an object with MAKE shares series values. makeObject's prototype copy does fields.set(spelling, rehomed(slot.value(), fields)) and rehomed passes anything that is not a FunctionValue through unchanged, so c: make a [] holds the very same block as a/b; mergedObject (make a make object! []) shares the same way. The C's Make_Object clone path deep-copies every parent value with TS_CLONE (blocks, strings, maps, functions) and then Rebind_Frame rebinds source-frame words inside the copies.

**Where:** src/main/java/org/jebol/domain/eval/Natives.java, makeObject prototype.ifPresent(...) (approx line 3691) and mergedObject (approx line 3715)

**Fix:** Replace rehomed with the shared clonedAndRebound(value, prototypeContext, fields) helper from the SET finding: deep-copy block/paren/path storage, copy strings/binaries/maps, clone function bodies, and rebind words bound to the prototype's context to the new fields context, recursing into nested blocks. Apply it in both makeObject's prototype copy and both copy loops of mergedObject.

**C:** rebol3-source/src/core/c-frame.c Make_Object lines 482-495 (Copy_Block_Values/Copy_Deep_Values with TS_CLONE) and t-object.c lines 397-407 (make-clone calls Make_Object then Rebind_Frame)

## [high] object-test.r3 ===MAKE object=== "issue-2045-a" - asserts #79 (suite line 236: 1 = o3/g)
**Cause:** When MAKE clones a method, JEBOL rebinds the whole body by name: rehomed does Binder.bind(function.body(), fields), and Binder.bind rebinds every word whose name the new context knows, whatever the word was bound to before. Global f's body [a] has a bound to the user context, but o3's fields also know 'a, so the clone's a is captured by o3 and o3/g answers 4. The C's Rebind_Block rebinds only words whose current frame IS the source object's frame (`VAL_WORD_FRAME(data) == src_frame`), so a word bound to the global context stays global and o3/g answers 1. o1/g and o2/g pass in JEBOL only because their g was assigned directly in the body, not cloned.

**Where:** src/main/java/org/jebol/domain/eval/Natives.java, rehomed (approx line 3736) - same change as issue-1874; rehomed must take the prototype's Context and rebind selectively

**Fix:** Make the function-clone path rebind by binding, not by name: for each word in the (deep-copied) body, if word.isBound() and word.binding() == the prototype object's context, rebind to the new fields; otherwise leave it exactly as it is (including unbound). Keep constructing the clone with closedOver = fields. This is the same clonedAndRebound helper; issue-2049/2050/2045-b already pass and keep passing because their bodies' words are bound to the prototype frame (rebound) or to the global context (left alone).

**C:** rebol3-source/src/core/c-frame.c Rebind_Block lines 1037-1060, condition on line 1053: `ANY_WORD(data) && VAL_WORD_FRAME(data) == src_frame`; REBIND_FUNC recursion into VAL_FUNC_BODY on line 1058

## [high] object-test.r3 ===MAKE object=== "issue-2118" and "construct" - asserts #85 (suite lines 252-256: construct [a: b:] gives none for both) and #93 (suite line 268: none? get/any in construct [a: <unset>] 'a)
**Cause:** Two faces of one divergence in constructInto: plain CONSTRUCT must never leave a field unset, because the C's frame slots are initialised to NONE (Create_Frame: SET_NONE on every slot) and Do_Construct floors any value below none! (an unset) to none. JEBOL's constructInto leaves a trailing set-word as a defined-but-unset slot (built.define), so construct [a: b:] gives unset for both fields (#85, the all[] fails on none? o/a), and an unset value after a set-word (the () that insert put in the block, or a dropped one leaving the set-word trailing) also comes out unset instead of none (#93). construct/only correctly stays unset (Do_Min_Construct copies the unset as-is), which is why suite line 286 passes and must not regress.

**Where:** src/main/java/org/jebol/domain/eval/Natives.java, constructInto (approx line 5606)

**Fix:** In the non-/only path only: (1) after the walk, set every still-waiting set-word to NoneValue.none() instead of merely defining it; (2) when the arriving item is an UnsetValue, store NoneValue.none() (extend the namedConstant step: values below none! become none, matching `else SET_NONE(temp)` in Do_Construct). Leave the /only (asWritten) path exactly as it is - leftover set-words stay unset there, and an unset value in the block is stored as unset.

**C:** rebol3-source/src/core/c-frame.c Create_Frame line 447 (slots init to NONE) and c-do.c Do_Construct lines 1705-1709 (`else if (VAL_TYPE(value) >= REB_NONE) *temp = *value; else SET_NONE(temp);` - REB_UNSET sits below REB_NONE); Do_Min_Construct lines 1738-1750 copies as-is

## [high] object-test.r3 ===Object actions=== "empty?" - asserts #125, #126 (suite lines 332-333: empty? object [], empty? #(object! []))
**Cause:** JEBOL's tail? (which EMPTY? is, under the wider mezz spec `series [series! object! gob! port! bitset! typeset! map! none!]`) has no object case: the SERIES_LIKE parameter typeset does not admit OBJECT and the switch falls to raiseCannotUse, so empty? on an object raises instead of answering true. The C's object action handler answers A_TAILQ with SERIES_TAIL(frame) <= 1 (slot 0 is self). length? already handles objects, which is why #127-128 pass.

**Where:** src/main/java/org/jebol/domain/eval/Natives.java, define("tail?", ...) (approx line 6272) and the SERIES_LIKE typeset constant it uses

**Fix:** Add Datatype.OBJECT (and for fidelity PORT/MODULE, which share the object arm) to the typeset tail? accepts, and add `case ObjectValue object -> LogicValue.of(object.context().slots().stream().noneMatch(slot -> !slot.canonical().equals("self")))` - i.e. true when the only slot is self, mirroring SERIES_TAIL <= 1. Hidden fields still count as fields.

**C:** rebol3-source/src/core/t-object.c REBTYPE(Object) case A_TAILQ lines 561-566: `SET_LOGIC(DS_RETURN, SERIES_TAIL(VAL_OBJ_FRAME(value)) <= 1)`

## [high] object-test.r3 ===IN object=== "unset in ctx" - asserts #135 (suite line 359: none? unset in ctx 'd)
**Cause:** `in ctx 'd` correctly answers none for the absent word, but JEBOL's UNSET given none falls through its switch's default arm and returns UnsetValue.unset(), so none? is false. The C special-cases none: `if (IS_NONE(word)) return R_NONE;` (Rebol-wishes/28) - unset of none answers none.

**Where:** src/main/java/org/jebol/domain/eval/Natives.java, define("unset", ...) (approx line 4705)

**Fix:** In the unset native, when the argument is a NoneValue return it (NoneValue.none()) instead of falling through to the common `return UnsetValue.unset()`. Word and block arguments keep returning unset.

**C:** rebol3-source/src/core/n-data.c REBNATIVE(unset) lines 765-767: `else if (IS_NONE(word)) { return R_NONE; }`

## [high] object-test.r3 ===APPEND on OBJECT=== "issue-2531" - asserts #148 (suite line 402: none? foreach x [1] [context? 'x])
**Cause:** JEBOL's context? answers `new ObjectValue(word.binding())` for any bound word, and foreach binds its loop word into an ordinary Context (forEachLoop: Context.childOf(within)), so context? 'x inside the body answers an object. The C answers NONE when the word's frame is a loop frame - `if (IS_INT_SERIES(VAL_WORD_FRAME(word))) return R_NONE;` with the comment naming exactly `foreach x [1] [context? 'x]`. USE builds a real object frame, which is why suite lines 397-398 (issue-2076/197) pass and must keep passing.

**Where:** src/main/java/org/jebol/domain/eval/Natives.java, define("context?", ...) (approx line 3258) plus the loop-local contexts built in forEachLoop (approx line 4208), remove-each (approx line 3901) and map-each

**Fix:** Give Context a loop-frame marker (e.g. a boolean set by a markAsLoopFrame() call, or a static factory Context.loopFrameOf(parent)); set it on the locals context created by forEachLoop, remove-each and map-each (repeat's counter context too, if it uses the same shape - the C's Init_Loop covers all of them). In context?, answer NoneValue.none() when word.binding() is such a loop frame; otherwise keep the current ObjectValue answer.

**C:** rebol3-source/src/core/n-data.c REBNATIVE(contextq) lines 388-390: `if (IS_INT_SERIES(VAL_WORD_FRAME(word))) // in case like: foreach x [1] [context? 'x] return R_NONE;`

## [high] object-test.r3 ===PROTECT object!=== "protect/words object!" and "protect/words/deep object!" - asserts #157, #158, #165, #166 (suite lines 423-424 and 439-440: not error? try [extend o 'c 3], not error? try [append o 'd])
**Cause:** JEBOL's setProtection object arm closes the object to new names whenever it is protecting: `if (protectedNow || !onlyTheWords) closeToNewNames(protectedNow)`. So protect/words (and protect/words/deep) refuse EXTEND and APPEND with `protected`. The C's Protect_Object always locks the word slots but marks the frame series protected only when PROT_WORDS is NOT set - /words means "lock the existing words, leave the object open to new ones". The o/a: 0 and o/a/b asserts already behave (slots are locked either way).

**Where:** src/main/java/org/jebol/domain/eval/Natives.java, setProtection ObjectValue case (approx line 9482)

**Fix:** Change the guard to `if (!onlyTheWords) { object.context().closeToNewNames(protectedNow); }` - protecting with /words leaves the frame open, and unprotecting with /words already correctly leaves it closed (the C's unprotect arm has the same PROT_WORDS skip). Also pass onlyTheWords down the /deep recursion (`setProtection(slot.value(), protectedNow, true, onlyTheWords)`) so an inner object under protect/words/deep also stays open to new names, matching PROT_WORDS staying set through Protect_Value; not demanded by these four asserts but the same C rule.

**C:** rebol3-source/src/core/n-control.c Protect_Object lines 121-150: word slots via Protect_Word unconditionally, then `if (!GET_FLAG(flags, PROT_WORDS)) PROTECT_SERIES(series);` when setting and the mirrored skip when unprotecting

## [high] lexer-test.r3 ===Transcode=== / ===Invalid construction=== / ===Special tests=== (Transcode/part, Transcode/part/next) - asserts #46, #427, #428, #432, #450 (and the error id half of #24)
**Cause:** JEBOL invents error ids the C does not have. SyntaxFailure.MISSING_CLOSE maps to id 'missing-close' and EXTRA_CLOSE to 'extra-close', and neither throw carries arg1/arg2. In the C every unbalanced-delimiter path is one error: end-of-input inside an open series goes to missing_error (l-scan.c:1970) and a stray closer at top level goes to extra_error (l-scan.c:1797-1803, 1973-1981); both call Scan_Error(RE_MISSING, ...) -> id 'missing. arg1 is the token name from boot/strings.reb ('end-of-script' for TOKEN_EOF, 'end-of-block'/'end-of-paren' for a stray ]/)), arg2 is the delimiter char. So load {#[x} must give id=missing arg1="end-of-script" arg2="]" (#46), and transcode/part "1 23]" 5 (part range includes the stray ]) must give id=missing (#427, #428; #432/#450 are the same via next+part 4). JEBOL raises 'missing-close'/'extra-close' with no args, so every all[...] fails at the id.

**Where:** SyntaxFailure.java lines 10-12 (errorId strings), and Transcoder.java readSequence: the END_OF_INPUT throw at lines 238-242 and the closing-delimiter throws at lines 260-265

**Fix:** 1) SyntaxFailure: change MISSING_CLOSE, EXTRA_CLOSE and MISMATCHED_CLOSE errorId all to "missing". 2) In readSequence, replace failure(MISSING_CLOSE, delimiterFor(closing)) with a failureReading-style throw carrying tokenKind "end-of-script" and offendingText String.valueOf((char) closing) ("]" or ")"). 3) For EXTRA_CLOSE throw with tokenKind (next==']' ? "end-of-block" : "end-of-paren") and offendingText (next==']' ? "[" : "("); for MISMATCHED_CLOSE tokenKind from the found delimiter, offendingText the wanted closer. TranscodeResult.Failure.error() already puts tokenKind in arg1 and offendingText in arg2. Note: readBinary's unterminated-binary throw at Transcoder.java:890 currently shares MISSING_CLOSE; the C reports that as -TOKEN_BINARY -> 'invalid, so give it its own failure (INVALID_BINARY, tokenKind "binary") rather than letting it become 'missing. Update any JEBOL tests pinning 'missing-close'/'extra-close'.

**C:** rebol3-source/src/core/l-scan.c:1797-1803, 1966-1981, 2004; rebol3-source/src/boot/strings.reb:23-56 (token name table, RS_SCAN)

## [high] lexer-test.r3 ===Transcode=== "transcode/one" - asserts #24
**Cause:** transcode/one/error "#(" must answer error id 'missing with arg1 "end-of-script". In the C, TOKEN_CONSTRUCT calls Scan_Full_Block(scan_state, ')') (l-scan.c:1909); end of input inside it goes to missing_error -> RE_MISSING with TOKEN_EOF -> arg1 "end-of-script", arg2 ")"; the construct handler then sees IS_ERROR(value) and leaves the missing error standing (l-scan.c:1911-1913). JEBOL's readConstruct catches every MalformedSource from readSequence(')') and rethrows MALCONSTRUCT (id 'malconstruct), squashing the missing error.

**Where:** Transcoder.java readConstruct, the catch at lines 690-694

**Fix:** In the catch block, rethrow the original MalformedSource unchanged when malformed.failure == SyntaxFailure.MISSING_CLOSE; keep converting everything else (e.g. the invalid '1d' inside "#(block! [1d)", which must stay 'malconstruct - asserts #22/#23 pass today). Depends on the missing-close finding above for the id and arg1 to be right once rethrown.

**C:** rebol3-source/src/core/l-scan.c:1908-1926 (TOKEN_CONSTRUCT), 1970-1981 (missing_error)

## [high] lexer-test.r3 ===Special cases with < char=== "special case with @ char as well" - asserts #41
**Cause:** transcode {1.1.1<@foo} must give error id 'invalid with arg1 "tag". JEBOL splits the tuple correctly (classify rewinds at the angle bracket), then readAngled sees '<@foo': the symbol run is just '<', '@' is not a delimiter, and the scan for a closing '>' runs off the end - at which point line 1020-1021 falls back to classify(readLexeme()), which throws INVALID_LEXEME with no tokenKind, so arg1 is none. The C's Skip_Tag returns NULL when no '>' arrives and Scan_Token answers -TOKEN_TAG, i.e. RE_INVALID with arg1 "tag".

**Where:** Transcoder.java readAngled, the no-closing-bracket branch at lines 1020-1022

**Fix:** Replace `if (scout >= codepoints.length) { return classify(readLexeme()); }` (the second occurrence, after the '>' search) with `throw failureReading(SyntaxFailure.INVALID_LEXEME, "tag", readLexeme())`. The earlier branch at line 1013-1015 (symbol run ending at a delimiter) must stay as classify, since that is how <, <=, <--> read as words.

**C:** rebol3-source/src/core/l-scan.c:1249-1254 (Skip_Tag failure -> -TOKEN_TAG)

## [high] lexer-test.r3 ===Invalid construction=== "Invalid char" - asserts #62
**Cause:** #"^(D834)" must be an error (id 'invalid): 0xD834 is a UTF-16 surrogate and no character may hold one. The C checks after reading the char: `if (type > MAX_UNI || IS_SURROGATE(type)) return -TOKEN_CHAR;` with MAX_UNI 0x10FFFF and IS_SURROGATE(c) meaning 0xD800-0xDFFF. JEBOL's readCharacter takes whatever readParenthesisedEscape parsed and builds CharacterValue.of(0xD834) with no range check, so the read succeeds and error? is false.

**Where:** Transcoder.java readCharacter, lines 849-862, after the closing-quote check

**Fix:** After consuming the closing quote in readCharacter, refuse the codepoint when `codepoint > 0x10FFFF || (codepoint >= 0xD800 && codepoint <= 0xDFFF)` by throwing failureReading(SyntaxFailure.INVALID_LEXEME, "char"). Name the constants (e.g. LAST_UNICODE_CODEPOINT, isSurrogate).

**C:** rebol3-source/src/core/l-scan.c:1305-1311; rebol3-source/src/include/reb-c.h:221 (MAX_UNI); rebol3-source/src/include/reb-defs.h:114 (IS_SURROGATE)

## [high] lexer-test.r3 ===Literal none=== "/_" - asserts #110, #111
**Cause:** transcode/error "/_" must give [/ _ <remainder>]: the word / then the none literal _. The C's slash arm has an explicit special case - after one slash, `if (*cp == '_' && IS_LEX_DELIMIT(cp[1])) return TOKEN_WORD;` (l-scan.c:1069-1072) - emitting the word / alone and rescanning _ as TOKEN_NONE (underscore arm, l-scan.c:1339-1340: `_` followed by a delimiter is none; and / itself IS a delimiter, which is what makes "/_/_" scan as / _ / _). JEBOL's readLexeme swallows "/_" (and "/_/_") as one lexeme and classifyPlain answers a refinement named _, so first is a refinement, not a word. Assert #111's expected length 5 is the four values plus the input remainder the transcode native appends whenever /next, /only or /error is used (l-scan.c:2196 Append_Val(blk, src)) - JEBOL's withWhatWasLeftUnread already does that, so only the lexing needs fixing.

**Where:** Transcoder.java classify(), add two early rules at the top (before line 1437); classifyPlain stays unchanged

**Fix:** In classify, before anything else: (1) if lexeme starts with "/_" and (length == 2 or charAt(2) == '/'), rewind position and column by lexeme.length()-1 and return WordValue.of("/"); (2) if lexeme starts with "_/" , rewind by lexeme.length()-1 and return NoneValue.none(). Then "/_" reads as [/ _], "/_/_" as [/ _ / _] (each re-read peels one token), while "/__" (charAt(2)=='_') still falls through to refinement __ (assert #112) and "/a_" stays a refinement (assert #113).

**C:** rebol3-source/src/core/l-scan.c:1060-1092 (slash arm, /_ special case at 1069), 1339-1343 (LEX_SPECIAL_UNDERSCORE -> TOKEN_NONE), 2196 (Append_Val(blk, src) - the fifth value)

## [high] lexer-test.r3 ===Money=== "space requirement" - asserts #239
**Cause:** load {$1/$2} must give error id 'invalid with arg2 = "$1/". In the C the money token ends at the slash (slash is a delimiter), and TOKEN_MONEY refuses any money followed by '/': `if (*ep == '/') {ep++; goto syntax_error;}` (l-scan.c:1830-1832) - ep is bumped past the slash first, so the reported token text (arg2) is "$1/". In JEBOL a slash does not end a lexeme, so readMoney's readLexeme returns "$1/$2" whole; BigDecimal("1/$2") fails and failureReading reports arg2 = "$1/$2", not "$1/". (The * + - spellings pass because there the whole lexeme IS the C's token.)

**Where:** Transcoder.java readMoney, lines 1217-1221

**Fix:** In readMoney, before parsing: if the lexeme contains '/', throw failureReading(SyntaxFailure.INVALID_LEXEME, "money", lexeme.substring(0, lexeme.indexOf('/') + 1)) - the token up to and including the first slash. Per the C this fires even when the amount before the slash is a valid number ($1/2 is also an error). Apply the same cut in the signed path (moneyOf called from readSignedOrLexeme), remembering its token gets the -$/+$ prefix re-attached.

**C:** rebol3-source/src/core/l-scan.c:1830-1833 (TOKEN_MONEY slash refusal)

## [high] lexer-test.r3 ===Get-word=== "invalid `get-word!`" - asserts #251
**Cause:** load {:2nd} must raise. The C never reads ':' + digit as a get-word: `case LEX_SPECIAL_COLON: if (IS_LEX_NUMBER(cp[1])) return TOKEN_TIME;` (l-scan.c:1155-1156), and Scan_Time on ":2nd" stops at the 'n' so `ep != Scan_Time(...)` -> syntax_error -> RE_INVALID arg1 "time". (":12" alone is genuinely the time 0:12 - Scan_Time tolerates the empty hours part, t-time.c:100-104.) JEBOL's classifyPlain get-word arm (startsWith ":") happily answers the get-word 2nd, and refuseAMisplacedSigil only refuses a digit after the tick sigil, not after the colon.

**Where:** Transcoder.java classifyPlain, the get-word arm at lines 1652-1655 (or refuseAMisplacedSigil at 1750-1755)

**Fix:** In the get-word arm: when the character after ':' is a digit, treat the lexeme as a time, not a word - try TIME.matcher("0" + lexeme) (the leading 0 stands for the empty hours the C's Grab_Int returns); if it matches, answer readTime on those groups (":12" -> 0:12), otherwise throw failureReading(SyntaxFailure.INVALID_LEXEME, "time", lexeme). The suite only checks error? here, but the arg1 "time" matches what the C reports.

**C:** rebol3-source/src/core/l-scan.c:1155-1156; rebol3-source/src/core/t-time.c:76-140 (Scan_Time); l-scan.c:1841-1846 (TOKEN_TIME ep mismatch -> syntax_error)

## [medium] lexer-test.r3 ===Construction syntax=== "any-string!" (the struct line) - asserts #326
**Cause:** load {#(struct! [a [uint8!]])} must answer a value struct? says yes to. The C dispatches construction through Make_Dispatch[type] (Construct_Value, l-types.c:1031-1034), and struct! has MT_Struct in t-struct.c building a REBSTU from the field-spec block (field word + type block naming uint8!/int8!/.../float64!/pointer/word!/rebval!, t-struct.c type_to_sym table). JEBOL has Datatype.STRUCT in the enum but no struct value class at all, and builtFrom's switch has no STRUCT arm, so the construct falls to default -> MALCONSTRUCT.

**Where:** Transcoder.java builtFrom switch at lines 779-804 (add case STRUCT), plus a new domain/value/StructValue.java

**Fix:** Introduce a minimal StructValue in domain/value (datatype() == Datatype.STRUCT) built from the spec block: validate the block as pairs of word + one-element block naming a known scalar type (uint8! int8! uint16! int16! uint32! int32! uint64! int64! float32! float64! pointer word! rebval!), zero-initialise each field; refuse anything else as MALCONSTRUCT. Add `case STRUCT -> only instanceof BlockValue spec ? StructValue.from(spec) : requireDatatype(only, Datatype.STRUCT)` in builtFrom. Confirm struct? exists (Datatype.STRUCT is already in the enum, so the generated predicate should answer by datatype); wire `make struct!` in Natives the same way when the suite reaches it. Scope note: full struct semantics (get/set/mold) are a larger port; this assert needs only construction + the predicate.

**C:** rebol3-source/src/core/l-types.c:963-1036 (Construct_Value/Make_Dispatch); rebol3-source/src/core/t-struct.c (MT_Struct, type_to_sym)

## [medium] lexer-test.r3 ===Construction syntax=== "function!" - asserts #339
**Cause:** transcode/one {#(function! [[a [series!]][print a]])} must answer a function. The C: Construct_Value dispatches to MT_Function -> Make_Function(REB_FUNCTION, out, def) where def is the single block holding [spec body]; it demands exactly two items, both blocks (c-function.c:194-200). JEBOL's builtFrom has no FUNCTION arm, so the construct is MALCONSTRUCT.

**Where:** Transcoder.java builtFrom switch at lines 779-804 (add case FUNCTION); FunctionSpec.java (domain/eval, package-private) holds the spec-parsing this needs

**Fix:** Add `case FUNCTION` accepting exactly one BlockValue whose remaining() is exactly two BlockValues [spec, body]; anything else -> MALCONSTRUCT. Build `new FunctionValue(spec, body, parameters, localNames, Context.root())`. The parameter/local parsing currently lives in package-private FunctionSpec.parametersIn (domain/eval) which domain/read cannot reach: either move that parsing into a factory on FunctionValue (domain/value) and have FunctionSpec delegate, or widen FunctionSpec to public and import it in Transcoder - the factory move is the cleaner dependency direction. Wrap any spec-parse Raised in MALCONSTRUCT so a bad spec inside a construct stays a syntax error, as the C's FALSE return does.

**C:** rebol3-source/src/core/t-function.c:54-60 (MT_Function); rebol3-source/src/core/c-function.c:182-210 (Make_Function: block of exactly [spec body])

## [high] lexer-test.r3 ===Construction syntax=== "bitset!" - asserts #401
**Cause:** #(bitset! not #{FF}) must build a complemented bitset. MT_Bitset (t-bitset.c:92-110) takes an optional leading word which must be NOT, then a binary, and sets the not-flag. JEBOL's builtFrom BITSET arm only handles a single binary; with contents [not, #{FF}] the size-2 branch does not match (second item is not an integer) and `contents.size() != 1` throws MALCONSTRUCT.

**Where:** Transcoder.java builtFrom, BITSET arm at lines 785-787 (restructure into its own branch before the generic checks)

**Fix:** Handle BITSET before the generic two-content position branch: one BinaryValue -> BitsetValue.of(bytesOf(octets)); a WordValue with canonical "not" followed by one BinaryValue -> BitsetValue.of(bytesOf(octets)).complemented(); anything else (extra items, wrong first word, a block) -> MALCONSTRUCT. BitsetValue.complemented() already exists (BitsetValue.java:58).

**C:** rebol3-source/src/core/t-bitset.c:92-110 (MT_Bitset: optional SYM_NOT, then binary, then must be END)

## [high] lexer-test.r3 ===Construction syntax=== "bitset!" - asserts #403
**Cause:** #(bitset! #{FF} 1) must be an error - MT_Bitset returns FALSE unless the binary is the last item (`return IS_END(++data);`). JEBOL's builtFrom hits the generic branch for [#{FF}, 1] (size 2, second an IntegerValue), builds the whole bitset, and because BitsetValue is not a SeriesValue the code does `return whole;` - silently discarding the trailing 1 and succeeding where the C refuses.

**Where:** Transcoder.java builtFrom, lines 766-774 (the two-content position branch, specifically `if (!(whole instanceof SeriesValue series)) { return whole; }`)

**Fix:** The trailing-integer position form belongs only to series datatypes. Change `return whole` to `throw failure(SyntaxFailure.MALCONSTRUCT, null)` when the built value is not a SeriesValue (or equivalently, only enter the size-2 branch when the datatype is a series type). Covered automatically if the BITSET arm from the previous finding runs before the generic branch, but the non-series `return whole` should still become a throw so no other non-series datatype quietly swallows a position.

**C:** rebol3-source/src/core/t-bitset.c:107-109 (`return IS_END(++data);`)

## [high] lexer-test.r3 ===BINARY=== "binary! with comments inside" - asserts #417
**Cause:** transcode/one/error "#{00;XXX^M02}" must give #{0002}: a carriage return ends a comment just as a line feed does - the C's comment skip runs `while (NOT_NEWLINE(*cp))` and NOT_NEWLINE stops at CR and LF both (sys-scan.h:246). JEBOL's readBinary comment loop stops only at '\n', so with a lone CR it eats the CR, the 02 and the closing brace, runs off the end, and throws missing-close instead of answering the binary.

**Where:** Transcoder.java readBinary, the ';' branch at lines 901-905; same defect in skipIgnorable at lines 506-510

**Fix:** In both comment-skipping loops change the stop condition to `peek() != '\n' && peek() != '\r'` (extract a named predicate, e.g. endsAComment). The C's top-level comment skip (l-scan.c:512) uses the same NOT_NEWLINE, so skipIgnorable gets the same fix even though only the binary case is asserted here.

**C:** rebol3-source/src/include/sys-scan.h:246 (NOT_NEWLINE stops at CR and LF); rebol3-source/src/core/l-scan.c:512

## [high] evaluation-test.r3 / compose (test "compose map") - asserts #165 #166 #167 #168 #169 #170 #171 (and stage one of #173)
**Cause:** Every binding walk in JEBOL stops at MapValue. Binder.bindValue, Binder.bindValueOnly and Binder.defineWordsIn recurse into BlockValue only, so a paren inside a map literal (#[a: (zero) ...]) never gets its words bound - not at script load (bindAndDefine), not at function construction/call (bindOnly, needed for #168 arg b and #171 val), not by USE/closure-with (bind, #169 #170). COMPOSE then evaluates the unbound paren and raises not-defined even though zero, red, now, white are all defined (zero is base-constants.reb:27). The C descends into maps in all three walks: Bind_Block_Words tests ANY_BLOCK_OR_MAP, and so does Bind_Relative_Words. Assert #172 passes today precisely because its paren holds only a literal block, no words - confirming binding is the failure, not the compose map arm.

**Where:** src/main/java/org/jebol/domain/eval/Binder.java - bindValue (~line 103), bindValueOnly (~line 87), defineWordsIn (~line 134)

**Fix:** Add a MapValue arm to all three switches. In bindValue: `case MapValue map -> boundMap(map, context)` where boundMap rebuilds/updates the entries with each stored value passed through bindValue (map.put(key, bindValue(value, context)) for each entry - keys stay untouched); same shape in bindValueOnly using bindValueOnly recursion; defineWordsIn walks map.values() (and nested blocks in them). This binds parens, words and nested blocks held as map values, mirroring the C's deep bind reaching map series.

**C:** rebol3-source/src/core/c-frame.c:846 (Bind_Block_Words, ANY_BLOCK_OR_MAP under BIND_DEEP) and c-frame.c:966 (Bind_Relative_Words)

## [high] evaluation-test.r3 / compose (test "compose map") - asserts #166 #167 (the m2/d/k == white legs; #165's other legs are covered by the Binder finding)
**Cause:** COMPOSE/DEEP does not recurse into a map nested inside a map. The map arm of compose (Natives.java ~7096) flattens the top map to pairs and runs composed() over them, but composed()'s deep branch only recurses when the item is a BlockValue with datatype BLOCK - a nested MapValue (d: #[k: (white)]) is pushed through unchanged, so m2/d/k stays a paren. The C recurses into both: `if (IS_BLOCK(value) || IS_MAP(value)) Compose_Block(value, TRUE, only, 0)`. Two lesser divergences in the same arm worth fixing while there: JEBOL forces only=true for the whole map compose (the C only suppresses splicing at the map level itself, `!only && !IS_MAP(block)`, and passes the caller's /only down into nested blocks), and JEBOL runs map KEYS through composed() where the C pushes keys raw (`DS_PUSH(value++)` skips the paren check for the key).

**Where:** src/main/java/org/jebol/domain/eval/Natives.java - the MapValue arm of compose (~7096-7107) and composed() (~7250-7289)

**Fix:** Extract composedMap(MapValue, evaluator, context, only, deep): walk pairs; keys added verbatim; a paren value is evaluated with splicing always suppressed; any other value when deep is a BLOCK -> composed(nested, evaluator, context, only, true), a MapValue -> composedMap(nested, ..., only, true), else as written. The compose native's map arm calls composedMap with the caller's /only and /deep; composed()'s deep branch also gains `item instanceof MapValue nested -> built.add(composedMap(nested, ...))` so a map inside a composed block is reached too.

**C:** rebol3-source/src/core/c-do.c:1387-1427 (Compose_Block: map keys pushed raw at 1388-1394, nested `IS_BLOCK(value) || IS_MAP(value)` recursion at 1413, splice guard `!only && !IS_MAP(block)` at 1403)

## [medium] evaluation-test.r3 / compose (test "compose map") - asserts #173
**Cause:** Deriving an object shares the parent's map (and block) values by reference and rebinds only functions. makeObject copies parent slots via rehomed(), which rebinds FunctionValue bodies and hands every other value through untouched; so after the Binder fix, *ob1: make *obp [one: 11] would still hold the very map whose paren (one + two) is bound to *obp's fields, and compose/deep obj/tmp would answer 3 for every child instead of [3 13 23 33]. The C deep-copies parent values on derivation - TS_CLONE explicitly includes REB_MAP - and then Rebind_Frame/Rebind_Block rebinds words referencing the parent frame to the child frame, descending ANY_BLOCK_OR_MAP.

**Where:** src/main/java/org/jebol/domain/eval/Natives.java - rehomed() (~3715) used by makeObject (~3670)

**Fix:** Extend rehomed (or the prototype-copy loop in makeObject) so a MapValue slot is deep-copied and its stored values rebound to the new fields context: copy the map, then for each entry put bindValue(value, fields) (blocks/parens get Binder.bind against fields; the child context defines the same field names so one and two resolve to the child's slots). Do the same for BlockValue slots to match TS_CLONE, though only the map is needed for this assert.

**C:** rebol3-source/src/core/t-object.c:406-407 (Make_Object + Rebind_Frame), c-frame.c:493-494 (Copy_Deep_Values with TS_CLONE), include/sys-core.h:172 (TS_CLONE includes TYPESET(REB_MAP)), c-frame.c:1050-1052 (Rebind_Block descends ANY_BLOCK_OR_MAP)

## [high] func-test.r3 / Apply (test "apply :do [:func]") - asserts #28 (executed ordinal; source line 105: `2 = try [apply :do [:add 1 1]]`)
**Cause:** The C's Apply_Block special-cases applying DO: after evaluating (or reading, under /only) the first value of the args block, if the function being applied is DO and that value is any-function, it resets and re-applies THAT function to the rest of the block (`goto reapply`). JEBOL's apply native has no such branch: it slices the reduced block to DO's arity and calls DO on :add alone, losing the 1 1, so the answer is not 2.

**Where:** src/main/java/org/jebol/domain/eval/Natives.java - the apply native (~3404-3421)

**Fix:** After computing `supplied`, loop: while arguments.get(0) (the callee) is the DO native (NativeValue with nativeName "do") and supplied is non-empty and supplied.getFirst() is a FunctionValue, NativeValue or OperatorValue, make that first value the callee and drop it from supplied; then proceed with the existing arity/pad/applyFunction logic against the new callee. Applies to both the reduced and the /only path, as in the C.

**C:** rebol3-source/src/core/c-do.c:1469-1477 (reduce path) and 1489-1496 (non-reduce path) in Apply_Block; IS_DO + ANY_FUNC reapply

## [high] func-test.r3 / Apply (test "apply with refinements") - asserts #30 (executed ordinal; source line 115: `[1 #(true) 3] = apply :f [1 2 3]` with f: func [a /b c])
**Cause:** APPLY fills the function's whole word frame positionally in the C - `len = SERIES_TAIL(words)-1` counts args, refinements and refinement args alike, and a truthy value landing on a refinement slot becomes TRUE while a falsey one becomes NONE and nones out that refinement's args. JEBOL's arityOf(FunctionValue) filters to `owningRefinement().isEmpty()` positional args only, so apply :f [1 2 3] slices the block to [1]; b and c then read none. The downstream conversion already exists and is correct - Evaluator.bindArgumentsPositionally turns a truthy value at a refinement slot into logic true - it is just never given the values.

**Where:** src/main/java/org/jebol/domain/eval/Natives.java - arityOf (~8641), FunctionValue arm

**Fix:** In arityOf, the FunctionValue arm answers function.parameters().size() - the parameters list holds one entry per frame slot (args, refinements, refinement args) which is exactly what bindArgumentsPositionally walks. Leave the NativeValue arm as it is: runNative's argument convention differs and the passing assert #33 (apply :add) depends on it.

**C:** rebol3-source/src/core/c-do.c:1461-1462 (len = whole words frame) and 1508-1531 (refinement slot conversion) in Apply_Block

## [high] func-test.r3 / Apply (test "apply op!") - asserts #34 (executed ordinal; source line 126: `3 = apply :+ [1 2]`; #33 apply :add passes)
**Cause:** arityOf has no OperatorValue arm, so an op! falls to the default of 0; apply slices [1 2] down to nothing and invokes + with no arguments, which raises instead of answering 3. Evaluator.applyFunction already dispatches OperatorValue through invokeUnderlying, so only the arity is missing. In the C an op! carries its wrapped function's words frame (Make_Function REB_OP copies VAL_FUNC_ARGS from the definition), so Apply_Block sees arity 2.

**Where:** src/main/java/org/jebol/domain/eval/Natives.java - arityOf (~8641)

**Fix:** Add `case OperatorValue operator -> arityOf(operator.underlying())` to arityOf. The underlying of + is the ADD native with two positional parameters, giving 2; apply then hands [1, 2] to applyFunction, whose OperatorValue arm already runs the underlying native.

**C:** rebol3-source/src/core/c-function.c:207-227 (op! shares the definition's args frame) with c-do.c:1461-1462 (Apply_Block uses that frame's length)

## [high] error-test.r3 / BIND (test "bind error!") - asserts #10 (line 31: `["cannot open:" %nonsense "reason:" 3] = reduce bind system/catalog/errors/(e/type)/(e/id) e`)
**Cause:** Two defects, the first fatal: (1) BIND refuses an error! as the context. The bind native resolves its target through fieldsOf, which handles ObjectValue, ModuleValue and PortValue and answers null for ErrorValue, so bind raises expect-arg ("bind wanted an object or a bound word"). The C explicitly accepts it: `else if (IS_ERROR(arg)) frame = VAL_ERR_OBJECT(arg);`. (2) Even bound, the reduce would give ["cannot open:" %nonsense "reason:" #(none)]: JEBOL's failed file read raises cannot-open with only the path as arg1 (throughPort builds ErrorValue.about with the subject alone), while the C carries the reason code in arg2 - dev-file.c sets file->error = -RFE_OPEN_FAIL on a failed open, Trap_Port pushes -err_code, and RFE_OPEN_FAIL is 3 (reb-file.h enum: BAD_PATH=1, NO_MODES, OPEN_FAIL) - exactly the 3 the suite hardcodes.

**Where:** src/main/java/org/jebol/domain/eval/Natives.java - the bind native (~3608-3662) / fieldsOf (~14914); and throughPort (~15762) with FileSystemPort.readBytes (src/main/java/org/jebol/application/FileSystemPort.java:215)

**Fix:** (1) In the bind native's target resolution (or fieldsOf), add an ErrorValue arm that builds a Context holding one slot per name in ErrorValue.FIELDS, each set from error.field(name); binding the catalogue block's :arg1/:arg2 get-words to it lets REDUCE read them. (2) Give FilePort.Denied an optional reason code; FileSystemPort.readBytes throws Denied("cannot-open", ..., path, 3) for a failed open, and throughPort builds ErrorValue.about(ACCESS, id, message, file, IntegerValue.of(reason)) so arg2 is the integer 3, matching Trap_Port's DS_PUSH_INTEGER(-err_code) with -(-RFE_OPEN_FAIL).

**C:** rebol3-source/src/core/n-data.c:322-324 (REBNATIVE(bind) accepts IS_ERROR), c-error.c:684-698 (Trap_Port pushes -err_code as arg2), os/posix/dev-file.c:481 (file->error = -RFE_OPEN_FAIL), include/reb-file.h (RFE_OPEN_FAIL = 3), p-file.c:208 (read open failure path)

## [high] evaluation-test.r3 / BOOT (test "issue-232") - asserts #248 (line 742: `file? system/options/boot`; #247 home and #249 script already pass)
**Cause:** JEBOL deliberately leaves system/options/boot as NONE - the comment at Natives.java:536-541 says an embedded interpreter has no exe path and that mezz-tail.reb guards on none before CLEAN-PATH. The C never leaves it none: Init_Main_Args unconditionally runs Set_Option_File(OPTIONS_BOOT, rargs->exe_path, FALSE), so on a real 3.22.1 boot is always a file! and the suite asserts exactly that. Note the diagnosis brief's #N here counts only top-level --assert words, the same ordinal SuiteFile assigns (nested foreach asserts are setup, not assertions), which is how #248 lands on this line.

**Where:** src/main/java/org/jebol/domain/eval/Natives.java - the system/options construction (~504-545), where "boot" is currently left at NoneValue by the field-initialisation loop

**Fix:** Set options/boot to a file! value naming the running executable: ProcessHandle.current().info().command() when present, else System.getProperty("java.home") + "/bin/java", normalised to an absolute forward-slash path via StringValue.of(..., Datatype.FILE) - an already-clean absolute path keeps mezz-tail.reb's `system/options/boot: clean-path system/options/boot` harmless, answering the concern in the existing comment.

**C:** rebol3-source/src/core/b-init.c:919 (Set_Option_File(OPTIONS_BOOT, rargs->exe_path, FALSE) in Init_Main_Args)

## [high] load-test.r3 / Load/header (Script checksum verification) and Load issues/wishes - asserts #13, #14 (with the mold finding), #26 (with the make-block finding)
**Cause:** CHECKSUM/PART refuses a series position as the limit. sys-load.reb line 326 runs `checksum/part mark 'sha1 remaining` where `remaining` is a position in the same binary; JEBOL's checksum native calls partOfOctets -> the howManyWanted overload that passes NoneValue.none() as the source, so the SeriesValue branch (Natives.java ~13379) always throws INVALID_PART. Verified live: `try [load s]` after `save/header none [...] [checksum: true]` answers #[error! invalid-part], so #13 gets an error instead of the block and #14 gets id 'invalid-part instead of 'bad-checksum. #26 dies the same way because import -> load-module -> load-header runs the same line (the suite hash E9A16FDE... is sha1 of the 5-byte body " a: 1", confirmed with shasum; JEBOL's save side already writes the right hash, 4DC8C9EE... = sha1 of the 21-byte body).

**Where:** src/main/java/org/jebol/domain/eval/Natives.java: define("checksum") ~line 10518-10543 (its partOfOctets call ~10528), partOfOctets ~10977, howManyWanted overloads ~13330-13388

**Fix:** Give partOfOctets an overload that takes the source Value and calls the source-aware howManyWanted(source, arguments, refinements, where); in checksum (and in compress/decompress, which share the helper and are hit by sys-load's `decompress/part mark 'zlib remaining` on compressed scripts) pass arguments.getFirst() as that source. The series branch then answers upTo.index() - from.index() as the count, exactly as it already does for COPY. Bytes taken remain from the value's current position, clamped at 0..lengthFromHere.

**C:** rebol3-source/src/core/n-strings.c:258 `len = Partial1(data, D_ARG(ARG_CHECKSUM_LENGTH))`; rebol3-source/src/core/f-stubs.c:702 Partial1 (series form: len = VAL_INDEX(lval) - VAL_INDEX(sval))

## [high] load-test.r3 / SAVE (save/header) and Load/header (Script checksum verification) - asserts #51, and #14 needs this too
**Cause:** Blocks always mold flat: Molder never reads the line-break markers (BlockStorage.breaksLineAt is only referenced by the new-line? native), and reflect 'body on an object builds a fresh block without setting them. The C's BODY-OF (Make_Object_Block mode 3) sets VAL_SET_LINE on every set-word, and Mold_Block_Series renders each flagged position as a new line indented 4 spaces per level with the closing bracket on its own line. So real save/header emits `REBOL [\n    title: "my code"\n]\n...` (the exact bytes assert #51 pins) while JEBOL emits `REBOL [title: "my code"]\n...`. Verified live: `mold body-of construct [title: "my code"]` answers `[title: "my code"]`. This also breaks #14: JEBOL's checksum script is 83 bytes (verified: `length? s` = 83) instead of the real 89, so `clear at s 88` clears nothing, the script is never corrupted, and no bad-checksum can arise even once CHECKSUM/PART works.

**Where:** src/main/java/org/jebol/domain/value/Molder.java block rendering (no breaksLineAt call anywhere); src/main/java/org/jebol/domain/eval/Natives.java reflect define, object 'body arm ~line 5267-5274

**Fix:** Two parts. (1) In reflect's object 'body arm, build the BlockStorage and set the line-break flag on each set-word position (positions 1,3,5... one flag per field). (2) In Molder, mold block/paren contents per Mold_Block_Series: keep an indent depth in the mold state; when an item's position carries the flag, on the first such item increment indent, and emit a newline plus 4 spaces per indent level instead of the separating space (converting a just-written trailing space into the newline, as New_Indented_Line does); after the last item, if any line was emitted (or the tail position carries the flag) decrement indent and emit a newline plus indent before the closing bracket. new-line/new-line? already maintain the flags so `new-line/all` blocks start molding correctly too.

**C:** rebol3-source/src/core/c-frame.c:524 Make_Object_Block (VAL_SET_LINE on each set-word, mode 3); rebol3-source/src/core/s-mold.c:742 Mold_Block_Series and s-mold.c:242 New_Indented_Line (4 spaces per indent level)

## [high] load-test.r3 / Load issues/wishes (Length-specified script embedding) - asserts #25, #26 (with the checksum/part finding)
**Cause:** MAKE BLOCK! of a binary (or string) wraps the value as a single item instead of tokenizing it as source. load-module (sys-load.reb line 1458-1460) does `module-code: make block! module-code` on the length-truncated body bytes; JEBOL's makeOfDatatype has no string/binary tokenizing arm and falls through to convertedTo (TO semantics), so make-module* receives body = [#{...}], bind/only/set finds no top-level set-words, and the module holds only lib-local. Verified live: `make block! #{20613A203120623A2032}` answers [#{20613A203120623A2032}], and `words-of import {rebol [length: 5] a: 1 ...}` answers [lib-local]. The rest of the chain is sound: load-header's length handling, copy/part with a series limit, and make module! with a proper block body all verified working ([lib-local a b] came back from a direct make module!).

**Where:** src/main/java/org/jebol/domain/eval/Natives.java makeOfDatatype ~line 12389-12409

**Fix:** In makeOfDatatype, before the convertedTo fallthrough: when wanted is BLOCK or PAREN and from is a BinaryValue or StringValue, transcode the text (UTF-8 decode for a binary, from the current position) with Transcoder and answer the resulting values as a block of the wanted datatype; raise the transcoder's syntax error if it fails. MAKE only - TO BLOCK! must keep wrapping (the C's `else if (ANY_BLOCK_TYPE(type)) goto to_blk_val` handles TO before the tokenizing arm is reached).

**C:** rebol3-source/src/core/t-block.c:417-420 ("make from string! or binary! with tokenization" -> Scan_Source); rebol3-source/src/core/l-scan.c:2062 Scan_Source

## [high] load-test.r3 / Load issues/wishes (Length-specified script embedding) - asserts #23, #24
**Cause:** JEBOL's DO native handles a string by calling evaluator.evaluateSource and has no binary case at all. (a) #23: a top-level RETURN in a do'd string escapes as a ReturnSignal, passes through TRY (which only catches Raised), and becomes the 'return outside a function' error at the outer walk - verified live. In real R3, DO of a string runs sys/do*, a plain FUNC, so RETURN unwinds to it and DO answers 3; the string branch loads with load/all so the embedded `rebol [length: 2]` header is never evaluated (evaluateSource already matches that part). (b) #24: BinaryValue is not a StringValue subclass, so a binary falls to the switch default and DO answers the binary itself unevaluated - verified live. Real R3 routes a binary through do* -> load/header, which finds the header, honours length: 2 (body truncated to " 1"), and evaluates to 1. Routing straight through sys/do* is blocked today by two more gaps, verified live: do*'s `do-needs` word is unbound (sys forward-reference to sys-load.reb not resolved) and `system/script` does not exist.

**Where:** src/main/java/org/jebol/domain/eval/Natives.java define("do") ~line 2498-2562 (StringValue case ~2524, missing BinaryValue case before the default)

**Fix:** (a) Wrap the StringValue case's evaluateSource call in a catch of ReturnSignal and answer returned.value(). (b) Add a BinaryValue case that runs the script pipeline: apply sys/load-header (proven working via applyFunction) to the binary; take [header mark remaining line]; body = copy/part mark remaining semantics (bytes from mark up to remaining, i.e. the integer `length` header field already applied by load-header); transcode those bytes, bind into the user context as evaluateSource does, evaluate, catching ReturnSignal the same way, and answer the last value. Note in docs that full parity is the C's route through sys/do* (script/args bookkeeping, do-needs), which additionally needs sys forward-binding (do-needs resolvable from sys-base.reb) and a system/script object - a separate piece of work.

**C:** rebol3-source/src/core/n-control.c:685-690 (REB_BINARY/REB_STRING/REB_URL/REB_FILE -> Do_Sys_Func(SYS_CTX_DO_P,...)); rebol3-source/src/mezz/sys-base.reb do* (string -> load/all, binary -> load/header + length truncation)

## [high] load-test.r3 / find-script native - asserts #33
**Cause:** JEBOL's find-script helper bracketFollows demands at least one whitespace character between the word REBOL and the open bracket (`return forward > at && ...`), so `{ Rebol[] print now}` answers none - verified live. The C's Scan_Head matches the five bytes of REBOL, then loops: `while (IS_LEX_SPACE(*cp)) cp++;` accepts zero spaces before the '[' case, so `Rebol[]` is a valid header and find-script answers the position of the R (index 2 here).

**Where:** src/main/java/org/jebol/domain/eval/Natives.java bracketFollows ~line 5830-5836 (used by headerStartsIn ~5803, behind the find-script define ~11753)

**Fix:** In bracketFollows, drop the `forward > at` conjunct: `return forward < text.length() && text.charAt(forward) == '[';`. Existing behaviour is otherwise right (`rebol 1` still answers none because '1' is not '['; `rebol  [] 1` and newline-separated forms still match).

**C:** rebol3-source/src/core/l-scan.c:1551 Scan_Head (after Match_Bytes for REBOL, zero-or-more IS_LEX_SPACE then the '[' case returns the header); n-strings.c:916 REBNATIVE(find_script)
