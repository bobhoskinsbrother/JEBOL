package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CryptPortAlgorithmsFromTheSourceTest {

    private static final String AES_128_KEY = "#{2B7E151628AED2A6ABF7158809CF4F3C}";

    private static final String VECTOR = "#{000102030405060708090A0B0C0D0E0F}";

    private static final String NIST_BLOCK = "#{6BC1BEE22E409F96E93D7E117393172A}";

    private static final String COUNTING_BLOCK = "#{00112233445566778899AABBCCDDEEFF}";

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
                either binary? r: read p [enbase/flat r 16] [mold r]"""
                .formatted(algorithm, key, vector, data));
    }

    @Test
    @DisplayName("the catalogue names what this build serves, and every one opens")
    void theCatalogueNamesWhatThisBuildServes() {
        assertThat(answerTo("mold/flat system/catalog/ciphers")).isEqualTo("""
                {[aes-128-ecb aes-192-ecb aes-256-ecb aes-128-cbc aes-192-cbc \
                aes-256-cbc aes-128-ccm aes-192-ccm aes-256-ccm \
                aes-128-gcm aes-192-gcm aes-256-gcm \
                camellia-128-ecb camellia-192-ecb camellia-256-ecb \
                camellia-128-cbc camellia-192-cbc camellia-256-cbc \
                camellia-128-ccm camellia-192-ccm camellia-256-ccm \
                camellia-128-gcm camellia-192-gcm camellia-256-gcm \
                aria-128-ecb aria-192-ecb aria-256-ecb \
                aria-128-cbc aria-192-cbc aria-256-cbc \
                aria-128-ccm aria-192-ccm aria-256-ccm \
                aria-128-gcm aria-192-gcm aria-256-gcm \
                chacha20 chacha20-poly1305 \
                des_ecb des3_ecb des_cbc des3_cbc]}""");
        assertThat(answerTo("""
                every-one-opens: true
                foreach named system/catalog/ciphers [
                    unless port? try [open make port! [scheme: 'crypt algorithm: named]] [
                        every-one-opens: false
                    ]
                ]
                every-one-opens""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("AES in electronic codebook, at all three key widths")
    void aesInElectronicCodebook() {
        assertThat(through("AES-128-ECB", AES_128_KEY, "none", NIST_BLOCK))
                .isEqualTo("\"3AD77BB40D7A3660A89ECAF32466EF97\"");
        assertThat(through("AES-192-ECB",
                "#{000102030405060708090A0B0C0D0E0F1011121314151617}",
                "none", COUNTING_BLOCK))
                .isEqualTo("\"DDA97CA4864CDFE06EAF70A0EC0D7191\"");
        assertThat(through("AES-256-ECB",
                "#{000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F}",
                "none", COUNTING_BLOCK))
                .isEqualTo("\"8EA2B7CA516745BFEAFC49904B496089\"");
    }

    @Test
    @DisplayName("AES in cipher block chaining, at all three key widths")
    void aesInCipherBlockChaining() {
        assertThat(through("AES-128-CBC", AES_128_KEY, VECTOR, NIST_BLOCK))
                .isEqualTo("\"7649ABAC8119B246CEE98E9B12E9197D\"");
        assertThat(through("AES-192-CBC",
                "#{8E73B0F7DA0E6452C810F32B809079E562F8EAD2522C6B7B}",
                VECTOR, NIST_BLOCK))
                .isEqualTo("\"4F021DB243BC633D7178183A9FA071E8\"");
        assertThat(through("AES-256-CBC",
                "#{603DEB1015CA71BE2B73AEF0857D77811F352C073B6108D72D9810A30914DFF4}",
                VECTOR, NIST_BLOCK))
                .isEqualTo("\"F58C4C04D6E5F1BA779EABFB5F7BFBD6\"");
    }

    @Test
    @DisplayName("every cipher in the catalogue decrypts what it encrypted")
    void everyCipherDecryptsWhatItEncrypted() {
        assertThat(answerTo("""
                key: #{000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F}
                vec: #{0F0E0D0C0B0A09080706050403020100}
                plain: %s
                every-one: true
                foreach named system/catalog/ciphers [
                    if named <> 'chacha20-poly1305 [
                        c: open make port! [scheme: 'crypt algorithm: named]
                        modify c 'key key   modify c 'init-vector vec
                        write c plain
                        sealed: read c
                        d: open make port! [scheme: 'crypt algorithm: named]
                        modify d 'direction 'decrypt
                        modify d 'key key   modify d 'init-vector vec
                        write d sealed
                        unless equal? plain read d [every-one: false]
                    ]
                ]
                every-one""".formatted(NIST_BLOCK))).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("and the cipher text between is what a real 3.22.5 writes")
    void theCipherTextBetweenIsWhatARealRebolWrites() {
        assertThat(answerTo("""
                key: #{000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F}
                vec: #{0F0E0D0C0B0A09080706050403020100}
                collect [
                    foreach named system/catalog/ciphers [
                        if named <> 'chacha20-poly1305 [
                            c: open make port! [scheme: 'crypt algorithm: named]
                            modify c 'key key   modify c 'init-vector vec
                            write c %s
                            keep enbase/flat read c 16
                        ]
                    ]
                ]""".formatted(NIST_BLOCK))).isEqualTo("""
                ["47C58D5E21CAAF840D015B7D9B910981" "1B58BC54CD0CB07A1C91B8D25339DA3B" \
                "E0A8F50EC76A04D5A96A175AA870EF63" "AA061FD394A67EAA4A88F12851E5C324" \
                "72B543B75FF6D542B05B6E61C809BA2D" "3225DA78CABFF85445AD4030B03EC0F3" \
                "132B32AF1069BD5B1DAAB020A7534E3C" "6A916607CD6A0894CEF26120A0A91DB9" \
                "713C386454B0C9EE7A508C441FF647C1" "F564FDD8D4BD3334450A156219775678" \
                "04B9EEE104E50ABD4CCD0452CD726D68" "D0112CE3234FCAAD35588AA9B22EE0B7" \
                "C25A3B717B03A5AD10FFA2EAD77CC0A7" "19D1888B53D612C38E795158E3FC1B11" \
                "749F3696F3AAD3C89ECEC548015F6B68" "4BCE2AB9FCC991B24BCA0E5E02F5A28B" \
                "7C0DEBB298FB5054CBFAF40D1321A4B8" "9AC80C9B6327573183277FAE27DCE33F" \
                "37C140C3F7382E25F7D50B8C9882EE49" "CF3DD51C019D90BCBD4A92224CE2D1DD" \
                "1AAEB76095519FE132D5E625E2E40919" "DBCA6BB633A1E8FE0F974DF85B803893" \
                "14FD9BDD0561F9FCBF84AAB5D4ADC5F0" "AFB8D5A6C4CCC646325BA0B1CEADDA39" \
                "8884337D54D724C4635408A4AC470D59" "D279869BF90140CAF3BCE287ECC57FA6" \
                "7793C7E1E026B02E089102C2D0C1C4B3" "A41F5C17165211CF9B1AF913C157E6E4" \
                "A8969DC2BE8A52AF306A4B4EADD9A759" "64F214ADB7EE08A9BFDD1D3DEF014591" \
                "4A6D751FEF7BAD2F55119D1DC0BFE0A5" "F424E882E860F9CC1068F2A82F793169" \
                "0C5EF4C456CE2B925890FEF87C62FDF7" "8E09EDE2A648F92884DE76ADCD5823BD" \
                "29C66991D0DA8F7EC458CEAA377AB9E3" "C2A9DF8384C2FF85F9662F6F39933C4C" \
                "1A4E8E57D67F11A342CE26E09D4643F9" "7D137E0C6D62961C761D7681A7FEBF3E" \
                "53B9D08B5D6920D0197C8F4A4A24EADE" "B42A4FD9CCE368FA8056006CAB43FD42" \
                "D281EE596286E734621AB658E489B9F5"]""");
    }

    @Test
    @DisplayName("and decrypting gives the plain text back")
    void decryptingGivesThePlainTextBack() {
        assertThat(answerTo("""
                p: open make port! [
                    scheme: 'crypt algorithm: 'AES-128-ECB direction: 'decrypt
                ]
                modify p 'key %s
                write p #{3AD77BB40D7A3660A89ECAF32466EF97}
                read p""".formatted(AES_128_KEY))).isEqualTo(NIST_BLOCK);
    }

    @Test
    @DisplayName("a key shorter than the cipher wants is padded with noughts")
    void aKeyShorterThanTheCipherWantsIsPaddedWithNoughts() {
        assertThat(through("AES-128-ECB", "none", "none", NIST_BLOCK))
                .isEqualTo("\"CF2EA38A123BE20765EB8C5C56CAF224\"");
        assertThat(through("AES-128-ECB", "#{2B}", "none", NIST_BLOCK))
                .isEqualTo("\"5FA1E453FAFD7C3CB3BE49F22A67BFD4\"");
        assertThat(through("AES-128-ECB", "#{2B7E151628AED2A6ABF7158809CF4F}",
                "none", NIST_BLOCK))
                .isEqualTo("\"83CFB16C86A639C75795F4B4D58A2620\"");
    }

    @Test
    @DisplayName("and a key longer than it wants is truncated")
    void aKeyLongerThanTheCipherWantsIsTruncated() {
        String exactly = through("AES-128-ECB", AES_128_KEY, "none", NIST_BLOCK);
        assertThat(exactly).isEqualTo("\"3AD77BB40D7A3660A89ECAF32466EF97\"");
        assertThat(through("AES-128-ECB",
                "#{2B7E151628AED2A6ABF7158809CF4F3CFF}", "none", NIST_BLOCK))
                .isEqualTo(exactly);
        assertThat(through("AES-128-ECB",
                "#{2B7E151628AED2A6ABF7158809CF4F3CFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF}",
                "none", NIST_BLOCK)).isEqualTo(exactly);
    }

    @Test
    @DisplayName("no vector is a vector of noughts, so the first block matches codebook")
    void noVectorIsAVectorOfNoughts() {
        assertThat(through("AES-128-CBC", AES_128_KEY, "none", NIST_BLOCK))
                .isEqualTo(through("AES-128-ECB", AES_128_KEY, "none", NIST_BLOCK));
    }

    @Test
    @DisplayName("a short vector is padded and a long one truncated")
    void aShortVectorIsPaddedAndALongOneTruncated() {
        assertThat(through("AES-128-CBC", AES_128_KEY, "#{0001}", NIST_BLOCK))
                .isEqualTo("\"EA1DBA6D8E336C34D4D584FB032B4021\"");
        assertThat(through("AES-128-CBC", AES_128_KEY,
                "#{000102030405060708090A0B0C0D0E0FFF}", NIST_BLOCK))
                .isEqualTo("\"7649ABAC8119B246CEE98E9B12E9197D\"");
    }

    @Test
    @DisplayName("ChaCha20 takes its counter from the end of the vector")
    void chaCha20TakesItsCounterFromTheEndOfTheVector() {
        String key =
                "#{000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F}";
        String nonceAndCounter = "#{000000090000004A0000000000000001}";
        assertThat(through("chacha20", key, nonceAndCounter,
                "#{00000000000000000000000000000000}"))
                .isEqualTo("\"10F1E7E4D13B5915500FDD1FA32071C4\"");
        assertThat(through("chacha20", key, "none",
                "#{00000000000000000000000000000000}"))
                .isEqualTo("\"39FD2B7DD9C5196A8DBD0377B8DC4A49\"");
    }

    @Test
    @DisplayName("and it pads to sixteen although it has no block to fill")
    void chaCha20PadsToSixteenAlthoughItHasNoBlock() {
        assertThat(answerTo("""
                p: open make port! [scheme: 'crypt algorithm: 'chacha20]
                modify p 'key #{000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F}
                modify p 'init-vector #{000000090000004A0000000000000001}
                write p #{00000000}
                enbase/flat take p 16"""))
                .isEqualTo("\"10F1E7E4D13B5915500FDD1FA32071C4\"");
    }

    @Test
    @DisplayName("DES and triple DES, in both modes")
    void desAndTripleDes() {
        String single = "#{0123456789ABCDEF}";
        String triple = "#{0123456789ABCDEF23456789ABCDEF01456789ABCDEF0123}";
        String now = "#{4E6F772069732074}";
        assertThat(through("des_ecb", single, "none", now))
                .isEqualTo("\"3FA40E8A984D4815\"");
        assertThat(through("des3_ecb", triple, "none", now))
                .isEqualTo("\"314F8327FA7A09A8\"");
        assertThat(through("des_cbc", single, "#{1234567890ABCDEF}", now))
                .isEqualTo("\"E5C7CDDE872BF27C\"");
        assertThat(through("des3_cbc", triple, "#{1234567890ABCDEF}", now))
                .isEqualTo("\"F3C0FF026C023089\"");
    }

    @Test
    @DisplayName("and DES holds back at eight bytes, not at sixteen")
    void desHoldsBackAtEightBytes() {
        assertThat(answerTo("""
                p: open make port! [scheme: 'crypt algorithm: 'des_ecb]
                modify p 'key #{0123456789ABCDEF}
                write p #{4E6F7720}
                first-read: read p
                reduce [first-read enbase/flat take p 16]"""))
                .isEqualTo("""
                        [_ "2E764270BE5A461A"]""");
    }

    @Test
    @DisplayName("GCM answers a cipher text and then its tag")
    void gcmAnswersACipherTextAndThenItsTag() {
        assertThat(gcmRun("encrypt", "#{1014F74310D1718D1CC8F65F033AAF83}",
                "#{6BB54C9FD83C12F5BA76CC83F7650D2C}", 16, "#{}", "#{}"))
                .isEqualTo("""
                        ["" "0B6B57DB309EFF920C8133B8691E0CAC"]""");
        assertThat(gcmRun("encrypt", "#{2397F163A0CB50B0E8C85F909B96ADC1}",
                "#{97A631F5F6FC928FFCE32EE2C92F5E50}", 15, "#{}", "#{}"))
                .isEqualTo("""
                        ["" "3B74CCA7BCDC07C8F8D4818DE714F2"]""");
    }

    @Test
    @DisplayName("a tag shorter than the JVM will issue is still answered")
    void aTagShorterThanTheJvmWillIssueIsStillAnswered() {
        assertThat(gcmRun("encrypt", "#{239C15492D6DEEC979E79236BACA4635}",
                "#{916B8B5417578FA83D2E9E9B8E2E7F6B}", 4, "#{}", COUNTING_BLOCK))
                .isEqualTo("""
                        ["4398CD8F05AE2B6F05E3B70EBD035EB8" "AE1081D3"]""");
    }

    @Test
    @DisplayName("GCM takes data to authenticate but not to encrypt")
    void gcmTakesDataToAuthenticateButNotToEncrypt() {
        assertThat(gcmRun("encrypt", "#{C939CC13397C1D37DE6AE0E1CB7DB1F7}",
                "#{B3D8CC017CBB89B39E0F67E2}", 16,
                "#{24825602BD12A984E0092D3E448EDA5F}",
                "#{C3B3C41F113A31B73D9A5CD432103069}"))
                .isEqualTo("""
                        ["5B7D2C86BFB5BA192B5F3F96E6453930" "1EBD3FDE595B9B955B0BC93841930F44"]""");
    }

    @Test
    @DisplayName("decrypting answers the tag rather than checking it")
    void decryptingAnswersTheTagRatherThanCheckingIt() {
        assertThat(gcmRun("decrypt", "#{C939CC13397C1D37DE6AE0E1CB7DB1F7}",
                "#{B3D8CC017CBB89B39E0F67E2}", 16,
                "#{24825602BD12A984E0092D3E448EDA5F}",
                "#{93FE7D9E9BFD10348A5606E5CAFA7354}"))
                .isEqualTo("""
                        ["0B30950735729B9A9C9365A71EAF7A0D" "4F584D5960EEB2CB2C3708FD0BF0FF23"]""");
    }

    @Test
    @DisplayName("taking without reading first answers the cipher text and the tag")
    void takingWithoutReadingFirstAnswersBoth() {
        assertThat(gcmWritten(16, "#{0011223344556677}", "take p"))
                .isEqualTo("\"2275F76D886B5A143FB42E0F80037654306F3C0DB04288D4\"");
        assertThat(gcmWritten(0, "#{0011223344556677}", "take p"))
                .isEqualTo("\"2275F76D886B5A14\"");
    }

    @Test
    @DisplayName("a second write answers only the bytes the second write added")
    void aSecondWriteAnswersOnlyItsOwnBytes() {
        assertThat(answerTo("""
                p: open make port! [scheme: 'crypt algorithm: 'AES-128-GCM]
                modify p 'key #{000102030405060708090A0B0C0D0E0F}
                modify p 'iv  #{0F0E0D0C0B0A090807060504}
                modify p 'tag-length 16
                write p #{0011223344556677}
                first-half: enbase/flat read p 16
                write p #{8899AABBCCDDEEFF}
                reduce [first-half enbase/flat read p 16]"""))
                .isEqualTo("""
                        ["2275F76D886B5A14" "EDBE6C03CDD01C8B"]""");
    }

    @Test
    @DisplayName("and the two halves together are what one write answers")
    void theTwoHalvesTogetherAreWhatOneWriteAnswers() {
        assertThat(gcmWritten(16, "#{00112233445566778899AABBCCDDEEFF}", "read p"))
                .isEqualTo("\"2275F76D886B5A14EDBE6C03CDD01C8B\"");
    }

    @Test
    @DisplayName("an authenticated cipher with no vector answers nothing at all")
    void anAuthenticatedCipherWithNoVectorAnswersNothing() {
        assertThat(answerTo("""
                p: open make port! [scheme: 'crypt algorithm: 'AES-128-GCM]
                modify p 'key #{000102030405060708090A0B0C0D0E0F}
                write p %s
                read p""".formatted(COUNTING_BLOCK))).isEqualTo("_");
        assertThat(through("AES-128-ECB",
                "#{000102030405060708090A0B0C0D0E0F}", "none", COUNTING_BLOCK))
                .isEqualTo("\"69C4E0D86A7B0430D8CDB78070B4C55A\"");
    }

    @Test
    @DisplayName("a tag length outside four to sixteen leaves nothing to take")
    void aTagLengthOutsideFourToSixteenLeavesNothing() {
        assertThat(gcmTagOf(4)).isEqualTo("#{AF70D249}");
        for (int refused : new int[] {1, 3, 17, -1}) {
            assertThat(gcmTagOf(refused)).as("tag length " + refused).isEqualTo("_");
        }
    }

    @Test
    @DisplayName("asking for no tag leaves nothing to take, not an empty binary")
    void askingForNoTagLeavesNothing() {
        assertThat(gcmTagOf(0)).isEqualTo("_");
    }

    private static String gcmTagOf(int tagLength) {
        return answerTo("""
                p: open make port! [scheme: 'crypt algorithm: 'AES-128-GCM]
                modify p 'key #{000102030405060708090A0B0C0D0E0F}
                modify p 'iv  #{0F0E0D0C0B0A090807060504}
                modify p 'tag-length %d
                write p #{0011}
                read p
                take p""".formatted(tagLength));
    }

    @Test
    @DisplayName("a tag length between writes keeps both, a vector keeps only the second")
    void aTagLengthDoesNotStartTheCipherAgainButAVectorDoes() {
        assertThat(gcmAcross("modify p 'tag-length 16")).isEqualTo("32");
        assertThat(gcmAcross("modify p 'iv  #{0F0E0D0C0B0A090807060504}"))
                .isEqualTo("16");
    }

    private static String gcmAcross(String between) {
        return answerTo("""
                p: open make port! [scheme: 'crypt algorithm: 'AES-128-GCM]
                modify p 'key #{000102030405060708090A0B0C0D0E0F}
                modify p 'iv  #{0F0E0D0C0B0A090807060504}
                modify p 'tag-length 16
                write p %s
                %s
                write p %s
                length? read p""".formatted(COUNTING_BLOCK, between, COUNTING_BLOCK));
    }

    @Test
    @DisplayName("the bytes to authenticate come off one write, not several")
    void theBytesToAuthenticateComeOffOneWrite() {
        assertThat(answerTo("""
                p: open make port! [scheme: 'crypt algorithm: 'AES-128-GCM]
                modify p 'key #{000102030405060708090A0B0C0D0E0F}
                modify p 'iv  #{0F0E0D0C0B0A090807060504}
                modify p 'tag-length 16
                modify p 'aad-length 4
                write p #{AABB}
                write p #{CCDD0102030405060708}
                enbase/flat read p 16"""))
                .isEqualTo("\"2160D058CB36\"");
    }

    @Test
    @DisplayName("ChaCha20 keeps nothing back once it has a block's worth")
    void chaCha20KeepsNothingBackOnceItHasABlock() {
        assertThat(answerTo("""
                c: open make port! [scheme: 'crypt algorithm: 'chacha20]
                modify c 'key #{000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F}
                modify c 'iv #{000000090000004A0000000000000001}
                write c #{000102030405060708090A0B0C0D0E0F1011121314}
                reduce [length? read c take c]"""))
                .isEqualTo("[21 _]");
    }

    @Test
    @DisplayName("a cipher that would not start answers nothing to update and take too")
    void aCipherThatWouldNotStartAnswersNothingToUpdateAndTake() {
        for (String asked : new String[] {"read p", "read update p", "take p"}) {
            assertThat(answerTo("""
                    p: open make port! [scheme: 'crypt algorithm: 'AES-128-GCM]
                    modify p 'key #{000102030405060708090A0B0C0D0E0F}
                    write p %s
                    %s""".formatted(COUNTING_BLOCK, asked)))
                    .as(asked).isEqualTo("_");
        }
    }

    private static String gcmWritten(int tagLength, String data, String asked) {
        return answerTo("""
                p: open make port! [scheme: 'crypt algorithm: 'AES-128-GCM]
                modify p 'key #{000102030405060708090A0B0C0D0E0F}
                modify p 'iv  #{0F0E0D0C0B0A090807060504}
                modify p 'tag-length %d
                write p %s
                enbase/flat %s 16""".formatted(tagLength, data, asked));
    }

    private static String gcmRun(String direction, String key, String vector,
            int tagLength, String toAuthenticate, String data) {

        return answerTo("""
                p: open make port! [scheme: 'crypt algorithm: 'AES-128-GCM]
                modify p 'direction '%s
                modify p 'key %s
                modify p 'iv %s
                modify p 'tag-length %d
                modify p 'aad-length length? %s
                write p %s
                write p %s
                reduce [enbase/flat read p 16 enbase/flat take p 16]"""
                .formatted(direction, key, vector, tagLength,
                        toAuthenticate, toAuthenticate, data));
    }
}
