package org.jebol.domain.read;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;
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

    // The two properties that used to live here are in ReaderNeverThrowsProperties,
    // and they had to move to run at all: a class holding both jqwik's @Property
    // and Jupiter's @Test is claimed by Jupiter, and the properties are reported as
    // skipped without ever executing. See that class for the whole story.

    @Test
    @DisplayName("a bare hash is a mistake, not a crash")
    void bareHashIsAMistake() {
        assertThat(Transcoder.transcode("#").succeeded()).isFalse();
    }

    @Test
    @DisplayName("construction syntax reads back what MOLD wrote")
    void constructionSyntaxReadsBack() {
        // Asserted as the round trip rather than as three spellings. The
        // spellings moved from #[none] to #(none) between R3-Alpha and
        // Rebol 3.x, and a test naming them passed while the property it
        // was there for had quietly stopped holding.
        assertThat(readBack(NoneValue.none())).isEqualTo(NoneValue.none());
        assertThat(readBack(LogicValue.yes())).isEqualTo(LogicValue.yes());
        assertThat(readBack(LogicValue.no())).isEqualTo(LogicValue.no());
        assertThat(readBack(UnsetValue.unset())).isEqualTo(UnsetValue.unset());
    }

    /** A value molded to source and read straight back. */
    private static Value readBack(Value value) {
        TranscodeResult reread = Transcoder.transcode(Molder.mold(value));
        assertThat(reread.succeeded())
                .as("mold produced %s, which will not read", Molder.mold(value))
                .isTrue();
        return reread.values().orElseThrow().first();
    }
}
