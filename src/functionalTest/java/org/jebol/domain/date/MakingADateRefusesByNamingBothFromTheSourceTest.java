package org.jebol.domain.date;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class MakingADateRefusesByNamingBothFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String refusalOf(String source) {
        return answerTo("e: try [" + source + "] "
                + "reduce [e/id mold e/arg1 mold e/arg2]");
    }

    @Nested
    @DisplayName("what a refused MAKE DATE! puts in arg1 and arg2")
    class TheTwoArgumentsOfTheRefusal {

        @Test
        @DisplayName("the datatype asked for, then the block that failed")
        void bothAreNamed() {
            assertThat(refusalOf("make date! [1 2]"))
                    .isEqualTo("[bad-make-arg \"#(date!)\" \"[1 2]\"]");
        }

        @Test
        @DisplayName("an empty block is named as the empty block it was")
        void anEmptyBlockIsNamed() {
            assertThat(refusalOf("make date! []"))
                    .isEqualTo("[bad-make-arg \"#(date!)\" \"[]\"]");
        }

        @Test
        @DisplayName("a day nobody has still names the block, not the day")
        void aDayThatDoesNotExistNamesTheBlock() {
            assertThat(refusalOf("make date! [99 13 99]"))
                    .isEqualTo("[bad-make-arg \"#(date!)\" \"[99 13 99]\"]");
        }

        @ParameterizedTest(name = "make date! {0} names the datatype in arg1")
        @ValueSource(strings = {
            "[1 2]", "[]", "[1]",
            "[1-Jan-2000 1 2]", "[2000 1 1 12]", "[2000 1 1 12:00 1:00 9]"})
        @DisplayName("arg1 is the datatype for every shape that fails")
        void arg1IsAlwaysTheDatatype(String block) {
            assertThat(refusalOf("make date! " + block))
                    .startsWith("[bad-make-arg \"#(date!)\"");
        }

        @Test
        @DisplayName("six numbers do not fail: they are a day and then a clock")
        void sixNumbersAreADayAndAClock() {
            assertThat(answerTo("make date! [1 2 3 4 5 6]"))
                    .as("day 1, month 2, year 3, then 4:05:06 -- and no day-year "
                            + "swap, because a first number of 1 could be a day")
                    .isEqualTo("1-Feb-0003/4:05:06");
        }
    }

    @Nested
    @DisplayName("why arg1 and arg2 and not the message")
    class WhyBothRatherThanTheProse {

        @Test
        @DisplayName("the id alone cannot tell these two refusals apart")
        void theIdAloneIsNotEnough() {
            assertThat(answerTo("e: try [make date! [1 2]] f: try [make time! [1 2 3 4]] "
                    + "reduce [e/id = f/id  e/arg1 = f/arg1]"))
                    .isEqualTo("[#(true) #(false)]");
        }

        @Test
        @DisplayName("and a script reads arg1 as a datatype, not as text")
        void arg1IsADatatypeValue() {
            assertThat(answerTo("e: try [make date! [1 2]] "
                    + "reduce [datatype? e/arg1  e/arg1 = date!]"))
                    .isEqualTo("[#(true) #(true)]");
        }

        @Test
        @DisplayName("arg2 is the block itself, so a script can look inside it")
        void arg2IsTheBlockItself() {
            assertThat(answerTo("e: try [make date! [1 2]] "
                    + "reduce [block? e/arg2  length? e/arg2  first e/arg2]"))
                    .isEqualTo("[#(true) 2 1]");
        }
    }
}
