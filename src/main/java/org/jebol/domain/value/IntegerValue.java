package org.jebol.domain.value;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;

public record IntegerValue(long magnitude) implements Value, RebolNumber {

    private static final int ALL_SIXTEEN_HEX_DIGITS = 16;

    @Override
    public String writtenInHex(HexWidth width) {
        return width.sixteenDigitsKeptToTheRight(magnitude, ALL_SIXTEEN_HEX_DIGITS);
    }

    @Override
    public long asPosition() {
        return magnitude;
    }

    @Override
    public byte[] asOctets() {
        return ByteBuffer.allocate(Long.BYTES).putLong(magnitude).array();
    }

    public byte[] asFewOctetsAsHoldIt() {
        byte[] whole = asOctets();
        if (magnitude < 0) {
            return whole;
        }
        int from = 0;
        while (from < Long.BYTES - 1 && whole[from] == 0) {
            from++;
        }
        return Arrays.copyOfRange(whole, from, Long.BYTES);
    }

    @Override
    public Value absolute() {
        if (magnitude == Long.MIN_VALUE) {
            throw Raised.of(EvaluationFailure.OVERFLOW,
                    "there is no positive counterpart to " + magnitude);
        }
        return IntegerValue.of(Math.abs(magnitude));
    }

    @Override
    public Value heldBetween(Value lowest, Value highest) {
        return IntegerValue.of(Math.max(((IntegerValue) lowest).magnitude,
                Math.min(((IntegerValue) highest).magnitude, magnitude)));
    }

    @Override
    public Value combinedWithANumber(Value right, ArithmeticOperation operation) {
        return switch (right) {
            case IntegerValue(long theirs) -> operation.onWholeNumbers(magnitude, theirs);
            case CharacterValue(int codepoint) ->
                    operation.onWholeNumbers(magnitude, codepoint);
            default -> operation.onFractions(
                    magnitude, Numbers.quantityOfANumber(right), true);
        };
    }

    @Override
    public boolean mayLoseATime(ArithmeticOperation operation) {
        return operation.subtractsOneFromTheOther();
    }

    @Override
    public boolean mayMeetADate() {
        return true;
    }

    @Override
    public Optional<IntegerValue> asWholeNumber() {
        return Optional.of(this);
    }

    @Override
    public Optional<DecimalValue> asDecimalNumber() {
        return Optional.of(DecimalValue.of(magnitude));
    }

    @Override
    public Optional<MoneyValue> asMoneyInTheCurrencyOf(MoneyValue other) {
        return Optional.of(new MoneyValue(new Deci(magnitude)));
    }

    @Override
    public Optional<Value[]> broughtTogetherWith(Value other) {
        return switch (other) {
            case DecimalValue ignored -> theyMeetAsDecimals(other);
            case TimeValue ignored -> theyMeetAsDecimals(other);
            case MoneyValue theirs -> both(asMoneyInTheCurrencyOf(theirs).orElseThrow(), theirs);
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
