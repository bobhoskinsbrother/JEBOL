package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FIND, tested against what Rebol's C says rather than what JEBOL does.
 *
 * <p>Every test here comes from reading {@code Find_Block} in
 * {@code src/core/t-block.c} and {@code find_string} in
 * {@code t-string.c}, one test per branch those functions take. Tests
 * written by reading the port only prove the port agrees with itself.
 *
 * <p>The C has four needle branches for a block: a word, a block, a
 * datatype or typeset, and everything else. Each has its own comparison,
 * and /SAME, /CASE and /ONLY change which one is used.
 */
class FindFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    // ---- the walk: start, end and step -------------------------------

    @Test
    @DisplayName("a forward search starts where the series is")
    void theWalkStartsAtThePosition() {
        // start = index in the C, thus an item before the position is
        // never reached.
        assertThat(answerTo("(find skip [1 2 1] 1 1) = [1]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a search from a place that already matches finds that place")
    void thePositionItselfCounts() {
        assertThat(answerTo("(find find \"abcabc\" \"b\" \"b\") = \"bcabc\""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/LAST starts at the end less the needle's width")
    void theLastSearchStartsAtTheEnd() {
        // index = end - len in the C. A needle of three cannot begin in
        // the last two places, thus the width has to be known first.
        assertThat(answerTo("(find/last [1 2 1] 1) = [1]")).isEqualTo("#(true)");
        assertThat(answerTo("(find/last [1 2 1 2] [1 2]) = [1 2]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/LAST walks back only as far as the position")
    void theLastSearchStopsAtThePosition() {
        // start = index in the C for /LAST, thus it never answers a place
        // behind where the series is.
        assertThat(answerTo("none? find/last skip [1 2 3] 2 1")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/REVERSE starts one before the position and reaches the head")
    void theReverseSearchLooksBehind() {
        // start = 0 and index-- in the C. It is the only search that
        // answers a place the series has passed.
        assertThat(answerTo("(find/reverse tail [1 2 1] 1) = [1]")).isEqualTo("#(true)");
        assertThat(answerTo("(find/reverse tail \"abc\" \"a\") = \"abc\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/PART bounds the end, counted from the position")
    void thePartBoundsTheEnd() {
        // end = index + Partial1(...) in the C.
        assertThat(answerTo("none? find/part [1 2 3] 3 2")).isEqualTo("#(true)");
        assertThat(answerTo("(find/part [1 2 3] 3 3) = [3]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a part of zero finds nothing at all")
    void theDegeneratePart() {
        assertThat(answerTo("none? find/part [x] 'x 0")).isEqualTo("#(true)");
        assertThat(answerTo("(find/part [x] 'x 1) = [x]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/MATCH looks at one place and then stops")
    void theMatchLooksOnlyHere() {
        // The C breaks out of the loop after the first item when
        // AM_FIND_MATCH is set.
        assertThat(answerTo("(find/match [x y] 'x) = [x y]")).isEqualTo("#(true)");
        assertThat(answerTo("none? find/match [y x] 'x")).isEqualTo("#(true)");
    }

    // ---- the needle branches ----------------------------------------

    @Test
    @DisplayName("a word matches another word of any type, unless /CASE or /SAME")
    void aWordMatchesAcrossTypes() {
        // The C's word branch: without /CASE or /SAME it compares the
        // canonical spelling, thus a set-word matches a word.
        assertThat(answerTo("(find [a: b] 'a) = [a: b]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("with /CASE a word must be the same type as well")
    void aWordMindsItsTypeWhenAsked() {
        // "Must be same type and spelling" in the C.
        assertThat(answerTo("none? find/case [a: b] 'a")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a block needle matches a run, unless /ONLY")
    void aBlockNeedleIsARun() {
        assertThat(answerTo("(find [1 2 3] [2 3]) = [2 3]")).isEqualTo("#(true)");
        assertThat(answerTo("none? find/only [1 2 3] [2 3]"))
                .as("with /ONLY the block is one value to look for")
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a run that would reach past the end does not match")
    void aRunNeedsRoom() {
        assertThat(answerTo("none? find [1 2] [2 3]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a datatype needle matches any value of that type")
    void aDatatypeMatchesItsValues() {
        // The C's datatype branch compares VAL_TYPE against the datatype
        // rather than comparing the values.
        assertThat(answerTo("(find [1 \"a\" 2] string!) = [\"a\" 2]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("with /ONLY a datatype needle looks for the datatype itself")
    void aDatatypeCanBeLookedForAsAValue() {
        // "not checking value types, only if value and target are really
        // same" in the C.
        assertThat(answerTo("(find/only reduce [1 string!] string!) = reduce [string!]"))
                .isEqualTo("#(true)");
        assertThat(answerTo("none? find/only [1 \"a\"] string!"))
                .as("and it stops asking about the values' types")
                .isEqualTo("#(true)");
        assertThat(answerTo("none? find reduce [1 string!] string!"))
                .as("without /ONLY a datatype needle asks about types, "
                        + "and no value here is a string")
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("anything else is compared value against value")
    void theOrdinaryNeedle() {
        assertThat(answerTo("(find [1 2 3] 2) = [2 3]")).isEqualTo("#(true)");
        assertThat(answerTo("none? find [1 2 3] 9")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/SAME compares by identity rather than by value")
    void sameUsesTheStricterComparison() {
        // The C calls Compare_Values with mode 3 for /SAME and Cmp_Value
        // otherwise, thus 1 and 1.0 stop being one another.
        assertThat(answerTo("(index? find [1.0 1] 1) = 1")).isEqualTo("#(true)");
        assertThat(answerTo("(index? find/same [1.0 1] 1) = 2")).isEqualTo("#(true)");
    }

    // ---- what the answer is ------------------------------------------

    @Test
    @DisplayName("FIND answers the series at the match")
    void findAnswersThePlace() {
        assertThat(answerTo("(find [1 2 3] 2) = [2 3]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/TAIL answers the series past the match")
    void tailStepsOverTheNeedle() {
        // ret += len in the C, thus a run of two steps over two.
        assertThat(answerTo("(find/tail [1 2 3] 1) = [2 3]")).isEqualTo("#(true)");
        assertThat(answerTo("(find/tail [1 2 3] [1 2]) = [3]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a match at the very end answers an empty series with /TAIL")
    void theDegenerateTail() {
        assertThat(answerTo("empty? find/tail [1 2] 2")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/SAME asks for the very same value, not an equal one")
    void sameIsIdentityAndNotEquality() {
        // `Compare_Values(value, val, 3)` in the C, and 3 is "same
        // (identical bits)" by its own comment.
        //
        // Two objects holding the same fields are equal and are not the
        // same object, thus /SAME finds the one that was handed in and
        // not the copy beside it. Comparing by value finds the copy,
        // which looks right until two copies sit in the same block.
        assertThat(answerTo("""
                one: context [a: 1] two: context [a: 1]
                b: reduce [one two]
                (index? find/same b two) = 2
                """)).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a plain FIND takes the first equal value")
    void theOffPointForSame() {
        // Both objects are equal, thus the first is found. This is what
        // makes the pair above a test of identity rather than of order.
        assertThat(answerTo("""
                one: context [a: 1] two: context [a: 1]
                b: reduce [one two]
                (index? find b two) = 1
                """)).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/CASE is not /SAME")
    void caseDoesNotAskAboutIdentity() {
        // The suite says so in a comment beside the assertion: "/case is
        // not /same in this case".
        assertThat(answerTo("""
                one: context [a: 1] two: context [a: 1]
                b: reduce [one two]
                (index? find/case b two) = 1
                """)).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/SAME tells two strings apart by which one they are")
    void twoEqualStringsAreNotTheSame() {
        assertThat(answerTo("""
                s: "a"
                b: reduce ["a" s]
                (index? find/same b s) = 2
                """)).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/SAME on a string minds case, which is what the C says")
    void sameOnAStringIsCase() {
        // "/SAME has same functionality as /CASE for any-string!" in
        // t-string.c. A string is compared by its characters either way.
        assertThat(answerTo("(find/same \"aAbcdAe\" \"A\") = \"AbcdAe\"")).isEqualTo("#(true)");
    }
}
