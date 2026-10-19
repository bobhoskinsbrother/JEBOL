package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CharacterComparisonTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String readTheErrorIdOf(String source) {
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
    void ordersWithoutFoldingCase() {
        assertThat(answerTo("#\"a\" < #\"B\"")).isEqualTo("#(false)");
        assertThat(answerTo("#\"a\" > #\"B\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("two characters that are genuinely different are still different")
    void leavesUnrelatedCharactersUnaffected() {
        assertThat(answerTo("#\"a\" = #\"b\"")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("a character is not a one-character string")
    void treatsACharacterAsNoString() {
        assertThat(answerTo("#\"a\" = \"A\"")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("SWITCH takes the first branch whose character matches, folding case")
    void switchFoldsCase() {
        assertThat(answerTo("switch #\"a\" [#\"A\" [1] #\"a\" [2]]")).isEqualTo("1");
    }

    @Test
    @DisplayName("SELECT and FIND fold case for a character too")
    void foldsCaseInTheSearches() {
        assertThat(answerTo("first select [#\"A\" [1] #\"a\" [2]] #\"a\"")).isEqualTo("1");
        assertThat(answerTo("first first find/tail [#\"A\" [1] #\"a\" [2]] #\"a\""))
                .isEqualTo("1");
    }

    @Test
    @DisplayName("/CASE turns the folding off where it is offered")
    void stopsFoldingForCase() {
        assertThat(answerTo("first first find/case/tail [#\"A\" [1] #\"a\" [2]] #\"a\""))
                .isEqualTo("2");
    }

    @Test
    @DisplayName("a character nested in a block folds case as well")
    void foldsInsideABlock() {
        assertThat(answerTo("(reduce [#\"a\"]) = reduce [#\"A\"]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("INDEX? and LENGTH? answer none for none, and EMPTY? answers true")
    void letsThreeReflectorsTolerateNone() {
        assertThat(answerTo("none? index? none")).isEqualTo("#(true)");
        assertThat(answerTo("none? length? none")).isEqualTo("#(true)");
        assertThat(answerTo("empty? none")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the rest of the family still refuses none")
    void refusesNoneInTheOtherReflectors() {
        assertThat(readTheErrorIdOf("first none")).isNotEqualTo("no-error");
        assertThat(readTheErrorIdOf("head none")).isNotEqualTo("no-error");
        assertThat(readTheErrorIdOf("next none")).isNotEqualTo("no-error");
    }

    @Test
    @DisplayName("and TAIL? refuses none: the declaration is the door")
    void refusesNoneForTail() {
        assertThat(readTheErrorIdOf("tail? none")).isEqualTo("expect-arg");
    }
}
