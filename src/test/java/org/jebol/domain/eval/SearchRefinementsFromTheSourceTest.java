package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The refinements FIND and SELECT share, read out of the C they share.
 *
 * <p>One arm serves both: {@code case A_FIND: case A_SELECT:} in
 * {@code t-block.c} and again in {@code t-string.c}. Both call the same
 * search, and one line parts them afterwards -- {@code ret += len} -- so
 * SELECT answers the item after the match where FIND answers the match.
 *
 * <p>That is why SELECT has /PART, /LAST, /REVERSE and /ONLY at all: it did
 * not gain them one at a time, it gets whatever the search takes. JEBOL had
 * written SELECT as its own loop over a block, which honoured /SKIP, /CASE
 * and /SAME and quietly ignored the other four.
 *
 * <p>Both take /ANY for the two wildcards and /WITH to rename them. /WITH
 * alone does nothing at all: {@code find_string} reaches the wildcard search
 * only under {@code AM_FIND_ANY}, so the pair is asked for together.
 */
class SearchRefinementsFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static final String NONE = "_";

    @Nested
    @DisplayName("SELECT searches the way FIND does")
    class TheSharedSearch {

        @Test
        @DisplayName("/REVERSE looks behind the position, so it answers an earlier pairing")
        void reverseLooksBehind() {
            assertThat(answerTo("select/reverse (skip [a 1 b 2 a 9] 4) 'a"))
                    .isEqualTo("1");
            assertThat(answerTo("select (skip [a 1 b 2 a 9] 4) 'a"))
                    .isEqualTo("9");
        }

        @Test
        @DisplayName("and finds nothing at the head, because there is nothing behind it")
        void reverseAtTheHeadFindsNothing() {
            assertThat(answerTo("select/reverse [a 1] 'a")).isEqualTo(NONE);
        }

        @Test
        @DisplayName("/LAST starts at the end, so it answers the last pairing")
        void lastStartsAtTheEnd() {
            assertThat(answerTo("select/last [a 1 a 9] 'a")).isEqualTo("9");
        }

        @Test
        @DisplayName("and a last match with nothing after it has no answer")
        void lastWithNothingAfterIt() {
            assertThat(answerTo("select/last [1 a] 'a")).isEqualTo(NONE);
        }

        @Test
        @DisplayName("/PART stops the search short")
        void partStopsTheSearch() {
            assertThat(answerTo("select/part [a 1 b 2] 'b 2")).isEqualTo(NONE);
            assertThat(answerTo("select/part [a 1 b 2] 'b 4")).isEqualTo("2");
        }

        @Test
        @DisplayName("and the answer has to be inside the range too, not just the match")
        void partStopsTheAnswerToo() {
            assertThat(answerTo("select/part [a 1 b 2] 'b 3")).isEqualTo(NONE);
            assertThat(answerTo("select/part [a 1] 'a 1")).isEqualTo(NONE);
            assertThat(answerTo("select/part [a 1] 'a 2")).isEqualTo("1");
        }

        @Test
        @DisplayName("a range can be a position to read up to, or a fraction of one item")
        void aRangeNeedNotBeACount() {
            assertThat(answerTo("b: [a 1 b 2 c 3] select/part b 'b (skip b 4)"))
                    .isEqualTo("2");
            assertThat(answerTo("b: [a 1 b 2 c 3] select/part b 'b (skip b 3)"))
                    .isEqualTo(NONE);
            assertThat(answerTo("select/part [a 1 b 2] 'b 4.9")).isEqualTo("2");
            assertThat(answerTo("select/part [a 1 b 2] 'b 3.9")).isEqualTo(NONE);
        }

        @Test
        @DisplayName("a block needle is a run of items, and the answer follows the whole run")
        void aBlockNeedleIsARun() {
            assertThat(answerTo("select [a b 1] [a b]")).isEqualTo("1");
            assertThat(answerTo("select [a b 1 a c 2] [a c]")).isEqualTo("2");
        }

        @Test
        @DisplayName("and /ONLY makes it one value again, which a block of words does not hold")
        void onlyMakesTheNeedleOneValue() {
            assertThat(answerTo("select/only [a b 1] [a b]")).isEqualTo(NONE);
            assertThat(answerTo("select/only [x [a b] 1] [a b]")).isEqualTo("1");
        }

        @Test
        @DisplayName("a datatype needle asks about each item's type")
        void aDatatypeNeedleAsksTheType() {
            assertThat(answerTo("select [1 \"a\" 2] string!")).isEqualTo("2");
        }

        @Test
        @DisplayName("and the four SELECT already had still behave")
        void theOldFourAreUnchanged() {
            assertThat(answerTo("select [a 1 b 2] 'b")).isEqualTo("2");
            assertThat(answerTo("select/skip [1 2 3 4 5 6] 3 2")).isEqualTo("4");
            assertThat(answerTo("select/skip [1 a 2 b] 'a 2")).isEqualTo(NONE);
            assertThat(answerTo("first select/same [1.0 [1] 1 [2]] 1")).isEqualTo("2");
            assertThat(answerTo("select \"abc\" \"a\"")).isEqualTo("#\"b\"");
            assertThat(errorIdFrom("select/skip [1 2 3] 1 0")).isEqualTo("out-of-range");
        }

        @Test
        @DisplayName("and a binary answers the byte after the one it found")
        void aBinaryAnswersAByte() {
            assertThat(answerTo("select #{010203} 1")).isEqualTo("2");
            assertThat(answerTo("select #{010203} 3")).isEqualTo(NONE);
            assertThat(answerTo("select #{010203} #{0203}")).isEqualTo(NONE);
            assertThat(answerTo("select #{010203} #{0102}")).isEqualTo("3");
        }

        @Test
        @DisplayName("and a string answers the character after the run, /REVERSE included")
        void aStringLooksBehindToo() {
            assertThat(answerTo("select/reverse (skip \"abcabc\" 4) \"a\""))
                    .isEqualTo("#\"b\"");
            assertThat(answerTo("select (skip \"abcabc\" 4) \"a\"")).isEqualTo(NONE);
            assertThat(answerTo("select/part \"abc\" \"a\" 1")).isEqualTo(NONE);
        }
    }

    @Nested
    @DisplayName("/WITH renames the two wildcards")
    class TheChosenWildcards {

        @Test
        @DisplayName("/ANY reads a star as any run and a question mark as one character")
        void theTwoDefaults() {
            assertThat(answerTo("find/any \"hello world\" \"h*o\"")).isEqualTo("\"hello world\"");
            assertThat(answerTo("find/any \"hello\" \"h?llo\"")).isEqualTo("\"hello\"");
        }

        @Test
        @DisplayName("and /WITH gives the run character first and the single one second")
        void withNamesBoth() {
            assertThat(answerTo("find/any/with \"hello\" \"h%o\" \"%_\""))
                    .isEqualTo("\"hello\"");
            assertThat(answerTo("find/any/with \"hello\" \"h_llo\" \"%_\""))
                    .isEqualTo("\"hello\"");
        }

        @Test
        @DisplayName("which leaves the star an ordinary character, and that is the point of it")
        void theStarLosesItsMeaning() {
            assertThat(answerTo("find/any \"abc\" \"a*c\"")).isEqualTo("\"abc\"");
            assertThat(answerTo("find/any/with \"abc\" \"a*c\" \"%_\"")).isEqualTo(NONE);
            assertThat(answerTo("find/any/with \"a*c\" \"a*c\" \"%_\"")).isEqualTo("\"a*c\"");
        }

        @Test
        @DisplayName("and a longer one ignores whatever came after the two")
        void alongerOneIgnoresTheRest() {
            assertThat(answerTo("find/any/with \"abc\" \"a%\" \"%_x\""))
                    .isEqualTo("\"abc\"");
        }

        @Test
        @DisplayName("the folding is what it always was, and /CASE stops it")
        void theCaseFoldingIsUnchanged() {
            assertThat(answerTo("find/any/with \"ABC\" \"a%\" \"%_\""))
                    .isEqualTo("\"ABC\"");
            assertThat(answerTo("find/any/with/case \"ABC\" \"a%\" \"%_\""))
                    .isEqualTo(NONE);
        }

        @Test
        @DisplayName("a one-character /WITH renames the run and leaves the single one alone")
        void oneCharacterRenamesOne() {
            assertThat(answerTo("find/any/with \"abc\" \"a%\" \"%\""))
                    .isEqualTo("\"abc\"");
            assertThat(answerTo("find/any/with \"abc\" \"a?\" \"%\""))
                    .isEqualTo("\"abc\"");
        }

        @Test
        @DisplayName("and an empty one keeps both")
        void anEmptyOneKeepsBoth() {
            assertThat(answerTo("find/any/with \"abc\" \"a*\" \"\"")).isEqualTo("\"abc\"");
            assertThat(answerTo("find/any/with \"abc\" \"a?\" \"\"")).isEqualTo("\"abc\"");
        }

        @Test
        @DisplayName("/WITH without /ANY does nothing, so the needle is taken as it stands")
        void withAloneDoesNothing() {
            assertThat(answerTo("find/with \"hello\" \"h%o\" \"%_\"")).isEqualTo(NONE);
            assertThat(answerTo("find/with \"h%o\" \"h%o\" \"%_\"")).isEqualTo("\"h%o\"");
        }

        @Test
        @DisplayName("/TAIL stands after however much the run took")
        void tailStandsAfterTheMatch() {
            assertThat(answerTo("find/any/with/tail \"hello\" \"h%l\" \"%_\""))
                    .isEqualTo("\"lo\"");
        }

        @Test
        @DisplayName("SELECT takes the same pair, and answers the character after the run")
        void selectTakesThemToo() {
            assertThat(answerTo("select/any/with \"hello\" \"h%l\" \"%_\""))
                    .isEqualTo("#\"l\"");
            assertThat(answerTo("select/any \"hello\" \"h*l\"")).isEqualTo("#\"l\"");
        }

        @Test
        @DisplayName("a wildcard match may not run past where /PART stopped the search")
        void thePartRangeBoundsTheMatchItself() {
            assertThat(answerTo("find/any/part \"abcd\" \"a*d\" 4")).isEqualTo("\"abcd\"");
            assertThat(answerTo("find/any/part \"abcd\" \"a*d\" 3")).isEqualTo(NONE);
            assertThat(answerTo("find/part \"abcd\" \"cd\" 3")).isEqualTo("\"cd\"");
        }

        @Test
        @DisplayName("and a run at the end of the needle takes exactly as far as the range")
        void aTrailingRunStopsAtTheRange() {
            assertThat(answerTo("find/any/tail/part \"abcdef\" \"a*\" 3"))
                    .isEqualTo("\"def\"");
            assertThat(answerTo("find/any/tail \"abcdef\" \"a*\"")).isEqualTo("\"\"");
        }

        @Test
        @DisplayName("and three refinements' arguments arrive in the order the path named them")
        void theArgumentsFollowThePath() {
            assertThat(answerTo("find/part/skip [a 1 b 2 c 3] 'c 6 2")).isEqualTo("[c 3]");
            assertThat(answerTo("find/skip/part [a 1 b 2 c 3] 'c 2 6")).isEqualTo("[c 3]");
            assertThat(answerTo("find/with/any/part \"aXc\" \"a_c\" \"%_\" 3"))
                    .isEqualTo("\"aXc\"");
            assertThat(answerTo("find/part/with/any \"aXc\" \"a_c\" 3 \"%_\""))
                    .isEqualTo("\"aXc\"");
        }

        @Test
        @DisplayName("records look for a shape as well, where they used to look for the text")
        void recordsTakeTheWildcardsToo() {
            assertThat(answerTo("find/any/skip \"xaxbxc\" \"?b\" 2")).isEqualTo("\"xbxc\"");
        }

        @Test
        @DisplayName("and the wildcards must be a string, not whatever the caller had")
        void theWildcardsAreTyped() {
            assertThat(errorIdFrom("find/any/with \"abc\" \"a*\" 5"))
                    .isEqualTo("expect-arg");
            assertThat(errorIdFrom("find/any/with \"abc\" \"a*\" #\"%\""))
                    .isEqualTo("expect-arg");
            assertThat(errorIdFrom("select/any/with \"abc\" \"a*\" 5"))
                    .isEqualTo("expect-arg");
        }
    }
}
