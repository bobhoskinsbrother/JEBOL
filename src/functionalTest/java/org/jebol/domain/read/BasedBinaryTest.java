package org.jebol.domain.read;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BasedBinaryTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String readTheErrorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("base 2 reads bits")
    void readsBits() {
        assertThat(answerTo("#{01} = 2#{00000001}")).isEqualTo("#(true)");
        assertThat(answerTo("#{02} = 2#{00000010}")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("base 16 is what a plain binary already means")
    void readsHexadecimalByDefault() {
        assertThat(answerTo("#{FF} = 16#{FF}")).isEqualTo("#(true)");
        assertThat(answerTo("#{0001} = 16#{0001}")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("base 64 decodes")
    void readsBase64() {
        assertThat(answerTo("#{414243} = 64#{QUJD}")).isEqualTo("#(true)");
        assertThat(answerTo("#{0001} = 64#{AAE=}")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("whitespace inside the braces is ignored")
    void whitespaceIsSkipped() {
        assertThat(answerTo("#{00} = 2#{0000 00 00}")).isEqualTo("#(true)");
        assertThat(answerTo("#{00} = load {2#^{0000^/0000^}}")).isEqualTo("#(true)");
        assertThat(answerTo("#{01} = load {2#^{0000^-0001^}}")).isEqualTo("#(true)");
        assertThat(answerTo("#{0001} = 16#{00 01}")).isEqualTo("#(true)");
        assertThat(answerTo("#{0001} = 64#{AA E=}")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a semicolon starts a comment inside the braces")
    void commentsAreSkipped() {
        assertThat(answerTo("#{00} = load {#{;note^/00}}")).isEqualTo("#(true)");
        assertThat(answerTo("#{00} = load {#{00;note^/}}")).isEqualTo("#(true)");
        assertThat(answerTo("#{0002} = load {#{00;note^/02}}")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a body that does not fill its last byte is padded")
    void padsShortBodies() {
        assertThat(answerTo("#{00} = 2#{000}")).isEqualTo("#(true)");
        assertThat(answerTo("#{00} = 16#{0}")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("an empty body is an empty binary")
    void readsAnEmptyBody() {
        assertThat(answerTo("empty? 2#{}")).isEqualTo("#(true)");
        assertThat(answerTo("empty? 16#{}")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("only three bases exist")
    void refusesAnyOtherBase() {
        assertThat(readTheErrorIdOf("load \"8#{00}\"")).isEqualTo("invalid");
        assertThat(readTheErrorIdOf("load \"10#{00}\"")).isEqualTo("invalid");
    }

    @Test
    @DisplayName("a base with leading zeros is refused")
    void requiresAPlainlyWrittenBase() {
        assertThat(readTheErrorIdOf("load \"0016#{FF}\"")).isEqualTo("invalid");
    }

    @Test
    @DisplayName("and a signed base is refused, because a base sits at the start of the token")
    void refusesASignedBase() {
        assertThat(readTheErrorIdOf("load \"+2#{}\"")).isEqualTo("invalid");
        assertThat(readTheErrorIdOf("load \"-2#{01}\"")).isEqualTo("invalid");
    }

    @Test
    @DisplayName("base 64 refuses a body it cannot decode")
    void badBase64IsRefused() {
        assertThat(readTheErrorIdOf("load \"64#{A}\"")).isEqualTo("invalid");
    }

    @Test
    @DisplayName("a plain binary still reads as it did")
    void leavesTheOrdinaryFormAlone() {
        assertThat(answerTo("#{0001} = load \"#{0001}\"")).isEqualTo("#(true)");
        assertThat(answerTo("#{0001} = load \"#{00 01}\"")).isEqualTo("#(true)");
    }
}
