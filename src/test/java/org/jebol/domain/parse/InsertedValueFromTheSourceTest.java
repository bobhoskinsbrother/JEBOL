package org.jebol.domain.parse;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What PARSE's INSERT and CHANGE put in, read out of {@code Get_Parse_Value}
 * and the {@code do_modify} block in {@code src/core/u-parse.c}.
 *
 * <p>Three rules, and the first is the one that matters: the value is looked
 * up before it is used. A word in that place contributes what the word holds
 * and not the word itself. Taking it as written leaves a rule that looks like
 * a working INSERT until something reads the result.
 *
 * <p>Found through Rebol's own ENUM, whose rule is
 * {@code pos: word! insert enum-value (...)} counting up as it goes. With the
 * word taken as written every entry gets the same word instead of its own
 * number, and the object then evaluates that word once at construction, so
 * every name in the enumeration comes out holding the final count. `enum [a b
 * c]` gave 3 for all three instead of 0, 1 and 2.
 */
class InsertedValueFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("Get_Parse_Value: a word is fetched, a path is evaluated")
    class TheValueIsLookedUp {

        @Test
        @DisplayName("INSERT of a word puts in what the word holds")
        void insertLooksUpAWord() {
            assertThat(answerTo("v: 7 b: [a b] parse b [some [word! insert v]] mold b"))
                    .isEqualTo("\"[a 7 b 7]\"");
        }

        @Test
        @DisplayName("CHANGE of a word does the same")
        void changeLooksUpAWord() {
            assertThat(answerTo("v: 7 b: [a b] parse b [some [change word! v]] mold b"))
                    .isEqualTo("\"[7 7]\"");
        }

        @Test
        @DisplayName("a word holding a block is spread, and ONLY puts it in whole")
        void aWordHoldingABlock() {
            assertThat(answerTo("v: [1 2] b: [a] parse b [insert v] mold b"))
                    .isEqualTo("\"[1 2 a]\"");
            assertThat(answerTo("v: [1 2] b: [a] parse b [insert only v] mold b"))
                    .isEqualTo("\"[[1 2] a]\"");
            assertThat(answerTo("b: [a] parse b [insert [1 2]] mold b"))
                    .isEqualTo("\"[1 2 a]\"");
        }

        @Test
        @DisplayName("INSERT of a path evaluates it")
        void insertEvaluatesAPath() {
            assertThat(answerTo(
                    "o: make object! [n: 7] b: [a] parse b [insert o/n] mold b"))
                    .isEqualTo("\"[7 a]\"");
        }

        @Test
        @DisplayName("a paren is still evaluated, which it already was")
        void aParenIsEvaluated() {
            assertThat(answerTo("b: [a] parse b [insert (3 + 4)] mold b"))
                    .isEqualTo("\"[7 a]\"");
        }

        @Test
        @DisplayName("anything that is not a word, a path or a paren goes in as it stands")
        void everythingElseIsTakenAsItIs() {
            assertThat(answerTo("b: [a] parse b [insert 9] mold b")).isEqualTo("\"[9 a]\"");
            assertThat(answerTo("b: [a] parse b [insert \"x\"] mold b"))
                    .isEqualTo("{[\"x\" a]}");
        }
    }

    @Nested
    @DisplayName("a lit-word loses its tick on the way in")
    class LitWords {

        @Test
        @DisplayName("CHANGE of a lit-word leaves a plain word")
        void changeOfALitWordLeavesAPlainWord() {
            assertThat(answerTo("b: [a b] parse b [some [change word! 'x]] mold b"))
                    .isEqualTo("\"[x x]\"");
        }

        @Test
        @DisplayName("INSERT of a lit-word does the same")
        void insertOfALitWordLeavesAPlainWord() {
            assertThat(answerTo("b: [a] parse b [insert 'x] mold b")).isEqualTo("\"[x a]\"");
        }

        @Test
        @DisplayName("this is the only way to put a plain word in")
        void itIsTheOnlyWayToInsertAWord() {
            assertThat(answerTo("b: [a] parse b [insert 'zz] first b"))
                    .isEqualTo("zz");
            assertThat(answerTo("b: [a] parse b [insert 'zz] word? first b"))
                    .isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("what this unblocks: Rebol's own ENUM")
    class WhatItUnblocks {

        @Test
        @DisplayName("ENUM's rule counts up, one number per name")
        void enumCountsUp() {
            assertThat(answerTo(
                    "n: 0 b: [a b c] parse b [some [word! insert n (n: n + 1)]] mold b"))
                    .isEqualTo("\"[a 0 b 1 c 2]\"");
        }

        @Test
        @DisplayName("and CHANGE turns each name into a set-word beside it")
        void changeMakesSetWords() {
            assertThat(answerTo(
                    "n: 0 b: [a b] parse b [some [pos: word! insert n "
                            + "(change pos to set-word! pos/1  n: n + 1)]] mold b"))
                    .isEqualTo("\"[a: 0 b: 1]\"");
        }
    }
}
