package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RIPEMD-160, which JEBOL writes out because the JVM has not got it.
 *
 * <p>{@code system/catalog/checksums} lists it, so a script may ask for it and
 * a port may be opened on it, and {@code java.security} offers MD5, the SHA
 * family and nothing else. The shipped jar takes no dependencies, so the
 * alternative to writing it was not offering it.
 *
 * <p>Checked against the published vectors rather than against JEBOL itself,
 * which is the only honest way to test a hash: an implementation that is
 * wrong in the same way twice still agrees with itself.
 */
class RipeMd160FromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("the published vectors, every one of them")
    void thePublishedVectors() {
        assertThat(answerTo("""
                collect [
                    foreach text [
                        ""
                        "a"
                        "abc"
                        "message digest"
                        "abcdefghijklmnopqrstuvwxyz"
                    ][
                        keep checksum text 'ripemd160
                    ]
                ]""")).isEqualTo(
                "[#{9C1185A5C5E9FC54612808977EE8F548B2258D31}"
                        + " #{0BDC9D2D256B3EE9DAAE347BE6F4DC835A467FFE}"
                        + " #{8EB208F7E05D987A9B044A8E98C6B087F15A0BFC}"
                        + " #{5D0689EF49D2FAE572B881B123A85FFA21595F36}"
                        + " #{F71C27109C692C1B56BBDCEB5B9D2865B3708DBC}]");
    }

    @Test
    @DisplayName("a message long enough to need a second block")
    void aMessageOverOneBlock() {
        assertThat(answerTo("""
                checksum
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
                    'ripemd160""")).isEqualTo("#{B0E20B6E3116640286ED3A87A5713079B21F5189}");
    }

    @Test
    @DisplayName("and one whose padding needs a block of its own")
    void aMessageWhosePaddingOverflows() {
        assertThat(answerTo("""
                checksum
                    "12345678901234567890123456789012345678901234567890123456789012345678901234567890"
                    'ripemd160""")).isEqualTo("#{9B752E45573D4B39F4DBD3323CAB82BF63326BFB}");
    }

    @Test
    @DisplayName("it is in the catalogue, so a script can ask whether it is there")
    void itIsInTheCatalogue() {
        assertThat(answerTo("""
                true? find system/catalog/checksums 'ripemd160""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a port can be opened on it, and sums across writes like the rest")
    void aPortCanBeOpenedOnIt() {
        assertThat(answerTo("""
                port: open checksum:ripemd160
                write port #{0BAD}
                write port #{0BAD}
                reduce [
                    port/spec/method
                    (checksum #{0BAD0BAD} 'ripemd160) = read port
                ]""")).isEqualTo("[ripemd160 #(true)]");
    }

    @Test
    @DisplayName("and reading it twice does not end the sum")
    void readingTwiceDoesNotEndIt() {
        assertThat(answerTo("""
                port: open checksum:ripemd160
                write port #{0BAD}
                first-read: read port
                reduce [first-read = read port first-read = read port]"""))
                .isEqualTo("[#(true) #(true)]");
    }
}
