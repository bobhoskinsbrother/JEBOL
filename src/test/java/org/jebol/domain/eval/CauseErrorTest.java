package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAUSE-ERROR, FUNCO and MAP, ported off the backlog.
 *
 * <p>Specified in {@code spec/natives.allium} and measured against a real
 * R3 3.22.1, whose own definitions were read out of the binary.
 *
 * <p>Porting CAUSE-ERROR found that MAKE ERROR! was reading its spec as
 * written rather than evaluating it, so every error it raised was called
 * err-id whatever it was asked for.
 */
class CauseErrorTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    /** The id of the error a snippet raises, or "no-error" if it raises none. */
    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("CAUSE-ERROR raises the error it was named")
    void theErrorIsTheOneAskedFor() {
        assertThat(errorIdOf("cause-error 'script 'invalid-arg 5")).isEqualTo("invalid-arg");
        assertThat(errorIdOf("cause-error 'math 'zero-divide []")).isEqualTo("zero-divide");
    }

    @Test
    @DisplayName("the arguments reach the error")
    void theArgumentsAreCarried() {
        assertThat(answerTo(
                "e: try [cause-error 'script 'invalid-arg 5] e/arg1")).isEqualTo("5");
    }

    @Test
    @DisplayName("all three arguments are carried, because a catalogue entry words three")
    void allThreeArgumentsAreCarried() {
        assertThat(answerTo(
                "e: try [cause-error 'script 'invalid-arg [1 2]] e/arg1")).isEqualTo("1");
        assertThat(answerTo(
                "e: try [cause-error 'script 'invalid-arg [1 2]] e/arg2")).isEqualTo("2");
        assertThat(answerTo(
                "e: try [cause-error 'script 'invalid-arg [1 2]] none? e/arg3"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("no arguments at all still raises")
    void theDegenerateCallStillRaises() {
        assertThat(errorIdOf("cause-error 'math 'zero-divide []")).isEqualTo("zero-divide");
    }

    @Test
    @DisplayName("MAKE ERROR! evaluates its spec")
    void theSpecIsEvaluated() {
        assertThat(answerTo(
                "kind: 'math which: 'zero-divide "
                        + "e: make error! [type: kind id: which] e/id"))
                .isEqualTo("zero-divide");
    }

    @Test
    @DisplayName("MAKE ERROR! with literal words is unaffected")
    void theOrdinarySpecStillWorks() {
        assertThat(answerTo(
                "e: make error! [type: 'script id: 'invalid-arg] e/id"))
                .isEqualTo("invalid-arg");
    }

    @Test
    @DisplayName("FUNCO builds a function")
    void funcoMakesAFunction() {
        assertThat(answerTo("f: funco [x] [x + 1] f 2")).isEqualTo("3");
    }

    @Test
    @DisplayName("MAP makes a map from a block")
    void mapMakesAMap() {
        assertThat(answerTo("map? map [a 1 b 2]")).isEqualTo("#(true)");
        assertThat(answerTo("(select map [a 1 b 2] 'b) = 2")).isEqualTo("#(true)");
        assertThat(answerTo("empty? map []")).isEqualTo("#(true)");
    }
}
