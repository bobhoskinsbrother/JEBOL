package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Indexing things that are not series, and dividing with decimals.
 *
 * <p>Each specified in {@code spec/natives.allium} and confirmed against a
 * real R3.
 *
 * <p>A pair and a tuple answer their parts by number the same way a series
 * does, decimal index and all. An error answers its fields the way an
 * object does, because that is what it is.
 */
class IndexingBeyondSeriesTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a pair answers its halves by number")
    void aPairAnswersItsHalves() {
        assertThat(answerTo("p: 1x2 mold reduce [p/1 p/2]")).isEqualTo("\"[1.0 2.0]\"");
    }

    @Test
    @DisplayName("a decimal index into a pair truncates")
    void aDecimalPairIndexTruncates() {
        assertThat(answerTo("p: 1x2 mold reduce [p/1.0 p/1.6 p/2.0 p/2.6]"))
                .isEqualTo("\"[1.0 1.0 2.0 2.0]\"");
    }

    @Test
    @DisplayName("a tuple answers its parts by number")
    void aTupleAnswersItsParts() {
        assertThat(answerTo("t: 1.2.3 mold reduce [t/1 t/2 t/3]"))
                .isEqualTo("\"[1 2 3]\"");
    }

    @Test
    @DisplayName("a decimal index into a tuple truncates")
    void aDecimalTupleIndexTruncates() {
        assertThat(answerTo("t: 1.2.3 mold reduce [t/1.6 t/2.6]"))
                .isEqualTo("\"[1 2]\"");
    }

    @Test
    @DisplayName("INTEGER-DIVIDE throws away a decimal's fraction")
    void integerDivideTakesDecimals() {
        assertThat(answerTo("mold reduce [integer-divide 23.5 10 integer-divide 23 10.5]"))
                .isEqualTo("\"[2 2]\"");
    }

    @Test
    @DisplayName("INTEGER-DIVIDE on two whole numbers is unchanged")
    void integerDivideOnWholeNumbersIsUnaffected() {
        assertThat(answerTo("integer-divide 23 10")).isEqualTo("2");
    }

    @Test
    @DisplayName("SELECT reaches an error's fields")
    void selectReadsAnErrorsFields() {
        assertThat(answerTo("e: try [1 / 0] mold select e 'arg1")).isEqualTo("\"_\"");
    }

    @Test
    @DisplayName("SELECT of a field an error has not got answers none")
    void selectOfAMissingFieldAnswersNone() {
        assertThat(answerTo("e: try [1 / 0] mold select e 'nonsense")).isEqualTo("\"_\"");
    }

    @Test
    @DisplayName("FIND answers something truthy for a field an error has")
    void findAnswersTruthyForARealField() {
        assertThat(answerTo("e: try [1 / 0] true? find e 'arg1")).isEqualTo("#(true)");
    }
}
