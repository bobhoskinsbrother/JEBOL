package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ShowingGobsFromTheSourceTest {

    private static final String TRUE = "#(true)";

    private static Interpreter makeAnInterpreterWithAScreen(RecordingScreen screen) {
        return Interpreter.createOn(
                AnInterpreter.standardPorts().withScreen(screen),
                OutputPort.discard(),
                Bounds.standard().grant(HostService.WINDOWS));
    }

    private static String answerFrom(RecordingScreen screen, String source) {
        Interpreter interpreter = makeAnInterpreterWithAScreen(screen);
        return interpreter.display(interpreter.run(source));
    }

    private static RecordingScreen makeAScreen() {
        return RecordingScreen.measure(1024, 768);
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
        void opensAWindow() {
            RecordingScreen screen = makeAScreen();
            Interpreter interpreter = makeAnInterpreterWithAScreen(screen);
            interpreter.defineNewWordsIn(A_ROOT_WITH_ONE_WINDOW + "show w");
            interpreter.run(A_ROOT_WITH_ONE_WINDOW + "show w");

            assertThat(screen.listWhatOpened()).hasSize(1);
        }

        @Test
        @DisplayName("and showing the root opens every child that has not got one")
        void opensEveryChildOfTheRoot() {
            RecordingScreen screen = makeAScreen();
            Interpreter interpreter = makeAnInterpreterWithAScreen(screen);
            String source = """
                    root: system/view/screen-gob
                    append root make gob! [size: 200x100]
                    append root make gob! [size: 300x200]
                    show root""";
            interpreter.defineNewWordsIn(source);
            interpreter.run(source);

            assertThat(screen.listWhatOpened())
                    .as("OS_Show_Gob walks Gob_Root's whole pane")
                    .hasSize(2);
        }
    }

    @Nested
    @DisplayName("showing a gob whose window is already open")
    class TheRefreshing {

        @Test
        @DisplayName("repaints it where it stands")
        void repaintsRatherThanReopening() {
            RecordingScreen screen = makeAScreen();
            Interpreter interpreter = makeAnInterpreterWithAScreen(screen);
            String source = A_ROOT_WITH_ONE_WINDOW + """
                    show w
                    w/text: "changed"
                    show w""";
            interpreter.defineNewWordsIn(source);
            interpreter.run(source);

            assertThat(screen.listWhatWasRefreshed())
                    .as("OS_Update_Window, not OS_Open_Window, when the flag is set")
                    .hasSize(1);
        }

        @Test
        @DisplayName("and does not open a second window for the same gob")
        void doesNotOpenTwice() {
            RecordingScreen screen = makeAScreen();
            Interpreter interpreter = makeAnInterpreterWithAScreen(screen);
            String source = A_ROOT_WITH_ONE_WINDOW + """
                    show w  show w  show w""";
            interpreter.defineNewWordsIn(source);
            interpreter.run(source);

            assertThat(screen.listWhatOpened())
                    .as("three shows of one gob are one window")
                    .hasSize(1);
            assertThat(screen.listWhatIsStandingOpen()).hasSize(1);
        }

        @Test
        @DisplayName("and showing the root again refreshes rather than reopening")
        void opensOnceWhenTheRootIsShownTwice() {
            RecordingScreen screen = makeAScreen();
            Interpreter interpreter = makeAnInterpreterWithAScreen(screen);
            String source = A_ROOT_WITH_ONE_WINDOW + """
                    show root  show root""";
            interpreter.defineNewWordsIn(source);
            interpreter.run(source);

            assertThat(screen.listWhatOpened()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("showing a gob inside a window")
    class TheInside {

        private final RecordingScreen screen = makeAScreen();

        private final Interpreter interpreter = makeAnInterpreterWithAScreen(screen);

        private void run(String source) {
            interpreter.defineNewWordsIn(source);
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
        void repaintsAChildsWindow() {
            run(A_WINDOW_WITH_A_KNOB_TWO_DEEP + "show track");

            assertThat(screen.listWhatWasRefreshed()).hasSize(1);
            assertThat(screen.listWhatWasRefreshed().getFirst().sharesStorageWith(screen.listWhatOpened().getFirst()))
                    .isTrue();
        }

        @Test
        @DisplayName("however deep it sits")
        void repaintsAGrandchildsWindow() {
            run(A_WINDOW_WITH_A_KNOB_TWO_DEEP + "show knob");

            assertThat(screen.listWhatWasRefreshed()).hasSize(1);
            assertThat(screen.listWhatWasRefreshed().getFirst().sharesStorageWith(screen.listWhatOpened().getFirst()))
                    .isTrue();
        }

        @Test
        @DisplayName("and neither opens nor closes anything")
        void opensAndClosesNothing() {
            run(A_WINDOW_WITH_A_KNOB_TWO_DEEP + "show knob");

            assertThat(screen.listWhatOpened()).hasSize(1);
            assertThat(screen.listWhatClosed()).isEmpty();
        }

        @Test
        @DisplayName("of the two windows open, only the one holding it is repainted")
        void repaintsOnlyItsOwnWindow() {
            run(A_WINDOW_WITH_A_KNOB_TWO_DEEP + """
                    other: make gob! [size: 50x50]
                    append root other
                    show other
                    show knob
                    """);

            assertThat(screen.listWhatWasRefreshed()).hasSize(1);
            assertThat(screen.listWhatWasRefreshed().getFirst().sharesStorageWith(screen.listWhatOpened().getFirst()))
                    .isTrue();
        }

        @Test
        @DisplayName("a gob in a window not yet opened repaints nothing and opens nothing")
        void ignoresAGobInAnUnopenedWindow() {
            run("""
                    root: system/view/screen-gob
                    w: make gob! [size: 200x100]
                    knob: make gob! [size: 20x20]
                    append w knob
                    append root w
                    show knob
                    """);

            assertThat(screen.listWhatOpened()).isEmpty();
            assertThat(screen.listWhatWasRefreshed()).isEmpty();
        }

        @Test
        @DisplayName("a gob in no window at all repaints nothing and closes nothing")
        void ignoresALooseGob() {
            run(A_WINDOW_WITH_A_KNOB_TWO_DEEP + """
                    loose: make gob! [size: 20x20]
                    holder: make gob! [size: 40x40]
                    append holder loose
                    show loose
                    """);

            assertThat(screen.listWhatWasRefreshed()).isEmpty();
            assertThat(screen.listWhatClosed()).isEmpty();
        }

        @Test
        @DisplayName("a block of gobs is shown by nothing, as 3.22's SHOW acts on a gob alone")
        void showsNothingForABlock() {
            run(A_WINDOW_WITH_A_KNOB_TWO_DEEP + "show reduce [knob track]");

            assertThat(screen.listWhatWasRefreshed()).isEmpty();
        }
    }

    @Nested
    @DisplayName("showing a gob that has left the screen's pane")
    class TheClosing {

        @Test
        @DisplayName("closes its window, which is the only way anything ever closes")
        void closesTheWindow() {
            RecordingScreen screen = makeAScreen();
            Interpreter interpreter = makeAnInterpreterWithAScreen(screen);
            String source = A_ROOT_WITH_ONE_WINDOW + """
                    show w
                    remove find root w
                    show w""";
            interpreter.defineNewWordsIn(source);
            interpreter.run(source);

            assertThat(screen.listWhatClosed())
                    .as("UNVIEW removes from the pane and calls SHOW; the removal "
                            + "is what makes it a close")
                    .hasSize(1);
        }

        @Test
        @DisplayName("and a closed window stays closed, however often it is shown")
        void keepsAClosedWindowClosed() {
            RecordingScreen screen = makeAScreen();
            Interpreter interpreter = makeAnInterpreterWithAScreen(screen);
            String source = A_ROOT_WITH_ONE_WINDOW + """
                    show w
                    remove find root w
                    show w  show w  show w""";
            interpreter.defineNewWordsIn(source);
            interpreter.run(source);

            assertThat(screen.listWhatClosed())
                    .as("closing is where a window's life ends; showing a gob "
                            + "outside the pane again has nothing left to close")
                    .hasSize(1);
            assertThat(screen.listWhatIsStandingOpen()).isEmpty();
        }

        @Test
        @DisplayName("and showing the root closes what left the pane")
        void closesWhatLeftWhenTheRootIsShown() {
            RecordingScreen screen = makeAScreen();
            Interpreter interpreter = makeAnInterpreterWithAScreen(screen);
            String source = A_ROOT_WITH_ONE_WINDOW + """
                    show root
                    remove find root w
                    show root""";
            interpreter.defineNewWordsIn(source);
            interpreter.run(source);

            assertThat(screen.listWhatClosed()).hasSize(1);
            assertThat(screen.listWhatIsStandingOpen()).isEmpty();
        }
    }

    @Nested
    @DisplayName("what showing answers")
    class TheAnswer {

        @Test
        @DisplayName("whatever it was given, so a caller can chain on it")
        void answersWhatItWasGiven() {
            assertThat(answerFrom(makeAScreen(), A_ROOT_WITH_ONE_WINDOW + """
                    same? w show w""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("which is what lets VIEW end with `show window` and then `window`")
        void letsViewChain() {
            assertThat(answerFrom(makeAScreen(), A_ROOT_WITH_ONE_WINDOW + """
                    gob? show w""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("showing none does nothing and answers none, which UNVIEW relies on")
        void allowsShowingNone() {
            assertThat(answerFrom(makeAScreen(), """
                    none? show none"""))
                    .as("UNVIEW calls `show window` where window may be none, "
                            + "under a comment reading \"none ok\"")
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and showing none opens nothing")
        void opensNothingForNone() {
            RecordingScreen screen = makeAScreen();
            Interpreter interpreter = makeAnInterpreterWithAScreen(screen);
            interpreter.run("show none");

            assertThat(screen.listWhatOpened()).isEmpty();
        }
    }

    @Nested
    @DisplayName("showing needs a display, unlike the other two commands")
    class TheAbsentScreen {

        @Test
        @DisplayName("a machine with no screen refuses to be drawn on")
        void refusesWithNoDisplay() {
            assertThat(answerFrom(RecordingScreen.absent(), """
                    error? try [show system/view/screen-gob]"""))
                    .as("asking how big a screen is has an answer when there is "
                            + "none; putting a window on it does not")
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and it says the service is not present rather than not granted")
        void namesWhatIsMissing() {
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
        void refuseAnUndeclaredType(String written) {
            assertThat(answerFrom(makeAScreen(), "error? try [show " + written + "]"))
                    .as("show takes gob!, none! and block! and nothing else")
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("an interpreter that was not granted the screen refuses")
        void refuseAnUngrantedScript() {
            Interpreter walled = AnInterpreter.create();
            assertThat(walled.display(walled.run(
                    "error? try [show make gob! []]"))).isEqualTo(TRUE);
        }
    }
}
