package org.jebol.domain.parse;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A word holding a position in the series being parsed names a span to REMOVE or
 * CHANGE, between that mark and wherever the parse has reached. A word holding
 * anything else falls through and is read as an ordinary rule. KEEP COPY keeps
 * the span as a series of the input's own kind where plain KEEP keeps the value,
 * and KEEP PICK spreads what it matched except a paren, which has nothing to
 * spread. FAIL never matches, so the walk takes the next alternative; a rule item
 * that is an unset or a function is no rule at all,
 * {@code if (VAL_TYPE(item) &lt;= REB_UNSET || VAL_TYPE(item) &gt;= REB_NATIVE) goto
 * bad_rule;}.
 */
class ParseSpansAndCollectFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("REMOVE and CHANGE take a marked position as a span")
    class TheMarkedSpan {

        @Test
        @DisplayName("REMOVE takes out everything between the mark and here")
        void removeTakesOutTheSpan() {
            assertThat(answerTo("""
                    digit: charset "0123456789"
                    s: "ab12cd"
                    parse s [thru "b" mark: some digit remove mark to end]
                    s = {abcd}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("CHANGE replaces the same span")
        void changeReplacesTheSpan() {
            assertThat(answerTo("""
                    digit: charset "0123456789"
                    s: "ab12cd"
                    parse s [thru "b" mark: some digit change mark "X" to end]
                    s = {abXcd}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a block is spanned the same way")
        void aBlockIsSpanned() {
            assertThat(answerTo("""
                    b: [1 2 3 4]
                    parse b [skip mark: 2 skip remove mark to end]
                    b = [1 4]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a mark ahead of the position names the same run")
        void aMarkAheadNamesTheSameRun() {
            assertThat(answerTo("""
                    s: "abcd"
                    parse s [(ahead-of-here: skip s 3) skip remove ahead-of-here to end]
                    s = {ad}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a mark at the head takes out the whole input")
        void aMarkAtTheHeadSpansEverything() {
            assertThat(answerTo("""
                    s: "abcd"
                    parse s [mark: to end remove mark]
                    empty? s""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a parse command word is never a mark, so REMOVE SKIP still removes one item")
        void aCommandWordIsNeverAMark() {
            assertThat(answerTo("""
                    b: [1 2] parse b [remove skip to end] b = [2]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and a word holding some other series is read as an ordinary rule")
        void aForeignPositionIsReadAsARule() {
            assertThat(answerTo("""
                    somewhere-else: "b"
                    s: "abc"
                    parse s [skip remove somewhere-else to end]
                    s = {ac}""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("KEEP COPY keeps a slice and plain KEEP keeps the value")
    class TheKeptShapes {

        @Test
        @DisplayName("one character kept plainly is a char")
        void plainKeepOfOneCharacter() {
            assertThat(answerTo("""
                    (parse "abc" [collect [keep skip to end]]) = [#"a"]"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("where the same match copied is a one-character string")
        void keepCopyOfOneCharacter() {
            assertThat(answerTo("""
                    (parse "abc" [collect [keep copy taken skip to end]]) = ["a"]"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and the word the copy was named by holds the last slice")
        void theCopyWordHoldsTheLastSlice() {
            assertThat(answerTo("""
                    all [
                        ["a" "b"] = parse "ab" [collect some [keep copy taken skip]]
                        taken = "b"
                    ]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a block keeps one-item blocks")
        void keepCopyFromABlock() {
            assertThat(answerTo("""
                    [[1] [2] [3]] = parse [1 2 3] [collect some [keep copy taken integer!]]"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and a binary keeps one-byte binaries")
        void keepCopyFromABinary() {
            assertThat(answerTo("""
                    [#{01} #{02}] = parse #{0102} [collect some [keep copy taken skip]]"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("KEEP PICK spreads what it matched, one item at a time")
        void keepPickSpreadsTheMatch() {
            assertThat(answerTo("""
                    [#"a" #"b"] = parse "ab" [collect [keep pick 2 skip]]"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a binary spreads as byte numbers")
        void keepPickFromABinary() {
            assertThat(answerTo("""
                    [1 2] = parse #{0102} [collect [keep pick 2 skip]]"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("but a paren has no span to spread, so its value goes in whole")
        void keepPickOfAParenKeepsItWhole() {
            assertThat(answerTo("""
                    [[1]] = parse [] [collect keep pick ([1])]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("exactly as a plain KEEP of the same paren does")
        void plainKeepOfTheSameParen() {
            assertThat(answerTo("""
                    [[1]] = parse [] [collect keep ([1])]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and a paren answering a single value keeps that value")
        void keepPickOfASingleValuedParen() {
            assertThat(answerTo("""
                    [1] = parse [] [collect keep pick (1)]""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("COLLECT SET names a word for the collection")
    class TheCollectedWord {

        @Test
        @DisplayName("a collect that keeps nothing sets the word to an empty block")
        void anEmptyCollectionIsAnEmptyBlock() {
            assertThat(answerTo("""
                    a: none
                    all [#(true) = parse [] [collect set a []] a = []]"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a kept value reaches the word")
        void theKeptValueReachesTheWord() {
            assertThat(answerTo("""
                    a: none
                    all [#(true) = parse [1] [collect set a [keep skip]] a = [1]]"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and it reaches the word even when the parse goes on to fail")
        void theWordIsSetBeforeTheParseFails() {
            assertThat(answerTo("""
                    a: none
                    all [#(false) = parse [1 2] [collect set a [keep skip]] a = [1]]"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an inner named collect keeps nothing for the outer one")
        void anInnerNamedCollectKeepsNothingOutside() {
            assertThat(answerTo("""
                    a: none
                    all [[] = parse [1] [collect [collect set a keep skip]] a = [1]]"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("two nested names take the inner collection and the empty outer one")
        void twoNamesTakeTheirOwnCollections() {
            assertThat(answerTo("""
                    a: none b: none
                    all [
                        #(true) = parse [1] [collect set a [collect set b keep skip]]
                        a = []
                        b = [1]
                    ]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("the same name twice keeps the inner collection")
        void thesameNameTwiceKeepsTheInner() {
            assertThat(answerTo("""
                    a: none
                    all [
                        #(true) = parse [1] [collect set a [collect set a keep skip]]
                        a = [1]
                    ]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and a SET that is not COLLECT's own sets the matched value instead")
        void aPlainSetIsNotCollectsOwn() {
            assertThat(answerTo("""
                    a: none
                    all [[[1]] = parse [1] [collect [collect [set a keep skip]]] a = 1]"""))
                    .isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("FAIL never matches")
    class TheDeliberateFailure {

        @Test
        @DisplayName("so the walk takes the block's next alternative")
        void failTakesTheNextAlternative() {
            assertThat(answerTo("""
                    parse "ab" ["a" fail | "ab"]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("with no alternative left the parse fails")
        void failWithNoAlternativeFails() {
            assertThat(answerTo("""
                    parse "ab" ["a" fail]""")).isEqualTo("#(false)");
        }

        @Test
        @DisplayName("inside a sub-block the enclosing rule carries on")
        void failInsideASubBlock() {
            assertThat(answerTo("""
                    parse "ab" [["a" fail | "a"] "b"]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and a repeated rule that always fails simply repeats no times")
        void failUnderARepeatMatchesNothing() {
            assertThat(answerTo("""
                    parse "ab" [any ["a" fail] "ab"]""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("a rule item that is no rule at all")
    class TheBadRuleItem {

        @Test
        @DisplayName("a word holding nothing is refused with parse-rule")
        void anUnsetWordIsRefused() {
            assertThat(answerTo("""
                    unassigned-word: ()
                    e: try [parse [1] [unassigned-word]] e/id = 'parse-rule"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a word nobody ever assigned is refused the same way")
        void aWordNeverAssignedIsRefused() {
            assertThat(answerTo("""
                    e: try [parse [1] [never-assigned-at-all]] e/id = 'parse-rule"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a function is refused as well, the dialect having no place for one")
        void aFunctionIsRefused() {
            assertThat(answerTo("""
                    e: try [parse [1] reduce [:add]] e/id = 'parse-rule"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("where a count with no rule after it runs out of rule instead")
        void acountWithNoRuleIsADifferentFailure() {
            assertThat(answerTo("""
                    e: try [parse [1] [1 2]] e/id = 'parse-end""")).isEqualTo("#(true)");
        }
    }
}
