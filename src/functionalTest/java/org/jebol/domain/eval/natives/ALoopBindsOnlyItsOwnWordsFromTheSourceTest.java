package org.jebol.domain.eval.natives;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ALoopBindsOnlyItsOwnWordsFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String AN_OBJECT_WITH_ITS_OWN_PROBE = """
            o: make object! [probe: func [x] [join "mine " x] n: 'outer]
            body: bind [probe "x"] o
            """;

    @Test
    @DisplayName("a word the body already binds to an object keeps that binding, though lib has the word too")
    void keepsABindingTheBodyAlreadyHas() {
        assertThat(answerTo(AN_OBJECT_WITH_ITS_OWN_PROBE + """
                reduce [repeat i 1 body  foreach i [1] body  for i 1 1 1 body  map-each i [1] body]""")).isEqualTo("""
                ["mine x" "mine x" "mine x" ["mine x"]]""");
    }

    @Test
    @DisplayName("and a word only that object has is still found, not left without a value")
    void keepsAWordOnlyTheObjectHas() {
        assertThat(answerTo(AN_OBJECT_WITH_ITS_OWN_PROBE + """
                reduce [
                    collect [repeat i 1 bind [keep n] o]
                    remove-each i [1 2] bind [n = 'outer] o
                ]""")).isEqualTo("[[outer] []]");
    }

    @Test
    @DisplayName("a function's own argument is still the argument inside a loop in its body")
    void keepsAFunctionsArgument() {
        assertThat(answerTo("""
                f: func [x] [repeat i 1 [x]]
                f 42
                """)).isEqualTo("42");
    }

    @Test
    @DisplayName("the loop's own word is bound to the loop, even when lib has a word of that name")
    void bindsItsOwnWordEvenWhenLibHasIt() {
        assertThat(answerTo("""
                repeat probe 1 [probe]""")).isEqualTo("1");
    }
}
