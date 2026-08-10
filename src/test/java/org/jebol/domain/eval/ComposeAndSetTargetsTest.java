package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * COMPOSE/INTO with a source that is not a block, and what SET will assign to.
 *
 * <p>Specified in {@code spec/natives.allium} and measured against a real
 * R3 3.22.1.
 *
 * <p>Both are cases where the value looks like something it is not. A
 * string composes to itself and so looks like nothing to insert; an issue
 * and a refinement are words underneath and so look like names to assign.
 */
class ComposeAndSetTargetsTest {

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
    @DisplayName("COMPOSE/INTO inserts a source that is not a block")
    void aNonBlockSourceStillGoesIn() {
        assertThat(answerTo("x: copy [] compose/into \"a\" x x = [\"a\"]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("COMPOSE/INTO answers the target after what it put there")
    void theAnswerIsAPosition() {
        // Not the source and not the head. The position is what lets a
        // run of these build one series, each carrying on from the last.
        assertThat(answerTo("x: copy [] tail? compose/into \"a\" x")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("COMPOSE/INTO inserts at the position, not at the head")
    void theInsertionGoesWhereTheTargetIs() {
        assertThat(answerTo("x: copy [z] compose/into [a] x x = [a z]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("COMPOSE without /INTO answers a non-block source unchanged")
    void aNonBlockSourceComposesToItself() {
        // The off point. Without /INTO there is nowhere to put it, and
        // the answer is the source rather than a block wrapping it.
        assertThat(answerTo("(compose \"a\") = \"a\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("COMPOSE/INTO with a block source is unaffected")
    void theOrdinaryCaseStillWorks() {
        assertThat(answerTo("x: copy [] compose/into [a (1 + 1)] x x = [a 2]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("SET refuses an issue or a refinement as its target")
    void setRefusesTheWordsThatAreNotNames() {
        assertThat(errorIdOf("set #ab 1")).isEqualTo("expect-arg");
        assertThat(errorIdOf("set /a 1")).isEqualTo("expect-arg");
    }

    @Test
    @DisplayName("the same targets inside a block carry a different id")
    void insideABlockItIsAWrongItemNotAWrongArgument() {
        // A wrong argument to SET and a wrong item inside a right
        // argument are two mistakes, and R3 names them differently.
        assertThat(errorIdOf("set [#f][6]")).isEqualTo("invalid-arg");
        assertThat(errorIdOf("set [/e][5]")).isEqualTo("invalid-arg");
    }

    @Test
    @DisplayName("SET still takes a word and a block of words")
    void theOrdinaryTargetsAreUnaffected() {
        assertThat(answerTo("set 'q 1 q")).isEqualTo("1");
        assertThat(answerTo("set [q1 q2] [1 2] q1 + q2")).isEqualTo("3");
    }
}
