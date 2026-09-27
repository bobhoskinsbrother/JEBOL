package org.jebol.domain.eval.bit.bitType;

import org.jebol.domain.eval.Raised;
import org.jebol.domain.eval.arithmetic.BitwiseOperation;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.BitsetValue;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DatatypeValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.TypesetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.VectorKind;
import org.jebol.domain.value.VectorValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WhichBitTypeClaimsAPairTest {

    private static final Value A_WHOLE_NUMBER = IntegerValue.of(12);
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

    private static List<BitType> everyKind() {
        return List.of(new WholeNumbers(), new Characters(), new Truths(),
                new Points(), new Tuples(), new Octets(), new Bitsets(),
                new Typesets(), new Datatypes(), new Vectors());
    }

    private static void claimsEveryPairLedBy(BitType kind, Value mine) {
        for (Value right : ALL_TEN) {
            assertThat(kind.shouldHandle(mine, right))
                    .as("%s asked about its own kind on the left and %s on the right",
                            kind.getClass().getSimpleName(), right.datatype())
                    .isTrue();
        }
        for (Value left : ALL_TEN) {
            if (left == mine) {
                continue;
            }
            assertThat(kind.shouldHandle(left, mine))
                    .as("%s claimed %s on the left, which is not its kind",
                            kind.getClass().getSimpleName(), left.datatype())
                    .isFalse();
        }
    }

    private static void takes(BitType kind, Value left, Value right) {
        assertThat(kind.combine(left, right, AND))
                .as("%s given %s and %s",
                        kind.getClass().getSimpleName(), left.datatype(), right.datatype())
                .isNotNull();
    }

    private static void refuses(BitType kind, Value left, Value right, String failure) {
        assertThatThrownBy(() -> kind.combine(left, right, AND))
                .as("%s given %s and %s",
                        kind.getClass().getSimpleName(), left.datatype(), right.datatype())
                .isInstanceOf(Raised.class)
                .extracting(raised -> ((Raised) raised).error().errorId())
                .isEqualTo(failure);
    }

    private static void answeredOrNamedAFailure(BitType kind, Value left, Value right) {
        try {
            assertThat(kind.combine(left, right, AND))
                    .as("%s given %s and %s",
                            kind.getClass().getSimpleName(),
                            left.datatype(), right.datatype())
                    .isNotNull();
        } catch (Raised named) {
            assertThat(named.error().errorId())
                    .as("%s given %s and %s",
                            kind.getClass().getSimpleName(),
                            left.datatype(), right.datatype())
                    .isNotBlank();
        }
    }

    private static void refusesEverythingBut(
            BitType kind, Value left, String failure, List<Value> taken) {

        for (Value right : ALL_TEN) {
            if (taken.contains(right)) {
                takes(kind, left, right);
            } else {
                refuses(kind, left, right, failure);
            }
        }
    }

    @Nested
    @DisplayName("a kind claims by the operand on its left and nothing else")
    class WhatEachKindClaims {

        @Test
        @DisplayName("whole numbers claim every pair led by a whole number")
        void wholeNumbers() {
            claimsEveryPairLedBy(new WholeNumbers(), A_WHOLE_NUMBER);
        }

        @Test
        @DisplayName("characters claim every pair led by a character")
        void characters() {
            claimsEveryPairLedBy(new Characters(), A_CHARACTER);
        }

        @Test
        @DisplayName("truths claim every pair led by a truth")
        void truths() {
            claimsEveryPairLedBy(new Truths(), A_TRUTH);
        }

        @Test
        @DisplayName("points claim every pair led by a point")
        void points() {
            claimsEveryPairLedBy(new Points(), A_POINT);
        }

        @Test
        @DisplayName("tuples claim every pair led by a tuple")
        void tuples() {
            claimsEveryPairLedBy(new Tuples(), A_TUPLE);
        }

        @Test
        @DisplayName("octets claim every pair led by a binary")
        void octets() {
            claimsEveryPairLedBy(new Octets(), SOME_OCTETS);
        }

        @Test
        @DisplayName("bitsets claim every pair led by a bitset")
        void bitsets() {
            claimsEveryPairLedBy(new Bitsets(), A_BITSET);
        }

        @Test
        @DisplayName("typesets claim every pair led by a typeset")
        void typesets() {
            claimsEveryPairLedBy(new Typesets(), A_TYPESET);
        }

        @Test
        @DisplayName("datatypes claim every pair led by a datatype")
        void datatypes() {
            claimsEveryPairLedBy(new Datatypes(), A_DATATYPE);
        }

        @Test
        @DisplayName("vectors claim every pair led by a vector")
        void vectors() {
            claimsEveryPairLedBy(new Vectors(), A_VECTOR);
        }
    }

    @Nested
    @DisplayName("exactly one kind claims each pair, so the order cannot decide anything")
    class ExactlyOneKindClaimsEachPair {

        @Test
        @DisplayName("every one of the hundred pairs is claimed by exactly one kind")
        void everyPairIsClaimedOnce() {
            for (Value left : ALL_TEN) {
                for (Value right : ALL_TEN) {
                    List<String> claiming = everyKind().stream()
                            .filter(kind -> kind.shouldHandle(left, right))
                            .map(kind -> kind.getClass().getSimpleName())
                            .toList();
                    assertThat(claiming)
                            .as("%s and %s", left.datatype(), right.datatype())
                            .hasSize(1);
                }
            }
        }
    }

    @Nested
    @DisplayName("a kind that claims a pair answers it or names its own failure")
    class WhatEachKindDoesWithWhatItClaimed {

        @Test
        @DisplayName("whole numbers take a whole number or a character, else not-related")
        void wholeNumbers() {
            refusesEverythingBut(new WholeNumbers(), A_WHOLE_NUMBER, "not-related",
                    List.of(A_WHOLE_NUMBER, A_CHARACTER));
        }

        @Test
        @DisplayName("characters take a character or a whole number, else not-related")
        void characters() {
            refusesEverythingBut(new Characters(), A_CHARACTER, "not-related",
                    List.of(A_CHARACTER, A_WHOLE_NUMBER));
        }

        @Test
        @DisplayName("truths take a truth, else expect-val")
        void truths() {
            refusesEverythingBut(new Truths(), A_TRUTH, "expect-val", List.of(A_TRUTH));
        }

        @Test
        @DisplayName("points take a point or a whole number, else not-related")
        void points() {
            refusesEverythingBut(new Points(), A_POINT, "not-related",
                    List.of(A_POINT, A_WHOLE_NUMBER));
        }

        @Test
        @DisplayName("tuples take a tuple or a whole number, else not-related")
        void tuples() {
            refusesEverythingBut(new Tuples(), A_TUPLE, "not-related",
                    List.of(A_TUPLE, A_WHOLE_NUMBER));
        }

        @Test
        @DisplayName("octets take a binary, else invalid-arg")
        void octets() {
            refusesEverythingBut(new Octets(), SOME_OCTETS, "invalid-arg",
                    List.of(SOME_OCTETS));
        }

        @Test
        @DisplayName("bitsets take a bitset or a binary, else not-related")
        void bitsets() {
            refusesEverythingBut(new Bitsets(), A_BITSET, "not-related",
                    List.of(A_BITSET, SOME_OCTETS));
        }

        @Test
        @DisplayName("typesets take a typeset or a datatype, else invalid-arg")
        void typesets() {
            refusesEverythingBut(new Typesets(), A_TYPESET, "invalid-arg",
                    List.of(A_TYPESET, A_DATATYPE));
        }

        @Test
        @DisplayName("datatypes take nothing at all, another datatype included")
        void datatypes() {
            refusesEverythingBut(new Datatypes(), A_DATATYPE, "cannot-use", List.of());
        }

        @Test
        @DisplayName("vectors take a vector or a whole number, else cannot-use")
        void vectors() {
            refusesEverythingBut(new Vectors(), A_VECTOR, "cannot-use",
                    List.of(A_VECTOR, A_WHOLE_NUMBER));
        }

        @Test
        @DisplayName("no kind ever fails on a cast rather than naming a failure")
        void nothingFailsOnACast() {
            for (BitType kind : everyKind()) {
                for (Value left : ALL_TEN) {
                    for (Value right : ALL_TEN) {
                        if (!kind.shouldHandle(left, right)) {
                            continue;
                        }
                        answeredOrNamedAFailure(kind, left, right);
                    }
                }
            }
        }
    }
}
