package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoadExtensionSurfaceFromTheSourceTest {

    private static String readTheErrorIdOf(String source) {
        Interpreter interpreter = Interpreter.create();
        String wrapped = "e: try [" + source + "] either error? e [e/id] ['no-error]";
        interpreter.defineNewWordsIn(wrapped);
        return interpreter.display(interpreter.run(wrapped));
    }

    @Test
    @DisplayName("a file name is no-extension, as r3 answers a library it cannot open")
    void refusesAFileAsNoExtension() {
        assertThat(readTheErrorIdOf("load-extension %a.so")).isEqualTo("no-extension");
    }

    @Test
    @DisplayName("a binary without /DISPATCH is invalid-arg, as r3 answers")
    void refusesABinaryAsAnInvalidArgument() {
        assertThat(readTheErrorIdOf("load-extension #{00}")).isEqualTo("invalid-arg");
    }

    @Test
    @DisplayName("a string is neither form and is refused as an argument")
    void refusesAStringAsAnArgument() {
        assertThat(readTheErrorIdOf("load-extension \"a.so\"")).isEqualTo("expect-arg");
    }

    @Test
    @DisplayName("none is refused as an argument")
    void refusesNoneAsAnArgument() {
        assertThat(readTheErrorIdOf("load-extension none")).isEqualTo("expect-arg");
    }

    @Test
    @DisplayName("/dispatch takes a function argument, checked before the refusal")
    void dispatchChecksItsArgument() {
        assertThat(readTheErrorIdOf("load-extension/dispatch %a.so 5"))
                .isEqualTo("expect-arg");
    }
}
