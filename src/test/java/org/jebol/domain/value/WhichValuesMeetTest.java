package org.jebol.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class WhichValuesMeetTest {

    private static final Value A_WHOLE_NUMBER = IntegerValue.of(1);
    private static final Value A_DECIMAL = DecimalValue.of(1.0);
    private static final Value A_PERCENT = DecimalValue.percent(1.0);
    private static final Value SOME_MONEY = new MoneyValue(BigDecimal.ONE, Optional.empty());
    private static final Value A_CHARACTER = CharacterValue.of(1);
    private static final Value A_TIME = new TimeValue(1_000_000_000L);

    private static final List<Value> THE_SIX = List.of(
            A_WHOLE_NUMBER, A_DECIMAL, A_PERCENT, SOME_MONEY, A_CHARACTER, A_TIME);

    private static boolean theyMeet(Value one, Value other) {
        return one.meeting(other).isPresent() || other.meeting(one).isPresent();
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> everyPairing() {
        return THE_SIX.stream().flatMap(one ->
                THE_SIX.stream().map(other ->
                        org.junit.jupiter.params.provider.Arguments.of(one, other)));
    }

    @Nested
    @DisplayName("meeting is symmetric, because the caller tries the other side")
    class MeetingIsSymmetric {

        @ParameterizedTest(name = "{0} and {1}")
        @MethodSource("org.jebol.domain.value.WhichValuesMeetTest#everyPairing")
        @DisplayName("if these two meet one way round they meet the other")
        void theyMeetBothWaysOrNeither(Value one, Value other) {
            assertThat(theyMeet(one, other))
                    .as("%s with %s", one.datatype(), other.datatype())
                    .isEqualTo(theyMeet(other, one));
        }

        @ParameterizedTest(name = "{0} and {1}")
        @MethodSource("org.jebol.domain.value.WhichValuesMeetTest#everyPairing")
        @DisplayName("and they arrive at the same datatype whichever side asks")
        void theyArriveAtTheSameDatatype(Value one, Value other) {
            if (!theyMeet(one, other)) {
                return;
            }
            assertThat(datatypesWhenBroughtTogether(one, other))
                    .as("%s with %s", one.datatype(), other.datatype())
                    .isEqualTo(datatypesWhenBroughtTogether(other, one).reversed());
        }

        private static List<Datatype> datatypesWhenBroughtTogether(Value one, Value other) {
            Value[] pair = one.meeting(other)
                    .orElseGet(() -> {
                        Value[] backwards = other.meeting(one).orElseThrow();
                        return new Value[] {backwards[1], backwards[0]};
                    });
            return List.of(pair[0].datatype(), pair[1].datatype());
        }
    }

    @Nested
    @DisplayName("which datatypes meet, and which deliberately do not")
    class WhichOnesMeet {

        @Test
        @DisplayName("a character meets a whole number and nothing else")
        void aCharacterMeetsOnlyAWholeNumber() {
            assertThat(theyMeet(A_CHARACTER, A_WHOLE_NUMBER)).isTrue();
            assertThat(theyMeet(A_CHARACTER, A_DECIMAL)).isFalse();
            assertThat(theyMeet(A_CHARACTER, A_PERCENT)).isFalse();
            assertThat(theyMeet(A_CHARACTER, SOME_MONEY)).isFalse();
            assertThat(theyMeet(A_CHARACTER, A_TIME)).isFalse();
        }

        @Test
        @DisplayName("money and a time never meet, though both meet the plain numbers")
        void moneyAndTimeNeverMeet() {
            assertThat(theyMeet(SOME_MONEY, A_TIME)).isFalse();
            assertThat(theyMeet(SOME_MONEY, A_WHOLE_NUMBER)).isTrue();
            assertThat(theyMeet(SOME_MONEY, A_DECIMAL)).isTrue();
            assertThat(theyMeet(A_TIME, A_WHOLE_NUMBER)).isTrue();
            assertThat(theyMeet(A_TIME, A_DECIMAL)).isTrue();
        }

        @Test
        @DisplayName("a whole number beside money becomes money, not the other way about")
        void theNarrowerSideWidens() {
            Value[] pair = A_WHOLE_NUMBER.meeting(SOME_MONEY).orElseThrow();
            assertThat(pair[0].datatype()).isEqualTo(Datatype.MONEY);
            assertThat(pair[1].datatype()).isEqualTo(Datatype.MONEY);
        }

        @Test
        @DisplayName("a time beside a whole number becomes decimal seconds")
        void aTimeWidensToSeconds() {
            Value[] pair = A_WHOLE_NUMBER.meeting(A_TIME).orElseThrow();
            assertThat(pair[0].datatype()).isEqualTo(Datatype.DECIMAL);
            assertThat(pair[1].datatype()).isEqualTo(Datatype.DECIMAL);
            assertThat(((DecimalValue) pair[1]).quantity()).isEqualTo(1.0);
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
            assertThat(A_TIME.asMoneyBeside((MoneyValue) SOME_MONEY)).isEmpty();
        }

        @Test
        @DisplayName("a datatype that is not a number converts to nothing at all")
        void somethingThatIsNotANumber() {
            Value text = StringValue.of("ab");
            assertThat(text.asWholeNumber()).isEmpty();
            assertThat(text.asDecimalNumber()).isEmpty();
            assertThat(text.asMoneyBeside((MoneyValue) SOME_MONEY)).isEmpty();
        }
    }
}
