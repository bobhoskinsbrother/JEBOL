package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * An object's body is bound to the object's own words and to nothing else.
 *
 * <p>{@code Do_Bind_Block(obj, arg)} is {@code Bind_Block(frame, block,
 * BIND_DEEP)}, and without {@code BIND_ALL} that means "only bind words found
 * in the frame". Every other word in the spec keeps whatever binding it
 * arrived with.
 *
 * <p>JEBOL bound every word the enclosing chain knew, and that chain runs
 * through call frames. So a word in an object spec could be captured by a
 * local of some function further out that happened to share its spelling --
 * and the spellings that matter are FUNCTION's own parameters, because
 * FUNCTION is what builds nearly every REBOL function there is.
 *
 * <p>What that cost: Rebol's WAV codec ends with {@code object compose/only
 * [...]}, and OBJECT was rebound to FUNCTION's {@code /with object}
 * parameter, a slot holding none. So OBJECT was never called, the composed
 * block fell out as the answer, and {@code load %sound.wav} came back a block.
 */
class ObjectBodyBindingFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    /**
     * The shadowing that is right, beside the one that is not.
     *
     * <p>A spec written inside a function shares that function's locals,
     * because the block is part of the body and was bound with it. So the
     * first of these really does see NONE, and a real 3.22 says so too.
     *
     * <p>The second is the whole difference. Its spec was written somewhere
     * else and handed in, so nothing about the function that builds the
     * object may reach into it. JEBOL bound it to the builder's frame anyway,
     * and the builder is FUNCTION, whose parameters are spelled SPEC, BODY,
     * OBJECT, WORDS, WITH and EXTERN.
     */
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
