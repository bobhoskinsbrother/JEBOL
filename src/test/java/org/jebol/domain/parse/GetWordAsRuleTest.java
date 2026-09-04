package org.jebol.domain.parse;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A get-word marks a place, so it cannot be asked to match anything.
 *
 * <p>Specified in {@code spec/parse.allium} and measured against a real R3
 * 3.22.1, which raises parse-rule.
 *
 * <p>Allowed through as COPY's rule it moved the position backwards and
 * left the capture reading a span that runs the wrong way. That failed as
 * a Java exception rather than as a REBOL error, which
 * {@code spec/embed.allium} says cannot happen.
 */
class GetWordAsRuleTest {

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
    @DisplayName("a get-word as COPY's rule is refused")
    void copyCannotCaptureAMark() {
        assertThat(errorIdOf(
                "data: \"aaabbb\" pos: head data parse data [some \"a\" copy var :pos]"))
                .isEqualTo("parse-rule");
    }

    @Test
    @DisplayName("a get-word naming nothing yet is refused the same way")
    void anUnsetMarkIsRefusedToo() {
        assertThat(errorIdOf("parse \"abcd\" [x: \"ab\" copy y :s thru \"abcd\"]"))
                .isEqualTo("parse-rule");
    }

    @Test
    @DisplayName("a get-word as SET's rule is refused")
    void setCannotCaptureAMarkEither() {
        assertThat(errorIdOf("data: \"ab\" pos: head data parse data [\"a\" set v :pos]"))
                .isEqualTo("parse-rule");
    }

    @Test
    @DisplayName("a get-word on its own still seeks back to the mark")
    void seekingBackIsUnaffected() {
        assertThat(answerTo("parse \"abc\" [x: \"a\" :x \"abc\"]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("COPY with an ordinary rule is unaffected")
    void captureStillWorks() {
        assertThat(answerTo("parse \"abc\" [copy v \"ab\" \"c\"] v = \"ab\""))
                .isEqualTo("#(true)");
    }
}
