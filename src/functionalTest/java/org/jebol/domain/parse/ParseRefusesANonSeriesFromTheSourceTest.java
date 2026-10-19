package org.jebol.domain.parse;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParseRefusesANonSeriesFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String readTheErrorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("an integer is refused, and the error says what was wrong")
    void anintegerIsRefused() {
        assertThat(readTheErrorIdFrom("parse 1 [end]")).isEqualTo("expect-arg");
        assertThat(answerTo("""
                e: try [parse 1 [end]]
                mold reduce [e/arg1 e/arg2 e/arg3]"""))
                .as("R3 names the function, the parameter and the datatype")
                .isEqualTo("\"[parse input #(integer!)]\"");
    }

    @Test
    @DisplayName("and so is none, which is the one a script hits by accident")
    void refusesNone() {
        assertThat(readTheErrorIdFrom("parse none [end]")).isEqualTo("expect-arg");
    }

    @Test
    @DisplayName("a map is refused, though it holds things")
    void amapIsRefused() {
        assertThat(readTheErrorIdFrom("parse make map! [] [end]")).isEqualTo("expect-arg");
    }

    @Test
    @DisplayName("a bitset is refused too")
    void abitsetIsRefused() {
        assertThat(readTheErrorIdFrom("parse make bitset! \"a\" [end]")).isEqualTo("expect-arg");
    }

    @Test
    @DisplayName("a string, a block and a binary are what it takes")
    void thethreeItTakes() {
        assertThat(readTheErrorIdFrom("parse \"ab\" [end]")).isEqualTo("no-error");
        assertThat(readTheErrorIdFrom("parse [1] [end]")).isEqualTo("no-error");
        assertThat(readTheErrorIdFrom("parse #{00} [end]")).isEqualTo("no-error");
    }
}
