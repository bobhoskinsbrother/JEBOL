package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * BODY-OF an object, and APPEND/PART and /DUP onto one.
 *
 * <p>Specified in {@code spec/natives.allium} and measured against a real
 * R3 3.22.1.
 *
 * <p>BODY-OF gives the object written out as source: each field as a
 * set-word with its value after it, which is what MAKE OBJECT! would take
 * to build the same object again. JEBOL answered none, so every assertion
 * that checked what an APPEND had done to an object was reading nothing.
 */
class ObjectBodyAndAppendPartTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("BODY-OF an object is its fields as set-words")
    void theBodyIsSource() {
        assertThat(answerTo("(body-of make object! [a: 1 b: 2]) = [a: 1 b: 2]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("BODY-OF an empty object is an empty block")
    void theDegenerateBodyIsEmpty() {
        assertThat(answerTo("empty? body-of make object! []")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("SELF is not in the body")
    void selfIsLeftOut() {
        assertThat(answerTo("(body-of make object! [a: 1]) = [a: 1]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("APPEND/PART reads only that many items of the source")
    void appendPartCountsItems() {
        String from = " [a 1 b 2 c 3] ";
        assertThat(answerTo("empty? body-of append/part make object! []" + from + "1"))
                .isEqualTo("#(true)");
        assertThat(answerTo("(body-of append/part make object! []" + from + "2) = [a: 1]"))
                .isEqualTo("#(true)");
        assertThat(answerTo("(body-of append/part make object! []" + from + "3) = [a: 1]"))
                .as("a dangling key adds nothing")
                .isEqualTo("#(true)");
        assertThat(answerTo(
                "(body-of append/part make object! []" + from + "4) = [a: 1 b: 2]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("APPEND/PART onto an object that already has the field replaces it")
    void anExistingFieldIsOverwritten() {
        assertThat(answerTo(
                "(body-of append/part make object! [a: 10] [a 1 b 2 c 3] 4) = [a: 1 b: 2]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a negative /PART counts backwards from where the source is")
    void aNegativePartReadsBackwards() {
        assertThat(answerTo(
                "(body-of append/part make object! [] tail [a 1 b 2 c 3] -4) = [b: 2 c: 3]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("APPEND/DUP repeats the source that many times")
    void appendDupRepeats() {
        assertThat(answerTo("empty? body-of append/dup make object! [] [a 1] 0"))
                .as("none at all is a count a caller can compute")
                .isEqualTo("#(true)");
        assertThat(answerTo("(body-of append/dup make object! [] [a 1] 1) = [a: 1]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a plain APPEND onto an object is unaffected")
    void theOrdinaryCaseStillWorks() {
        assertThat(answerTo("(body-of append make object! [] [a 1 b 2]) = [a: 1 b: 2]"))
                .isEqualTo("#(true)");
    }
}
