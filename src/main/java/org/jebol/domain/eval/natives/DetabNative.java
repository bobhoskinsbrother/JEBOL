package org.jebol.domain.eval.natives;

import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Raised;

public class DetabNative extends TabbingNative {

    @Override
    public String nativeName() {
        return "detab";
    }

    @Override
    byte[] tab(byte[] bytes, int stop) {
        refuseAnEstimatePastTwoGigabytes(bytes, stop);
        byte[] detabbed = allocate(measureDetabbed(bytes, stop));
        int written = 0;
        int column = 0;
        for (byte each : bytes) {
            if (each == TAB) {
                do {
                    detabbed[written++] = SPACE;
                    column++;
                } while (column % stop != 0);
                continue;
            }
            column = each == LINE_FEED ? 0 : column + 1;
            detabbed[written++] = each;
        }
        return detabbed;
    }

    private static void refuseAnEstimatePastTwoGigabytes(byte[] bytes, int stop) {
        long tabs = 0;
        for (byte each : bytes) {
            if (each == TAB) {
                tabs++;
            }
        }
        if (bytes.length + tabs * (stop - 1) >= TWO_GIGABYTES) {
            throw Raised.of(EvaluationFailure.PAST_END);
        }
    }

    private static final long TWO_GIGABYTES = 1L << 31;

    private static int measureDetabbed(byte[] bytes, int stop) {
        long length = 0;
        long column = 0;
        for (byte each : bytes) {
            if (each == TAB) {
                long padded = (column / stop + 1) * stop - column;
                length += padded;
                column += padded;
            } else {
                length++;
                column = each == LINE_FEED ? 0 : column + 1;
            }
        }
        return (int) length;
    }

    private static byte[] allocate(int length) {
        try {
            return new byte[length];
        } catch (OutOfMemoryError nothingLeftToGive) {
            throw Raised.of(EvaluationFailure.NO_MEMORY);
        }
    }
}
