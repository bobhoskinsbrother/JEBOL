package org.jebol.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AnEventsTouchFlagTest {

    private static EventValue raising(Set<EventValue.Flag> flags) {
        return new EventValue(1, flags, EventValue.Model.GUI, 0, NoneValue.none());
    }

    @Test
    @DisplayName("is spelled touch")
    void isSpelledTouch() {
        assertThat(EventValue.Flag.TOUCH.spelling()).isEqualTo("touch");
    }

    @Test
    @DisplayName("is read after R3's own three")
    void isReadLast() {
        assertThat(raising(Set.of(EventValue.Flag.TOUCH, EventValue.Flag.SHIFT, EventValue.Flag.DOUBLE))
                .listRaisedFlagWords())
                .containsExactly(WordValue.of("double"), WordValue.of("shift"), WordValue.of("touch"));
    }

    @Test
    @DisplayName("is readable on its own")
    void isReadableAlone() {
        assertThat(raising(Set.of(EventValue.Flag.TOUCH)).listRaisedFlagWords())
                .containsExactly(WordValue.of("touch"));
    }

    @Test
    @DisplayName("is not raised by the internal flags beside it")
    void isNotRaisedByTheOthers() {
        assertThat(raising(Set.of(EventValue.Flag.HAS_XY, EventValue.Flag.ALT)).listRaisedFlagWords()).isEmpty();
    }
}
