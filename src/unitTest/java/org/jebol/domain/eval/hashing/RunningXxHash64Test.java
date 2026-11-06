package org.jebol.domain.eval.hashing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class RunningXxHash64Test {

    private static byte[] noise(int length) {
        byte[] octets = new byte[length];
        new Random(length).nextBytes(octets);
        return octets;
    }

    private static long oneShot(byte[] message) {
        return ByteBuffer.wrap(new XxHash().of64MostSignificantByteFirst(message)).getLong();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 4, 7, 8, 15, 16, 31, 32, 33, 63, 64, 65, 100, 1000})
    @DisplayName("Fed whole, the running digest is the one-shot XXH64 at either side of every stripe and tail size")
    void fedWhole(int length) {
        byte[] message = noise(length);
        RunningXxHash64 running = new RunningXxHash64();
        running.update(message, 0, length);
        assertThat(running.digest()).isEqualTo(oneShot(message));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 5, 31, 32, 33, 64})
    @DisplayName("Fed in chunks of any size, the running digest is the one-shot XXH64")
    void fedInChunks(int chunk) {
        byte[] message = noise(257);
        RunningXxHash64 running = new RunningXxHash64();
        for (int at = 0; at < message.length; at += chunk) {
            running.update(message, at, Math.min(chunk, message.length - at));
        }
        assertThat(running.digest()).isEqualTo(oneShot(message));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 40})
    @DisplayName("After a reset the running digest starts again from nothing")
    void reset(int length) {
        byte[] message = noise(length);
        RunningXxHash64 running = new RunningXxHash64();
        running.update(noise(77), 0, 77);
        running.reset();
        running.update(message, 0, length);
        assertThat(running.digest()).isEqualTo(oneShot(message));
    }
}
