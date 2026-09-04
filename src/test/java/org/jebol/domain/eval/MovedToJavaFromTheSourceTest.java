package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ten functions Rebol writes in C and JEBOL had written in REBOL.
 *
 * <p>Each was a fork by the layer rule -- a function Rebol implements in C is
 * Java here -- and each blocked the Rebol file that defines it from ever being
 * loaded over the top. They are tested together because they moved together,
 * and each is read from its own C function: {@code also} and {@code comment}
 * from {@code n-control.c}, {@code forever} and {@code forskip} from
 * {@code n-loop.c}, {@code to-value} and the four ordinals from
 * {@code n-data.c}, {@code unique} from {@code n-sets.c}.
 *
 * <p>Three of them behave in ways the REBOL versions had got wrong, and all
 * three come from reading the C rather than the name. COMMENT evaluates its
 * argument and throws the value away, where the REBOL version quoted it and so
 * threw the work away too. FORSKIP walks backwards from the tail when the step
 * is negative. And FORSKIP puts the word back where it started, unless BREAK
 * left the loop -- because the C returns before the line that restores it.
 */
class MovedToJavaFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("ALSO and COMMENT")
    class TwoThatDropAValue {

        @Test
        @DisplayName("ALSO answers its first argument")
        void alsoAnswersTheFirst() {
            assertThat(answerTo("also 1 2")).isEqualTo("1");
        }

        @Test
        @DisplayName("and evaluates the second, whose value is dropped")
        void alsoStillEvaluatesTheSecond() {
            assertThat(answerTo("n: 0 also 1 (n: 9) n")).isEqualTo("9");
        }

        @Test
        @DisplayName("COMMENT answers nothing at all")
        void commentAnswersUnset() {
            assertThat(answerTo("unset? comment \"a note\"")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and evaluates what it was given, ignoring the value rather than the work")
        void commentStillEvaluates() {
            assertThat(answerTo("n: 0 comment (n: 9) n")).isEqualTo("9");
        }
    }

    @Nested
    @DisplayName("TO-VALUE")
    class TurningAnUnsetIntoNone {

        @Test
        @DisplayName("an unset becomes none")
        void anUnsetBecomesNone() {
            assertThat(answerTo("none? to-value ()")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and everything else is answered as it stands")
        void everythingElseStands() {
            assertThat(answerTo("to-value 5")).isEqualTo("5");
            assertThat(answerTo("none? to-value none")).isEqualTo(TRUE);
            assertThat(answerTo("to-value \"a\"")).isEqualTo("\"a\"");
        }
    }

    @Nested
    @DisplayName("the four ordinals")
    class TheOrdinals {

        @Test
        @DisplayName("each is PICK with the position written into the name")
        void eachIsAPick() {
            String block = "b: [1 2 3 4 5 6 7 8 9 10] ";
            assertThat(answerTo(block + "reduce [seventh b eighth b ninth b tenth b]"))
                    .isEqualTo("[7 8 9 10]");
        }

        @Test
        @DisplayName("and answers none past the end, as PICK does")
        void pastTheEndIsNone() {
            assertThat(answerTo("none? seventh [1 2 3]")).isEqualTo(TRUE);
            assertThat(answerTo("none? tenth [1 2 3]")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("they count from where the series is, not from its head")
        void theyCountFromHere() {
            assertThat(answerTo("b: skip [1 2 3 4 5 6 7 8 9 10] 2 seventh b"))
                    .isEqualTo("9");
        }

        @Test
        @DisplayName("and a string answers a character")
        void aStringAnswersACharacter() {
            assertThat(answerTo("seventh \"abcdefgh\"")).isEqualTo("#\"g\"");
        }
    }

    @Nested
    @DisplayName("UNIQUE")
    class DroppingRepeats {

        @Test
        @DisplayName("keeps the first of each")
        void itKeepsTheFirst() {
            assertThat(answerTo("unique [1 2 1 3 2]")).isEqualTo("[1 2 3]");
        }

        @Test
        @DisplayName("and works on a string as well as a block")
        void aStringToo() {
            assertThat(answerTo("unique \"abcabc\"")).isEqualTo("\"abc\"");
            assertThat(answerTo("reduce [union \"abc\" \"bd\" "
                    + "intersect \"abc\" \"bd\" exclude \"abc\" \"bd\"]"))
                    .isEqualTo("[\"abcd\" \"b\" \"ac\"]");
        }

        @Test
        @DisplayName("and on a map, whose members are its keys")
        void aMapToo() {
            assertThat(answerTo(
                    "keys-of union make map! [a 1] make map! [b 2]"))
                    .isEqualTo("[a b]");
            assertThat(answerTo(
                    "keys-of intersect make map! [a 1 b 2] make map! [b 9]"))
                    .isEqualTo("[b]");
        }

        @Test
        @DisplayName("/CASE makes two spellings two values")
        void caseMattersWhenAsked() {
            assertThat(answerTo("unique [\"a\" \"A\"]")).isEqualTo("[\"a\"]");
            assertThat(answerTo("unique/case [\"a\" \"A\"]"))
                    .isEqualTo("[\"a\" \"A\"]");
        }

        @Test
        @DisplayName("and an empty series has nothing to drop")
        void theEmptyCase() {
            assertThat(answerTo("unique []")).isEqualTo("[]");
        }
    }

    @Nested
    @DisplayName("FOREVER")
    class TheEndlessLoop {

        @Test
        @DisplayName("runs until something leaves the loop, and answers what it carried")
        void itRunsUntilSomethingLeaves() {
            assertThat(answerTo("n: 0 forever [n: n + 1 if n > 2 [break/return n]]"))
                    .isEqualTo("3");
        }

        @Test
        @DisplayName("a plain BREAK leaves it answering unset")
        void aPlainBreakAnswersUnset() {
            assertThat(answerTo("n: 0 unset? forever [n: n + 1 if n > 2 [break]]"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a THROW travels out through it")
        void aThrowTravelsOut() {
            assertThat(answerTo("catch [forever [throw 7]]")).isEqualTo("7");
        }
    }

    @Nested
    @DisplayName("FORSKIP")
    class WalkingBySteps {

        @Test
        @DisplayName("walks the word by the step it was given")
        void itWalksByTheStep() {
            assertThat(answerTo(
                    "b: [1 2 3 4 5] c: copy [] forskip b 2 [append c first b] c"))
                    .isEqualTo("[1 3 5]");
        }

        @Test
        @DisplayName("and puts the word back where it started")
        void itRestoresTheWord() {
            assertThat(answerTo(
                    "b: [1 2 3 4] forskip b 2 [] index? b")).isEqualTo("1");
        }

        @Test
        @DisplayName("unless BREAK left the loop, which stops before the restoring")
        void aBreakLeavesTheWordWhereItStopped() {
            assertThat(answerTo(
                    "b: [1 2 3 4] forskip b 1 [if 3 = first b [break]] index? b"))
                    .isEqualTo("3");
        }

        @Test
        @DisplayName("a negative step walks backwards from the tail")
        void aNegativeStepWalksBack() {
            assertThat(answerTo(
                    "b: tail [1 2 3 4] c: copy [] forskip b -1 [append c first b] c"))
                    .isEqualTo("[4 3 2 1]");
        }

        @Test
        @DisplayName("a word holding none answers none rather than raising")
        void aNoneWordAnswersNone() {
            assertThat(answerTo("b: none none? forskip b 1 [1]")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a word holding something that is not a series is refused")
        void anythingElseIsRefused() {
            assertThat(errorIdFrom("b: 5 forskip b 1 [1]")).isNotEqualTo("no-error");
        }

        @Test
        @DisplayName("a step of zero is refused rather than looping for ever")
        void aStepOfZeroIsRefused() {
            assertThat(errorIdFrom("b: [1 2] forskip b 0 [1]")).isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("FORALL is the same walk with a step of one")
        void forallIsTheSameWalk() {
            assertThat(answerTo(
                    "b: [1 2 3] c: copy [] forall b [append c first b] "
                    + "reduce [c index? b]")).isEqualTo("[[1 2 3] 1]");
        }
    }
}
