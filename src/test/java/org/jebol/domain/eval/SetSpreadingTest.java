package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Conclusion;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SET given several words at once, and what it spreads across them.
 *
 * <p>Specified in {@code spec/natives.allium}, confirmed against a real R3.
 *
 * <p>The boundaries are the lengths: more values than words, fewer, exactly
 * as many, and none at all. A short value block pads rather than failing,
 * so the interesting case is what the padding is made of.
 */
class SetSpreadingTest {

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
    @DisplayName("one word takes the value it was given")
    void oneWordIsTheOrdinaryCase() {
        assertThat(answerTo("set 'a 5 a")).isEqualTo("5");
    }

    @Test
    @DisplayName("a block of values spreads across a block of words")
    void aBlockSpreadsOneForOne() {
        assertThat(answerTo("set [b c] [1 2] mold reduce [b c]")).isEqualTo("\"[1 2]\"");
    }

    @Test
    @DisplayName("anything that is not a block goes to every word")
    void aSingleValueGoesToEveryWord() {
        assertThat(answerTo("set [d e] 9 mold reduce [d e]")).isEqualTo("\"[9 9]\"");
    }

    @Test
    @DisplayName("too few values pads the rest with none")
    void tooFewValuesPadWithNone() {
        assertThat(answerTo("set [k l m] [1 2] mold reduce [k l m]"))
                .isEqualTo("\"[1 2 _]\"");
    }

    @Test
    @DisplayName("too many values leaves the extras unused")
    void tooManyValuesAreIgnored() {
        assertThat(answerTo("set [n o] [1 2 3] mold reduce [n o]"))
                .isEqualTo("\"[1 2]\"");
    }

    @Test
    @DisplayName("an empty value block leaves every word holding none")
    void anEmptyValueBlockIsTheDegenerateCase() {
        assertThat(answerTo("set [p q] [] mold reduce [p q]")).isEqualTo("\"[_ _]\"");
    }

    @Test
    @DisplayName("/only gives every word the whole block")
    void onlyTurnsOffTheSpreading() {
        assertThat(answerTo("set/only [f g] [1 2] mold reduce [f g]"))
                .isEqualTo("\"[[1 2] [1 2]]\"");
    }

    @Test
    @DisplayName("/some leaves a word alone where the value would be none")
    void someSkipsTheNones() {
        assertThat(answerTo(
                "h: 1 i: 2 set/some [h i] reduce [10 none] mold reduce [h i]"))
                .as("i keeps the 2 it had")
                .isEqualTo("\"[10 2]\"");
    }

    @Test
    @DisplayName("without /some the none is written")
    void plainSetWritesTheNone() {
        assertThat(answerTo(
                "h: 1 i: 2 set [h i] reduce [10 none] mold reduce [h i]"))
                .isEqualTo("\"[10 _]\"");
    }

    @Test
    @DisplayName("SET on an object fills its fields in order")
    void anObjectTakesValuesInFieldOrder() {
        assertThat(answerTo(
                "o: make object! [x: 0 y: 0] set o [7 8] mold reduce [o/x o/y]"))
                .isEqualTo("\"[7 8]\"");
    }

    @Test
    @DisplayName("/any allows a word to be set to unset")
    void anyAllowsUnset() {
        assertThat(answerTo("set/any 'j () value? 'j")).isEqualTo("#(false)");
    }
}
