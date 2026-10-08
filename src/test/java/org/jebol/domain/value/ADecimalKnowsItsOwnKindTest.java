package org.jebol.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ADecimalKnowsItsOwnKindTest {

    @Test
    @DisplayName("a decimal answers decimal! and a percent answers percent!")
    void eachAnswersToItsOwnDatatype() {
        assertThat(DecimalValue.of(1.5).datatype()).isSameAs(DecimalValue.TYPE);
        assertThat(PercentValue.of(0.5).datatype()).isSameAs(PercentValue.TYPE);
    }

    @Test
    @DisplayName("the same quantity in the two kinds is two different values")
    void differentKindsDiffer() {
        assertThat(PercentValue.of(0.5)).isNotEqualTo(DecimalValue.of(0.5));
    }

    @Test
    @DisplayName("the same quantity in one kind is equal and hashes alike")
    void theSameKindIsEqual() {
        assertThat(PercentValue.of(0.5)).isEqualTo(PercentValue.of(0.5));
        assertThat(PercentValue.of(0.5)).hasSameHashCodeAs(PercentValue.of(0.5));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-2.5, -0.0, 0.0, 1.0})
    @DisplayName("negating and taking the absolute keep the kind")
    void signChangesKeepTheKind(double quantity) {
        assertThat(PercentValue.of(quantity).negated()).isInstanceOf(PercentValue.class);
        assertThat(PercentValue.of(quantity).absolute()).isInstanceOf(PercentValue.class);
        assertThat(DecimalValue.of(quantity).negated()).isInstanceOf(DecimalValue.class);
        assertThat(DecimalValue.of(quantity).absolute()).isInstanceOf(DecimalValue.class);
    }

    @Test
    @DisplayName("two percents added stay a percent")
    void twoPercentsAddedStayAPercent() {
        assertThat(PercentValue.of(0.25).combinedWithANumber(PercentValue.of(0.5), new Add()))
                .isEqualTo(PercentValue.of(0.75));
    }

    @Test
    @DisplayName("a percent added to a decimal is a decimal")
    void aPercentAndADecimalMakeADecimal() {
        assertThat(PercentValue.of(0.25).combinedWithANumber(DecimalValue.of(0.5), new Add()))
                .isInstanceOf(DecimalValue.class);
    }

    @Test
    @DisplayName("two percents divided are a plain decimal")
    void twoPercentsDividedAreADecimal() {
        assertThat(PercentValue.of(0.5).combinedWithANumber(PercentValue.of(0.25), new Divide()))
                .isInstanceOf(DecimalValue.class);
    }

    @Test
    @DisplayName("a decimal repeats as many times as its whole part")
    void aDecimalRepeatsByItsWholePart() {
        assertThat(DecimalValue.of(3.9).asCountOfRepetitions()).isEqualTo(3);
    }
}
