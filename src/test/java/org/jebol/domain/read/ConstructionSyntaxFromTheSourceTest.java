package org.jebol.domain.read;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Construction syntax, and the three other things that stopped the reader.
 *
 * <p>Rebol keeps no list of which datatypes {@code #(...)} works for.
 * {@code Construct_Value} skips the datatype word and calls
 * {@code Make_Dispatch[type]} on what is left, so a type has the syntax
 * exactly when it has a maker. JEBOL had a hardcoded switch whose
 * {@code default} answered {@code malconstruct}, and that one line stopped
 * ten of Rebol's own test files dead -- make-test.r3 at 216 of its 1,029
 * assertions, copy-test.r3 at 0 of 223.
 *
 * <p>The other three are unrelated to each other and to construction. A
 * percent may carry an exponent. A file may open with a percent escape. And a
 * path may hold a tag or a character, both of which were being cut out of the
 * lexeme and read as separate values -- the character silently, since
 * {@code b/#"a"} read as two values instead of one without changing how many
 * assertions a file appeared to have.
 *
 * <p>Every expectation here was run against a Rebol built from the vendored
 * source by {@code scripts/build-r3.sh} before it was written down.
 */
class ConstructionSyntaxFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        String shown = interpreter.display(interpreter.run(source));
        boolean wrapped = shown.length() >= 2
                && ((shown.charAt(0) == '"' && shown.charAt(shown.length() - 1) == '"')
                        || (shown.charAt(0) == '{' && shown.charAt(shown.length() - 1) == '}'));
        return wrapped ? shown.substring(1, shown.length() - 1) : shown;
    }

    private static String reading(String literal) {
        return answerTo("either error? e: try [load {" + literal + "}] [e/id] [mold e]");
    }

    @Nested
    @DisplayName("a datatype has construction syntax when it has a maker")
    class WhenItHasAMaker {

        @ParameterizedTest(name = "{0} reads as {1}")
        @CsvSource(delimiter = '|', value = {
            "#(map! [a: 1])                 | a: 1",
            "#(date! 1 2 3)                 | 1-Feb-0003",
            "#(date! 1-1-2000)              | 1-Jan-2000",
            "#(date! 1-1-2000 10:30)        | 1-Jan-2000/10:30",
            "#(tuple! 1 2 3)                | 1.2.3",
            "#(time! 1 2 3)                 | 0:00:01",
            "#(datatype! string!)           | #(string!)",
            "#(typeset! [char! string!])    | make typeset! [char! string!]",
            "#(image! 1x1 #{FFFFFF})        | make image! [1x1 #{FFFFFF}]",
        })
        @DisplayName("the fourteen that were refused")
        void thefourteenThatWereRefused(String literal, String expected) {
            assertThat(reading(literal.strip())).contains(expected.strip());
        }

        @Test
        @DisplayName("a time construct reads one loose value where MAKE reads a block")
        void atimeConstructReadsOneLooseValue() {
            assertThat(reading("#(time! 1 2 3)"))
                    .as("Make_Time takes a bare integer as seconds and only reads "
                            + "hours, minutes and seconds from a block")
                    .isEqualTo("0:00:01");
            assertThat(answerTo("mold make time! [1 2 3]")).isEqualTo("1:02:03");
        }

        @Test
        @DisplayName("a datatype word on its own is still the datatype")
        void adatatypeWordOnItsOwn() {
            assertThat(reading("#(string!)")).isEqualTo("#(string!)");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
            "#(money! 1)", "#(char! 97)", "#(word! a)", "#(integer! 5)", "#(issue! a)",
        })
        @DisplayName("and a datatype with no maker is still refused, as Rebol refuses it")
        void adatatypeWithNoMaker(String literal) {
            assertThat(reading(literal)).isEqualTo("malconstruct");
        }

        @Test
        @DisplayName("a spec the maker rejects is a malconstruct, not an escaping exception")
        void aspecTheMakerRejects() {
            assertThat(reading("#(date! 1 13 2000)")).isEqualTo("malconstruct");
        }
    }

    @Nested
    @DisplayName("MAKE builds a date or a time from its parts")
    class MakingDatesAndTimes {

        @ParameterizedTest(name = "{0} is {1}")
        @CsvSource({
            "make date! [1 2 3],          1-Feb-0003",
            "make date! [2000 1 1],       1-Jan-2000",
            "make date! [1 1 2000 10 30 0], 1-Jan-2000/10:30",
            "make time! [1 2 3],          1:02:03",
            "make time! [-1 30 0],        -1:30",
        })
        @DisplayName("in either order, because the first number over ninety-nine is a year")
        void ineitherOrder(String source, String expected) {
            assertThat(answerTo("mold " + source)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
            "make date! [1 13 2000]", "make date! [32 1 2000]", "make date! [29 2 2001]",
        })
        @DisplayName("and a block it cannot use is refused")
        void ablockItCannotUse(String source) {
            assertThat(answerTo(
                    "either error? e: try [" + source + "] [e/id] ['worked]"))
                    .isEqualTo("bad-make-arg");
        }
    }

    @Nested
    @DisplayName("a percent may carry an exponent")
    class PercentWithAnExponent {

        @ParameterizedTest(name = "{0} is {1}")
        @CsvSource({
            "1e18%,   1.0e18%",
            "1.5e2%,  150%",
            "-1e2%,   -100%",
            "1e-2%,   0.01%",
            "50%,     50%",
            "0.5%,    0.5%",
        })
        @DisplayName("which is what percent-test.r3 line 18 asks for")
        void whichIsWhatItAsksFor(String literal, String expected) {
            assertThat(reading(literal)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("a file may open with a percent escape")
    class AfileOpeningWithAnEscape {

        @ParameterizedTest(name = "{0} is {1}")
        @CsvSource({
            "%%40b,   %@b",
            "%%40,    %@",
            "%%2Fy,   %/y",
            "%a%40b,  %a@b",
        })
        @DisplayName("two hex digits after the second percent make it an escape")
        void twohexDigitsMakeItAnEscape(String literal, String expected) {
            assertThat(reading(literal)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "{0} is refused")
        @ValueSource(strings = {"%%3", "%%zz", "%%/x"})
        @DisplayName("and anything else after two percents is not a file")
        void anythingElseIsNotAFile(String literal) {
            assertThat(reading(literal))
                    .as("%%/x was being read as the modulo operator, which Rebol "
                            + "refuses outright")
                    .isEqualTo("invalid");
        }

        @Test
        @DisplayName("the modulo operator still works")
        void themoduloOperatorStillWorks() {
            assertThat(answerTo("-7 %% 3")).isEqualTo("2");
        }
    }

    @Nested
    @DisplayName("a path may hold a tag or a character")
    class WhatAPathMayHold {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
            "m/(<A>)", "m/(<A>)/x", "m/(\"x\")", "m/([1 2])", "m/(a)", "m/(1)",
        })
        @DisplayName("a paren segment holds any value, tags included")
        void aparenSegmentHoldsAnyValue(String literal) {
            assertThat(reading(literal)).isEqualTo(literal);
        }

        @Test
        @DisplayName("a character literal is one segment and not two values")
        void acharacterLiteralIsOneSegment() {
            assertThat(reading("b/#\"a\""))
                    .as("read as the path b/# and a separate string, which no count "
                            + "of assertions could ever have caught")
                    .isEqualTo("b/#\"a\"");
        }

        @Test
        @DisplayName("an issue in a path still reads")
        void anissueInAPathStillReads() {
            assertThat(reading("b/#foo")).isEqualTo("b/#foo");
        }
    }
}
