package org.jebol.domain.value;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.ToLongFunction;

public record IntegerValue(long magnitude) implements Value, RebolNumber {

    @Override
    public Value randomised(RandomDraw draw) {
        return IntegerValue.of(draw.upTo(magnitude));
    }

    @Override
    public long asCountOfRepetitions() {
        return magnitude;
    }

    @Override
    public long asRandomSeed(ToLongFunction<byte[]> checksumOfTheOctets) {
        return magnitude;
    }

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
    public Optional<AnyDecimalValue> asDecimalNumber() {
        return Optional.of(DecimalValue.of(magnitude));
    }

    @Override
    public Optional<MoneyValue> asMoneyInTheCurrencyOf(MoneyValue other) {
        return Optional.of(new MoneyValue(new Deci(magnitude)));
    }

    @Override
    public Optional<Value[]> broughtTogetherWith(Value other) {
        return switch (other) {
            case AnyDecimalValue ignored -> theyMeetAsDecimals(other);
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
    public Optional<Value> asDecimal(AnyDecimalValue.AnyDecimalDatatype wanted, Conversion asking) {
        return Optional.of(asItStands(wanted, magnitude));
    }

    @Override
    public boolean isAQuantityOfNothing() {
        return magnitude == 0;
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    public static final Datatype TYPE = new IntegerDatatype();

    private static final class IntegerDatatype extends Datatype {

        private static final int MOST_HEX_DIGITS = 16;

        private static final int MOST_WHOLE_NUMBER_CHARACTERS = 25;

        private static final double TOO_LARGE_FOR_A_WHOLE_NUMBER = 9.223372036854776E18;

        IntegerDatatype() {
            super("integer", Typeset.NUMBER, Typeset.SCALAR);
        }

        @Override
        public Value constructedFrom(List<Value> contents, Construction construction) {
            throw refusingConstruction(contents);
        }

        @Override
        protected Value built(Conversion asking, Value from, Maker maker) {
            return switch (from) {
                case IntegerValue whole -> whole;
                case LogicValue truth -> {
                    if (!asking.builds()) {
                        throw refusing(from);
                    }
                    yield IntegerValue.of(truth.truth() ? 1 : 0);
                }
                case IssueValue issue -> hexNumberIn(issue);
                case AnyStringValue text -> wholeNumberReadFrom(text);
                case CharacterValue character -> IntegerValue.of(character.codepoint());
                case BinaryValue bytes -> IntegerValue.of(bytes.bitsOfTheLastEightOctets());
                case DateValue moment -> IntegerValue.of(moment.wholeSecondsSinceTheEpoch());
                case AnyDecimalValue number -> wholeNumberWithinRange(number.quantity());
                case MoneyValue amount -> IntegerValue.of(amount.asDeci().toLong());
                case TimeValue clock ->
                        IntegerValue.of(clock.nanoseconds() / TimeValue.NANOSECONDS_PER_SECOND);
                default -> throw refusing(from);
            };
        }

        private Value wholeNumberWithinRange(double quantity) {
            if (Double.isNaN(quantity)
                    || quantity < -TOO_LARGE_FOR_A_WHOLE_NUMBER
                    || quantity >= TOO_LARGE_FOR_A_WHOLE_NUMBER) {
                throw Raised.of(EvaluationFailure.OVERFLOW,
                        "no whole number is what " + quantity + " names");
            }
            return IntegerValue.of((long) quantity);
        }

        private Value hexNumberIn(AnyWordValue issue) {
            String digits = issue.spelling();
            if (digits.isEmpty() || digits.length() > MOST_HEX_DIGITS) {
                throw refusing(issue);
            }
            try {
                return IntegerValue.of(Long.parseUnsignedLong(digits, 16));
            } catch (NumberFormatException notHexAtAll) {
                throw refusing(issue);
            }
        }

        private Value wholeNumberReadFrom(AnyStringValue text) {
            String withoutSeparators = new WrittenText(text.text())
                    .theOneNumberIn("an integer", MOST_WHOLE_NUMBER_CHARACTERS)
                    .replace("'", "");
            try {
                return IntegerValue.of(Long.parseLong(withoutSeparators));
            } catch (NumberFormatException notAWholeNumber) {
                return truncatedDecimal(withoutSeparators, text);
            }
        }

        private Value truncatedDecimal(String candidate, AnyStringValue original) {
            if (candidate.indexOf('.') < 0) {
                throw refusing(original);
            }
            double asNumber;
            try {
                asNumber = Double.parseDouble(candidate);
            } catch (NumberFormatException notANumberEither) {
                throw refusing(original);
            }
            if (!(Math.abs(asNumber) < TOO_LARGE_FOR_A_WHOLE_NUMBER)) {
                throw refusing(original);
            }
            return IntegerValue.of((long) asNumber);
        }
    }

    @Override
    public String toString() {
        return Long.toString(magnitude);
    }
}
