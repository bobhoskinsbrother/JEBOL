package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CONTINUE stops this round of a loop and starts the next.
 *
 * <p>Specified in {@code spec/natives.allium} and measured against a real
 * R3 3.22.1. Ported off the porting backlog; JEBOL had BREAK and not this.
 *
 * <p>The two are caught in different places, and that is the whole of the
 * difference: a break around the whole walk, a continue around each turn
 * of it. A CONTINUE that reached the outer catch would end the loop, which
 * is what BREAK is for.
 */
class ContinueTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("LOOP starts the next round")
    void loopCarriesOn() {
        assertThat(answerTo(
                "r: copy [] loop 3 [append r 1 continue append r 2] r = [1 1 1]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("REPEAT and FOREACH skip the round they were in")
    void theCountingLoopsSkipOne() {
        assertThat(answerTo(
                "r: copy [] repeat i 3 [if i = 2 [continue] append r i] r = [1 3]"))
                .isEqualTo("#(true)");
        assertThat(answerTo(
                "r: copy [] foreach v [1 2 3] [if v = 2 [continue] append r v] r = [1 3]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("WHILE re-tests its condition")
    void theConditionIsAskedAgain() {
        assertThat(answerTo(
                "n: 0 r: copy [] while [n < 3] [n: n + 1 continue append r n] "
                        + "all [empty? r n = 3]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("BREAK still stops the loop outright")
    void breakIsNotContinue() {
        assertThat(answerTo(
                "r: copy [] repeat i 3 [if i = 2 [break] append r i] r = [1]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a CONTINUE several blocks deep still reaches the loop")
    void itReachesUpThroughBlocks() {
        assertThat(answerTo(
                "r: copy [] repeat i 3 [if true [if i = 2 [continue]] append r i] r = [1 3]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a loop whose last round continued answers none")
    void theCutShortRoundAnswersNothing() {
        assertThat(answerTo("none? loop 2 [continue 5]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a loop with no CONTINUE in it is unaffected")
    void theOrdinaryLoopStillWorks() {
        assertThat(answerTo("r: copy [] repeat i 3 [append r i] r = [1 2 3]"))
                .isEqualTo("#(true)");
        assertThat(answerTo("(loop 3 [5]) = 5")).isEqualTo("#(true)");
    }
}
