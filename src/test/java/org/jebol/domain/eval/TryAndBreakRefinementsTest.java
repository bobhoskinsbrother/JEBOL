package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Conclusion;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TRY/ALL, TRY/WITH, BREAK/RETURN and ATTEMPT/SAFER.
 *
 * <p>Specified in {@code spec/natives.allium}, confirmed against a real R3.
 *
 * <p>The boundary that matters for each is the same one: whether the thing
 * being widened to actually happened. A handler that runs when nothing
 * failed is a finally rather than a handler, and a refinement that changes
 * the answer on the ordinary path has changed more than it was asked to.
 */
class TryAndBreakRefinementsTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        ScriptOutcome outcome = interpreter.run(source);
        assertThat(outcome.conclusion())
                .as("%s must not escape as a host exception", source)
                .isEqualTo(Conclusion.PRODUCED_A_VALUE);
        return interpreter.display(outcome);
    }

    @Test
    @DisplayName("TRY/ALL turns a throw into an error value")
    void tryAllCatchesAThrow() {
        assertThat(answerTo("e: try/all [throw 5] e/id")).isEqualTo("throw");
    }

    @Test
    @DisplayName("plain TRY still lets a throw past")
    void plainTryDoesNotCatchAThrow() {
        // The default stays honest: a throw is a decision, not a failure.
        assertThat(answerTo("catch [try [throw 5] 99]")).isEqualTo("5");
    }

    @Test
    @DisplayName("TRY/ALL still catches an ordinary failure")
    void tryAllStillCatchesErrors() {
        assertThat(answerTo("error? try/all [1 / 0]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("TRY/ALL answers the block's value when nothing went wrong")
    void tryAllIsTransparentOnSuccess() {
        assertThat(answerTo("try/all [3]")).isEqualTo("3");
    }

    @Test
    @DisplayName("TRY/WITH runs its handler on a failure")
    void tryWithRunsTheHandlerOnFailure() {
        assertThat(answerTo("try/with [1 / 0] [42]")).isEqualTo("42");
    }

    @Test
    @DisplayName("TRY/WITH leaves the handler alone when nothing failed")
    void tryWithIsAHandlerNotAFinally() {
        assertThat(answerTo("try/with [3] [42]")).isEqualTo("3");
    }

    @Test
    @DisplayName("BREAK/RETURN gives the loop a value to answer")
    void breakReturnGivesTheLoopAValue() {
        assertThat(answerTo("repeat i 3 [break/return 9]")).isEqualTo("9");
    }

    @Test
    @DisplayName("a plain BREAK leaves the loop answering unset")
    void plainBreakAnswersUnset() {
        assertThat(answerTo("mold repeat i 3 [break]")).isEqualTo("\"#(unset)\"");
    }

    @Test
    @DisplayName("BREAK/RETURN works from inside a WHILE too")
    void breakReturnWorksInEveryLoop() {
        assertThat(answerTo("while [true] [break/return 7]")).isEqualTo("7");
    }

    @Test
    @DisplayName("BREAK/RETURN can carry a value that looks like nothing")
    void breakReturnCarriesDegenerateValues() {
        assertThat(answerTo("repeat i 3 [break/return none]")).isEqualTo("_");
        assertThat(answerTo("repeat i 3 [break/return 0]")).isEqualTo("0");
        assertThat(answerTo("mold repeat i 3 [break/return []]")).isEqualTo("\"[]\"");
    }

    @Test
    @DisplayName("ATTEMPT answers none on a failure")
    void attemptAnswersNoneOnFailure() {
        assertThat(answerTo("attempt [1 / 0]")).isEqualTo("_");
    }

    @Test
    @DisplayName("ATTEMPT answers the block's value when nothing failed")
    void attemptIsTransparentOnSuccess() {
        assertThat(answerTo("attempt [3]")).isEqualTo("3");
    }

    @Test
    @DisplayName("ATTEMPT/SAFER answers none as well, having caught more")
    void attemptSaferStillAnswersNone() {
        assertThat(answerTo("attempt/safer [1 / 0]")).isEqualTo("_");
        assertThat(answerTo("attempt/safer [3]")).isEqualTo("3");
    }
}
