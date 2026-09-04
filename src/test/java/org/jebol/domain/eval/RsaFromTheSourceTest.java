package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RSA, which is two natives as RC4 is: RSA-INIT builds a key context and RSA
 * uses it.
 *
 * <p>{@code n-crypt.c}. The split is not about state -- an RSA operation
 * carries nothing between calls -- but about cost and shape. The key arrives
 * as raw numbers, each a binary, and turning those into something that can
 * encipher means checking they really are a key: {@code
 * mbedtls_rsa_check_pubkey} for a public one, {@code check_privkey} for a
 * private one. A caller holding a context has somewhere to be told the numbers
 * were wrong before any data is handed over.
 *
 * <p>The failure shape is the surprise and is pinned below. A wrong handle
 * raises and wrong refinements raise, but numbers that do not form a key
 * answer <em>none</em>, and so does asking a public-only context to decrypt.
 *
 * <p>The key material is a real 1024-bit pair, and every expectation here was
 * run against a real 3.22.1 with these exact numbers. 1024 rather than 512
 * because OAEP needs sixty-six bytes of overhead and a 512-bit modulus has
 * only sixty-four, so a smaller key answers none for that mode and the test
 * would have proved nothing.
 *
 * <p>Specified in {@code spec/natives.allium} under RSA.
 */
class RsaFromTheSourceTest {

