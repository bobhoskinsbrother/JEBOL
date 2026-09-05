package org.jebol.domain.parse;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PARSE takes a series, and refuses anything else rather than answering false.
 *
 * <p>{@code natives.reb} declares {@code input [series!]}, and a value outside
 * that typeset never reaches the parser: R3 raises {@code expect-arg} naming
 * the function, the parameter and the datatype it was handed. JEBOL declared
 * the parameter with no typeset at all, so {@code parse 1 [end]} ran and
 * answered false -- which a caller reads as "it did not match" rather than
 * "that was not a thing to parse".
 *
 * <p>Answering false to a question that should have been refused is the shape
 * worth naming: a rule that never matches and a value that cannot be matched
 * are different facts, and one of them is a defect in the caller's script.
 *
 * <p>{@code series!} is narrower than "has a position". A map, a bitset and a
 * typeset all carry contents and none of them is parseable, which is why the
 * set is written out from Rebol's own typeset rather than reused from the
 * broader one JEBOL keeps for TAIL? and its kin.
 */
class ParseRefusesANonSeriesFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("an integer is refused, and the error says what was wrong")
    void anintegerIsRefused() {
        assertThat(errorIdFrom("parse 1 [end]")).isEqualTo("expect-arg");
        assertThat(answerTo("""
                e: try [parse 1 [end]]
                mold reduce [e/arg1 e/arg2 e/arg3]"""))
                .as("R3 names the function, the parameter and the datatype")
                .isEqualTo("\"[parse input #(integer!)]\"");
    }

    @Test
    @DisplayName("and so is none, which is the one a script hits by accident")
    void noneIsRefused() {
        assertThat(errorIdFrom("parse none [end]")).isEqualTo("expect-arg");
    }

    @Test
    @DisplayName("a map is refused, though it holds things")
    void amapIsRefused() {
        assertThat(errorIdFrom("parse make map! [] [end]")).isEqualTo("expect-arg");
    }

    @Test
    @DisplayName("a bitset is refused too")
    void abitsetIsRefused() {
        assertThat(errorIdFrom("parse make bitset! \"a\" [end]")).isEqualTo("expect-arg");
    }

    @Test
    @DisplayName("a string, a block and a binary are what it takes")
    void thethreeItTakes() {
        assertThat(errorIdFrom("parse \"ab\" [end]")).isEqualTo("no-error");
        assertThat(errorIdFrom("parse [1] [end]")).isEqualTo("no-error");
        assertThat(errorIdFrom("parse #{00} [end]")).isEqualTo("no-error");
    }
}
