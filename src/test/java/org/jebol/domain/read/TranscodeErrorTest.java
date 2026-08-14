package org.jebol.domain.read;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Conclusion;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TRANSCODE/ERROR, which hands back what went wrong instead of raising it.
 *
 * <p>Specified in {@code spec/load.allium}, confirmed against a real R3.
 *
 * <p>TRY built into the reader, for a caller reading text they did not
 * write who wants to look at the failure rather than catch it. Without the
 * refinement the same source raises, which is what makes it worth having.
 */
class TranscodeErrorTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        ScriptOutcome outcome = interpreter.run(source);
        assertThat(outcome.conclusion())
                .as("%s must not raise", source)
                .isEqualTo(Conclusion.PRODUCED_A_VALUE);
        return interpreter.display(outcome);
    }

    @Test
    @DisplayName("bad source comes back as an error value")
    void badSourceIsAValue() {
        assertThat(answerTo("error? transcode/one/error \"2#12\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the error says what was wrong with it")
    void theErrorCarriesItsId() {
        assertThat(answerTo("e: transcode/one/error \"2#12\" e/id")).isEqualTo("invalid");
    }

    @Test
    @DisplayName("good source is unaffected by the refinement")
    void goodSourceIsUnaffected() {
        assertThat(answerTo("transcode/one/error \"1\"")).isEqualTo("1");
    }

    @Test
    @DisplayName("without the refinement the same source raises")
    void withoutTheRefinementItRaises() {
        Interpreter interpreter = Interpreter.create();
        String source = "transcode/one \"2#12\"";
        interpreter.defineFreshWordsIn(source);

        assertThat(interpreter.run(source).conclusion())
                .as("the refinement is what changes it, not the reader")
                .isEqualTo(Conclusion.RAISED);
    }

    @Test
    @DisplayName("a digit outside the base is refused in each base")
    void eachBaseRefusesItsOwnBadDigits() {
        assertThat(answerTo("mold reduce [error? transcode/one/error \"2#12\" "
                + "error? transcode/one/error \"8#88\" "
                + "error? transcode/one/error \"10#1A2\"]"))
                .isEqualTo("\"[#(true) #(true) #(true)]\"");
    }

    @Test
    @DisplayName("a number too big for its base is refused too")
    void anOversizedNumberIsRefused() {
        assertThat(answerTo("error? transcode/one/error "
                + "\"10#9999999999999999999\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the block form answers an error the same way")
    void theBlockFormAnswersAnErrorToo() {
        assertThat(answerTo("error? first transcode/error \"2#12\"")).isEqualTo("#(true)");
    }
}
