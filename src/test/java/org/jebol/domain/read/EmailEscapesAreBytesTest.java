package org.jebol.domain.read;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailEscapesAreBytesTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("two escapes that spell one letter arrive as one letter")
    void twoEscapesSpellOneLetter() {
        assertThat(answerTo("""
                reduce [mold a@%C5%A1 form a@%C5%A1 length? a@%C5%A1]"""))
                .isEqualTo("[\"a@š\" \"a@š\" 3]");
    }

    @Test
    @DisplayName("an escape inside the seven-bit range is that character")
    void anAsciiEscapeIsThatCharacter() {
        assertThat(answerTo("""
                reduce [mold a@%62 mold a@%2E]"""))
                .isEqualTo("[\"a@b\" \"a@.\"]");
    }

    @Test
    @DisplayName("an escaped at-sign does not count as the one an email needs")
    void anEscapedAtSignDoesNotCount() {
        assertThat(answerTo("""
                e: try [load "a%40b"]
                error? e""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("and a second unescaped one is still refused")
    void aSecondAtSignIsRefused() {
        assertThat(answerTo("""
                e: try [load "a@b@c"]
                error? e""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("four escapes spelling a character above the basic plane")
    void fourEscapesSpellAWideCharacter() {
        assertThat(answerTo("""
                reduce [mold a@%F0%9F%99%82 length? a@%F0%9F%99%82]"""))
                .isEqualTo("[\"a@🙂\" 3]");
    }

    @Test
    @DisplayName("TO EMAIL! does not decode, because it is not reading source")
    void toEmailDoesNotDecode() {
        assertThat(answerTo("""
                mold to email! "a@%C5%A1\"""")).isEqualTo("\"a@%C5%A1\"");
    }

    @Test
    @DisplayName("a truncated or non-hex escape is a syntax error")
    void aBadEscapeIsRefused() {
        assertThat(answerTo("""
                collect [
                    foreach text ["a@%C" "a@%" "a@%ZZ" "a@%C5%"][
                        keep error? try [load text]
                    ]
                ]""")).isEqualTo("[#(true) #(true) #(true) #(true)]");
    }
}
