package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seven refinements JEBOL had not got, and the constant PI.
 *
 * <p>Specified in {@code spec/natives.allium} and measured against a real
 * R3 3.22.1.
 *
 * <p>Each test here pairs the refined call with the plain one. A
 * refinement that is accepted and then changes nothing passes any test
 * that only looks at the refined side, and that is the shape this ends up
 * in if the pair is not written down.
 */
class DeclaredRefinementsTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    /** The id of the error a snippet raises, or "no-error" if it raises none. */
    private static String errorIdOf(String source) {
        return answerTo(
                "e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("PI is a constant to the last bit, not to the digits it prints")
    void piKeepsItsBits() {
        assertThat(answerTo("pi = 3.141592653589793")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("CASE/ALL runs every matching branch and answers the last")
    void caseAllRunsEveryMatchingBranch() {
        assertThat(answerTo("case/all [true [1] true [2]]")).isEqualTo("2");
        assertThat(answerTo("case [true [1] true [2]]"))
                .as("plain CASE stops at the first")
                .isEqualTo("1");
    }

    @Test
    @DisplayName("CASE/ALL with an empty branch answers unset")
    void caseAllAnswersUnsetForAnEmptyBranch() {
        assertThat(answerTo("unset? case/all [true [1] true []]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("CASE/ALL with nothing matching answers none")
    void caseAllAnswersNoneWhenNothingMatches() {
        assertThat(answerTo("none? case/all [false [1] false [2]]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("CASE/ALL skips the branches whose condition is false")
    void caseAllSkipsFalseBranches() {
        assertThat(answerTo("case/all [false [1] true [2]]")).isEqualTo("2");
    }

    @Test
    @DisplayName("GET/ANY answers unset where GET raises")
    void getAnyAnswersUnsetForAWordWithNoValue() {
        assertThat(answerTo("unset? get/any 'xyz")).isEqualTo("#(true)");
        assertThat(errorIdOf("get 'xyz"))
                .as("plain GET still refuses")
                .isEqualTo("no-value");
    }

    @Test
    @DisplayName("GET/ANY of a word that has a value answers the value")
    void getAnyIsUnchangedForAnOrdinaryWord() {
        assertThat(answerTo("q: 5 get/any 'q")).isEqualTo("5");
    }

    @Test
    @DisplayName("REDUCE/NO-SET leaves a set-word alone and assigns nothing")
    void reduceNoSetLeavesSetWordsInPlace() {
        assertThat(answerTo("mold reduce/no-set [x: 1 + 2]")).isEqualTo("\"[x: 3]\"");
        assertThat(answerTo("mold reduce [1 + 2]"))
                .as("plain REDUCE assigns and drops the set-word")
                .isEqualTo("\"[3]\"");
    }

    @Test
    @DisplayName("REDUCE/NO-SET leaves a set-path alone too")
    void reduceNoSetLeavesSetPathsInPlace() {
        assertThat(answerTo("mold reduce/no-set [x/1: 1 + 2]")).isEqualTo("\"[x/1: 3]\"");
    }

    @Test
    @DisplayName("REDUCE/NO-SET reduces normally when there is no set-word")
    void reduceNoSetIsOrdinaryOtherwise() {
        assertThat(answerTo("mold reduce/no-set [1 + 2]")).isEqualTo("\"[3]\"");
    }

    @Test
    @DisplayName("the trigonometric natives take radians when asked")
    void theTrigonometryTakesRadians() {
        assertThat(answerTo("-1.0 = cosine/radians pi")).isEqualTo("#(true)");
        assertThat(answerTo("0.0 = sine/radians 0")).isEqualTo("#(true)");
        assertThat(answerTo("0.0 = tangent/radians 0")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the inverse trigonometric natives answer radians when asked")
    void theInverseTrigonometryAnswersRadians() {
        assertThat(answerTo("(arccosine/radians 0) - (pi / 2) < 1E-13")).isEqualTo("#(true)");
        assertThat(answerTo("0.0 = arcsine/radians 0")).isEqualTo("#(true)");
        assertThat(answerTo("0.0 = arctangent/radians 0")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the trigonometric natives still take degrees without the refinement")
    void theTrigonometryIsUnchangedInDegrees() {
        assertThat(answerTo("0.5 = cosine 60")).isEqualTo("#(true)");
        assertThat(answerTo("90.0 = arccosine 0")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("SHIFT/LOGICAL fills with zeros rather than keeping the sign")
    void shiftLogicalIgnoresTheSign() {
        assertThat(answerTo("-9223372036854775808 = shift/logical 1 63")).isEqualTo("#(true)");
        assertThat(answerTo("shift/logical -9223372036854775808 -63")).isEqualTo("1");
        assertThat(answerTo("shift/logical -9223372036854775808 -64")).isEqualTo("0");
    }

    @Test
    @DisplayName("SHIFT keeps the sign without the refinement")
    void shiftIsUnchangedWithoutTheRefinement() {
        assertThat(answerTo("shift -8 -1")).isEqualTo("-4");
        assertThat(answerTo("shift/logical 8 -1")).isEqualTo("4");
    }

    @Test
    @DisplayName("SHIFT/LOGICAL of zero is zero, whichever way")
    void shiftLogicalOfZeroIsZero() {
        assertThat(answerTo("shift/logical 0 5")).isEqualTo("0");
        assertThat(answerTo("shift/logical 0 -5")).isEqualTo("0");
    }

    @Test
    @DisplayName("PUT/CASE matches its key exactly")
    void putCaseDoesNotFoldCase() {
        assertThat(answerTo(
                "v: reduce [\"A\" 1 \"a\" 2] put/case v \"A\" 4 v = reduce [\"A\" 4 \"a\" 2]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("PUT folds case without the refinement")
    void putFoldsCaseWithoutTheRefinement() {
        assertThat(answerTo(
                "w: reduce [\"A\" 1 \"a\" 2] put w \"a\" 9 w = reduce [\"A\" 9 \"a\" 2]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("SELECT/SAME tells 1, 1.0 and 100% apart")
    void selectSameComparesByExactDatatype() {
        assertThat(answerTo("first select/same [1.0 [1] 1 [2]] 1")).isEqualTo("2");
        assertThat(answerTo("first select/same [1 [1] 1.0 [2] 100% [3]] 1.0")).isEqualTo("2");
        assertThat(answerTo("first select/same [1 [1] 1.0 [2] 100% [3]] 100%")).isEqualTo("3");
    }

    @Test
    @DisplayName("plain SELECT treats them as one key")
    void selectFoldsTheNumbersTogether() {
        assertThat(answerTo("first select [1.0 [1] 1 [2]] 1")).isEqualTo("1");
    }

    @Test
    @DisplayName("SELECT/SAME on a string stops folding case")
    void selectSameIsCaseSensitiveOnAString() {
        assertThat(answerTo("#\"b\" = select/same \"aAbcdAe\" \"A\"")).isEqualTo("#(true)");
        assertThat(answerTo("#\"e\" = select/same/last \"aAbcdAe\" \"A\"")).isEqualTo("#(true)");
    }
}
