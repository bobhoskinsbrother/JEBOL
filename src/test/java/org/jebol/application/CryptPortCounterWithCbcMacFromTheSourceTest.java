package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Counter with CBC-MAC, the second authenticated mode and the one the JVM has
 * not got.
 *
 * <p>{@code ccm.c}, and NIST SP 800-38C. No JVM provider offers it, so it is
 * built here out of the AES the JVM does have: the tag is a CBC-MAC over a
 * first block naming the lengths, then the header, then the message; the
 * cipher text is the message counted through the same AES; and the two are
 * joined by masking the tag with the block at counter nought.
 *
 * <p>It is the mode TLS 1.3 carries alongside Galois counter mode, and the one
 * constrained hardware reaches for -- CCMP in WPA2, Bluetooth Low Energy,
 * Zigbee, and DTLS for CoAP.
 *
 * <p>Two things set it apart from counting with Galois, and both are pinned
 * below. It will only issue the <em>even</em> tag lengths from four to
 * sixteen, because the length is built into the first block it authenticates
 * as {@code (t - 2) / 2} in three bits and an odd one has no spelling. And it
 * checks the tag itself while deciphering, answering nothing when it
 * disagrees, where Galois hands the tag back and compares nothing.
 *
 * <p>Every expectation here was read off a real 3.22.5 before it was written,
 * and the first is RFC 3610's own vector.
 */
class CryptPortCounterWithCbcMacFromTheSourceTest {

    private static final String KEY = "#{C0C1C2C3C4C5C6C7C8C9CACBCCCDCECF}";

    private static final String NONCE = "#{00000003020100A0A1A2A3A4A5}";

    private static final String HEADER = "#{0001020304050607}";

    private static final String MESSAGE =
            "#{08090A0B0C0D0E0F101112131415161718191A1B1C1D1E}";

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    /** One message through one port, as hexadecimal, or the answer itself. */
    private static String sealed(String algorithm, String key, String nonce,
            int tagLength, String header, String message) {

        return answerTo("""
                p: open make port! [scheme: 'crypt algorithm: '%s]
                modify p 'key %s
                modify p 'iv %s
                modify p 'tag-length %d
                modify p 'aad-length length? %s
                write p join %s %s
                either binary? r: take p [enbase/flat r 16] [r]"""
                .formatted(algorithm, key, nonce, tagLength,
                        header, header, message));
    }

    private static String sealed(int tagLength) {
        return sealed("AES-128-CCM", KEY, NONCE, tagLength, HEADER, MESSAGE);
    }

    /** RFC 3610's first packet vector, header and all. */
    @Test
    @DisplayName("the RFC 3610 vector, cipher text and tag together")
    void theRfc3610Vector() {
        assertThat(sealed(8)).isEqualTo("""
                {588C979A61C663D2F066D0C2C0F989806D5F6B61DAC38417E8D12CFDF926E0}""");
    }

    @Test
    @DisplayName("and it deciphers what it enciphered")
    void andItDeciphersWhatItEnciphered() {
        assertThat(answerTo("""
                p: open make port! [scheme: 'crypt algorithm: 'AES-128-CCM]
                modify p 'direction 'decrypt
                modify p 'key %s
                modify p 'iv %s
                modify p 'tag-length 8
                modify p 'aad-length length? %s
                write p join %s \
                #{588C979A61C663D2F066D0C2C0F989806D5F6B61DAC38417E8D12CFDF926E0}
                enbase/flat read p 16""".formatted(KEY, NONCE, HEADER, HEADER)))
                .isEqualTo("\"08090A0B0C0D0E0F101112131415161718191A1B1C1D1E\"");
    }

