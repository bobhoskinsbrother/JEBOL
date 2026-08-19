package org.jebol.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The whole journey, through the only interface a person has: a REBOL script.
 *
 * <p>Nothing here calls a native directly or reaches for a Java method a
 * script cannot reach. A script describes a window, shows it, waits, and
 * carries on when the operator closes it, and every one of those steps is
 * written the way somebody would write it.
 *
 * <p>The screen behind it is a recording one rather than a real display,
 * because the gate runs with {@code java.awt.headless=true} and the real
 * adapter refuses on every machine the suite runs on. That is a substitution
 * at the port and nowhere else: setup says what the display measures and
 * plays the part of a person closing a window, and the test body writes
 * REBOL.
 *
 * <p>Specified in {@code spec/screen.allium}.
 */
class ViewingAWindowEndToEndTest {

    private static final String TRUE = "#(true)";

    private static Interpreter withAScreen(RecordingScreen screen) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard()
                        .granting(HostService.WINDOWS)
                        .withWallClockLimit(Duration.ofSeconds(10)));
        interpreter.useScreen(screen);
        return interpreter;
    }

    private static String sessionOn(RecordingScreen screen, String script) {
        Interpreter interpreter = withAScreen(screen);
        interpreter.defineFreshWordsIn(script);
        return interpreter.display(interpreter.run(script));
    }

    @Nested
    @DisplayName("a script that shows a window and waits")
    class TheHappyPath {

        @Test
        @DisplayName("opens one, and carries on when the operator closes it")
        @Timeout(20)
        void theWholeJourney() {
            RecordingScreen screen = RecordingScreen.measuring(1440, 900)
                    .whereTheOperatorClosesWhateverOpens();

            assertThat(sessionOn(screen, """
                    view make gob! [
                        size: 320x200
                        text: "Hello from JEBOL"
                        color: 40.40.60
                    ]
                    finished: true"""))
                    .as("VIEW returns when the last window closes, which is what "
                            + "makes a script ending in it a program")
                    .isEqualTo(TRUE);

            assertThat(screen.whatOpened())
                    .as("and a window really did appear on the way")
                    .hasSize(1);
        }

        @Test
        @DisplayName("and a script that asks not to wait carries on at once")
        @Timeout(20)
        void notWaitingReturnsImmediately() {
            RecordingScreen screen = RecordingScreen.measuring(1440, 900);

            assertThat(sessionOn(screen, """
                    view/no-wait make gob! [size: 320x200 text: "Quick"]
                    finished: true""")).isEqualTo(TRUE);

            assertThat(screen.whatOpened()).hasSize(1);
        }

        @Test
        @DisplayName("the window it shows is a window on the screen gob")
        @Timeout(20)
        void theWindowHangsUnderTheScreen() {
            assertThat(sessionOn(RecordingScreen.measuring(1440, 900), """
                    view/no-wait make gob! [size: 320x200]
                    1 = length? system/view/screen-gob""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and closing it by hand takes it off again")
        @Timeout(20)
        void unviewTakesItOff() {
            assertThat(sessionOn(RecordingScreen.measuring(1440, 900), """
                    w: view/no-wait make gob! [size: 320x200]
                    unview w
                    tail? system/view/screen-gob""")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("what a person meets when it cannot work")
    class TheUnhappyPaths {

        @Test
        @DisplayName("a machine with no display refuses, and says the screen is missing")
        @Timeout(20)
        void noDisplayRefuses() {
            assertThat(sessionOn(RecordingScreen.absent(), """
                    e: try [view/no-wait make gob! [size: 320x200]]
                    error? e"""))
                    .as("a headless server is the commonest place this runs")
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a layout block fails, because there is no VID to lay it out")
        @Timeout(20)
        void alayoutBlockFails() {
            // Not a gap in JEBOL: `layout` is defined nowhere in src/mezz or
            // src/boot, so a real 3.22.1 fails here too. There was a native
            // that answered its own argument, which made a VID program report
            // success and draw nothing -- a stub that says yes is worse than
            // the failure it hides.
            assertThat(sessionOn(RecordingScreen.measuring(1440, 900), """
                    e: try [view/no-wait [button "Press"]]
                    error? e""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and nothing here pretends to be VID")
        @Timeout(20)
        void nothingPretendsToBeVid() {
            assertThat(sessionOn(RecordingScreen.measuring(1440, 900), """
                    value? 'layout""")).isEqualTo("#(false)");
        }

        @Test
        @DisplayName("a script that was not granted the screen refuses")
        @Timeout(20)
        void withoutTheGrantItRefuses() {
            Interpreter walled = Interpreter.create();
            String script = """
                    error? try [view/no-wait make gob! [size: 320x200]]""";
            walled.defineFreshWordsIn(script);

            assertThat(walled.display(walled.run(script))).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the two refusals are told apart, because they need different fixes")
        @Timeout(20)
        void theTwoRefusalsAreDifferent() {
            // Both carry the id no-service and differ in what they say, which
            // is how every host service here reports the three refusal
            // reasons: the id names the kind of failure and the message says
            // which of not granted, not present and nothing can offer it.
            assertThat(whatViewingSays(Interpreter.create()))
                    .contains("not granted");
            assertThat(whatViewingSays(withAScreen(RecordingScreen.absent())))
                    .as("one can be fixed by granting and the other cannot")
                    .contains("not present");
        }

        private String whatViewingSays(Interpreter interpreter) {
            String script = """
                    e: try [view/no-wait make gob! [size: 320x200]]
                    either error? e [form e/arg1] ["no error at all"]""";
            interpreter.defineFreshWordsIn(script);
            return interpreter.display(interpreter.run(script));
        }
    }

    @Nested
    @DisplayName("two windows")
    class TheSecondWindow {

        @Test
        @DisplayName("both appear, and the screen holds both")
        @Timeout(20)
        void bothAppear() {
            RecordingScreen screen = RecordingScreen.measuring(1440, 900);

            assertThat(sessionOn(screen, """
                    view/no-wait make gob! [size: 200x100 text: "One"]
                    view/no-wait make gob! [size: 200x100 text: "Two"]
                    2 = length? system/view/screen-gob""")).isEqualTo(TRUE);

            assertThat(screen.whatOpened()).hasSize(2);
        }

        @Test
        @DisplayName("and closing one leaves the other standing")
        @Timeout(20)
        void closingOneLeavesTheOther() {
            assertThat(sessionOn(RecordingScreen.measuring(1440, 900), """
                    first-window:  view/no-wait make gob! [size: 200x100]
                    second-window: view/no-wait make gob! [size: 200x100]
                    unview first-window
                    1 = length? system/view/screen-gob""")).isEqualTo(TRUE);
        }
    }
}
