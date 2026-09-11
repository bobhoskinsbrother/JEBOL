package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ChaCha20 with Poly1305, the last cipher REBOL's catalogue names that this
 * build can serve.
 *
 * <p>RFC 8439, and {@code chachapoly.c}. Two halves that are not alike:
 * ChaCha20 masks the message with a keystream, and Poly1305 authenticates the
 * result with arithmetic modulo the prime two to the hundred and thirtieth
 * less five. Nothing here is a block cipher, which is why none of the other
 * modes helped.
 *
 * <p>The port drives it in a shape no other cipher uses, and the shape is
 * TLS's rather than the cipher's: the first write is the header, and its first
 * eight bytes are folded into the tail of the starting vector to make the
 * nonce. In TLS those eight bytes are a record's sequence number, so no two
 * records under one key are ever counted the same way.
 *
 * <p>Which means the header does two jobs and produces nothing, and only the
 * first eight bytes of it reach the nonce -- so a header of eight bytes and a
 * header of nine give the same cipher text and different tags.
 *
 * <p>Every expectation here was read off a real 3.22.5 first, and the exchange
 * below is REBOL's own test: a client and a server with their own keys, one
 * record enciphered, sent, and deciphered at the far end.
 */
class CryptPortChaChaWithPoly1305FromTheSourceTest {

    private static final String CLIENT_KEY =
            "#{438D7027FD611C1A5CD532D1151665EA3BB925CF1F37453C109790B604E7A0C4}";

    private static final String CLIENT_VECTOR = "#{9F45E14C213A3719186DDF50}";

    /** A TLS record header: sequence number, type, version and length. */
    private static final String HEADER = "#{000000000000000016030300 10}";

    private static final String MESSAGE = "#{1400000C89F6A49D54518857D140BE74}";

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    /** One record through one port: the cipher text and then the tag. */
    private static String sealed(String direction, String vector,
            String header, String message) {

        return answerTo("""
                p: open crypt:chacha20-poly1305%s
                modify p 'key %s
                modify p 'iv %s
                write p %s
                write p %s
                reduce [enbase/flat read p 16 enbase/flat take p 16]"""
                .formatted(direction, CLIENT_KEY, vector, header, message));
    }

    @Test
    @DisplayName("a record enciphered, and its tag after it")
    void aRecordEncipheredAndItsTagAfterIt() {
        assertThat(sealed("", CLIENT_VECTOR, HEADER, MESSAGE)).isEqualTo("""
                ["AE84B0499E0B7837027C6FD712A68894" \
                "3604F4477DCA0C6856559D1DD2EEC03C"]""");
    }

    /**
     * The other end of REBOL's own exchange. Deciphering hands the tag back
     * rather than checking it, the same as counting with Galois and unlike
     * counter with CBC-MAC, so the caller compares.
     */
    @Test
    @DisplayName("and the far end deciphers it and computes the same tag")
    void andTheFarEndDeciphersItAndComputesTheSameTag() {
        assertThat(sealed("#decrypt", CLIENT_VECTOR, HEADER,
                "#{AE84B0499E0B7837027C6FD712A68894}")).isEqualTo("""
                ["1400000C89F6A49D54518857D140BE74" \
                "3604F4477DCA0C6856559D1DD2EEC03C"]""");
    }

    /**
     * Only the first eight bytes of the header reach the nonce, and they are
     * folded into its last eight. So eight bytes and nine give the same cipher
     * text -- and the tags differ, because the tag covers the whole header.
     */
    @Test
    @DisplayName("eight header bytes and nine derive the same nonce")
    void eightHeaderBytesAndNineDeriveTheSameNonce() {
        String ofEight = sealed("", CLIENT_VECTOR,
                "copy/part #{0102030405060708090A} 8", MESSAGE);
        String ofNine = sealed("", CLIENT_VECTOR,
                "copy/part #{0102030405060708090A} 9", MESSAGE);
        assertThat(ofEight).isEqualTo("""
                ["3EFAE7A81ED417E6B95D6B3F36C20ECA" \
                "D9B96245FE9EE3EEAD6F0D3108BAA3EA"]""");
        assertThat(ofNine).isEqualTo("""
                ["3EFAE7A81ED417E6B95D6B3F36C20ECA" \
                "16A402F21B68812E9F88C16D00185351"]""");
    }

