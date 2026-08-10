package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Conclusion;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A protected series refuses every change and allows every read.
 *
 * <p>Specified in {@code spec/natives.allium}, confirmed against a real R3.
 *
 * <p>PROTECT answers the value it was given rather than unset, which is
 * what makes {@code b: protect #{0102}} a way to build a protected value
 * and keep hold of it in one step. Answering unset leaves b with no value
 * at all, and then every later use fails on the missing word rather than
 * on the protection -- which reads as though the protection is working.
 */
class ProtectedSeriesTest {

    private static String errorIdOf(String setup, String attempt) {
        String source = setup + " e: try [" + attempt + "] "
                + "either error? e [e/id] ['no-error]";
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        ScriptOutcome outcome = interpreter.run(source);
        assertThat(outcome.conclusion())
                .as("`%s` must arrive as an outcome, never as a host exception", attempt)
                .isEqualTo(Conclusion.PRODUCED_A_VALUE);
        return interpreter.display(outcome);
    }

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("PROTECT answers the value it was given")
    void protectAnswersItsArgument() {
        assertThat(answerTo("mold protect [1 2 3]")).isEqualTo("\"[1 2 3]\"");
        assertThat(answerTo("mold protect #{0102}")).isEqualTo("\"#{0102}\"");
        assertThat(answerTo("protect \"abc\"")).isEqualTo("\"abc\"");
    }

    @Test
    @DisplayName("a protected block refuses CLEAR and POKE")
    void aProtectedBlockRefusesChanges() {
        assertThat(errorIdOf("k: protect [1 2 3]", "clear k")).isEqualTo("protected");
        assertThat(errorIdOf("k: protect [1 2 3]", "poke k 1 9")).isEqualTo("protected");
        assertThat(errorIdOf("k: protect [1 2 3]", "append k 4")).isEqualTo("protected");
    }

    @Test
    @DisplayName("a protected string refuses CLEAR and INSERT")
    void aProtectedStringRefusesChanges() {
        assertThat(errorIdOf("s: protect \"abc\"", "clear s")).isEqualTo("protected");
        assertThat(errorIdOf("s: protect \"abc\"", "insert s \"z\"")).isEqualTo("protected");
    }

    @Test
    @DisplayName("a protected binary refuses CLEAR and APPEND")
    void aProtectedBinaryRefusesChanges() {
        assertThat(errorIdOf("b: protect #{0102}", "clear b")).isEqualTo("protected");
        assertThat(errorIdOf("b: protect #{0102}", "append b 3")).isEqualTo("protected");
    }

    @Test
    @DisplayName("reading a protected series is untouched")
    void readsStillWork() {
        assertThat(answerTo("b: protect #{0102} mold b")).isEqualTo("\"#{0102}\"");
        assertThat(answerTo("k: protect [1 2 3] first k")).isEqualTo("1");
        assertThat(answerTo("k: protect [1 2 3] length? k")).isEqualTo("3");
    }

    @Test
    @DisplayName("UNPROTECT lets the changes through again")
    void unprotectRestoresTheSeries() {
        assertThat(errorIdOf("k: protect [1 2 3] unprotect k", "clear k"))
                .isEqualTo("no-error");
    }

    @Test
    @DisplayName("an unprotected series was never refusing anything")
    void anUnprotectedSeriesIsTheOffPoint() {
        assertThat(errorIdOf("k: [1 2 3]", "clear k")).isEqualTo("no-error");
    }

    @Test
    @DisplayName("PROTECT/DEEP reaches a series inside a block")
    void deepReachesNestedSeries() {
        assertThat(errorIdOf("k: protect/deep [[1 2]]", "clear first k"))
                .isEqualTo("protected");
    }

    @Test
    @DisplayName("without /deep an inner series is left alone")
    void shallowProtectionStopsAtTheOuterSeries() {
        assertThat(errorIdOf("k: protect [[1 2]]", "clear first k"))
                .isEqualTo("no-error");
    }
}
