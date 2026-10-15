package org.jebol.domain.parse;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeReplacementTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("a block replacement is spread")
    void aBlockIsSpreadByDefault() {
        assertThat(answerTo("parse s: [a b] [change some word! [z p]] s = [z p]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("ONLY puts the block in whole")
    void onlyKeepsTheBlockTogether() {
        assertThat(answerTo("parse s: [a b] [change ['a 'b] only [z p]] s = [[z p]]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a nested rule's match is replaced the same way")
    void changingIntoANestedBlock() {
        assertThat(answerTo("parse s: [[a b]] [change into ['a 'b] [z p]] s = [z p]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("ONLY before the rule is refused")
    void onlyBelongsBeforeTheReplacement() {
        assertThat(errorIdOf("parse s: [a b] [change only ['a 'b] [z p]]"))
                .isEqualTo("parse-rule");
    }

    @Test
    @DisplayName("a paren replacement is evaluated")
    void aParenIsEvaluated() {
        assertThat(answerTo("parse s: [a] [change 'a (1 + 1)] s = [2]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a single value replacement is unaffected")
    void oneValueGoesInAsItself() {
        assertThat(answerTo("parse s: [a b] [change 'a 'z 'b] s = [z b]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("an empty block replacement takes the match out")
    void theDegenerateReplacementRemoves() {
        assertThat(answerTo("parse s: [a b] [change 'a [] 'b] s = [b]")).isEqualTo("#(true)");
    }
}
