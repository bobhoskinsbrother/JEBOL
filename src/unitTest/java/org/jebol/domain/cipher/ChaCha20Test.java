package org.jebol.domain.cipher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.crypto.Cipher;
import javax.crypto.spec.ChaCha20ParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ChaCha20Test {

    private static byte[] filled(int length, long seed) {
        byte[] octets = new byte[length];
        new Random(seed).nextBytes(octets);
        return octets;
    }

    private static byte[] jdk(byte[] key, byte[] nonce, int counter, byte[] octets) throws Exception {
        Cipher cipher = Cipher.getInstance("ChaCha20");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "ChaCha20"), new ChaCha20ParameterSpec(nonce, counter));
        return cipher.doFinal(octets);
    }

    static Stream<Arguments> everyBoundary() {
        return Stream.of(0, 1, 7, 2, 0x7fffffff, -2).flatMap(counter -> IntStream.of(
                0, 1, 31, 63, 64, 65, 127, 128, 129, 1000).mapToObj(length -> Arguments.of(counter, length)));
    }

    @ParameterizedTest(name = "counter {0}, {1} bytes")
    @MethodSource("everyBoundary")
    @DisplayName("masks as the JDK's ChaCha20 does, either side of every 64-byte block")
    void masksAsTheJdkDoes(int counter, int length) throws Exception {
        byte[] key = filled(32, 1);
        byte[] nonce = filled(12, 2);
        byte[] octets = filled(length, length);

        assertThat(new ChaCha20(key, nonce, counter).mask(octets)).isEqualTo(jdk(key, nonce, counter, octets));
    }

    @Test
    @DisplayName("fed in pieces of every size, it carries on the stream where the last piece stopped")
    void carriesOnAcrossPieces() throws Exception {
        byte[] key = filled(32, 3);
        byte[] nonce = filled(12, 4);
        byte[] octets = filled(300, 5);
        byte[] whole = jdk(key, nonce, 1, octets);
        for (int piece = 1; piece <= 70; piece++) {
            ChaCha20 running = new ChaCha20(key, nonce, 1);
            byte[] masked = new byte[octets.length];
            for (int at = 0; at < octets.length; at += piece) {
                int length = Math.min(piece, octets.length - at);
                byte[] part = new byte[length];
                System.arraycopy(octets, at, part, 0, length);
                System.arraycopy(running.mask(part), 0, masked, at, length);
            }
            assertThat(masked).as("pieces of %d", piece).isEqualTo(whole);
        }
    }

    @Test
    @DisplayName("RFC 7539's own example, section 2.4.2, comes out as the RFC prints it")
    void answersRfc7539() {
        byte[] key = HexFormat.of().parseHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
        byte[] nonce = HexFormat.of().parseHex("000000000000004a00000000");
        byte[] text = ("Ladies and Gentlemen of the class of '99: If I could offer you only one tip for the future, "
                + "sunscreen would be it.").getBytes(StandardCharsets.US_ASCII);

        byte[] masked = new ChaCha20(key, nonce, 1).mask(text);

        assertThat(HexFormat.of().formatHex(masked)).startsWith(
                "6e2e359a2568f98041ba0728dd0d6981e97e7aec1d4360c20a27afccfd9fae0b"
                        + "f91b65c5524733ab8f593dabcd62b3571639d624e65152ab8f530c359f0861d8");
        assertThat(masked).hasSize(text.length);
    }
}
