package org.jebol.domain.parse;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PARSE's CHANGE and INSERT, which alter the input as they go.
 *
 * <p>Specified in {@code spec/parse.allium}, confirmed against a real R3.
 *
 * <p>The interesting part of CHANGE is when the replacement is worked out.
 * A paren is evaluated at the moment the change happens, not when the rule
 * was written, which is what makes it useful with SET: the paren can read
 * a word the very match it is replacing has just set.
 */
class ParseChangeAndInsertTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("CHANGE puts a literal where the match was")
    void changeReplacesWithALiteral() {
        assertThat(answerTo("parse s: [1 2] [change integer! 9 skip] mold s"))
                .isEqualTo("\"[9 2]\"");
    }

    @Test
    @DisplayName("CHANGE evaluates a paren replacement when the change happens")
    void changeEvaluatesAParen() {
        assertThat(answerTo("parse s: [1] [change set n integer! (n * 10)] mold s"))
                .as("the paren reads the n that the replaced match just set")
                .isEqualTo("\"[10]\"");
    }

    @Test
    @DisplayName("CHANGE replaces however many items the rule matched with one value")
    void changeCollapsesAMultiItemMatch() {
        assertThat(answerTo("parse s: [1 2 3] [change 2 skip 9] mold s"))
                .isEqualTo("\"[9 3]\"");
    }

    @Test
    @DisplayName("CHANGE works on a string too")
    void changeWorksOnAString() {
        assertThat(answerTo("parse s: \"ab\" [change \"a\" \"z\"] s")).isEqualTo("\"zb\"");
    }

    @Test
    @DisplayName("CHANGE leaves the input alone when the rule does not match")
    void changeOnAFailedMatchChangesNothing() {
        assertThat(answerTo("parse s: [1] [change string! 9] mold s"))
                .isEqualTo("\"[1]\"");
    }

    @Test
    @DisplayName("INSERT puts a value in and consumes nothing")
    void insertAddsWithoutConsuming() {
        assertThat(answerTo("parse s: [1] [insert 9] mold s")).isEqualTo("\"[9 1]\"");
    }

    @Test
    @DisplayName("INSERT leaves the position after what it put in")
    void insertLeavesThePositionAfterIt() {
        assertThat(answerTo("parse s: [1] [insert 9 skip] mold s"))
                .as("the SKIP took the 1, not the 9 that was just inserted")
                .isEqualTo("\"[9 1]\"");
    }

    @Test
    @DisplayName("REMOVE takes what the rule matched out")
    void removeStillWorks() {
        assertThat(answerTo("parse s: [1 2 3] [remove skip to end] mold s"))
                .isEqualTo("\"[2 3]\"");
    }

    @Test
    @DisplayName("REMOVE on a string takes the rest out")
    void removeWorksOnAString() {
        assertThat(answerTo("parse s: \"aaabbb\" [some \"a\" remove to end] s"))
                .isEqualTo("\"aaa\"");
    }
}
