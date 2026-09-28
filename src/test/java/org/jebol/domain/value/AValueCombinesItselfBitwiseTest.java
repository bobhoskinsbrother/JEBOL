package org.jebol.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AValueCombinesItselfBitwiseTest {

    private static final Value A_WHOLE_NUMBER = IntegerValue.of(12);
    private static final Value ANOTHER_WHOLE_NUMBER = IntegerValue.of(10);
    private static final Value A_TRUTH = LogicValue.of(true);
    private static final Value A_POINT = PairValue.of(12, 10);
    private static final Value A_TUPLE = TupleValue.of(12, 10, 6);
    private static final Value A_CHARACTER = CharacterValue.of('L');
    private static final Value SOME_OCTETS = BinaryValue.of(0x0F, 0x10);
    private static final Value A_BITSET = BitsetValue.of(new byte[]{0x60});
    private static final Value A_TYPESET = TypesetValue.of(Set.of(Datatype.STRING));
    private static final Value A_DATATYPE = DatatypeValue.of(Datatype.INTEGER);
    private static final Value A_VECTOR = VectorValue.holding(VectorKind.INT8, 12, 10, 6);

    private static final List<Value> ALL_TEN = List.of(
            A_WHOLE_NUMBER, A_TRUTH, A_POINT, A_TUPLE, A_CHARACTER,
            SOME_OCTETS, A_BITSET, A_TYPESET, A_DATATYPE, A_VECTOR);

    private static final BitwiseOperation AND = BitwiseOperation.theOneCalled("and");

    private static void takes(Value left, Value right) {
        assertThat(left.bitwise(right, AND))
                .as("%s and %s", left.datatype(), right.datatype())
                .isNotNull();
    }

    private static void refuses(Value left, Value right, String failure) {
        assertThatThrownBy(() -> left.bitwise(right, AND))
                .as("%s and %s", left.datatype(), right.datatype())
                .isInstanceOf(Raised.class)
                .extracting(raised -> ((Raised) raised).error().errorId())
                .isEqualTo(failure);
    }

    private static void takesOnly(Value left, List<Value> taken, String failure) {
        for (Value right : ALL_TEN) {
            if (taken.contains(right)) {
                takes(left, right);
            } else {
                refuses(left, right, failure);
            }
        }
    }

    @Nested
    @DisplayName("each value answers for itself, and names its own failure")
    class EachValueAnswersForItself {

        @Test
        @DisplayName("a whole number takes a whole number or a character, else not-related")
        void aWholeNumber() {
            takesOnly(A_WHOLE_NUMBER,
                    List.of(A_WHOLE_NUMBER, A_CHARACTER), "not-related");
        }

        @Test
        @DisplayName("a character takes a character or a whole number, else not-related")
        void aCharacter() {
            takesOnly(A_CHARACTER,
                    List.of(A_CHARACTER, A_WHOLE_NUMBER), "not-related");
        }

        @Test
        @DisplayName("a truth takes a truth, else expect-val")
        void aTruth() {
            takesOnly(A_TRUTH, List.of(A_TRUTH), "expect-val");
        }

        @Test
        @DisplayName("a point takes a point or a whole number, else not-related")
        void aPoint() {
            takesOnly(A_POINT, List.of(A_POINT, A_WHOLE_NUMBER), "not-related");
        }

        @Test
        @DisplayName("a tuple takes a tuple or a whole number, else not-related")
        void aTuple() {
            takesOnly(A_TUPLE, List.of(A_TUPLE, A_WHOLE_NUMBER), "not-related");
        }

        @Test
        @DisplayName("a binary takes a binary, else invalid-arg")
        void aBinary() {
            takesOnly(SOME_OCTETS, List.of(SOME_OCTETS), "invalid-arg");
        }

        @Test
        @DisplayName("a bitset takes a bitset or a binary, else not-related")
        void aBitset() {
            takesOnly(A_BITSET, List.of(A_BITSET, SOME_OCTETS), "not-related");
        }

        @Test
        @DisplayName("a typeset takes a typeset or a datatype, else invalid-arg")
        void aTypeset() {
            takesOnly(A_TYPESET, List.of(A_TYPESET, A_DATATYPE), "invalid-arg");
        }

        @Test
        @DisplayName("a datatype takes nothing at all, another datatype included")
        void aDatatype() {
            takesOnly(A_DATATYPE, List.of(), "cannot-use");
        }

        @Test
        @DisplayName("a vector takes a vector or a whole number, else cannot-use")
        void aVector() {
            takesOnly(A_VECTOR, List.of(A_VECTOR, A_WHOLE_NUMBER), "cannot-use");
        }
    }

    @Nested
    @DisplayName("the answer is the left value's own datatype")
    class TheAnswerIsTheLeftValuesDatatype {

        @Test
        @DisplayName("the same two values either way round answer two datatypes")
        void theSameTwoValuesGiveTwoDatatypes() {
            assertThat(A_WHOLE_NUMBER.bitwise(A_CHARACTER, AND).datatype())
                    .isEqualTo(Datatype.INTEGER);
            assertThat(A_CHARACTER.bitwise(A_WHOLE_NUMBER, AND).datatype())
                    .isEqualTo(Datatype.CHAR);
        }

        @Test
        @DisplayName("every pair that works answers the left value's datatype")
        void everyPairThatWorksAnswersTheLeftsDatatype() {
            for (Value left : ALL_TEN) {
                for (Value right : ALL_TEN) {
                    Value answered;
                    try {
                        answered = left.bitwise(right, AND);
                    } catch (Raised refused) {
                        continue;
                    }
                    assertThat(answered.datatype())
                            .as("%s and %s", left.datatype(), right.datatype())
                            .isEqualTo(left.datatype());
                }
            }
        }
    }

    @Nested
    @DisplayName("a datatype with no bit operations refuses rather than answering")
    class ADatatypeWithNoBitOperations {

        @Test
        @DisplayName("a string, a decimal and a none all refuse")
        void theOnesOutsideTheTen() {
            for (Value outsider : List.of(
                    StringValue.of("ab"), DecimalValue.of(1.5), NoneValue.none())) {
                assertThatThrownBy(() -> outsider.bitwise(A_WHOLE_NUMBER, AND))
                        .as("%s", outsider.datatype())
                        .isInstanceOf(Raised.class)
                        .extracting(raised -> ((Raised) raised).error().errorId())
                        .isEqualTo("expect-arg");
            }
        }
    }

    @Nested
    @DisplayName("neither value is changed by combining")
    class NeitherValueIsChanged {

        @Test
        @DisplayName("a bitset keeps its bits")
        void aBitsetKeepsItsBits() {
            BitsetValue ours = BitsetValue.of(new byte[]{0x60});
            BitsetValue theirs = BitsetValue.of(new byte[]{0x30});
            ours.bitwise(theirs, AND);
            assertThat(ours.octets()).containsExactly((byte) 0x60);
            assertThat(theirs.octets()).containsExactly((byte) 0x30);
        }
    }
}
