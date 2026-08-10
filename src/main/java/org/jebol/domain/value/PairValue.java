package org.jebol.domain.value;

import java.util.Optional;

/**
 * A coordinate pair, written {@code 40x40}.
 *
 * <p>Both halves are decimals, which the spelling gives no hint of:
 * {@code 1x2} reads back as {@code 1x2}, and {@code first 1x2} is
 * {@code 1.0}. R3 was asked; see {@code corpus/pairs.corpus}. Holding
 * them as integers here would have been the obvious choice and would
 * have made {@code 1.5x2} unreadable and {@code 1x2 / 2} wrong.
 *
 * <p>Not a series. It has two halves rather than two items, which is why
 * {@code length?} refuses it and {@code to block!} wraps it rather than
 * splitting it.
 *
 * <p>184 of these appear across the fourteen demo programs in
 * {@code corpus/sources}, because View code is built from sizes and offsets.
 */
public record PairValue(double x, double y) implements Value {

    private static final String FIRST_HALF = "x";
    private static final String SECOND_HALF = "y";

    public PairValue {
        rejectUnrepresentable(x, FIRST_HALF);
        rejectUnrepresentable(y, SECOND_HALF);
    }

    /**
     * A half that is not a finite number would mold to something the
     * reader cannot read back, breaking the round trip every other value
     * keeps. Refusing on construction keeps that at the boundary.
     */
    private static void rejectUnrepresentable(double half, String which) {
        if (Double.isNaN(half) || Double.isInfinite(half)) {
            throw new IllegalArgumentException(
                    "the " + which + " half of a pair must be a finite number");
        }
    }

    public static PairValue of(double x, double y) {
        return new PairValue(x, y);
    }

    /** Both halves the same, which is what {@code to pair! 5} gives. */
    public static PairValue square(double half) {
        return new PairValue(half, half);
    }

    @Override
    public Datatype datatype() {
        return Datatype.PAIR;
    }

    /** The half a path names, as in {@code p/x}. */
    public Optional<Value> half(String name) {
        return switch (name) {
            case FIRST_HALF -> Optional.of(DecimalValue.of(x));
            case SECOND_HALF -> Optional.of(DecimalValue.of(y));
            default -> Optional.empty();
        };
    }

    /**
     * The half a position names, as in {@code p/1}. The two spellings ask
     * the same question, and only one of them is obvious from {@code 1x2}.
     */
    public Optional<Value> halfAt(int position) {
        return switch (position) {
            case 1 -> Optional.of(DecimalValue.of(x));
            case 2 -> Optional.of(DecimalValue.of(y));
            default -> Optional.empty();
        };
    }

    public PairValue reversed() {
        return new PairValue(y, x);
    }

    @Override
    public String toString() {
        return Molder.mold(this);
    }
}
