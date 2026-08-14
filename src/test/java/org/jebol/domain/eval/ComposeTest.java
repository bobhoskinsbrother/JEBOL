package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * COMPOSE fills the parens in a template and leaves everything else alone.
 *
 * <p>Specified in {@code spec/natives.allium}, confirmed against a real R3.
 *
 * <p>The boundaries here are depth -- top level only, one deep, several
 * deep -- and what a paren answers with, since a block splices unless
 * /ONLY says otherwise.
 */
class ComposeTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a paren at the top level is filled in")
    void topLevelParensAreFilled() {
        assertThat(answerTo("mold compose [1 (2 + 3) 4]")).isEqualTo("\"[1 5 4]\"");
    }

    @Test
    @DisplayName("a paren inside a block is left alone without /deep")
    void nestedParensAreLeftAloneByDefault() {
        assertThat(answerTo("mold compose [[(7 - 6)]]")).isEqualTo("\"[[(7 - 6)]]\"");
    }

    @Test
    @DisplayName("/deep fills a paren one block down")
    void deepFillsOneLevelDown() {
        assertThat(answerTo("mold compose/deep [[(7 - 6)]]")).isEqualTo("\"[[1]]\"");
    }

    @Test
    @DisplayName("/deep keeps going however deep the nesting")
    void deepFillsAllTheWayDown() {
        assertThat(answerTo("mold compose/deep [[[(1 + 1)]]]")).isEqualTo("\"[[[2]]]\"");
    }

    @Test
    @DisplayName("/deep still fills the top level too")
    void deepDoesNotSkipTheTopLevel() {
        assertThat(answerTo("mold compose/deep [x (1 + 1) [y (2 + 2)]]"))
                .isEqualTo("\"[x 2 [y 4]]\"");
    }

    @Test
    @DisplayName("a paren answering a block splices its contents in")
    void aBlockAnswerIsSpliced() {
        assertThat(answerTo("mold compose [a (reduce [1 2]) b]")).isEqualTo("\"[a 1 2 b]\"");
    }

    @Test
    @DisplayName("/only puts the block in as one value")
    void onlyKeepsTheBlockWhole() {
        assertThat(answerTo("mold compose/only [a (reduce [1 2]) b]"))
                .isEqualTo("\"[a [1 2] b]\"");
    }

    @Test
    @DisplayName("an empty paren contributes nothing")
    void anEmptyParenLeavesNothingBehind() {
        assertThat(answerTo("mold compose [a () b]")).isEqualTo("\"[a b]\"");
    }

    @Test
    @DisplayName("a template with no parens comes back as it went in")
    void aTemplateWithoutParensIsUnchanged() {
        assertThat(answerTo("mold compose [a b]")).isEqualTo("\"[a b]\"");
    }

    @Test
    @DisplayName("an empty template gives an empty block")
    void anEmptyTemplateGivesAnEmptyBlock() {
        assertThat(answerTo("mold compose []")).isEqualTo("\"[]\"");
    }

    @Test
    @DisplayName("a paren that answers nothing contributes nothing")
    void anUnsetParenLeavesNothingBehind() {
        assertThat(answerTo("mold compose [x (print \"\") y]")).isEqualTo("\"[x y]\"");
    }

    @Test
    @DisplayName("the awkward parens all behave together")
    void theAwkwardCasesTogether() {
        assertThat(answerTo(
                "mold compose [a (1 + 2) b () (print \"\") ([]) 789 ([1 2 3])]"))
                .as("empty paren, unset paren and empty block all vanish; "
                        + "a block splices")
                .isEqualTo("\"[a 3 b 789 1 2 3]\"");
    }

    @Test
    @DisplayName("/into fills a block the caller already has")
    void intoFillsAnExistingBlock() {
        assertThat(answerTo("a: [] compose/into [1 (1 + 1) 3] a mold head a"))
                .isEqualTo("\"[1 2 3]\"");
    }

    @Test
    @DisplayName("/into puts them at the position, pushing what was there along")
    void intoInsertsAtThePosition() {
        assertThat(answerTo("b: [9] compose/into [1] b mold head b"))
                .isEqualTo("\"[1 9]\"");
        assertThat(answerTo("c: [8 9] compose/into [1] next c mold head c"))
                .isEqualTo("\"[8 1 9]\"");
    }

    @Test
    @DisplayName("/into answers the position after what it put there")
    void intoAnswersThePositionAfter() {
        assertThat(answerTo("a: [] mold compose/into [1 2] a")).isEqualTo("\"[]\"");
    }

    @Test
    @DisplayName("/into of an empty template leaves the block alone")
    void intoNothingIsTheDegenerateCase() {
        assertThat(answerTo("b: [9] compose/into [] b mold head b")).isEqualTo("\"[9]\"");
    }

    @Test
    @DisplayName("/into refuses a string, which has no text for values")
    void intoRefusesAString() {
        assertThat(answerTo("e: try [compose/into [1] \"x\"] "
                + "either error? e ['refused] ['no-error]"))
                .isEqualTo("refused");
    }

    @Test
    @DisplayName("something that is not a block is handed straight back")
    void aNonBlockPassesThrough() {
        assertThat(answerTo("compose 1")).isEqualTo("1");
        assertThat(answerTo("compose \"a-string\"")).isEqualTo("\"a-string\"");
        assertThat(answerTo("compose none")).isEqualTo("_");
    }
}
