package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What MAKE ERROR! refuses, and what ASSERT actually checks.
 *
 * <p>The catalogue is what makes an error spec valid, and it says no three
 * times. {@code Find_Error_Info} looks the type up and then the id inside it,
 * and each miss raises invalid-arg naming the word that was not found. Then a
 * third refusal names the whole spec: an error whose code is below a hundred
 * cannot be built by hand. That is the Throw category, which numbers from
 * nothing -- so a script may not manufacture a BREAK or a HALT and throw it as
 * though the interpreter had.
 *
 * <p>An error rebuilt from an object skips that third check, because the
 * object branch returns three lines before it. That is what makes the round
 * trip work: TO-OBJECT an error, change what you like, TO-ERROR it back.
 *
 * <p>ASSERT walked its block with DO and looked at the value, which is not
 * what it does: it checks every expression in turn and stops at the first
 * false one. So {@code assert [true 1 + 3 = 2 true]} passed, the last
 * expression being true. What it raises with is the block itself rather than a
 * sentence about it, so a script can read back what did not hold.
 */
class MakeErrorAndAssertFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("failure: try [" + source + "] failure/id");
    }

    private static String argumentOfFailureFrom(String source) {
        return answerTo("failure: try [" + source + "] mold failure/arg1");
    }

    @Nested
    @DisplayName("an error the catalogue has not got")
    class TheCatalogueSaysNo {

        @Test
        @DisplayName("an id its type does not have names the id")
        void anIdTheTypeHasNot() {
            assertThat(errorIdFrom("""
                    make error! [type: 'math id: 'foo]""")).isEqualTo("invalid-arg");
            assertThat(argumentOfFailureFrom("""
                    make error! [type: 'math id: 'foo]""")).isEqualTo("\"foo\"");
        }

        @Test
        @DisplayName("an id that is not even a word names it all the same")
        void anIdThatIsNotAWord() {
            assertThat(argumentOfFailureFrom("""
                    make error! [type: 'math id: 42]""")).isEqualTo("\"42\"");
        }

        @Test
        @DisplayName("and a type nobody has heard of names the type")
        void aTypeNobodyHasHeardOf() {
            assertThat(errorIdFrom("""
                    make error! [type: 'foo id: 'overflow]""")).isEqualTo("invalid-arg");
            assertThat(argumentOfFailureFrom("""
                    make error! [type: 'foo id: 'overflow]""")).isEqualTo("\"foo\"");
        }

        @Test
        @DisplayName("something that is not a spec at all is refused as itself")
        void somethingThatIsNotASpec() {
            assertThat(errorIdFrom("make error! 1")).isEqualTo("invalid-arg");
            assertThat(argumentOfFailureFrom("make error! 1")).isEqualTo("\"1\"");
        }

        @Test
        @DisplayName("a string is not refused, being a user error with a message")
        void aStringIsAUserError() {
            assertThat(answerTo("""
                    failure: make error! "went wrong"
                    reduce [failure/type failure/id failure/arg1]"""))
                    .isEqualTo("[User message \"went wrong\"]");
        }
    }

    @Nested
    @DisplayName("the Throw category, which numbers from nothing")
    class TheThrowCategory {

        @Test
        @DisplayName("none of its errors can be built by hand")
        void noneOfThemCanBeBuilt() {
            assertThat(errorIdFrom("""
                    make error! [type: 'Throw id: 'halt]""")).isEqualTo("invalid-arg");
            assertThat(errorIdFrom("""
                    make error! [type: 'Throw id: 'break]""")).isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("and the refusal names the whole spec, not one word of it")
        void theRefusalNamesTheWholeSpec() {
            assertThat(argumentOfFailureFrom("""
                    make error! [type: 'Throw id: 'halt]"""))
                    .isEqualTo("\"[type: 'Throw id: 'halt]\"");
        }

        @Test
        @DisplayName("where a category numbering from a hundred is fine")
        void aCategoryFromAHundredIsFine() {
            assertThat(answerTo("""
                    failure: make error! [type: 'Note id: 'exited]
                    reduce [failure/type failure/id failure/code]"""))
                    .isEqualTo("[Note exited 101]");
        }
    }

    @Nested
    @DisplayName("the round trip through an object")
    class TheRoundTrip {

        @Test
        @DisplayName("an error becomes an object and comes back an error")
        void itComesBackAnError() {
            assertThat(answerTo("""
                    as-object: to-object try [1 / 0]
                    back-again: to-error as-object
                    reduce [object? as-object error? back-again back-again/id]"""))
                    .isEqualTo("[#(true) #(true) zero-divide]");
        }

        @Test
        @DisplayName("and the code is worked out again rather than believed")
        void theCodeIsWorkedOutAgain() {
            assertThat(answerTo("""
                    as-object: to-object try [1 / 0]
                    as-object/code: 1
                    back-again: to-error as-object
                    back-again/code""")).isEqualTo("400");
        }
    }

    @Nested
    @DisplayName("ASSERT, which checks every expression")
    class TheAssertions {

        @Test
        @DisplayName("a false expression fails even with true ones after it")
        void aFalseOneInTheMiddle() {
            assertThat(errorIdFrom("""
                    assert [true 1 + 3 = 2 true]""")).isEqualTo("assert-failed");
        }

        @Test
        @DisplayName("and the block itself is what it complains with")
        void theBlockIsWhatItComplainsWith() {
            assertThat(argumentOfFailureFrom("""
                    assert [not none? none]""")).isEqualTo("\"[not none? none]\"");
            assertThat(argumentOfFailureFrom("""
                    assert [true 1 + 3 = 2 true]"""))
                    .isEqualTo("\"[true 1 + 3 = 2 true]\"");
        }

        @Test
        @DisplayName("a block of true expressions passes, and an empty one too")
        void trueOnesPass() {
            assertThat(answerTo("""
                    reduce [assert [true 1 + 1 = 2] assert []]"""))
                    .isEqualTo("[#(true) #(true)]");
        }
    }

    @Nested
    @DisplayName("ASSERT/TYPE, which is a different function under the same name")
    class TheTypeAssertions {

        @Test
        @DisplayName("a value that is not a word or a path is invalid-arg, named")
        void aValueThatIsNotAWord() {
            assertThat(errorIdFrom("""
                    assert/type [1 integer!]""")).isEqualTo("invalid-arg");
            assertThat(argumentOfFailureFrom("""
                    assert/type [1 integer!]""")).isEqualTo("\"1\"");
        }

        @Test
        @DisplayName("a word whose value is the wrong type is wrong-type, named")
        void aWordOfTheWrongType() {
            assertThat(errorIdFrom("""
                    x: 1
                    assert/type [x string!]""")).isEqualTo("wrong-type");
            assertThat(argumentOfFailureFrom("""
                    x: 1
                    assert/type [x string!]""")).isEqualTo("\"x\"");
        }

        @Test
        @DisplayName("and a word with no type after it has run out of arguments")
        void aWordWithNoTypeAfterIt() {
            assertThat(errorIdFrom("""
                    x: 1
                    assert/type [x]""")).isEqualTo("missing-arg");
        }

        @Test
        @DisplayName("a word of the right type passes")
        void aWordOfTheRightType() {
            assertThat(answerTo("""
                    x: 1
                    assert/type [x integer!]""")).isEqualTo("#(true)");
        }
    }
}
