package org.jebol.domain.read;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Splitting source into its top-level expressions, as written.
 *
 * <p>Reading source into values and molding them back is lossy, in JEBOL
 * and in R3 alike: {@code 1.7976931348623157e308} molds to fifteen digits
 * and reads back as infinity. Anything that needs one expression's source
 * therefore has to take the text rather than rebuild it, which is what
 * this is for.
 */
class TopLevelSpansTest {

    private static List<String> textsOf(String source) {
        return Transcoder.topLevelSpans(source).stream()
                .map(Transcoder.SourceSpan::text)
                .toList();
    }

    @Test
    @DisplayName("one expression is its own span")
    void oneValue() {
        assertThat(textsOf("1 + 1")).containsExactly("1", "+", "1");
    }

    @Test
    @DisplayName("a block counts as one, brackets and all")
    void aBlockIsOneSpan() {
        assertThat(textsOf("[1 2] 3")).containsExactly("[1 2]", "3");
    }

    @Test
    @DisplayName("nesting does not add spans")
    void nestingIsNotSplit() {
        assertThat(textsOf("[a [b c]]")).containsExactly("[a [b c]]");
    }

    @Test
    @DisplayName("a paren counts as one too")
    void aParenIsOneSpan() {
        assertThat(textsOf("(1 + 1) 2")).containsExactly("(1 + 1)", "2");
    }

    @Test
    @DisplayName("the text comes back exactly as written, digits and all")
    void aLongDecimalKeepsItsDigits() {
        assertThat(textsOf("even? 1.7976931348623157e308"))
                .containsExactly("even?", "1.7976931348623157e308");
    }

    @Test
    @DisplayName("a time keeps the spelling it was written with")
    void aTimeKeepsItsSpelling() {
        assertThat(textsOf("0:0:1")).containsExactly("0:0:1");
    }

    @Test
    @DisplayName("a string keeps its own brackets and spaces")
    void aStringIsNotSplitOnItsSpaces() {
        assertThat(textsOf("\"a b\" c")).containsExactly("\"a b\"", "c");
    }

    @Test
    @DisplayName("a braced string that holds brackets is still one span")
    void bracesInsideAStringDoNotOpenABlock() {
        assertThat(textsOf("{a [ b} c")).containsExactly("{a [ b}", "c");
    }

    @Test
    @DisplayName("a comment is not an expression")
    void commentsAreSkipped() {
        assertThat(textsOf("1 ; a note\n2")).containsExactly("1", "2");
    }

    @Test
    @DisplayName("empty source has no spans")
    void nothingInNothingOut() {
        assertThat(textsOf("")).isEmpty();
    }

    @Test
    @DisplayName("source the reader refuses has no spans rather than some")
    void unreadableSourceGivesNone() {
        assertThat(textsOf("[1 2")).isEmpty();
    }

    @Test
    @DisplayName("construction syntax is one span, not one per part")
    void constructionSyntaxIsNotSplit() {
        assertThat(textsOf("#(true) 1")).containsExactly("#(true)", "1");
    }

    @Test
    @DisplayName("a character above the Basic Multilingual Plane does not shift the cut")
    void astralCharactersDoNotMisalignTheSpans() {
        assertThat(textsOf("\"a\uD83D\uDE00b\" second"))
                .containsExactly("\"a\uD83D\uDE00b\"", "second");
    }

    @Test
    @DisplayName("there is one span for every value read")
    void spansAndValuesAgree() {
        String source = "a: 1 [b c] (d) \"e\" 0:0:1";

        assertThat(textsOf(source))
                .hasSameSizeAs(Transcoder.transcode(source).values().orElseThrow().remaining());
    }
}
