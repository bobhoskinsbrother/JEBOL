package org.jebol.domain.value;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The tuple datatype, read out of {@code src/core/t-tuple.c}.
 *
 * <p>Written from the C and not from the Java beside it. Each group names
 * the function it was taken from, so a disagreement can be settled by
 * reading that function rather than by arguing about what a tuple ought to
 * do.
 *
 * <p>The one idea underneath all of it: a tuple keeps a length and twelve
 * octets, and the octets past the length are zeros rather than absent. So
 * {@code 1.2.3} and {@code 1.2.3.0} hold the same twelve octets and differ
 * only in their length, which is why they are equal and are not the same.
 */
class TupleFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Nested
    @DisplayName("Emit_Tuple: what a tuple shows")
    class Molding {

        @Test
        @DisplayName("a tuple shows every octet it keeps")
        void theKeptOctetsAreShown() {
            assertThat(answerTo("mold 1.2.3")).isEqualTo("\"1.2.3\"");
            assertThat(answerTo("mold 1.2.3.4.5.6.7.8.9.10.11.12"))
                    .isEqualTo("\"1.2.3.4.5.6.7.8.9.10.11.12\"");
        }

        @Test
        @DisplayName("a tuple keeping fewer than three shows zeros up to three")
        void theShownLengthHasAFloor() {
            assertThat(answerTo("mold to tuple! #{01}")).isEqualTo("\"1.0.0\"");
            assertThat(answerTo("mold to tuple! #{0102}")).isEqualTo("\"1.2.0\"");
        }

        @Test
        @DisplayName("a tuple keeping nothing shows three zeros")
        void theDegenerateTupleShowsZeros() {
            assertThat(answerTo("mold to tuple! []")).isEqualTo("\"0.0.0\"");
        }
    }

    @Nested
    @DisplayName("Scan_Tuple: a tuple from a string")
    class FromAString {

        @Test
        @DisplayName("a string of one number keeps three octets")
        void aShortStringIsPaddedToThree() {
            assertThat(answerTo("(to tuple! \"1\") == 1.0.0")).isEqualTo("#(true)");
            assertThat(answerTo("length? to tuple! \"1\"")).isEqualTo("3");
        }

        @Test
        @DisplayName("two numbers keep three octets as well")
        void twoNumbersArePaddedToo() {
            assertThat(answerTo("(to tuple! \"1.2\") == 1.2.0")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("three numbers keep three")
        void theOnPointIsUnchanged() {
            assertThat(answerTo("(to tuple! \"1.2.3\") == 1.2.3")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("more than three keeps what was written")
        void aLongStringKeepsItsLength() {
            assertThat(answerTo("(to tuple! \"1.2.3.4\") == 1.2.3.4")).isEqualTo("#(true)");
            assertThat(answerTo("mold to tuple! \"1.2.3.4.5.6.7.8.9.10.11.12\""))
                    .isEqualTo("\"1.2.3.4.5.6.7.8.9.10.11.12\"");
        }

        @Test
        @DisplayName("thirteen numbers is one too many")
        void theOffPointIsRefused() {
            assertThat(errorIdOf("to tuple! \"1.2.3.4.5.6.7.8.9.10.11.12.13\""))
                    .isNotEqualTo("no-error");
        }

        @Test
        @DisplayName("a number above 255 is refused")
        void anOctetHasACeiling() {
            assertThat(errorIdOf("to tuple! \"1.2.256\"")).isNotEqualTo("no-error");
            assertThat(answerTo("(to tuple! \"1.2.255\") == 1.2.255")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("text that is not a number at all is refused")
        void aWrongTypeInsideTheTextIsRefused() {
            assertThat(errorIdOf("to tuple! \"a.b.c\"")).isNotEqualTo("no-error");
            assertThat(errorIdOf("to tuple! \"\"")).isNotEqualTo("no-error");
        }
    }

    @Nested
    @DisplayName("MT_Tuple: a tuple from a block")
    class FromABlock {

        @Test
        @DisplayName("a block of numbers keeps exactly what it holds")
        void aBlockIsNotPadded() {
            assertThat(answerTo("(to tuple! [1 2 3]) == 1.2.3")).isEqualTo("#(true)");
            assertThat(answerTo("mold to tuple! [1]")).isEqualTo("\"1.0.0\"");
            assertThat(answerTo("(to tuple! [1]) == 1.0.0"))
                    .as("kept one octet, so not strictly equal to a written 1.0.0")
                    .isEqualTo("#(false)");
        }

        @Test
        @DisplayName("twelve is the most a block may hold")
        void theBoundaryOnLength() {
            assertThat(answerTo("mold to tuple! [1 2 3 4 5 6 7 8 9 10 11 12]"))
                    .isEqualTo("\"1.2.3.4.5.6.7.8.9.10.11.12\"");
            assertThat(errorIdOf("to tuple! [1 2 3 4 5 6 7 8 9 10 11 12 13]"))
                    .isNotEqualTo("no-error");
        }

        @Test
        @DisplayName("a decimal rounds to the nearest whole octet")
        void aDecimalIsRounded() {
            assertThat(answerTo("(to tuple! [0.5 25.4 200.01]) == 1.25.200"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a number outside an octet is refused rather than clamped")
        void theBoundaryOnEachValue() {
            assertThat(answerTo("(to tuple! [0 255 128]) == 0.255.128")).isEqualTo("#(true)");
            assertThat(errorIdOf("to tuple! [0.5 25.4 300.01]")).isNotEqualTo("no-error");
            assertThat(errorIdOf("to tuple! [0.5 25.4 -10.01]")).isNotEqualTo("no-error");
            assertThat(errorIdOf("to tuple! [256 0 0]")).isNotEqualTo("no-error");
            assertThat(errorIdOf("to tuple! [-1 0 0]")).isNotEqualTo("no-error");
        }

        @Test
        @DisplayName("a character counts as its code point")
        void aCharacterIsAnOctet() {
            assertThat(answerTo("(to tuple! [#\"a\" 0 0]) == 97.0.0")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("anything else in the block is refused")
        void aWrongTypeIsRefused() {
            assertThat(errorIdOf("to tuple! [\"1\" 2 3]")).isNotEqualTo("no-error");
            assertThat(errorIdOf("to tuple! [none 2 3]")).isNotEqualTo("no-error");
            assertThat(errorIdOf("to tuple! [1 2 3x4]")).isNotEqualTo("no-error");
        }
    }

    @Nested
    @DisplayName("REBTYPE(Tuple) A_TO: a tuple from a binary or an issue")
    class FromOctets {

        @Test
        @DisplayName("a binary gives one octet per byte")
        void aBinaryIsCopiedAcross() {
            assertThat(answerTo("(to tuple! #{010203}) == 1.2.3")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a binary of more than twelve bytes is cut short")
        void aLongBinaryIsTruncated() {
            assertThat(answerTo("mold to tuple! #{0102030405060708090A0B0C}"))
                    .isEqualTo("\"1.2.3.4.5.6.7.8.9.10.11.12\"");
            assertThat(answerTo("mold to tuple! #{0102030405060708090A0B0C0D}"))
                    .isEqualTo("\"1.2.3.4.5.6.7.8.9.10.11.12\"");
        }

        @Test
        @DisplayName("an empty binary gives a tuple keeping nothing")
        void theDegenerateBinary() {
            assertThat(answerTo("mold to tuple! #{}")).isEqualTo("\"0.0.0\"");
        }

        @Test
        @DisplayName("an issue is read as pairs of hex digits")
        void anIssueIsHex() {
            assertThat(answerTo("(to tuple! #010203) == 1.2.3")).isEqualTo("#(true)");
            assertThat(answerTo("mold to tuple! #0102030405060708090A0B0C"))
                    .isEqualTo("\"1.2.3.4.5.6.7.8.9.10.11.12\"");
        }

        @Test
        @DisplayName("an odd number of digits in an issue is refused")
        void anIssueNeedsPairs() {
            assertThat(errorIdOf("to tuple! #01020")).isNotEqualTo("no-error");
        }

        @Test
        @DisplayName("an issue of more than twelve pairs is refused, not cut short")
        void aLongIssueRaises() {
            assertThat(errorIdOf("to tuple! #0102030405060708090A0B0C0D"))
                    .isNotEqualTo("no-error");
        }

        @Test
        @DisplayName("a tuple converted to a tuple is itself")
        void theIdentityCase() {
            assertThat(answerTo("(to tuple! 1.1.1) == 1.1.1")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("anything else is refused")
        void thereAreNoOtherSources() {
            assertThat(errorIdOf("to tuple! 1")).isNotEqualTo("no-error");
            assertThat(errorIdOf("to tuple! none")).isNotEqualTo("no-error");
            assertThat(errorIdOf("to tuple! 1x2")).isNotEqualTo("no-error");
        }
    }

    @Nested
    @DisplayName("Cmp_Tuple and CT_Tuple: comparing two tuples")
    class Comparing {

        @Test
        @DisplayName("the zeros behind the length count, so 1.2.3 equals 1.2.3.0")
        void equalityIgnoresTheLength() {
            assertThat(answerTo("equal? 1.2.3 1.2.3.0")).isEqualTo("#(true)");
            assertThat(answerTo("equiv? 1.2.3 1.2.3.0")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("strict equality asks about the length as well")
        void strictEqualityMindsTheLength() {
            assertThat(answerTo("strict-equal? 1.2.3 1.2.3.0")).isEqualTo("#(false)");
            assertThat(answerTo("same? 1.2.3 1.2.3.0")).isEqualTo("#(false)");
            assertThat(answerTo("strict-equal? 1.2.3 1.2.3"))
                    .as("the same length is the on point")
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("ordering compares octet by octet")
        void orderingWalksTheOctets() {
            assertThat(answerTo("1.2.3 < 1.2.4")).isEqualTo("#(true)");
            assertThat(answerTo("1.2.3 > 1.2.2")).isEqualTo("#(true)");
            assertThat(answerTo("1.2.3 < 1.2.3.1"))
                    .as("a longer tuple is greater when the extra octet is not zero")
                    .isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("PD_Tuple: reading an octet through a path")
    class Reading {

        @Test
        @DisplayName("a path reads any of the shown octets")
        void withinTheShownLength() {
            assertThat(answerTo("t: 1.2.3 t/1")).isEqualTo("1");
            assertThat(answerTo("t: 1.2.3 t/3")).isEqualTo("3");
        }

        @Test
        @DisplayName("an octet past the kept ones reads as zero, not none")
        void pastTheKeptOctets() {
            assertThat(answerTo("t: to tuple! [1] t/2")).isEqualTo("0");
            assertThat(answerTo("t: to tuple! [1] t/3")).isEqualTo("0");
        }

        @Test
        @DisplayName("past the shown length it reads none")
        void theOffPoint() {
            assertThat(answerTo("t: to tuple! [1] t/4")).isEqualTo("_");
            assertThat(answerTo("t: 1.2.3 t/4")).isEqualTo("_");
            assertThat(answerTo("t: 1.2.3.4 t/4")).isEqualTo("4");
            assertThat(answerTo("t: 1.2.3.4 t/5")).isEqualTo("_");
        }

        @Test
        @DisplayName("position zero and below read none")
        void theFloor() {
            assertThat(answerTo("t: 1.2.3 t/0")).isEqualTo("_");
            assertThat(answerTo("t: 1.2.3 t/-1")).isEqualTo("_");
        }

        @Test
        @DisplayName("LENGTH? is the shown length, never below three")
        void theLengthAnswered() {
            assertThat(answerTo("length? to tuple! [1]")).isEqualTo("3");
            assertThat(answerTo("length? to tuple! []")).isEqualTo("3");
            assertThat(answerTo("length? 1.2.3.4")).isEqualTo("4");
            assertThat(answerTo("length? 1.2.3.4.5.6.7.8.9.10.11.12")).isEqualTo("12");
        }

        @Test
        @DisplayName("PICK reads the same way a path does")
        void pickAgrees() {
            assertThat(answerTo("pick 1.2.3 2")).isEqualTo("2");
            assertThat(answerTo("pick 1.2.3 4")).isEqualTo("_");
            assertThat(answerTo("pick to tuple! [1] 3")).isEqualTo("0");
        }
    }

    @Nested
    @DisplayName("PD_Tuple with a value to set: writing an octet")
    class Writing {

        @Test
        @DisplayName("a write answers what was handed to it")
        void theAnswerIsTheWrittenValue() {
            assertThat(answerTo("t: 1.2.3.4 t/2: 99")).isEqualTo("99");
        }

        @Test
        @DisplayName("a write changes the octet")
        void theOctetChanges() {
            assertThat(answerTo("t: 1.2.3.4 t/2: 99 t == 1.99.3.4")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a write past the end lengthens the tuple and fills with zeros")
        void writingPastTheEndExtends() {
            assertThat(answerTo("t: 1.2.3 t/5: 5 t == 1.2.3.0.5")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("twelve is the last position a write may reach")
        void theBoundaryOnPosition() {
            assertThat(answerTo("t: 1.2.3 t/12: 12")).isEqualTo("12");
            assertThat(errorIdOf("t: 1.2.3 t/13: 13")).isNotEqualTo("no-error");
            assertThat(errorIdOf("t: 1.2.3 t/0: 1")).isNotEqualTo("no-error");
        }

        @Test
        @DisplayName("a written octet is clamped rather than refused")
        void theBoundaryOnTheValue() {
            assertThat(answerTo("t: 1.2.3 t/1: 300")).isEqualTo("300");
            assertThat(answerTo("t: 1.2.3 t/1: 300 t/2: -10 t == 255.0.3")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("writing NONE cuts the tuple short")
        void noneShortens() {
            assertThat(answerTo("t: 1.2.3.4 t/4: none t == 1.2.3")).isEqualTo("#(true)");
            assertThat(answerTo("t: 1.2.3.4 t/4: none (t + 0.0.0.0) == 1.2.3.0"))
                    .as("and the octet behind it was zeroed, not left at 4")
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a decimal may be written and truncates")
        void aDecimalIsAccepted() {
            assertThat(answerTo("t: 1.2.3 t/1: 9.7 t/1")).isEqualTo("9");
        }

        @Test
        @DisplayName("anything that is not a number or NONE is refused")
        void aWrongTypeIsRefused() {
            assertThat(errorIdOf("t: 1.2.3 t/1: \"9\"")).isNotEqualTo("no-error");
            assertThat(errorIdOf("t: 1.2.3 t/1: true")).isNotEqualTo("no-error");
        }

        @Test
        @DisplayName("POKE is refused where a set-path is allowed")
        void pokeIsNotAWrite() {
            assertThat(errorIdOf("t: 1.2.3.4 poke t 2 99")).isEqualTo("expect-arg");
        }
    }

    @Nested
    @DisplayName("REBTYPE(Tuple) arithmetic: octet by octet, then clamped")
    class Arithmetic {

        @Test
        @DisplayName("a single number applies to every octet")
        void oneNumberSpreads() {
            assertThat(answerTo("(1.2.3 + 1) == 2.3.4")).isEqualTo("#(true)");
            assertThat(answerTo("(1.2.3 - 1) == 0.1.2")).isEqualTo("#(true)");
            assertThat(answerTo("(1.2.3 * 2) == 2.4.6")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an answer outside an octet is clamped, never wrapped")
        void theBoundaries() {
            assertThat(answerTo("(255.255.255 + 1) == 255.255.255")).isEqualTo("#(true)");
            assertThat(answerTo("(0.0.0 - 1) == 0.0.0")).isEqualTo("#(true)");
            assertThat(answerTo("(255.255.255 + 0) == 255.255.255"))
                    .as("the on point is untouched")
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("two tuples work octet against octet and take the longer length")
        void twoTuplesTogether() {
            assertThat(answerTo("(1.2.3 + 1.1.1) == 2.3.4")).isEqualTo("#(true)");
            assertThat(answerTo("(1.2.3 + 0.0.0.0) == 1.2.3.0"))
                    .as("the shorter side contributes zeros and the answer keeps four")
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("dividing by a whole number truncates each octet")
        void integerDivisionTruncates() {
            assertThat(answerTo("(1.2.3 / 2) == 0.1.1")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("dividing by a decimal rounds each octet")
        void decimalDivisionRounds() {
            assertThat(answerTo("(1.1.1 / 0.625) == 2.2.2")).isEqualTo("#(true)");
            assertThat(answerTo("(1.1.1 / 0.1) == 10.10.10")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a division that leaves the range clamps at either end")
        void divisionSaturates() {
            assertThat(answerTo("(1.1.1 / 1.953125E-3) == 255.255.255")).isEqualTo("#(true)");
            assertThat(answerTo("(1.1.1 / -1.0) == 0.0.0")).isEqualTo("#(true)");
            assertThat(answerTo("(1.1.1 / 4.656612873077393e-10) == 255.255.255"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("dividing by zero raises")
        void theDegenerateDivisor() {
            assertThat(errorIdOf("1.2.3 / 0")).isEqualTo("zero-divide");
            assertThat(errorIdOf("1.2.3 / 0.0")).isEqualTo("zero-divide");
        }

        @Test
        @DisplayName("a factor above 255 saturates without being multiplied out")
        void multiplicationSaturates() {
            assertThat(answerTo("(1.1.1 * 2147483648.0) == 255.255.255")).isEqualTo("#(true)");
            assertThat(answerTo("(1.1.1 * 4.656612873077e+100) == 255.255.255"))
                    .isEqualTo("#(true)");
            assertThat(answerTo("(1.1.1 * 4656612873077) == 255.255.255")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a zero octet stays zero however large the factor")
        void zeroIsLeftAlone() {
            assertThat(answerTo("(0.0.0 * 4656612873077) == 0.0.0")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("REMAINDER works octet by octet too")
        void remainderIsOctetWise() {
            assertThat(answerTo("(10.20.30 % 7) == 3.6.2")).isEqualTo("#(true)");
            assertThat(answerTo("(remainder 10.20.30 7) == 3.6.2")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("raising a tuple to a power is refused")
        void thereIsNoPower() {
            assertThat(errorIdOf("1.2.3.4 ** 1")).isEqualTo("cannot-use");
        }
    }

    @Nested
    @DisplayName("REBTYPE(Tuple) logic: and, or, xor with an integer")
    class Logic {

        @Test
        @DisplayName("OR works on the whole integer and then clamps")
        void orIsSixtyFourBitThenClamped() {
            assertThat(answerTo("(1.2.3.255 or -1) == 0.0.0.0")).isEqualTo("#(true)");
            assertThat(answerTo("(1.2.3.255 or -11111111111) == 0.0.0.0")).isEqualTo("#(true)");
            assertThat(answerTo("(1.2.3.4 or 1) == 1.3.3.5")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("AND works the same way")
        void andIsSixtyFourBitThenClamped() {
            assertThat(answerTo("(1.2.3.255 and -1) == 1.2.3.255")).isEqualTo("#(true)");
            assertThat(answerTo("(1.2.3.255 and -1111111111) == 1.0.1.57")).isEqualTo("#(true)");
            assertThat(answerTo("(1.2.3.255 and -11111111111) == 1.0.1.57")).isEqualTo("#(true)");
            assertThat(answerTo("(1.2.3.4 and 1) == 1.0.1.0")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("XOR works the same way")
        void xorIsSixtyFourBitThenClamped() {
            assertThat(answerTo("(1.2.3.255 xor -1) == 0.0.0.0")).isEqualTo("#(true)");
            assertThat(answerTo("(1.2.3.255 xor -1111111111) == 0.0.0.0")).isEqualTo("#(true)");
            assertThat(answerTo("(1.2.3.4 xor 1) == 0.3.2.5")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a decimal is not a logic operand")
        void aDecimalIsRefused() {
            assertThat(errorIdOf("1.2.3 or 1.0")).isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("COMPLEMENT flips every kept octet")
        void complementFlipsTheKeptOctets() {
            assertThat(answerTo("(complement 1.0.0) == 254.255.255")).isEqualTo("#(true)");
            assertThat(answerTo("(complement 0.0.0) == 255.255.255")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("REBTYPE(Tuple) A_RANDOM")
    class Randomising {

        @Test
        @DisplayName("RANDOM keeps each octet between zero and what it was")
        void eachOctetStaysWithinItself() {
            assertThat(answerTo("t: random 9.9.9 all [t/1 <= 9 t/2 <= 9 t/3 <= 9]"))
                    .isEqualTo("#(true)");
            assertThat(answerTo("tuple? random 1.2.3")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a zero octet has nowhere to go and stays zero")
        void theDegenerateOctet() {
            assertThat(answerTo("(random 0.0.0) == 0.0.0")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("RANDOM keeps the length")
        void theLengthSurvives() {
            assertThat(answerTo("length? random 1.2.3.4")).isEqualTo("4");
        }
    }

    @Nested
    @DisplayName("REBTYPE(Tuple) A_REVERSE")
    class Reversing {

        @Test
        @DisplayName("REVERSE turns the kept octets around")
        void theOrdinaryCase() {
            assertThat(answerTo("(reverse 1.2.3) == 3.2.1")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("REVERSE works over the kept length, zeros and all")
        void theZerosAreReversedToo() {
            assertThat(answerTo("(reverse to tuple! \"1\") == 0.0.1")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("/PART reverses only that many octets")
        void partStopsEarly() {
            assertThat(answerTo("(reverse/part 1.2.3.4.5 3) == 3.2.1.4.5")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a /PART of none or one changes nothing")
        void theDegeneratePart() {
            assertThat(answerTo("(reverse/part 1.2.3 0) == 1.2.3")).isEqualTo("#(true)");
            assertThat(answerTo("(reverse/part 1.2.3 1) == 1.2.3")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a /PART longer than the tuple stops at the end")
        void thePartIsClamped() {
            assertThat(answerTo("(reverse/part 1.2.3 9) == 3.2.1")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a negative /PART is refused")
        void theOffPointBelowZero() {
            assertThat(errorIdOf("reverse/part 1.2.3 -1")).isNotEqualTo("no-error");
        }
    }

    @Nested
    @DisplayName("REBNATIVE(lerp) over tuples")
    class Interpolating {

        @Test
        @DisplayName("a factor of none and of one give the two ends")
        void theTwoEnds() {
            assertThat(answerTo("(lerp 10.100.255 255.128.64 0.0) == 10.100.255"))
                    .isEqualTo("#(true)");
            assertThat(answerTo("(lerp 10.100.255 255.128.64 1.0) == 255.128.64"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a factor between them interpolates and truncates")
        void theMiddle() {
            assertThat(answerTo("(lerp 10.100.255 255.128.64 0.3) == 83.108.197"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a factor outside 0 to 1 is clamped rather than extrapolated")
        void theOffPoints() {
            assertThat(answerTo("(lerp 10.100.255 255.128.64 2.0) == 255.128.64"))
                    .isEqualTo("#(true)");
            assertThat(answerTo("(lerp 10.100.255 255.128.64 -2.0) == 10.100.255"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a percent is a factor as readily as a decimal")
        void aPercentFactor() {
            assertThat(answerTo("(lerp 10.100.255 255.128.64 0%) == 10.100.255"))
                    .isEqualTo("#(true)");
            assertThat(answerTo("(lerp 10.100.255 255.128.64 30%) == 83.108.197"))
                    .isEqualTo("#(true)");
            assertThat(answerTo("(lerp 10.100.255 255.128.64 100%) == 255.128.64"))
                    .isEqualTo("#(true)");
            assertThat(answerTo("(lerp 10.100.255 255.128.64 200%) == 255.128.64"))
                    .isEqualTo("#(true)");
            assertThat(answerTo("(lerp 10.100.255 255.128.64 -200%) == 10.100.255"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("both ends must be the same kind of thing")
        void aMixedPairIsRefused() {
            assertThat(errorIdOf("lerp 0.0.0 10x10 0")).isEqualTo("type-mismatch");
        }
    }
}
