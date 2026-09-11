package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ARIA, the last cipher REBOL's catalogue names.
 *
 * <p>RFC 5794, and {@code aria.c}. South Korea's national block cipher,
 * standardised as KS X 1213 and mandated for government use there. Same block
 * and same three key widths as AES, and the same substitution-permutation
 * shape -- unlike Camellia, which is a Feistel design.
 *
 * <p>Twelve rounds for the shortest key and sixteen for the longest, each an
 * exclusive-or against a round key, a pass through four substitution tables,
 * and a diffusion step that mixes every byte into every other. The odd and
 * even rounds use the same four tables in a different order, which is the
 * whole of the difference between them.
 *
 * <p>Nothing asks for it. It appears in no codec, no protocol and no fallback
 * in REBOL's own library, and no assertion in REBOL's own suite touches it.
 * It is here because the catalogue names it and a name in a catalogue is a
 * promise -- and because it is the last twelve entries between this build's
 * thirty and REBOL's forty-two.
 *
 * <p>Every expectation was read off a real 3.22.5 first, and the three
 * codebook vectors are RFC 5794's own.
 */
class CryptPortAriaFromTheSourceTest {

    private static final String KEY_128 = "#{000102030405060708090A0B0C0D0E0F}";

    private static final String KEY_192 =
            "#{000102030405060708090A0B0C0D0E0F1011121314151617}";

    private static final String KEY_256 =
            "#{000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F}";

    private static final String COUNTING_BLOCK = "#{00112233445566778899AABBCCDDEEFF}";

    private static final String VECTOR = "#{0F0E0D0C0B0A09080706050403020100}";

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String through(String algorithm, String key, String vector) {
        return answerTo("""
                p: open make port! [scheme: 'crypt algorithm: '%s]
                modify p 'key %s
                modify p 'init-vector %s
                write p %s
                either binary? r: read p [enbase/flat r 16] [r]"""
                .formatted(algorithm, key, vector, COUNTING_BLOCK));
    }

    /** RFC 5794 appendix A, one vector per key width. */
    @Test
    @DisplayName("the RFC 5794 vectors, at all three key widths")
    void theRfc5794Vectors() {
        assertThat(through("ARIA-128-ECB", KEY_128, "none"))
                .isEqualTo("\"D718FBD6AB644C739DA95F3BE6451778\"");
        assertThat(through("ARIA-192-ECB", KEY_192, "none"))
                .isEqualTo("\"26449C1805DBE7AA25A468CE263A9E79\"");
        assertThat(through("ARIA-256-ECB", KEY_256, "none"))
                .isEqualTo("\"F92BD7C79FB72E2F2B8F80C1972D24FC\"");
    }

    @Test
    @DisplayName("and in cipher block chaining")
    void andInCipherBlockChaining() {
        assertThat(through("ARIA-128-CBC", KEY_128, VECTOR))
                .isEqualTo("\"37C7A1F5259E100637798850FDC1FC48\"");
        assertThat(through("ARIA-192-CBC", KEY_192, VECTOR))
                .isEqualTo("\"B74ACEFA7CF905A529A77C435A1B5265\"");
        assertThat(through("ARIA-256-CBC", KEY_256, VECTOR))
                .isEqualTo("\"08186E7986B43C2C7B93C7C95373C4D8\"");
    }

    /**
     * The authenticated modes take it without a line written for them, which
     * is the third time that has been true and the point of a mode knowing
     * nothing about the cipher it drives.
     */
    @Test
    @DisplayName("and in both authenticated modes, which were written for AES")
    void andInBothAuthenticatedModes() {
        assertThat(through("ARIA-128-GCM", KEY_128, VECTOR))
                .isEqualTo("\"E5D97133CC5D00C9E57AA2077216DA68\"");
        assertThat(through("ARIA-192-GCM", KEY_192, VECTOR))
                .isEqualTo("\"4216F540BACF769FA5FC1A0088344036\"");
        assertThat(through("ARIA-256-GCM", KEY_256, VECTOR))
                .isEqualTo("\"A9794352EED7066498C2FBC586DDC599\"");
        assertThat(through("ARIA-128-CCM", KEY_128, VECTOR))
                .isEqualTo("\"21BDE9CE856E54CE34B549B77FF11970\"");
        assertThat(through("ARIA-192-CCM", KEY_192, VECTOR))
                .isEqualTo("\"9FF474538275002D71CC26029037C8BC\"");
        assertThat(through("ARIA-256-CCM", KEY_256, VECTOR))
                .isEqualTo("\"678E68153CDBD27339342A52C32C0422\"");
    }

    @Test
    @DisplayName("and deciphering gives the plain text back")
    void andDecipheringGivesThePlainTextBack() {
        assertThat(answerTo("""
                p: open make port! [scheme: 'crypt algorithm: 'ARIA-128-ECB]
                modify p 'direction 'decrypt
                modify p 'key %s
                write p #{D718FBD6AB644C739DA95F3BE6451778}
                enbase/flat read p 16""".formatted(KEY_128)))
                .isEqualTo("\"00112233445566778899AABBCCDDEEFF\"");
    }

    /**
     * A thousand rounds, the same walk Camellia gets, and for the same reason:
     * a key schedule can be wrong in a way one block never shows. ARIA's is
     * the more fiddly of the two -- four derived words rotated by four
     * different amounts across a hundred and twenty-eight bits.
     */
    @Test
    @DisplayName("a thousand rounds, which a single block would not catch")
    void aThousandRounds() {
        assertThat(answerTo("""
                p: open make port! [scheme: 'crypt algorithm: 'ARIA-128-ECB]
                modify p 'key #{80000000000000000000000000000000}
                block: #{00000000000000000000000000000000}
                after-one: enbase/flat cipher: read write p block 16
                loop 99 [cipher: read write p cipher]
                after-a-hundred: enbase/flat cipher 16
                loop 900 [cipher: read write p cipher]
                reduce [after-one after-a-hundred enbase/flat cipher 16]"""))
                .isEqualTo("""
                        ["4ABA3055788204D82F4539D81BC9384B" \
                        "B94301563FE9AA1C9298CAB075B24CD5" \
                        "8F6B4899F9C4A21CD549198DD7B30C5B"]""");
    }

    @Test
    @DisplayName("the catalogue names all twelve, and it is now complete")
    void theCatalogueNamesAllTwelve() {
        assertThat(answerTo("""
                length? collect [
                    foreach named system/catalog/ciphers [
                        if find form named "aria" [keep named]
                    ]
                ]""")).isEqualTo("12");
        assertThat(answerTo("42 = length? system/catalog/ciphers"))
                .isEqualTo("#(true)");
    }
}
