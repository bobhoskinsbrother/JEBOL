package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GENERATE, which names a curve and answers a point that is not on it.
 *
 * <p>{@code n-crypt.c}. This is a stub upstream rather than a defect, and the
 * difference decides what to do with it. {@code mbedtls_ecdsa_genkey} is
 * commented out, the group is loaded as SECP192R1 whichever curve was asked
 * for, and the point written out is one nobody set. A real 3.22.1 answers a
 * single zero byte -- the point at infinity -- for every curve in the
 * catalogue, which is what the tests below assert because it is what it does.
 *
 * <p>Copied rather than finished, and the declaration is why. The answer is
 * one binary with nowhere in it for a private key, so a GENERATE that really
 * made a pair would hand back the public half and discard the private half:
 * an answer nothing can use. ECDH/INIT already makes a usable elliptic-curve
 * key, and inventing something better here would be behaviour no caller asked
 * for and no other Rebol has.
 *
 * <p>Specified in {@code spec/natives.allium} under GENERATE.
 */
class GenerateFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String TRUE = "#(true)";

    @Test
    @DisplayName("it answers a single zero byte, whatever curve is named")
    void itAnswersThePointAtInfinity() {
        assertThat(answerTo("generate 'secp256r1")).isEqualTo("#{00}");
        assertThat(answerTo("generate 'secp192r1")).isEqualTo("#{00}");
        assertThat(answerTo("generate 'secp521r1")).isEqualTo("#{00}");
    }

    @Test
    @DisplayName("and every curve in the catalogue gives the same, including ones ECDH declines")
    void everyCatalogueCurveAnswersTheSame() {
        assertThat(answerTo("""
                empty? remove-each c copy system/catalog/elliptic-curves [
                    #{00} = generate c
                ]""")).isEqualTo(TRUE);
    }

    @Test
    @DisplayName("a word naming no curve is invalid-arg, which is the part that works")
    void anUnknownCurveIsRefused() {
        assertThat(answerTo("""
                e: try [generate 'nosuchcurve] e/id""")).isEqualTo("invalid-arg");
    }

    @Test
    @DisplayName("and the refusal names the word that was wrong")
    void theRefusalNamesTheWord() {
        assertThat(answerTo("""
                e: try [generate 'nosuchcurve] e/arg1 = 'nosuchcurve""")).isEqualTo(TRUE);
    }

    @Test
    @DisplayName("something that is not a word is refused by the declaration")
    void aNonWordIsRefused() {
        assertThat(answerTo("""
                e: try [generate 5] e/id""")).isEqualTo("expect-arg");
        assertThat(answerTo("""
                e: try [generate "secp256r1"] e/id""")).isEqualTo("expect-arg");
    }

    @Test
    @DisplayName("and it needs an argument")
    void itNeedsAnArgument() {
        assertThat(answerTo("""
                e: try [generate] e/id""")).isEqualTo("no-arg");
    }

    @Test
    @DisplayName("ECDH/INIT is the way to a key that can actually be used")
    void ecdhInitIsTheRealOne() {
        // Stated here rather than only in the javadoc, because the reason
        // this stub is copied instead of finished is that a usable key
        // already has a home.
        assertThat(answerTo("""
                k: ecdh/init none 'secp256r1
                all [handle? k  65 = length? ecdh/public k]""")).isEqualTo(TRUE);
    }
}
