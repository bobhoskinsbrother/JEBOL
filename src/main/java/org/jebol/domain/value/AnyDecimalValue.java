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

    abstract AnyDecimalValue sameKindHolding(double another);

    public double quantity() {
        return quantity;
    }

    @Override
    public Value randomised(RandomDraw draw) {
        return sameKindHolding(draw.fraction() * quantity);
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
    public Value absolute() {
        return quantity == 0.0 ? this : sameKindHolding(Math.abs(quantity));
    }

    @Override
    public Value negated() {
        return sameKindHolding(-quantity);
    }

    @Override
    public Value heldBetween(Value lowest, Value highest) {
        return sameKindHolding(Math.max(((AnyDecimalValue) lowest).quantity,
                Math.min(((AnyDecimalValue) highest).quantity, quantity)));
    }

    @Override
    public Value combinedWithANumber(Value right, ArithmeticOperation operation) {
        return operation.onFractions(quantity, Numbers.quantityOfANumber(right), true);
    }

    @Override
    public boolean mayLoseATime(ArithmeticOperation operation) {
        return false;
    }

    @Override
    public boolean mayMeetADate() {
        return false;
    }

    @Override
    public Optional<AnyDecimalValue> asDecimalNumber() {
        return Optional.of(this);
    }

    @Override
    public Optional<MoneyValue> asMoneyInTheCurrencyOf(MoneyValue other) {
        return Optional.of(new MoneyValue(new Deci(quantity)));
    }

    @Override
    public Optional<Value[]> broughtTogetherWith(Value other) {
        return switch (other) {
            case MoneyValue theirs -> both(asMoneyInTheCurrencyOf(theirs).orElseThrow(), theirs);
            case AnyDecimalValue ignored -> both(this, other);
            case TimeValue theirs -> both(this, theirs.asSeconds());
            default -> Optional.empty();
        };
    }

    @Override
    public Optional<Value> asDecimal(AnyDecimalDatatype wanted, Conversion asking) {
        return Optional.of(asItStands(wanted, quantity));
    }

    @Override
    public boolean isAQuantityOfNothing() {
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

        public abstract AnyDecimalValue holding(double quantity);

        public AnyDecimalValue holdingHundredths(double quantity) {
            return holding(quantity);
        }

        abstract boolean isWrittenWithAPercentSign();

        @Override
        protected Value built(Conversion asking, Value from, Maker maker) {
            return from.asDecimal(this, asking).orElseGet(() -> scannedIntoADecimal(from));
        }

        private Value scannedIntoADecimal(Value from) {
            OptionalDouble scanned = theQuantityScannedFrom(from);
            if (scanned.isEmpty()) {
                throw refusing(from);
            }
            return from.inHundredths(this, scanned.getAsDouble());
        }

        private OptionalDouble theQuantityScannedFrom(Value from) {
            if (from instanceof StringValue text) {
                return decimalScannedFrom(new WrittenText(text.text())
                        .theOneNumberIn("a number", MOST_FRACTION_CHARACTERS));
            }
            if (from instanceof AnyBlockValue parts) {
                return OptionalDouble.of(mantissaTimesTenTo(parts));
            }
            return OptionalDouble.empty();
        }

        private double mantissaTimesTenTo(AnyBlockValue parts) {
            List<Value> both = parts.remaining();
            if (both.size() != 2) {
                throw refusing(parts);
            }
            double scaled = numberInTheBlock(both.get(0));
            double exponent = numberInTheBlock(both.get(1));
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

        private double numberInTheBlock(Value part) {
            return switch (part) {
                case IntegerValue(long magnitude) -> magnitude;
                case AnyDecimalValue number -> number.quantity();
                default -> throw refusing(part);
            };
        }

        private OptionalDouble decimalScannedFrom(String written) {
            String body = written;
            if (body.endsWith("%")) {
                if (!isWrittenWithAPercentSign()) {
                    return OptionalDouble.empty();
                }
                body = body.substring(0, body.length() - 1);
            }
            OptionalDouble endless = endlessNumberIn(body.replace("'", ""));
            if (endless.isPresent()) {
                return endless;
            }
            return numberRewrittenForTheJvm(body)
                    .map(plain -> OptionalDouble.of(Double.parseDouble(plain)))
                    .orElseGet(OptionalDouble::empty);
        }

        private Optional<String> numberRewrittenForTheJvm(String written) {
            String body = written.replace("'", "").replaceFirst(",", ".");
            return WRITTEN_DECIMAL.matcher(body).matches()
                    ? Optional.of(EMPTY_EXPONENT.matcher(body).replaceFirst(""))
                    : Optional.empty();
        }

        private OptionalDouble endlessNumberIn(String body) {
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
