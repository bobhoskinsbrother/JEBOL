package org.jebol.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConditionalTruthTest {

    @Nested
    @DisplayName("the two values that are false")
    class FalseValues {

        @Test
        void includeNone() {
            assertThat(NoneValue.none().isTruthy()).isFalse();
        }

        @Test
        void logicFalseIsFalse() {
            assertThat(LogicValue.answerNo().isTruthy()).isFalse();
        }

        @Test
        @DisplayName("and nothing else is")
        void includeNothingElse() {
            List<Value> everythingElse = List.of(
                    LogicValue.yes(),
                    IntegerValue.of(0),
                    IntegerValue.of(-1),
                    DecimalValue.of(0.0),
                    StringValue.of(""),
                    BlockValue.of(),
                    CharacterValue.of(0),
                    MoneyValue.of(BigDecimal.ZERO),
                    PairValue.of(0, 0),
                    TupleValue.of(0, 0, 0),
                    TimeValue.ofNanoseconds(0),
                    WordValue.of("anything"),
                    BinaryValue.of(),
                    IntegerValue.TYPE,
                    ErrorValue.makeAScriptError("some-error", "a message"));

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
        void coversAnEmptyString() {
            assertThat(StringValue.of("").isTruthy()).isTrue();
        }

        @Test
        void coversAnEmptyBlock() {
            assertThat(BlockValue.of().isTruthy()).isTrue();
        }

        @Test
        @DisplayName("a block holding false is true, because the block is the value")
        void coversABlockHoldingFalse() {
            assertThat(BlockValue.of(LogicValue.answerNo()).isTruthy()).isTrue();
        }

        @Test
        @DisplayName("an error value is true; raising it is a separate matter")
        void coversAnErrorValue() {
            assertThat(ErrorValue.makeAScriptError("no-value", "x has no value").isTruthy()).isTrue();
        }

        @Test
        @DisplayName("a held Java null is true, and is not none")
        void coversAHeldHostNull() {
            JavaObjectValue hostNull = JavaObjectValue.holdHostNull("java.lang.String");
            assertThat(hostNull.isTruthy()).isTrue();
            assertThat(hostNull.datatype()).isNotEqualTo(NoneValue.TYPE);
        }
    }

    @Nested
    @DisplayName("unset is a condition, and it is true")
    class UnsetIsConditional {

        @Test
        @DisplayName("an unset is true, as IS_FALSE in the C says")
        void readsUnsetAsTrue() {
            assertThat(UnsetValue.unset().isTruthy()).isTrue();
        }

        @Test
        @DisplayName("only none and a false logic are false")
        void findsOnlyTwoFalseValues() {
            assertThat(NoneValue.none().isTruthy()).isFalse();
            assertThat(LogicValue.answerNo().isTruthy()).isFalse();
            assertThat(LogicValue.yes().isTruthy()).isTrue();
            assertThat(IntegerValue.of(0).isTruthy())
                    .as("zero is true, unlike in most languages")
                    .isTrue();
            assertThat(StringValue.of("").isTruthy()).isTrue();
            assertThat(BlockValue.of().isTruthy()).isTrue();
        }

        @Test
        @DisplayName("unset is not none, and the difference is the point")
        void keepsUnsetApartFromNone() {
            assertThat(UnsetValue.unset().datatype()).isEqualTo(UnsetValue.TYPE);
            assertThat(NoneValue.none().datatype()).isEqualTo(NoneValue.TYPE);
            assertThat((Value) UnsetValue.unset()).isNotEqualTo(NoneValue.none());
        }
    }

    @Nested
    @DisplayName("singletons")
    class Singletons {

        @Test
        void makeEveryNoneEqual() {
            assertThat(NoneValue.none()).isEqualTo(new NoneValue());
        }

        @Test
        void makeEveryUnsetEqual() {
            assertThat(UnsetValue.unset()).isEqualTo(new UnsetValue());
        }

        @Test
        void logicValuesAreEqualByTruth() {
            assertThat(LogicValue.of(true)).isEqualTo(LogicValue.yes());
            assertThat(LogicValue.of(false)).isEqualTo(LogicValue.answerNo());
            assertThat(LogicValue.yes()).isNotEqualTo(LogicValue.answerNo());
        }
    }
}
