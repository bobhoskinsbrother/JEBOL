package org.jebol.domain.value;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AFunctionKeepsItsKindTest {

    private String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a closure copied into a derived object is still a closure")
    void aCopiedClosureIsStillAClosure() {
        assertThat(answerTo("""
                o: make object! [f: closure [] [1]] p: make o [] type? get in p 'f""")).isEqualTo("#(closure!)");
    }

    @Test
    @DisplayName("a function copied into a derived object is still a function")
    void aCopiedFunctionIsStillAFunction() {
        assertThat(answerTo("""
                o: make object! [f: func [] [1]] p: make o [] type? get in p 'f""")).isEqualTo("#(function!)");
    }

    @Test
    @DisplayName("and the copied closure still runs")
    void theCopiedClosureRuns() {
        assertThat(answerTo("""
                o: make object! [n: 2 f: closure [x] [x * n]] p: make o [n: 3] p/f 5""")).isEqualTo("15");
    }
}
