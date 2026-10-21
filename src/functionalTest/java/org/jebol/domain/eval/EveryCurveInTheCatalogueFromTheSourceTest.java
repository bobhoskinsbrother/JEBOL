package org.jebol.domain.eval;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class EveryCurveInTheCatalogueFromTheSourceTest {

    static Stream<String> listTheThirteen() {
        return Stream.of("secp192r1", "secp224r1", "secp256r1", "secp384r1",
                "secp521r1", "secp192k1", "secp224k1", "secp256k1",
                "bp256r1", "bp384r1", "bp512r1", "curve25519", "curve448");
    }

    static Stream<String> listTheElevenThatSign() {
        return listTheThirteen().filter(curve -> !curve.startsWith("curve"));
    }

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("the catalogue names thirteen curves, in the order Rebol names them")
    void namesThirteenCurves() {
        assertThat(answerTo("""
                system/catalog/elliptic-curves = [
                    secp192r1 secp224r1 secp256r1 secp384r1 secp521r1
                    secp192k1 secp224k1 secp256k1
                    bp256r1 bp384r1 bp512r1 curve25519 curve448
                ]""")).isEqualTo("#(true)");
    }

    @Nested
    @DisplayName("every one of them makes a key and agrees a secret")
    class TheExchange {

        @ParameterizedTest(name = "{0}")
        @MethodSource(
                "org.jebol.domain.eval.EveryCurveInTheCatalogueFromTheSourceTest#listTheThirteen")
        @DisplayName("two sides on the same curve reach the same secret")
        void reachesTheSameSecretOnBothSides(String curve) {
            assertThat(answerTo("""
                    alice: ecdh/init none 'CURVE
                    boban: ecdh/init none 'CURVE
                    reduce [
                        handle? alice
                        'CURVE = ecdh/curve alice
                        (ecdh/secret alice ecdh/public boban)
                            = (ecdh/secret boban ecdh/public alice)
                    ]""".replace("CURVE", curve)))
                    .as(curve)
                    .isEqualTo("[#(true) #(true) #(true)]");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(
                "org.jebol.domain.eval.EveryCurveInTheCatalogueFromTheSourceTest#listTheThirteen")
        @DisplayName("and a released key publishes nothing afterwards")
        void publishesNothingForAReleasedKey(String curve) {
            assertThat(answerTo("""
                    k: ecdh/init none 'CURVE
                    reduce [true? release k  none? ecdh/public k]"""
                    .replace("CURVE", curve)))
                    .as(curve)
                    .isEqualTo("[#(true) #(true)]");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(
                "org.jebol.domain.eval.EveryCurveInTheCatalogueFromTheSourceTest#listTheThirteen")
        @DisplayName("and initialising over an existing key refills that same handle")
        void refillsTheHandleWhenInitialisedOverAnExistingKey(String curve) {
            assertThat(answerTo("""
                    alice: ecdh/init none 'CURVE
                    boban: ecdh/init none 'CURVE
                    release alice
                    reduce [
                        same? alice ecdh/init alice 'CURVE
                        (ecdh/secret alice ecdh/public boban)
                            = (ecdh/secret boban ecdh/public alice)
                    ]""".replace("CURVE", curve)))
                    .as(curve)
                    .isEqualTo("[#(true) #(true)]");
        }
    }

    @Nested
    @DisplayName("the eleven that can sign, and the two that cannot")
    class TheSigning {

        @ParameterizedTest(name = "{0}")
        @MethodSource(
                "org.jebol.domain.eval.EveryCurveInTheCatalogueFromTheSourceTest#listTheElevenThatSign")
        @DisplayName("a signature verifies by the key and by the published point")
        void verifiesASignatureBothWays(String curve) {
            assertThat(answerTo("""
                    k: ecdh/init none 'CURVE
                    hash: checksum "some data" 'sha256
                    sig: ecdsa/sign k hash
                    reduce [
                        binary? sig
                        block? try [decode 'der sig]
                        true? ecdsa/verify k hash sig
                        true? ecdsa/verify/curve (ecdh/public k) hash sig 'CURVE
                    ]""".replace("CURVE", curve)))
                    .as(curve)
                    .isEqualTo("[#(true) #(true) #(true) #(true)]");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(
                "org.jebol.domain.eval.EveryCurveInTheCatalogueFromTheSourceTest#listTheElevenThatSign")
        @DisplayName("and a signature over other data does not")
        void rejectsASignatureOverOtherData(String curve) {
            assertThat(answerTo("""
                    k: ecdh/init none 'CURVE
                    sig: ecdsa/sign k checksum "some data" 'sha256
                    none? ecdsa/verify k (checksum "other data" 'sha256) sig"""
                    .replace("CURVE", curve)))
                    .as(curve)
                    .isEqualTo("#(true)");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(
                "org.jebol.domain.eval.EveryCurveInTheCatalogueFromTheSourceTest#listTheElevenThatSign")
        @DisplayName("and two signatures over one hash differ, because signing is random")
        void givesTwoDifferentSignaturesOverOneHash(String curve) {
            assertThat(answerTo("""
                    k: ecdh/init none 'CURVE
                    hash: checksum "some data" 'sha256
                    (ecdsa/sign k hash) != (ecdsa/sign k hash)"""
                    .replace("CURVE", curve)))
                    .as(curve)
                    .isEqualTo("#(true)");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"curve25519", "curve448"})
        @DisplayName("the two exchange-only curves sign nothing at all")
        void signsNothingOnTheExchangeOnlyCurves(String curve) {
            assertThat(answerTo("""
                    k: ecdh/init none 'CURVE
                    none? ecdsa/sign k checksum "some data" 'sha256"""
                    .replace("CURVE", curve)))
                    .as(curve)
                    .isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("what the published point looks like on the wire")
    class ThePublishedPoint {

        @ParameterizedTest(name = "{0}")
        @CsvSource({
                "secp192r1, 49", "secp224r1, 57", "secp256r1, 65",
                "secp384r1, 97", "secp521r1, 133",
                "secp192k1, 49", "secp224k1, 57", "secp256k1, 65",
                "bp256r1, 65", "bp384r1, 97", "bp512r1, 129",
        })
        @DisplayName("both coordinates after a lead byte saying it is uncompressed")
        void carriesBothCoordinatesAfterALeadByte(String curve, int width) {
            assertThat(answerTo("""
                    k: ecdh/init none 'CURVE
                    pub: ecdh/public k
                    reduce [length? pub  pub/1]""".replace("CURVE", curve)))
                    .as(curve)
                    .isEqualTo("[" + width + " 4]");
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({"curve25519, 32", "curve448, 56"})
        @DisplayName("except the exchange-only curves, which publish one coordinate")
        void carriesOneCoordinateOnTheExchangeOnlyCurves(String curve, int width) {
            assertThat(answerTo("""
                    length? ecdh/public ecdh/init none 'CURVE"""
                    .replace("CURVE", curve)))
                    .as(curve)
                    .isEqualTo(String.valueOf(width));
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({
                "secp192r1, 24", "secp224r1, 28", "secp256r1, 32",
                "secp192k1, 24", "secp224k1, 28", "secp256k1, 32",
                "bp256r1, 32", "bp384r1, 48", "bp512r1, 64",
        })
        @DisplayName("and the secret is one coordinate wide")
        void matchesTheSecretsWidthToOneCoordinate(String curve, int width) {
            assertThat(answerTo("""
                    k: ecdh/init none 'CURVE
                    length? ecdh/secret k ecdh/public k""".replace("CURVE", curve)))
                    .as(curve)
                    .isEqualTo(String.valueOf(width));
        }
    }

    @Nested
    @DisplayName("what a curve refuses")
    class TheRefusals {

        @Test
        @DisplayName("a point from another curve is not agreed with")
        void refuseAPointFromAnotherCurve() {
            assertThat(answerTo("""
                    alice: ecdh/init none 'secp192r1
                    boban: ecdh/init none 'bp256r1
                    none? ecdh/secret alice ecdh/public boban"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and a point that is not on the curve at all is refused")
        void refuseAPointNotOnTheCurve() {
            assertThat(answerTo("""
                    k: ecdh/init none 'secp192r1
                    bad: copy ecdh/public k
                    bad/49: bad/49 xor 255
                    none? ecdh/secret k bad"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a curve nobody has heard of makes no key")
        void makeNoKeyForACurveNobodyHasHeardOf() {
            assertThat(answerTo("none? ecdh/init none 'secp999r1"))
                    .isEqualTo("#(true)");
        }
    }
}