    /**
     * A header shorter than eight is folded into the tail all the same, which
     * is what "into the tail" means rather than "from the front". Seven bytes
     * of {@code 01..07} land in the same places as eight bytes of
     * {@code 00..07}, so those two give the same cipher text -- the leading
     * nought folds nothing.
     */
    @Test
    @DisplayName("a short header is folded into the tail, not the front")
    void aShortHeaderIsFoldedIntoTheTail() {
        assertThat(sealed("", CLIENT_VECTOR,
                "copy/part #{0102030405060708090A} 7", MESSAGE)).isEqualTo("""
                ["EB9B9791C05FCA9FB0F80D67A31E61BB" \
                "AC781CCBC9BADA64E3E1004118B9F934"]""");
        assertThat(sealed("", CLIENT_VECTOR,
                "#{000102030405060708090A0B0C0D0E0F10}", MESSAGE)).isEqualTo("""
                ["EB9B9791C05FCA9FB0F80D67A31E61BB" \
                "19A12C8955885DAB99A6D3A3130D4A96"]""");
    }

    @Test
    @DisplayName("a header of one byte, and of two")
    void aHeaderOfOneByteAndOfTwo() {
        assertThat(sealed("", CLIENT_VECTOR,
                "copy/part #{0102030405060708090A} 1", MESSAGE)).isEqualTo("""
                ["E774CCE3BF9C2F420573EEFEB8B2852B" \
                "3817738484399147EC3DF5302C888AAC"]""");
        assertThat(sealed("", CLIENT_VECTOR,
                "copy/part #{0102030405060708090A} 2", MESSAGE)).isEqualTo("""
                ["DFCBC35C2E7BEBE69FD421AE81ADDE05" \
                "AE4EB59DA1AE7F949686389051A44744"]""");
    }

    @Test
    @DisplayName("and no starting vector at all is a vector of noughts")
    void andNoStartingVectorAtAllIsAVectorOfNoughts() {
        assertThat(answerTo("""
                p: open crypt:chacha20-poly1305
                modify p 'key %s
                write p #{0000000000000000}
                write p %s
                reduce [enbase/flat read p 16 enbase/flat take p 16]"""
                .formatted(CLIENT_KEY, MESSAGE))).isEqualTo("""
                ["72503FABADC107839C17B07770C46301" \
                "47D6CB2D8B5249F734B02DB89B2BEDAF"]""");
    }

    /**
     * A header and nothing after it still has a tag, over the header alone.
     * The port answers nothing to read, because a header is not enciphered.
     */
    @Test
    @DisplayName("a header with no message has a tag and nothing to read")
    void aHeaderWithNoMessageHasATagAndNothingToRead() {
        assertThat(answerTo("""
                p: open crypt:chacha20-poly1305
                modify p 'key %s
                modify p 'iv %s
                write p %s
                reduce [read p enbase/flat take p 16]"""
                .formatted(CLIENT_KEY, CLIENT_VECTOR, HEADER))).isEqualTo("""
                [_ "18D837E8A06F1C9B8BE1EFC280E17232"]""");
    }

    /**
     * A write of nothing at all is not a header: the port takes no notice of
     * it, so the write after it becomes the header instead and the message
     * never gets enciphered. Which is a trap rather than a feature, and it is
     * what a real 3.22.5 does.
     */
    @Test
    @DisplayName("a header of no bytes is not a header, and swallows the message")
    void aHeaderOfNoBytesIsNotAHeader() {
        assertThat(answerTo("""
                p: open crypt:chacha20-poly1305
                modify p 'key %s
                modify p 'iv %s
                write p #{}
                write p %s
                read p""".formatted(CLIENT_KEY, CLIENT_VECTOR, MESSAGE)))
                .isEqualTo("_");
    }

    @Test
    @DisplayName("the catalogue names it, and it is the twenty-ninth")
    void theCatalogueNamesIt() {
        assertThat(answerTo(
                "true? find system/catalog/ciphers 'chacha20-poly1305"))
                .isEqualTo("#(true)");
    }
}
