package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WordsBoundToAFunctionFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return withoutTheDelimitersAroundAText(
                interpreter.display(interpreter.run(source)));
    }

    private static String withoutTheDelimitersAroundAText(String shown) {
        return isWrappedIn(shown, '"', '"') || isWrappedIn(shown, '{', '}')
                ? shown.substring(1, shown.length() - 1)
                : shown;
    }

    private static boolean isWrappedIn(String shown, char opening, char closing) {
        return shown.length() >= 2
                && shown.charAt(0) == opening
                && shown.charAt(shown.length() - 1) == closing;
    }

    @Nested
    @DisplayName("one word in a body is the same word on every call")
    class TheSameWordOnEveryCall {

        @Test
        @DisplayName("a word naming the function's own argument, passed to a call of itself")
        void awordNamingItsOwnArgument() {
            assertThat(answerTo("""
                    checker: func [depth /token given] [
                        either depth > 0 [checker/token depth - 1 'given] [same? :given 'given]
                    ]
                    checker 1"""))
                    .as("comparing the frames themselves says these are two words, "
                            + "and Rebol's ARRAY reads exactly this to know it called itself")
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("but the same word written outside the function is a different word")
        void thesameWordWrittenOutside() {
            assertThat(answerTo("""
                    checker: func [depth /token given] [
                        either depth > 0 [checker/token depth - 1 'given] [same? :given 'given]
                    ]
                    checker/token 0 'given"""))
                    .isEqualTo("#(false)");
        }

        @Test
        @DisplayName("and two functions naming the same argument do not share it")
        void twofunctionsNamingTheSameArgument() {
            assertThat(answerTo("""
                    theirs: func [given] ['given]
                    ours: func [given] [same? :given 'given]
                    ours theirs 1"""))
                    .isEqualTo("#(false)");
        }

        @Test
        @DisplayName("though equality ignores the binding and calls them equal")
        void equalityIgnoresTheBinding() {
            assertThat(answerTo("""
                    theirs: func [given] ['given]
                    equal? theirs 1 'given"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a closure's words stay its own call's words")
        void aclosuresWordsStayItsOwn() {
            assertThat(answerTo("""
                    checker: closure [depth /token given] [
                        either depth > 0 [checker/token depth - 1 'given] [same? :given 'given]
                    ]
                    checker 1"""))
                    .as("a closure's body is bound to a real object for each call, "
                            + "so the C's relative binding does not apply to it")
                    .isEqualTo("#(false)");
        }
    }

    @Nested
    @DisplayName("a word reads the innermost running call of its function")
    class ReadingTheInnermostCall {

        @Test
        @DisplayName("a word handed down to a call of the same function")
        void awordHandedDown() {
            assertThat(answerTo("""
                    counting: func [depth carried /local here] [
                        here: depth * 10
                        either depth > 0 [
                            counting depth - 1 either carried [carried] [[here]]
                        ] [
                            do carried
                        ]
                    ]
                    counting 3 none"""))
                    .as("the block holding HERE was written down by the outermost call, "
                            + "and Rebol reads the innermost call's HERE when it runs")
                    .isEqualTo("0");
        }

        @Test
        @DisplayName("and reads the outer call's own copy again once the inner one ends")
        void readsTheOuterCopyAgainAfterwards() {
            assertThat(answerTo("""
                    counting: func [depth /local here] [
                        here: depth
                        if depth > 0 [counting depth - 1]
                        here
                    ]
                    counting 2"""))
                    .isEqualTo("2");
        }

        @Test
        @DisplayName("a call of another function in between changes nothing")
        void anothercallInBetween() {
            assertThat(answerTo("""
                    elsewhere: func [/local here] [here: 99]
                    counting: func [/local here] [here: 5 elsewhere here]
                    counting"""))
                    .isEqualTo("5");
        }
    }

    @Nested
    @DisplayName("which is what ARRAY needs")
    class WhichIsWhatArrayNeeds {

        @Test
        @DisplayName("a two-dimensional array built by a function of two indexes")
        void atwoDimensionalArray() {
            assertThat(answerTo("""
                    mold array/initial [2 2] func [row column] [ajoin [row " " column]]"""))
                    .isEqualTo("""
                            [["1 1" "1 2"] ["2 1" "2 2"]]""");
        }

        @Test
        @DisplayName("a three-dimensional one takes three")
        void athreeDimensionalArray() {
            assertThat(answerTo("""
                    mold array/initial [2 2 2] func [a b c] [ajoin [a b c]]"""))
                    .isEqualTo("""
                            [[["111" "112"] ["121" "122"]] [["211" "212"] ["221" "222"]]]""");
        }

        @Test
        @DisplayName("a single dimension passes one index")
        void asingleDimension() {
            assertThat(answerTo("mold array/initial 3 func [at] [at * 10]"))
                    .isEqualTo("[10 20 30]");
        }

        @Test
        @DisplayName("and a plain value is copied rather than called")
        void aplainValueIsCopied() {
            assertThat(answerTo("mold array/initial [2 2] 7"))
                    .isEqualTo("[[7 7] [7 7]]");
        }
    }
}
