package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Conclusion;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DO/NEXT, CONSTRUCT/ONLY, CONTEXT? and RESOLVE.
 *
 * <p>Specified in {@code spec/natives.allium}, confirmed against a real R3.
 *
 * <p>Each of these is about not evaluating something: DO/NEXT stops after
 * one expression, CONSTRUCT/ONLY refuses even to turn the word `none` into
 * the none value, and RESOLVE leaves alone whatever the target already had.
 */
class StepwiseAndContextTest {

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
    @DisplayName("DO/NEXT evaluates one expression and answers it")
    void doNextEvaluatesOneExpression() {
        assertThat(answerTo("blk: [1 + 1 5] do/next blk 'blk")).isEqualTo("2");
    }

    @Test
    @DisplayName("DO/NEXT moves the word on to what is left")
    void doNextStepsTheWord() {
        assertThat(answerTo("blk: [1 + 1 5] do/next blk 'blk mold blk"))
                .isEqualTo("\"[5]\"");
    }

    @Test
    @DisplayName("DO/NEXT can be stepped to the end of the block")
    void doNextWalksTheWholeBlock() {
        assertThat(answerTo("blk: [1 2] do/next blk 'blk do/next blk 'blk mold blk"))
                .as("two expressions, two steps, nothing left")
                .isEqualTo("\"[]\"");
    }

    @Test
    @DisplayName("CONSTRUCT reads none, true and false as the values they name")
    void constructReadsTheNamedValues() {
        assertThat(answerTo("mold construct [a: 1 b: none]"))
                .contains("b: _");
    }

    @Test
    @DisplayName("CONSTRUCT/ONLY keeps them as words")
    void constructOnlyKeepsTheWords() {
        assertThat(answerTo("mold construct/only [a: 1 b: none]"))
                .as("data shaped like a spec should not have its words become values")
                .contains("b: 'none");
    }

    @Test
    @DisplayName("CONTEXT? answers the object a bound word lives in")
    void contextAnswersTheOwningObject() {
        assertThat(answerTo("o: make object! [q: 1] mold context? in o 'q"))
                .contains("q: 1");
    }

    @Test
    @DisplayName("RESOLVE fills in only what the target has no value for")
    void resolveLeavesExistingValuesAlone() {
        assertThat(answerTo(
                "a: make object! [x: 1] b: make object! [x: 9 y: 2] resolve a b a/x"))
                .as("x already had a value, so it keeps it")
                .isEqualTo("1");
    }

    @Test
    @DisplayName("RESOLVE does not add words the target lacks")
    void resolveDoesNotExtendByDefault() {
        assertThat(answerTo(
                "a: make object! [x: 1] b: make object! [x: 9 y: 2] resolve a b "
                        + "mold words-of a"))
                .isEqualTo("\"[x]\"");
    }

    @Test
    @DisplayName("RESOLVE/ALL overwrites what the target had")
    void resolveAllOverwrites() {
        assertThat(answerTo(
                "a: make object! [x: 1] b: make object! [x: 9] resolve/all a b a/x"))
                .isEqualTo("9");
    }

    @Test
    @DisplayName("RESOLVE/EXTEND adds the words the target lacks")
    void resolveExtendAddsWords() {
        assertThat(answerTo(
                "a: make object! [x: 1] b: make object! [x: 9 y: 2] "
                        + "resolve/extend a b mold words-of a"))
                .isEqualTo("\"[x y]\"");
    }

    @Test
    @DisplayName("RESOLVE/ONLY takes a block of the words it may write")
    void resolveOnlyLimitsToTheNamedWords() {
        assertThat(answerTo(
                "a: make object! [x: 1 y: 2] b: make object! [x: 9 y: 9] "
                        + "resolve/only/all a b [x] mold reduce [a/x a/y]"))
                .isEqualTo("\"[9 2]\"");
    }

    @Test
    @DisplayName("and anything in that block which is not a word is passed over")
    void resolveOnlyIgnoresWhatIsNotAWord() {
        assertThat(answerTo(
                "a: make object! [x: 1] b: make object! [x: 9] "
                        + "resolve/only/all a b [x: 5 \"z\"] a/x"))
                .isEqualTo("9");
    }

    @Test
    @DisplayName("a word it names that the source has not got is unset in the target")
    void resolveOnlyUnsetsWhatTheSourceLacks() {
        assertThat(answerTo(
                "a: make object! [x: 1] b: make object! [y: 9] "
                        + "resolve/only/all a b [x] unset? a/x"))
                .isEqualTo("#(true)");
        assertThat(answerTo(
                "a: make object! [x: 1] b: make object! [y: 9] "
                        + "resolve/all a b a/x"))
                .as("with no /ONLY the word was never marked, so it keeps its value")
                .isEqualTo("1");
    }

    @Test
    @DisplayName("RESOLVE/ONLY takes a position instead, meaning the target's words from there")
    void resolveOnlyTakesAPosition() {
        assertThat(answerTo(
                "a: make object! [x: 1 y: 2] b: make object! [x: 9 y: 9] "
                        + "resolve/only/all a b 1 mold reduce [a/x a/y]"))
                .isEqualTo("\"[9 9]\"");
    }

    @Test
    @DisplayName("and a position past the end has nothing to do rather than failing")
    void resolveOnlyPastTheEnd() {
        assertThat(answerTo(
                "a: make object! [x: 1] b: make object! [x: 9] "
                        + "resolve/only/all a b 99 a/x"))
                .isEqualTo("1");
    }

    @Test
    @DisplayName("and /EXTEND adds only what /ONLY named")
    void resolveOnlyLimitsTheExtending() {
        assertThat(answerTo(
                "a: make object! [x: 1] b: make object! [y: 2 z: 3] "
                        + "resolve/only/extend a b [y] mold words-of a"))
                .isEqualTo("\"[x y]\"");
    }
}
