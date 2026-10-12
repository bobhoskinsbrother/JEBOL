package org.jebol.domain.eval;

import org.jebol.domain.host.DigestPort;
import org.jebol.domain.host.DigestPorts;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Raised;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

final class SecureDrawing extends RandomDrawing {

    private static final int BYTES_HASHED = 20;
    private static final double TWO_TO_THE_SIXTY_FOURTH = 0x1p64;
    private static final double ONE_OVER_TWO_TO_THE_SIXTY_FOURTH = 0x1p-64;
    private static final long THE_LARGEST_UNSIGNED = -1L;

    private final RebolRandom generator;

    SecureDrawing(RebolRandom generator) {
        this.generator = generator;
    }

    @Override
    public long next() {
        long drawn = generator.next();
        ByteBuffer hashed = ByteBuffer.allocate(BYTES_HASHED).order(ByteOrder.LITTLE_ENDIAN);
        hashed.putLong(drawn);
        byte lowestByte = (byte) drawn;
        while (hashed.hasRemaining()) {
            hashed.put(lowestByte);
        }
        byte[] digest = sha1Of(hashed.array());
        return ByteBuffer.wrap(Arrays.copyOf(digest, Long.BYTES))
                .order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    private static byte[] sha1Of(byte[] octets) {
        try {
            return DigestPorts.theOneForThisBuild().digestOf("sha1", octets);
        } catch (DigestPort.Refused refused) {
            throw Raised.of(EvaluationFailure.FEATURE_NA, "sha1");
        }
    }

    @Override
    public long upTo(long limit) {
        if (limit == 0) {
            return 0;
        }
        long span = limit < 0 ? -limit : limit;
        long lastExactMultiple = THE_LARGEST_UNSIGNED
                - Long.remainderUnsigned(THE_LARGEST_UNSIGNED - span + 1, span);
        long drawn;
        do {
            drawn = next();
        } while (Long.compareUnsigned(drawn, lastExactMultiple) > 0);
        long picked = Long.remainderUnsigned(drawn, span) + 1;
        return limit > 0 ? picked : -picked;
    }

    @Override
    public double fraction() {
        double drawn = next();
        return (drawn < 0 ? drawn + TWO_TO_THE_SIXTY_FOURTH : drawn)
                * ONE_OVER_TWO_TO_THE_SIXTY_FOURTH;
    }
}
