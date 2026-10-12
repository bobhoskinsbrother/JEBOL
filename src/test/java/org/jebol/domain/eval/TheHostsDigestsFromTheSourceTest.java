package org.jebol.domain.eval;

import org.jebol.domain.host.DigestPort;
import org.jebol.domain.value.Raised;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class TheHostsDigestsFromTheSourceTest {

    private static byte[] bytesOf(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static DigestPort aHostWithOnly(String... methods) {
        Set<String> able = Set.of(methods);
        return new DigestPort() {

            @Override
            public Set<String> methods() {
                return able;
            }

            @Override
            public RunningDigest start(String method) {
                throw new DigestPort.Refused("feature-na", method);
            }

            @Override
            public byte[] digestOf(String method, byte[] octets) {
                throw new DigestPort.Refused("feature-na", method);
            }

            @Override
            public byte[] keyedDigestOf(String method, byte[] octets, byte[] key) {
                throw new DigestPort.Refused("feature-na", method);
            }
        };
    }

    @Test
    @DisplayName("the JVM computes Rebol's whole digest catalogue")
    void theJvmComputesTheWholeCatalogue() {
        Encodings encodings = new Encodings();

        assertThat(encodings.checksumMethods())
                .contains("md5", "sha1", "sha256", "ripemd160", "xxh3", "crc32");
    }

    @Test
    @DisplayName("a host with less has less, and its own Java digests always")
    void aHostWithLessHasLess() {
        Encodings encodings = new Encodings(aHostWithOnly("sha256"));

        assertThat(encodings.canDigest("sha256")).isTrue();
        assertThat(encodings.canDigest("sha1")).isFalse();
        assertThat(encodings.canDigest("md5")).isFalse();
        assertThat(encodings.canDigest("ripemd160"))
                .as("JEBOL's own digests are Java and are here whatever the host has")
                .isTrue();
        assertThat(encodings.checksumMethods())
                .contains("sha256", "ripemd160", "crc32")
                .doesNotContain("sha1", "md5", "sha3-256");
    }

    @Test
    @DisplayName("a digest the host has not got is refused by name")
    void aDigestTheHostHasNotGotIsRefused() {
        Encodings encodings = new Encodings(aHostWithOnly("sha256"));

        Raised refused = catchThrowableOfType(Raised.class,
                () -> encodings.digestOf(bytesOf("abc"), "sha1"));

        assertThat(refused.error().errorId()).isEqualTo("feature-na");
    }
}
