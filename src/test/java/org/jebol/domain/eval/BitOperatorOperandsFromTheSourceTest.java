package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class BitOperatorOperandsFromTheSourceTest {

    private static final String A_WHOLE_NUMBER = "12";
    private static final String ANOTHER_WHOLE_NUMBER = "10";
    private static final String A_TRUTH = "true";
    private static final String ANOTHER_TRUTH = "false";
    private static final String A_POINT = "12x10";
    private static final String ANOTHER_POINT = "10x12";
    private static final String A_TUPLE = "12.10.6";
    private static final String ANOTHER_TUPLE = "10.12.6";
    private static final String A_CHARACTER = """
            #"L"
            """.strip();
    private static final String ANOTHER_CHARACTER = """
            #"J"
            """.strip();
    private static final String SOME_OCTETS = "#{0F10}";
    private static final String OTHER_OCTETS = "#{33}";
    private static final String A_BITSET = """
            charset "ab"
            """.strip();
    private static final String ANOTHER_BITSET = """
            charset "bc"
            """.strip();
    private static final String A_TYPESET = "any-string!";
    private static final String ANOTHER_TYPESET = "any-block!";
    private static final String A_DATATYPE = "integer!";
    private static final String A_VECTOR = "make vector! [integer! 8 [12 10 6]]";
    private static final String ANOTHER_VECTOR = "make vector! [integer! 8 [10 12 6]]";

    private static final List<String> THE_THREE_OPERATORS = List.of("and~", "or~", "xor~");

    private static String outcomeOf(String operator, String left, String right) {
        String asked = "e: try [" + operator + " (" + left + ") (" + right + ")] "
                + "either error? e [rejoin [{!} e/id]] [mold e]";
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(asked);
        String answered = interpreter.display(interpreter.run(asked));
        return answered.startsWith("\"") && answered.endsWith("\"")
                ? answered.substring(1, answered.length() - 1)
                : answered;
    }

    private static void refusedBy(String left, String right, String failure) {
        for (String operator : THE_THREE_OPERATORS) {
            assertThat(outcomeOf(operator, left, right))
                    .as("%s (%s) (%s)", operator, left, right)
                    .isEqualTo("!" + failure);
        }
    }

    private static void acceptedBy(String left, String right) {
        for (String operator : THE_THREE_OPERATORS) {
            assertThat(outcomeOf(operator, left, right))
                    .as("%s (%s) (%s)", operator, left, right)
                    .doesNotStartWith("!");
        }
    }

    @Nested
    @DisplayName("the ten datatypes in the declaration are the outer gate")
    class TheArgumentCheckClosesFirst {

        static Stream<String> datatypesOutsideTheTen() {
            return Stream.of("1.5", "50%", "$5", "0:00:01", "1-Jan-2020",
                    "\"ab\"", "%a", "<t>", "#i", "[1 2]", "[]", "\"\"",
                    "'a", "quote a:", "none", "make object! []",
                    "try [1 / 0]", "()");
        }

        @ParameterizedTest(name = "and~ 12 ({0})")
        @MethodSource("datatypesOutsideTheTen")
        @DisplayName("on the right it is expect-arg, from the argument check")
        void onTheRightItIsRefusedAtTheDoor(String outsider) {
            refusedBy(A_WHOLE_NUMBER, outsider, "expect-arg");
        }

        @ParameterizedTest(name = "and~ ({0}) 10")
        @MethodSource("datatypesOutsideTheTen")
        @DisplayName("and on the left it is expect-arg too")
        void onTheLeftItIsRefusedAtTheDoor(String outsider) {
            refusedBy(outsider, ANOTHER_WHOLE_NUMBER, "expect-arg");
        }

        @Test
        @DisplayName("both sides outside is still the one failure")
        void bothSidesOutside() {
            refusedBy("1.5", "2.5", "expect-arg");
        }

        @Test
        @DisplayName("the gate closes before the left operand's own code runs")
        void theGateClosesBeforeTheLeftOperandsOwnCode() {
            refusedBy(A_DATATYPE, "1.5", "expect-arg");
        }
    }

    @Nested
    @DisplayName("a whole number takes a whole number or a character")
    class AWholeNumberOnTheLeft {

        static Stream<String> everythingElseOfTheTen() {
            return Stream.of(A_TRUTH, A_POINT, A_TUPLE, SOME_OCTETS,
                    A_BITSET, A_TYPESET, A_DATATYPE, A_VECTOR);
        }

        @Test
        @DisplayName("a whole number and a character are both taken")
        void theTwoItTakes() {
            acceptedBy(A_WHOLE_NUMBER, ANOTHER_WHOLE_NUMBER);
            acceptedBy(A_WHOLE_NUMBER, ANOTHER_CHARACTER);
        }

        @ParameterizedTest(name = "12 and {0}")
        @MethodSource("everythingElseOfTheTen")
        @DisplayName("everything else of the ten is not-related")
        void everythingElseIsNotRelated(String right) {
            refusedBy(A_WHOLE_NUMBER, right, "not-related");
        }

        @Test
        @DisplayName("a point on the right is refused, not cast to a point")
        void aPointOnTheRightIsRefusedRatherThanCrashing() {
            assertThat(outcomeOf("and~", A_WHOLE_NUMBER, ANOTHER_POINT))
                    .isEqualTo("!not-related");
        }
    }

    @Nested
    @DisplayName("a character takes a character or a whole number")
    class ACharacterOnTheLeft {

        static Stream<String> everythingElseOfTheTen() {
            return Stream.of(A_TRUTH, A_POINT, A_TUPLE, SOME_OCTETS,
                    A_BITSET, A_TYPESET, A_DATATYPE, A_VECTOR);
        }

        @Test
        @DisplayName("a character and a whole number are both taken")
        void theTwoItTakes() {
            acceptedBy(A_CHARACTER, ANOTHER_CHARACTER);
            acceptedBy(A_CHARACTER, ANOTHER_WHOLE_NUMBER);
        }

        @ParameterizedTest(name = "a character and {0}")
        @MethodSource("everythingElseOfTheTen")
        @DisplayName("everything else of the ten is not-related")
        void everythingElseIsNotRelated(String right) {
            refusedBy(A_CHARACTER, right, "not-related");
        }
    }

    @Nested
    @DisplayName("a truth takes a truth, and refuses the rest with expect-val")
    class ATruthOnTheLeft {

        static Stream<String> everythingElseOfTheTen() {
            return Stream.of(ANOTHER_WHOLE_NUMBER, ANOTHER_POINT, ANOTHER_TUPLE,
                    ANOTHER_CHARACTER, OTHER_OCTETS, ANOTHER_BITSET,
                    ANOTHER_TYPESET, A_DATATYPE, ANOTHER_VECTOR);
        }

        @Test
        @DisplayName("a truth is taken")
        void theOneItTakes() {
            acceptedBy(A_TRUTH, ANOTHER_TRUTH);
        }

        @ParameterizedTest(name = "true and {0}")
        @MethodSource("everythingElseOfTheTen")
        @DisplayName("everything else of the ten is expect-val, which no other kind raises")
        void everythingElseIsExpectVal(String right) {
            refusedBy(A_TRUTH, right, "expect-val");
        }
    }

    @Nested
    @DisplayName("a point takes a point or a whole number")
    class APointOnTheLeft {

        static Stream<String> everythingElseOfTheTen() {
            return Stream.of(A_TRUTH, ANOTHER_TUPLE, ANOTHER_CHARACTER, OTHER_OCTETS,
                    ANOTHER_BITSET, ANOTHER_TYPESET, A_DATATYPE, ANOTHER_VECTOR);
        }

        @Test
        @DisplayName("a point and a whole number are both taken")
        void theTwoItTakes() {
            acceptedBy(A_POINT, ANOTHER_POINT);
            acceptedBy(A_POINT, ANOTHER_WHOLE_NUMBER);
        }

        @ParameterizedTest(name = "12x10 and {0}")
        @MethodSource("everythingElseOfTheTen")
        @DisplayName("everything else of the ten is not-related, a character included")
        void everythingElseIsNotRelated(String right) {
            refusedBy(A_POINT, right, "not-related");
        }
    }

    @Nested
    @DisplayName("a tuple takes a tuple or a whole number")
    class ATupleOnTheLeft {

        static Stream<String> everythingElseOfTheTen() {
            return Stream.of(A_TRUTH, ANOTHER_POINT, ANOTHER_CHARACTER, OTHER_OCTETS,
                    ANOTHER_BITSET, ANOTHER_TYPESET, A_DATATYPE, ANOTHER_VECTOR);
        }

        @Test
        @DisplayName("a tuple and a whole number are both taken")
        void theTwoItTakes() {
            acceptedBy(A_TUPLE, ANOTHER_TUPLE);
            acceptedBy(A_TUPLE, ANOTHER_WHOLE_NUMBER);
        }

        @ParameterizedTest(name = "12.10.6 and {0}")
        @MethodSource("everythingElseOfTheTen")
        @DisplayName("everything else of the ten is not-related")
        void everythingElseIsNotRelated(String right) {
            refusedBy(A_TUPLE, right, "not-related");
        }
    }

    @Nested
    @DisplayName("a binary takes a binary, and refuses the rest with invalid-arg")
    class ABinaryOnTheLeft {

        static Stream<String> everythingElseOfTheTen() {
            return Stream.of(ANOTHER_WHOLE_NUMBER, A_TRUTH, ANOTHER_POINT,
                    ANOTHER_TUPLE, ANOTHER_CHARACTER, ANOTHER_BITSET,
                    ANOTHER_TYPESET, A_DATATYPE, ANOTHER_VECTOR);
        }

        @Test
        @DisplayName("a binary is taken")
        void theOneItTakes() {
            acceptedBy(SOME_OCTETS, OTHER_OCTETS);
        }

        @ParameterizedTest(name = "a binary and {0}")
        @MethodSource("everythingElseOfTheTen")
        @DisplayName("everything else of the ten is invalid-arg, a whole number included")
        void everythingElseIsInvalidArg(String right) {
            refusedBy(SOME_OCTETS, right, "invalid-arg");
        }
    }

    @Nested
    @DisplayName("a bitset takes a bitset or a binary")
    class ABitsetOnTheLeft {

        static Stream<String> everythingElseOfTheTen() {
            return Stream.of(ANOTHER_WHOLE_NUMBER, A_TRUTH, ANOTHER_POINT,
                    ANOTHER_TUPLE, ANOTHER_CHARACTER, ANOTHER_TYPESET,
                    A_DATATYPE, ANOTHER_VECTOR);
        }

        @Test
        @DisplayName("a bitset and a binary are both taken")
        void theTwoItTakes() {
            acceptedBy(A_BITSET, ANOTHER_BITSET);
            acceptedBy(A_BITSET, OTHER_OCTETS);
        }

        @ParameterizedTest(name = "a bitset and {0}")
        @MethodSource("everythingElseOfTheTen")
        @DisplayName("everything else of the ten is not-related")
        void everythingElseIsNotRelated(String right) {
            refusedBy(A_BITSET, right, "not-related");
        }
    }

    @Nested
    @DisplayName("a typeset takes a typeset or a datatype")
    class ATypesetOnTheLeft {

        static Stream<String> everythingElseOfTheTen() {
            return Stream.of(ANOTHER_WHOLE_NUMBER, A_TRUTH, ANOTHER_POINT,
                    ANOTHER_TUPLE, ANOTHER_CHARACTER, OTHER_OCTETS,
                    ANOTHER_BITSET, ANOTHER_VECTOR);
        }

        @Test
        @DisplayName("a typeset and a datatype are both taken")
        void theTwoItTakes() {
            acceptedBy(A_TYPESET, ANOTHER_TYPESET);
            acceptedBy(A_TYPESET, A_DATATYPE);
        }

        @ParameterizedTest(name = "a typeset and {0}")
        @MethodSource("everythingElseOfTheTen")
        @DisplayName("everything else of the ten is invalid-arg")
        void everythingElseIsInvalidArg(String right) {
            refusedBy(A_TYPESET, right, "invalid-arg");
        }
    }

    @Nested
    @DisplayName("a datatype has no bit operations at all")
    class ADatatypeOnTheLeft {

        static Stream<String> allTenOfThem() {
            return Stream.of(ANOTHER_WHOLE_NUMBER, A_TRUTH, ANOTHER_POINT,
                    ANOTHER_TUPLE, ANOTHER_CHARACTER, OTHER_OCTETS,
                    ANOTHER_BITSET, ANOTHER_TYPESET, A_DATATYPE, ANOTHER_VECTOR);
        }

        @ParameterizedTest(name = "integer! and {0}")
        @MethodSource("allTenOfThem")
        @DisplayName("every one of the ten is cannot-use, another datatype included")
        void everyOneOfTheTenIsCannotUse(String right) {
            refusedBy(A_DATATYPE, right, "cannot-use");
        }
    }

    @Nested
    @DisplayName("a vector takes a vector or a whole number")
    class AVectorOnTheLeft {

        static Stream<String> everythingElseOfTheTen() {
            return Stream.of(A_TRUTH, ANOTHER_POINT, ANOTHER_TUPLE,
                    ANOTHER_CHARACTER, OTHER_OCTETS, ANOTHER_BITSET,
                    ANOTHER_TYPESET, A_DATATYPE);
        }

        @Test
        @DisplayName("a vector and a whole number are both taken")
        void theTwoItTakes() {
            acceptedBy(A_VECTOR, ANOTHER_VECTOR);
            acceptedBy(A_VECTOR, ANOTHER_WHOLE_NUMBER);
        }

        @ParameterizedTest(name = "a vector and {0}")
        @MethodSource("everythingElseOfTheTen")
        @DisplayName("everything else of the ten is cannot-use")
        void everythingElseIsCannotUse(String right) {
            refusedBy(A_VECTOR, right, "cannot-use");
        }
    }

    @Nested
    @DisplayName("the answer's datatype is the left operand's")
    class TheLeftOperandDecidesTheAnswersDatatype {

        private static String theDatatypeAnsweredFor(String left, String right) {
            String asked = "mold type? and~ (" + left + ") (" + right + ")";
            Interpreter interpreter = Interpreter.create();
            interpreter.defineFreshWordsIn(asked);
            String shown = interpreter.display(interpreter.run(asked));
            return shown.replace("\"", "");
        }

        static Stream<Arguments> everyPairThatWorks() {
            return Stream.of(
                    Arguments.of(
                            A_WHOLE_NUMBER, ANOTHER_WHOLE_NUMBER, "#(integer!)"),
                    Arguments.of(
                            A_WHOLE_NUMBER, ANOTHER_CHARACTER, "#(integer!)"),
                    Arguments.of(
                            A_CHARACTER, ANOTHER_WHOLE_NUMBER, "#(char!)"),
                    Arguments.of(
                            A_CHARACTER, ANOTHER_CHARACTER, "#(char!)"),
                    Arguments.of(
                            A_TRUTH, ANOTHER_TRUTH, "#(logic!)"),
                    Arguments.of(
                            A_POINT, ANOTHER_WHOLE_NUMBER, "#(pair!)"),
                    Arguments.of(
                            A_POINT, ANOTHER_POINT, "#(pair!)"),
                    Arguments.of(
                            A_TUPLE, ANOTHER_WHOLE_NUMBER, "#(tuple!)"),
                    Arguments.of(
                            A_TUPLE, ANOTHER_TUPLE, "#(tuple!)"),
                    Arguments.of(
                            SOME_OCTETS, OTHER_OCTETS, "#(binary!)"),
                    Arguments.of(
                            A_BITSET, OTHER_OCTETS, "#(bitset!)"),
                    Arguments.of(
                            A_BITSET, ANOTHER_BITSET, "#(bitset!)"),
                    Arguments.of(
                            A_TYPESET, ANOTHER_TYPESET, "#(typeset!)"),
                    Arguments.of(
                            A_TYPESET, A_DATATYPE, "#(typeset!)"),
                    Arguments.of(
                            A_VECTOR, ANOTHER_WHOLE_NUMBER, "#(vector!)"),
                    Arguments.of(
                            A_VECTOR, ANOTHER_VECTOR, "#(vector!)"));
        }

        @ParameterizedTest(name = "({0}) and ({1}) answers {2}")
        @MethodSource("everyPairThatWorks")
        @DisplayName("every pair that works answers the left operand's datatype")
        void everyPairThatWorksAnswersTheLeftsDatatype(
                String left, String right, String datatype) {
            assertThat(theDatatypeAnsweredFor(left, right)).isEqualTo(datatype);
        }

        @Test
        @DisplayName("the same two values either way round answer two datatypes")
        void theSameTwoValuesGiveTwoDatatypes() {
            assertThat(theDatatypeAnsweredFor(A_WHOLE_NUMBER, ANOTHER_CHARACTER))
                    .isEqualTo("#(integer!)");
            assertThat(theDatatypeAnsweredFor(A_CHARACTER, ANOTHER_WHOLE_NUMBER))
                    .isEqualTo("#(char!)");
        }
    }

    @Nested
    @DisplayName("neither operand is changed by the operation")
    class NeitherOperandIsChanged {

        private static String afterCombining(String left, String right, String read) {
            String asked = "left: (" + left + ") right: (" + right + ") "
                    + "and~ left right mold " + read;
            Interpreter interpreter = Interpreter.create();
            interpreter.defineFreshWordsIn(asked);
            return interpreter.display(interpreter.run(asked)).replace("\"", "");
        }

        @Test
        @DisplayName("a bitset on either side keeps its bits")
        void aBitsetKeepsItsBits() {
            assertThat(afterCombining(A_BITSET, ANOTHER_BITSET, "left"))
                    .isEqualTo(moldOf(A_BITSET));
            assertThat(afterCombining(A_BITSET, ANOTHER_BITSET, "right"))
                    .isEqualTo(moldOf(ANOTHER_BITSET));
        }

        @Test
        @DisplayName("a typeset on either side keeps its datatypes")
        void aTypesetKeepsItsDatatypes() {
            assertThat(afterCombining(A_TYPESET, ANOTHER_TYPESET, "left"))
                    .isEqualTo(moldOf(A_TYPESET));
            assertThat(afterCombining(A_TYPESET, ANOTHER_TYPESET, "right"))
                    .isEqualTo(moldOf(ANOTHER_TYPESET));
        }

        @Test
        @DisplayName("a vector on either side keeps its numbers")
        void aVectorKeepsItsNumbers() {
            assertThat(afterCombining(A_VECTOR, ANOTHER_VECTOR, "left"))
                    .isEqualTo(moldOf(A_VECTOR));
            assertThat(afterCombining(A_VECTOR, ANOTHER_VECTOR, "right"))
                    .isEqualTo(moldOf(ANOTHER_VECTOR));
        }

        @Test
        @DisplayName("a binary on either side keeps its bytes")
        void aBinaryKeepsItsBytes() {
            assertThat(afterCombining(SOME_OCTETS, OTHER_OCTETS, "left"))
                    .isEqualTo(moldOf(SOME_OCTETS));
            assertThat(afterCombining(SOME_OCTETS, OTHER_OCTETS, "right"))
                    .isEqualTo(moldOf(OTHER_OCTETS));
        }

        @Test
        @DisplayName("a tuple on either side keeps its octets")
        void aTupleKeepsItsOctets() {
            assertThat(afterCombining(A_TUPLE, ANOTHER_TUPLE, "left"))
                    .isEqualTo(moldOf(A_TUPLE));
            assertThat(afterCombining(A_TUPLE, ANOTHER_TUPLE, "right"))
                    .isEqualTo(moldOf(ANOTHER_TUPLE));
        }

        @Test
        @DisplayName("a point on either side keeps its halves")
        void aPointKeepsItsHalves() {
            assertThat(afterCombining(A_POINT, ANOTHER_POINT, "left"))
                    .isEqualTo(moldOf(A_POINT));
            assertThat(afterCombining(A_POINT, ANOTHER_POINT, "right"))
                    .isEqualTo(moldOf(ANOTHER_POINT));
        }

        private static String moldOf(String source) {
            String asked = "mold (" + source + ")";
            Interpreter interpreter = Interpreter.create();
            interpreter.defineFreshWordsIn(asked);
            return interpreter.display(interpreter.run(asked)).replace("\"", "");
        }
    }

    @Nested
    @DisplayName("a degenerate operand is an ordinary operand")
    class DegenerateOperandsAreOrdinaryOperands {

        @ParameterizedTest(name = "{0} is {1}")
        @MethodSource("theDegenerateOnes")
        void theAnswerIsTheOrdinaryOne(String source, String expected) {
            Interpreter interpreter = Interpreter.create();
            interpreter.defineFreshWordsIn(source);
            assertThat(interpreter.display(interpreter.run(source)))
                    .as(source)
                    .isEqualTo(expected);
        }

        static Stream<Arguments> theDegenerateOnes() {
            return Stream.of(
                    Arguments.of(
                            "and~ #{} #{0F10}", "#{0000}"),
                    Arguments.of(
                            "and~ #{0F10} #{}", "#{0000}"),
                    Arguments.of(
                            "and~ #{} #{}", "#{}"),
                    Arguments.of(
                            "mold and~ (make bitset! #{}) (charset \"ab\")",
                            "\"#(bitset! #{})\""),
                    Arguments.of(
                            "mold and~ (make typeset! []) any-string!",
                            "\"make typeset! []\""),
                    Arguments.of(
                            "mold and~ (make vector! [integer! 8 []]) 10",
                            "\"#(int8! [])\""),
                    Arguments.of("and~ 0 0", "0"),
                    Arguments.of("and~ 0 12", "0"),
                    Arguments.of("and~ 12 0", "0"),
                    Arguments.of("and~ 0x0 0", "0x0"),
                    Arguments.of(
                            "and~ 0.0.0 0", "0.0.0"),
                    Arguments.of("and~ -1 -1", "-1"),
                    Arguments.of("or~ -1 0", "-1"),
                    Arguments.of("xor~ -1 -1", "0"),
                    Arguments.of(
                            "and~ false false", "#(false)"),
                    Arguments.of(
                            "or~ false false", "#(false)"));
        }
    }

    @Nested
    @DisplayName("every spelling of an operator reaches the same code")
    class EverySpellingAnswersTheSame {

        private static String answerTo(String source) {
            Interpreter interpreter = Interpreter.create();
            interpreter.defineFreshWordsIn(source);
            return interpreter.display(interpreter.run(source));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "and~ 12 10", "12 and 10", "12 & 10"})
        @DisplayName("the three spellings of AND agree on whole numbers")
        void theThreeSpellingsOfAndAgree(String source) {
            assertThat(answerTo(source)).isEqualTo("8");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "or~ 12 10", "12 or 10", "12 | 10"})
        @DisplayName("the three spellings of OR agree on whole numbers")
        void theThreeSpellingsOfOrAgree(String source) {
            assertThat(answerTo(source)).isEqualTo("14");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"xor~ 12 10", "12 xor 10"})
        @DisplayName("the two spellings of XOR agree on whole numbers")
        void theTwoSpellingsOfXorAgree(String source) {
            assertThat(answerTo(source)).isEqualTo("6");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "and~ 12x10 10x12", "12x10 and 10x12", "12x10 & 10x12"})
        @DisplayName("and they agree on a datatype that is not a whole number")
        void theSpellingsAgreeOnAPointToo(String source) {
            assertThat(answerTo(source)).isEqualTo("8x8");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "e: try [and~ 12 10x12] e/id",
                "e: try [12 and 10x12] e/id",
                "e: try [12 & 10x12] e/id"})
        @DisplayName("and they agree on the refusal, rather than one of them crashing")
        void theSpellingsAgreeOnTheRefusal(String source) {
            assertThat(answerTo(source)).isEqualTo("not-related");
        }
    }
}
