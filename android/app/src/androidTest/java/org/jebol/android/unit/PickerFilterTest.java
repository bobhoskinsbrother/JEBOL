package org.jebol.android.unit;

import org.jebol.adapter.android.PickerFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PickerFilterTest {

    @Test
    @DisplayName("no filter opens everything")
    void noFilterIsEverything() {
        assertThat(PickerFilter.mimeTypes(List.of())).containsExactly("*/*");
    }

    @Test
    @DisplayName("a pattern's suffix becomes the MIME type Android's own table gives it")
    void aSuffixIsItsMimeType() {
        assertThat(PickerFilter.mimeTypes(List.of("Text", "*.txt"))).containsExactly("text/plain");
    }

    @Test
    @DisplayName("every pattern of every pair counts, each type once, in the order written")
    void everyPatternCounts() {
        assertThat(PickerFilter.mimeTypes(List.of("Pictures", "*.png;*.jpg; *.jpeg", "Text", "*.txt", "Also", "*.PNG")))
                .containsExactly("image/png", "image/jpeg", "text/plain");
    }

    @Test
    @DisplayName("* and *.* open everything, wherever they are written")
    void aCatchAllIsEverything() {
        assertThat(PickerFilter.mimeTypes(List.of("Text", "*.txt", "All", "*"))).containsExactly("*/*");
        assertThat(PickerFilter.mimeTypes(List.of("All", "*.*"))).containsExactly("*/*");
    }

    @Test
    @DisplayName("a pattern Android has no MIME type for opens everything rather than hiding the file")
    void anUnknownSuffixIsEverything() {
        assertThat(PickerFilter.mimeTypes(List.of("Rebol", "*.reb", "Text", "*.txt"))).containsExactly("*/*");
        assertThat(PickerFilter.mimeTypes(List.of("Odd", "data-??.bin"))).containsExactly("*/*");
    }
}
