package org.jebol.domain.value;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Only {@code none} and a false {@code logic} are false. Everything else is
 * true, including zero, the empty string and the empty block.
 *
 * <p>This is the single most surprising thing about REBOL for anyone arriving
 * from another language, and every conditional native leans on it, so it is
 * tested exhaustively rather than by sampling.
 */
class ConditionalTruthTest {

    @Nested
    @DisplayName("the two values that are false")
    class FalseValues {

        @Test
        void noneIsFalse() {
            assertThat(NoneValue.none().isTruthy()).isFalse();
        }

        @Test
        void logicFalseIsFalse() {
            assertThat(LogicValue.no().isTruthy()).isFalse();
        }

        @Test
        @DisplayName("and nothing else is")
        void nothingElseIsFalse() {
            List<Value> everythingElse = List.of(
                    LogicValue.yes(),
                    IntegerValue.of(0),
                    IntegerValue.of(-1),
                    DecimalValue.of(0.0),
                    StringValue.of(""),
                    BlockValue.block(),
                    CharacterValue.of(0),
                    MoneyValue.of(BigDecimal.ZERO),
                    PairValue.of(0, 0),
                    TupleValue.of(0, 0, 0),
                    TimeValue.ofNanoseconds(0),
                    WordValue.of("anything"),
                    BinaryValue.of(),
                    DatatypeValue.of(Datatype.INTEGER),
                    ErrorValue.script("some-error", "a message"));

            assertThat(everythingElse)
                    .allSatisfy(value -> assertThat(value.isTruthy())
                            .as("%s (%s) must be true", value, value.datatype())
                            .isTrue());
        }
    }

    @Nested
    @DisplayName("the ones people expect to be false and are not")
    class SurprisinglyTrue {

        @Test
        @DisplayName("zero is a value, so zero is true")
        void zeroIsTrue() {
            assertThat(IntegerValue.of(0).isTruthy()).isTrue();
        }

        @Test
        void zeroDecimalIsTrue() {
            assertThat(DecimalValue.of(0.0).isTruthy()).isTrue();
            assertThat(DecimalValue.of(-0.0).isTruthy()).isTrue();
        }

        @Test
        void emptyStringIsTrue() {
            assertThat(StringValue.of("").isTruthy()).isTrue();
        }

        @Test
        void emptyBlockIsTrue() {
            assertThat(BlockValue.block().isTruthy()).isTrue();
        }

        @Test
        @DisplayName("a block holding false is true, because the block is the value")
        void blockHoldingFalseIsTrue() {
            assertThat(BlockValue.block(LogicValue.no()).isTruthy()).isTrue();
        }

        @Test
        @DisplayName("an error value is true; raising it is a separate matter")
        void errorValueIsTrue() {
            assertThat(ErrorValue.script("no-value", "x has no value").isTruthy()).isTrue();
        }

        @Test
        @DisplayName("a held Java null is true, and is not none")
        void heldHostNullIsTrue() {
            JavaObjectValue hostNull = JavaObjectValue.hostNull("java.lang.String");
            assertThat(hostNull.isTruthy()).isTrue();
            assertThat(hostNull.datatype()).isNotEqualTo(Datatype.NONE);
        }
    }

    @Nested
    @DisplayName("unset is a condition, and it is true")
    class UnsetIsConditional {

        @Test
        @DisplayName("an unset is true, as IS_FALSE in the C says")
        void unsetIsTrue() {
            assertThat(UnsetValue.unset().isTruthy()).isTrue();
        }

        @Test
        @DisplayName("only none and a false logic are false")
        void onlyTwoValuesAreFalse() {
            assertThat(NoneValue.none().isTruthy()).isFalse();
            assertThat(LogicValue.no().isTruthy()).isFalse();
            assertThat(LogicValue.yes().isTruthy()).isTrue();
            assertThat(IntegerValue.of(0).isTruthy())
                    .as("zero is true, unlike in most languages")
                    .isTrue();
            assertThat(StringValue.of("").isTruthy()).isTrue();
            assertThat(BlockValue.block().isTruthy()).isTrue();
        }

        @Test
        @DisplayName("unset is not none, and the difference is the point")
        void unsetIsDistinctFromNone() {
            assertThat(UnsetValue.unset().datatype()).isEqualTo(Datatype.UNSET);
            assertThat(NoneValue.none().datatype()).isEqualTo(Datatype.NONE);
            assertThat((Value) UnsetValue.unset()).isNotEqualTo(NoneValue.none());
        }
    }

    @Nested
    @DisplayName("singletons")
    class Singletons {

        @Test
        void allNoneValuesAreEqual() {
            assertThat(NoneValue.none()).isEqualTo(new NoneValue());
        }

        @Test
        void allUnsetValuesAreEqual() {
            assertThat(UnsetValue.unset()).isEqualTo(new UnsetValue());
        }

        @Test
        void logicValuesAreEqualByTruth() {
            assertThat(LogicValue.of(true)).isEqualTo(LogicValue.yes());
            assertThat(LogicValue.of(false)).isEqualTo(LogicValue.no());
            assertThat(LogicValue.yes()).isNotEqualTo(LogicValue.no());
        }
    }
}
