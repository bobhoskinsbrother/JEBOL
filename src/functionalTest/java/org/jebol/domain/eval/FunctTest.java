package org.jebol.domain.eval;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FunctTest {

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a set-word in the body does not reach outside")
    void keepsTheAssignmentsIn() {
        assertThat(answerTo("x: 1 f: funct [] [x: 2] f x")).isEqualTo("1");
    }

    @Test
    @DisplayName("FUNC lets the same assignment out")
    void funcIsDifferent() {
        assertThat(answerTo("x: 1 f: func [] [x: 2] f x")).isEqualTo("2");
    }

    @Test
    @DisplayName("the body still answers what it computed")
    void keepsTheLocalsValue() {
        assertThat(answerTo("f: funct [] [x: 2 x] f")).isEqualTo("2");
    }

    @Test
    @DisplayName("arguments work beside the locals")
    void leavesArgumentsUnaffected() {
        assertThat(answerTo("f: funct [a] [b: a + 1 b] f 2")).isEqualTo("3");
    }

    @Test
    @DisplayName("/EXTERN names a word that must not become local")
    void externLetsOneOut() {
        assertThat(answerTo("y: 1 f: funct/extern [] [y: 5] [y] f y")).isEqualTo("5");
    }

    @Test
    @DisplayName("the collected names go after /local in the spec")
    void showsTheLocalsInTheSpec() {
        assertThat(answerTo("(spec-of funct [a] [b: 1]) = [a /local b]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a body with no set-word gets no locals")
    void handlesTheDegenerateBody() {
        assertThat(answerTo("(spec-of funct [a] [a]) = [a /local]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a spec that already has /local keeps it")
    void neverDoublesAnExistingLocal() {
        assertThat(answerTo("(spec-of funct [a /local c] [b: 1]) = [a /local b c]"))
                .isEqualTo("#(true)");
    }
}
