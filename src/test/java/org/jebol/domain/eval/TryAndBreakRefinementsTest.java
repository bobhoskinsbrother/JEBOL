package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Conclusion;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.Molder;
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

    /**
     * The id of the error a script failed with.
     *
     * <p>Read from the outcome rather than through TRY, because the four
     * signals are exactly the ones TRY does not catch: they have to reach the
     * top for this to say anything.
     */
    private static String errorIdFrom(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        ScriptOutcome outcome = interpreter.run(source);
        assertThat(outcome.conclusion())
                .as("%s must fail as a script rather than as a host exception", source)
                .isEqualTo(Conclusion.RAISED);
        return ((ErrorValue) outcome.value()).field("id")
                .map(Molder::form)
                .orElse("no id");
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
    @Test
    @DisplayName("a disarmed throw carries what was thrown, and the name it was thrown under")
    void aDisarmedThrowCarriesItsValue() {
        // `Make_Error(code, arg1, sym ? &word : 0, 0)` in Disarm_Throw_Error:
        // the value thrown becomes arg1 and the name arg2. Without them a
        // handler can see that something was thrown and not what, which is the
        // half that matters. Every answer here is measured against a real R3.
        assertThat(answerTo("reduce [string? try/all/with [throw 1] :mold "
                + "system/state/last-error/id system/state/last-error/arg1]"))
                .isEqualTo("[#(true) throw 1]");
        assertThat(answerTo("try/all/with [throw/name 1 'foo] :mold "
                + "reduce [system/state/last-error/arg1 system/state/last-error/arg2]"))
                .isEqualTo("[1 foo]");
    }

    @Test
    @DisplayName("and a disarmed RETURN carries its value, where EXIT carries none")
    void aDisarmedReturnCarriesItsValue() {
        assertThat(answerTo("try/all/with [return 1] :mold "
                + "reduce [system/state/last-error/id system/state/last-error/arg1]"))
                .isEqualTo("[return 1]");
        assertThat(answerTo("try/all/with [exit] :mold "
                + "reduce [system/state/last-error/id none? system/state/last-error/arg1]"))
                .isEqualTo("[return #(true)]");
    }

    @Test
    @DisplayName("a BREAK carries nothing, having nothing to carry")
    void aDisarmedBreakCarriesNothing() {
        assertThat(answerTo("try/all/with [break] :mold "
                + "reduce [system/state/last-error/id none? system/state/last-error/arg1]"))
                .isEqualTo("[break #(true)]");
    }

    @Test
    @DisplayName("TRY clears the last error on the way in, so a success leaves none")
    void tryClearsTheLastErrorOnTheWayIn() {
        // `SET_NONE(error); // reset the last error` before the block runs. So
        // last-error describes this call and not some earlier one, and code
        // reading it after a TRY that worked reads none.
        assertThat(answerTo("try [1 / 0] try [1] none? system/state/last-error"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a signal nothing caught becomes its own error at the top of the script")
    void anUncaughtSignalBecomesAnError() {
        // The whole Throw category of boot/errors.reb is these four: `break:
        // {no loop to break}`, `continue:`, `return:` and `throw:`. Each is
        // reported when the signal reaches the top with nothing having caught
        // it, so the run ends as a failed script rather than as a host
        // exception -- which spec/embed.allium forbids outright. The helper
        // above asserts that on every call in this class.
        assertThat(errorIdFrom("break")).isEqualTo("break");
        assertThat(errorIdFrom("continue")).isEqualTo("continue");
        assertThat(errorIdFrom("return 5")).isEqualTo("return");
        assertThat(errorIdFrom("exit")).isEqualTo("return");
        assertThat(errorIdFrom("throw 5")).isEqualTo("throw");
    }

    @Test
    @DisplayName("and one reached through a path built at runtime is no different")
    void aSignalReachedThroughAPathIsTheSame() {
        // How Rebol's own ANY-OF and ALL-OF break out of a FOREACH: `to path!
        // reduce [:break 'return]` builds `break/return` with the native value
        // at its head rather than the word. That escaped as a Java exception,
        // and it is the same signal by another road.
        assertThat(errorIdFrom("p: to path! reduce [:break 'return] do reduce [p 7]"))
                .isEqualTo("break");
    }

    @Test
    @DisplayName("while a loop and a function still catch their own")
    void theOnesWithSomewhereToGoStillGoThere() {
        assertThat(answerTo("unset? loop 2 [break]")).isEqualTo("#(true)");
        assertThat(answerTo("loop 2 [break/return 7]")).isEqualTo("7");
        assertThat(answerTo("f: func [] [return 5] f")).isEqualTo("5");
    }

    @Test
    @DisplayName("a block handler reads the error through system/state/last-error")
    void aBlockHandlerReadsTheLastError() {
        // A block has no argument to receive the error, so this is the only way
        // it can see what it is handling -- and Rebol's own suite handles all
        // five signals this way.
        assertThat(answerTo(
                "h: [system/state/last-error/id] reduce [try/all/with [break] :h "
                + "try/all/with [continue] :h try/all/with [exit] :h "
                + "try/all/with [return 1] :h try/all/with [throw 1] :h]"))
                .isEqualTo("[break continue return return throw]");
    }
}
