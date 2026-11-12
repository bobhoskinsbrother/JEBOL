package org.jebol.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnErrorsArgumentIsAValueNotASentenceTest {

    @Test
    @DisplayName("a word spelt with a space in it is refused as an argument")
    void aSpacedWordIsRefused() {
        assertThatThrownBy(() -> ErrorValue.of(ErrorCategory.SCRIPT, "no-value", "",
                WordValue.of("zork has no value")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("so is one in the second or third place, and one spelt as a set-word inside a word")
    void everyPlaceIsGuarded() {
        assertThatThrownBy(() -> ErrorValue.of(ErrorCategory.SCRIPT, "not-related", "",
                SetWordValue.of("add"), WordValue.of("pair arithmetic")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ErrorValue.of(ErrorCategory.SCRIPT, "expect-arg", "",
                WordValue.of("f"), WordValue.of("x"), WordValue.of("")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ErrorValue.of(ErrorCategory.SCRIPT, "not-related", "",
                WordValue.of("subtract:")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a word any scanner reads, a set-word and a string with spaces are all accepted")
    void whatAScannerReadsIsAccepted() {
        assertThat(ErrorValue.of(ErrorCategory.SCRIPT, "not-related", "",
                SetWordValue.of("and~"), IntegerValue.TYPE).subject())
                .contains(SetWordValue.of("and~"));
        assertThat(ErrorValue.of(ErrorCategory.ACCESS, "read-error", "",
                WordValue.of("clipboard"), StringValue.of("no clipboard here")).secondArgument())
                .contains(StringValue.of("no clipboard here"));
    }
}
