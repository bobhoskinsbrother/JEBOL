package org.jebol.domain.parse;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GetWordAsRuleTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

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
