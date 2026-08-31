package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * xxHash, the thirty-two and sixty-four bit forms, written out because the JVM
 * has not got them.
 *
 * <p>Not cryptographic, and REBOL lists them beside the digests anyway: a
 * caller reaches for one to tell whether two blocks differ, not to keep a
 * secret.
 *
 * <p>Checked against the published vectors and at every length that changes
 * which branch runs -- under a block, exactly a block, a block and a tail --
 * because those boundaries are where a hash implementation goes wrong.
 */
class XxHashFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("the published thirty-two bit vectors")
    void theThirtyTwoBitVectors() {
        assertThat(answerTo("""
                collect [
                    foreach text ["" "a" "abc" "message digest"][
                        keep checksum text 'xxh32
                    ]
                ]""")).isEqualTo(
                "[#{02CC5D05} #{550D7456} #{32D153FF} #{7C948494}]");
    }

    @Test
    @DisplayName("the published sixty-four bit vectors")
    void theSixtyFourBitVectors() {
        assertThat(answerTo("""
                collect [
                    foreach text ["" "a" "abc" "message digest"][
                        keep checksum text 'xxh64
                    ]
                ]""")).isEqualTo(
                "[#{EF46DB3751D8E999} #{D24EC4F1A98C6E5B}"
                        + " #{44BC2CF5AD770999} #{066ED728FCEEB3BE}]");
    }

    @Test
    @DisplayName("a message long enough for the four accumulators to run")
    void aMessageOverABlock() {
        assertThat(answerTo("""
                reduce [
                    checksum
                        "12345678901234567890123456789012345678901234567890123456789012345678901234567890"
                        'xxh32
                    checksum
                        "12345678901234567890123456789012345678901234567890123456789012345678901234567890"
                        'xxh64
                ]""")).isEqualTo("[#{9C05F475} #{E04A477F19EE145D}]");
    }

    @Test
    @DisplayName("the answer is four bytes wide for one and eight for the other")
    void theWidthsDiffer() {
        assertThat(answerTo("""
                reduce [
                    length? checksum "abc" 'xxh32
                    length? checksum "abc" 'xxh64
                ]""")).isEqualTo("[4 8]");
    }

    @Test
    @DisplayName("both are in the catalogue, so a script can ask whether they are there")
    void bothAreInTheCatalogue() {
        assertThat(answerTo("""
                reduce [
                    true? find system/catalog/checksums 'xxh32
                    true? find system/catalog/checksums 'xxh64
                ]""")).isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("a port sums across writes for either of them")
    void aPortSumsAcrossWrites() {
        assertThat(answerTo("""
                collect [
                    foreach method [xxh32 xxh64][
                        port: open to url! join "checksum:" form method
                        write port #{0BAD}
                        write port #{0BAD}
                        keep (checksum #{0BAD0BAD} method) = read port
                    ]
                ]""")).isEqualTo("[#(true) #(true)]");
    }
}
