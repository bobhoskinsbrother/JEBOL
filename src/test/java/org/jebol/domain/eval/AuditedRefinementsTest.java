package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Refinements found by comparing the whole library against Rebol's own
 * declarations rather than by a failing assertion.
 *
 * <p>{@code boot/actions.reb} and {@code boot/natives.reb} declare all 224 C
 * functions with every refinement, so the complete list of what JEBOL is
 * missing can be taken in one pass. {@code copy/deep} had never worked and no
 * test had ever asked it to.
 *
 * <p>{@code scripts/c-parity.py} is that pass, and Goal 3 in TODO.md is what it
 * still reports.
 */
class AuditedRefinementsTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("COPY/DEEP copies what the block holds, not just the block")
    void copyDeepReachesInside() {
        assertThat(answerTo(
                "b: [[1]] c: copy/deep b append first c 2 (first b) = [1]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("COPY without /DEEP shares what the block holds")
    void copyWithoutDeepSharesTheInsides() {
        assertThat(answerTo(
                "b: [[1]] c: copy b append first c 2 (first b) = [1 2]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("COPY/PART takes the first few")
    void copyPartTakesTheFront() {
        assertThat(answerTo("(copy/part [1 2 3] 2) = [1 2]")).isEqualTo("#(true)");
        assertThat(answerTo("(copy/part \"abc\" 2) = \"ab\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("COPY/PART and /DEEP work together")
    void copyPartAndDeepCombine() {
        assertThat(answerTo("(copy/part/deep [[1] [2] [3]] 2) = [[1] [2]]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("and COPY refuses what its own declaration does not name")
    void copyOfANonSeriesIsRefused() {
        assertThat(answerTo(
                "e: try [copy 5] either error? e [e/id] ['no-error]"))
                .isEqualTo("expect-arg");
        assertThat(answerTo(
                "e: try [copy make gob! []] either error? e [e/id] ['no-error]"))
                .isEqualTo("expect-arg");
    }

    @Test
    @DisplayName("UPPERCASE/PART and LOWERCASE/PART change only the front")
    void theCaseChangesTakeAPart() {
        assertThat(answerTo("(uppercase/part \"abcd\" 2) = \"ABcd\"")).isEqualTo("#(true)");
        assertThat(answerTo("(lowercase/part \"ABCD\" 2) = \"abCD\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the count runs from the position, not from the head")
    void theCaseChangeCountsFromWhereItIs() {
        assertThat(answerTo("(uppercase/part next \"abcd\" 2) = \"BCd\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a part of zero changes nothing and a part past the end changes everything")
    void theCaseChangeAtItsBoundaries() {
        assertThat(answerTo("(uppercase/part \"abcd\" 0) = \"abcd\"")).isEqualTo("#(true)");
        assertThat(answerTo("(uppercase/part \"abcd\" 9) = \"ABCD\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("REVERSE/PART turns round only the front")
    void reversePartLeavesTheRest() {
        assertThat(answerTo("(reverse/part [1 2 3 4] 2) = [2 1 3 4]")).isEqualTo("#(true)");
        assertThat(answerTo("(reverse/part \"abcd\" 3) = \"cbad\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("REVERSE changes the series it was given")
    void reversePartChangesInPlace() {
        assertThat(answerTo("b: [1 2 3 4] reverse/part b 2 b = [2 1 3 4]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("TYPE?/WORD answers a word and TYPE? answers a datatype")
    void typeOfAsAWord() {
        assertThat(answerTo("(type?/word 1) = 'integer!")).isEqualTo("#(true)");
        assertThat(answerTo("word? type?/word 1")).isEqualTo("#(true)");
        assertThat(answerTo("(type? 1) = integer!"))
                .as("without the refinement it is still the datatype")
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("IF/ONLY, EITHER/ONLY and UNLESS/ONLY hand the branch back unrun")
    void theConditionalsCanWithhold() {
        assertThat(answerTo("(if/only true [1]) = [1]")).isEqualTo("#(true)");
        assertThat(answerTo("(either/only true [1] [2]) = [1]")).isEqualTo("#(true)");
        assertThat(answerTo("(either/only false [1] [2]) = [2]")).isEqualTo("#(true)");
        assertThat(answerTo("(unless/only false [1]) = [1]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("without /ONLY the branch is still run")
    void theConditionalsRunTheBranchOtherwise() {
        assertThat(answerTo("(if true [1]) = 1")).isEqualTo("#(true)");
        assertThat(answerTo("(either true [1] [2]) = 1")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/ONLY does not make a false condition take the branch")
    void onlyDoesNotChangeWhichBranchIsChosen() {
        assertThat(answerTo("none? if/only false [1]")).isEqualTo("#(true)");
        assertThat(answerTo("none? unless/only true [1]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the set operations stop folding case when asked")
    void theSetOperationsCanMindCase() {
        assertThat(answerTo("(union/case [\"a\"] [\"A\"]) = [\"a\" \"A\"]")).isEqualTo("#(true)");
        assertThat(answerTo("(intersect/case [\"a\" \"b\"] [\"A\" \"b\"]) = [\"b\"]"))
                .isEqualTo("#(true)");
        assertThat(answerTo("(difference/case [\"a\"] [\"A\"]) = [\"a\" \"A\"]"))
                .isEqualTo("#(true)");
        assertThat(answerTo("(exclude/case [\"a\" \"b\"] [\"A\"]) = [\"a\" \"b\"]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the set operations fold case without the refinement")
    void theSetOperationsFoldCaseOtherwise() {
        assertThat(answerTo("(union [\"a\"] [\"A\"]) = [\"a\"]")).isEqualTo("#(true)");
        assertThat(answerTo("empty? difference [\"a\"] [\"A\"]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/SKIP reads records and the first field decides")
    void skipComparesByTheRecordsKey() {
        assertThat(answerTo("(union/skip [1 2 1 3] [1 2] 2) = [1 2]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("SORT/UNSTABLE names an algorithm, not a different answer")
    void unstableSortsTheSame() {
        assertThat(answerTo("(sort/unstable [3 1 2]) = [1 2 3]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("CONTEXT/ONLY builds the same object")
    void contextOnlyIsAccepted() {
        assertThat(answerTo("(context/only [a: 1]) = context [a: 1]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("REQUEST-COLOR has no /RGB16, and a script that writes one is told so")
    void requestColourHasNoSixteenBitRefinement() {
        assertThat(answerTo(
                "either error? e: try [request-color/rgb16] [e/id] ['accepted]"))
                .as("the audit reads in both directions: a refinement JEBOL invented "
                        + "is as much a difference as one it is missing, and a script "
                        + "written against it would not run on a real Rebol")
                .isEqualTo("no-refine");
    }
}
