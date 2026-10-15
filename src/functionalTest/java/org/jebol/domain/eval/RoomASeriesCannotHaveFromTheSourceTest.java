package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoomASeriesCannotHaveFromTheSourceTest {

    private static final String ONE_SLOT_PAST_WHAT_A_BLOCK_COUNTS = "67108863";

    private static final String ONE_BYTE_PAST_WHAT_A_TEXT_COUNTS = "2147483647";

    private static final String A_COUNT_NO_HEAP_CAN_SERVE = "2000000000";

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String failureOf(String source) {
        return answerTo("e: try [" + source + """
                ]
                either error? e [reduce [e/type e/id]] [reduce ['ok]]""");
    }

    @Test
    @DisplayName("a block asked for one slot more than a 32-bit byte count holds is refused")
    void aBlockPastTheCountIsRefused() {
        assertThat(failureOf("make block! " + ONE_SLOT_PAST_WHAT_A_BLOCK_COUNTS))
                .isEqualTo("[Internal no-memory]");
        assertThat(failureOf("make block! 500000000")).isEqualTo("[Internal no-memory]");
    }

    @Test
    @DisplayName("and every other series of values shares that boundary")
    void everySeriesOfValuesSharesTheBoundary() {
        assertThat(failureOf("make paren! " + ONE_SLOT_PAST_WHAT_A_BLOCK_COUNTS))
                .isEqualTo("[Internal no-memory]");
        assertThat(failureOf("make hash! " + ONE_SLOT_PAST_WHAT_A_BLOCK_COUNTS))
                .isEqualTo("[Internal no-memory]");
        assertThat(failureOf("make path! " + ONE_SLOT_PAST_WHAT_A_BLOCK_COUNTS))
                .isEqualTo("[Internal no-memory]");
        assertThat(failureOf("make map! " + ONE_SLOT_PAST_WHAT_A_BLOCK_COUNTS))
                .isEqualTo("[Internal no-memory]");
    }

    @Test
    @DisplayName("a text or a binary counts bytes, so its boundary is thirty-two times further out")
    void aTextOrBinaryCountsBytes() {
        assertThat(failureOf("make binary! " + ONE_BYTE_PAST_WHAT_A_TEXT_COUNTS))
                .isEqualTo("[Internal no-memory]");
        assertThat(failureOf("make string! " + ONE_BYTE_PAST_WHAT_A_TEXT_COUNTS))
                .isEqualTo("[Internal no-memory]");
        assertThat(failureOf("make file! " + ONE_BYTE_PAST_WHAT_A_TEXT_COUNTS))
                .isEqualTo("[Internal no-memory]");
        assertThat(failureOf("make tag! " + ONE_BYTE_PAST_WHAT_A_TEXT_COUNTS))
                .isEqualTo("[Internal no-memory]");
    }

    @Test
    @DisplayName("a vector counts four bytes a number, so its boundary sits between the two")
    void aVectorCountsFourBytesANumber() {
        assertThat(failureOf("make vector! 536870911")).isEqualTo("[Internal no-memory]");
        assertThat(answerTo("length? make vector! 1000")).isEqualTo("1000");
    }

    @Test
    @DisplayName("the refusal is arithmetic, so it does not depend on the block slot count being reachable")
    void theRefusalIsArithmeticRatherThanAnAttempt() {
        assertThat(failureOf("make block! " + ONE_SLOT_PAST_WHAT_A_BLOCK_COUNTS))
                .isEqualTo("[Internal no-memory]");
        assertThat(failureOf("make block! 99999999999")).isEqualTo("[Internal no-memory]");
    }

    @Test
    @DisplayName("a count the host cannot serve is the same failure, not a host exception")
    void aCountTheHostCannotServeIsTheSameFailure() {
        assertThat(failureOf("make string! " + A_COUNT_NO_HEAP_CAN_SERVE))
                .isEqualTo("[Internal no-memory]");
    }

    @Test
    @DisplayName("an ordinary count is served")
    void anOrdinaryCountIsServed() {
        assertThat(answerTo("length? make block! 1000000")).isEqualTo("0");
        assertThat(answerTo("length? make string! 1000000")).isEqualTo("0");
        assertThat(answerTo("empty? make binary! 1000")).isEqualTo("#(true)");
    }
}
