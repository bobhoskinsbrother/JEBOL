package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MD4, which java.security dropped and old formats still carry.
 *
 * <p>Thoroughly broken as a cryptographic hash and has been since the
 * nineties, which is why the JVM no longer offers it. It is here because
 * protocols and file formats written when it was new still store MD4 sums,
 * and reading one of those means computing one.
 *
 * <p>Checked against the published vectors rather than against JEBOL, which is
 * the only honest way to test a hash.
 */
class Md4FromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("the published vectors")
    void thePublishedVectors() {
        assertThat(answerTo("""
                collect [
                    foreach text ["" "a" "abc" "message digest"][
                        keep checksum text 'md4
                    ]
                ]""")).isEqualTo(
                "[#{31D6CFE0D16AE931B73C59D7E0C089C0}"
                        + " #{BDE52CB31DE33E46245E05FBDBD6FB24}"
                        + " #{A448017AAF21D8525FC10AE87AA6729D}"
                        + " #{D9130A8164549FE818874806E1C7014B}]");
    }

    @Test
    @DisplayName("a message whose padding needs a block of its own")
    void aMessageWhosePaddingOverflows() {
        assertThat(answerTo("""
                checksum
                    "12345678901234567890123456789012345678901234567890123456789012345678901234567890"
                    'md4""")).isEqualTo("#{E33B4DDC9C38F2199C3E7B164FCC0536}");
    }

    @Test
    @DisplayName("it is in the catalogue and a port can be opened on it")
    void itIsInTheCatalogueAndOpens() {
        assertThat(answerTo("""
                port: open checksum:md4
                write port #{0BAD}
                write port #{0BAD}
                reduce [
                    true? find system/catalog/checksums 'md4
                    port/spec/method
                    (checksum #{0BAD0BAD} 'md4) = read port
                ]""")).isEqualTo("[#(true) md4 #(true)]");
    }
}
