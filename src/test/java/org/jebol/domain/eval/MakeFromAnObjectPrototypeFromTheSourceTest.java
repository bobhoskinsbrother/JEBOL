package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class MakeFromAnObjectPrototypeFromTheSourceTest {

    private String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("make with an object prototype and none is a separate copy of the prototype")
    void noneMakesACopy() {
        assertThat(answerTo("""
                o: object [a: 1]
                p: make o none
                p/a: 2
                reduce [o/a p/a same? o p]""")).isEqualTo("[1 2 #(false)]");
    }

    @ParameterizedTest(name = "make o {0} is bad-make-arg naming object! and the prototype")
    @ValueSource(strings = {"5", "{b: 2}", "quote (b: 2)", "#{00}", "'x", "1.5", "#(true)", "2x3"})
    @DisplayName("make with an object prototype refuses anything but a block, none or an object")
    void anythingElseIsRefused(String offered) {
        assertThat(answerTo("""
                o: object [a: 1]
                e: try [make o %s]
                reduce [e/id e/arg1 same? e/arg2 o]""".formatted(offered)))
                .isEqualTo("[bad-make-arg #(object!) #(true)]");
    }

    @ParameterizedTest(name = "make object! of {0} molds as {1}")
    @CsvSource(delimiter = '|', value = {
            "make map! [a 2 b 3]    | make object! [^/    a: 2^/    b: 3^/]",
            "make map! []           | make object! [^/]",
            "make map! [{a} 2]      | make object! [^/]",
            "make map! [1 2]        | make object! [^/]",
            "make map! [a: 2]       | make object! [^/    a: 2^/]",
            "make map! [A 1 b none] | make object! [^/    A: 1^/    b: 'none^/]",
            "make map! [self 2]     | make object! [^/    self: 2^/]",
    })
    @DisplayName("make object! of a map takes the word keys, as a real 3.22.5 does")
    void makeObjectOfAMapTakesItsWordKeys(String map, String molded) {
        assertThat(answerTo("""
                {%s} = mold make object! %s""".formatted(molded, map)))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("make object! of a map leaves out a key whose value is none")
    void aNoneValueIsLeftOut() {
        assertThat(answerTo("""
                m: make map! [a 1]
                m/b: none
                words-of make object! m""")).isEqualTo("[a]");
    }

    @Test
    @DisplayName("make object! of a map shares the map's values rather than copying them")
    void theValuesAreShared() {
        assertThat(answerTo("""
                m: make map! [a [x y]]
                p: make object! m
                same? p/a select m 'a""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("to object! of a map is still refused")
    void toObjectOfAMapIsRefused() {
        assertThat(answerTo("""
                e: try [to object! make map! [a 2]]
                e/id""")).isEqualTo("bad-make-arg");
    }

    @Test
    @DisplayName("make with an object prototype and a block still adds the block's fields")
    void aBlockStillAddsFields() {
        assertThat(answerTo("""
                o: object [a: 1]
                p: make o [b: 2]
                reduce [p/a p/b]""")).isEqualTo("[1 2]");
    }
}
