package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EllipticCurveFromTheSourceTest {

    private static final String KEYS = """
            alice: ecdh/init none 'secp256r1
            bob: ecdh/init none 'secp256r1
            hash: #{0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F20}
            other: #{FF02030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F20}
            """;

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        String whole = KEYS + source;
        interpreter.defineFreshWordsIn(whole);
        return interpreter.display(interpreter.run(whole));
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("/INIT makes a key pair on a named curve")
    class TheContext {

        @Test
        @DisplayName("it is a handle of type ECDH")
        void itIsAnEcdhHandle() {
            assertThat(answerTo("handle? alice")).isEqualTo(TRUE);
            assertThat(answerTo("alice/type = 'ecdh")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/CURVE names the curve it was made on, which a peer needs to know")
        void itRemembersItsCurve() {
            assertThat(answerTo("(ecdh/curve alice) = 'secp256r1")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (ecdh/curve ecdh/init none 'secp384r1) = 'secp384r1""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a curve this build has not got answers none")
        void anUnknownCurveAnswersNone() {
            assertThat(answerTo("none? ecdh/init none 'nosuchcurve")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the curves it does have are in the catalogue")
        void theCurvesAreCatalogued() {
            assertThat(answerTo("true? find system/catalog/elliptic-curves 'secp256r1"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("true? find system/catalog/elliptic-curves 'secp521r1"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("ECDH is in the handle catalogue")
        void theTypeIsCatalogued() {
            assertThat(answerTo("true? find system/catalog/handles 'ecdh")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("naming two actions is bad-refines")
        void twoActionsAreRefused() {
            assertThat(answerTo("""
                    e: try [ecdh/public/curve alice] e/id""")).isEqualTo("bad-refines");
        }
    }

    @Nested
    @DisplayName("/PUBLIC and /SECRET carry the exchange")
    class TheExchange {

        @Test
        @DisplayName("the published value is a lead byte and two coordinates")
        void thePublishedPointIsUncompressed() {
            assertThat(answerTo("length? ecdh/public alice"))
                    .as("one byte plus two 32-byte coordinates on P-256")
                    .isEqualTo("65");
            assertThat(answerTo("65 = length? ecdh/public bob")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a wider curve gives a wider value")
        void aWiderCurveIsWider() {
            assertThat(answerTo("""
                    97 = length? ecdh/public ecdh/init none 'secp384r1""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("two contexts publish different points")
        void twoContextsDiffer() {
            assertThat(answerTo("(ecdh/public alice) = (ecdh/public bob)"))
                    .isEqualTo("#(false)");
        }

        @Test
        @DisplayName("both sides reach the same secret")
        void bothSidesAgree() {
            assertThat(answerTo("""
                    (ecdh/secret alice ecdh/public bob)
                        = (ecdh/secret bob ecdh/public alice)""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and it is one coordinate wide, not two")
        void theSecretIsOneCoordinate() {
            assertThat(answerTo("length? ecdh/secret alice ecdh/public bob"))
                    .isEqualTo("32");
        }

        @Test
        @DisplayName("a point from another curve answers none rather than a useless secret")
        void aPointFromAnotherCurveAnswersNone() {
            assertThat(answerTo("""
                    none? ecdh/secret alice ecdh/public ecdh/init none 'secp384r1"""))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and so does something that is not a point at all")
        void rubbishAnswersNone() {
            assertThat(answerTo("none? ecdh/secret alice #{00}")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a handle of another type answers none")
        void aWrongHandleAnswersNone() {
            assertThat(answerTo("none? ecdh/public rc4/key #{01}")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("the two curves made for the exchange alone")
    class TheCurvesMadeForTheExchangeAlone {

        @Test
        @DisplayName("publish one coordinate, with no lead byte")
        void publishOneCoordinateWithNoLeadByte() {
            assertThat(answerTo("""
                    length? ecdh/public ecdh/init none 'curve25519""")).isEqualTo("32");
            assertThat(answerTo("""
                    length? ecdh/public ecdh/init none 'curve448""")).isEqualTo("56");
        }

        @Test
        @DisplayName("and both sides reach the same secret, one coordinate wide")
        void bothSidesReachTheSameSecret() {
            assertThat(answerTo("""
                    a: ecdh/init none 'curve25519
                    b: ecdh/init none 'curve25519
                    reduce [
                        length? ecdh/secret a ecdh/public b
                        (ecdh/secret a ecdh/public b) = (ecdh/secret b ecdh/public a)
                    ]""")).isEqualTo("[32 #(true)]");
            assertThat(answerTo("""
                    a: ecdh/init none 'curve448
                    b: ecdh/init none 'curve448
                    reduce [
                        length? ecdh/secret a ecdh/public b
                        (ecdh/secret a ecdh/public b) = (ecdh/secret b ecdh/public a)
                    ]""")).isEqualTo("[56 #(true)]");
        }

        @Test
        @DisplayName("two contexts publish different points")
        void twoContextsPublishDifferentPoints() {
            assertThat(answerTo("""
                    (ecdh/public ecdh/init none 'curve25519)
                        = (ecdh/public ecdh/init none 'curve25519)"""))
                    .isEqualTo("#(false)");
        }

        @Test
        @DisplayName("and each names the curve it was made on")
        void eachNamesTheCurveItWasMadeOn() {
            assertThat(answerTo("""
                    reduce [
                        ecdh/curve ecdh/init none 'curve25519
                        ecdh/curve ecdh/init none 'curve448
                    ]""")).isEqualTo("[curve25519 curve448]");
        }

        @Test
        @DisplayName("a peer value of the wrong width answers none")
        void aPeerValueOfTheWrongWidthAnswersNone() {
            assertThat(answerTo("""
                    a: ecdh/init none 'curve25519
                    reduce [
                        none? ecdh/secret a copy/part ecdh/public a 31
                        none? ecdh/secret a append copy ecdh/public a #{00}
                        none? ecdh/secret a #{}
                    ]""")).isEqualTo("[#(true) #(true) #(true)]");
        }

        @Test
        @DisplayName("and neither of them signs anything")
        void neitherOfThemSignsAnything() {
            assertThat(answerTo("""
                    k: ecdh/init none 'curve25519
                    reduce [none? ecdsa/sign k hash  none? ecdsa/verify k hash #{3045}]"""))
                    .isEqualTo("[#(true) #(true)]");
            assertThat(answerTo("""
                    none? ecdsa/sign (ecdh/init none 'curve448) hash"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and one from another curve answers none")
        void oneFromAnotherCurveAnswersNone() {
            assertThat(answerTo("""
                    reduce [
                        none? ecdh/secret (ecdh/init none 'curve25519)
                            (ecdh/public ecdh/init none 'curve448)
                        none? ecdh/secret (ecdh/init none 'curve25519)
                            (ecdh/public ecdh/init none 'secp256r1)
                        none? ecdh/secret (ecdh/init none 'secp256r1)
                            (ecdh/public ecdh/init none 'curve25519)
                    ]""")).isEqualTo("[#(true) #(true) #(true)]");
        }
    }

    @Nested
    @DisplayName("ECDSA signs and verifies")
    class TheSignature {

        @Test
        @DisplayName("a signature is ASN.1, so it is a sequence rather than two numbers")
        void theSignatureIsAsn1() {
            assertThat(answerTo("binary? ecdsa/sign alice hash")).isEqualTo(TRUE);
            assertThat(answerTo("48 = first ecdsa/sign alice hash"))
                    .as("0x30 is the ASN.1 tag for a sequence")
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and it verifies against the key that made it")
        void itVerifies() {
            assertThat(answerTo("true? ecdsa/verify alice hash ecdsa/sign alice hash"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("signing is randomised, so two signatures differ and both hold")
        void signingIsRandomised() {
            assertThat(answerTo("(ecdsa/sign alice hash) = (ecdsa/sign alice hash)"))
                    .as("unlike RSA's, which is deterministic")
                    .isEqualTo("#(false)");
            assertThat(answerTo("""
                    all [
                        true? ecdsa/verify alice hash ecdsa/sign alice hash
                        true? ecdsa/verify alice hash ecdsa/sign alice hash
                    ]""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a signature that does not hold answers NONE, not false")
        void aFailedVerifyAnswersNone() {
            assertThat(answerTo("none? ecdsa/verify alice other ecdsa/sign alice hash"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and one made by another key likewise")
        void anotherKeysSignatureDoesNotHold() {
            assertThat(answerTo("none? ecdsa/verify alice hash ecdsa/sign bob hash"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a handle of another type answers none")
        void aWrongHandleAnswersNone() {
            assertThat(answerTo("none? ecdsa/sign (rc4/key #{01}) hash")).isEqualTo(TRUE);
        }
    }
}
