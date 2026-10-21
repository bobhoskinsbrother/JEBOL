package org.jebol.domain.parse;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CharsetFoldsCaseTest {

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a lowercase set matches an uppercase character")
    void matchesUpperWithALowerSet() {
        assertThat(answerTo("a: charset \"a\" parse \"A\" reduce [a]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("an uppercase set matches a lowercase character")
    void matchesLowerWithAnUpperSet() {
        assertThat(answerTo("u: charset \"A\" parse \"a\" reduce [u]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/case turns the folding off")
    void mindsCaseWhenAsked() {
        assertThat(answerTo("a: charset \"a\" parse/case \"A\" reduce [a]"))
                .isEqualTo("#(false)");
    }

    @Test
    @DisplayName("an exact match works either way")
    void leavesAnExactMatchAlone() {
        assertThat(answerTo("a: charset \"a\" parse \"a\" reduce [a]")).isEqualTo("#(true)");
        assertThat(answerTo("a: charset \"a\" parse/case \"a\" reduce [a]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a character outside the set still does not match")
    void stillFailsOnSomethingElse() {
        assertThat(answerTo("a: charset \"a\" parse \"b\" reduce [a]")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("a set of several characters folds each of them")
    void foldsEveryMember() {
        assertThat(answerTo("s: charset \"abc\" parse \"ABC\" reduce [s s s]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("CASE and NO-CASE work on a block parse too")
    void mindsCaseOnBlocks() {
        assertThat(answerTo("parse [\"A\"] [\"a\"]"))
                .as("a parse folds case by default, whatever it is parsing")
                .isEqualTo("#(true)");
        assertThat(answerTo("parse [\"A\"] [case [\"a\"]]")).isEqualTo("#(false)");
        assertThat(answerTo("parse [\"A\"] [case [\"A\"]]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the later of the two words wins, so it really is a mode")
    void turnsTheModeBackOff() {
        assertThat(answerTo("parse [\"A\"] [case no-case [\"a\"]]")).isEqualTo("#(true)");
        assertThat(answerTo("parse \"A\" [case no-case \"a\"]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a set with no letters in it is unaffected")
    void leavesDigitsUnfolded() {
        assertThat(answerTo("d: charset \"123\" parse \"2\" reduce [d]")).isEqualTo("#(true)");
    }
}
