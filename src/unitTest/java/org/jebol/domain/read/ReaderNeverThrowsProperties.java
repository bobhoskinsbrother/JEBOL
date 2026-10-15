package org.jebol.domain.read;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.CharRange;
import net.jqwik.api.constraints.Size;
import net.jqwik.api.constraints.StringLength;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReaderNeverThrowsProperties {

    @Property
    void arbitraryTextNeverThrows(@ForAll @StringLength(max = 40) String source) {
        TranscodeResult result = Transcoder.transcode(source);

        assertThat(result).isNotNull();
        assertThat(result.succeeded() || result.error().isPresent())
                .as("a read either succeeded or carries an error, never neither")
                .isTrue();
    }

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
