package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MOLD/ALL and LOAD, read from {@code load-test.r3}: a positioned series
 * round-trips through the construct form, and a binary that is not valid
 * UTF-8 is refused with invalid-chars rather than read as replacement
 * characters.
 */
class LoadAndMoldFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("MOLD/ALL keeps the series position, and LOAD reads it back")
    void moldAllKeepsThePosition() {
        assertThat(answerTo("""
                v: load/all mold/all next {123}
                reduce [block? v  v/1 = {23}  (head v/1) = {123}]"""))
                .isEqualTo("[#(true) #(true) #(true)]");
    }

    @Test
    @DisplayName("MOLD/ALL of a head series is the plain mold")
    void moldAllOfAHeadSeriesIsPlain() {
        assertThat(answerTo("""
                (mold/all {123}) = mold {123}""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("LOAD of bytes that are not UTF-8 raises invalid-chars")
    void loadOfInvalidBytesRaises() {
        assertThat(errorIdOf("""
                load #{789DE3}""")).isEqualTo("invalid-chars");
    }

    @Test
    @DisplayName("LOAD of valid UTF-8 bytes still reads")
    void loadOfValidBytesStillReads() {
        assertThat(answerTo("""
                load to-binary {1 2 3}""")).isEqualTo("[1 2 3]");
    }
}
