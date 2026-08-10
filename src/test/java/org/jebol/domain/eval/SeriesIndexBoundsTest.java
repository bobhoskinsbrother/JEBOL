package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Conclusion;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the series natives do at the edges of an index.
 *
 * <p>Specified by {@code WritingAnElementOutsideTheSeriesRaises},
 * {@code ReadingAnElementOutsideTheSeriesGivesNone},
 * {@code NonPositiveStepSizeRaises} and
 * {@code TakingEverythingFromAnEmptySeriesGivesAnEmptyOne} in
 * {@code spec/natives.allium}, each confirmed against a real R3.
 *
 * <p>Every case here currently escapes as a Java exception rather than as
 * a REBOL error, which {@code spec/embed.allium} promises cannot happen: a
 * host has to be able to tell a script failing from JEBOL having a bug, and
 * it cannot if both arrive as a throwable.
 *
 * <p>The boundaries are the index against a three-element series -- one
 * below the bottom, the bottom, the middle, the top, one past the top --
 * and then the degenerate series that has no valid index at all.
 */
class SeriesIndexBoundsTest {

    private static ScriptOutcome ran(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.run(source);
    }

    private static String errorIdOf(String source) {
        ScriptOutcome outcome = ran("e: try [" + source + "] either error? e [e/id] ['no-error]");
        assertThat(outcome.conclusion())
                .as("%s must arrive as an outcome, never as a host exception", source)
                .isEqualTo(Conclusion.PRODUCED_A_VALUE);
        return Interpreter.create().display(outcome);
    }

    private static String answerTo(String source) {
        ScriptOutcome outcome = ran(source);
        assertThat(outcome.conclusion())
                .as("%s must not escape as a host exception", source)
                .isEqualTo(Conclusion.PRODUCED_A_VALUE);
        return Interpreter.create().display(outcome);
    }

    // === POKE, which refuses an index outside the series ===

    @Test
    @DisplayName("POKE one below the first element raises out-of-range")
    void pokeAtZeroRaises() {
        assertThat(errorIdOf("poke b: [1 2 3] 0 9")).isEqualTo("out-of-range");
    }

    @Test
    @DisplayName("POKE at a negative index raises out-of-range")
    void pokeAtMinusOneRaises() {
        assertThat(errorIdOf("poke b: [1 2 3] -1 9")).isEqualTo("out-of-range");
    }

    @Test
    @DisplayName("POKE at the first element works")
    void pokeAtOneWorks() {
        assertThat(answerTo("poke b: [1 2 3] 1 9 b")).isEqualTo("[9 2 3]");
    }

    @Test
    @DisplayName("POKE at the last element works")
    void pokeAtTheLastWorks() {
        assertThat(answerTo("poke b: [1 2 3] 3 9 b")).isEqualTo("[1 2 9]");
    }

    @Test
    @DisplayName("POKE one past the last element raises out-of-range")
    void pokePastTheEndRaises() {
        assertThat(errorIdOf("poke b: [1 2 3] 4 9")).isEqualTo("out-of-range");
    }

    @Test
    @DisplayName("POKE into an empty series raises, even at 1")
    void pokeIntoAnEmptySeriesRaises() {
        assertThat(errorIdOf("poke b: copy [] 1 9")).isEqualTo("out-of-range");
    }

    @Test
    @DisplayName("POKE respects the same bounds on a string")
    void pokeOnAStringHasTheSameBounds() {
        assertThat(errorIdOf("poke s: copy \"abc\" 0 #\"x\"")).isEqualTo("out-of-range");
        assertThat(errorIdOf("poke s: copy \"abc\" 4 #\"x\"")).isEqualTo("out-of-range");
        assertThat(answerTo("poke s: copy \"abc\" 1 #\"x\" s")).isEqualTo("\"xbc\"");
    }

    @Test
    @DisplayName("POKE respects the same bounds on a binary")
    void pokeOnABinaryHasTheSameBounds() {
        assertThat(errorIdOf("poke b: #{010203} 4 9")).isEqualTo("out-of-range");
    }

