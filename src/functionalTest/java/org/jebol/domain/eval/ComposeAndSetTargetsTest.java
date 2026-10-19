package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ComposeAndSetTargetsTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String readTheErrorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("COMPOSE/INTO inserts a source that is not a block")
    void stillPutsANonBlockSourceIn() {
        assertThat(answerTo("x: copy [] compose/into \"a\" x x = [\"a\"]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("COMPOSE/INTO answers the target after what it put there")
    void answersAPosition() {
        assertThat(answerTo("x: copy [] tail? compose/into \"a\" x")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("COMPOSE/INTO inserts at the position, not at the head")
    void insertsWhereTheTargetIs() {
        assertThat(answerTo("x: copy [z] compose/into [a] x x = [a z]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("COMPOSE without /INTO answers a non-block source unchanged")
    void composesANonBlockSourceToItself() {
        assertThat(answerTo("(compose \"a\") = \"a\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("COMPOSE/INTO with a block source is unaffected")
    void stillHandlesTheOrdinaryCase() {
        assertThat(answerTo("x: copy [] compose/into [a (1 + 1)] x x = [a 2]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("SET refuses an issue or a refinement as its target")
    void setRefusesTheWordsThatAreNotNames() {
        assertThat(readTheErrorIdOf("set #ab 1")).isEqualTo("expect-arg");
        assertThat(readTheErrorIdOf("set /a 1")).isEqualTo("expect-arg");
    }

    @Test
    @DisplayName("the same targets inside a block carry a different id")
    void reportsAWrongItemRatherThanAWrongArgumentInsideABlock() {
        assertThat(readTheErrorIdOf("set [#f][6]")).isEqualTo("invalid-arg");
        assertThat(readTheErrorIdOf("set [/e][5]")).isEqualTo("invalid-arg");
    }

    @Test
    @DisplayName("SET still takes a word and a block of words")
    void leavesTheOrdinaryTargetsUnaffected() {
        assertThat(answerTo("set 'q 1 q")).isEqualTo("1");
        assertThat(answerTo("set [q1 q2] [1 2] q1 + q2")).isEqualTo("3");
    }
}
