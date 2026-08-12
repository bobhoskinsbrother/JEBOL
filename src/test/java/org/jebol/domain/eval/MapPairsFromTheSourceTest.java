package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Reading a map's pairs out, and walking over them.
 *
 * <p>Read out of {@code Map_To_Block} and {@code Find_Entry} in {@code t-map.c}
 * and {@code Loop_Each} in {@code n-loop.c}, and every case checked against the
 * R3 binary before any of it was written.
 *
 * <p>The one thing to understand before reading the rest: a word key is not
 * stored as a word. Every kind of word becomes a set-word going in, so `a`,
 * `'a`, `:a` and `/a` are one key rather than four, and that is why a map molds
 * as {@code #[a: 1]} while a map keyed by a string molds as
 * {@code #["k" 1]} -- the colon belongs to the key, not to the map.
 *
 * <p>Coming back out, only two questions turn it back: KEYS-OF, and the walk.
 * BODY-OF and {@code to block!} show what is stored.
 *
 * <p>Specified in {@code spec/natives.allium} under "The pairs of a map, read
 * out and walked over".
 */
class MapPairsFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("a word key is held as a set-word, and every spelling is one key")
    class HowAKeyIsHeld {

        @Test
        @DisplayName("a plain word goes in and a set-word is stored")
        void aWordIsStoredAsASetWord() {
            // `if (ANY_WORD(key) && VAL_TYPE(key) != REB_SET_WORD) ...
            // VAL_SET(set, REB_SET_WORD);`
            assertThat(answerTo("set-word? first body-of make map! [a 1]"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the four kinds of word are one key, not four")
        void everyWordSpellingIsTheSameKey() {
            // Which is the point of normalising rather than a side effect of
            // it: a map read from source and a map written through a path have
            // to agree, and the path may carry any kind of word.
            assertThat(answerTo(
                    "m: make map! [] m/(to lit-word! 'a): 1 select m 'a"))
                    .isEqualTo("1");
            assertThat(answerTo(
                    "m: make map! [] m/(to get-word! 'a): 1 select m 'a"))
                    .isEqualTo("1");
            assertThat(answerTo(
                    "m: make map! [] m/(to refinement! 'a): 1 select m 'a"))
                    .isEqualTo("1");
            assertThat(answerTo(
                    "m: make map! [] m/a: 1 m/(to lit-word! 'a): 2 "
                    + "reduce [length? m select m 'a]")).isEqualTo("[1 2]");
        }

        @Test
        @DisplayName("a key of another type is held exactly as written")
        void anyOtherKeyIsUntouched() {
            assertThat(answerTo("integer? first body-of make map! [1 2]"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("string? first body-of make map! [\"k\" 2]"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("so the colon a map molds with belongs to the key")
        void theColonBelongsToTheKey() {
            // A word key molds with one and every other key molds without,
            // which is only explicable once the key is known to be a set-word.
            assertThat(answerTo("find mold make map! [a 1] \"a: 1\""))
                    .isNotEqualTo("#(none)");
            assertThat(answerTo("none? find mold make map! [1 2] \"1: 2\""))
                    .isEqualTo(TRUE);
            assertThat(answerTo("none? find mold make map! [{k} 2] {\"k\": 2}"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and what a map molds to reads back as an equal map")
        void aMoldedMapReadsBack() {
            // The round trip is what the storage has to satisfy, and it is the
            // reason the keys carry their colons rather than the molder adding
            // one: a molder that added a colon to an integer key would write
            // `1: 2`, which does not read back as a map at all.
            assertThat(answerTo("m: make map! [a 1 \"k\" 2 3 4] m = load mold m"))
                    .isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("MAKE MAP! given a number makes room, not pairs")
    class MakingRoom {

        @Test
        @DisplayName("a number answers an empty map")
        void aNumberAnswersAnEmptyMap() {
            // `else if (IS_NUMBER(arg)) { ... n = Int32s(arg, 0); }` and then
            // `Make_Map(n)`. Rebol's own MIME type table opens with
            // `make map! 111` and stopped the whole file here.
            assertThat(answerTo("m: make map! 111 reduce [map? m length? m]"))
                    .isEqualTo("[#(true) 0]");
            assertThat(answerTo("m: make map! 0 reduce [map? m length? m]"))
                    .isEqualTo("[#(true) 0]");
        }

        @Test
        @DisplayName("and a decimal reads the same way, being a number too")
        void aDecimalIsANumberToo() {
            assertThat(answerTo("m: make map! 10.5 reduce [map? m length? m]"))
                    .isEqualTo("[#(true) 0]");
        }

        @Test
        @DisplayName("less than no room is refused rather than read as none")
        void aNegativeCountIsRefused() {
            assertThat(errorIdFrom("make map! -5")).isEqualTo("out-of-range");
        }

        @Test
        @DisplayName("TO MAP! refuses a number on the same line MAKE reads it")
        void toMapRefusesANumber() {
            // `if (action == A_TO) Trap_Arg(arg);` -- a conversion answers
            // something made of what it was given, and there are no pairs in
            // a number.
            assertThat(errorIdFrom("to map! 111")).isEqualTo("invalid-arg");
            assertThat(errorIdFrom("to map! 10.5")).isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("a block, a paren, a map and an object all make one")
        void theFourThingsThatHoldPairs() {
            assertThat(answerTo("length? make map! [a 1]")).isEqualTo("1");
            assertThat(answerTo("length? make map! quote (a 1)")).isEqualTo("1");
            assertThat(answerTo("length? make map! make map! [a 1]")).isEqualTo("1");
            assertThat(answerTo("length? make map! make object! [a: 1]")).isEqualTo("1");
        }

        @Test
        @DisplayName("and anything else is refused")
        void anythingElseIsRefused() {
            assertThat(errorIdFrom("make map! \"ab\"")).isEqualTo("bad-make-arg");
            assertThat(errorIdFrom("make map! none")).isEqualTo("bad-make-arg");
            // An odd block is a key with no value, which is a typo rather
            // than a map: `if (n & 1) return FALSE;` and the caller raises.
            assertThat(errorIdFrom("make map! [a]")).isEqualTo("invalid-arg");
        }
    }

    @Nested
    @DisplayName("reading the pairs out")
    class ReadingThemOut {

        @Test
        @DisplayName("KEYS-OF turns the set-words back into plain words")
        void keysOfAnswersPlainWords() {
            // `if (ANY_WORD(val)) VAL_SET(out - 1, REB_WORD);` and only for
            // this question: the flag Map_To_Block takes is -1 for the keys,
            // +1 for the values and 0 for the pairs, and only -1 normalises.
            assertThat(answerTo("keys-of make map! [a 1 b 2]")).isEqualTo("[a b]");
            assertThat(answerTo("word? first keys-of make map! [a 1]")).isEqualTo(TRUE);
            assertThat(answerTo("words-of make map! [a 1 b 2]")).isEqualTo("[a b]");
        }

        @Test
        @DisplayName("VALUES-OF answers the values in the same order")
        void valuesOfAnswersTheValues() {
            assertThat(answerTo("values-of make map! [a 1 b 2]")).isEqualTo("[1 2]");
        }

        @Test
        @DisplayName("BODY-OF answers the pairs, keys as stored")
        void bodyOfAnswersThePairs() {
            assertThat(answerTo("body-of make map! [a 1 b 2]")).isEqualTo("[a: 1 b: 2]");
        }

        @Test
        @DisplayName("and TO BLOCK! asks the same question")
        void toBlockAsksTheSameQuestion() {
            // Not "a block holding the map", which is what a conversion that
            // did not know about maps would produce.
            assertThat(answerTo("to block! make map! [a 1 b 2]"))
                    .isEqualTo("[a: 1 b: 2]");
            assertThat(answerTo("to block! make map! [1 2]")).isEqualTo("[1 2]");
            assertThat(answerTo("(to block! make map! [a 1]) = body-of make map! [a 1]"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("an empty map reads out as nothing, four ways")
        void theEmptyMapReadsOutEmpty() {
            assertThat(answerTo(
                    "m: make map! [] reduce [keys-of m values-of m body-of m to block! m]"))
                    .isEqualTo("[[] [] [] []]");
        }

        @Test
        @DisplayName("a removed key is in none of them")
        void aRemovedKeyIsGone() {
            // REMOVE/KEY hides the key rather than closing the gap, so every
            // reader has to know to skip it. Any one of them that did not would
            // report a pair that LENGTH? says is not there.
            assertThat(answerTo(
                    "m: make map! [a 1 b 2] remove/key m 'a "
                    + "reduce [length? m keys-of m values-of m body-of m]"))
                    .isEqualTo("[1 [b] [2] [b: 2]]");
        }

        @Test
        @DisplayName("a key holding none is in all of them, because it is still a pair")
        void aKeyHoldingNoneIsStillThere() {
            assertThat(answerTo(
                    "m: make map! [a 1] m/a: none "
                    + "reduce [length? m keys-of m body-of m]"))
                    .isEqualTo("[1 [a] [a: _]]");
        }
    }

    @Nested
    @DisplayName("walking a map")
    class WalkingIt {

        @Test
        @DisplayName("one word walks the keys, as plain words")
        void oneWordWalksTheKeys() {
            // `*vars = *BLK_SKIP(series, index & ~1)` reads the key from the
            // even slot, and `if (IS_SET_WORD(vars)) SET_TYPE(vars, REB_WORD);`
            // hands out the plain word. So what the walk gives back can be
            // compared against a word the caller wrote.
            assertThat(answerTo(
                    "c: copy [] foreach k make map! [a 1 b 2] [append c k] c"))
                    .isEqualTo("[a b]");
            assertThat(answerTo(
                    "c: copy [] foreach k make map! [a 1] [append c type? k] c"))
                    .isEqualTo("[#(word!)]");
        }

        @Test
        @DisplayName("and hands out any other kind of key as it is stored")
        void anotherKindOfKeyComesBackAsItIs() {
            assertThat(answerTo(
                    "c: copy [] foreach k make map! [a 1 \"k\" 2 3 4] "
                    + "[append c type? k] c"))
                    .isEqualTo("[#(word!) #(string!) #(integer!)]");
        }

        @Test
        @DisplayName("two words walk the keys and the values together")
        void twoWordsWalkThePairs() {
            assertThat(answerTo(
                    "c: copy [] foreach [k v] make map! [a 1 b 2] "
                    + "[append c reduce [k v]] c"))
                    .isEqualTo("[a 1 b 2]");
        }

        @Test
        @DisplayName("three words are refused, a pair having only two halves")
        void threeWordsAreRefused() {
            // `else Trap_Arg(words);` -- there is nothing for a third name to
            // be set to, and setting it to none would make a malformed loop
            // look like a working one.
            assertThat(errorIdFrom(
                    "foreach [k v x] make map! [a 1 b 2] [k]")).isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("a removed key is stepped over")
        void aRemovedKeyIsSteppedOver() {
            // `index += 2; goto skip_hidden;`
            assertThat(answerTo(
                    "m: make map! [a 1 b 2] remove/key m 'a "
                    + "c: copy [] foreach [k v] m [append c reduce [k v]] c"))
                    .isEqualTo("[b 2]");
        }

        @Test
        @DisplayName("and a key holding none is walked, because it is still a pair")
        void aKeyHoldingNoneIsWalked() {
            assertThat(answerTo(
                    "m: make map! [a 1 b 2] m/a: none "
                    + "c: copy [] foreach [k v] m [append c reduce [k v]] c"))
                    .isEqualTo("[a _ b 2]");
        }

        @Test
        @DisplayName("an empty map runs the body no times and answers none")
        void theEmptyMapRunsNothing() {
            assertThat(answerTo(
                    "c: copy [] foreach [k v] make map! [] [append c k] "
                    + "reduce [empty? c]")).isEqualTo("[#(true)]");
            assertThat(answerTo("none? foreach [k v] make map! [] [1]"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("the walk answers the body's last value, as any loop does")
        void theWalkAnswersTheLastValue() {
            assertThat(answerTo("foreach [k v] make map! [a 1] [4]")).isEqualTo("4");
        }

        @Test
        @DisplayName("BREAK stops it partway")
        void breakStopsIt() {
            assertThat(answerTo(
                    "c: copy [] foreach [k v] make map! [a 1 b 2] "
                    + "[append c k break] c")).isEqualTo("[a]");
        }

        @Test
        @DisplayName("a map never has a short last round, because a pair is never half there")
        void aMapNeverRunsShort() {
            // Worth pinning next to the block case below it: over a block the
            // last round can run with a name holding none, and over a map it
            // cannot, because the walk steps two slots at a time over a series
            // that always holds whole pairs.
            assertThat(answerTo(
                    "c: copy [] foreach [k v] make map! [a 1 b 2] "
                    + "[append c v] c")).isEqualTo("[1 2]");
        }

        @Test
        @DisplayName("over a block the last round runs short, filling the rest with none")
        void aBlockRunsAShortLastRound() {
            // `else SET_NONE(vars);` -- the walk is over the series and the
            // names that have nothing left to take hold none. The body runs one
            // more time than a caller counting pairs would expect, which is the
            // opposite of dropping the odd item and easy to get wrong the
            // convenient way.
            assertThat(answerTo(
                    "c: copy [] foreach [k v] [1 2 3] [append c reduce [k v]] c"))
                    .isEqualTo("[1 2 3 _]");
            assertThat(answerTo(
                    "c: copy [] foreach [a b d] [1 2 3 4] [append c reduce [a b d]] c"))
                    .isEqualTo("[1 2 3 4 _ _]");
        }

        @Test
        @DisplayName("an object walks the same way, which is why one arm serves both")
        void anObjectWalksTheSameWay() {
            assertThat(answerTo(
                    "c: copy [] foreach [k v] make object! [a: 1 b: 2] "
                    + "[append c reduce [k v]] c")).isEqualTo("[a 1 b 2]");
            assertThat(answerTo(
                    "c: copy [] foreach k make object! [a: 1 b: 2] [append c k] c"))
                    .isEqualTo("[a b]");
        }
    }

    @Nested
    @DisplayName("REMOVE-EACH and MAP-EACH over a map")
    class TheOtherWalks {

        @Test
        @DisplayName("REMOVE-EACH takes a map, and answers it")
        void removeEachTakesAMap() {
            assertThat(answerTo(
                    "m: make map! [a 1 b 2] n: remove-each [k v] m [v > 1] "
                    + "reduce [length? m same? m n keys-of m]"))
                    .isEqualTo("[1 #(true) [a]]");
        }

        @Test
        @DisplayName("and /COUNT counts pairs, not values")
        void countCountsPairs() {
            // `SET_INTEGER(DS_RETURN, IS_MAP(value) ? index / 2 : index);` --
            // a caller counting values would be told it removed twice what it
            // removed.
            assertThat(answerTo(
                    "m: make map! [a 1 b 2] "
                    + "reduce [remove-each/count [k v] m [v > 1] length? m]"))
                    .isEqualTo("[1 1]");
        }

        @Test
        @DisplayName("removing nothing answers a count of nothing")
        void removingNothingCountsNothing() {
            assertThat(answerTo(
                    "m: make map! [a 1] "
                    + "reduce [remove-each/count [k v] m [false] length? m]"))
                    .isEqualTo("[0 1]");
        }

        @Test
        @DisplayName("MAP-EACH will not take a map at all")
        void mapEachRefusesAMap() {
            // Not a decision in the walk but in the spec above it: MAP-EACH
            // declares `data [block! vector!]` where FOREACH declares
            // `data [series! any-object! map! none!]`.
            assertThat(errorIdFrom("map-each [k v] make map! [a 1] [v]"))
                    .isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("but it does take a block of words, and answers one value a round")
        void mapEachTakesABlockOfWords() {
            // One Loop_Each serves all four walks, and the names are read
            // before the mode is looked at. Refusing a block here threw a Java
            // exception out of the interpreter rather than answering anything.
            assertThat(answerTo("map-each [a b] [1 2 3 4] [a + b]"))
                    .isEqualTo("[3 7]");
            assertThat(answerTo("map-each [a b] [1 2 3 4] [reduce [b a]]"))
                    .isEqualTo("[[2 1] [4 3]]");
        }

        @Test
        @DisplayName("a last round with nothing left over still runs, filling with none")
        void aShortLastRoundStillRuns() {
            // `else SET_NONE(vars);` when the walk has passed the tail. So the
            // body runs one more time than a caller counting whole rounds
            // expects, and the leftover names hold none rather than the round
            // being dropped. Two walks over the same block with different
            // numbers of names therefore do different numbers of rounds.
            assertThat(answerTo("map-each [a b] [1 2 3] [a]")).isEqualTo("[1 3]");
            assertThat(answerTo("map-each [a b] [1 2 3] [reduce [a b]]"))
                    .isEqualTo("[[1 2] [3 _]]");
        }
    }
}
