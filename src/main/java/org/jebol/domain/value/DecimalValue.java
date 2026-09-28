package org.jebol.domain.value;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * A double, covering both {@code decimal!} and {@code percent!}.
 *
 * <p>The two share a representation and differ only in how they are printed,
 * which is why the datatype is carried rather than inferred.
 */
public record DecimalValue(double quantity, Datatype datatype) implements Value {

    @Override
    public Optional<DecimalValue> asDecimalNumber() {
        return Optional.of(this);
    }

    @Override
    public Optional<MoneyValue> asMoneyBeside(MoneyValue meeting) {
        return Optional.of(meeting.amounting(BigDecimal.valueOf(quantity)));
    }

    @Override
    public Optional<Value[]> meeting(Value other) {
        return switch (other) {
            case MoneyValue theirs -> both(asMoneyBeside(theirs).orElseThrow(), theirs);
            case DecimalValue ignored -> both(this, other);
            case TimeValue theirs -> both(this, theirs.asSeconds());
            default -> Optional.empty();
        };
    }


    public DecimalValue {
        if (datatype != Datatype.DECIMAL && datatype != Datatype.PERCENT) {
            throw new IllegalArgumentException(
                    "a decimal value is decimal! or percent!, not " + datatype.literalSpelling());
        }
    }

    @Override
    public java.util.Optional<Value> asDecimal(Datatype wanted, Conversion asking) {
        return java.util.Optional.of(asItStands(wanted, quantity));
    }

    public static DecimalValue of(double quantity) {
        return new DecimalValue(quantity, Datatype.DECIMAL);
    }

    public static DecimalValue percent(double quantity) {
        return new DecimalValue(quantity, Datatype.PERCENT);
    }

    @Override
    public String toString() {
        return Double.toString(quantity);
    }
}
