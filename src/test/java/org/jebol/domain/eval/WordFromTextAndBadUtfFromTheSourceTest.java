package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Making a word out of text, and refusing bytes that are not text.
 *
 * <p>Two rules that both come down to what counts as whitespace and where the
 * refusal points.
 */
class WordFromTextAndBadUtfFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("raised: try [" + source + "] raised/id");
    }

    @Test
    @DisplayName("spaces and tabs come off both ends of a name")
    void surroundingSpaceIsTrimmed() {
        assertThat(answerTo("""
                reduce [to word! " a" to word! "a " to word! " a " to word! "^-a"]"""))
                .isEqualTo("[a a a a]");
    }

    @Test
    @DisplayName("a newline is not whitespace for this, so it is a bad character")
    void aNewlineIsNotTrimmed() {
        assertThat(errorIdFrom("""
                to word! "^/a^/\"""")).isEqualTo("invalid-chars");
    }

    @Test
    @DisplayName("a space inside a name is still a bad character")
    void anInnerSpaceIsRefused() {
        assertThat(errorIdFrom("""
                to word! "a b\"""")).isEqualTo("invalid-chars");
    }

    @Test
    @DisplayName("text with no name in it is too short rather than badly spelled")
    void nothingToNameIsTooShort() {
        assertThat(errorIdFrom("""
                to word! {}""")).isEqualTo("too-short");
        assertThat(errorIdFrom("""
                to word! {  }""")).isEqualTo("too-short");
    }

    @Test
    @DisplayName("a name outside ASCII is a name like any other")
    void aNameOutsideAsciiWorks() {
        assertThat(answerTo("""
                reduce [to word! "š" to word! " š " to word! "🙂"]"""))
                .isEqualTo("[š š 🙂]");
    }

    @Test
    @DisplayName("bytes that are not UTF-8 have no text form")
    void badBytesAreRefused() {
        assertThat(errorIdFrom("""
                to string! #{C5A1C5}""")).isEqualTo("invalid-utf");
    }

    @Test
    @DisplayName("and the refusal names what was left from where it stopped")
    void theRefusalNamesWhatIsLeft() {
        assertThat(answerTo("""
                collect [
                    foreach bytes [#{C5A1C5} #{C5A1C500} #{FF} #{C5A1FFFF} #{E282}][
                        raised: try [to string! bytes]
                        keep raised/arg1
                    ]
                ]""")).isEqualTo("[#{C5} #{C500} #{FF} #{FFFF} #{E282}]");
    }

    @Test
    @DisplayName("bytes that are UTF-8 come back as the text they spell")
    void goodBytesDecode() {
        assertThat(answerTo("""
                reduce [to string! #{C5A1} to string! #{F09F9982} to string! #{}]"""))
                .isEqualTo("[\"š\" \"🙂\" \"\"]");
    }

    @Test
    @DisplayName("a surrogate pair encoded separately is the one character it stands for")
    void aSurrogatePairIsJoined() {
        assertThat(answerTo("""
                "𝄢" == to string! #{EDA0B4EDB4A2}""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("but a lone surrogate is still not a character")
    void aLoneSurrogateIsRefused() {
        assertThat(answerTo("""
                collect [
                    foreach bytes [#{EDA0B4} #{EDB4A2}][
                        raised: try [to string! bytes]
                        keep raised/id
                    ]
                ]""")).isEqualTo("[invalid-utf invalid-utf]");
    }

    @Test
    @DisplayName("TO CHAR! names the bytes it could not read, not just the type")
    void toCharNamesTheBytes() {
        assertThat(answerTo("""
                collect [
                    foreach bytes [#{C5} #{F09F99}][
                        raised: try [to char! bytes]
                        keep raised/arg2
                    ]
                ]""")).isEqualTo("[#{C5} #{F09F99}]");
    }

    @Test
    @DisplayName("COPY/PART counts characters the way LENGTH? does, not the way Java does")
    void copyPartCountsCodePoints() {
        assertThat(answerTo("""
                wide: to string! #"^(1F642)"
                reduce [length? wide  to binary! copy/part wide 1]"""))
                .isEqualTo("[1 #{F09F9982}]");
    }

    @Test
    @DisplayName("and a copy of one wide character is that whole character")
    void aCopyOfAWideCharacterIsWhole() {
        assertThat(answerTo("""
                wide: to string! #"^(1F642)"
                (copy/part wide 1) == wide""")).isEqualTo("#(true)");
    }
}
