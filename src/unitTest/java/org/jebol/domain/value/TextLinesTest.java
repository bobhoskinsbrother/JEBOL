package org.jebol.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextLinesTest {

    @Test
    @DisplayName("agrees with String.lines on every text of six characters or fewer")
    void everyShortText() {
        for (int length = 0; length <= 6; length++) {
            int howMany = (int) Math.pow(3, length);
            for (int written = 0; written < howMany; written++) {
                String text = fromThreeCharacters(written, length);
                assertThat(TextLines.of(text))
                        .as("the lines of %s", text)
                        .isEqualTo(text.lines().toList());
            }
        }
    }

    private static String fromThreeCharacters(int written, int length) {
        StringBuilder text = new StringBuilder();
        for (int digit = 0; digit < length; digit++) {
            text.append("a\n\r".charAt(written % 3));
            written /= 3;
        }
        return text.toString();
    }

    @Test
    @DisplayName("does not split on the separators String.lines ignores")
    void otherSeparators() {
        for (String text : List.of(
                "a\u0085b", "a\u2028b", "a\u2029b", "a\n\u0085\nb", "\u0085", "a\u000Bb")) {
            assertThat(TextLines.of(text)).isEqualTo(text.lines().toList());
        }
    }

    @Test
    @DisplayName("gives an empty list for an empty text and keeps interior blanks")
    void blanks() {
        List<String> expected = new ArrayList<>(List.of("", "", ""));
        assertThat(TextLines.of("")).isEmpty();
        assertThat(TextLines.of("\n\n\n")).isEqualTo(expected);
    }
}
