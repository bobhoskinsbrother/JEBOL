package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TheBodyOfAModuleFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("the body is the fields and their values, as an object's is")
    void theBodyIsTheFieldsAndTheirValues() {
        assertThat(answerTo("""
                m: module [] [a: 1 2]
                [lib-local: #(object![]) a: 1] = body-of m""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("LIB-LOCAL comes first, as a set-word, holding an empty object")
    void libLocalComesFirst() {
        assertThat(answerTo("""
                m: module [] [a: 1 2]
                reduce [
                    to word! first body-of m
                    type? first body-of m
                    object? second body-of m
                    empty? second body-of m
                ]""")).isEqualTo("[lib-local #(set-word!) #(true) #(true)]");
    }

    @Test
    @DisplayName("a module that defines nothing has LIB-LOCAL and nothing else")
    void aModuleThatDefinesNothingHasLibLocalAndNothingElse() {
        assertThat(answerTo("body-of module [] []")).isEqualTo("""
                [
                    lib-local: make object! [
                    ]
                ]""");
    }

    @Test
    @DisplayName("each field stands on its own line")
    void eachFieldStandsOnItsOwnLine() {
        assertThat(answerTo("body-of module [] [alpha: 1 beta: 2]")).isEqualTo("""
                [
                    lib-local: make object! [
                    ]
                    alpha: 1
                    beta: 2
                ]""");
    }

    @Test
    @DisplayName("exporting a word does not change what the body says")
    void exportingAWordDoesNotChangeTheBody() {
        assertThat(answerTo("""
                [lib-local: #(object![]) a: 1]
                    = body-of module [exports: [a]] [a: 1 2]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("REFLECT with the BODY word answers the same thing")
    void reflectAnswersTheSameThing() {
        assertThat(answerTo("""
                m: module [] [a: 1 2]
                (body-of m) = (reflect m 'body)""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("and a module made straight from a header and a body answers it too")
    void aModuleMadeStraightFromAHeaderAndABodyAnswersItToo() {
        assertThat(answerTo("""
                (body-of module [] [a: 1 2])
                    = (body-of make module! reduce [[] [a: 1 2]])"""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the keys and the values line up with it")
    void theKeysAndTheValuesLineUpWithIt() {
        assertThat(answerTo("""
                m: module [] [alpha: 1 beta: 2]
                reduce [keys-of m  fourth body-of m  sixth body-of m]"""))
                .isEqualTo("[[lib-local alpha beta] 1 2]");
    }
}
