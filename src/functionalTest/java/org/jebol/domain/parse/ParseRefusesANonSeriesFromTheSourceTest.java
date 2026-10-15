package org.jebol.domain.parse;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
