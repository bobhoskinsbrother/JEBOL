package org.jebol.domain.value;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Arithmetic on a whole vector at once.
 *
 * <p>Two shapes, and {@code REBTYPE(Vector)} sends them to two functions. A
 * vector against a number applies the number to every element and answers a
 * new vector; a vector against another vector works element by element, as far
 * as the shorter of the two goes.
 *
 * <p>Neither ever complains about a number that will not fit. The point of
 * asking for an {@code int8!} vector is that it holds bytes, so 200 added to
 * 4 is -52 and that is the answer, not an error. Rebol's own test file says
 * so in as many words: "the values are truncated on overflow".
 */
class VectorMathFromTheSourceTest {

    private static final String TRUE = "#(true)";

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String holds(String expression, String expected) {
        return answerTo("(" + expression + ") == " + expected);
    }

    private static String whatHappensTo(String source) {
        return answerTo("either error? e: try [" + source + "] [e/id] ['accepted]");
    }

    @Nested
    @DisplayName("a vector and a number")
    class AgainstANumber {

        @Test
        @DisplayName("adding and subtracting reach every element")
        void addingAndSubtracting() {
            assertThat(holds("#(u8! [1 2 3 4]) + 200", "#(u8! [201 202 203 204])"))
                    .isEqualTo(TRUE);
            assertThat(holds("#(u8! [201 202 203 204]) - 200", "#(u8! [1 2 3 4])"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and what will not fit wraps, at both ends of the range")
        void overflowWraps() {
            assertThat(holds("#(u8! [201 202 203 204]) + 200", "#(u8! [145 146 147 148])"))
                    .isEqualTo(TRUE);
            assertThat(holds("#(i8! [1 2 3 4]) + 125", "#(i8! [126 127 -128 -127])"))
                    .as("a signed byte wraps from 127 round to -128")
                    .isEqualTo(TRUE);
            assertThat(holds("#(u8! [1 2 3 4]) * 20", "#(u8! [20 40 60 80])"))
                    .isEqualTo(TRUE);
            assertThat(holds("#(u8! [4 8 12 16]) * 20", "#(u8! [80 160 240 64])"))
                    .as("sixteen twenties is 320, which is 64 once the byte fills")
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("the number may be on either side")
        void thenumberMayLeadOrFollow() {
            assertThat(holds("10 * #(u16! [1 2 3 4])", "#(u16! [10 20 30 40])"))
                    .isEqualTo(TRUE);
            assertThat(holds("1 + #(u8! [1 2 3 4])", "#(u8! [2 3 4 5])")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a decimal against a counting vector is truncated first")
        void adecimalAgainstACountingVector() {
            assertThat(holds("10.0 * #(u16! [1 2 3 4])", "#(u16! [10 20 30 40])"))
                    .isEqualTo(TRUE);
            assertThat(holds("#(i8! [2 4 6 8]) * 2.4", "#(i8! [4 8 12 16])"))
                    .as("2.4 becomes 2 before it reaches an int8! vector, so this "
                            + "doubles rather than multiplying by two and a bit")
                    .isEqualTo(TRUE);
            assertThat(holds("-1.0 + #(u8! [2 3 4 5])", "#(u8! [1 2 3 4])")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a measuring vector keeps the decimal")
        void ameasuringVectorKeepsTheDecimal() {
            assertThat(holds("#(f64! [1 2 3 4]) + 0.5", "#(f64! [1.5 2.5 3.5 4.5])"))
                    .isEqualTo(TRUE);
            assertThat(holds("#(f64! [1 2 3 4]) * 20.5", "#(f64! [20.5 41.0 61.5 82.0])"))
                    .isEqualTo(TRUE);
        }

        @ParameterizedTest
        @ValueSource(strings = {"-", "/", "%", "and", "or", "xor"})
        @DisplayName("but only the two that do not care about order take it on the left")
        void onlyTheCommutativeOnesTakeTheNumberFirst(String operator) {
            assertThat(whatHappensTo("10 " + operator + " #(u16! [1 2 3 4])"))
                    .as("a number's own arm forwards to the vector for ADD and "
                            + "MULTIPLY and for nothing else, so 10 - v never "
                            + "reaches Math_Op_Vector to be read backwards")
                    .isEqualTo("not-related");
        }

        @Test
        @DisplayName("dividing a counting vector by zero is refused")
        void dividingByZero() {
            assertThat(whatHappensTo("#(u16! [1 2]) / 0")).isEqualTo("zero-divide");
            assertThat(whatHappensTo("#(u16! [1 2]) / 0.0"))
                    .as("the C truncates the divisor before it tests it, so a "
                            + "fraction below one divides by zero as surely as zero does")
                    .isEqualTo("zero-divide");
        }

        @Test
        @DisplayName("but dividing a measuring one by zero answers infinity")
        void dividingAmeasuringVectorByZero() {
            assertThat(whatHappensTo("#(f32! [1 2]) / 0"))
                    .as("the guard in the C reads `if (i == 0 && bits <= VTUI64)`, so "
                            + "a float vector is left to the machine and the machine "
                            + "has an answer")
                    .isEqualTo("accepted");
        }

        @Test
        @DisplayName("and the remainder by zero is refused whatever the vector holds")
        void theremainderByZero() {
            assertThat(whatHappensTo("#(i8! [1 2]) % 0")).isEqualTo("zero-divide");
            assertThat(whatHappensTo("#(f32! [1 2]) % 0"))
                    .as("A_REMAINDER tests the divisor before it looks at the kind, "
                            + "which is the one place the two guards differ")
                    .isEqualTo("zero-divide");
        }

        @ParameterizedTest
        @ValueSource(strings = {"int8!", "int16!", "int32!", "int64!",
                "uint8!", "uint16!", "uint32!", "uint64!"})
        @DisplayName("the bitwise three work on every counting kind")
        void thebitwiseThree(String kind) {
            assertThat(holds("#(" + kind + " [1 2 3 4]) or 2", "#(" + kind + " [3 2 3 6])"))
                    .isEqualTo(TRUE);
            assertThat(holds("#(" + kind + " [1 2 3 4]) and 10", "#(" + kind + " [0 2 2 0])"))
                    .isEqualTo(TRUE);
            assertThat(holds("#(" + kind + " [1 2 3 4]) xor 2", "#(" + kind + " [3 0 1 6])"))
                    .isEqualTo(TRUE);
        }

        @ParameterizedTest
        @CsvSource({"float32!, or", "float32!, and", "float32!, xor",
                "float64!, or", "float64!, and", "float64!, xor"})
        @DisplayName("but not on a measuring one, which has no bits to speak of")
        void thebitwiseThreeRefuseDecimals(String kind, String operator) {
            assertThat(whatHappensTo("#(" + kind + " [1 2]) " + operator + " 1"))
                    .isEqualTo("not-related");
        }

        @ParameterizedTest
        @ValueSource(strings = {"int8!", "uint32!", "float32!", "float64!"})
        @DisplayName("and the remainder works on every kind there is")
        void theremainder(String kind) {
            assertThat(holds("#(" + kind + " [1 2 3 4]) % 2", "#(" + kind + " [1 0 1 0])"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("working from a position leaves the vector it started from alone")
        void workingFromAPosition() {
            assertThat(answerTo("""
                    v: #(i8! [1 2 3 4])
                    all [(2 + skip v 2) == #(i8! [5 6])
                         v == #(i8! [1 2 3 4])]""")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("a vector and another vector")
    class AgainstAnotherVector {

        @Test
        @DisplayName("the four arithmetic operations work element by element")
        void elementByElement() {
            assertThat(holds("#(i8! [1 2]) + #(i8! [3 4])", "#(i8! [4 6])")).isEqualTo(TRUE);
            assertThat(holds("#(i8! [4 6]) - #(i8! [3 4])", "#(i8! [1 2])")).isEqualTo(TRUE);
            assertThat(holds("#(i8! [1 2]) * #(i8! [3 4])", "#(i8! [3 8])")).isEqualTo(TRUE);
            assertThat(holds("#(i8! [10 20]) / #(i8! [2 4])", "#(i8! [5 5])")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and stop at the shorter of the two")
        void stoppingAtTheShorter() {
            assertThat(holds("#(i16! [1 2]) + #(i16! [3 4 5])", "#(i16! [4 6])"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("each one counted from where it points")
        void countingFromEachPosition() {
            assertThat(holds("#(u32! [1 2]) + #(u32! [1 3 4] 2)", "#(u32! [4 6])"))
                    .isEqualTo(TRUE);
            assertThat(holds("#(f64! [1 1 2] 2) + #(f64! [1 3 4] 2)", "#(f64! [4 6])"))
                    .isEqualTo(TRUE);
        }

        @ParameterizedTest
        @ValueSource(strings = {"int8!", "int64!", "uint8!", "uint32!"})
        @DisplayName("the bitwise three work element by element too")
        void thebitwiseThree(String kind) {
            assertThat(holds("#(" + kind + " [1 2 3 4]) or #(" + kind + " [5 6 7 8])",
                    "#(" + kind + " [5 6 7 12])")).isEqualTo(TRUE);
            assertThat(holds("#(" + kind + " [1 2 3 4]) and #(" + kind + " [5 6 7 8])",
                    "#(" + kind + " [1 2 3 0])")).isEqualTo(TRUE);
            assertThat(holds("#(" + kind + " [1 2 3 4]) xor #(" + kind + " [5 6 7 8])",
                    "#(" + kind + " [4 4 4 12])")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the remainder does, on measuring vectors as well")
        void theremainder() {
            assertThat(holds("#(i8! [1 2 3 4]) % #(i8! [2 2 2 2])", "#(i8! [1 0 1 0])"))
                    .isEqualTo(TRUE);
            assertThat(holds("#(f64! [1 2 3 4]) % #(f64! [2 2 2 2])", "#(f64! [1 0 1 0])"))
                    .isEqualTo(TRUE);
        }

        @ParameterizedTest
        @ValueSource(strings = {"#(i16! [1 2])", "#(f32! [1 2])", "#(u8! [1 2])"})
        @DisplayName("two kinds that are not the same kind cannot be combined")
        void twodifferentKinds(String other) {
            assertThat(whatHappensTo("#(i8! [1 2]) + " + other))
                    .as("Math_Op_Vector_Vector compares the encodings and refuses "
                            + "outright rather than widening one to the other, so a "
                            + "different sign is as incompatible as a different width")
                    .isEqualTo("vector-not-compatible");
        }

        @Test
        @DisplayName("and a zero anywhere in the divisor is still a division by zero")
        void azeroInTheDivisor() {
            assertThat(whatHappensTo("#(i8! [10 20]) / #(i8! [2 0])"))
                    .isEqualTo("zero-divide");
        }

        @ParameterizedTest
        @CsvSource({"float32!, or", "float64!, and", "float64!, xor"})
        @DisplayName("the bitwise three refuse two measuring vectors as well")
        void thebitwiseThreeRefuseDecimals(String kind, String operator) {
            assertThat(whatHappensTo(
                    "#(" + kind + " [1 2]) " + operator + " #(" + kind + " [1 2])"))
                    .isEqualTo("not-related");
        }
    }

    @Nested
    @DisplayName("comparing two vectors")
    class Comparing {

        @Test
        @DisplayName("equal when the numbers are equal, whatever width holds them")
        void equalityIgnoresTheWidth() {
            assertThat(answerTo("#(u16! [1 2]) = #(u16! [1 2])")).isEqualTo(TRUE);
            assertThat(answerTo("#(u64! [1 2]) = #(u32! [1 2])")).isEqualTo(TRUE);
            assertThat(answerTo("#(i64! [-1]) = #(i32! [-1])")).isEqualTo(TRUE);
            assertThat(answerTo("equal? #(u16! [1 2]) #(u16! [1 2 3])")).isEqualTo("#(false)");
        }

        @Test
        @DisplayName("and ordered by the first number they differ at, then by length")
        void orderingIsElementwiseThenByLength() {
            assertThat(answerTo("#(u16! [1 2]) < #(u16! [1 2 0])")).isEqualTo(TRUE);
            assertThat(answerTo("#(u16! [1 2]) < #(u16! [2 2])")).isEqualTo(TRUE);
            assertThat(answerTo("#(u16! [2 2]) > #(u16! [1 2])")).isEqualTo(TRUE);
            assertThat(answerTo("#(i64! [-1]) < #(i32! [0])")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a signed value and an unsigned one are told apart at the widest width")
        void signednessIsNotIgnored() {
            assertThat(answerTo("v: #(u64! [-1]) w: #(i64! [-1]) not w = v"))
                    .as("the same sixty-four bits are the largest unsigned value and "
                            + "minus one, and treating them as equal would be the "
                            + "easiest mistake to make here")
                    .isEqualTo(TRUE);
            assertThat(answerTo("v: #(u64! [-1]) w: #(i64! [-1]) w < v")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a negative zero is a zero")
        void negativeZeroIsZero() {
            assertThat(answerTo("#(f64! [-0.0]) = #(f64! [0.0])")).isEqualTo(TRUE);
            assertThat(answerTo("not #(f64! [-0.0]) < #(f64! [0.0])")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("but a counting vector and a measuring one cannot be compared at all")
        void countingAgainstMeasuring() {
            assertThat(whatHappensTo("#(i64! [1 2]) = #(f64! [1.0 2.0])"))
                    .as("Compare_Vector raises rather than converting, because "
                            + "there is no answer that is not a guess")
                    .isEqualTo("not-same-type");
        }

        @Test
        @DisplayName("and a vector is never equal to a block of the same numbers")
        void avectorIsNotABlock() {
            assertThat(answerTo("#(i32! [1 2]) = [1 2]")).isEqualTo("#(false)");
        }
    }
}
