package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectBodyBindingFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a spec written inside a function shares that function's locals")
    void aSpecWrittenInsideAFunction() {
        assertThat(answerTo("""
                caught: func [/local object][
                    object: none
                    make object! [made: type? object [inner: 1]]
                ]
                o: caught
                o/made""")).isEqualTo("#(none!)");
    }

    @Test
    @DisplayName("but a spec handed in from elsewhere shares nothing with the builder")
    void aSpecHandedInSharesNothing() {
        assertThat(answerTo("""
                build: func [spec /local object][
                    object: none
                    make object! spec
                ]
                o: build [made: type? object [inner: 1]]
                o/made""")).isEqualTo("#(object!)");
    }

    @Test
    @DisplayName("which is what FUNCTION does, its parameters being ordinary words")
    void whichIsWhatFunctionDoes() {
        assertThat(answerTo("""
                build: function [spec][make object! spec]
                o: build [made: type? object [inner: 1]]
                o/made""")).isEqualTo("#(object!)");
    }

    @Test
    @DisplayName("a word the object does declare is bound to the object")
    void aDeclaredWordIsBoundToTheObject() {
        assertThat(answerTo("""
                x: 10
                o: make object! [x: 1 y: x + 1]
                reduce [o/x o/y x]""")).isEqualTo("[1 2 10]");
    }

    @Test
    @DisplayName("and one it does not declare still reads the enclosing value")
    void anUndeclaredWordStillReadsTheOuterValue() {
        assertThat(answerTo("""
                outer: 10
                o: make object! [inner: outer + 1]
                o/inner""")).isEqualTo("11");
    }

    @Test
    @DisplayName("a function's own local reaches an object spec written inside it")
    void aFunctionsLocalReachesTheSpec() {
        assertThat(answerTo("""
                f: func [x][make object! [held: x]]
                o: f 7
                o/held""")).isEqualTo("7");
    }

    @Test
    @DisplayName("a prototype's fields are the object's own, so they bind too")
    void aPrototypesFieldsBindToo() {
        assertThat(answerTo("""
                base: make object! [a: 1]
                o: make base [b: a + 1]
                reduce [o/a o/b]""")).isEqualTo("[1 2]");
    }

    @Test
    @DisplayName("and the codec that found this reads a WAV back as an object")
    void theCodecReadsAnObject() {
        assertThat(answerTo("""
                b: binary #{}
                binary/write b [
                    #{52494646} UI32LE 36 #{57415645}
                    #{666D7420} UI32LE 16
                    UI16LE 1 UI16LE 1 UI32LE 8000 UI32LE 16000 UI16LE 2 UI16LE 16
                    #{64617461} UI32LE 4 #{01000200}
                ]
                snd: decode 'WAV b/buffer
                reduce [type? snd snd/rate snd/channels snd/bits]"""))
                .isEqualTo("[#(object!) 8000 1 16]");
    }
}