    @Test
    @DisplayName("at all three key widths")
    void atAllThreeKeyWidths() {
        assertThat(sealed("AES-192-CCM",
                "#{C0C1C2C3C4C5C6C7C8C9CACBCCCDCECFD0D1D2D3D4D5D6D7}",
                NONCE, 8, HEADER, MESSAGE)).isEqualTo("""
                {579FB86EDDB4A64AAE5FE96DBD75440533A9FC3A84573667AEC80AC588AB16}""");
        assertThat(sealed("AES-256-CCM",
                "#{C0C1C2C3C4C5C6C7C8C9CACBCCCDCECFD0D1D2D3D4D5D6D7D8D9DADBDCDDDEDF}",
                NONCE, 8, HEADER, MESSAGE)).isEqualTo("""
                {59615510A7C43BFB123D636B4613C03C6CE26907102A3FB5572A172D4916D5}""");
    }

    /**
     * The tag length is built into the first block that gets authenticated, so
     * it changes the tag rather than merely trimming it. Four is the shortest
     * and sixteen the longest, and the message is twenty-three bytes, so the
     * whole answer is twenty-three plus the tag.
     */
    @Test
    @DisplayName("only the even lengths from four to sixteen are issued")
    void onlyTheEvenLengthsFourToSixteenAreIssued() {
        assertThat(sealed(4)).isEqualTo("""
                {588C979A61C663D2F066D0C2C0F989806D5F6B61DAC38450198BBC}""");
        assertThat(sealed(6)).isEqualTo("""
                {588C979A61C663D2F066D0C2C0F989806D5F6B61DAC384BA92D47A5283}""");
        assertThat(sealed(10)).isEqualTo("""
                {588C979A61C663D2F066D0C2C0F989806D5F6B61DAC384FEA4B050E8727D0D2CB3}""");
        assertThat(sealed(12)).isEqualTo("""
                {588C979A61C663D2F066D0C2C0F989806D5F6B61DAC38448656D11AAAAF12CB8DFF99E}""");
        assertThat(sealed(14)).isEqualTo("""
                {588C979A61C663D2F066D0C2C0F989806D5F6B61DAC3844C776147E6A6CC97BF5EF3D93D67}""");
        assertThat(sealed(16)).isEqualTo("""
                {588C979A61C663D2F066D0C2C0F989806D5F6B61DAC384509DA654E32DEAC369C2DAE7133CB08D}""");
    }

    /**
     * A shorter tag is a different number rather than the first bytes of a
     * longer one, which is the opposite of counting with Galois. The four byte
     * tag is {@code 50198BBC} and the sixteen byte tag begins {@code 509DA654}
     * -- one byte in common and then nothing.
     */
    @Test
    @DisplayName("a shorter tag is a different number, not a prefix of a longer one")
    void aShorterTagIsADifferentNumberNotAPrefix() {
        assertThat(sealed(4)).endsWith("50198BBC}");
        assertThat(sealed(16)).endsWith("509DA654E32DEAC369C2DAE7133CB08D}");
    }

    @Test
    @DisplayName("an odd length, or one outside four to sixteen, leaves nothing")
    void aTagLengthThisModeCannotSpellLeavesNothing() {
        for (int refused : new int[] {2, 3, 5, 18, -2}) {
            assertThat(sealed(refused)).as("tag length " + refused).isEqualTo("_");
        }
    }

    /**
     * Asking for no tag at all is the starred form of the mode, which counts
     * the message through and authenticates nothing. The cipher text is the
     * same twenty-three bytes as before, because the tag length does not
     * reach the counting.
     */
    @Test
    @DisplayName("asking for no tag gives the cipher text alone")
    void askingForNoTagGivesTheCipherTextAlone() {
        assertThat(answerTo("""
                p: open make port! [scheme: 'crypt algorithm: 'AES-128-CCM]
                modify p 'key %s
                modify p 'iv %s
                write p %s
                enbase/flat take p 16""".formatted(KEY, NONCE, MESSAGE)))
                .isEqualTo("\"588C979A61C663D2F066D0C2C0F989806D5F6B61DAC384\"");
    }

    /**
     * The header reaches the tag and not the cipher text, so dropping it
     * leaves the first twenty-three bytes where they were and changes the
     * eight after them.
     */
    @Test
    @DisplayName("the header changes the tag and not the cipher text")
    void theHeaderChangesTheTagAndNotTheCipherText() {
        assertThat(sealed("AES-128-CCM", KEY, NONCE, 8, "#{}", MESSAGE))
                .isEqualTo("""
                        {588C979A61C663D2F066D0C2C0F989806D5F6B61DAC3847C2051A7AE200BCF}""");
    }

