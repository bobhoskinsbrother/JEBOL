package org.jebol.domain.parse;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A bitset folds case like everything else in a parse.
 *
 * <p>Specified in {@code spec/parse.allium}, confirmed against a real R3.
 *
 * <p>Asking the set only about the character as written makes a charset
 * the one rule in the dialect that always minds case -- not something a
 * rule author would expect, and not something /CASE could switch off,
 * because it was already on.
 */
class CharsetFoldsCaseTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a lowercase set matches an uppercase character")
    void aLowerSetMatchesUpper() {
        assertThat(answerTo("a: charset \"a\" parse \"A\" reduce [a]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("an uppercase set matches a lowercase character")
    void anUpperSetMatchesLower() {
        assertThat(answerTo("u: charset \"A\" parse \"a\" reduce [u]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/case turns the folding off")
    void caseMindsIt() {
        assertThat(answerTo("a: charset \"a\" parse/case \"A\" reduce [a]"))
                .isEqualTo("#(false)");
    }

    @Test
    @DisplayName("an exact match works either way")
    void anExactMatchIsUnaffected() {
        assertThat(answerTo("a: charset \"a\" parse \"a\" reduce [a]")).isEqualTo("#(true)");
        assertThat(answerTo("a: charset \"a\" parse/case \"a\" reduce [a]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a character outside the set still does not match")
    void somethingElseStillFails() {
        assertThat(answerTo("a: charset \"a\" parse \"b\" reduce [a]")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("a set of several characters folds each of them")
    void everyMemberFolds() {
        assertThat(answerTo("s: charset \"abc\" parse \"ABC\" reduce [s s s]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("CASE and NO-CASE work on a block parse too")
    void theCaseModeWorksOnBlocks() {
        assertThat(answerTo("parse [\"A\"] [\"a\"]"))
                .as("a parse folds case by default, whatever it is parsing")
                .isEqualTo("#(true)");
        assertThat(answerTo("parse [\"A\"] [case [\"a\"]]")).isEqualTo("#(false)");
        assertThat(answerTo("parse [\"A\"] [case [\"A\"]]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the later of the two words wins, so it really is a mode")
    void theModeCanBeTurnedBackOff() {
        assertThat(answerTo("parse [\"A\"] [case no-case [\"a\"]]")).isEqualTo("#(true)");
        assertThat(answerTo("parse \"A\" [case no-case \"a\"]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a set with no letters in it is unaffected")
    void digitsAreTheDegenerateCase() {
        assertThat(answerTo("d: charset \"123\" parse \"2\" reduce [d]")).isEqualTo("#(true)");
    }
}
