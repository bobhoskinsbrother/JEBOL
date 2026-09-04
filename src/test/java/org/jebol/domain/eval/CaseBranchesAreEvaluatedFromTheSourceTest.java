package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CASE evaluates its branch, and runs the branch only if what it evaluated to
 * is a block.
 *
 * <p>JEBOL took a branch that was not a block literally, so {@code case [true
 * x]} answered the word {@code x} rather than what {@code x} held. The C does
 * two things in a row and JEBOL was doing neither: {@code index =
 * Do_Next(block, index, 0)} evaluates one expression, and then {@code if
 * (IS_BLOCK(ds)) ds = DO_BLK(ds)} runs the answer if the answer is a block.
 *
 * <p>Both halves matter and they are separate. One expression is not one
 * value, so {@code case [true 1 + 2]} is three. And it is the *result* that is
 * tested for being a block, not the source, so a word holding a block has its
 * block run.
 *
 * <p>Found through REWORD, which is thirty lines of Rebol's own REBOL and
 * opens with {@code wtype: lib/case [case binary! tag? source string! 'else
 * type? source]}. Every branch there is a bare word, so every one of them came
 * back as a word and the next line asked TO for a datatype it had not got.
 * Twenty-three assertions were failing on this one line.
 */
class CaseBranchesAreEvaluatedFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String moldOf(String source) {
        return answerTo("mold " + source).replace("\"", "");
    }

    @Nested
    @DisplayName("the branch is one expression, evaluated")
    class TheBranchIsEvaluated {

        @Test
        @DisplayName("a word answers what it holds, not itself")
        void awordAnswersWhatItHolds() {
            assertThat(answerTo("x: 5 case [true x]")).isEqualTo("5");
        }

        @Test
        @DisplayName("a datatype word answers the datatype, which is what REWORD needs")
        void adatatypeWordAnswersTheDatatype() {
            assertThat(moldOf("case [false binary! true string!]")).isEqualTo("#(string!)");
            assertThat(moldOf("type? case [false binary! true string!]"))
                    .isEqualTo("#(datatype!)");
        }

        @Test
        @DisplayName("and NONE answers none rather than the word none")
        void noneAnswersNone() {
            assertThat(moldOf("case [true none]")).isEqualTo("_");
        }

        @Test
        @DisplayName("a whole expression is taken, not the first value of one")
        void awholeExpressionIsTaken() {
            assertThat(answerTo("case [true 1 + 2]"))
                    .as("Do_Next reads an expression, and 1 + 2 is one expression")
                    .isEqualTo("3");
            assertThat(answerTo("case [true add 1 2]")).isEqualTo("3");
        }

        @Test
        @DisplayName("a value that is already a value is itself")
        void aplainValueIsItself() {
            assertThat(answerTo("case [true 1]")).isEqualTo("1");
            assertThat(answerTo("case [true (3 + 4)]")).isEqualTo("7");
        }
    }

    @Nested
    @DisplayName("the answer is run only when the answer is a block")
    class TheAnswerIsRunWhenItIsABlock {

        @Test
        @DisplayName("a block written in place is run")
        void ablockInPlaceIsRun() {
            assertThat(answerTo("case [true [1 + 2]]")).isEqualTo("3");
        }

        @Test
        @DisplayName("and so is a block a word was holding")
        void ablockFromAWordIsRunToo() {
            assertThat(answerTo("b: [1 2] case [true b]"))
                    .as("the C tests what the branch evaluated to, not what was "
                            + "written, so a word holding a block is a block")
                    .isEqualTo("2");
        }

        @Test
        @DisplayName("and there is no way to have the block back instead")
        void ablockCannotBeKept() {
            assertThat(answerTo("case [true quote [1 2]]"))
                    .as("QUOTE stops the branch being read as source but not the "
                            + "block it answers being run, because the C tests the "
                            + "answer and by then the quoting has already happened")
                    .isEqualTo("2");
        }
    }

    @Nested
    @DisplayName("what happens at the ends")
    class TheEnds {

        @Test
        @DisplayName("a true condition with nothing after it answers true")
        void atrueConditionWithNoBranch() {
            assertThat(answerTo("case [true]")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and a false one with nothing after it answers none")
        void afalseConditionWithNoBranch() {
            assertThat(moldOf("case [false]")).isEqualTo("_");
        }

        @Test
        @DisplayName("nothing matching answers none")
        void nothingMatches() {
            assertThat(moldOf("case [false 1 false 2]")).isEqualTo("_");
        }

        @Test
        @DisplayName("/ALL keeps going and answers the last branch it took")
        void allKeepsGoing() {
            assertThat(answerTo("case/all [true 1 true 2]")).isEqualTo("2");
            assertThat(answerTo("n: 0 case/all [true n: n + 1 true n: n + 1] n"))
                    .isEqualTo("2");
        }
    }

    @Nested
    @DisplayName("REWORD, which is what found this")
    class Rewording {

        @Test
        @DisplayName("substitutes a keyword")
        void substitutesAKeyword() {
            assertThat(answerTo("reword {$a} [a 1]").replace("\"", "")).isEqualTo("1");
            assertThat(answerTo("reword {$a$A$a} [a 1 A 2]").replace("\"", ""))
                    .as("keywords fold case unless /CASE says otherwise")
                    .isEqualTo("222");
            assertThat(answerTo("reword/case {$a$A$a} [a 1 A 2]").replace("\"", ""))
                    .isEqualTo("121");
        }

        @Test
        @DisplayName("and /ESCAPE chooses the delimiters")
        void escapeChoosesTheDelimiters() {
            assertThat(answerTo("reword/escape {ba} [a 1 b 2] none").replace("\"", ""))
                    .isEqualTo("21");
            assertThat(answerTo("reword/escape {<bang>} [bang {!}] [{<} {>}]")
                    .replace("\"", "")).isEqualTo("!");
        }
    }
}
