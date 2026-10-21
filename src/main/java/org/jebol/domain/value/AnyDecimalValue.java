package org.jebol.domain.value;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.ToLongFunction;
import java.util.regex.Pattern;

public abstract sealed class AnyDecimalValue implements Value, RebolNumber
        permits DecimalValue, PercentValue {

    private final double quantity;

    AnyDecimalValue(double quantity) {
        this.quantity = quantity;
    }

    @Override
    public abstract Datatype datatype();

    abstract AnyDecimalValue makeSameKindHolding(double another);

    public double quantity() {
        return quantity;
    }

    @Override
    public Value randomise(RandomDraw draw) {
        return makeSameKindHolding(draw.fraction() * quantity);
    }

    @Override
    public long asRandomSeed(ToLongFunction<byte[]> checksumOfTheOctets) {
        return Double.doubleToRawLongBits(quantity);
    }

    @Override
    public long asPosition() {
        return (long) quantity;
    }

    @Override
    public Value makeAbsolute() {
        return quantity == 0.0 ? this : makeSameKindHolding(Math.abs(quantity));
    }

    @Override
    public Value negated() {
        return makeSameKindHolding(-quantity);
    }

    @Override
    public Value holdBetween(Value lowest, Value highest) {
        return makeSameKindHolding(Math.max(((AnyDecimalValue) lowest).quantity,
                Math.min(((AnyDecimalValue) highest).quantity, quantity)));
    }

    @Override
    public Value combineWithNumber(Value right, ArithmeticOperation operation) {
        return operation.onFractions(quantity, Numbers.measureTheQuantityOfANumber(right), true);
    }

    @Override
    public boolean canDeductTime(ArithmeticOperation operation) {
        return false;
    }

    @Override
    public boolean canBeUsedToCalculateDate() {
        return false;
    }

    @Override
    public Optional<AnyDecimalValue> asDecimalNumber() {
        return Optional.of(this);
    }

    @Override
    public Optional<MoneyValue> asMoneyUsingCurrency(MoneyValue other) {
        return Optional.of(new MoneyValue(new Deci(quantity)));
    }

    @Override
    public Optional<Value[]> combineWith(Value other) {
        return switch (other) {
            case MoneyValue theirs -> pairUp(asMoneyUsingCurrency(theirs).orElseThrow(), theirs);
            case AnyDecimalValue ignored -> pairUp(this, other);
            case TimeValue theirs -> pairUp(this, theirs.asSeconds());
            default -> Optional.empty();
        };
    }

    @Override
    public Optional<Value> asDecimal(AnyDecimalDatatype wanted, Conversion asking) {
        return Optional.of(asItStands(wanted, quantity));
    }

    @Override
    public boolean isZero() {
        return quantity == 0.0;
    }

    public abstract static class AnyDecimalDatatype extends NumberDatatype {

        private static final int MOST_FRACTION_CHARACTERS = 24;

        private static final Pattern WRITTEN_DECIMAL = Pattern.compile(
                "[+-]?(?:[0-9]+(?:[.][0-9]*)?|[.][0-9]+)(?:[eE][+-]?[0-9]*)?");

        private static final Pattern EMPTY_EXPONENT = Pattern.compile("[eE][+-]?$");

        AnyDecimalDatatype(String spelling) {
            super(spelling);
        }

        public abstract AnyDecimalValue wrap(double quantity);

        public AnyDecimalValue wrapHundredths(double quantity) {
            return wrap(quantity);
        }

        abstract boolean isWrittenWithAPercentSign();

        @Override
        protected Value build(Conversion asking, Value from, Maker maker) {
            return from.asDecimal(this, asking).orElseGet(() -> scanIntoDecimal(from));
        }

        private Value scanIntoDecimal(Value from) {
            OptionalDouble scanned = scanQuantityFrom(from);
            if (scanned.isEmpty()) {
                throw refuse(from);
            }
            return from.countInHundredths(this, scanned.getAsDouble());
        }

        private OptionalDouble scanQuantityFrom(Value from) {
            if (from instanceof StringValue text) {
                return scanDecimalFrom(new WrittenText(text.text())
                        .findOneNumberIn(MOST_FRACTION_CHARACTERS));
            }
            if (from instanceof AnyBlockValue parts) {
                return OptionalDouble.of(multiplyTheMantissaByTenTo(parts));
            }
            return OptionalDouble.empty();
        }

        private double multiplyTheMantissaByTenTo(AnyBlockValue parts) {
            List<Value> both = parts.remaining();
            if (both.size() != 2) {
                throw refuse(parts);
            }
            double scaled = readNumberInBlock(both.get(0));
            double exponent = readNumberInBlock(both.get(1));
            while (exponent >= 1) {
                exponent--;
                scaled *= 10.0;
            }
            while (exponent <= -1) {
                exponent++;
                scaled /= 10.0;
            }
            return scaled;
        }

        private double readNumberInBlock(Value part) {
            return switch (part) {
                case IntegerValue(long magnitude) -> magnitude;
                case DecimalValue number -> number.quantity();
                default -> throw Raised.of(EvaluationFailure.BAD_MAKE_ARG,
                        DecimalValue.TYPE, part);
            };
        }

        private OptionalDouble scanDecimalFrom(String written) {
            String body = written;
            if (body.endsWith("%")) {
                if (!isWrittenWithAPercentSign()) {
                    return OptionalDouble.empty();
                }
                body = body.substring(0, body.length() - 1);
            }
            OptionalDouble endless = readEndlessNumberIn(body.replace("'", ""));
            if (endless.isPresent()) {
                return endless;
            }
            return rewriteTheNumberForTheJvm(body)
                    .map(plain -> OptionalDouble.of(Double.parseDouble(plain)))
                    .orElseGet(OptionalDouble::empty);
        }

        private Optional<String> rewriteTheNumberForTheJvm(String written) {
            String body = written.replace("'", "").replaceFirst(",", ".");
            return WRITTEN_DECIMAL.matcher(body).matches()
                    ? Optional.of(EMPTY_EXPONENT.matcher(body).replaceFirst(""))
                    : Optional.empty();
        }

        private OptionalDouble readEndlessNumberIn(String body) {
            int hash = body.indexOf('#');
            if (hash < 0) {
                return OptionalDouble.empty();
            }
            boolean negative = body.charAt(0) == '-';
            String afterTheHash = body.substring(hash + 1);
            if (afterTheHash.equalsIgnoreCase("INF")) {
                return OptionalDouble.of(negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
            }
            return afterTheHash.equalsIgnoreCase("NAN")
                    ? OptionalDouble.of(Double.NaN)
                    : OptionalDouble.empty();
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AnyDecimalValue number
                && number.datatype() == datatype()
                && Double.compare(number.quantity, quantity) == 0;
    }

    @Override
    public int hashCode() {
        return datatype().hashCode() * 31 + Double.hashCode(quantity);
    }

    @Override
    public String toString() {
        return Double.toString(quantity);
    }
}
