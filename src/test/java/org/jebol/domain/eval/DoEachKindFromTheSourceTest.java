package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What DO does with each kind of value, out of {@code REBNATIVE(do)} in
 * {@code n-control.c}.
 *
 * <p>The arm that matters is the word: {@code *D_RET = *Get_Var(value); if
 * (ANY_FUNC(D_RET)) VAL_SET_OPT(D_RET, OPTS_REVAL);}. So {@code do 'a} runs what
 * A holds -- calling it when A holds a function -- and that is how a script runs
 * a name it was handed rather than one it wrote.
 *
 * <p>{@code OPTS_REVAL} is set on four arms and no others: a function value, a
 * path, a word and a get-word. So {@code do [f]} and {@code do 'f} are different
 * questions, and only the second calls F.
 *
 * <p>Specified in {@code spec/natives.allium} under "What DO does with each kind
 * of value".
 */
class DoEachKindFromTheSourceTest {

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
    @DisplayName("a word")
    class AWord {

        @Test
        @DisplayName("runs what the word holds")
        void itRunsWhatTheWordHolds() {
            assertThat(answerTo("a: 23 do 'a")).isEqualTo("23");
            assertThat(answerTo("logic? do 'true")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and calls it when the word holds a function")
        void itCallsAFunction() {
            assertThat(answerTo("a: does [\"OK\"] do 'a")).isEqualTo("\"OK\"");
            assertThat(answerTo("f: func [x] [x * 2] do 'f 5")).isEqualTo("10");
        }

        @Test
        @DisplayName("a word holding a block answers the block rather than running it")
        void aBlockIsNotRun() {
            assertThat(answerTo("b: [print \"x\"] block? do 'b")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and IN names a word in an object, which DO then runs")
        void inThenDo() {
            assertThat(answerTo(
                    "o: make object! [a: does [\"OK\"] b: 23] "
                    + "reduce [do in o 'a do in o 'b]")).isEqualTo("[\"OK\" 23]");
        }
    }

    @Nested
    @DisplayName("a path")
    class APath {

        @Test
        @DisplayName("runs what the path reads")
        void itRunsWhatThePathReads() {
            assertThat(answerTo(
                    "o: make object! [a: does [\"OK\"] b: 23] "
                    + "reduce [do 'o/a do 'o/b]")).isEqualTo("[\"OK\" 23]");
        }

        @Test
        @DisplayName("and a field the object has not got refuses")
        void anAbsentFieldRefuses() {
            assertThat(errorIdFrom("o: make object! [a: 1] do 'o/x"))
                    .isEqualTo("invalid-path");
        }

        @Test
        @DisplayName("a path into a block reads the item, and does not run it")
        void aPathIntoABlock() {
            assertThat(answerTo("b: [[\"OK\"]] (do first [b/1]) = [\"OK\"]"))
                    .isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("a quoted name")
    class AQuotedName {

        @Test
        @DisplayName("answers the plain name, having spent its quote")
        void itSpendsTheQuote() {
            assertThat(answerTo("b: ['a 'a/1] reduce [type? do first b type? do second b]"))
                    .isEqualTo("[#(word!) #(path!)]");
        }
    }

    @Nested
    @DisplayName("a set-word or a set-path")
    class HalfAnExpression {

        @Test
        @DisplayName("is refused, because there is nothing to assign")
        void itIsRefused() {
            assertThat(errorIdFrom("do quote a:")).isEqualTo("invalid-arg");
            assertThat(errorIdFrom("do quote a/1:")).isEqualTo("invalid-arg");
        }
    }

    @Nested
    @DisplayName("a block, and everything else")
    class TheRest {

        @Test
        @DisplayName("a block evaluates")
        void aBlockEvaluates() {
            assertThat(answerTo("do [1 + 2]")).isEqualTo("3");
            assertThat(answerTo("a: [[1 + 2]] b: [a a/1] (do first b) = [[1 + 2]]"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("a: [[1 + 2]] b: [a a/1] (do second b) = [1 + 2]"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a value that is not code answers itself")
        void aPlainValueAnswersItself() {
            assertThat(answerTo("do 5")).isEqualTo("5");
            assertThat(answerTo("do \"1 + 2\"")).isEqualTo("3");
        }
    }

    @Nested
    @DisplayName("TO WORD! of a datatype")
    class DatatypeToWord {

        @Test
        @DisplayName("is the word that names it, exclamation mark and all")
        void itIsTheSpelling() {
            assertThat(answerTo("'logic! = to word! logic!")).isEqualTo(TRUE);
            assertThat(answerTo("'percent! = to word! percent!")).isEqualTo(TRUE);
            assertThat(answerTo("'money! = to word! money!")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the trip back gives the datatype again")
        void theTripBack() {
            assertThat(answerTo("logic! = to datatype! to word! logic!"))
                    .isEqualTo(TRUE);
        }
    }
}
