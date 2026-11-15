package org.jebol.domain.host;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class Sha3Test {

    private record Variant(int outputBytes, String jdkName, int blockBytes) {
    }

    private static final Variant[] VARIANTS = {
            new Variant(28, "SHA3-224", 144),
            new Variant(32, "SHA3-256", 136),
            new Variant(48, "SHA3-384", 104),
            new Variant(64, "SHA3-512", 72),
    };

    private static byte[] filled(int length) {
        byte[] octets = new byte[length];
        new Random(length * 31L + 7).nextBytes(octets);
        return octets;
    }

    private static byte[] jdk(Variant variant, byte[] octets) throws Exception {
        return MessageDigest.getInstance(variant.jdkName()).digest(octets);
    }

    private static byte[] ours(Variant variant, byte[] octets) {
        Sha3 running = new Sha3(variant.outputBytes());
        running.add(octets, 0, octets.length);
        return running.digest();
    }

    static Stream<Arguments> everyBoundary() {
        return Stream.of(VARIANTS).flatMap(variant -> IntStream.of(
                        0, 1, 7, 8, 9, 15, 16, 17,
                        variant.blockBytes() - 9, variant.blockBytes() - 8, variant.blockBytes() - 1,
                        variant.blockBytes(), variant.blockBytes() + 1, variant.blockBytes() + 8,
                        2 * variant.blockBytes() - 1, 2 * variant.blockBytes(), 2 * variant.blockBytes() + 1,
                        1000, 65536)
                .mapToObj(length -> Arguments.of(variant, length)));
    }

    @ParameterizedTest(name = "{0}, {1} bytes")
    @MethodSource("everyBoundary")
    @DisplayName("each variant digests as the JDK's does, either side of every block and lane boundary")
    void digestsAsTheJdkDoes(Variant variant, int length) throws Exception {
        byte[] octets = filled(length);

        assertThat(ours(variant, octets)).isEqualTo(jdk(variant, octets));
    }

    @Test
    @DisplayName("SHA3-256 of nothing and of abc are FIPS 202's own answers")
    void answersFips202() {
        byte[] abc = "abc".getBytes(StandardCharsets.US_ASCII);

        assertThat(HexFormat.of().formatHex(ours(VARIANTS[1], new byte[0])))
                .isEqualTo("a7ffc6f8bf1ed76651c14756a061d662f580ff4de43b49fa82d80a4b80f8434a");
        assertThat(HexFormat.of().formatHex(ours(VARIANTS[1], abc)))
                .isEqualTo("3a985da74fe225b2045c172d6bd390bd855f086e3e9d525b46bfe24511431532");
    }

    @Test
    @DisplayName("fed in two pieces split anywhere, the digest is the one of the whole")
    void splitsAnywhere() throws Exception {
        for (Variant variant : VARIANTS) {
            byte[] octets = filled(3 * variant.blockBytes() + 5);
            byte[] whole = jdk(variant, octets);
            for (int split = 0; split <= octets.length; split++) {
                Sha3 running = new Sha3(variant.outputBytes());
                running.add(octets, 0, split);
                running.add(octets, split, octets.length - split);
                assertThat(running.digest()).as("%s split at %d", variant.jdkName(), split).isEqualTo(whole);
            }
        }
    }

    @Test
    @DisplayName("a copy reads the digest so far and leaves the running one free to go on")
    void copiesSoFar() throws Exception {
        Variant variant = VARIANTS[2];
        byte[] first = filled(200);
        byte[] second = filled(77);
        Sha3 running = new Sha3(variant.outputBytes());
        running.add(first, 0, first.length);

        byte[] soFar = running.copy().digest();
        running.add(second, 0, second.length);

        assertThat(soFar).isEqualTo(jdk(variant, first));
        byte[] both = new byte[first.length + second.length];
        System.arraycopy(first, 0, both, 0, first.length);
        System.arraycopy(second, 0, both, first.length, second.length);
        assertThat(running.digest()).isEqualTo(jdk(variant, both));
    }

    static Stream<Arguments> everyKeyLength() {
        return Stream.of(VARIANTS).flatMap(variant -> IntStream.of(
                        1, 20, variant.blockBytes() - 1, variant.blockBytes(), variant.blockBytes() + 1, 300)
                .mapToObj(length -> Arguments.of(variant, length)));
    }

    @ParameterizedTest(name = "{0}, a key of {1} bytes")
    @MethodSource("everyKeyLength")
    @DisplayName("HMAC over each variant answers as the JDK's, with keys shorter than, equal to and longer than its block")
    void keysAsTheJdkDoes(Variant variant, int keyLength) throws Exception {
        byte[] key = filled(keyLength + 1000);
        byte[] octets = filled(333);
        Mac mac = Mac.getInstance("Hmac" + variant.jdkName());
        mac.init(new SecretKeySpec(key, 0, keyLength, mac.getAlgorithm()));
        byte[] keyUsed = new byte[keyLength];
        System.arraycopy(key, 0, keyUsed, 0, keyLength);

        assertThat(Sha3.hmac(variant.outputBytes(), keyUsed, octets)).isEqualTo(mac.doFinal(octets));
    }
}
