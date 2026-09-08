package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Whose word a name in the binary dialect means.
 *
 * <p>{@code u-bincode.c} reaches a get-word with {@code if (IS_GET_WORD(next))
 * next = Get_Var(next);}, and {@code Get_Var} follows the binding the word
 * already carries. There is no context to pass it and no second chance to get
 * the name wrong.
 *
 * <p>Binding the block again on the way in is the mistake, and it hides
 * completely until a caller picks a name the borrowed library also uses.
 * Rebol's own ZIP encoder does: it keeps the central directory it is building
 * in a word called DIR and writes it with {@code BYTES :dir/buffer}, and DIR is
 * also {@code mezz-files.reb}'s directory-listing function. Rebound, the name
 * found that function, handed it to a code expecting bytes, and refused -- so
 * ENCODE 'ZIP could not write a single archive, whatever it was given.
 *
 * <p>Which is why the names in these tests are HEAD and DIR rather than
 * anything tidier. A test using a name of its own passes either way and proves
 * nothing.
 *
 * <p>Every expectation here was read off a real 3.22.5 before it was written.
 */
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
