package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Conclusion;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ++ and --, TRUNCATE, and the conversions to binary! and file!.
 *
 * <p>Specified in {@code spec/natives.allium}, confirmed against a real R3.
 *
 * <p>Two of these answer something other than the obvious. ++ answers what
 * the word held before it changed, and TO BINARY! of an integer gives all
 * eight bytes of its machine width rather than the fewest that would hold
 * it. Both readings are defensible and both are wrong.
 */
class CountersAndConversionsTest {

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
    @DisplayName("++ answers what the word held before, and leaves it one higher")
    void incrementAnswersTheOldValue() {
        assertThat(answerTo("a: 1 mold reduce [++ a a]")).isEqualTo("\"[1 2]\"");
    }

    @Test
    @DisplayName("-- answers the old value and leaves it one lower")
    void decrementAnswersTheOldValue() {
        assertThat(answerTo("b: 5 mold reduce [-- b b]")).isEqualTo("\"[5 4]\"");
    }

    @Test
    @DisplayName("++ on a series steps the position, not the contents")
    void incrementStepsASeries() {
        assertThat(answerTo("s: [1 2 3] mold reduce [++ s s]"))
                .isEqualTo("\"[[1 2 3] [2 3]]\"");
    }

    @Test
    @DisplayName("++ from zero and from a negative are ordinary")
    void incrementAtTheBoundaries() {
        assertThat(answerTo("z: 0 ++ z z")).isEqualTo("1");
        assertThat(answerTo("n: -1 ++ n n")).isEqualTo("0");
    }

    @Test
    @DisplayName("TO BINARY! of a string is its UTF-8")
    void stringToBinaryIsItsBytes() {
        assertThat(answerTo("mold to binary! \"ab\"")).isEqualTo("\"#{6162}\"");
    }

    @Test
    @DisplayName("TO BINARY! of a block is one byte per number")
    void blockToBinaryIsOneBytePerNumber() {
        assertThat(answerTo("mold to binary! [1 2 3]")).isEqualTo("\"#{010203}\"");
    }

    @Test
    @DisplayName("TO BINARY! of an integer is its whole machine width")
    void integerToBinaryIsEightBytes() {
        // Not #{41}. The shorter reading is the useful one and the wrong
        // one, and the two agree for anything under 256 written as a block.
        assertThat(answerTo("mold to binary! 65"))
                .isEqualTo("\"#{0000000000000041}\"");
    }

    @Test
    @DisplayName("TO BINARY! of an empty string or block gives an empty binary")
    void emptyToBinaryIsTheDegenerateCase() {
        assertThat(answerTo("mold to binary! \"\"")).isEqualTo("\"#{}\"");
        assertThat(answerTo("mold to binary! []")).isEqualTo("\"#{}\"");
    }

    @Test
    @DisplayName("TO FILE! takes the text of the value")
    void toFileTakesTheText() {
        assertThat(answerTo("mold to file! \"a/b\"")).isEqualTo("\"%a/b\"");
        assertThat(answerTo("mold to file! 'abc")).isEqualTo("\"%abc\"");
    }

    @Test
    @DisplayName("TO FILE! of a block runs the parts together, inserting nothing")
    void toFileOfABlockInsertsNoSeparators() {
        assertThat(answerTo("mold to file! [a b]"))
                .as("%ab, not %a/b")
                .isEqualTo("\"%ab\"");
    }

    @Test
    @DisplayName("TRUNCATE keeps only what is at and after the position")
    void truncateDropsWhatIsBehind() {
        assertThat(answerTo("mold truncate skip [1 2 3 4] 2")).isEqualTo("\"[3 4]\"");
    }

    @Test
    @DisplayName("TRUNCATE/PART bounds how much of the rest is kept")
    void truncatePartBoundsTheRest() {
        assertThat(answerTo("mold truncate/part skip [1 2 3 4] 2 1")).isEqualTo("\"[3]\"");
    }

    @Test
    @DisplayName("TRUNCATE at the head changes nothing")
    void truncateAtTheHeadIsTheDegenerateCase() {
        assertThat(answerTo("mold truncate [1 2 3]")).isEqualTo("\"[1 2 3]\"");
    }
}
