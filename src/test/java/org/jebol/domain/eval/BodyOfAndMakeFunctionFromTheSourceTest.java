package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class BodyOfAndMakeFunctionFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String erroredOn(String source) {
        return answerTo("error? try [" + source + "]");
    }

    @Test
    @DisplayName("BODY-OF answers the body")
    void bodyOfAnswersTheBody() {
        assertThat(answerTo("""
                f: func [a [integer!]] [probe a]
                [probe a] = body-of :f""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("clearing part of BODY-OF does not reach into the function")
    void clearingBodyOfDoesNotReachTheFunction() {
        assertThat(answerTo("""
                f: func [a] [append {xx} s]
                clear second body-of :f
                [append {xx} s] = body-of :f""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("MAKE from a native prototype with a body block is refused")
    void makeFromANativeWithABodyIsRefused() {
        assertThat(erroredOn("make :read [[][]]")).isEqualTo("#(true)");
        assertThat(erroredOn("make action! [[][]]")).isEqualTo("#(true)");
        assertThat(erroredOn("make native! [[][]]")).isEqualTo("#(true)");
    }

    @ParameterizedTest(name = "make := {0} is refused as cannot-use naming it and op!")
    @ValueSource(strings = {"[* [value1 > value2]]", "[[a b] [a]]", "[[a b] [a] extra]", "1", "none"})
    @DisplayName("MAKE from an operator prototype refuses a body and anything but a block")
    void makeFromAnOperatorWithABodyIsRefused(String offered) {
        assertThat(answerTo("""
                e: try [make := %s]
                reduce [e/id e/arg1 = %s e/arg2]""".formatted(offered, offered)))
                .isEqualTo("[cannot-use #(true) #(op!)]");
    }

    @ParameterizedTest(name = "make :add {0} is refused as cannot-use naming it and action!")
    @ValueSource(strings = {"1", "none", "[x]", "[[a] [b]]"})
    @DisplayName("MAKE from an action prototype refuses a body and anything but a block")
    void makeFromAnActionWithABodyIsRefused(String offered) {
        assertThat(answerTo("""
                e: try [make :add %s]
                reduce [e/id e/arg1 = %s e/arg2]""".formatted(offered, offered)))
                .isEqualTo("[cannot-use #(true) #(action!)]");
    }

    @ParameterizedTest(name = "make := {0} answers an operator")
    @ValueSource(strings = {"[]", "[*]", "[[a b]]"})
    @DisplayName("MAKE from an operator prototype without a body answers an operator")
    void makeFromAnOperatorWithoutABodyAnswersAnOperator(String offered) {
        assertThat(answerTo("""
                type? make := %s""".formatted(offered))).isEqualTo("#(op!)");
    }

    @Test
    @DisplayName("MAKE from an action prototype with one spec block answers an action with that spec")
    void makeFromAnActionWithASpecAnswersThatSpec() {
        assertThat(answerTo("""
                derived: make :subtract [[a b]]
                reduce [type? :derived spec-of :derived]""")).isEqualTo("[#(action!) [a b]]");
    }

    @Test
    @DisplayName("MAKE OP! from a two-argument function still builds an operator")
    void makeOpFromATwoArgumentFunctionStillBuilds() {
        assertThat(answerTo("""
                plus: make op! [[a b] [a + b]]
                reduce [op? :plus 3 plus 4]""")).isEqualTo("[#(true) 7]");
    }

    @Test
    @DisplayName("deriving a wider interface from a native with one spec block still works")
    void derivingWithOneSpecBlockStillWorks() {
        assertThat(answerTo("""
                wider: make :tail? [[series [series! none!]]]
                any-function? :wider""")).isEqualTo("#(true)");
    }
}
