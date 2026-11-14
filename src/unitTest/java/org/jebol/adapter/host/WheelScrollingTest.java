package org.jebol.adapter.host;

import org.jebol.domain.host.ScreenEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.event.InputEvent;

import static org.assertj.core.api.Assertions.assertThat;

class WheelScrollingTest {

    private static WheelScrolling.Turning taken(WheelScrolling wheel, double wheelRotation, boolean aWholePage) {
        return wheel.take(wheelRotation, aWholePage).orElseThrow();
    }

    @Nested
    @DisplayName("a whole notch of a wheel")
    class AWholeNotch {

        @Test
        @DisplayName("away from the operator is +30, which is the line the GUI expects")
        void awayIsPlusThirty() {
            WheelScrolling wheel = new WheelScrolling();
            WheelScrolling.Turning turning = taken(wheel, -1.0, false);
            assertThat(turning.kind()).isEqualTo(ScreenEventType.SCROLL_LINE);
            assertThat(turning.amount()).isEqualTo(30);
        }

        @Test
        @DisplayName("toward the operator is -30")
        void towardIsMinusThirty() {
            WheelScrolling wheel = new WheelScrolling();
            assertThat(taken(wheel, 1.0, false).amount()).isEqualTo(-30);
        }

        @Test
        @DisplayName("two notches in one event are two lines")
        void twoNotchesAreTwoLines() {
            WheelScrolling wheel = new WheelScrolling();
            assertThat(taken(wheel, 2.0, false).amount()).isEqualTo(-60);
            assertThat(taken(wheel, -3.0, false).amount()).isEqualTo(90);
        }

        @Test
        @DisplayName("a controlled wheel is a page, and a page is ten")
        void aPageIsTen() {
            WheelScrolling wheel = new WheelScrolling();
            WheelScrolling.Turning turning = taken(wheel, -1.0, true);
            assertThat(turning.kind()).isEqualTo(ScreenEventType.SCROLL_PAGE);
            assertThat(turning.amount()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("a trackpad, which turns in fractions")
    class Fractions {

        @Test
        @DisplayName("does not report until a whole notch has been turned")
        void waitsForAWholeNotch() {
            WheelScrolling wheel = new WheelScrolling();
            assertThat(wheel.take(-0.4, false)).isEmpty();
            assertThat(wheel.take(-0.4, false)).isEmpty();
            assertThat(taken(wheel, -0.4, false).amount()).isEqualTo(30);
        }

        @Test
        @DisplayName("keeps what was left over after a notch")
        void keepsTheRemainder() {
            WheelScrolling wheel = new WheelScrolling();
            assertThat(taken(wheel, -1.4, false).amount()).isEqualTo(30);
            assertThat(wheel.take(-0.5, false)).isEmpty();
            assertThat(taken(wheel, -0.2, false).amount()).isEqualTo(30);
        }

        @Test
        @DisplayName("truncates toward nought, so minus one and nine tenths is one notch and nine tenths left")
        void truncatesTowardNought() {
            WheelScrolling wheel = new WheelScrolling();
            assertThat(taken(wheel, -1.9, false).amount()).isEqualTo(30);
            assertThat(wheel.take(-0.05, false)).isEmpty();
        }

        @Test
        @DisplayName("and gives nothing at all for no turn")
        void nothingForNoTurn() {
            WheelScrolling wheel = new WheelScrolling();
            assertThat(wheel.take(0.0, false)).isEmpty();
            assertThat(wheel.take(0.0, true)).isEmpty();
        }
    }

    @Nested
    @DisplayName("a turn read from the toolkit's modifiers")
    class FromTheModifiers {

        @Test
        @DisplayName("is a line with no modifier held")
        void plainIsALine() {
            WheelScrolling wheel = new WheelScrolling();
            WheelScrolling.Turning turning = wheel.take(-1.0, 0, false).orElseThrow();
            assertThat(turning.kind()).isEqualTo(ScreenEventType.SCROLL_LINE);
            assertThat(turning.amount()).isEqualTo(30);
        }

        @Test
        @DisplayName("is a page with Control held, or when the machine scrolls by blocks")
        void controlOrABlockIsAPage() {
            assertThat(new WheelScrolling().take(-1.0, InputEvent.CTRL_DOWN_MASK, false).orElseThrow().kind())
                    .isEqualTo(ScreenEventType.SCROLL_PAGE);
            assertThat(new WheelScrolling().take(-1.0, 0, true).orElseThrow().kind())
                    .isEqualTo(ScreenEventType.SCROLL_PAGE);
        }

        @Test
        @DisplayName("is nothing with Shift, which is how Java hands over a sideways turn, as R3 posts nothing for one")
        void shiftIsASidewaysTurnAndNothing() {
            WheelScrolling wheel = new WheelScrolling();
            assertThat(wheel.take(-1.0, InputEvent.SHIFT_DOWN_MASK, false)).isEmpty();
            assertThat(wheel.take(3.0, InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK, true)).isEmpty();
        }

        @Test
        @DisplayName("and a sideways turn is not kept toward the next vertical notch")
        void aSidewaysTurnIsNotKept() {
            WheelScrolling wheel = new WheelScrolling();
            assertThat(wheel.take(-0.6, InputEvent.SHIFT_DOWN_MASK, false)).isEmpty();
            assertThat(wheel.take(-0.6, 0, false)).isEmpty();
        }
    }

    @Nested
    @DisplayName("a wheel that changes its mind")
    class Direction {

        @Test
        @DisplayName("has its turns cancel out before a notch is reported")
        void cancelsOppositeTurns() {
            WheelScrolling wheel = new WheelScrolling();
            assertThat(wheel.take(-0.6, false)).isEmpty();
            assertThat(wheel.take(0.6, false)).isEmpty();
            assertThat(wheel.take(0.4, false)).isEmpty();
            assertThat(taken(wheel, 0.6, false).amount()).isEqualTo(-30);
        }

        @Test
        @DisplayName("reports each whole notch even when the next event comes back")
        void reportsEachNotchEitherWay() {
            WheelScrolling wheel = new WheelScrolling();
            assertThat(taken(wheel, -1.0, false).amount()).isEqualTo(30);
            assertThat(taken(wheel, 1.0, false).amount()).isEqualTo(-30);
        }
    }
}