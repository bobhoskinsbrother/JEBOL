package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(20)
class AnObjectThatRefersToItselfComparesFromTheSourceTest {

    private static final String AN_OBJECT_POINTING_AT_ITSELF = """
            o: object [a: 1 me: none]
            o/me: o
            """;

    private final Interpreter interpreter = Interpreter.create();

    private String answerTo(String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource(delimiter = '|', value = {
            "equal? o o           | #(true)",
            "o = o                | #(true)",
            "o == o               | #(true)",
            "equiv? o o           | #(true)",
            "strict-equal? o o    | #(true)",
            "not-equal? o o       | #(false)",
            "o <> o               | #(false)",
            "index? find reduce [1 o] o | 2",
    })
    @DisplayName("an object that points at itself is equal to itself, as a real 3.22.5 answers")
    void anObjectEqualsItself(String asked, String answer) {
        assertThat(answerTo(AN_OBJECT_POINTING_AT_ITSELF + asked)).isEqualTo(answer);
    }

    @Test
    @DisplayName("two such objects differing in a field before the one that loops are simply not equal")
    void aDifferenceBeforeTheLoopEndsIt() {
        assertThat(answerTo("""
                o: object [a: 1 me: none] o/me: o
                p: object [a: 2 me: none] p/me: p
                equal? o p""")).isEqualTo("#(false)");
    }

    @ParameterizedTest(name = "{0} raises stack-overflow")
    @CsvSource(delimiter = '|', value = {
            "o: object [a: 1 me: none] o/me: o  p: object [a: 1 me: none] p/me: p  equal? o p",
            "o: object [a: 1 b: none] p: object [a: 1 b: none] o/b: p p/b: o  equal? o p",
            "b: copy [1] append/only b b  c: copy [1] append/only c c  equal? b c",
    })
    @DisplayName("a comparison with no end raises stack-overflow, an error a script can catch")
    void aComparisonWithNoEndRaisesStackOverflow(String compared) {
        assertThat(answerTo("""
                e: try [%s]
                reduce [e/type e/id]""".formatted(compared))).isEqualTo("[Internal stack-overflow]");
    }

    @Test
    @DisplayName("and the interpreter carries on afterwards")
    void theInterpreterCarriesOn() {
        answerTo("""
                b: copy [1] append/only b b  c: copy [1] append/only c c
                try [equal? b c]""");

        assertThat(answerTo("1 + 2")).isEqualTo("3");
    }
}
