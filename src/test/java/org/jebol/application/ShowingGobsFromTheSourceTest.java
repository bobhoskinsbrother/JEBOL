package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ShowingGobsFromTheSourceTest {

    private static final String TRUE = "#(true)";

    private static Interpreter withAScreen(RecordingScreen screen) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.WINDOWS));
        interpreter.useScreen(screen);
        return interpreter;
    }

    private static String answerFrom(RecordingScreen screen, String source) {
        Interpreter interpreter = withAScreen(screen);
        return interpreter.display(interpreter.run(source));
    }

    private static RecordingScreen aScreen() {
        return RecordingScreen.measuring(1024, 768);
    }

    private static final String A_ROOT_WITH_ONE_WINDOW = """
            root: system/view/screen-gob
            w: make gob! [size: 200x100]
            append root w
            """;

    @Nested
    @DisplayName("showing a gob in the screen's pane")
    class TheOpening {

        @Test
        @DisplayName("opens a window for it")
        void itOpensAWindow() {
            RecordingScreen screen = aScreen();
            Interpreter interpreter = withAScreen(screen);
            interpreter.defineFreshWordsIn(A_ROOT_WITH_ONE_WINDOW + "show w");
            interpreter.run(A_ROOT_WITH_ONE_WINDOW + "show w");

            assertThat(screen.whatOpened()).hasSize(1);
        }

        @Test
        @DisplayName("and showing the root opens every child that has not got one")
        void showingTheRootOpensEveryChild() {
            RecordingScreen screen = aScreen();
            Interpreter interpreter = withAScreen(screen);
            String source = """
                    root: system/view/screen-gob
                    append root make gob! [size: 200x100]
                    append root make gob! [size: 300x200]
                    show root""";
            interpreter.defineFreshWordsIn(source);
            interpreter.run(source);

            assertThat(screen.whatOpened())
                    .as("OS_Show_Gob walks Gob_Root's whole pane")
                    .hasSize(2);
        }
    }

    @Nested
    @DisplayName("showing a gob whose window is already open")
    class TheRefreshing {

        @Test
        @DisplayName("repaints it where it stands")
        void itRepaintsRatherThanReopening() {
            RecordingScreen screen = aScreen();
            Interpreter interpreter = withAScreen(screen);
            String source = A_ROOT_WITH_ONE_WINDOW + """
                    show w
                    w/text: "changed"
                    show w""";
            interpreter.defineFreshWordsIn(source);
            interpreter.run(source);

            assertThat(screen.whatWasRefreshed())
                    .as("OS_Update_Window, not OS_Open_Window, when the flag is set")
                    .hasSize(1);
        }

        @Test
        @DisplayName("and does not open a second window for the same gob")
        void itDoesNotOpenTwice() {
            RecordingScreen screen = aScreen();
            Interpreter interpreter = withAScreen(screen);
            String source = A_ROOT_WITH_ONE_WINDOW + """
                    show w  show w  show w""";
            interpreter.defineFreshWordsIn(source);
            interpreter.run(source);

            assertThat(screen.whatOpened())
                    .as("three shows of one gob are one window")
                    .hasSize(1);
            assertThat(screen.whatIsStandingOpen()).hasSize(1);
        }

        @Test
        @DisplayName("and showing the root again refreshes rather than reopening")
        void showingTheRootTwiceOpensOnce() {
            RecordingScreen screen = aScreen();
            Interpreter interpreter = withAScreen(screen);
            String source = A_ROOT_WITH_ONE_WINDOW + """
                    show root  show root""";
            interpreter.defineFreshWordsIn(source);
            interpreter.run(source);

            assertThat(screen.whatOpened()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("showing a gob inside a window")
    class TheInside {

        private final RecordingScreen screen = aScreen();

        private final Interpreter interpreter = withAScreen(screen);

        private void running(String source) {
            interpreter.defineFreshWordsIn(source);
            interpreter.run(source);
        }

        private static final String A_WINDOW_WITH_A_KNOB_TWO_DEEP = """
                root: system/view/screen-gob
                w: make gob! [size: 200x100]
                track: make gob! [size: 180x20]
                knob: make gob! [size: 20x20]
                append track knob
                append w track
                append root w
                show w
                """;

        @Test
        @DisplayName("repaints the window holding it, as a face does each time it changes")
        void aChildRepaintsItsWindow() {
            running(A_WINDOW_WITH_A_KNOB_TWO_DEEP + "show track");

            assertThat(screen.whatWasRefreshed()).hasSize(1);
            assertThat(screen.whatWasRefreshed().getFirst().sharesStorageWith(screen.whatOpened().getFirst()))
                    .isTrue();
        }

        @Test
        @DisplayName("however deep it sits")
        void aGrandchildRepaintsItsWindow() {
            running(A_WINDOW_WITH_A_KNOB_TWO_DEEP + "show knob");

            assertThat(screen.whatWasRefreshed()).hasSize(1);
            assertThat(screen.whatWasRefreshed().getFirst().sharesStorageWith(screen.whatOpened().getFirst()))
                    .isTrue();
        }

        @Test
        @DisplayName("and neither opens nor closes anything")
        void itOpensAndClosesNothing() {
            running(A_WINDOW_WITH_A_KNOB_TWO_DEEP + "show knob");

            assertThat(screen.whatOpened()).hasSize(1);
            assertThat(screen.whatClosed()).isEmpty();
        }

        @Test
        @DisplayName("of the two windows open, only the one holding it is repainted")
        void onlyItsOwnWindowIsRepainted() {
            running(A_WINDOW_WITH_A_KNOB_TWO_DEEP + """
                    other: make gob! [size: 50x50]
                    append root other
                    show other
                    show knob
                    """);

            assertThat(screen.whatWasRefreshed()).hasSize(1);
            assertThat(screen.whatWasRefreshed().getFirst().sharesStorageWith(screen.whatOpened().getFirst()))
                    .isTrue();
        }

        @Test
        @DisplayName("a gob in a window not yet opened repaints nothing and opens nothing")
        void aGobInAnUnopenedWindowDoesNothing() {
            running("""
                    root: system/view/screen-gob
                    w: make gob! [size: 200x100]
                    knob: make gob! [size: 20x20]
                    append w knob
                    append root w
                    show knob
                    """);

            assertThat(screen.whatOpened()).isEmpty();
            assertThat(screen.whatWasRefreshed()).isEmpty();
        }

        @Test
        @DisplayName("a gob in no window at all repaints nothing and closes nothing")
        void aLooseGobDoesNothing() {
            running(A_WINDOW_WITH_A_KNOB_TWO_DEEP + """
                    loose: make gob! [size: 20x20]
                    holder: make gob! [size: 40x40]
                    append holder loose
                    show loose
                    """);

            assertThat(screen.whatWasRefreshed()).isEmpty();
            assertThat(screen.whatClosed()).isEmpty();
        }

        @Test
        @DisplayName("a block of gobs is shown by nothing, as 3.22's SHOW acts on a gob alone")
        void aBlockIsShownByNothing() {
            running(A_WINDOW_WITH_A_KNOB_TWO_DEEP + "show reduce [knob track]");

            assertThat(screen.whatWasRefreshed()).isEmpty();
        }
    }

    @Nested
    @DisplayName("showing a gob that has left the screen's pane")
    class TheClosing {

        @Test
        @DisplayName("closes its window, which is the only way anything ever closes")
        void itClosesTheWindow() {
            RecordingScreen screen = aScreen();
            Interpreter interpreter = withAScreen(screen);
            String source = A_ROOT_WITH_ONE_WINDOW + """
                    show w
                    remove find root w
                    show w""";
            interpreter.defineFreshWordsIn(source);
            interpreter.run(source);

            assertThat(screen.whatClosed())
                    .as("UNVIEW removes from the pane and calls SHOW; the removal "
                            + "is what makes it a close")
                    .hasSize(1);
        }

        @Test
        @DisplayName("and a closed window stays closed, however often it is shown")
        void aClosedWindowDoesNotComeBack() {
            RecordingScreen screen = aScreen();
            Interpreter interpreter = withAScreen(screen);
            String source = A_ROOT_WITH_ONE_WINDOW + """
                    show w
                    remove find root w
                    show w  show w  show w""";
            interpreter.defineFreshWordsIn(source);
            interpreter.run(source);

            assertThat(screen.whatClosed())
                    .as("closing is where a window's life ends; showing a gob "
                            + "outside the pane again has nothing left to close")
                    .hasSize(1);
            assertThat(screen.whatIsStandingOpen()).isEmpty();
        }

        @Test
        @DisplayName("and showing the root closes what left the pane")
        void showingTheRootClosesWhatLeft() {
            RecordingScreen screen = aScreen();
            Interpreter interpreter = withAScreen(screen);
            String source = A_ROOT_WITH_ONE_WINDOW + """
                    show root
                    remove find root w
                    show root""";
            interpreter.defineFreshWordsIn(source);
            interpreter.run(source);

            assertThat(screen.whatClosed()).hasSize(1);
            assertThat(screen.whatIsStandingOpen()).isEmpty();
        }
    }

    @Nested
    @DisplayName("what showing answers")
    class TheAnswer {

        @Test
        @DisplayName("whatever it was given, so a caller can chain on it")
        void itAnswersWhatItWasGiven() {
            assertThat(answerFrom(aScreen(), A_ROOT_WITH_ONE_WINDOW + """
                    same? w show w""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("which is what lets VIEW end with `show window` and then `window`")
        void thatIsWhyViewCanChain() {
            assertThat(answerFrom(aScreen(), A_ROOT_WITH_ONE_WINDOW + """
                    gob? show w""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("showing none does nothing and answers none, which UNVIEW relies on")
        void showingNoneIsAllowed() {
            assertThat(answerFrom(aScreen(), """
                    none? show none"""))
                    .as("UNVIEW calls `show window` where window may be none, "
                            + "under a comment reading \"none ok\"")
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and showing none opens nothing")
        void showingNoneOpensNothing() {
            RecordingScreen screen = aScreen();
            Interpreter interpreter = withAScreen(screen);
            interpreter.run("show none");

            assertThat(screen.whatOpened()).isEmpty();
        }
    }

    @Nested
    @DisplayName("showing needs a display, unlike the other two commands")
    class TheAbsentScreen {

        @Test
        @DisplayName("a machine with no screen refuses to be drawn on")
        void itRefusesWithNoDisplay() {
            assertThat(answerFrom(RecordingScreen.absent(), """
                    error? try [show system/view/screen-gob]"""))
                    .as("asking how big a screen is has an answer when there is "
                            + "none; putting a window on it does not")
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and it says the service is not present rather than not granted")
        void theRefusalNamesWhatIsMissing() {
            assertThat(answerFrom(RecordingScreen.absent(), """
                    e: try [show system/view/screen-gob]
                    true? find form e/id {no-service}"""))
                    .as("granted with nothing behind it is a different fact from "
                            + "not granted, and a script may act on either")
                    .isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("what it refuses")
    class TheRefusals {

        @ParameterizedTest
        @ValueSource(strings = {"5", "\"gob\"", "1x1", "#(true)"})
        @DisplayName("a value the declaration does not accept is refused")
        void anUndeclaredTypeIsRefused(String written) {
            assertThat(answerFrom(aScreen(), "error? try [show " + written + "]"))
                    .as("show takes gob!, none! and block! and nothing else")
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("an interpreter that was not granted the screen refuses")
        void anUngrantedScriptIsRefused() {
            Interpreter walled = Interpreter.create();
            assertThat(walled.display(walled.run(
                    "error? try [show make gob! []]"))).isEqualTo(TRUE);
        }
    }
}
