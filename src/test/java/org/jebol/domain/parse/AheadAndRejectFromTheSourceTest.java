package org.jebol.domain.parse;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AHEAD and REJECT from {@code parse-test.r3}. AHEAD looks at a compound rule
 * without consuming input, and REJECT fails the current block at once without
 * trying its later alternatives.
 */
class AheadAndRejectFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("AHEAD looks at a compound rule and consumes nothing")
    void aheadLooksAtACompoundRule() {
        assertThat(answerTo("""
                parse "abc" [ahead thru "c" 3 skip]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("AHEAD fails when the compound rule does not match ahead")
    void aheadFailsWhenTheRuleDoesNot() {
        assertThat(answerTo("""
                parse "abc" [and thru "z" 3 skip]""")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("REJECT fails the current block without trying its later alternatives")
    void rejectSkipsLaterAlternatives() {
        assertThat(answerTo("""
                not parse "aabb" [[#"a" reject | "aabb"]]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("REJECT in a sub-block leaves the enclosing block's alternatives")
    void rejectInASubBlockLeavesEnclosingAlternatives() {
        assertThat(answerTo("""
                parse "aabb" [[#"a" reject] | "aabb"]""")).isEqualTo("#(true)");
    }
}
