package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class WordsRebolWritesInRebolTest {

    private static String answerTo(String source) {
        return Interpreter.create().run(source).display();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "any-block?", "any-function?", "any-object?", "any-path?", "any-string?",
        "any-word?", "quote", "scalar?", "series?", "use", "exists?", "make-dir",
        "load", "ask", "input", "split"})
    @DisplayName("Rebol's library defines it, so the catalogue of natives does not list it")
    void notANative(String word) {
        assertThat(answerTo("none? find system/catalog/natives '" + word))
                .isEqualTo("#(true)");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "any-block?", "any-function?", "any-object?", "any-path?", "any-string?",
        "any-word?", "quote", "scalar?", "series?", "use", "exists?", "make-dir",
        "load", "ask", "input", "split"})
    @DisplayName("what a script holds is the function! Rebol's library made")
    void aFunctionOfRebolsOwn(String word) {
        assertThat(answerTo("type? :" + word)).isEqualTo("#(function!)");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"any-type?", "copyable?", "immediate?", "internal?"})
    @DisplayName("a typeset predicate Rebol has no definition for is still JEBOL's native")
    void aPredicateOnlyJebolOffersStays(String word) {
        assertThat(answerTo("type? :" + word)).isEqualTo("#(native!)");
    }

    @Test
    @DisplayName("NUMBER? is a native in Rebol and stays one")
    void numberIsANative() {
        assertThat(answerTo("type? :number?")).isEqualTo("#(native!)");
    }

    @Test
    @DisplayName("and each still answers as Rebol's does")
    void theyStillWork() {
        assertThat(answerTo("""
                reduce [
                    any-block? [a] any-word? 'a series? "s" scalar? 1
                    quote (1 + 2)
                    use [x] [x: 5 x * 2]
                    split "a,b" ","
                    load "1 2"
                ]"""))
                .isEqualTo("""
                        [#(true) #(true) #(true) #(true) (1 + 2) 10 ["a" "b"] [1 2]]""");
    }
}
