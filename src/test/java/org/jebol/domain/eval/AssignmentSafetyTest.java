package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A word keeps its previous value when the expression assigned to it
 * raises.
 *
 * <p>Claimed as a guarantee in {@code spec/eval.allium}, so it needs a test
 * rather than a reading of the code. There must be no state in which a slot
 * holds a partly computed result.
 */
class AssignmentSafetyTest {

    private static Interpreter freshInterpreter(String... lines) {
        Interpreter interpreter = Interpreter.create();
        for (String line : lines) {
            interpreter.defineFreshWordsIn(line);
            interpreter.run(line);
        }
        return interpreter;
    }

    @Test
    @DisplayName("a word keeps its old value when the assignment raises")
    void failedAssignmentLeavesTheSlotAlone() {
        Interpreter interpreter = freshInterpreter("total: 42");

        interpreter.defineFreshWordsIn("total: divide 1 0");
        ScriptOutcome failed = interpreter.run("total: divide 1 0");

        assertThat(failed.succeeded()).as("the assignment should have raised").isFalse();
        assertThat(interpreter.display(interpreter.run("total")))
                .as("total must still hold what it held before")
                .isEqualTo("42");
    }

    @Test
    @DisplayName("and stays unset if it never held anything")
    void failedFirstAssignmentLeavesTheSlotUnset() {
        Interpreter interpreter = Interpreter.create();

        interpreter.defineFreshWordsIn("fresh: divide 1 0");
        ScriptOutcome failed = interpreter.run("fresh: divide 1 0");

        assertThat(failed.succeeded()).isFalse();
        assertThat(interpreter.display(interpreter.run("value? 'fresh")))
                .as("a word whose only assignment failed holds nothing")
                .isEqualTo("#(false)");
    }

    @Test
    @DisplayName("an error partway through a block leaves earlier assignments standing")
    void earlierAssignmentsSurvive() {
        Interpreter interpreter = Interpreter.create();
        String source = "first-word: 1 second-word: divide 1 0 third-word: 3";

        interpreter.defineFreshWordsIn(source);
        ScriptOutcome failed = interpreter.run(source);

        assertThat(failed.succeeded()).isFalse();
        assertThat(interpreter.display(interpreter.run("first-word")))
                .as("the assignment before the failure completed")
                .isEqualTo("1");
        assertThat(interpreter.display(interpreter.run("value? 'third-word")))
                .as("the assignment after the failure never ran")
                .isEqualTo("#(false)");
    }

    @Test
    @DisplayName("a protected word refuses assignment and keeps its value")
    void protectedWordKeepsItsValue() {
        Interpreter interpreter = freshInterpreter("kept: \"original\"", "protect 'kept");

        interpreter.defineFreshWordsIn("kept: \"replacement\"");
        ScriptOutcome refused = interpreter.run("kept: \"replacement\"");

        assertThat(refused.succeeded()).as("assigning to a protected word must raise").isFalse();
        assertThat(refused.errorId().orElseThrow()).isEqualTo("locked-word");
        assertThat(interpreter.display(interpreter.run("kept"))).isEqualTo("\"original\"");
    }
}
