package org.jebol.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class WhichValuesConvertToACommonDatatypeTest {

    private static final Value A_WHOLE_NUMBER = IntegerValue.of(1);
    private static final Value A_DECIMAL = DecimalValue.of(1.0);
    private static final Value A_PERCENT = PercentValue.of(1.0);
    private static final Value SOME_MONEY = new MoneyValue(BigDecimal.ONE, Optional.empty());
    private static final Value A_CHARACTER = CharacterValue.of(1);
    private static final Value A_TIME = new TimeValue(1_000_000_000L);

    private static final List<Value> THE_SIX = List.of(
            A_WHOLE_NUMBER, A_DECIMAL, A_PERCENT, SOME_MONEY, A_CHARACTER, A_TIME);

    private static boolean shareADatatype(Value one, Value other) {
        return one.broughtTogetherWith(other).isPresent()
                || other.broughtTogetherWith(one).isPresent();
    }

    private static List<Datatype> theDatatypeTheyBothConvertTo(Value one, Value other) {
        Value[] pair = one.broughtTogetherWith(other).orElseGet(() -> {
            Value[] backwards = other.broughtTogetherWith(one).orElseThrow();
            return new Value[] {backwards[1], backwards[0]};
        });
        return List.of(pair[0].datatype(), pair[1].datatype());
    }

    static Stream<Arguments> everyPairing() {
        return THE_SIX.stream().flatMap(one ->
                THE_SIX.stream().map(other -> Arguments.of(one, other)));
    }

    @Nested
    @DisplayName("whichever side is asked, the answer is the same")
    class TheAnswerDoesNotDependOnWhichSideIsAsked {

        @ParameterizedTest(name = "{0} and {1}")
        @MethodSource(
                "org.jebol.domain.value.WhichValuesConvertToACommonDatatypeTest#everyPairing")
        @DisplayName("two values either convert to a common datatype both ways or neither")
        void bothWaysOrNeither(Value one, Value other) {
            assertThat(shareADatatype(one, other))
                    .as("%s with %s", one.datatype(), other.datatype())
                    .isEqualTo(shareADatatype(other, one));
        }

        @ParameterizedTest(name = "{0} and {1}")
        @MethodSource(
                "org.jebol.domain.value.WhichValuesConvertToACommonDatatypeTest#everyPairing")
        @DisplayName("and they land on the same datatype whichever side is asked first")
        void theSameDatatypeEitherWayRound(Value one, Value other) {
            if (!shareADatatype(one, other)) {
                return;
            }
            assertThat(theDatatypeTheyBothConvertTo(one, other))
                    .as("%s with %s", one.datatype(), other.datatype())
                    .isEqualTo(theDatatypeTheyBothConvertTo(other, one).reversed());
        }
    }

    @Nested
    @DisplayName("which datatypes convert to a common one, and which deliberately do not")
    class WhichOnesConvert {

        @Test
        @DisplayName("a character converts to meet a whole number and nothing else")
        void aCharacterOnlyMeetsAWholeNumber() {
            assertThat(shareADatatype(A_CHARACTER, A_WHOLE_NUMBER)).isTrue();
            assertThat(shareADatatype(A_CHARACTER, A_DECIMAL)).isFalse();
            assertThat(shareADatatype(A_CHARACTER, A_PERCENT)).isFalse();
            assertThat(shareADatatype(A_CHARACTER, SOME_MONEY)).isFalse();
            assertThat(shareADatatype(A_CHARACTER, A_TIME)).isFalse();
        }

        @Test
        @DisplayName("money and a time share nothing, though both meet the plain numbers")
        void moneyAndATimeShareNothing() {
            assertThat(shareADatatype(SOME_MONEY, A_TIME)).isFalse();
            assertThat(shareADatatype(SOME_MONEY, A_WHOLE_NUMBER)).isTrue();
            assertThat(shareADatatype(SOME_MONEY, A_DECIMAL)).isTrue();
            assertThat(shareADatatype(A_TIME, A_WHOLE_NUMBER)).isTrue();
            assertThat(shareADatatype(A_TIME, A_DECIMAL)).isTrue();
        }

        @Test
        @DisplayName("a whole number beside money becomes money, not the other way about")
        void theNarrowerSideWidens() {
            assertThat(theDatatypeTheyBothConvertTo(A_WHOLE_NUMBER, SOME_MONEY))
                    .containsExactly(Datatype.MONEY, Datatype.MONEY);
        }

        @Test
        @DisplayName("a time beside a whole number becomes decimal seconds")
        void aTimeWidensToSeconds() {
            Value[] pair = A_WHOLE_NUMBER.broughtTogetherWith(A_TIME).orElseThrow();
            assertThat(pair[0].datatype()).isEqualTo(Datatype.DECIMAL);
            assertThat(pair[1].datatype()).isEqualTo(Datatype.DECIMAL);
            assertThat(((AnyDecimalValue) pair[1]).quantity()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("a conversion a value cannot make answers nothing")
    class AConversionThatCannotBeMade {

        @Test
        @DisplayName("a character has a whole number but no decimal, which is what keeps it apart")
        void aCharacterHasNoDecimal() {
            assertThat(A_CHARACTER.asWholeNumber()).isPresent();
            assertThat(A_CHARACTER.asDecimalNumber()).isEmpty();
        }

        @Test
        @DisplayName("a time has a decimal but no money, which is what keeps it apart")
        void aTimeHasNoMoney() {
            assertThat(A_TIME.asDecimalNumber()).isPresent();
            assertThat(A_TIME.asMoneyInTheCurrencyOf((MoneyValue) SOME_MONEY)).isEmpty();
        }

        @Test
        @DisplayName("a datatype that is not a number converts to nothing at all")
        void somethingThatIsNotANumber() {
            Value text = StringValue.of("ab");
            assertThat(text.asWholeNumber()).isEmpty();
            assertThat(text.asDecimalNumber()).isEmpty();
            assertThat(text.asMoneyInTheCurrencyOf((MoneyValue) SOME_MONEY)).isEmpty();
        }
    }
}
