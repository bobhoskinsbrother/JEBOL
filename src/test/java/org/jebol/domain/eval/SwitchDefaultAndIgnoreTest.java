package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SWITCH/DEFAULT and COLLECT-WORDS/IGNORE.
 *
 * <p>Specified in {@code spec/natives.allium}, confirmed against a real R3.
 * Both were declared and never read.
 */
class SwitchDefaultAndIgnoreTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("/default runs when nothing matched")
    void defaultRunsOnAMiss() {
        assertThat(answerTo("switch/default 9 [1 [\"one\"]] [\"none of them\"]"))
                .isEqualTo("\"none of them\"");
    }

    @Test
    @DisplayName("/default is left alone when something matched")
    void defaultIsSkippedOnAHit() {
        assertThat(answerTo("switch/default 1 [1 [\"one\"]] [\"none of them\"]"))
                .isEqualTo("\"one\"");
    }

    @Test
    @DisplayName("without /default a miss answers none")
    void aMissWithoutDefaultIsNone() {
        assertThat(answerTo("mold switch 9 [1 [\"one\"]]")).isEqualTo("\"_\"");
    }

    @Test
    @DisplayName("/default is how a none branch is told apart from a miss")
    void defaultDistinguishesAMissFromANoneBranch() {
        assertThat(answerTo("switch/default 1 [1 [none]] ['missed]")).isEqualTo("_");
        assertThat(answerTo("mold switch/default 9 [1 [none]] ['missed]"))
                .isEqualTo("\"missed\"");
    }

    @Test
    @DisplayName("/ignore leaves out the words it is given")
    void ignoreLeavesWordsOut() {
        assertThat(answerTo("mold collect-words/ignore [a: 1 b: 2] [a]"))
                .isEqualTo("\"[b]\"");
    }

    @Test
    @DisplayName("without /ignore every word is collected")
    void withoutIgnoreEverythingComes() {
        assertThat(answerTo("mold collect-words [a: 1 b: 2]")).isEqualTo("\"[a b]\"");
    }

    @Test
    @DisplayName("/ignore takes an object as readily as a block")
    void ignoreTakesAnObject() {
        assertThat(answerTo(
                "o: make object! [a: 1] mold collect-words/ignore [a: 1 b: 2] o"))
                .isEqualTo("\"[b]\"");
    }

    @Test
    @DisplayName("ignoring everything leaves an empty block")
    void ignoringEverythingIsTheDegenerateCase() {
        assertThat(answerTo("mold collect-words/ignore [a: 1 b: 2] [a b]"))
                .isEqualTo("\"[]\"");
    }
}
