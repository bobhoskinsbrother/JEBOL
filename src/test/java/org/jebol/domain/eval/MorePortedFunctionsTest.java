package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Four more functions ported off the backlog, and the crash one of them found.
 *
 * <p>Specified in {@code spec/natives.allium} and measured against a real
 * R3 3.22.1, whose own definitions were read out of the binary.
 */
class MorePortedFunctionsTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("ARCTANGENT2 gives the angle of a point, in degrees")
    void theAngleOfAPoint() {
        assertThat(answerTo("(arctangent2 1x1) = 45.0")).isEqualTo("#(true)");
        assertThat(answerTo("(arctangent2 0x1) = 90.0")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("it goes to -180 rather than round to 225")
    void theAngleIsSigned() {
        assertThat(answerTo("(arctangent2 -1x-1) = -135.0")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/RADIANS answers the same angle the other way")
    void theAngleCanBeInRadians() {
        assertThat(answerTo("((arctangent2/radians 1x1) - (pi / 4)) < 1E-13"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("SPLIT-LINES takes a carriage return with the newline")
    void aCarriageReturnGoesWithTheNewline() {
        assertThat(answerTo("(split-lines \"a^/b^M^/c\") = [\"a\" \"b\" \"c\"]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("one line is one line, and nothing is no lines")
    void theDegenerateStrings() {
        assertThat(answerTo("(split-lines \"abc\") = [\"abc\"]")).isEqualTo("#(true)");
        assertThat(answerTo("empty? split-lines \"\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("WILDCARD? tells a pattern from a name")
    void aPatternIsNotAName() {
        assertThat(answerTo("wildcard? %a*.txt")).isEqualTo("#(true)");
        assertThat(answerTo("wildcard? %a?.txt")).isEqualTo("#(true)");
        assertThat(answerTo("wildcard? %a.txt")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("WRAP makes the set-words in a block local to it")
    void wrapKeepsItsAssignmentsIn() {
        assertThat(answerTo("x: 1 wrap [x: 2] x")).isEqualTo("1");
        assertThat(answerTo("wrap [1 + 1]"))
                .as("and it still answers what the block answered")
                .isEqualTo("2");
    }

    @Test
    @DisplayName("MAKE OBJECT! with a number gives an empty object")
    void aNumberIsASizeAndNotABody() {
        assertThat(answerTo("empty? words-of make object! 0")).isEqualTo("#(true)");
        assertThat(answerTo("object? make object! 0")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("MAKE OBJECT! with a body is unaffected")
    void theOrdinaryMakeStillWorks() {
        assertThat(answerTo("(words-of make object! [a: 1]) = [a]")).isEqualTo("#(true)");
    }
}
