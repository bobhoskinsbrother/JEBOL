package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BincodeNamesFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("raised: try [" + source + "] raised/id");
    }

    @Test
    @DisplayName("a get-word finds the caller's field, not the library's function")
    void aGetWordFindsTheCallersFieldNotTheLibrarys() {
        assertThat(answerTo("""
                keeper: context [
                    head: none
                    run: does [head: 5 b: binary 10 binary/write b [UI8 :head] b/buffer]
                ]
                keeper/run""")).isEqualTo("#{05}");
    }

    @Test
    @DisplayName("a get-path's head finds the caller's field too")
    void aGetPathFindsTheCallersFieldNotTheLibrarys() {
        assertThat(answerTo("""
                keeper: context [
                    dir: none
                    run: does [
                        dir: context [size: 5]
                        b: binary 10
                        binary/write b [UI8 :dir/size]
                        b/buffer
                    ]
                ]
                keeper/run""")).isEqualTo("#{05}");
    }

    @Test
    @DisplayName("a name the library does not have is unaffected")
    void aNameTheLibraryDoesNotHaveIsUnaffected() {
        assertThat(answerTo("""
                keeper: context [
                    spare-name: none
                    run: does [
                        spare-name: 9 b: binary 10
                        binary/write b [UI8 :spare-name] b/buffer
                    ]
                ]
                keeper/run""")).isEqualTo("#{09}");
    }

    @Test
    @DisplayName("a function's local is found the same way a field is")
    void aFunctionsLocalIsFoundToo() {
        assertThat(answerTo("""
                f: func [/local dir b][
                    dir: 7 b: binary 10 binary/write b [UI8 :dir] b/buffer
                ]
                f""")).isEqualTo("#{07}");
    }

    @Test
    @DisplayName("and a function's local reached through a path")
    void aFunctionsLocalGetPathIsFoundToo() {
        assertThat(answerTo("""
                g: func [/local dir b][
                    dir: context [size: 8]
                    b: binary 10
                    binary/write b [UI8 :dir/size]
                    b/buffer
                ]
                g""")).isEqualTo("#{08}");
    }

    @Test
    @DisplayName("the same holds when reading rather than writing")
    void theSameHoldsWhenReading() {
        assertThat(answerTo("""
                keeper: context [
                    dir: none
                    run: does [dir: 3 binary/read #{0A0B0C0D} [AT :dir UI8]]
                ]
                keeper/run""")).isEqualTo("[12]");
    }

    @Test
    @DisplayName("the shape Rebol's own ZIP encoder writes its directory with")
    void theShapeRebolsZipEncoderWrites() {
        assertThat(answerTo("""
                h: func [/local dir bin][
                    dir: binary 100
                    binary/write dir [UI8 1 UI8 2]
                    bin: binary 100
                    binary/write bin [BYTES :dir/buffer #{FFFF}]
                    bin/buffer
                ]
                h""")).isEqualTo("#{0102FFFF}");
    }

    @Test
    @DisplayName("a name nothing holds a value for is refused")
    void aNameWithNoValueIsRefused() {
        assertThat(errorIdFrom("""
                b: binary 10
                binary/write b [UI8 :nothing-holds-this]""")).isEqualTo("dialect");
    }
}
