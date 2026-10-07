package org.jebol.domain.eval;

import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Raised;

public final class RebolRandom extends RandomDrawing {

    private static final int LONG_LAG = 100;
    private static final int SHORT_LAG = 37;
    private static final long MODULUS = 1L << 62;
    private static final int QUALITY = 1009;
    private static final int STREAM_SEPARATION = 70;

    private static final long WHEN_NOBODY_CHOSE = 314159L;

    private final long[] state = new long[LONG_LAG];
    private final long[] drawn = new long[QUALITY];
    private int cursor = -1;
    private boolean everSeeded;

    private long modDiff(long left, long right) {
        return (left - right) & (MODULUS - 1);
    }

    @Override
    public long next() {
        if (cursor >= 1 && cursor < LONG_LAG) {
            return drawn[cursor++];
        }
        return refill();
    }

    private long refill() {
        if (!everSeeded) {
            seed(WHEN_NOBODY_CHOSE);
        }
        fill(drawn, QUALITY);
        cursor = 1;
        return drawn[0];
    }

    public void seed(long chosen) {
        long[] preparing = new long[LONG_LAG + LONG_LAG - 1];
        long spread = (chosen + 2) & (MODULUS - 2);
        for (int at = 0; at < LONG_LAG; at++) {
            preparing[at] = spread;
            spread <<= 1;
            if (spread >= MODULUS) {
                spread -= MODULUS - 2;
            }
        }
        preparing[1]++;
        long remaining = chosen & (MODULUS - 1);
        for (int rounds = STREAM_SEPARATION - 1; rounds > 0;) {
            for (int at = LONG_LAG - 1; at > 0; at--) {
                preparing[at + at] = preparing[at];
                preparing[at + at - 1] = 0;
            }
            for (int at = LONG_LAG + LONG_LAG - 2; at >= LONG_LAG; at--) {
                preparing[at - (LONG_LAG - SHORT_LAG)] =
                        modDiff(preparing[at - (LONG_LAG - SHORT_LAG)], preparing[at]);
                preparing[at - LONG_LAG] =
                        modDiff(preparing[at - LONG_LAG], preparing[at]);
            }
            if ((remaining & 1) == 1) {
                for (int at = LONG_LAG; at > 0; at--) {
                    preparing[at] = preparing[at - 1];
                }
                preparing[0] = preparing[LONG_LAG];
                preparing[SHORT_LAG] =
                        modDiff(preparing[SHORT_LAG], preparing[LONG_LAG]);
            }
            if (remaining != 0) {
                remaining >>= 1;
            } else {
                rounds--;
            }
        }
        System.arraycopy(preparing, 0, state, 63, SHORT_LAG);
        System.arraycopy(preparing, SHORT_LAG, state, 0, LONG_LAG - 37);
        for (int round = 0; round < 10; round++) {
            fill(preparing, LONG_LAG + LONG_LAG - 1);
        }
        everSeeded = true;
        cursor = -1;
    }

    private void fill(long[] into, int howMany) {
        int at = 0;
        for (; at < LONG_LAG; at++) {
            into[at] = state[at];
        }
        for (; at < howMany; at++) {
            into[at] = modDiff(into[at - LONG_LAG], into[at - SHORT_LAG]);
        }
        int back = 0;
        for (; back < SHORT_LAG; back++, at++) {
            state[back] = modDiff(into[at - LONG_LAG], into[at - SHORT_LAG]);
        }
        for (; back < LONG_LAG; back++, at++) {
            state[back] = modDiff(into[at - LONG_LAG], state[back - SHORT_LAG]);
        }
    }

    @Override
    public long upTo(long limit) {
        if (limit == 0) {
            return 0;
        }
        long span = Math.abs(limit);
        if (Long.compareUnsigned(span, MODULUS) > 0) {
            throw Raised.of(EvaluationFailure.OVERFLOW);
        }
        long lastExactMultiple = MODULUS - Long.remainderUnsigned(MODULUS, span) - 1;
        long drawn;
        do {
            drawn = next();
        } while (Long.compareUnsigned(drawn, lastExactMultiple) > 0);
        long picked = 1 + Long.remainderUnsigned(drawn, span);
        return limit < 0 ? -picked : picked;
    }

    @Override
    public double fraction() {
        return (double) next() / (double) MODULUS;
    }

    public RandomDrawing secured() {
        return new SecureDrawing(this);
    }
}
