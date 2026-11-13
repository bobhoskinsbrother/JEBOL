package org.jebol.domain.parse;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParseBacksUpOnlyToTheAlternativeFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("a failed alternative goes back to where it started, and the next one is tried from there")
    class TheAlternative {

        @Test
        @DisplayName("the second alternative starts where the first did")
        void triesTheNextAlternativeFromTheSamePlace() {
            assertThat(answerTo("""
                    parse "ab" [["a" "x"] | "a" "b"]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an alternative inside a block backs up only to the start of that block")
        void backsUpToTheStartOfTheInnerBlock() {
            assertThat(answerTo("""
                    parse "abc" ["a" ["b" "x" | "b" "c"]]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an alternative made of blocks is tried whole")
        void triesAWholeBlockAlternative() {
            assertThat(answerTo("""
                    parse "abc" [["a" "b" "x"] | ["a" "b" "c"]]""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("a repeat takes all it can and never gives any back")
    class TheRepeat {

        @Test
        @DisplayName("SOME followed by more of the same never matches")
        void someIsNotRetriedAtALowerCount() {
            assertThat(answerTo("""
                    parse "aab" [[some "a"] "a" "b"]""")).isEqualTo("#(false)");
        }

        @Test
        @DisplayName("ANY followed by more of the same never matches")
        void anyIsNotRetriedAtALowerCount() {
            assertThat(answerTo("""
                    parse "aaab" [any "a" "a" "b"]""")).isEqualTo("#(false)");
        }

        @Test
        @DisplayName("a range takes its upper count when it can, and is not tried lower")
        void aRangeIsNotRetriedAtItsLowerCount() {
            assertThat(answerTo("""
                    parse "aab" [1 3 "a" "a" "b"]""")).isEqualTo("#(false)");
        }

        @Test
        @DisplayName("a range stopped short of its upper count leaves the rest to what follows")
        void aRangeStoppedByTheInputLeavesTheRest() {
            assertThat(answerTo("""
                    parse "aaa" [1 2 "a" "a"]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("OPT takes its rule when it can, and is not tried without it")
        void optIsNotRetriedEmpty() {
            assertThat(answerTo("""
                    parse "a" [opt "a" "a"]""")).isEqualTo("#(false)");
        }

        @Test
        @DisplayName("a fixed count followed by more of the same matches when there is more")
        void aFixedCountLeavesTheRest() {
            assertThat(answerTo("""
                    parse "aaa" [2 "a" "a"]""")).isEqualTo("#(true)");
        }
    }
}
