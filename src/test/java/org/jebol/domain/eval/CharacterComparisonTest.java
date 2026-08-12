package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Characters compare equal without regard to case, and in order with it.
 *
 * <p>Specified in {@code spec/natives.allium} and measured against a real
 * R3 3.22.1.
 *
 * <p>The two questions are answered differently on purpose. Folding for
 * both would put the letters in an order nobody asked for; folding for
 * neither makes {@code switch} and {@code find} miss a capital they were
 * meant to catch.
 */
class CharacterComparisonTest {

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
    @DisplayName("= folds case and == does not")
    void equalityFoldsCase() {
        assertThat(answerTo("#\"a\" = #\"A\"")).isEqualTo("#(true)");
        assertThat(answerTo("#\"a\" == #\"A\"")).isEqualTo("#(false)");
        assertThat(answerTo("equal? #\"a\" #\"A\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("ordering goes by code point and folds nothing")
    void orderingDoesNotFoldCase() {
        // The pair that would go the other way under a folding compare:
        // small a sorts after capital B by code point and before it by
        // letter. R3 goes by code point.
        assertThat(answerTo("#\"a\" < #\"B\"")).isEqualTo("#(false)");
        assertThat(answerTo("#\"a\" > #\"B\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("two characters that are genuinely different are still different")
    void unrelatedCharactersAreUnaffected() {
        assertThat(answerTo("#\"a\" = #\"b\"")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("a character is not a one-character string")
    void aCharacterIsNotAString() {
        assertThat(answerTo("#\"a\" = \"A\"")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("SWITCH takes the first branch whose character matches, folding case")
    void switchFoldsCase() {
        assertThat(answerTo("switch #\"a\" [#\"A\" [1] #\"a\" [2]]")).isEqualTo("1");
    }

    @Test
    @DisplayName("SELECT and FIND fold case for a character too")
    void theSearchesFoldCase() {
        assertThat(answerTo("first select [#\"A\" [1] #\"a\" [2]] #\"a\"")).isEqualTo("1");
        assertThat(answerTo("first first find/tail [#\"A\" [1] #\"a\" [2]] #\"a\""))
                .isEqualTo("1");
    }

    @Test
    @DisplayName("/CASE turns the folding off where it is offered")
    void caseTurnsTheFoldingOff() {
        // Without this the tests above would pass just as well on a FIND
        // that could not tell the two apart at all.
        assertThat(answerTo("first first find/case/tail [#\"A\" [1] #\"a\" [2]] #\"a\""))
                .isEqualTo("2");
    }

    @Test
    @DisplayName("a character nested in a block folds case as well")
    void theFoldingReachesInsideABlock() {
        assertThat(answerTo("(reduce [#\"a\"]) = reduce [#\"A\"]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("INDEX? and LENGTH? answer none for none, and EMPTY? answers true")
    void threeReflectorsTolerateNone() {
        assertThat(answerTo("none? index? none")).isEqualTo("#(true)");
        assertThat(answerTo("none? length? none")).isEqualTo("#(true)");
        assertThat(answerTo("empty? none")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the rest of the family still refuses none")
    void theOthersAreNotSoForgiving() {
        // Named exceptions rather than a rule about none, so the ones that are
        // not exceptions have to be pinned too. FIRST, HEAD and NEXT all raise.
        assertThat(errorIdOf("first none")).isNotEqualTo("no-error");
        assertThat(errorIdOf("head none")).isNotEqualTo("no-error");
        assertThat(errorIdOf("next none")).isNotEqualTo("no-error");
    }

    @Test
    @DisplayName("and TAIL? refuses none: the declaration is the door")
    void tailRefusesNone() {
        // This test asserted true here twice, each time from half the C.
        // REBTYPE(None) has the arm -- `case A_TAILQ: if (IS_NONE(val))
        // return R_TRUE;` -- but the declared spec in actions.reb has no
        // none!, so a direct call never reaches the arm. EMPTY? reaches it,
        // because Rebol's own mezz builds EMPTY? as `make :tail?` with a
        // spec that adds none!. Line 97 above pins that half.
        assertThat(errorIdOf("tail? none")).isEqualTo("expect-arg");
    }
}
