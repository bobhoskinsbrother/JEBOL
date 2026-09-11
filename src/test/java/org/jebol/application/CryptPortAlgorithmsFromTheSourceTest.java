package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ciphers a cipher port serves, each against a published vector.
 *
 * <p>{@code Crypt_Init} and {@code Crypt_Crypt} in {@code p-crypt.c}. This
 * build serves the fourteen the JVM carries: AES in three key widths across
 * electronic codebook, cipher block chaining and Galois counter mode, then
 * ChaCha20 and four spellings of DES. REBOL's own catalogue holds forty-two,
 * and the missing twenty-eight are Camellia, ARIA and counter-with-CBC-MAC,
 * none of which the JVM has and all of which are goal 4b.
 *
 * <p>A name in {@code system/catalog/ciphers} is a promise a script reads
 * before it chooses, so the catalogue holds what this port really serves and
 * nothing else.
 *
 * <p>Two quirks worth knowing before reading the assertions. A key shorter
 * than the cipher wants is padded with noughts rather than refused, and one
 * longer is truncated -- so a sixteen byte key and a thirty-two byte key whose
 * first sixteen bytes match give AES-128 the same answer. And ChaCha20 takes
 * its block counter from bytes twelve to fifteen of the starting vector, so
 * the vector is a twelve byte nonce and a four byte counter run together.
 *
 * <p>Every expectation here was read off a real 3.22.5 before it was written.
 */
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

    /**
     * One message through one port, as hexadecimal. The key and the vector go
     * on with MODIFY rather than in the specification, because a specification
     * block is evaluated as an object and a field set from a word of its own
     * name would read the field rather than the variable.
     */
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
                aes-256-cbc aes-128-gcm aes-192-gcm aes-256-gcm chacha20 \
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

    /** FIPS-197 appendix C, one vector per key width. */
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

    /** SP 800-38A section F.2, the first vector of each key width. */
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

    /**
     * The invariant the spec states over every cipher in the catalogue, walked
     * rather than sampled: one message through each of the fourteen and back.
     *
     * <p>Sixteen bytes on purpose. It is a whole number of blocks for the
     * eight byte ciphers and the sixteen byte ones alike, so nothing is padded
     * and the answer coming back is the message rather than the message and
     * some noughts.
     */
    @Test
    @DisplayName("every cipher in the catalogue decrypts what it encrypted")
    void everyCipherDecryptsWhatItEncrypted() {
        assertThat(answerTo("""
                key: #{000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F}
                vec: #{0F0E0D0C0B0A09080706050403020100}
                plain: %s
                every-one: true
                foreach named system/catalog/ciphers [
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
                every-one""".formatted(NIST_BLOCK))).isEqualTo("#(true)");
    }

    /**
     * And the bytes in between are a real 3.22.5's bytes, not merely ones this
     * port agrees with itself about. All fourteen were compared against
     * {@code ./r3-head} under the same key and vector.
     */
    @Test
    @DisplayName("and the cipher text between is what a real 3.22.5 writes")
    void theCipherTextBetweenIsWhatARealRebolWrites() {
        assertThat(answerTo("""
                key: #{000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F}
                vec: #{0F0E0D0C0B0A09080706050403020100}
                collect [
                    foreach named system/catalog/ciphers [
                        c: open make port! [scheme: 'crypt algorithm: named]
                        modify c 'key key   modify c 'init-vector vec
                        write c %s
                        keep enbase/flat read c 16
                    ]
                ]""".formatted(NIST_BLOCK))).isEqualTo("""
                ["47C58D5E21CAAF840D015B7D9B910981" "1B58BC54CD0CB07A1C91B8D25339DA3B" \
                "E0A8F50EC76A04D5A96A175AA870EF63" "AA061FD394A67EAA4A88F12851E5C324" \
                "72B543B75FF6D542B05B6E61C809BA2D" "3225DA78CABFF85445AD4030B03EC0F3" \
                "F564FDD8D4BD3334450A156219775678" "04B9EEE104E50ABD4CCD0452CD726D68" \
                "D0112CE3234FCAAD35588AA9B22EE0B7" "1A4E8E57D67F11A342CE26E09D4643F9" \
                "7D137E0C6D62961C761D7681A7FEBF3E" "53B9D08B5D6920D0197C8F4A4A24EADE" \
                "B42A4FD9CCE368FA8056006CAB43FD42" "D281EE596286E734621AB658E489B9F5"]""");
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

    /**
     * A key too short is padded with noughts and a key too long is truncated,
     * neither of which is refused. Four lengths either side of sixteen, and
     * the answers say which happened: the short ones all differ, and the long
     * ones all match the exact key.
     */
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

    /**
     * No starting vector at all is a vector of noughts, which makes the first
     * block of chaining the same as electronic codebook. A short vector is
     * padded and a long one truncated, exactly as the key is.
     */
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

    /**
     * RFC 8439 section 2.4.2. The vector is a twelve byte nonce followed by a
     * four byte block counter read most significant first, so the counter here
     * is one and the keystream is the second block of the example.
     *
     * <p>And the port pads to sixteen even though ChaCha20 is a stream cipher
     * with no block to fill: four bytes written and taken give the same
     * sixteen bytes as sixteen written and read.
     */
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

    /**
     * The classic vector: "Now is t" under the key {@code 0123456789ABCDEF}.
     * DES has an eight byte block where AES has sixteen, which is the only
     * other block size this build serves and therefore the only other place
     * the holding-back can be wrong.
     */
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

    /**
     * Galois counter mode answers a cipher text and then a tag, which is what
     * makes READ and TAKE different things on the same port rather than two
     * names for one.
     *
     * <p>The tag is truncated to whatever length was asked for. Fifteen bytes
     * is below what the JVM's own parameter object accepts and four is far
     * below it, so both are computed whole and cut down.
     */
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

    /**
     * Decrypting hands the computed tag back rather than checking it. The
     * caller compares, which is what REBOL's own test does, and it is why a
     * wrong tag here is a wrong answer rather than an exception.
     */
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

    /**
     * TAKE on its own hands back the cipher text and the tag together, because
     * UPDATE appends the tag to the buffer rather than replacing what is in
     * it: {@code Extend_Series(bin, ctx->tag_len)} then
     * {@code SERIES_TAIL(bin) += ctx->tag_len}.
     *
     * <p>Which only shows when nothing read first. REBOL's own GCM test reads
     * and then takes, so the cipher text has already left and the take answers
     * a tag alone -- and a port that threw the cipher text away instead would
     * pass that test and lose the message here.
     */
    @Test
    @DisplayName("taking without reading first answers the cipher text and the tag")
    void takingWithoutReadingFirstAnswersBoth() {
        assertThat(gcmWritten(16, "#{0011223344556677}", "take p"))
                .isEqualTo("\"2275F76D886B5A143FB42E0F80037654306F3C0DB04288D4\"");
        assertThat(gcmWritten(0, "#{0011223344556677}", "take p"))
                .isEqualTo("\"2275F76D886B5A14\"");
    }

    /**
     * And a second write answers only what the second write added. A port that
     * re-enciphered everything gathered so far would hand the first eight
     * bytes back twice, and twenty-four bytes would come out for sixteen in.
     */
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

    /**
     * An authenticated cipher with no starting vector has no answer at all: a
     * repeated vector under one key is what breaks the mode outright, so there
     * is no vector of noughts to fall back on the way the block ciphers have.
     *
     * <p>And it answers nothing rather than raising, which is what the C does
     * by remembering the failure in {@code ctx->error} and letting
     * {@code A_READ} return none. The block ciphers take a vector of noughts
     * and carry on, which is the contrast worth having beside it.
     */
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

    /**
     * A tag length outside four to sixteen leaves the cipher with no answer.
     *
     * <p>{@code mbedtls_gcm_finish} refuses a length outside that range, the
     * failure is remembered in {@code ctx->error}, and READ answers none from
     * then on. So one, three, seventeen and a negative all come back as
     * nothing, and four is the shortest tag there is.
     */
    @Test
    @DisplayName("a tag length outside four to sixteen leaves nothing to take")
    void aTagLengthOutsideFourToSixteenLeavesNothing() {
        assertThat(gcmTagOf(4)).isEqualTo("#{AF70D249}");
        for (int refused : new int[] {1, 3, 17, -1}) {
            assertThat(gcmTagOf(refused)).as("tag length " + refused).isEqualTo("_");
        }
    }

    /**
     * And asking for no tag at all leaves nothing to take either, rather than
     * an empty run of bytes. The C only computes one {@code if (ctx->tag_len)},
     * so nothing new becomes ready and the port stays empty.
     */
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

    /**
     * Setting the tag length does not start the cipher again, where setting
     * the starting vector does.
     *
     * <p>The C assigns {@code ctx->tag_len} and restarts nothing, while
     * {@code init_crypt_iv} sets the state back to needing initialisation. So
     * a tag length set between two writes keeps both blocks and a vector set
     * between them throws the first away. This settles the open question the
     * spec used to carry.
     */
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

    /**
     * The bytes to authenticate come off the front of one write, and a write
     * too short to hold them is thrown away whole.
     *
     * <p>{@code if (ctx->state == CRYPT_PORT_NO_DATA && ctx->aad_len)} acts
     * only while nothing has been enciphered yet, and the line below it
     * returns an error when that write is shorter than the header. So two
     * bytes then ten leaves the first two discarded, the next four read as the
     * header and six enciphered -- not four bytes of header gathered across
     * the pair.
     *
     * <p>This loses data with no error a caller can see, and it is what a real
     * 3.22.5 does.
     */
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

    /**
     * ChaCha20's block of sixteen only decides whether the cipher runs at all.
     * Once it has that much it takes everything, tail included, because the C
     * returns early below a block and its ChaCha20 arm then consumes the whole
     * input.
     *
     * <p>So twenty-one bytes answer twenty-one and leave nothing, where a
     * block cipher would answer sixteen and hold five.
     */
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

    /**
     * A cipher that would not start answers nothing to UPDATE and TAKE as well
     * as to READ, which is the whole of what {@code ctx->error} does: it is
     * read once, above the switch on what was asked.
     */
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
