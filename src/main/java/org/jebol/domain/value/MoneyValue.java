package org.jebol.domain.value;

import java.math.BigDecimal;
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

    /** Digits after the decimal point, as written. Not normalised away. */
    public int scale() {
        return amount.scale();
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
