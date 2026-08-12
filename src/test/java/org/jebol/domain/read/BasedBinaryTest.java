package org.jebol.domain.read;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A binary literal that says which base it is written in.
 *
 * <p>Specified in {@code spec/natives.allium} and measured against a real
 * R3 3.22.1.
 *
 * <p>Three bases and no others: bits, hexadecimal and base 64. Whitespace
 * and comments inside the braces are ignored, which is what lets a long
 * binary be broken across lines with a note beside it.
 */
class BasedBinaryTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    /** The id of the error a snippet raises, or "no-error" if it raises none. */
    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("base 2 reads bits")
    void bitsAreRead() {
        assertThat(answerTo("#{01} = 2#{00000001}")).isEqualTo("#(true)");
        assertThat(answerTo("#{02} = 2#{00000010}")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("base 16 is what a plain binary already means")
    void hexadecimalIsTheDefault() {
        assertThat(answerTo("#{FF} = 16#{FF}")).isEqualTo("#(true)");
        assertThat(answerTo("#{0001} = 16#{0001}")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("base 64 decodes")
    void base64IsRead() {
        assertThat(answerTo("#{414243} = 64#{QUJD}")).isEqualTo("#(true)");
        assertThat(answerTo("#{0001} = 64#{AAE=}")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("whitespace inside the braces is ignored")
    void whitespaceIsSkipped() {
        // Read from strings, because a caret escape only becomes a
        // newline or a tab inside one. Written straight into the source
        // the reader would see the caret and the slash themselves.
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
    void shortBodiesArePadded() {
        // Refusing these is the tempting reading, and it is wrong.
        assertThat(answerTo("#{00} = 2#{000}")).isEqualTo("#(true)");
        assertThat(answerTo("#{00} = 16#{0}")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("an empty body is an empty binary")
    void theDegenerateBodyIsEmpty() {
        assertThat(answerTo("empty? 2#{}")).isEqualTo("#(true)");
        assertThat(answerTo("empty? 16#{}")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("only three bases exist")
    void anyOtherBaseIsRefused() {
        assertThat(errorIdOf("load \"8#{00}\"")).isEqualTo("invalid");
        assertThat(errorIdOf("load \"10#{00}\"")).isEqualTo("invalid");
    }

    @Test
    @DisplayName("a base with leading zeros is refused")
    void theBaseMustBeWrittenPlainly() {
        // The base is read as a number first, which is why this is a
        // complaint about the integer rather than about the binary.
        assertThat(errorIdOf("load \"0016#{FF}\"")).isEqualTo("invalid");
    }

    @Test
    @DisplayName("and a signed base is refused, because a base sits at the start of the token")
    void aSignedBaseIsRefused() {
        // `if (cp == scan_state->begin) { // no +2 +16 +64 allowed`.
        //
        // This test asserted `no-error` until the divergence was closed: `+2#{}`
        // used to read as the integer 2 and an empty binary. The check that catches
        // `0016#` never saw it, because the reader takes the hash into the lexeme
        // when a sign is present and leaves it ahead when one is not, so the two
        // spellings arrive in different shapes.
        assertThat(errorIdOf("load \"+2#{}\"")).isEqualTo("invalid");
        assertThat(errorIdOf("load \"-2#{01}\"")).isEqualTo("invalid");
    }

    @Test
    @DisplayName("base 64 refuses a body it cannot decode")
    void badBase64IsRefused() {
        assertThat(errorIdOf("load \"64#{A}\"")).isEqualTo("invalid");
    }

    @Test
    @DisplayName("a plain binary still reads as it did")
    void theOrdinaryFormIsUnaffected() {
        assertThat(answerTo("#{0001} = load \"#{0001}\"")).isEqualTo("#(true)");
        assertThat(answerTo("#{0001} = load \"#{00 01}\"")).isEqualTo("#(true)");
    }
}
