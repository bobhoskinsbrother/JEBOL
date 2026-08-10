package org.jebol.domain.parse;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Telling PARSE's COLLECT where to put what it gathers.
 *
 * <p>Specified in {@code spec/parse.allium}, confirmed against a real R3.
 *
 * <p>The target need not be a block: a string takes the text of what it is
 * given rather than the values, so collecting characters into one gives a
 * string and not a block of characters.
 */
class CollectIntoTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("INTO a string gathers the text")
    void intoAStringGathersText() {
        assertThat(answerTo("a: \"\" parse \"1\" [collect into a [keep skip]] a"))
                .isEqualTo("\"1\"");
    }

    @Test
    @DisplayName("INTO a block gathers the values")
    void intoABlockGathersValues() {
        assertThat(answerTo("b: [] parse \"1\" [collect into b [keep skip]] b = [#\"1\"]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("INTO puts them at the position and pushes what was there along")
    void intoInsertsAtThePosition() {
        assertThat(answerTo("c: \"x\" parse \"1\" [collect into c keep skip] c"))
                .isEqualTo("\"1x\"");
    }

    @Test
    @DisplayName("AFTER puts them past what is there")
    void afterAppends() {
        assertThat(answerTo("d: \"x\" parse \"1\" [collect after d [keep skip]] d"))
                .isEqualTo("\"x1\"");
    }

    @Test
    @DisplayName("SET names a word to hold the collection")
    void setNamesAWord() {
        assertThat(answerTo("parse \"1\" [collect set s [keep skip]] s = [#\"1\"]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a plain COLLECT is unaffected")
    void aPlainCollectStillAnswersItsBlock() {
        assertThat(answerTo("(parse \"1\" [collect [keep skip]]) = [#\"1\"]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the rule after the target really runs")
    void theWalkResumesInTheRightPlace() {
        // Measuring these forms as a plain COLLECT left the walk matching
        // the target word as a rule, which collected nothing and still
        // reported a match.
        assertThat(answerTo(
                "e: [] parse \"12\" [collect into e [some [keep skip]]] e = [#\"1\" #\"2\"]"))
                .isEqualTo("#(true)");
    }
}
