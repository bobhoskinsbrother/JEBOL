package org.jebol.domain.value;

/**
 * A coordinate pair, written {@code 40x40}.
 *
 * <p>184 of these appear across the fourteen demo programs in
 * {@code corpus/sources}, because View code is built from sizes and offsets.
 */
public record PairValue(long x, long y) implements Value {

    public static PairValue of(long x, long y) {
        return new PairValue(x, y);
    }

    @Override
    public Datatype datatype() {
        return Datatype.PAIR;
    }

    @Override
    public String toString() {
        return x + "x" + y;
    }
}
