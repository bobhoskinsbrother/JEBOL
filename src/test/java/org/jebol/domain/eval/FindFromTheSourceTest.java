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

    @Test
    @DisplayName("a forward search starts where the series is")
    void theWalkStartsAtThePosition() {
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
        assertThat(answerTo("(find/last [1 2 1] 1) = [1]")).isEqualTo("#(true)");
        assertThat(answerTo("(find/last [1 2 1 2] [1 2]) = [1 2]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/LAST walks back only as far as the position")
    void theLastSearchStopsAtThePosition() {
        assertThat(answerTo("none? find/last skip [1 2 3] 2 1")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/REVERSE starts one before the position and reaches the head")
    void theReverseSearchLooksBehind() {
        assertThat(answerTo("(find/reverse tail [1 2 1] 1) = [1]")).isEqualTo("#(true)");
        assertThat(answerTo("(find/reverse tail \"abc\" \"a\") = \"abc\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/PART bounds the end, counted from the position")
    void thePartBoundsTheEnd() {
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
        assertThat(answerTo("(find/match [x y] 'x) = [x y]")).isEqualTo("#(true)");
        assertThat(answerTo("none? find/match [y x] 'x")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a word matches another word of any type, unless /CASE or /SAME")
    void aWordMatchesAcrossTypes() {
        assertThat(answerTo("(find [a: b] 'a) = [a: b]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("with /CASE a word must be the same type as well")
    void aWordMindsItsTypeWhenAsked() {
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
        assertThat(answerTo("(find [1 \"a\" 2] string!) = [\"a\" 2]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("with /ONLY a datatype needle looks for the datatype itself")
    void aDatatypeCanBeLookedForAsAValue() {
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
        assertThat(answerTo("(index? find [1.0 1] 1) = 1")).isEqualTo("#(true)");
        assertThat(answerTo("(index? find/same [1.0 1] 1) = 2")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("FIND answers the series at the match")
    void findAnswersThePlace() {
        assertThat(answerTo("(find [1 2 3] 2) = [2 3]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/TAIL answers the series past the match")
    void tailStepsOverTheNeedle() {
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
        assertThat(answerTo("""
                one: context [a: 1] two: context [a: 1]
                b: reduce [one two]
                (index? find/same b two) = 2
                """)).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a plain FIND takes the first equal value")
    void theOffPointForSame() {
        assertThat(answerTo("""
                one: context [a: 1] two: context [a: 1]
                b: reduce [one two]
                (index? find b two) = 1
                """)).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/CASE is not /SAME")
    void caseDoesNotAskAboutIdentity() {
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
        assertThat(answerTo("(find/same \"aAbcdAe\" \"A\") = \"AbcdAe\"")).isEqualTo("#(true)");
    }
}
