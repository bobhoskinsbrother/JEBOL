package org.jebol.domain.parse;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How CHANGE puts its replacement in.
 *
 * <p>Specified in {@code spec/parse.allium} and measured against a real R3
 * 3.22.1.
 *
 * <p>A block replacement is spread by default and put in whole only when
 * ONLY says so. JEBOL put every block in whole, so a rule meant to swap
 * two words for two others left one block holding them instead, and
 * nothing said anything.
 */
class ChangeReplacementTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    /** The id of the error a snippet raises, or "no-error" if it raises none. */
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
        // The off point. Without this the test above would pass on a
        // CHANGE that could only ever nest.
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
        // It says how to put the replacement in, so before the rule it
        // reads as a rule called only, which is no rule at all.
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
        // Spreading nothing is how a CHANGE deletes, and a nesting
        // CHANGE could not do it at all.
        assertThat(answerTo("parse s: [a b] [change 'a [] 'b] s = [b]")).isEqualTo("#(true)");
    }
}
