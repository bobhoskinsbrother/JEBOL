package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class FuncAndFunctionAreRebolsOwnTest {

    private static String answerTo(String source) {
        return Interpreter.create().run(source).display();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"func", "function", "funct"})
    @DisplayName("Rebol defines it in its own library, so the catalogue of natives does not list it")
    void notANative(String word) {
        assertThat(answerTo("none? find system/catalog/natives '" + word))
                .isEqualTo("#(true)");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"func", "function", "funct", "does", "has", "closure"})
    @DisplayName("what a script holds is a function!, as Rebol's library leaves it")
    void aFunctionOfRebolsOwn(String word) {
        assertThat(answerTo("type? :" + word)).isEqualTo("#(function!)");
    }

    @Test
    @DisplayName("FUNCTION declares what base-funcs.reb declares, refinements and all")
    void functionDeclaresWhatTheLibraryDeclares() {
        assertThat(answerTo("words-of :function"))
                .isEqualTo("[spec body /with object /extern words]");
    }

    @Test
    @DisplayName("FUNCT is the same function as FUNCTION, not a copy without /with")
    void functIsFunction() {
        assertThat(answerTo("same? :funct :function")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("FUNCT/WITH finds the object's words, as Rebol's own suite asserts")
    void functWithFindsTheObjectsWords() {
        assertThat(answerTo("""
                fun: funct/with [i [integer!]] [blk: [1 2 3] return pick data i] [data: ["ab" "cd"]]
                equal? "ab" fun 1"""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("FUNC makes a function whose arguments bind")
    void funcMakesAWorkingFunction() {
        assertThat(answerTo("f: func [x] [x * 2] f 21")).isEqualTo("42");
    }

    @Test
    @DisplayName("FUNCTION makes its set-words local, leaving the caller's alone")
    void functionMakesSetWordsLocal() {
        assertThat(answerTo("x: 1 f: function [] [x: 2] f x")).isEqualTo("1");
    }

    @Test
    @DisplayName("FUNCTION/EXTERN leaves a named word outside")
    void functionExternLeavesAWordOutside() {
        assertThat(answerTo("x: 1 f: function/extern [] [x: 2] [x] f x")).isEqualTo("2");
    }

    @Test
    @DisplayName("FUNCTION/WITH binds the body into the object it is given")
    void functionWithBindsIntoAnObject() {
        assertThat(answerTo("""
                counter: make object! [n: 0]
                bump: function/with [] [n: n + 1] counter
                bump bump counter/n"""))
                .isEqualTo("2");
    }
}
