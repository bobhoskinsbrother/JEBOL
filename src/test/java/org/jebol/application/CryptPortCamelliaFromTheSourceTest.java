package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


class CryptPortCamelliaFromTheSourceTest {

    private static final String KEY_128 = "#{2B7E151628AED2A6ABF7158809CF4F3C}";

    private static final String KEY_192 =
            "#{8E73B0F7DA0E6452C810F32B809079E562F8EAD2522C6B7B}";

    private static final String KEY_256 =
            "#{603DEB1015CA71BE2B73AEF0857D77811F352C073B6108D72D9810A30914DFF4}";

    private static final String NIST_BLOCK = "#{6BC1BEE22E409F96E93D7E117393172A}";

    private static final String VECTOR = "#{000102030405060708090A0B0C0D0E0F}";

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String through(String algorithm, String key,
            String vector, String data) {

        return answerTo("""
                p: open make port! [scheme: 'crypt algorithm: '%s]
                modify p 'key %s
                modify p 'init-vector %s
                write p %s
                either binary? r: read p [enbase/flat r 16] [r]"""
                .formatted(algorithm, key, vector, data));
    }

    @Test
    @DisplayName("the RFC 3713 vectors, at all three key widths")
    void theRfc3713Vectors() {
        String block = "#{0123456789ABCDEFFEDCBA9876543210}";
        assertThat(through("CAMELLIA-128-ECB", block, "none", block))
                .isEqualTo("\"67673138549669730857065648EABE43\"");
        assertThat(through("CAMELLIA-192-ECB",
                "#{0123456789ABCDEFFEDCBA98765432100011223344556677}",
                "none", block))
                .isEqualTo("\"B4993401B3E996F84EE5CEE7D79B09B9\"");
        assertThat(through("CAMELLIA-256-ECB",
                "#{0123456789ABCDEFFEDCBA98765432100011223344556677"
                        + "8899AABBCCDDEEFF}", "none", block))
                .isEqualTo("\"9ACC237DFF16D76C20EF7C919E3A7509\"");
    }

    @Test
    @DisplayName("in electronic codebook, at all three key widths")
    void inElectronicCodebook() {
        assertThat(through("CAMELLIA-128-ECB", KEY_128, "none", NIST_BLOCK))
                .isEqualTo("\"432FC5DCD628115B7C388D770B270C96\"");
        assertThat(through("CAMELLIA-192-ECB", KEY_192, "none", NIST_BLOCK))
                .isEqualTo("\"CCCC6C4E138B45848514D48D0D3439D3\"");
        assertThat(through("CAMELLIA-256-ECB", KEY_256, "none", NIST_BLOCK))
                .isEqualTo("\"BEFD219B112FA00098919CD101C9CCFA\"");
    }

    @Test
    @DisplayName("and in cipher block chaining")
    void andInCipherBlockChaining() {
        assertThat(through("CAMELLIA-128-CBC", KEY_128, VECTOR, NIST_BLOCK))
                .isEqualTo("\"1607CF494B36BBF00DAEB0B503C831AB\"");
        assertThat(through("CAMELLIA-192-CBC", KEY_192, VECTOR, NIST_BLOCK))
                .isEqualTo("\"2A4830AB5AC4A1A2405955FD2195CF93\"");
        assertThat(through("CAMELLIA-256-CBC", KEY_256, VECTOR, NIST_BLOCK))
                .isEqualTo("\"E6CFA35FC02B134A4D2C0B6737AC3EDA\"");
    }

    @Test
    @DisplayName("and in both authenticated modes, which were written for AES")
    void andInBothAuthenticatedModes() {
        assertThat(through("CAMELLIA-128-GCM", KEY_128, VECTOR, NIST_BLOCK))
                .isEqualTo("\"C53D719F66B3091BB034E12652111535\"");
        assertThat(through("CAMELLIA-192-GCM", KEY_192, VECTOR, NIST_BLOCK))
                .isEqualTo("\"D6B55E49D3E612D5DEEC918812D3007E\"");
        assertThat(through("CAMELLIA-256-GCM", KEY_256, VECTOR, NIST_BLOCK))
                .isEqualTo("\"34C36D4BEF20E4C83E448E7B5563084E\"");
        assertThat(through("CAMELLIA-128-CCM", KEY_128, VECTOR, NIST_BLOCK))
                .isEqualTo("\"F9FD8BA4986B6451E8285F0F21453389\"");
        assertThat(through("CAMELLIA-192-CCM", KEY_192, VECTOR, NIST_BLOCK))
                .isEqualTo("\"FA4C0AF0534ADADC5CC0302222723E8F\"");
        assertThat(through("CAMELLIA-256-CCM", KEY_256, VECTOR, NIST_BLOCK))
                .isEqualTo("\"8B8A35D00C16D4EFF4B963B7533CDD04\"");
    }

    @Test
    @DisplayName("and deciphering gives the plain text back")
    void andDecipheringGivesThePlainTextBack() {
        assertThat(answerTo("""
                p: open make port! [scheme: 'crypt algorithm: 'CAMELLIA-128-ECB]
                modify p 'direction 'decrypt
                modify p 'key %s
                write p #{432FC5DCD628115B7C388D770B270C96}
                enbase/flat read p 16""".formatted(KEY_128)))
                .isEqualTo("\"6BC1BEE22E409F96E93D7E117393172A\"");
    }

    @Test
    @DisplayName("a thousand rounds, which a single block would not catch")
    void aThousandRounds() {
        assertThat(answerTo("""
                p: open make port! [scheme: 'crypt algorithm: 'CAMELLIA-128-ECB]
                modify p 'key #{80000000000000000000000000000000}
                block: #{00000000000000000000000000000000}
                after-one: enbase/flat cipher: read write p block 16
                loop 99 [cipher: read write p cipher]
                after-a-hundred: enbase/flat cipher 16
                loop 900 [cipher: read write p cipher]
                reduce [after-one after-a-hundred enbase/flat cipher 16]"""))
                .isEqualTo("""
                        ["6C227F749319A3AA7DA235A9BBA05A2C" \
                        "F77AEC22A6043FE27A3BCB861C4BB0AC" \
                        "89D3D322736F0C50B994120738D08782"]""");
    }

    @Test
    @DisplayName("the catalogue names all twelve, and every one opens")
    void theCatalogueNamesAllTwelve() {
        assertThat(answerTo("""
                length? collect [
                    foreach named system/catalog/ciphers [
                        if find form named "camellia" [keep named]
                    ]
                ]""")).isEqualTo("12");
    }
}
