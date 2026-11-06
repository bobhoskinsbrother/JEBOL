package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ExtensionsReadStringsAsStoredFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"zstd", "bzip2"})
    @DisplayName("A command that takes a string reads the UTF-8 R3 stores, from the character at its index, and /part counts octets")
    void readsTheUtf8FromItsIndex(String extension) {
        assertThat(answerTo("""
                m: import '%s
                round-trip: func [text] [m/decompress m/compress text]
                reduce [
                    round-trip "é"
                    round-trip "ěšč"
                    round-trip skip "ěšč" 1
                    round-trip %%ě
                    m/decompress m/compress/part "ěšč" 1
                ]""".formatted(extension))).isEqualTo("""
                [#{C3A9} #{C49BC5A1C48D} #{C5A1C48D} #{C49B} #{C4}]""");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"deflate", "zlib-ng"})
    @DisplayName("The deflate extensions read a string the same way")
    void readsTheUtf8ThroughDeflate(String extension) {
        assertThat(answerTo("""
                m: import '%s
                round-trip: func [text] [m/decompress m/compress text]
                reduce [
                    round-trip "é"
                    round-trip "ěšč"
                    round-trip skip "ěšč" 1
                    m/decompress m/compress/part "ěšč" 1
                ]""".formatted(extension))).isEqualTo("""
                [#{C3A9} #{C49BC5A1C48D} #{C5A1C48D} #{C4}]""");
    }
}
