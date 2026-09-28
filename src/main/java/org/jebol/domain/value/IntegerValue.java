package org.jebol.domain.value;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * A 64-bit signed integer, as R3-Alpha's {@code integer!} is.
 *
 * <p>Zero is a value and therefore true. That catches everyone once.
 */
public record IntegerValue(long magnitude) implements Value {

    @Override
    public Optional<IntegerValue> asWholeNumber() {
        return Optional.of(this);
    }

    @Override
    public Optional<DecimalValue> asDecimalNumber() {
        return Optional.of(DecimalValue.of(magnitude));
    }

    @Override
    public Optional<MoneyValue> asMoneyBeside(MoneyValue meeting) {
        return Optional.of(meeting.amounting(BigDecimal.valueOf(magnitude)));
    }

    @Override
    public Optional<Value[]> meeting(Value other) {
        return switch (other) {
            case DecimalValue ignored -> theyMeetAsDecimals(other);
            case TimeValue ignored -> theyMeetAsDecimals(other);
            case MoneyValue theirs -> both(asMoneyBeside(theirs).orElseThrow(), theirs);
            case CharacterValue letter ->
                    both(this, letter.asWholeNumber().orElseThrow());
            default -> Optional.empty();
        };
    }

    private Optional<Value[]> theyMeetAsDecimals(Value other) {
        return both(asDecimalNumber().orElseThrow(),
                other.asDecimalNumber().orElseThrow());
    }

    @Override
    public Value bitwise(Value right, BitwiseOperation operation) {
        return IntegerValue.of(
                operation.onWholeElements(magnitude, aWholeNumberFrom(right)));
    }

    private long aWholeNumberFrom(Value right) {
        return switch (right) {
            case IntegerValue(long other) -> other;
            case CharacterValue(int codepoint) -> codepoint;
            default -> throw Raised.notRelated(this, right);
        };
    }


    public static IntegerValue of(long magnitude) {
        return new IntegerValue(magnitude);
    }

    @Override
    public Optional<Value> asDecimal(Datatype wanted, Conversion asking) {
        return Optional.of(asItStands(wanted, magnitude));
    }

    @Override
    public Datatype datatype() {
        return Datatype.INTEGER;
    }

    @Override
    public String toString() {
        return Long.toString(magnitude);
    }
}
