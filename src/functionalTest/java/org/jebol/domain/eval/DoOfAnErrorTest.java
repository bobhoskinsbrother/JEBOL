package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DoOfAnErrorTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String outcomeOf(String source) {
        return answerTo("r: try [" + source + "] "
                + "either error? r [join \"raised \" r/id] [join \"answered \" mold r]");
    }

    @Test
    @DisplayName("DO of an error raises it")
    void anErrorValueRaises() {
        assertThat(outcomeOf("do make error! [type: 'script id: 'invalid-data]"))
                .isEqualTo("\"raised invalid-data\"");
    }

    @Test
    @DisplayName("the raise stops what follows it")
    void nothingAfterItRuns() {
        assertThat(outcomeOf("do make error! [type: 'script id: 'invalid-data] \"after\""))
                .isEqualTo("\"raised invalid-data\"");
    }

    @Test
    @DisplayName("CAUSE-ERROR raises, and stops what follows it")
    void causeErrorDependsOnThis() {
        assertThat(outcomeOf("cause-error 'script 'invalid-data 9 \"after\""))
                .isEqualTo("\"raised invalid-data\"");
    }

    @Test
    @DisplayName("a raise inside a function body leaves the function")
    void theRaiseLeavesTheBody() {
        assertThat(outcomeOf(
                "f: func [v] [cause-error 'script 'invalid-data v \"after\"] f 9"))
                .isEqualTo("\"raised invalid-data\"");
    }

    @Test
    @DisplayName("a raise inside a conditional's block leaves the body too")
    void theRaiseLeavesANestedBlock() {
        assertThat(outcomeOf(
                "f: func [v] [unless find [0 1] v [cause-error 'script 'invalid-data v] true] "
                        + "f 9"))
                .isEqualTo("\"raised invalid-data\"");
    }

    @Test
    @DisplayName("the same function answers ordinarily when nothing is wrong")
    void theOffPoint() {
        assertThat(outcomeOf(
                "f: func [v] [unless find [0 1] v [cause-error 'script 'invalid-data v] true] "
                        + "f 1"))
                .isEqualTo("\"answered #(true)\"");
    }

    @Test
    @DisplayName("DO of anything else is unaffected")
    void theOrdinaryDoStillWorks() {
        assertThat(outcomeOf("do [1 + 1]")).isEqualTo("\"answered 2\"");
        assertThat(outcomeOf("do \"1 + 1\"")).isEqualTo("\"answered 2\"");
        assertThat(outcomeOf("do 5")).isEqualTo("\"answered 5\"");
    }
}
