package org.jebol.domain.read;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The constructs and the tokens that are nearly something else. A complemented
 * bitset is written {@code #(bitset! not #{FF})}; a trailing number is a position
 * and belongs only to a series, so anything else carrying one is malformed. The
 * char literal refuses what {@code IS_INVALID_CHAR} names -- {@code if (type >
 * MAX_UNI || IS_SURROGATE(type)) return -TOKEN_CHAR;} -- and the C's Scan_Quote
 * refuses the same code points inside a string, {@code if (IS_INVALID_CHAR(chr))
 * return 0;}.
 */
class ConstructAndOddLiteralFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("a bitset written as a construct")
    class TheBitsetConstruct {

        @Test
        @DisplayName("NOT before the bytes builds the complemented set")
        void notBuildsAComplementedSet() {
            assertThat(answerTo("""
                    bitset? load {#(bitset! not #{FF})}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("which holds every character the plain bytes left out")
        void theComplementHoldsWhatTheBytesDidNot() {
            assertThat(answerTo("""
                    complemented: load {#(bitset! not #{FF})}
                    all [find complemented #"a"]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and holds none of what they named")
        void theComplementDropsWhatTheBytesNamed() {
            assertThat(answerTo("""
                    complemented: load {#(bitset! not #{FF})}
                    all [not find complemented #"^(00)"]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("where the plain form holds exactly those, which is the contrast")
        void thePlainFormIsTheOffPoint() {
            assertThat(answerTo("""
                    plain: load {#(bitset! #{FF})}
                    all [find plain #"^(00)" not find plain #"a"]""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("a trailing number is a position, and only a series has one")
    class TheTrailingNumber {

        @Test
        @DisplayName("a series construct reads it as where the series stands")
        void aSeriesConstructTakesAPosition() {
            assertThat(answerTo("""
                    (load {#(block! [1 2] 2)}) = [2]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a bitset carrying one is malformed")
        void aBitsetWithAPositionIsRefused() {
            assertThat(answerTo("""
                    e: try [load {#(bitset! #{FF} 2)}] e/id = 'malconstruct"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and so is a char")
        void aCharWithAPositionIsRefused() {
            assertThat(answerTo("""
                    e: try [load {#(char! 65 2)}] e/id = 'malconstruct"""))
                    .isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("a struct construct declares a layout of scalar fields")
    class TheStructConstruct {

        @Test
        @DisplayName("one field of one scalar type reads back as a struct")
        void oneFieldReads() {
            assertThat(answerTo("""
                    struct? load {#(struct! [a [uint8!]])}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and so does a layout of several")
        void severalFieldsRead() {
            assertThat(answerTo("""
                    struct? load {#(struct! [a [int32!] b [float64!]])}"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a type that is not one of the scalars is malformed")
        void aNonScalarTypeIsRefused() {
            assertThat(answerTo("""
                    e: try [load {#(struct! [a [integer!]])}] e/id = 'malconstruct"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a field with no type beside it is malformed")
        void aFieldWithNoTypeIsRefused() {
            assertThat(answerTo("""
                    e: try [load {#(struct! [a])}] e/id = 'malconstruct"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and so is a layout that is not a block at all")
        void somethingThatIsNotALayoutIsRefused() {
            assertThat(answerTo("""
                    e: try [load {#(struct! 5)}] e/id = 'malconstruct"""))
                    .isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("a function construct is a spec and a body")
    class TheFunctionConstruct {

        @Test
        @DisplayName("the pair reads back as a function")
        void theSpecAndBodyRead() {
            assertThat(answerTo("""
                    function? load {#(function! [[a] [a]])}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and the function it built can be called")
        void theFunctionCanBeCalled() {
            assertThat(answerTo("""
                    built: load {#(function! [[a] [a]])}
                    (built 7) = 7""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a typed parameter and a body with a call in it read as well")
        void aTypedSpecReads() {
            assertThat(answerTo("""
                    function? transcode/one {#(function! [[a [series!]][print a]])}"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a spec with no body is malformed")
        void aSpecWithoutABodyIsRefused() {
            assertThat(answerTo("""
                    e: try [load {#(function! [[a]])}] e/id = 'malconstruct"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a third block is malformed as well")
        void aThirdBlockIsRefused() {
            assertThat(answerTo("""
                    e: try [transcode/one {#(function! [[a] [a] [b]])}]
                    e/id = 'malconstruct""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a spec that is not a block is malformed")
        void aSpecThatIsNotABlockIsRefused() {
            assertThat(answerTo("""
                    e: try [transcode/one {#(function! [5 [a]])}]
                    e/id = 'malconstruct""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and so is a pair that is not a block at all")
        void somethingThatIsNotAPairIsRefused() {
            assertThat(answerTo("""
                    e: try [transcode/one {#(function! 5)}]
                    e/id = 'malconstruct""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("tokens that are nearly something else")
    class TheNearMisses {

        @Test
        @DisplayName("a slash before an underscore is two values, not one word")
        void aSlashBeforeAnUnderscoreSplits() {
            assertThat(answerTo("""
                    (load "/_") = reduce ['/ none]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and there really are two of them")
        void theSplitIsTwoValues() {
            assertThat(answerTo("""
                    (length? load "/_") = 2""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a slash inside a money amount makes it an invalid token")
        void aSlashInMoneyIsRefused() {
            assertThat(answerTo("""
                    e: try [load "$1/2"]
                    all [e/id = 'invalid e/arg1 = "money" e/arg2 = "$1/"]"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("where the amount without one is ordinary money")
        void moneyWithoutASlashReads() {
            assertThat(answerTo("""
                    money? load {$1}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a colon before digits is a time, not a get-word")
        void aColonBeforeDigitsIsATime() {
            assertThat(answerTo("""
                    time? load {:10}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and the digits are the minutes")
        void theDigitsAreTheMinutes() {
            assertThat(answerTo("""
                    (load ":10") = 0:10""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("with a third part read as seconds")
        void aThirdPartIsSeconds() {
            assertThat(answerTo("""
                    (load ":10:20") = 0:10:20""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("a carriage return ends a comment as a line feed does")
    class TheCarriageReturn {

        @Test
        @DisplayName("what follows the return is read")
        void theReturnEndsTheComment() {
            assertThat(answerTo("""
                    (load {; comment^M1}) = 1""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and the rest of the line is read as its own expressions")
        void whatFollowsIsOrdinarySource() {
            assertThat(answerTo("""
                    (load {1 ; comment^Mx: 2 x}) = [1 x: 2 x]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("inside a binary script body as well")
        void theSameHoldsInsideAScript() {
            assertThat(answerTo("""
                    (do to binary! {REBOL [] ; comment^M1}) = 1""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("as a line feed always did")
        void theLineFeedIsTheOffPoint() {
            assertThat(answerTo("""
                    (load "; comment^/1") = 1""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("a code point no character may hold")
    class TheInvalidCodePoint {

        @Test
        @DisplayName("a lone surrogate in a char literal is an invalid char")
        void aSurrogateCharIsRefused() {
            assertThat(answerTo("""
                    e: try [load {#"^^(D800)"}]
                    all [e/id = 'invalid e/arg1 = "char"]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and one above the last code point is refused the same way")
        void anOversizeCharIsRefused() {
            assertThat(answerTo("""
                    e: try [load {#"^^(110000)"}]
                    all [e/id = 'invalid e/arg1 = "char"]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("the code point just below the surrogates reads")
        void theCodePointBelowTheSurrogatesReads() {
            assertThat(answerTo("""
                    (to integer! load {#"^^(D7FF)"}) = 55295""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and so does the last code point there is")
        void theLastCodePointReads() {
            assertThat(answerTo("""
                    (to integer! load {#"^^(10FFFF)"}) = 1114111""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a lone surrogate inside a string is refused too")
        void aSurrogateInsideAStringIsRefused() {
            assertThat(answerTo("""
                    error? try [load {"^^(D800)"}]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and so is one above the last code point")
        void anOversizeEscapeInsideAStringIsRefused() {
            assertThat(answerTo("""
                    error? try [load {"^^(110000)"}]""")).isEqualTo("#(true)");
        }
    }
}
