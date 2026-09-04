package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LOAD-EXTENSION declares the C's surface from {@code natives.reb} even
 * though {@code HostServiceGrantTest} pins that it always refuses.
 */
class LoadExtensionSurfaceFromTheSourceTest {

    private static String errorIdOf(String source) {
        Interpreter interpreter = Interpreter.create();
        String wrapped = "e: try [" + source + "] either error? e [e/id] ['no-error]";
        interpreter.defineFreshWordsIn(wrapped);
        return interpreter.display(interpreter.run(wrapped));
    }

    @Test
    @DisplayName("a file name reaches the refusal")
    void aFileReachesTheRefusal() {
        assertThat(errorIdOf("load-extension %a.so")).isEqualTo("no-service");
    }

    @Test
    @DisplayName("a binary is UTF-8 source in the C, and reaches the refusal too")
    void aBinaryReachesTheRefusal() {
        assertThat(errorIdOf("load-extension #{00}")).isEqualTo("no-service");
    }

    @Test
    @DisplayName("a string is neither form and is refused as an argument")
    void aStringIsRefusedAsAnArgument() {
        assertThat(errorIdOf("load-extension \"a.so\"")).isEqualTo("expect-arg");
    }

    @Test
    @DisplayName("none is refused as an argument")
    void noneIsRefusedAsAnArgument() {
        assertThat(errorIdOf("load-extension none")).isEqualTo("expect-arg");
    }

    @Test
    @DisplayName("/dispatch takes a function argument, checked before the refusal")
    void dispatchChecksItsArgument() {
        assertThat(errorIdOf("load-extension/dispatch %a.so 5"))
                .isEqualTo("expect-arg");
    }
}