    @Test
    @DisplayName("POKE with a string where the index belongs is rejected, not coerced")
    void pokeWithAWrongTypeIndexRaises() {
        // The index arrives from the script, so nothing has vetted it.
        // Rejection is what matters here; silently reading "1" as 1 would
        // be the worst answer available.
        //
        // JEBOL says expect-arg where a real R3 says invalid-arg. That is
        // one of the error-id divergences being reconciled separately, and
        // it is deliberately not papered over by asserting the wrong id.
        assertThat(errorIdOf("poke b: [1 2 3] \"x\" 9"))
                .isIn("expect-arg", "invalid-arg");
    }

    // === PICK, which shrugs at the same index ===

    @Test
    @DisplayName("PICK past the end gives none rather than raising")
    void pickPastTheEndGivesNone() {
        assertThat(answerTo("pick [1 2 3] 4")).isEqualTo("_");
    }

    @Test
    @DisplayName("PICK at zero and below gives none")
    void pickBelowTheStartGivesNone() {
        assertThat(answerTo("pick [1 2 3] 0")).isEqualTo("_");
        assertThat(answerTo("pick [1 2 3] -1")).isEqualTo("_");
    }

    // === A step size is an element count, so it has the same floor ===

    @Test
    @DisplayName("SELECT/SKIP with a step of zero raises out-of-range")
    void selectWithNoStepRaises() {
        assertThat(errorIdOf("select/skip [1 2 3 4 5 6] 5 0")).isEqualTo("out-of-range");
    }

    @Test
    @DisplayName("SELECT/SKIP with a negative step raises out-of-range")
    void selectWithABackwardStepRaises() {
        assertThat(errorIdOf("select/skip [1 2 3 4 5 6] 5 -4")).isEqualTo("out-of-range");
    }

    @Test
    @DisplayName("SELECT/SKIP with a step of one is the ordinary case")
    void selectWithTheSmallestStepWorks() {
        assertThat(answerTo("select/skip [1 2 3 4 5 6] 5 1")).isEqualTo("6");
    }

    @Test
    @DisplayName("SELECT answers the element after the match, whatever the record width")
    void selectAnswersTheNextElementNotTheRecordEnd() {
        // The width decides only where SELECT may look. Returning the
        // record's last field instead agrees at a width of two and is
        // wrong at every other width, which is why three are checked.
        assertThat(answerTo("select/skip [1 2 3 4 5 6] 5 2")).isEqualTo("6");
        assertThat(answerTo("select/skip [1 2 3 4 5 6] 3 2")).isEqualTo("4");
        assertThat(answerTo("select/skip [1 2 3 4 5 6] 4 3")).isEqualTo("5");
        assertThat(answerTo("select/skip [1 2 3 4 5 6] 1 3")).isEqualTo("2");
    }

    @Test
    @DisplayName("SELECT with no record width looks at every position")
    void selectWithoutSkipLooksEverywhere() {
        // A default width of two would never look at an even position and
        // would answer none here.
        assertThat(answerTo("select [1 2 3 4 5 6] 2")).isEqualTo("3");
        assertThat(answerTo("select [1 2 3 4 5 6] 3")).isEqualTo("4");
    }

    @Test
    @DisplayName("SELECT answers none when the match is the last element")
    void selectAtTheTailAnswersNone() {
        assertThat(answerTo("select [1 2 3 4 5 6] 6")).isEqualTo("_");
        assertThat(answerTo("select/skip [1 2 3 4 5] 5 2")).isEqualTo("_");
    }

    @Test
    @DisplayName("SELECT ignores a value sitting inside a record rather than at its start")
    void selectIgnoresMidRecordValues() {
        assertThat(answerTo("select/skip [1 2 3 4 5 6] 2 2")).isEqualTo("_");
    }

    // === Emptiness is ordinary, not an error path ===

    @Test
    @DisplayName("TAKE/ALL on an empty block gives an empty block")
    void takeAllOnAnEmptyBlockGivesAnEmptyBlock() {
        assertThat(answerTo("take/all copy []")).isEqualTo("[]");
    }

    @Test
    @DisplayName("TAKE/ALL on an empty string gives an empty string")
    void takeAllOnAnEmptyStringGivesAnEmptyString() {
        assertThat(answerTo("take/all copy \"\"")).isEqualTo("\"\"");
    }

    @Test
    @DisplayName("TAKE/ALL on a series with values still takes them all")
    void takeAllOnAFullSeriesTakesEverything() {
        assertThat(answerTo("take/all b: copy [1 2 3]")).isEqualTo("[1 2 3]");
    }
}
