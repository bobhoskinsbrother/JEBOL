package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SORT's refinements, and what each of them leaves alone.
 *
 * <p>Specified in {@code spec/natives.allium}, confirmed against a real R3.
 *
 * <p>The boundaries for /PART are the counts: nothing, one, all of it, and
 * more than there is. A part that reaches past the end must sort what is
 * there rather than failing, and a part of nothing must move nothing.
 */
class SortRefinementsTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("SORT puts a block in order")
    void sortOrdersABlock() {
        assertThat(answerTo("mold sort [3 1 2]")).isEqualTo("\"[1 2 3]\"");
    }

    @Test
    @DisplayName("SORT/REVERSE puts it in the other order")
    void reverseSortsTheOtherWay() {
        assertThat(answerTo("mold sort/reverse [3 1 2]")).isEqualTo("\"[3 2 1]\"");
    }

    @Test
    @DisplayName("SORT/PART sorts the front and leaves the rest where it was")
    void partSortsOnlyTheFront() {
        assertThat(answerTo("mold sort/part [3 1 2 9] 3"))
                .as("the 9 was outside the part and did not move")
                .isEqualTo("\"[1 2 3 9]\"");
    }

    @Test
    @DisplayName("SORT/PART of nothing moves nothing")
    void partOfNothingIsTheDegenerateCase() {
        assertThat(answerTo("mold sort/part [3 1 2] 0")).isEqualTo("\"[3 1 2]\"");
    }

    @Test
    @DisplayName("SORT/PART of one item moves nothing either")
    void partOfOneMovesNothing() {
        assertThat(answerTo("mold sort/part [3 1 2] 1")).isEqualTo("\"[3 1 2]\"");
    }

    @Test
    @DisplayName("SORT/PART past the end sorts what is there")
    void anOversizedPartClamps() {
        assertThat(answerTo("mold sort/part [3 1 2] 9")).isEqualTo("\"[1 2 3]\"");
    }

    @Test
    @DisplayName("SORT/COMPARE with a function uses it")
    void compareTakesAFunction() {
        assertThat(answerTo("mold sort/compare [3 1 2] func [a b] [a > b]"))
                .isEqualTo("\"[3 2 1]\"");
    }

    @Test
    @DisplayName("SORT/SKIP/COMPARE with a column number sorts by that column")
    void compareTakesAColumn() {
        assertThat(answerTo("mold sort/skip/compare [3 9 1 8] 2 1"))
                .isEqualTo("\"[1 8 3 9]\"");
    }

    @Test
    @DisplayName("SORT/CASE minds case")
    void caseMindsIt() {
        assertThat(answerTo("first sort/case [\"B\" \"a\"]"))
                .as("uppercase sorts before lowercase when case is minded")
                .isEqualTo("\"B\"");
    }

    @Test
    @DisplayName("sorting an empty block gives an empty block")
    void anEmptyBlockIsTheOtherDegenerateCase() {
        assertThat(answerTo("mold sort []")).isEqualTo("\"[]\"");
    }
}
