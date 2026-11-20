package org.jebol.domain.host;

import org.jebol.domain.value.PairValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class WhereAWindowIsShownOnOneSurfaceTest {

    private static final PairValue PHONE = PairValue.of(384, 800);

    private static ShownWindow show(int wide, int high, int across, int down) {
        return ShownWindow.of(PHONE, PairValue.of(wide, high), PairValue.of(across, down), 0);
    }

    @Nested
    @DisplayName("a window that fits")
    class ThatFits {

        @Test
        @DisplayName("is shown where it is, at its own size")
        void isShownWhereItIs() {
            ShownWindow shown = show(300, 200, 40, 30);

            assertThat(shown.scale()).isEqualTo(1.0);
            assertThat(shown.across()).isEqualTo(40);
            assertThat(shown.down()).isEqualTo(30);
        }

        @Test
        @DisplayName("but lies partly beyond the screen is moved just far enough to lie on it")
        void isMovedOnToTheScreen() {
            ShownWindow shown = show(100, 100, 350, 760);

            assertThat(shown.scale()).isEqualTo(1.0);
            assertThat(shown.across()).isEqualTo(284);
            assertThat(shown.down()).isEqualTo(700);
        }

        @Test
        @DisplayName("and one exactly the screen's size is not moved or made smaller")
        void anExactFitIsLeftAlone() {
            ShownWindow shown = show(384, 800, 0, 0);

            assertThat(shown.scale()).isEqualTo(1.0);
            assertThat(shown.across()).isZero();
            assertThat(shown.down()).isZero();
        }
    }

    @Nested
    @DisplayName("a window that cannot fit")
    class ThatCannotFit {

        @Test
        @DisplayName("too wide is made smaller by the factor that fits its width, at the top left")
        void tooWideFitsItsWidth() {
            ShownWindow shown = show(640, 360, 20, 20);

            assertThat(shown.scale()).isCloseTo(384.0 / 640, within(1e-9));
            assertThat(shown.across()).isZero();
            assertThat(shown.down()).isZero();
        }

        @Test
        @DisplayName("too tall is made smaller by the factor that fits its height")
        void tooTallFitsItsHeight() {
            ShownWindow shown = show(200, 1600, 0, 0);

            assertThat(shown.scale()).isCloseTo(0.5, within(1e-9));
        }

        @Test
        @DisplayName("too wide and too tall takes the smaller factor, so it keeps its proportions")
        void keepsItsProportions() {
            ShownWindow shown = show(768, 3200, 0, 0);

            assertThat(shown.scale()).isCloseTo(0.25, within(1e-9));
        }
    }

    @Nested
    @DisplayName("a pointer on it")
    class APointer {

        @Test
        @DisplayName("is counted from the window's own top left, at the window's own size")
        void isMappedBackIntoTheWindow() {
            ShownWindow shown = show(640, 360, 20, 20);

            assertThat(shown.mapBack(192, 108)).isEqualTo(new int[] {320, 180});
        }

        @Test
        @DisplayName("on a window moved on to the screen counts from where it was shown")
        void countsFromWhereItWasShown() {
            ShownWindow shown = show(100, 100, 350, 10);

            assertThat(shown.mapBack(300, 20)).isEqualTo(new int[] {16, 10});
        }

        @Test
        @DisplayName("lies on it when it falls inside what was shown, and not otherwise")
        void coversWhatWasShown() {
            ShownWindow shown = show(640, 360, 0, 0);

            assertThat(shown.covers(383, 215)).isTrue();
            assertThat(shown.covers(383, 217)).isFalse();
        }
    }

    @Nested
    @DisplayName("with the picture moved up for the keyboard")
    class RaisedForTheKeyboard {

        @Test
        @DisplayName("every window is shown that much higher")
        void isShownHigher() {
            ShownWindow shown = ShownWindow.of(PHONE, PairValue.of(384, 800), PairValue.of(0, 0), 240);

            assertThat(shown.down()).isEqualTo(-240);
        }

        @Test
        @DisplayName("and a pointer is counted as if the picture had not moved")
        void aPointerIsCountedAsIfItHadNot() {
            ShownWindow shown = ShownWindow.of(PHONE, PairValue.of(384, 800), PairValue.of(0, 0), 240);

            assertThat(shown.mapBack(10, 480)).isEqualTo(new int[] {10, 720});
        }
    }
}
