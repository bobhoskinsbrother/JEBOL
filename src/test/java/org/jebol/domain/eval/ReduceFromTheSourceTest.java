package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * REDUCE, tested against {@code REBNATIVE(reduce)} in
 * {@code src/core/n-control.c} and the three walks it calls in
 * {@code c-do.c}.
 *
 * <p>The C's shape is: work out the values, then store them.
 * {@code Copy_Stack_Values} does the storing and is the same for every
 * branch, thus /INTO behaves the same however the values were reached.
 */
class ReduceFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("/INTO inserts at the target's own position")
    void theTargetPositionIsWhereItGoes() {
        // Insert_Series at VAL_INDEX(into) in the C, not at the head.
        assertThat(answerTo("x: copy [9] reduce/into [1] tail x x = [9 1]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/INTO answers the target just past what went in")
    void theAnswerIsAPosition() {
        // `VAL_INDEX(blk) = len` in Copy_Stack_Values, where len is what
        // Insert_Series gave back. That is what lets a run of these build
        // one series, each carrying on where the last stopped.
        assertThat(answerTo("x: copy [] tail? reduce/into [1] x")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a value that is not a block still goes in")
    void aBareValueIsInserted() {
        // `else if (into != 0)` in the C: the non-block branch pushes the
        // value and stores it the same way.
        assertThat(answerTo("a: 1 x: copy [] reduce/into a x x = [1]")).isEqualTo("#(true)");
        assertThat(answerTo("(head reduce/into \"a\" copy []) = [\"a\"]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("without /INTO a value that is not a block comes back unchanged")
    void aBareValueOtherwiseAnswersItself() {
        // Rebol's own JOIN leans on this: `reduce :rest` where rest may
        // be a single value.
        assertThat(answerTo("(reduce 5) = 5")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the whole sequence from Rebol's own tests")
    void theSequenceHolds() {
        // evaluation-test.r3, "reduce/into". Each call reads and writes
        // the same series, thus the whole run is one test of the rule
        // that the answer is a position.
        assertThat(answerTo("""
                a: 1 b: 2 x: copy []
                reduce/into a x
                reduce/into b x
                reduce/into [a b] x
                reduce/into [b a] tail x
                x = [1 2 2 1 2 1]
                """)).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/ONLY works out the words and copies everything else")
    void onlyReducesNames() {
        // Reduce_Only in the C: a word is looked up, anything else is
        // pushed as it stands.
        assertThat(answerTo("native? second reduce/only [1 now 2] none")).isEqualTo("#(true)");
        assertThat(answerTo("(reduce/only [1 2] none) = [1 2]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/ONLY leaves a word named in its list alone")
    void theListNamesTheExceptions() {
        // `if (ser && NOT_FOUND != Find_Word(...)) { DS_PUSH(val);
        // continue; }` in the C.
        assertThat(answerTo("word? second reduce/only [1 now 2] [now]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a paren reduces to a paren")
    void theTypeIsKept() {
        // `if (type == REB_PAREN) SET_TYPE(DS_TOP, REB_PAREN)` in the C.
        assertThat(answerTo("paren? reduce quote (1 + 1)")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("an empty block reduces to an empty block")
    void theDegenerateBlock() {
        assertThat(answerTo("empty? reduce []")).isEqualTo("#(true)");
    }
}
