package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplyPassesRefinementsFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a refinement that is asked for is used")
    void arefinementThatIsAskedForIsUsed() {
        assertThat(answerTo("mold apply :copy [[1 2 3 4 5] true 3]"))
                .isEqualTo("\"[1 2 3]\"");
    }

    @Test
    @DisplayName("and one that is not is not, though its argument is still consumed")
    void andOneThatIsNotIsNot() {
        assertThat(answerTo("mold apply :copy [[1 2 3 4 5] false 3]"))
                .as("the 3 is read into /part's slot and ignored, so the position "
                        + "of anything after it does not move")
                .isEqualTo("\"[1 2 3 4 5]\"");
    }

    @Test
    @DisplayName("a later refinement is reached past an earlier one")
    void alaterRefinementIsReached() {
        assertThat(answerTo("mold apply :append [[1] [2 3] false none true none]"))
                .as("/part false, range none, /only true: the block goes in whole")
                .isEqualTo("\"[1 [2 3]]\"");
    }

    @Test
    @DisplayName("TAKE/PART takes that many rather than one")
    void takePartTakesThatMany() {
        assertThat(answerTo("mold apply :take [[1 2 3 4] true 2]"))
                .isEqualTo("\"[1 2]\"");
    }

    @Test
    @DisplayName("UPPERCASE/PART reaches only that far")
    void uppercasePartReachesOnlyThatFar() {
        assertThat(answerTo("""
                {HEllo} = apply :uppercase [{hello} true 2]"""))
                .as("compared inside REBOL: molding a string that holds quotes "
                        + "and then molding that again is not what is being asked")
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a function written in REBOL was always right and still is")
    void arebolFunctionIsUnaffected() {
        assertThat(answerTo("mold apply func [a /b c][reduce [a b c]] [1 true 2]"))
                .isEqualTo("\"[1 #(true) 2]\"");
    }

    @Test
    @DisplayName("missing values are none, and a refinement with none is not asked for")
    void missingValuesAreNone() {
        assertThat(answerTo("mold apply :copy [[1 2 3]]"))
                .as("nothing said about /part, so no /part")
                .isEqualTo("\"[1 2 3]\"");
    }
}
