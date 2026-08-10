package org.jebol.domain.value;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * An amount of money, with an optional three-character currency designator.
 *
 * <p>R3-Alpha's {@code money!} carries 26 significant digits and is
 * deliberately not normalised, so {@code $1.50} keeps its trailing zero.
 * {@link BigDecimal} preserves scale, which is what not normalising means,
 * and {@link #ARITHMETIC} gives the same width for operations that need one.
 *
 * <p>Note that {@code BigDecimal.equals} is scale-sensitive while
 * {@code compareTo} is not. Which of those REBOL's {@code =} and {@code ==}
 * mean is an open question in {@code spec/values.allium}; until it is
 * settled, this type exposes both rather than choosing.
 */
public record MoneyValue(BigDecimal amount, Optional<String> currency) implements Value {

    /** 26 significant digits, matching R3-Alpha's width. */
    public static final MathContext ARITHMETIC = new MathContext(26, RoundingMode.HALF_EVEN);

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

    /** The largest significand eighty-seven bits will hold: ten to the 26th. */
    private static final BigInteger SIGNIFICAND_LIMIT = BigInteger.TEN.pow(26);

    /** The power of ten fits in a signed byte, so it runs from -128 to 127. */
    private static final int SMALLEST_EXPONENT = -128;
    private static final int LARGEST_EXPONENT = 127;

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
     * Whether this amount is one a {@code deci} can hold.
     *
     * <p>Two ways to leave the range and both raise overflow: more than
     * twenty-six significant digits, or a power of ten outside a signed
     * byte. An implementation on an unbounded decimal passes every assertion
     * about amounts inside the bound and quietly answers a number Rebol
     * refuses, which is why this is asked rather than assumed.
     */
    public boolean isWithinTheDeciRange() {
        return significand().compareTo(SIGNIFICAND_LIMIT) < 0
                && exponent() >= SMALLEST_EXPONENT
                && exponent() <= LARGEST_EXPONENT;
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
        // toByteArray is signed and so may carry a leading zero byte, and it
        // is only as long as the number needs. Both are why the copy is
        // right-aligned into the low eleven bytes rather than being used as
        // it stands.
        int wanted = Math.min(significandBytes.length, BINARY_WIDTH - 1);
        System.arraycopy(significandBytes, significandBytes.length - wanted,
                twelve, BINARY_WIDTH - wanted, wanted);

        twelve[1] = (byte) ((twelve[1] & 0x7F) | ((exponent & 0x01) << 7));
        twelve[0] = (byte) (((amount.signum() < 0 ? 1 : 0) << 7)
                | ((exponent >> 1) & 0x7F));
        return twelve;
    }

    /**
     * {@code $1.50} equals {@code $1.5}, under the loose and the strict
     * operator alike.
     *
     * <p>Confirmed against a real R3, which answers true to both while still
     * molding {@code $1.50} with its trailing zero. The scale is kept for
     * printing and ignored for comparing, so {@code BigDecimal.compareTo} is
     * what both operators mean and {@code BigDecimal.equals} is what neither
     * means.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof MoneyValue money
                && currency.equals(money.currency)
                && amount.compareTo(money.amount) == 0;
    }

    @Override
    public int hashCode() {
        return currency.hashCode() * 31 + amount.stripTrailingZeros().hashCode();
    }

    @Override
    public Datatype datatype() {
        return Datatype.MONEY;
    }

    @Override
    public String toString() {
        return currency.map(designator -> designator + amount.toPlainString())
                .orElseGet(() -> "$" + amount.toPlainString());
    }
}
