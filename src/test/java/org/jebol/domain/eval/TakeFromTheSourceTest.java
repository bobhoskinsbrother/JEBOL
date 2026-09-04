package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TAKE, tested against the {@code A_TAKE} case in Rebol's
 * {@code src/core/t-block.c}.
 *
 * <p>One test per branch that case takes. The C's shape is: work out how
 * many to take, move the index if /LAST, then answer one value or a
 * series depending on whether /PART was asked for.
 *
 * <p>That last rule is the one to keep in mind. Without /PART the answer
 * is the value itself; with /PART it is always a series, even when the
 * count is one and even when it is zero.
 */
class TakeFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("without /PART the answer is one value, not a series")
    void oneValueComesBackBare() {
        assertThat(answerTo("b: copy [1 2 3] (take b) = 1")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("with /PART the answer is a series, even of one")
    void aPartAlwaysAnswersASeries() {
        assertThat(answerTo("b: copy [1 2 3] (take/part b 1) = [1]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the taken items leave the series")
    void theSeriesShrinks() {
        assertThat(answerTo("b: copy [1 2 3] take b b = [2 3]")).isEqualTo("#(true)");
        assertThat(answerTo("b: copy [1 2 3] take/part b 2 b = [3]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a part of zero answers an empty series and changes nothing")
    void theZeroPart() {
        assertThat(answerTo("b: copy [1 2 3] all [empty? take/part b 0  b = [1 2 3]]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/ALL takes everything and implies /PART")
    void allTakesTheRest() {
        assertThat(answerTo("b: copy [1 2 3] all [(take/all b) = [1 2 3]  empty? b]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/ALL of an empty series answers an empty series")
    void theDegenerateAll() {
        assertThat(answerTo("b: copy [] empty? take/all b")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/LAST takes from the end")
    void lastReadsTheEnd() {
        assertThat(answerTo("b: copy [1 2 3] all [(take/last b) = 3  b = [1 2]]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/LAST with /PART takes the last few, not the first few")
    void lastMovesWhereTheTakingStarts() {
        assertThat(answerTo("b: copy [1 2 3] all [(take/last/part b 2) = [2 3]  b = [1]]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("taking from a series at its end answers none")
    void theEmptyTakeIsNone() {
        assertThat(answerTo("b: tail copy [1 2] none? take b")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("taking a part from a series at its end answers an empty series")
    void theEmptyPartIsASeries() {
        assertThat(answerTo("b: tail copy [1 2] empty? take/part b 2")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a part longer than what is left takes what is left")
    void aPartBeyondTheEnd() {
        assertThat(answerTo("b: copy [1 2] all [(take/part b 9) = [1 2]  empty? b]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("TAKE works on a string and a binary too")
    void theOtherSeriesKinds() {
        assertThat(answerTo("s: copy \"abc\" all [(take s) = #\"a\"  s = \"bc\"]"))
                .isEqualTo("#(true)");
        assertThat(answerTo("s: copy \"abc\" (take/part s 2) = \"ab\"")).isEqualTo("#(true)");
        assertThat(answerTo("b: copy #{010203} (take/part b 2) = #{0102}")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("from the tail, a negative count reads backwards")
    void aNegativeCountReadsBack() {
        assertThat(answerTo("(take/last/part tail \"123\" -3) = \"123\"")).isEqualTo("#(true)");
        assertThat(answerTo("(take/last/part tail [1 2 3] -3) = [1 2 3]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("from the tail, a positive count reads nothing")
    void aPositiveCountFromTheTailIsEmpty() {
        assertThat(answerTo("empty? take/last/part tail \"123\" 3")).isEqualTo("#(true)");
        assertThat(answerTo("empty? take/last/part tail [1 2 3] 3")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/LAST at the tail with no /PART is none")
    void theTailWithNoPart() {
        assertThat(answerTo("none? take/last tail \"123\"")).isEqualTo("#(true)");
        assertThat(answerTo("none? take/last tail [1 2 3]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("without /DEEP the taken value is the one the series held")
    void aPlainTakeSharesWhatItTook() {
        assertThat(answerTo("a: [1 [2]] b: reduce [a] c: take b same? a c"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/DEEP copies what it took, all the way down")
    void deepTakesACopy() {
        assertThat(answerTo("a: [1 [2]] b: reduce [a] c: take/deep b not same? a c"))
                .isEqualTo("#(true)");
        assertThat(answerTo("a: [1 [2]] b: reduce [a] c: take/deep b not same? a/2 c/2"))
                .as("Clone_Block reaches the nested block too")
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a change through the copy leaves the first block alone")
    void theCopyIsIndependent() {
        assertThat(answerTo(
                "a: [1 [2]] b: reduce [a] c: take/deep b append c/2 3 a/2 = [2]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/DEEP copies a string as readily as a block")
    void deepCopiesAnySeries() {
        assertThat(answerTo("a: \"1\" b: reduce [a 2] c: take/deep b not same? a c"))
                .isEqualTo("#(true)");
    }
}
