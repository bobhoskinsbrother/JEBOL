package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A binary is a series like the others.
 *
 * <p>Specified in {@code spec/natives.allium}, confirmed against a real R3.
 *
 * <p>APPEND, INSERT, SWAP and REMOVE-EACH were each written for blocks and
 * strings and left binaries out. That refused ordinary calls, and where
 * the binary was protected it reported a type error in place of the
 * refusal -- which reads as though the protection was never reached.
 */
class BinariesAreSeriesTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("APPEND puts a binary on the end of a binary")
    void appendTakesABinary() {
        assertThat(answerTo("b: #{0102} append b #{0304} mold b"))
                .isEqualTo("\"#{01020304}\"");
    }

    @Test
    @DisplayName("SWAP exchanges the bytes two binaries stand on")
    void swapTakesBinaries() {
        assertThat(answerTo("a: #{0102} b: #{0304} swap a b mold reduce [a b]"))
                .isEqualTo("\"[#{0302} #{0104}]\"");
    }

    @Test
    @DisplayName("REMOVE-EACH walks a binary's bytes")
    void removeEachTakesABinary() {
        assertThat(answerTo("b: #{010203} remove-each x b [x < 3] mold b"))
                .isEqualTo("\"#{03}\"");
    }

    @Test
    @DisplayName("REMOVE-EACH walks a string's characters")
    void removeEachTakesAString() {
        assertThat(answerTo("s: \"abc\" remove-each x s [x = #\"b\"] s"))
                .isEqualTo("\"ac\"");
    }

    @Test
    @DisplayName("each of them refuses a protected binary")
    void eachRefusesAProtectedBinary() {
        assertThat(errorIdOf("b: protect #{cafe} append b #{0bad}")).isEqualTo("protected");
        assertThat(errorIdOf("b: protect #{cafe} insert b #{0bad}")).isEqualTo("protected");
        assertThat(errorIdOf("b: protect #{cafe} swap b #{0bad}")).isEqualTo("protected");
    }

    @Test
    @DisplayName("REMOVE-EACH refuses even when it would remove nothing")
    void theGuardDoesNotDependOnTheData() {
        assertThat(errorIdOf("b: protect #{cafe} remove-each a b [a < 3]"))
                .isEqualTo("protected");
    }

    @Test
    @DisplayName("the three container routes into a protected object all say protected")
    void everyContainerRouteSaysProtected() {
        String guarded = "o: make object! [a: 1] ignore: protect/deep o ";
        assertThat(errorIdOf(guarded + "append o [b: 2]")).isEqualTo("protected");
        assertThat(errorIdOf(guarded + "put o 'a 2")).isEqualTo("protected");
        assertThat(errorIdOf(guarded + "resolve o make object! [a: 99]"))
                .isEqualTo("protected");
    }

    @Test
    @DisplayName("PUT on a protected object is protected, not locked-word")
    void putNamesTheRoute() {
        assertThat(errorIdOf("o: make object! [a: 1] protect o put o 'a 2"))
                .isEqualTo("protected");
        assertThat(errorIdOf("o: make object! [a: 1] protect o o/a: 2"))
                .as("an assignment through a name is the other route")
                .isEqualTo("locked-word");
    }
}
