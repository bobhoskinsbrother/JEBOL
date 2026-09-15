package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WritingAValueTypeThatNoWordHoldsFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a date a word holds is written where the word is, as it always was")
    void aDateAWordHolds() {
        assertThat(answerTo("""
                d: 1-Jan-2000/0:00
                d/year: 2001
                d""")).isEqualTo("1-Jan-2001/0:00");
    }

    @Nested
    @DisplayName("a date reached through something else is written where it sits")
    class ADate {

        @Test
        @DisplayName("in a block, at a position")
        void inABlock() {
            assertThat(answerTo("""
                    b: reduce [1-Jan-2000/0:00]
                    b/1/year: 2001
                    b/1""")).isEqualTo("1-Jan-2001/0:00");
        }

        @Test
        @DisplayName("in a field of an object")
        void inAnObject() {
            assertThat(answerTo("""
                    o: make object! [dd: 1-Jan-2000/0:00]
                    o/dd/year: 2001
                    o/dd""")).isEqualTo("1-Jan-2001/0:00");
        }

        @Test
        @DisplayName("under a key of a map")
        void inAMap() {
            assertThat(answerTo("""
                    m: make map! reduce ['k 1-Jan-2000/0:00]
                    m/k/year: 2001
                    m/k""")).isEqualTo("1-Jan-2001/0:00");
        }

        @Test
        @DisplayName("and in a block inside a block, so depth is no limit")
        void inABlockInABlock() {
            assertThat(answerTo("""
                    w: reduce [reduce [1-Jan-2000/0:00]]
                    w/1/1/year: 2001
                    w/1/1""")).isEqualTo("1-Jan-2001/0:00");
        }
    }

    @Nested
    @DisplayName("and so is every other value that cannot be changed in place")
    class EveryOtherValueType {

        @Test
        @DisplayName("a pair in a block")
        void aPairInABlock() {
            assertThat(answerTo("""
                    q: reduce [1x2]
                    q/1/x: 9
                    q/1""")).isEqualTo("9x2");
        }

        @Test
        @DisplayName("a tuple in a field of an object")
        void aTupleInAnObject() {
            assertThat(answerTo("""
                    u: make object! [tt: 1.2.3]
                    u/tt/1: 9
                    u/tt""")).isEqualTo("9.2.3");
        }

        @Test
        @DisplayName("an event in a block")
        void anEventInABlock() {
            assertThat(answerTo("""
                    v: reduce [make event! [type: 'down]]
                    v/1/type: 'up
                    v/1/type""")).isEqualTo("up");
        }
    }
}
