package org.jebol.domain.eval.compression.lzav;

import org.jebol.domain.eval.compression.SeriesRoom;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Raised;

public final class Lzav {

    private static final int UNREPORTED = 0;

    private Lzav() {
    }

    public static byte[] compress(byte[] octets) {
        if (octets.length == 0) {
            throw refusal(UNREPORTED);
        }
        return LzavEncoder.encode(octets);
    }

    public static byte[] decompress(byte[] octets, int wanted) {
        if (wanted <= 0) {
            throw refusal(UNREPORTED);
        }
        SeriesRoom.makeBinaryOf(wanted);
        LzavDecoder.Outcome outcome = LzavDecoder.decode(octets, wanted);
        if (outcome.code() < 0) {
            throw refusal(outcome.code());
        }
        return outcome.octets();
    }

    private static Raised refusal(int code) {
        return Raised.of(EvaluationFailure.BAD_PRESS, IntegerValue.of(code));
    }
}