    @Test
    @DisplayName("a message of nothing at all is just a tag")
    void aMessageOfNothingAtAllIsJustATag() {
        assertThat(sealed("AES-128-CCM", KEY, NONCE, 8, HEADER, "#{}"))
                .isEqualTo("\"E4288AC378000FF5\"");
    }

    /**
     * The nonce and the count of blocks share sixteen bytes, so a longer nonce
     * leaves fewer bytes to count with and seven to thirteen is where that
     * trade runs out. Four, six and seven all differ, so a short nonce is read
     * with noughts after it rather than refused; thirteen, fourteen and
     * sixteen agree, so a long one has its tail ignored.
     */
    @Test
    @DisplayName("the nonce is clamped to seven bytes and to thirteen")
    void theNonceIsClampedToSevenAndThirteen() {
        assertThat(nonceOf(4)).isEqualTo("""
                {1CD90724DC723D102B8ABBC4E042A2E51F4BAE8700D87AE12123FEAB5E30DB}""");
        assertThat(nonceOf(6)).isEqualTo("""
                {EB2B0D1EDE2CFC45EB7C8736C3216EA55C952B59E8BFBDD34ADBF32BA3B27A}""");
        assertThat(nonceOf(7)).isEqualTo("""
                {FB8F427FD3302DA88C5EB84188F9DA7F9C423C5A339C1020F12D4F75750A47}""");
        assertThat(nonceOf(12)).isEqualTo("""
                {9152A3DFFC66B90BDC05DD2664B1CCFB07478C70F890F26C974850A699B43F}""");
        String thirteen = """
                {CC0A1146652768E4B88AA3BA635A671A9D778A18BAFDDF499BF1033BF1F170}""";
        assertThat(nonceOf(13)).isEqualTo(thirteen);
        assertThat(nonceOf(14)).isEqualTo(thirteen);
        assertThat(nonceOf(16)).isEqualTo(thirteen);
    }

    private static String nonceOf(int howManyBytes) {
        return sealed("AES-128-CCM", KEY,
                "copy/part #{000102030405060708090A0B0C0D0E0F} " + howManyBytes,
                8, HEADER, MESSAGE);
    }

    @Test
    @DisplayName("and no vector at all is a vector of noughts")
    void andNoVectorAtAllIsAVectorOfNoughts() {
        assertThat(sealed("AES-128-CCM", KEY, "none", 8, HEADER, MESSAGE))
                .isEqualTo("""
                        {F24AB18BC56570F758B1A55699A25AFDB4A17A0F42FB3EDFBBBEB60179BA30}""");
    }

    /**
     * The part that differs from counting with Galois: this mode compares the
     * tag for itself and hands nothing over when it disagrees, so a careless
     * caller cannot use plain text that was never vouched for. One byte
     * changed anywhere -- in the tag or in the cipher text -- is caught.
     */
    @Test
    @DisplayName("a tag that disagrees gives nothing back, plain text included")
    void aTagThatDisagreesGivesNothingBack() {
        assertThat(deciphering("""
                #{588C979A61C663D2F066D0C2C0F989806D5F6B61DAC38417E8D12CFDFFFFFFF}"""))
                .isEqualTo("_");
        assertThat(deciphering("""
                #{598C979A61C663D2F066D0C2C0F989806D5F6B61DAC38417E8D12CFDF926E0}"""))
                .isEqualTo("_");
    }

    private static String deciphering(String sealedBytes) {
        return answerTo("""
                p: open make port! [scheme: 'crypt algorithm: 'AES-128-CCM]
                modify p 'direction 'decrypt
                modify p 'key %s
                modify p 'iv %s
                modify p 'tag-length 8
                modify p 'aad-length length? %s
                write p join %s %s
                read p""".formatted(KEY, NONCE, HEADER, HEADER, sealedBytes));
    }
}
