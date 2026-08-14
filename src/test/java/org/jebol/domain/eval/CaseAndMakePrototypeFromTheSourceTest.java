package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Two C behaviours Rebol's own CLEAN-PATH needs, read out of
 * {@code REBNATIVE(case)} in {@code src/core/n-control.c} and the
 * {@code A_MAKE} arm of {@code REBTYPE(String)} in
 * {@code src/core/t-string.c}.
 *
 * <p>Both are in one test file because they are one piece of work: CLEAN-PATH
 * uses each once, and it will not load without both. CLEAN-PATH is in
 * {@code mezz-files.reb}, which is where LIST-DIR lives, which is the first
 * thing {@code mezz-shell.reb} asks for. So these two natives are what stands
 * between JEBOL and twenty-five of Rebol's own functions.
 *
 * <p>Neither is about files. Both are general rules that happened to be
 * discovered through a file function, which is the argument for reading the C
 * rather than the caller.
 */
class CaseAndMakePrototypeFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";
    private static final String NO_ERROR = "no-error";

    @Nested
    @DisplayName("REBNATIVE(case): the block is walked two values at a time")
    class Case {

        @Test
        @DisplayName("a truthy condition with nothing after it answers logic true")
        void aTrailingTruthyConditionAnswersTrue() {
            assertThat(answerTo("case [true]")).isEqualTo(TRUE);
            assertThat(answerTo("case [false [1] true]")).isEqualTo(TRUE);
            assertThat(errorIdOf("case [true]")).isEqualTo(NO_ERROR);
        }

        @Test
        @DisplayName("the trailing condition is still evaluated, so its effect happens")
        void theTrailingConditionIsEvaluated() {
            assertThat(answerTo("x: 0 case [false [1] x: 9] x")).isEqualTo("9");
            assertThat(answerTo("x: 0 case [true [1] x: 9] x")).isEqualTo("0");
        }

        @Test
        @DisplayName("a false condition skips its branch without evaluating it")
        void aFalseConditionSkipsWithoutEvaluating() {
            assertThat(answerTo("x: 0 case [false [x: 1] true [x: 2]] x")).isEqualTo("2");
            assertThat(errorIdOf("case [false [1 / 0] true [1]]")).isEqualTo(NO_ERROR);
        }

        @Test
        @DisplayName("a branch that is not a block is its own answer")
        void aBareBranchAnswersItself() {
            assertThat(answerTo("case [true 1]")).isEqualTo("1");
            assertThat(answerTo("case [true \"a\"]")).isEqualTo("\"a\"");
            assertThat(answerTo("case [true [1]]")).isEqualTo("1");
        }

        @Test
        @DisplayName("no condition truthy answers none")
        void nothingTruthyAnswersNone() {
            assertThat(answerTo("case [false [1] false [2]]")).isEqualTo("_");
            assertThat(answerTo("case []")).isEqualTo("_");
        }

        @Test
        @DisplayName("the first truthy condition wins and the rest are not reached")
        void theFirstTruthyConditionWins() {
            assertThat(answerTo("x: 0 case [true [1] true [x: 9]] x")).isEqualTo("0");
            assertThat(answerTo("case [true [1] true [2]]")).isEqualTo("1");
        }

        @Test
        @DisplayName("/ALL keeps going, and the last branch it ran is the answer")
        void theAllRefinementKeepsGoing() {
            assertThat(answerTo("case/all [true [1] true [2]]")).isEqualTo("2");
            assertThat(answerTo("x: 0 case/all [true [x: 1] true [x: x + 1]] x"))
                    .isEqualTo("2");
        }
    }

    @Nested
    @DisplayName("A_MAKE: the first argument may be a value rather than a datatype")
    class MakeFromAPrototype {

        @Test
        @DisplayName("MAKE on a series value builds an empty series of that datatype")
        void aValueStandsForItsOwnDatatype() {
            assertThat(answerTo("file? make %a/b 10")).isEqualTo(TRUE);
            assertThat(answerTo("empty? make %a/b 10")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("it works for every string datatype, because the rule is about the value")
        void everyStringDatatype() {
            assertThat(answerTo("string? make \"abc\" 10")).isEqualTo(TRUE);
            assertThat(answerTo("empty? make \"abc\" 10")).isEqualTo(TRUE);
            assertThat(answerTo("tag? make <a> 10")).isEqualTo(TRUE);
            assertThat(answerTo("url? make http://a 10")).isEqualTo(TRUE);
            assertThat(answerTo("email? make (to email! \"a\") 10")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and for a block and a binary")
        void blocksAndBinaries() {
            assertThat(answerTo("block? make [1 2] 10")).isEqualTo(TRUE);
            assertThat(answerTo("empty? make [1 2] 10")).isEqualTo(TRUE);
            assertThat(answerTo("paren? make quote (1 2) 10")).isEqualTo(TRUE);
            assertThat(answerTo("binary? make #{01} 10")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("the number is a capacity and not a value, which is MAKE's whole difference from TO")
        void theNumberIsACapacity() {
            assertThat(answerTo("length? make [1 2] 10")).isEqualTo("0");
            assertThat(answerTo("length? make \"ab\" 10")).isEqualTo("0");
        }

        @Test
        @DisplayName("naming the datatype still works, because that is the second line")
        void aDatatypeStillWorks() {
            assertThat(answerTo("file? make file! 10")).isEqualTo(TRUE);
            assertThat(answerTo("block? make block! 10")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("MAKE on a value with none is refused")
        void noneIsRefused() {
            assertThat(errorIdOf("make %a none")).isNotEqualTo(NO_ERROR);
            assertThat(errorIdOf("make \"a\" none")).isNotEqualTo(NO_ERROR);
        }
    }
}
