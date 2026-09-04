package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two things a module needs before it can be built, neither of them about
 * modules.
 *
 * <p>{@code protect/hide/words} over a block hides each word the block holds,
 * bindings and all. That is how a module hides the fields its body marked
 * HIDDEN: {@code if block? hidden [protect/hide/words hidden]} in
 * {@code sys-base.reb}, where the block is the words gathered while the body
 * was read. JEBOL took a bare word and refused a block, so every module with a
 * HIDDEN in it failed to build.
 *
 * <p>{@code system/ports/output} is the console port, and it was none. Nothing
 * writes through it here -- the output port does that -- but REBOL code asks
 * it how wide the terminal is, and HELP asks on its first line. So calling
 * HELP answered "query does not allow none!" instead of helping, which is what
 * stopped the module test file on its third step, three lines above the first
 * assertion.
 */
class HiddenWordsAndTheConsolePortFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("failure: try [" + source + "] failure/id");
    }

    @Nested
    @DisplayName("hiding a block of words")
    class HidingABlock {

        @Test
        @DisplayName("each word the block names goes out of the object it belongs to")
        void eachWordGoesOut() {
            assertThat(answerTo("""
                    o: make object! [a: 1 b: 2]
                    protect/hide/words bind [a] o
                    words-of o""")).isEqualTo("[b]");
        }

        @Test
        @DisplayName("and the value is still there for the object's own code")
        void theValueIsStillThere() {
            assertThat(answerTo("""
                    o: make object! [a: 1 b: does [a + 1]]
                    protect/hide/words bind [a] o
                    reduce [words-of o o/b]""")).isEqualTo("[[b] 2]");
        }

        @Test
        @DisplayName("hiding one bare word still works, which is the older spelling")
        void oneBareWordStillWorks() {
            assertThat(answerTo("""
                    p: make object! [c: 3]
                    protect/hide in p 'c
                    words-of p""")).isEqualTo("[]");
        }

        @Test
        @DisplayName("and /HIDE without a word or a block of them is still refused")
        void hideWithoutWords() {
            assertThat(errorIdFrom("""
                    protect/hide [1 2 3]""")).isEqualTo("bad-refines");
        }
    }

    @Nested
    @DisplayName("a module whose body hides something")
    class TheHidingModule {

        @Test
        @DisplayName("the hidden word is not one of the module's own")
        void theHiddenWordIsNotListed() {
            assertThat(answerTo("""
                    m: module [] [hidden a: 1 b: does [a + 1]]
                    words-of m""")).isEqualTo("[lib-local b]");
        }

        @Test
        @DisplayName("reaching it from outside is a mistake, and from inside is not")
        void reachingItFromOutside() {
            assertThat(answerTo("""
                    m: module [] [hidden a: 1 b: does [a + 1]]
                    reduce [error? try [m/a] m/b]""")).isEqualTo("[#(true) 2]");
        }

        @Test
        @DisplayName("and a module that hides nothing lists everything")
        void aModuleThatHidesNothing() {
            assertThat(answerTo("""
                    m: module [] [a: 1]
                    reduce [words-of m m/a]""")).isEqualTo("[[lib-local a] 1]");
        }
    }

    @Nested
    @DisplayName("the console port, which QUERY measures")
    class TheConsolePort {

        @Test
        @DisplayName("it is open, and it is a console")
        void itIsOpenAndAConsole() {
            assertThat(answerTo("""
                    reduce [port? system/ports/output system/ports/output/spec/scheme]"""))
                    .isEqualTo("[#(true) console]");
        }

        @Test
        @DisplayName("the width is eighty and the other three are nothing")
        void theFourMeasurements() {
            assertThat(answerTo("""
                    collect [
                        foreach field [window-cols window-rows buffer-cols buffer-rows][
                            keep query system/ports/output field
                        ]
                    ]""")).isEqualTo("[80 0 0 0]");
        }

        @Test
        @DisplayName("asking for a measurement it has not got names the word")
        void askingForSomethingElse() {
            assertThat(errorIdFrom("""
                    query system/ports/output 'nonsense""")).isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("and HELP works, which is what needed the width")
        void helpWorks() {
            assertThat(answerTo("""
                    unset? ? system/ports/output""")).isEqualTo("#(true)");
        }
    }
}
