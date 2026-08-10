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
 * <p>Single precision decimals, which is the part that took reading the C
 * to find. {@code reb-c.h} declares {@code REBD32} as a C {@code float},
 * and {@code sys-value.h} stores a pair's halves in two of them. So a half
 * carries about seven significant digits and no more, and three things
 * follow that nothing about {@code 40x40} suggests:
 *
 * <ul>
 *   <li>A large whole number loses its low digits. {@code 2147483647} and
 *       {@code 2147483648} are one pair half, which is why Rebol's own
 *       suite asserts that {@code 2147483647x2147483647 / 2} equals
 *       {@code 1073741823x1073741823}.</li>
 *   <li>A half above about 3.4e38 becomes infinite rather than staying
 *       large, so {@code as-pair 1e300 -1e300} molds as
 *       {@code 1.#INFx-1.#INF}. A pair is the one datatype here that holds
 *       an infinity as a matter of course.</li>
 *   <li>A fraction is kept to single precision and read back at double, so
 *       {@code first 0.1x0.2} is 0.100000001490116. Molding hides it again,
 *       because a pair half molds to seven digits.</li>
 * </ul>
 *
 * <p>The narrowing happens on construction, once, so nothing downstream has
 * to remember it. Every half this record hands out is a double that a float
 * can hold exactly.
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
    private static final String AREA = "area";

    public PairValue {
        x = narrowedToSinglePrecision(x);
        y = narrowedToSinglePrecision(y);
    }

    /**
     * A half as a {@code REBD32} holds it: rounded to single precision, and
     * infinite where it was too large for one.
     *
     * <p>An earlier version of this refused an infinity on the grounds that
     * it would not mold back. It molds back perfectly well as
     * {@code 1.#INF}, and refusing it made {@code as-pair 1e300 -1e300}
     * fail where Rebol answers a pair.
     */
    private static double narrowedToSinglePrecision(double half) {
        return (float) half;
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

    /**
     * The half a path names, as in {@code p/x}, or the derived area.
     *
     * <p>{@code PD_Pair} answers three word spellings and not two. AREA is
     * {@code fabsf(x * y)}, so it drops the sign: the area of
     * {@code -10x20} is 200.0, the same as the area of {@code 10x20}. It is
     * derived rather than stored, which is why writing it is refused.
     */
    public Optional<Value> half(String name) {
        return switch (name) {
            case FIRST_HALF -> Optional.of(DecimalValue.of(x));
            case SECOND_HALF -> Optional.of(DecimalValue.of(y));
            case AREA -> Optional.of(DecimalValue.of(Math.abs(x * y)));
            default -> Optional.empty();
        };
    }

    /** Whether this name is a half that may be written, rather than the area. */
    public static boolean isWritableHalf(String name) {
        return name.equals(FIRST_HALF) || name.equals(SECOND_HALF);
    }

    /** This pair with one half replaced, as {@code p/x: 0} leaves it. */
    public PairValue withHalf(String name, double replacement) {
        return name.equals(FIRST_HALF)
                ? new PairValue(replacement, y)
                : new PairValue(x, replacement);
    }

    /** This pair with the half at a position replaced, as {@code p/1: 0} leaves it. */
    public PairValue withHalfAt(int position, double replacement) {
        return position == 1
                ? new PairValue(replacement, y)
                : new PairValue(x, replacement);
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
