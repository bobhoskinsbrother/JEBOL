package org.jebol.domain.value;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/**
 * An amount of money, with an optional three-character currency designator.
 *
 * <p>R3-Alpha's {@code money!} carries 26 significant digits and is
 * deliberately not normalised, so {@code $1.50} keeps its trailing zero.
 * {@link BigDecimal} preserves scale, which is what not normalising means,
 * and {@link Deci} does the arithmetic on it as Rebol's {@code f-deci.c} does.
 *
 * <p>Note that {@code BigDecimal.equals} is scale-sensitive while
 * {@code compareTo} is not. Which of those REBOL's {@code =} and {@code ==}
 * mean is an open question in {@code spec/values.allium}; until it is
 * settled, this type exposes both rather than choosing.
 */
public record MoneyValue(BigDecimal amount, Optional<String> currency, boolean negative)
        implements Value {

    public MoneyValue(BigDecimal amount, Optional<String> currency) {
        this(amount, currency, amount != null && amount.signum() < 0);
    }

    public MoneyValue(Deci amount) {
        this(new BigDecimal(amount.negative() ? amount.significand().negate() : amount.significand(),
                -amount.exponent()), Optional.empty(), amount.negative());
    }

    public Deci asDeci() {
        return new Deci(significand(), exponent(), negative);
    }

    public MoneyValue signed(boolean wanted) {
        return new MoneyValue(amount, currency, wanted);
    }

    @Override
    public Value absolute() {
        return MoneyValue.of(amount.abs()).signed(false);
    }


    @Override
    public Value arithmetic(Value right, ArithmeticOperation operation) {
        return new MoneyActions(this).combinedWith(right, operation);
    }

    @Override
    public Value negated() {
        return amounting(amount.negate()).signed(!negative);
    }

    @Override
    public Value heldBetween(Value lowest, Value highest) {
        return new MoneyActions(this).heldBetween(
                (MoneyValue) lowest, (MoneyValue) highest);
    }

    private static final int MAXIMUM_CURRENCY_LENGTH = 3;

    public MoneyValue {
        if (amount == null) {
            throw new IllegalArgumentException("money must have an amount");
        }
        if (currency == null) {
            throw new IllegalArgumentException("currency must be present or empty, never null");
        }
        currency.ifPresent(designator -> {
            if (designator.isEmpty() || designator.length() > MAXIMUM_CURRENCY_LENGTH) {
                throw new IllegalArgumentException(
                        "a currency designator is one to three characters, got \""
                                + designator + "\"");
            }
        });
    }

    public static MoneyValue of(BigDecimal amount) {
        return new MoneyValue(amount, Optional.empty());
    }

    public static MoneyValue of(BigDecimal amount, String currency) {
        return new MoneyValue(amount, Optional.of(currency));
    }

    /**
     * A different amount in the same currency.
     *
     * <p>The way to carry a currency across an operation without unwrapping
     * the Optional and putting a possible null back in. Rounding a money and
     * doing arithmetic on one both need it, and both had it wrong first: they
     * passed {@code currency().orElse(null)} to the two-argument factory,
     * which wraps the null and fails on every plain money.
     */
    public MoneyValue amounting(BigDecimal replacement) {
        return new MoneyValue(replacement, currency);
    }

    @Override
    public Optional<MoneyValue> asMoneyInTheCurrencyOf(MoneyValue other) {
        return Optional.of(this);
    }

    /** Digits after the decimal point, as written. Not normalised away. */
    public int scale() {
        return amount.scale();
    }

    /**
     * How many bytes the {@code deci} form takes: ninety-six bits.
     *
     * <p>One sign bit, an eight-bit signed power of ten, and eighty-seven
     * bits of whole-number significand. That is the whole of what a money
     * can hold, and both bounds below fall out of it.
     */
    public static final int BINARY_WIDTH = 12;

    private static final long THE_HIGHEST_WORD_OF_THE_LARGEST_SIGNIFICAND = 5421010L;

    private static final long THE_MIDDLE_WORD_OF_THE_LARGEST_SIGNIFICAND = 3704098002L;

    private static final long THE_LOWEST_WORD_OF_THE_LARGEST_SIGNIFICAND = 3825205247L;

    private static final long A_WORD = 0xFFFFFFFFL;


    /**
     * The whole number the digits spell, without the point.
     *
     * <p>{@code BigDecimal} calls this the unscaled value and Rebol spreads
     * it across three fields, which is the same number written two ways.
     */
    public BigInteger significand() {
        return amount.unscaledValue().abs();
    }

    /**
     * The power of ten the significand is multiplied by, which is the
     * negation of the scale. {@code $1.50} has scale 2 and exponent -2.
     */
    public int exponent() {
        return -amount.scale();
    }

    /**
     * A money read from its twelve byte form, padded from the left.
     *
     * <p>{@code Bin_To_Money} takes at most twelve bytes from the front of
     * the binary and then shifts them to the right-hand end of a twelve byte
     * buffer, zeroing the front. So {@code #{0F}} is fifteen and not fifteen
     * times a power of ten, and a longer binary loses its tail rather than
     * being refused.
     *
     * <p>The layout, from {@code binary_to_deci}: the top bit of the first
     * byte is the sign; the next eight bits, spanning the first two bytes,
     * are the signed power of ten; the remaining eighty-seven bits are the
     * significand.
     */
    public static MoneyValue fromBytes(byte[] given) {
        byte[] twelve = new byte[BINARY_WIDTH];
        int taken = Math.min(given.length, BINARY_WIDTH);
        System.arraycopy(given, 0, twelve, BINARY_WIDTH - taken, taken);

        boolean negative = (twelve[0] & 0x80) != 0;
        int exponent = (byte) (((twelve[0] & 0x7F) << 1) | ((twelve[1] & 0xFF) >>> 7));

        byte[] significandBytes = new byte[BINARY_WIDTH - 1];
        significandBytes[0] = (byte) (twelve[1] & 0x7F);
        System.arraycopy(twelve, 2, significandBytes, 1, BINARY_WIDTH - 2);
        BigInteger significand = new BigInteger(1, significandBytes);
        long highest = significand.shiftRight(64).longValue() & A_WORD;
        long middle = significand.shiftRight(32).longValue() & A_WORD;
        long lowest = significand.longValue() & A_WORD;
        boolean refusedByBinaryToDeci = highest >= THE_HIGHEST_WORD_OF_THE_LARGEST_SIGNIFICAND
                && (middle >= THE_MIDDLE_WORD_OF_THE_LARGEST_SIGNIFICAND
                        ? lowest > THE_LOWEST_WORD_OF_THE_LARGEST_SIGNIFICAND
                                || middle > THE_MIDDLE_WORD_OF_THE_LARGEST_SIGNIFICAND
                        : highest > THE_HIGHEST_WORD_OF_THE_LARGEST_SIGNIFICAND);
        if (refusedByBinaryToDeci) {
            throw Raised.of(EvaluationFailure.OVERFLOW);
        }

        BigDecimal amount = new BigDecimal(
                negative ? significand.negate() : significand, -exponent);
        return MoneyValue.of(amount);
    }

    /**
     * This money as its twelve byte form, so that reading it back gives the
     * same money and the same bytes.
     *
     * <p>{@code deci_to_binary}, which is {@code binary_to_deci} written
     * backwards. Nothing normalises on the way through, which is what makes
     * the round trip exact rather than merely equal.
     */
    public byte[] toBytes() {
        BigInteger significand = significand();
        int exponent = exponent();
        byte[] twelve = new byte[BINARY_WIDTH];

        byte[] significandBytes = significand.toByteArray();
        int wanted = Math.min(significandBytes.length, BINARY_WIDTH - 1);
        System.arraycopy(significandBytes, significandBytes.length - wanted,
                twelve, BINARY_WIDTH - wanted, wanted);

        twelve[1] = (byte) ((twelve[1] & 0x7F) | ((exponent & 0x01) << 7));
        twelve[0] = (byte) (((amount.signum() < 0 ? 1 : 0) << 7)
                | ((exponent >> 1) & 0x7F));
        return twelve;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MoneyValue theirs
                && currency.equals(theirs.currency)
                && amount.compareTo(theirs.amount) == 0;
    }

    @Override
    public int hashCode() {
        return currency.hashCode() * 31 + amount.stripTrailingZeros().hashCode();
    }

    @Override
    public Optional<Value> asDecimal(AnyDecimalValue.AnyDecimalDatatype wanted, Conversion asking) {
        return Optional.of(asItStands(wanted, asDeci().toDouble()));
    }

    @Override
    public boolean isAQuantityOfNothing() {
        return amount.signum() == 0;
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    public static final Datatype TYPE = new MoneyDatatype();

    private static final class MoneyDatatype extends Datatype {

        private static final int MOST_MONEY_CHARACTERS = 36;

        MoneyDatatype() {
            super("money", Typeset.SCALAR);
        }

        @Override
        public Value constructedFrom(List<Value> contents, Construction construction) {
            throw refusingConstruction(contents);
        }

        @Override
        protected Value built(Conversion asking, Value from, Maker maker) {
            return switch (from) {
                case MoneyValue already -> already;
                case IntegerValue whole -> new MoneyValue(new Deci(whole.magnitude()));
                case AnyDecimalValue quantity -> new MoneyValue(new Deci(quantity.quantity()));
                case AnyStringValue text -> readMoney(text);
                case BinaryValue bytes -> MoneyValue.fromBytes(bytes.bytesFromHere());
                case LogicValue truth when asking.builds() ->
                        new MoneyValue(new Deci(truth.truth() ? 1 : 0));
                default -> throw refusing(from);
            };
        }

        private MoneyValue readMoney(AnyStringValue text) {
            String written = new WrittenText(text.text())
                    .theOneNumberIn("a money", MOST_MONEY_CHARACTERS);
            return new DeciReading(written).theWholeOf()
                    .map(MoneyValue::new)
                    .orElseThrow(() -> refusing(text));
        }
    }

    @Override
    public String toString() {
        return currency.map(designator -> designator + amount.toPlainString())
                .orElseGet(() -> "$" + amount.toPlainString());
    }
}
