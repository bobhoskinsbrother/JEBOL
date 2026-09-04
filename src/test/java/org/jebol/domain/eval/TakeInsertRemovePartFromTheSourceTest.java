package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TAKE, INSERT and REMOVE with /part, from {@code series-test.r3}. /deep clones
 * the nested series that /part took; INSERT/part splices only the counted run
 * and refuses a count outside the 32-bit range; and REMOVE/part reads a length
 * from a position into the same series.
 */
class TakeInsertRemovePartFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("TAKE/deep/part clones the nested series it took")
    void deepPartClonesTheNested() {
        assertThat(answerTo("""
                orig: [x] b: reduce [orig 9] c: take/deep/part b 1
                same? orig first c""")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("TAKE/part without /deep hands back the very same nested series")
    void shallowPartShares() {
        assertThat(answerTo("""
                orig: [x] b: reduce [orig 9] c: take/part b 1
                same? orig first c""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("INSERT/part splices only the counted run from the head")
    void insertPartSplicesTheCountedRun() {
        assertThat(answerTo("""
                a: [1 2 3 4] b: [5 6 7 8 9] insert/part a b 2
                a = [5 6 1 2 3 4]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("INSERT/part counts backward from the value's position")
    void insertPartCountsBackwardFromTheTail() {
        assertThat(answerTo("""
                a: [1 2 3 4] b: [5 6 7 8 9] insert/part a tail b -2
                a = [8 9 1 2 3 4]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("INSERT/part refuses a count past the 32-bit range")
    void insertPartRefusesAnOversizeCount() {
        assertThat(answerTo("""
                a: [1 2 3 4] b: [5 6 7 8 9]
                e: try [insert/part a b 2147483648] e/id = 'out-of-range"""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("REMOVE/part reads a length from a position into the same series")
    void removePartFromASameSeriesPosition() {
        assertThat(answerTo("""
                r: [1 2 3] remove/part r next r r = [2 3]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("REMOVE/part at the very position removes nothing")
    void removePartAtTheSamePositionRemovesNothing() {
        assertThat(answerTo("""
                r: [1 2 3] remove/part r r r = [1 2 3]""")).isEqualTo("#(true)");
    }
}
