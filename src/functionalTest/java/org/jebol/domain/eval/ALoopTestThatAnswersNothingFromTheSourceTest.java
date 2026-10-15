package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ALoopTestThatAnswersNothingFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String failureOf(String source) {
        return answerTo("set/any 'e try [" + source + """
                ]
                either all [value? 'e  error? :e] [reduce [e/type e/id]] [reduce ['ok]]""");
    }

    @Test
    @DisplayName("UNTIL tests its body, so an empty body has no truth to test")
    void untilTestsItsBodySoAnEmptyBodyHasNoTruth() {
        assertThat(failureOf("until []")).isEqualTo("[Script no-return]");
        assertThat(failureOf("until [()]")).isEqualTo("[Script no-return]");
    }

    @Test
    @DisplayName("and a body that answers anything at all goes round on it")
    void aBodyThatAnswersAnythingGoesRoundOnIt() {
        assertThat(failureOf("until [true]")).isEqualTo("[ok]");
        assertThat(failureOf("until [0]")).isEqualTo("[ok]");
        assertThat(failureOf("""
                counted: 0
                until [counted: counted + 1  counted = 3]""")).isEqualTo("[ok]");
    }

    @Test
    @DisplayName("WHILE tests its condition, and only its condition")
    void whileTestsItsConditionAndOnlyItsCondition() {
        assertThat(failureOf("while [()] []")).isEqualTo("[Script no-return]");
        assertThat(failureOf("while [false] [()]")).isEqualTo("[ok]");
        assertThat(failureOf("while [] []")).isEqualTo("[Script no-return]");
    }

    @Test
    @DisplayName("a loop that counts rather than tests is not asking for a truth")
    void aLoopThatCountsIsNotAskingForATruth() {
        assertThat(failureOf("loop 1 []")).isEqualTo("[ok]");
        assertThat(failureOf("repeat step 1 []")).isEqualTo("[ok]");
        assertThat(failureOf("foreach item [1] []")).isEqualTo("[ok]");
        assertThat(failureOf("forever [break]")).isEqualTo("[ok]");
    }

    @Test
    @DisplayName("and the refusal is what stops UNTIL looping for ever on nothing")
    void theRefusalIsWhatStopsUntilLoopingForEver() {
        assertThat(answerTo("""
                set/any 'e try [until [()]]
                either all [value? 'e  error? :e] [e/id] ['ran-away]"""))
                .isEqualTo("no-return");
    }
}
