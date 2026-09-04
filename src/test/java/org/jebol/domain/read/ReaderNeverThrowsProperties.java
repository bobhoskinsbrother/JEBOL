package org.jebol.domain.read;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.CharRange;
import net.jqwik.api.constraints.Size;
import net.jqwik.api.constraints.StringLength;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reader answers with a result or a failure and never with a host exception,
 * whatever it is handed. Asserted over generated input rather than over a list.
 *
 * <p>The example-based half of this promise is in {@link ReaderNeverThrowsTest},
 * which walks fifty malformed spellings somebody thought of. These two ask the
 * harder question: what about the ones nobody thought of.
 *
 * <p><b>Why this is a class of its own, and why that matters.</b> These two
 * properties lived in {@code ReaderNeverThrowsTest} beside its {@code @Test} and
 * {@code @ParameterizedTest} methods, and <em>they never ran</em>. A class holding
 * both jqwik's {@code @Property} and Jupiter's {@code @Test} is claimed by the
 * Jupiter engine, and the properties are reported as skipped without executing.
 * Gradle counted them, named them, said "2 skipped" beside a SUCCESSFUL build, and
 * that was the only sign.
 *
 * <p>It was proved by putting {@code assertThat(false)} inside one: the build
 * stayed green. A property in a class of its own runs perfectly well, which is the
 * whole of the fix.
 *
 * <p>So: <b>no Jupiter annotation belongs in this file.</b> Adding a single
 * {@code @Test} here would silently switch both properties off again, and nothing
 * would fail to tell you.
 */
class ReaderNeverThrowsProperties {

    @Property
    void arbitraryTextNeverThrows(@ForAll @StringLength(max = 40) String source) {
        TranscodeResult result = Transcoder.transcode(source);

        assertThat(result).isNotNull();
        assertThat(result.succeeded() || result.error().isPresent())
                .as("a read either succeeded or carries an error, never neither")
                .isTrue();
    }

    /**
     * Punctuation only, because that is where a reader breaks.
     *
     * <p>The range {@code !} to {@code /} is fifteen characters and holds most of
     * what the scanner treats specially: the quote, the hash, the dollar, the
     * percent, the apostrophe, both parentheses, the comma, the full stop and the
     * slash. Random letters rarely find anything; random punctuation finds the
     * places two rules meet.
     */
    @Property
    void arbitraryPunctuationNeverThrows(
            @ForAll @Size(max = 24) List<@CharRange(from = '!', to = '/')
                    Character> characters) {

        StringBuilder source = new StringBuilder();
        characters.forEach(source::append);

        TranscodeResult result = Transcoder.transcode(source.toString());

        assertThat(result).isNotNull();
        assertThat(result.succeeded() || result.error().isPresent())
                .as("a read either succeeded or carries an error, never neither")
                .isTrue();
    }
}
