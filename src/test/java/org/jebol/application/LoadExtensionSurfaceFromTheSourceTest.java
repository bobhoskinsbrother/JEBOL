package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoadExtensionSurfaceFromTheSourceTest {

    private static String errorIdOf(String source) {
        Interpreter interpreter = Interpreter.create();
        String wrapped = "e: try [" + source + "] either error? e [e/id] ['no-error]";
        interpreter.defineFreshWordsIn(wrapped);
        return interpreter.display(interpreter.run(wrapped));
    }

    @Test
    @DisplayName("a file name is no-extension, as r3 answers a library it cannot open")
    void aFileIsNoExtension() {
        assertThat(errorIdOf("load-extension %a.so")).isEqualTo("no-extension");
    }

    @Test
    @DisplayName("a binary without /DISPATCH is invalid-arg, as r3 answers")
    void aBinaryIsAnInvalidArgument() {
        assertThat(errorIdOf("load-extension #{00}")).isEqualTo("invalid-arg");
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
