package org.jebol.domain.eval.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.KeyAgreement;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.XECPublicKey;
import java.security.spec.NamedParameterSpec;
import java.security.spec.XECPrivateKeySpec;
import java.security.spec.XECPublicKeySpec;
import java.util.HexFormat;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class MontgomeryCurveTest {

    private static byte[] hex(String written) {
        return HexFormat.of().parseHex(written);
    }

    private static MontgomeryCurve curve(String name) {
        return MontgomeryCurve.named(name).orElseThrow();
    }

    private static String jdkName(String curve) {
        return curve.equals("curve25519") ? "X25519" : "X448";
    }

    private static byte[] littleEndian(BigInteger value, int width) {
        byte[] written = new byte[width];
        byte[] biggestFirst = value.toByteArray();
        for (int step = 0; step < Math.min(width, biggestFirst.length); step++) {
            written[step] = biggestFirst[biggestFirst.length - 1 - step];
        }
        return written;
    }

    @Test
    @DisplayName("X25519 answers RFC 7748's section 5.2 vector")
    void answersTheX25519Vector() {
        byte[] scalar = hex("a546e36bf0527c9d3b16154b82465edd62144c0ac1fc5a18506a2244ba449ac4");
        byte[] u = hex("e6db6867583030db3594c1a424b15f7c726624ec26b3353b10a903a6d0ab1c4c");

        assertThat(curve("curve25519").agree(scalar, u)).contains(
                hex("c3da55379de9c6908e94ea4df28d084f32eccf03491c71f754b4075577a28552"));
    }

    @Test
    @DisplayName("X448 answers RFC 7748's section 5.2 vector")
    void answersTheX448Vector() {
        byte[] scalar = hex("3d262fddf9ec8e88495266fea19a34d28882acef045104d0d1aae121"
                + "700a779c984c24f8cdd78fbff44943eba368f54b29259a4f1c600ad3");
        byte[] u = hex("06fce640fa3487bfda5f6cf2d5263f8aad88334cbd07437f020f08f9"
                + "814dc031ddbdc38c19c6da2583fa5429db94ada18aa7a7fb4ef8a086");

        assertThat(curve("curve448").agree(scalar, u)).contains(hex(
                "ce3e4ff95a60dc6697da1db1d85e6afbdf79b50a2412d7546d5f239f"
                        + "e14fbaadeb445fc66a01b0779d98223961111e21766282f73dd96b6f"));
    }

    @Test
    @DisplayName("Alice's public key and the shared secret are RFC 7748's section 6.1 answers")
    void answersTheX25519Exchange() {
        MontgomeryCurve on = curve("curve25519");
        byte[] alice = hex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a");
        byte[] bob = hex("5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb");

        assertThat(on.publicFor(alice)).isEqualTo(
                hex("8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a"));
        assertThat(on.agree(alice, on.publicFor(bob))).contains(
                hex("4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"curve25519", "curve448"})
    @DisplayName("public keys and shared secrets are the JDK's XDH answers, over many keys")
    void answersAsTheJdkDoes(String name) throws Exception {
        MontgomeryCurve on = curve(name);
        Random seeded = new Random(name.hashCode());
        NamedParameterSpec spec = new NamedParameterSpec(jdkName(name));
        KeyFactory factory = KeyFactory.getInstance("XDH");
        for (int round = 0; round < 40; round++) {
            byte[] ours = new byte[on.width()];
            seeded.nextBytes(ours);
            KeyPairGenerator generating = KeyPairGenerator.getInstance("XDH");
            generating.initialize(spec);
            KeyPair theirs = generating.generateKeyPair();
            byte[] theirPublic = littleEndian(((XECPublicKey) theirs.getPublic()).getU(), on.width());

            KeyAgreement agreeing = KeyAgreement.getInstance("XDH");
            agreeing.init(factory.generatePrivate(new XECPrivateKeySpec(spec, ours)));
            agreeing.doPhase(factory.generatePublic(new XECPublicKeySpec(spec,
                    ((XECPublicKey) theirs.getPublic()).getU())), true);

            assertThat(on.agree(ours, theirPublic)).contains(agreeing.generateSecret());

            KeyAgreement backwards = KeyAgreement.getInstance("XDH");
            backwards.init(theirs.getPrivate());
            backwards.doPhase(factory.generatePublic(new XECPublicKeySpec(spec,
                    new BigInteger(1, reversed(on.publicFor(ours))))), true);
            assertThat(on.agree(ours, theirPublic)).contains(backwards.generateSecret());
        }
    }

    @Test
    @DisplayName("a peer whose point gives a shared secret of all zeros is refused, as the JDK refuses it")
    void refusesAnAllZeroSecret() {
        MontgomeryCurve on = curve("curve25519");

        assertThat(on.agree(new byte[32], new byte[32])).isEmpty();
    }

    @Test
    @DisplayName("only the two exchange curves are Montgomery curves here")
    void namesOnlyTheTwo() {
        assertThat(MontgomeryCurve.named("curve25519").orElseThrow().width()).isEqualTo(32);
        assertThat(MontgomeryCurve.named("curve448").orElseThrow().width()).isEqualTo(56);
        assertThat(MontgomeryCurve.named("secp256r1")).isEmpty();
    }

    private static byte[] reversed(byte[] littleEndian) {
        byte[] biggestFirst = new byte[littleEndian.length];
        for (int step = 0; step < littleEndian.length; step++) {
            biggestFirst[step] = littleEndian[littleEndian.length - 1 - step];
        }
        return biggestFirst;
    }
}