    /** A 1024-bit key pair, fixed so the tests do not depend on generation. */
    private static final String KEY = """
            n: #{B8092F6F04726A921CFAB2D313AE9D2F01C7CE465FAB7DA62C7A5C73FACE5FFB
                 A2F1DD80A29ADC43399CFCA22279B89A264810E5B926BB5E0D3F727A763E1601
                 3F89F8FEAC59D0FBDD5E8B0C52827E5490F13B84C3634E89C6D1731AE5F1A60F
                 88ED118D080E1AB2CAA532D06C2F7D2A0874DEE4E6B6E57283F6478DAF4253DB}
            e: #{010001}
            d: #{1FB8D198C8C2F214B273121CE911199DEF282229A636F8A70A96A2D608FEC6B3
                 A8C01906A1C0A0C3E3ABE82E085443DA2A4C14C18C3B1D63D653BFE754F759B2
                 5D19D1F35419C559B315F651675C8D05913B35CB631F550CD6E7A35F4F1DA7CC
                 A914467C6145F2760C2755387299AEA7C2A7835B68409CFED2ECB722B07B3DFD}
            p: #{F24D7B797432A7AAF05C29E032FAA297277A14F8A8C7B477EFF89D2215A1D41C
                 88E244EF9D8A617B35199D3FF844D6368067F0BED914EF608DC77FC6D9520045}
            q: #{C2707CA219CB8D445C7ABD31A8F12BB8EB4FCF17C56200B394019C0F8853BE17
                 1A71DA4C962A4E43B4A6C239781E10F13681C0854F5CED6FF9EEB0F26F3C959F}
            pub: rsa-init n e
            priv: rsa-init/private n e d p q
            msg: #{48656C6C6F}
            """;

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        String whole = KEY + source;
        interpreter.defineFreshWordsIn(whole);
        return interpreter.display(interpreter.run(whole));
    }

    private static final String TRUE = "#(true)";
    private static final String FALSE = "#(false)";

    /** 1024 bits is 128 bytes, and every ciphertext is that wide. */
    private static final String MODULUS_WIDTH = "128";

    @Nested
    @DisplayName("RSA-INIT builds a context out of raw numbers")
    class TheContext {

        @Test
        @DisplayName("a modulus and a public exponent make a public context")
        void thePublicContext() {
            assertThat(answerTo("handle? pub")).isEqualTo(TRUE);
            assertThat(answerTo("pub/type = 'rsa")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/PRIVATE takes the three numbers that let it decrypt and sign")
        void thePrivateContext() {
            assertThat(answerTo("handle? priv")).isEqualTo(TRUE);
            assertThat(answerTo("priv/type = 'rsa")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("numbers that are not a key answer none rather than raising")
        void badNumbersAnswerNone() {
            assertThat(answerTo("none? rsa-init #{00} #{00}")).isEqualTo(TRUE);
            assertThat(answerTo("none? rsa-init #{} #{}")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and private numbers that do not match the modulus likewise")
        void mismatchedPrivateNumbersAnswerNone() {
            assertThat(answerTo("none? rsa-init/private n e #{0102} p q")).isEqualTo(TRUE);
            assertThat(answerTo("none? rsa-init/private n e d #{03} #{05}")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("though the two primes may arrive either way round, since they multiply")
        void thePrimesMayBeSwapped() {
            // Not a mismatch: p times q is q times p, so the key is the same
            // key. Asserting this refuses would have been a guess, and the
            // binary answers a perfectly good context.
            assertThat(answerTo("handle? rsa-init/private n e d q p")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and an even modulus is no modulus at all")
        void anEvenModulusIsRefused() {
            assertThat(answerTo("none? rsa-init #{04} #{010001}")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("RSA is in the handle catalogue beside the cipher kinds")
        void theTypeIsCatalogued() {
            assertThat(answerTo("true? find system/catalog/handles 'rsa")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("something that is not a binary is refused by the declaration")
        void nonBinaryNumbersAreRefused() {
            assertThat(answerTo("""
                    e: try [rsa-init "n" e] e/id""")).isEqualTo("expect-arg");
        }
    }

    @Nested
    @DisplayName("enciphering and deciphering")
    class TheCipher {

        @Test
        @DisplayName("a ciphertext is as wide as the modulus, whatever went in")
        void theCiphertextIsTheModulusWidth() {
            assertThat(answerTo("length? rsa/encrypt pub msg")).isEqualTo(MODULUS_WIDTH);
            assertThat(answerTo("length? rsa/encrypt pub #{}")).isEqualTo(MODULUS_WIDTH);
        }

        @Test
        @DisplayName("and the private context turns it back into what went in")
        void decryptUndoesEncrypt() {
            assertThat(answerTo("msg = rsa/decrypt priv rsa/encrypt pub msg"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("enciphering twice gives two answers, because the padding is random")
        void encryptIsRandomised() {
            assertThat(answerTo("(rsa/encrypt pub msg) = (rsa/encrypt pub msg)"))
                    .as("a test comparing two ciphertexts is testing the wrong thing")
                    .isEqualTo(FALSE);
        }

        @Test
        @DisplayName("a string is read as its bytes")
        void aStringIsReadAsBytes() {
            assertThat(answerTo("binary? rsa/encrypt pub \"hello\"")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (to binary! "hello") = rsa/decrypt priv rsa/encrypt pub "hello\""""))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a private context can encipher too, holding the public numbers")
        void aPrivateContextCanEncrypt() {
            assertThat(answerTo("msg = rsa/decrypt priv rsa/encrypt priv msg"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("OAEP padding round trips, needing a modulus wide enough for it")
        void oaepRoundTrips() {
            assertThat(answerTo("msg = rsa/decrypt/oaep priv rsa/encrypt/oaep pub msg"))
                    .isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("signing and verifying")
    class TheSignature {

        @Test
        @DisplayName("a signature is as wide as the modulus")
        void theSignatureIsTheModulusWidth() {
            assertThat(answerTo("length? rsa/sign priv msg")).isEqualTo(MODULUS_WIDTH);
        }

        @Test
        @DisplayName("and it verifies against the public context")
        void itVerifies() {
            assertThat(answerTo("rsa/verify pub msg rsa/sign priv msg")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("while a signature over other data does not")
        void aWrongSignatureDoesNotVerify() {
            assertThat(answerTo("rsa/verify pub #{00} rsa/sign priv msg")).isEqualTo(FALSE);
        }

        @Test
        @DisplayName("signing is not randomised, so the same data signs the same way")
        void signingIsDeterministic() {
            assertThat(answerTo("(rsa/sign priv msg) = (rsa/sign priv msg)"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/HASH names the digest, and the default agrees with SHA256")
        void theHashRefinement() {
            assertThat(answerTo(
                    "rsa/verify/hash pub msg (rsa/sign/hash priv msg 'sha256) 'sha256"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("rsa/verify/hash pub msg (rsa/sign priv msg) 'sha256"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("PSS signs and verifies as its own scheme")
        void pssRoundTrips() {
            // The nested call is parenthesised because /VERIFY takes a third
            // argument: without them the signature slot swallows the word
            // RSA and the rest reads as its arguments.
            assertThat(answerTo("rsa/verify/pss pub msg (rsa/sign/pss priv msg)"))
                    .isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("what it refuses, and what it merely declines")
    class TheRefusals {

        @Test
        @DisplayName("naming two actions is bad-refines")
        void twoActionsAreRefused() {
            assertThat(answerTo("""
                    e: try [rsa/encrypt/decrypt pub msg] e/id""")).isEqualTo("bad-refines");
            assertThat(answerTo("""
                    e: try [rsa/sign/verify priv msg #{00}] e/id"""))
                    .isEqualTo("bad-refines");
        }

        @Test
        @DisplayName("and padding without an action likewise")
        void paddingWithoutAnActionIsRefused() {
            assertThat(answerTo("""
                    e: try [rsa/oaep pub msg] e/id""")).isEqualTo("bad-refines");
            assertThat(answerTo("""
                    e: try [rsa/pss pub msg] e/id""")).isEqualTo("bad-refines");
        }

        @Test
        @DisplayName("a handle of another type is invalid-handle")
        void aHandleOfAnotherTypeIsRefused() {
            assertThat(answerTo("""
                    e: try [rsa/encrypt (rc4/key #{01}) msg] e/id"""))
                    .isEqualTo("invalid-handle");
        }

        @Test
        @DisplayName("but a public context asked to decrypt merely answers none")
        void aPublicContextDecliningIsNotAnError() {
            assertThat(answerTo("none? rsa/decrypt pub rsa/encrypt pub msg"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and one asked to sign likewise")
        void aPublicContextCannotSign() {
            assertThat(answerTo("none? rsa/sign pub msg")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("no action at all answers none rather than doing something")
        void noActionAnswersNone() {
            assertThat(answerTo("none? rsa pub msg")).isEqualTo(TRUE);
        }
    }
}
