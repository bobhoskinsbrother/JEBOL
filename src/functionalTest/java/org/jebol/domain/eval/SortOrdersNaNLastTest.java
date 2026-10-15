package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SortOrdersNaNLastTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("the infinities, the ordinary numbers and then the NaNs")
    void everythingInItsPlace() {
        assertThat(answerTo(
                "mold sort [1.#inf -1.0 1.#nan 1.0 -1.#inf 0 1.#NAN]"))
                .isEqualTo("\"[-1.#INF -1.0 0 1.0 1.#INF 1.#NaN 1.#NaN]\"");
    }

    @Test
    @DisplayName("the answer does not depend on the order they arrived in")
    void theOrderingIsStableWhereverTheNaNsStart() {
        String wanted = "\"[-1.#INF -1.0 0 1.0 1.#INF 1.#NaN 1.#NaN]\"";
        assertThat(answerTo("mold sort [1.#NAN 1.#inf -1.0 1.#nan 1.0 -1.#inf 0]"))
                .isEqualTo(wanted);
        assertThat(answerTo("mold sort [1.#inf 1.#NAN -1.0 1.#nan 1.0 -1.#inf 0]"))
                .isEqualTo(wanted);
    }

    @Test
    @DisplayName("a NaN among whole numbers still goes last")
    void aNaNAmongIntegersGoesLast() {
        assertThat(answerTo("mold sort [1.#nan 5 1]")).isEqualTo("\"[1 5 1.#NaN]\"");
    }

    @Test
    @DisplayName("two NaNs are equal, so nothing moves")
    void twoNaNsAreEqual() {
        assertThat(answerTo("mold sort [1.#nan 1.#nan]"))
                .isEqualTo("\"[1.#NaN 1.#NaN]\"");
    }

    @Test
    @DisplayName("the comparison itself still answers true both ways")
    void comparisonIsUnchanged() {
        assertThat(answerTo("mold reduce [1.#NaN < 1 1 < 1.#NaN]"))
                .isEqualTo("\"[#(true) #(true)]\"");
    }

    @Test
    @DisplayName("/all compares whole records rather than their first elements")
    void allComparesWholeRecords() {
        assertThat(answerTo("mold sort/skip/all [4 3 4 1] 2")).isEqualTo("\"[4 1 4 3]\"");
    }

    @Test
    @DisplayName("without /all a record is ordered by its first element alone")
    void withoutAllOnlyTheFirstElementCounts() {
        assertThat(answerTo("mold sort/skip [4 3 4 1] 2")).isEqualTo("\"[4 3 4 1]\"");
    }

    @Test
    @DisplayName("/all and /compare together are refused")
    void theTwoRefinementsConflict() {
        assertThat(answerTo("e: try [sort/skip/all/compare [4 3 4 1] 2 2] "
                + "either error? e [e/id] ['no-error]"))
                .isEqualTo("bad-refines");
    }
}
