package org.jebol.domain.eval.compression;

import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Raised;

import java.util.Arrays;

public final class ExpandingBinary {

    private static final long HIGH_BIT_REFUSED_AS_PAST_END = 0x80000000L;
    private static final long UNSIGNED_32 = 0xFFFFFFFFL;
    private static final int POWER_OF_TWO_BELOW = 512 * 1024;
    private static final long MOST_ROOM_GIVEN = 1L << 28;

    private byte[] octets;
    private long rest;
    private int tail;
    private boolean isRecentlyExpanded;

    public ExpandingBinary(long rest) {
        this.rest = rest;
        this.octets = new byte[(int) rest];
    }

    public byte[] octets() {
        return octets;
    }

    public long rest() {
        return rest;
    }

    public int tail() {
        return tail;
    }

    public void setTail(int tail) {
        this.tail = tail;
    }

    public long available() {
        return rest - tail;
    }

    public void expandAtTail(long delta) {
        if (delta == 0) {
            return;
        }
        if ((delta & HIGH_BIT_REFUSED_AS_PAST_END) != 0) {
            throw Raised.of(EvaluationFailure.PAST_END);
        }
        long size = tail + 1L;
        if (size + delta > rest) {
            long extra = isRecentlyExpanded ? tail + delta + 1 : 1;
            long newSize = tail + delta + extra;
            if (newSize > UNSIGNED_32) {
                throw Raised.of(EvaluationFailure.PAST_END);
            }
            rest = SeriesRoom.makeSeriesOf(newSize, newSize < POWER_OF_TWO_BELOW);
            if (rest > MOST_ROOM_GIVEN) {
                throw Raised.of(EvaluationFailure.NO_MEMORY);
            }
            octets = Arrays.copyOf(octets, (int) rest);
            isRecentlyExpanded = true;
        }
        tail += (int) delta;
    }
}
