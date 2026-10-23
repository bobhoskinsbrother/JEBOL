package org.jebol.domain.read;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConstructionKeywordAndMalconstructFromTheSourceTest {

    private String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private String refusalOf(String construct) {
        return answerTo("e: try [transcode {" + construct + "}] reduce [e/id e/arg1]");
    }

    @Nested
    @DisplayName("a keyword construct ignores whatever follows the keyword")
    class AKeywordIgnoresTheRest {

        @Test
        @DisplayName("#(true 1) is true")
        void trueWithAnExtraItem() {
            assertThat(answerTo("""
                    (mold/flat/all transcode {#(true 1)}) = {[#(true)]}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("#(false x y) is false")
        void falseWithTwoExtraItems() {
            assertThat(answerTo("""
                    (mold/flat/all transcode {#(false x y)}) = {[#(false)]}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("#(unset 2) is unset")
        void unsetWithAnExtraItem() {
            assertThat(answerTo("""
                    (mold/flat/all transcode {#(unset 2)}) = {[#(unset)]}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("#(none [a]) is none")
        void noneWithABlockAfterIt() {
            assertThat(answerTo("""
                    (mold/flat/all transcode {#(none [a])}) = {[_]}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("in capitals too, #(NONE 1) is none")
        void capitalNoneWithAnExtraItem() {
            assertThat(answerTo("""
                    (mold/flat/all transcode {#(NONE 1)}) = {[_]}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and #(True) alone is true")
        void mixedCaseTrueAlone() {
            assertThat(answerTo("""
                    (mold/flat/all transcode {#(True)}) = {[#(true)]}""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("a malconstruct names its first item when that item is not a word")
    class TheFirstItemWhenItIsNotAWord {

        @Test
        @DisplayName("a set-word")
        void aSetWord() {
            assertThat(refusalOf("#(k: 1)")).isEqualTo("[malconstruct k:]");
        }

        @Test
        @DisplayName("a set-word on its own")
        void aSetWordAlone() {
            assertThat(refusalOf("#(k:)")).isEqualTo("[malconstruct k:]");
        }

        @Test
        @DisplayName("an integer")
        void anInteger() {
            assertThat(refusalOf("#(1 2)")).isEqualTo("[malconstruct 1]");
        }

        @Test
        @DisplayName("a decimal on its own")
        void aDecimalAlone() {
            assertThat(refusalOf("#(1.5)")).isEqualTo("[malconstruct 1.5]");
        }

        @Test
        @DisplayName("a string")
        void aString() {
            assertThat(refusalOf("""
                    #("a" b)""")).isEqualTo("""
                    [malconstruct "a"]""");
        }

        @Test
        @DisplayName("a block")
        void aBlock() {
            assertThat(refusalOf("#([a] 1)")).isEqualTo("[malconstruct [a]]");
        }

        @Test
        @DisplayName("a get-word")
        void aGetWord() {
            assertThat(refusalOf("#(:g 1)")).isEqualTo("[malconstruct :g]");
        }

        @Test
        @DisplayName("a lit-word")
        void aLitWord() {
            assertThat(refusalOf("#('l 1)")).isEqualTo("[malconstruct 'l]");
        }

        @Test
        @DisplayName("a path")
        void aPath() {
            assertThat(refusalOf("#(a/b 1)")).isEqualTo("[malconstruct a/b]");
        }

        @Test
        @DisplayName("an issue")
        void anIssue() {
            assertThat(refusalOf("#(#a 1)")).isEqualTo("[malconstruct #a]");
        }

        @Test
        @DisplayName("a file")
        void aFile() {
            assertThat(refusalOf("#(%f 1)")).isEqualTo("[malconstruct %f]");
        }
    }

    @Nested
    @DisplayName("and the whole construct when it starts with a word, or holds nothing")
    class TheWholeConstruct {

        @Test
        @DisplayName("a word that names no datatype")
        void aWordNamingNoDatatype() {
            assertThat(refusalOf("#(foo 1)")).isEqualTo("[malconstruct [foo 1]]");
        }

        @Test
        @DisplayName("a datatype with no maker")
        void aDatatypeWithNoMaker() {
            assertThat(refusalOf("#(char! 65)")).isEqualTo("[malconstruct [char! 65]]");
        }

        @Test
        @DisplayName("a datatype whose maker refuses what follows")
        void aDatatypeWhoseMakerRefuses() {
            assertThat(refusalOf("""
                    #(string! "ab" x)""")).isEqualTo("""
                    [malconstruct [string! "ab" x]]""");
        }

        @Test
        @DisplayName("an empty construct")
        void anEmptyConstruct() {
            assertThat(refusalOf("#()")).isEqualTo("[malconstruct []]");
        }
    }

    @Nested
    @DisplayName("the same argument comes back when the failure is handed back as a value")
    class HandedBackAsAValue {

        @Test
        @DisplayName("the first item, for a construct starting with an integer")
        void theFirstItem() {
            assertThat(answerTo("""
                    e: first transcode/error {#(1 2) x}
                    reduce [e/id e/arg1]""")).isEqualTo("[malconstruct 1]");
        }

        @Test
        @DisplayName("the whole construct, for one starting with a word")
        void theWholeConstruct() {
            assertThat(answerTo("""
                    e: first transcode/error {#(foo 1)}
                    reduce [e/id e/arg1]""")).isEqualTo("[malconstruct [foo 1]]");
        }

        @Test
        @DisplayName("and LOAD raises it the same way")
        void loadRaisesItTheSameWay() {
            assertThat(answerTo("""
                    e: try [load {#(1 2)}]
                    reduce [e/id e/arg1]""")).isEqualTo("[malconstruct 1]");
        }
    }
}
