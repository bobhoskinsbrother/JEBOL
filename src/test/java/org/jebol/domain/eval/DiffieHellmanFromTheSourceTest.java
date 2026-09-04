package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Diffie-Hellman: DH-INIT makes a key and DH uses it, once to publish and once
 * to agree.
 *
 * <p>{@code n-crypt.c}. What it is for explains the shape. Two parties who
 * have never met need a shared secret over a line anyone can read. Each makes
 * a private number, publishes something derived from it, and combines the
 * other's published value with their own private one. Both reach the same
 * secret; a listener who saw both published values cannot work it out.
 *
 * <p>So the context is unlike RSA's. An RSA context holds a key the caller
 * supplied. This one holds a key the interpreter <em>generated</em>, and the
 * private half never leaves it -- the only things a caller can do are publish
 * and agree.
 *
 * <p>The parameters below are RFC 3526 group 5, the 1536-bit MODP group, which
 * is what {@code prot-tls.reb} reaches for. Every expectation was run against
 * a real 3.22.1 with them, except the one marked as a divergence.
 *
 * <p>Specified in {@code spec/natives.allium} under Diffie-Hellman.
 */
class DiffieHellmanFromTheSourceTest {

    /** RFC 3526 group 5, and the generator that goes with it. */
    private static final String PARAMETERS = """
            p: #{FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74
                 020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F1437
                 4FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED
                 EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF05
                 98DA48361C55D39A69163FA8FD24CF5F83655D23DCA3AD961C62F356208552BB
                 9ED529077096966D670C354E4ABC9804F1746C08CA237327FFFFFFFFFFFFFFFF}
            g: #{02}
            alice: dh-init g p
            bob: dh-init g p
            """;

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        String whole = PARAMETERS + source;
        interpreter.defineFreshWordsIn(whole);
        return interpreter.display(interpreter.run(whole));
    }

    private static final String TRUE = "#(true)";

    /** 1536 bits is 192 bytes, and both the public value and secret are that wide. */
    private static final String PRIME_WIDTH = "192";

    @Nested
    @DisplayName("DH-INIT generates a key pair")
    class TheContext {

        @Test
        @DisplayName("a generator and a prime make a context")
        void itMakesAContext() {
            assertThat(answerTo("handle? alice")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("named DHM, which is the group it holds rather than the exchange")
        void itIsNamedDhm() {
            assertThat(answerTo("alice/type = 'dhm")).isEqualTo(TRUE);
            assertThat(answerTo("true? find system/catalog/handles 'dhm")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("two contexts hold different private numbers, which is the point")
        void eachContextIsItsOwn() {
            assertThat(answerTo("(dh/public alice) = (dh/public bob)"))
                    .as("a generated key that repeated would give everyone one secret")
                    .isEqualTo("#(false)");
        }

        @Test
        @DisplayName("parameters too small to carry an exchange answer none")
        void tinyParametersAnswerNone() {
            assertThat(answerTo("none? dh-init #{02} #{03}")).isEqualTo(TRUE);
            assertThat(answerTo("none? dh-init #{02} #{}")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and something that is not a binary is refused by the declaration")
        void nonBinaryParametersAreRefused() {
            assertThat(answerTo("""
                    e: try [dh-init "2" p] e/id""")).isEqualTo("expect-arg");
        }
    }

    @Nested
    @DisplayName("/PUBLIC hands out the value to send")
    class ThePublicValue {

        @Test
        @DisplayName("as a binary the width of the field prime")
        void itIsThePrimeWidth() {
            assertThat(answerTo("binary? dh/public alice")).isEqualTo(TRUE);
            assertThat(answerTo("length? dh/public alice")).isEqualTo(PRIME_WIDTH);
        }

        @Test
        @DisplayName("and the same value every time, since it comes from one private number")
        void itDoesNotChange() {
            assertThat(answerTo("(dh/public alice) = (dh/public alice)")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("/SECRET is what the whole exchange is for")
    class TheSharedSecret {

        @Test
        @DisplayName("both sides reach the same secret from the other's public value")
        void bothSidesAgree() {
            assertThat(answerTo("""
                    (dh/secret alice dh/public bob) = (dh/secret bob dh/public alice)"""))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and it is as wide as the field prime")
        void theSecretIsThePrimeWidth() {
            assertThat(answerTo("length? dh/secret alice dh/public bob"))
                    .isEqualTo(PRIME_WIDTH);
        }

        @Test
        @DisplayName("a third party's value gives a different secret, which is the security")
        void aThirdPartyDoesNotAgree() {
            assertThat(answerTo("""
                    eve: dh-init g p
                    (dh/secret alice dh/public bob) = (dh/secret alice dh/public eve)"""))
                    .isEqualTo("#(false)");
        }

        @Test
        @DisplayName("a public value outside the field answers none rather than a secret")
        void anImpossiblePeerValueAnswersNone() {
            assertThat(answerTo("none? dh/secret alice #{00}")).isEqualTo(TRUE);
            assertThat(answerTo("none? dh/secret alice p")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("what it refuses, and the one place this leaves the C behind")
    class TheRefusals {

        @Test
        @DisplayName("naming both refinements is bad-refines")
        void bothRefinementsAreRefused() {
            assertThat(answerTo("""
                    e: try [dh/public/secret alice #{02}] e/id""")).isEqualTo("bad-refines");
        }

        @Test
        @DisplayName("a handle of another type answers none, which the C doubts in a comment")
        void aHandleOfAnotherTypeAnswersNone() {
            // `return R_NONE; //or? Trap0(RE_INVALID_HANDLE);` -- so this one
            // declines where RC4 and RSA raise. Followed as written.
            assertThat(answerTo("none? dh/public rc4/key #{01}")).isEqualTo(TRUE);
            assertThat(answerTo("none? dh/secret (rc4/key #{01}) #{02}")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and naming neither answers none, where a real 3.22.1 reads free memory")
        void noRefinementAnswersNone() {
            // The C's branches are `if (refPublic)` and `if (refSecret)`, and
            // with neither taken it reaches `return R_RET` having never
            // written the return slot. A real 3.22.1 hands back whatever that
            // memory held: a binary of nothing in particular here, and a
            // segmentation fault when two contexts were built in one
            // expression. Not a rule to port.
            assertThat(answerTo("none? dh alice")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and building several contexts in one expression is safe here")
        void severalContextsInOneExpressionAreSafe() {
            assertThat(answerTo("""
                    (dh/public dh-init g p) = (dh/public dh-init g p)"""))
                    .isEqualTo("#(false)");
        }
    }
}
