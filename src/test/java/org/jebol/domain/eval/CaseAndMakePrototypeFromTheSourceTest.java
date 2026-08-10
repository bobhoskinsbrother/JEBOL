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
            // `if (index >= SERIES_TAIL(block)) return R_TRUE;` -- reached
            // after a condition came out truthy and the walk found no branch
            // to pair with it. This is what makes a trailing expression a
            // default clause whose side effect is the point, and Rebol's own
            // CLEAN-PATH ends with exactly that shape.
            assertThat(answerTo("case [true]")).isEqualTo(TRUE);
            assertThat(answerTo("case [false [1] true]")).isEqualTo(TRUE);
            assertThat(errorIdOf("case [true]")).isEqualTo(NO_ERROR);
        }

        @Test
        @DisplayName("the trailing condition is still evaluated, so its effect happens")
        void theTrailingConditionIsEvaluated() {
            // The point of the shape: the answer is thrown away and the
            // assignment is not.
            assertThat(answerTo("x: 0 case [false [1] x: 9] x")).isEqualTo("9");
            assertThat(answerTo("x: 0 case [true [1] x: 9] x")).isEqualTo("0");
        }

        @Test
        @DisplayName("a false condition skips its branch without evaluating it")
        void aFalseConditionSkipsWithoutEvaluating() {
            // `if (IS_FALSE(ds)) index++;` -- the branch is stepped over, not
            // run. So a branch that would fail is harmless behind a false
            // condition.
            assertThat(answerTo("x: 0 case [false [x: 1] true [x: 2]] x")).isEqualTo("2");
            assertThat(errorIdOf("case [false [1 / 0] true [1]]")).isEqualTo(NO_ERROR);
        }

        @Test
        @DisplayName("a branch that is not a block is its own answer")
        void aBareBranchAnswersItself() {
            // The branch is evaluated once as a value and run a second time
            // only `if (IS_BLOCK(ds))`. So a bare value is not a mistake.
            assertThat(answerTo("case [true 1]")).isEqualTo("1");
            assertThat(answerTo("case [true \"a\"]")).isEqualTo("\"a\"");
            assertThat(answerTo("case [true [1]]")).isEqualTo("1");
        }

        @Test
        @DisplayName("no condition truthy answers none")
        void nothingTruthyAnswersNone() {
            // `return R_NONE;` after the loop runs out.
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
            // `type = VAL_TYPE(value); if (type == REB_DATATYPE) type =
            // VAL_DATATYPE(value);` -- two lines, and the first is the one
            // that matters: MAKE takes a value as readily as a datatype and
            // reads the value's own datatype off it.
            //
            // Rebol's own CLEAN-PATH writes `out: make file length? file` with
            // the comment "same datatype", which is the whole reason the rule
            // exists: it wants an empty series of whatever kind it was handed.
            assertThat(answerTo("file? make %a/b 10")).isEqualTo(TRUE);
            assertThat(answerTo("empty? make %a/b 10")).isEqualTo(TRUE);
            // Not asserted here: R3 molds the empty file as %"" and JEBOL
            // molds it as a bare %, which does not read back. That is a
            // molder defect rather than a MAKE one -- s-mold.c quotes a file
            // that needs it -- and it belongs to the reader and molder work
            // in TODO.md. Found by this test, recorded rather than widened
            // into it.
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
            // `make_string(arg, action == A_MAKE, type)` -- the MAKE flag is
            // what turns the number into a capacity. TO would read it as
            // something to convert.
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
            // `if (IS_NONE(arg)) Trap_Make(type, arg);` -- before anything
            // else, so it applies to the prototype form as well.
            assertThat(errorIdOf("make %a none")).isNotEqualTo(NO_ERROR);
            assertThat(errorIdOf("make \"a\" none")).isNotEqualTo(NO_ERROR);
        }
    }
}
