package org.jebol.domain.read;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TRANSCODE/NEXT, and what /PART bounds.
 *
 * <p>Specified in {@code spec/load.allium} and measured against a real R3
 * 3.22.1.
 *
 * <p>/NEXT answers the first value and whatever is left, so a caller can
 * walk a source a value at a time without counting characters. Getting
 * "whatever is left" right is the whole of it: a value has to be taken as
 * far as it goes, the whitespace after it belongs to what follows, and a
 * bound on how much may be read is not a bound on what is left over.
 */
class TranscodeNextTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    /** The id of the error a snippet raises, or "no-error" if it raises none. */
    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("asking for a value where there is none is a failure")
    void anEmptySourceIsPastItsEnd() {
        assertThat(errorIdOf("transcode/next \"\"")).isEqualTo("past-end");
        assertThat(errorIdOf("transcode/one \"\"")).isEqualTo("past-end");
    }

    @Test
    @DisplayName("a value is taken as far as it goes")
    void theLongestReadingWins() {
        assertThat(answerTo("(transcode/part/next \"123]\" 2) = [12 \"3]\"]"))
                .isEqualTo("#(true)");
        assertThat(answerTo("(transcode/part/next \"123]\" 3) = [123 \"]\"]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the whitespace after a value belongs to what follows")
    void trailingSpaceIsGivenBack() {
        assertThat(answerTo("(transcode/next \"1 2\") = [1 \" 2\"]")).isEqualTo("#(true)");
        assertThat(answerTo("(transcode/part/next \"1 23]\" 3) = [1 \" 23]\"]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/PART bounds what is read, not what is left")
    void theBoundIsOnTheReading() {
        assertThat(answerTo("(transcode/part/next \"123]\" 1) = [1 \"23]\"]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a bound past the end reads everything it can")
    void aBoundBeyondTheSourceIsHarmless() {
        assertThat(answerTo("(transcode/part/next \"123]\" 10) = [123 \"]\"]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("what is left comes back as the kind that went in")
    void theRemainderKeepsItsType() {
        assertThat(answerTo(
                "(transcode/next to binary! \"1 + 1\") = reduce [1 to binary! \" + 1\"]"))
                .isEqualTo("#(true)");
        assertThat(answerTo("second transcode/next to binary! \"[1 + 1]\""))
                .as("nothing left is an empty binary, not an empty string")
                .isEqualTo("#{}");
    }

    @Test
    @DisplayName("reading a whole source is unaffected")
    void theOrdinaryCallStillWorks() {
        assertThat(answerTo("(transcode \"1 2\") = [1 2]")).isEqualTo("#(true)");
        assertThat(answerTo("(transcode/one \"1 2\") = 1")).isEqualTo("#(true)");
    }
}
