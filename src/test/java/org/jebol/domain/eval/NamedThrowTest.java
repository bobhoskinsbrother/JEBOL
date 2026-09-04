package org.jebol.domain.eval;

import org.jebol.application.Conclusion;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A throw may carry a name, and the name decides who may catch it.
 *
 * <p>Specified by {@code CatchTakesOnlyAThrowItWasExpecting} and
 * {@code CatchWithRunsItsHandlerOnlyOnAThrow} in
 * {@code spec/natives.allium}, confirmed against a real R3.
 *
 * <p>The pairing is strict in both directions, and that is the point: an
 * unnamed CATCH is not a catch-all, so a throw meant for an outer handler
 * goes straight past an inner one that was not expecting it. The
 * boundaries are therefore the four combinations of named and unnamed on
 * each side, plus the deliberate catch-all.
 */
class NamedThrowTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        ScriptOutcome outcome = interpreter.run(source);
        assertThat(outcome.conclusion())
                .as("%s must not escape as a host exception", source)
                .isEqualTo(Conclusion.PRODUCED_A_VALUE);
        return interpreter.display(outcome);
    }

    /** Whether the inner CATCH took it, seen from an outer catch-all. */
    private static String escapesTo(String inner) {
        return answerTo("catch/all [" + inner + " 'not-thrown]");
    }

    @Test
    @DisplayName("an unnamed CATCH takes an unnamed throw")
    void unnamedCatchesUnnamed() {
        assertThat(answerTo("catch [throw 5]")).isEqualTo("5");
    }

    @Test
    @DisplayName("a CATCH that catches nothing answers what its block answered")
    void nothingThrownGivesTheBlocksOwnValue() {
        assertThat(answerTo("catch [1 2 3]")).isEqualTo("3");
    }

    @Test
    @DisplayName("a named CATCH takes a throw of that name")
    void namedCatchesTheSameName() {
        assertThat(answerTo("catch/name [throw/name 5 'foo] 'foo")).isEqualTo("5");
    }

    @Test
    @DisplayName("a named CATCH lets a throw of another name past")
    void namedDoesNotCatchAnotherName() {
        assertThat(escapesTo("catch/name [throw/name 5 'foo] 'bar"))
                .as("the throw was addressed to somebody else")
                .isEqualTo("5");
    }

    @Test
    @DisplayName("an unnamed CATCH lets a named throw past")
    void unnamedDoesNotCatchNamed() {
        assertThat(escapesTo("catch [throw/name 5 'foo]"))
                .as("an unnamed CATCH is not a catch-all")
                .isEqualTo("5");
    }

    @Test
    @DisplayName("/all takes a named throw")
    void allCatchesNamed() {
        assertThat(answerTo("catch/all [throw/name 5 'foo]")).isEqualTo("5");
    }

    @Test
    @DisplayName("/all takes an unnamed throw too")
    void allCatchesUnnamed() {
        assertThat(answerTo("catch/all [throw 5]")).isEqualTo("5");
    }

    @Test
    @DisplayName("/name accepts a block of names and takes any of them")
    void nameAcceptsSeveralNames() {
        assertThat(answerTo("catch/name [throw/name 5 'foo] [bar foo]")).isEqualTo("5");
        assertThat(escapesTo("catch/name [throw/name 5 'foo] [bar baz]")).isEqualTo("5");
    }

    @Test
    @DisplayName("/with runs its handler instead of answering the thrown value")
    void withRunsTheHandler() {
        assertThat(answerTo("catch/with [throw 5] [10]")).isEqualTo("10");
    }

    @Test
    @DisplayName("/with leaves the handler alone when nothing was thrown")
    void withIsAHandlerNotAFinally() {
        assertThat(answerTo("catch/with [1] [10]"))
                .as("the handler runs on a throw, not on the way out")
                .isEqualTo("1");
    }

    @Test
    @DisplayName("/quit catches what QUIT raised, and the run carries on")
    void quitIsCatchable() {
        assertThat(answerTo("catch/quit [quit/return 7]")).isEqualTo("7");
    }

    @Test
    @DisplayName("/quit answers the block's own value when nothing quit")
    void quitWithoutAQuitIsOrdinary() {
        assertThat(answerTo("catch/quit [3]")).isEqualTo("3");
    }

    @Test
    @DisplayName("a plain CATCH still does not catch a QUIT")
    void quitNeedsTheRefinement() {
        Interpreter interpreter = Interpreter.create();
        String source = "catch [quit/return 7] 99";
        interpreter.defineFreshWordsIn(source);

        assertThat(interpreter.run(source).conclusion())
                .as("stopping the script is not a throw")
                .isEqualTo(Conclusion.QUIT_EARLY);
    }
}
