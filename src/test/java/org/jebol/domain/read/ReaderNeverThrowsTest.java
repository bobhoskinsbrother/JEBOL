package org.jebol.domain.read;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.CharRange;
import net.jqwik.api.constraints.Size;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The reader answers with a result or a failure, and never with a host
 * exception. Whatever it is handed.
 *
 * <p>{@code spec/load.allium} promises that a syntax failure arrives as an
 * {@code error!} a script could catch. That promise was quietly broken by
 * {@code #[unset!]}, which threw {@link IllegalArgumentException} out of the
 * reader because a bare hash left an empty word behind. Found by molding one
 * of every datatype and reading each back, which is a thing worth doing to
 * anything claiming to round-trip.
 */
class ReaderNeverThrowsTest {

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {
        "#", "##", "#[", "#[]", "#[unset!]", "#[none]", "#[true]", "#[nonsense]",
        "#\"", "#{", "#{ZZ}", "#{A}",
        "[", "]", "(", ")", "([)]", "[[[",
        "\"", "\"unterminated", "{", "{{}",
        "^", "^(", "^(zzz)", "\"^\"", "\"^(nonsense)\"",
        "%", "$", "$notanumber", "<", "<unclosed",
        ":", "'", "/", "//", "///", "a/", "/a/", "a//b",
        "1.2.3.4.5.6.7.8.9.10.11.12.13", "999.999.999",
        "99999999999999999999999999", "1x", "x1", "10:", ":30",
        "-", "- 1", "+", "1-", "notadate-zzz-2000", "0-0-0",
        "nonsense!", "1..2", "....",
    })
    @DisplayName("malformed input gives a failure, not an exception")
    void malformedInputNeverThrows(String source) {
        TranscodeResult result = Transcoder.transcode(source);

        assertThat(result).isNotNull();
        if (!result.succeeded()) {
            assertThat(result.error())
                    .as("a failure must carry an error! value")
                    .isPresent();
            assertThat(result.error().orElseThrow().category().spelling())
                    .isEqualTo("syntax");
        }
    }

    @Property
    @DisplayName("nor does arbitrary text")
    void arbitraryTextNeverThrows(@ForAll @StringLength(max = 40) String source) {
        TranscodeResult result = Transcoder.transcode(source);

        assertThat(result).isNotNull();
        assertThat(result.succeeded() || result.error().isPresent()).isTrue();
    }

    @Property
    @DisplayName("nor does arbitrary punctuation, which is where readers break")
    void arbitraryPunctuationNeverThrows(
            @ForAll @Size(max = 24) List<@CharRange(from = '!', to = '/')
                    Character> characters) {
        StringBuilder source = new StringBuilder();
        characters.forEach(source::append);

        TranscodeResult result = Transcoder.transcode(source.toString());

        assertThat(result).isNotNull();
        assertThat(result.succeeded() || result.error().isPresent()).isTrue();
    }

    @Test
    @DisplayName("a bare hash is a mistake, not a crash")
    void bareHashIsAMistake() {
        assertThat(Transcoder.transcode("#").succeeded()).isFalse();
    }

    @Test
    @DisplayName("construction syntax reads back what MOLD wrote")
    void constructionSyntaxReadsBack() {
        assertThat(Transcoder.transcode("#[unset!]").succeeded()).isTrue();
        assertThat(Transcoder.transcode("#[none]").succeeded()).isTrue();
        assertThat(Transcoder.transcode("#[true]").succeeded()).isTrue();
    }
}
