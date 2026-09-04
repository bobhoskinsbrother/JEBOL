package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every series action begins by bringing a stranded position home --
 * {@code if (index > tail) VAL_INDEX(value) = index = tail;} -- so a CHANGE or an
 * INSERT through a view that another view has shortened lands at the end. The
 * negative /part span is Partial1: the position moves back by the count and the
 * count turns positive, so the span always runs forwards from where it lands.
 */
class PastTailClampingFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("a position another view stranded is brought back to the tail")
    class TheStrandedPosition {

        @Test
        @DisplayName("INSERT through a block position one past the tail appends")
        void insertOneStepPastTheTailOfABlock() {
            assertThat(answerTo("""
                    b: [1 2 3] stranded: at b 4 remove b
                    insert stranded 9 b = [2 3 9]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("CHANGE through the same position appends as well")
        void changeOneStepPastTheTailOfABlock() {
            assertThat(answerTo("""
                    b: [1 2 3] stranded: at b 4 remove b
                    change stranded 9 b = [2 3 9]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a position at the tail itself needs no bringing back")
        void aPositionAtTheTailIsUntouched() {
            assertThat(answerTo("""
                    b: [1 2 3] at-the-tail: at b 4
                    insert at-the-tail 9 b = [1 2 3 9]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("INSERT through a string position past a cleared string appends")
        void insertPastTheTailOfAString() {
            assertThat(answerTo("""
                    s: {abcd} stranded: at s 4 clear s
                    insert stranded #"z" s = {z}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("CHANGE through a string position one past the tail appends")
        void changeOneStepPastTheTailOfAString() {
            assertThat(answerTo("""
                    s: {abc} stranded: at s 4 remove s
                    change stranded {zz} s = {bczz}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a position stranded beyond an emptied block still appends")
        void insertPastTheTailOfAnEmptiedBlock() {
            assertThat(answerTo("""
                    b: [1 2 3 4] stranded: at b 4 clear b
                    insert stranded 9 b = [9]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a block inserted at a stranded position splices")
        void insertingABlockAtAStrandedPosition() {
            assertThat(answerTo("""
                    b: [1 2 3 4] stranded: at b 4 clear b
                    insert stranded [7 8] b = [7 8]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and the answer is the position past what was written, not the old index")
        void theAnswerIsThePositionPastTheWrite() {
            assertThat(answerTo("""
                    b: [1 2 3 4] stranded: at b 4 clear b
                    (index? insert stranded 9) = 2""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("a negative /part is the span behind the position")
    class TheSpanBehind {

        @Test
        @DisplayName("CHANGE/PART with a negative count replaces the characters behind")
        void aNegativePartOnAString() {
            assertThat(answerTo("""
                    s: {12345} here: at s 3 change/part here {ab} -2
                    s = {ab345}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and answers the position just past what it wrote")
        void aNegativePartAnswersPastTheWrite() {
            assertThat(answerTo("""
                    s: {12345} here: at s 3
                    (index? change/part here {ab} -2) = 3""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("the same holds for a block")
        void aNegativePartOnABlock() {
            assertThat(answerTo("""
                    b: [1 2 3 4 5] here: at b 3 change/part here 'x -2
                    b = [x 3 4 5]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("at the head there is nothing behind, so nothing is replaced")
        void aNegativePartAtTheHeadReplacesNothing() {
            assertThat(answerTo("""
                    s: {12345} change/part s {ab} -2
                    s = {ab12345}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("at the tail it replaces the last of the series")
        void aNegativePartAtTheTailReplacesTheEnd() {
            assertThat(answerTo("""
                    s: {12345} here: tail s change/part here {ab} -2
                    s = {123ab}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a count reaching further back than the head clamps to the head")
        void aNegativePartClampsToTheHead() {
            assertThat(answerTo("""
                    s: {12345} here: at s 3 change/part here {ab} -9
                    s = {ab345}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a count of zero replaces nothing and still writes")
        void aZeroPartWritesWithoutRemoving() {
            assertThat(answerTo("""
                    s: {12345} here: at s 3 change/part here {ab} 0
                    s = {12ab345}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("TAKE/PART reads the same span backwards")
        void takePartTakesTheSpanBehind() {
            assertThat(answerTo("""
                    (take/part tail {123} -2) = {23}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and takes nothing at the head, where there is nothing behind")
        void takePartAtTheHeadTakesNothing() {
            assertThat(answerTo("""
                    empty? take/part {123} -2""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a block reads the span behind the same way")
        void takePartOnABlockTakesTheSpanBehind() {
            assertThat(answerTo("""
                    (take/part tail [1 2 3] -2) = [2 3]""")).isEqualTo("#(true)");
        }
    }
}
