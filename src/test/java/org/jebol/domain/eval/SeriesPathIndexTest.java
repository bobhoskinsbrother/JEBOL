package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeriesPathIndexTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a string gives the character at that position")
    void aStringIndexGivesACharacter() {
        assertThat(answerTo("s: \"12\" s/1")).isEqualTo("#\"1\"");
    }

    @Test
    @DisplayName("the last position of a string works")
    void theLastPositionWorks() {
        assertThat(answerTo("s: \"12\" s/2")).isEqualTo("#\"2\"");
    }

    @Test
    @DisplayName("past the end of a string gives none")
    void pastTheEndOfAStringGivesNone() {
        assertThat(answerTo("s: \"12\" mold s/9")).isEqualTo("\"_\"");
    }

    @Test
    @DisplayName("a decimal index into a string truncates")
    void aDecimalStringIndexTruncates() {
        assertThat(answerTo("s: \"12\" reduce [s/1.0 s/1.6 s/2.0 s/2.6]"))
                .isEqualTo("[#\"1\" #\"1\" #\"2\" #\"2\"]");
    }

    @Test
    @DisplayName("a binary gives the byte at that position")
    void aBinaryIndexGivesAByte() {
        assertThat(answerTo("b: #{0A0B} b/1")).isEqualTo("10");
    }

    @Test
    @DisplayName("past the end of a binary gives none")
    void pastTheEndOfABinaryGivesNone() {
        assertThat(answerTo("b: #{0A0B} mold b/9")).isEqualTo("\"_\"");
    }

    @Test
    @DisplayName("a block still works, which is the case that was already right")
    void aBlockIndexIsUnaffected() {
        assertThat(answerTo("b: [1 2] b/1")).isEqualTo("1");
        assertThat(answerTo("b: [1 2] mold b/9")).isEqualTo("\"_\"");
    }
}
