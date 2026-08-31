package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The port that hashes what is written to it, {@code checksum://}.
 *
 * <p>{@code Checksum_Actor} in {@code p-checksum.c}. What makes it worth
 * having rather than a call to CHECKSUM is that the sum is built up across
 * writes, so a file too large to hold can be summed a block at a time.
 *
 * <p>Reading must not end the sum, and that is the part most easily got wrong:
 * the C says so in a comment beside the copy it makes, and a port read twice
 * has to answer the same thing both times and still accept more writes
 * afterwards.
 */
class ChecksumPortFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("raised: try [" + source + "] raised/id");
    }

    @Test
    @DisplayName("the port opens, and opens open")
    void thePortOpens() {
        assertThat(answerTo("""
                port: open checksum://
                reduce [port? port open? port]""")).isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("the method defaults to MD5 however the port is named")
    void theMethodDefaultsToMd5() {
        assertThat(answerTo("""
                bare: open checksum://
                named: open checksum:md5
                worded: open 'checksum
                reduce [bare/spec/method named/spec/method worded/spec/method]"""))
                .isEqualTo("[md5 md5 md5]");
    }

    @Test
    @DisplayName("a method in the URL is the method the port uses")
    void aMethodInTheUrlIsUsed() {
        assertThat(answerTo("""
                first-port: open checksum:sha1
                second-port: open checksum:sha256
                third-port: open checksum:sha3-512
                reduce [
                    first-port/spec/method
                    second-port/spec/method
                    third-port/spec/method
                ]""")).isEqualTo("[sha1 sha256 sha3-512]");
    }

    @Test
    @DisplayName("WRITE answers the port, so writes chain")
    void writeAnswersThePort() {
        assertThat(answerTo("""
                port? write open checksum:// #{0BAD}""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("one write sums what CHECKSUM sums")
    void oneWriteMatchesChecksum() {
        assertThat(answerTo("""
                port: open checksum://
                write port #{0BAD}
                (checksum #{0BAD} 'md5) = read port""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("two writes sum the two runs joined, which is the point of the port")
    void twoWritesSumTheJoin() {
        assertThat(answerTo("""
                port: open checksum://
                write port #{0BAD}
                write port #{0BAD}
                (checksum #{0BAD0BAD} 'md5) = read port""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("reading twice answers the same thing, because READ copies the state")
    void readingTwiceAnswersTheSame() {
        assertThat(answerTo("""
                port: open checksum://
                write port #{0BAD}
                first-read: read port
                reduce [first-read = read port first-read = read port]"""))
                .isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("and writing after a read carries on rather than starting again")
    void writingAfterAReadCarriesOn() {
        assertThat(answerTo("""
                port: open checksum://
                write port #{0BAD}
                read port
                write port #{0BAD}
                (checksum #{0BAD0BAD} 'md5) = read port""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("CLOSE shuts the port")
    void closeShutsThePort() {
        assertThat(answerTo("""
                port: open checksum://
                not open? close port""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("reading a closed port answers nothing rather than raising")
    void readingAClosedPortAnswersNothing() {
        assertThat(answerTo("""
                port: open checksum://
                write port #{0BAD}
                close port
                none? read port""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("opening an open port throws away what was written to it")
    void openingAgainRestartsTheSum() {
        assertThat(answerTo("""
                port: open checksum://
                write port #{CAFE}
                write open port #{0BAD}
                (checksum #{0BAD} 'md5) = read port""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("UPDATE answers the port and leaves the sum in port/data")
    void updateLeavesTheSumInData() {
        assertThat(answerTo("""
                port: open checksum://
                write port #{0BAD}
                reduce [port? update port (checksum #{0BAD} 'md5) = port/data]"""))
                .isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("every digest method this build has works through the port")
    void everyMethodWorks() {
        assertThat(answerTo("""
                collect [
                    foreach method [
                        md5 sha1 sha224 sha256 sha384 sha512
                        sha3-224 sha3-256 sha3-384 sha3-512
                    ][
                        port: open to url! join "checksum:" form method
                        write port #{0BAD}
                        keep (checksum #{0BAD} method) = read port
                    ]
                ]""")).isEqualTo(
                "[#(true) #(true) #(true) #(true) #(true)"
                        + " #(true) #(true) #(true) #(true) #(true)]");
    }

    @Test
    @DisplayName("WRITE/PART sums only what it counts")
    void writePartCountsBytes() {
        assertThat(answerTo("""
                port: open checksum://
                write/part port #{0BAD} 1
                (checksum #{0B} 'md5) = read port""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("WRITE starts from the value's own index, not from its head")
    void writeStartsAtTheIndex() {
        assertThat(answerTo("""
                port: open checksum://
                write/part port next #{0BAD} 1
                (checksum #{AD} 'md5) = read port""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("two counted writes together sum the whole")
    void twoCountedWritesSumTheWhole() {
        assertThat(answerTo("""
                port: open checksum://
                write/part port #{0BAD} 1
                write/part port next #{0BAD} 1
                (checksum #{0BAD} 'md5) = read port""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a count of nothing adds nothing")
    void aCountOfNothingAddsNothing() {
        assertThat(answerTo("""
                port: open checksum://
                write port #{0BAD}
                before: read port
                before = read write/part port #{CAFE} 0""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a negative count reaches backwards, and stops at the head")
    void aNegativeCountReachesBackwards() {
        assertThat(answerTo("""
                port: open checksum://
                write port #{0BAD}
                before: read port
                reduce [
                    before = read write/part port #{0BAD} -1
                    (checksum #{0BAD0BAD} 'md5) = read write/part port tail #{0BAD} -2
                ]""")).isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("WRITE/SEEK moves the start, and /PART counts from there")
    void seekMovesTheStart() {
        assertThat(answerTo("""
                port: open checksum://
                (checksum #{0BAD} 'md5)
                    = read write/seek/part port #{CAFE0BAD} 2 2""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a seek backwards from the tail lands in the same place")
    void seekBackwardsFromTheTail() {
        assertThat(answerTo("""
                port: open checksum://
                (checksum #{0BAD} 'md5)
                    = read write/seek/part port tail #{CAFE0BAD} -2 2"""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("writing something that carries no bytes is an invalid argument")
    void writingANumberRaises() {
        assertThat(errorIdFrom("""
                write checksum:md5 1""")).isEqualTo("invalid-arg");
    }

    @Test
    @DisplayName("a URL may be written to directly, port and all")
    void aUrlMayBeWrittenTo() {
        assertThat(answerTo("""
                port? write checksum:md5 #{0BAD}""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a scheme nothing serves has no scheme, whichever verb asks")
    void anUnknownSchemeHasNoScheme() {
        assertThat(errorIdFrom("""
                read 'nowhere""")).isEqualTo("no-scheme");
        assertThat(errorIdFrom("""
                write 'nowhere #{00}""")).isEqualTo("no-scheme");
    }
}
