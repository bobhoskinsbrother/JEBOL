package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FIND and SELECT with /skip, from {@code series-test.r3}. /reverse and /last
 * step one element at a time whatever the record width; a sub-one forward skip
 * misses on a string but is refused on a block; and a block key matches a run
 * inside a record.
 */
class FindAndSelectSkipFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("FIND/skip/reverse steps by one, not by the record width")
    void reverseStepsByOne() {
        assertThat(answerTo("""
                "cde" = find/skip/reverse tail "acd000cde" "cd" -3""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("SELECT/skip/last steps by one and reads the element after the key")
    void lastStepsByOne() {
        assertThat(answerTo("""
                'c = select/skip/last [a b a c] 'a 2""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("FIND/skip with a negative forward width misses on a string")
    void negativeForwardSkipMissesOnAString() {
        assertThat(answerTo("""
                none? find/skip "acdcde" "cd" -3""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("FIND/skip with a zero forward width misses on a string")
    void zeroForwardSkipMissesOnAString() {
        assertThat(answerTo("""
                none? find/skip "acdcde" "cd" 0""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("FIND/skip with a sub-one forward width is refused on a block")
    void subOneForwardSkipIsRefusedOnABlock() {
        assertThat(answerTo("""
                e: try [find/skip [1 2 3 4 5 6] 5 -4] e/id = 'out-of-range"""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("SELECT/skip matches a block key as a run inside a record")
    void blockKeyMatchesARun() {
        assertThat(answerTo("""
                'y = select/skip [a a a b b y] [b b] 3""")).isEqualTo("#(true)");
    }
}
