package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * APPLY hands a built-in its refinements, not just its arguments.
 *
 * <p>The block APPLY takes is read against the function's words in order: a
 * word takes the next value as its argument, and a refinement takes the next
 * value as a logic saying whether it is used. {@code words-of :copy} is
 * {@code [value /part range /deep /types kinds]}, so
 * {@code apply :copy [[1 2 3 4 5] true 3]} is COPY/PART of three.
 *
 * <p>JEBOL took the first N values positionally, where N was the arity with no
 * refinements asked for, and dropped the rest. The refinement was never passed
 * and the call ran without it, so {@code apply :copy [[1 2 3 4 5] true 3]}
 * answered the whole series -- a wrong answer with no error, which is the worst
 * shape a defect can have. User-defined functions were unaffected, because a
 * refinement is an ordinary parameter to one of those.
 *
 * <p>Rebol's own suite uses APPLY thirteen times and not once on a built-in
 * with a refinement, so none of this was reachable from the suite. It was found
 * by asking two running interpreters -- `scripts/runtime-parity.py` -- and this
 * fix depends on the one before it: the walk needs WORDS-OF to answer.
 *
 * <p>Every expectation here was read off `./r3-head` first.
 */
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
