package org.jebol.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * INIT-TOP-WINDOW: the gob every window hangs under, and the command that is
 * spent as soon as it has been used.
 *
 * <p>Three lines in {@code src/os/posix/host-window.c} and all three matter:
 * {@code Gob_Root = ...} remembers the gob, {@code Gob_Root->parent = NULL}
 * cuts it loose, and two calls to {@code OS_Get_Metrics} write the screen's
 * size onto it.
 *
 * <p>The size is the one that catches people out. VIEW centres a window with
 * {@code screen/size - window/size / 2}, so a root of the wrong size puts
 * every centred window in the wrong place and a root of no size puts them all
 * in the same place.
 *
 * <p>Nothing here calls the command by name, and that is the behaviour rather
 * than an omission. INIT-VIEW-SYSTEM ends with {@code init-top-window:
 * init-view-system: 'done}, so both words hold a word by the time any script
 * runs -- the view system may be started once and nothing may take the screen
 * over afterwards. In a real 3.22.1 that costs nothing, because the graphics
 * host is registered before the library loads. In JEBOL the screen arrives
 * after the interpreter is built, which is what
 * {@code ScreenPort.takeAsTheRoot} exists for.
 *
 * <p>It answers rather than refuses on a machine with no display. INIT-VIEW-SYSTEM
 * runs while {@code view-funcs.reb} is still loading, so refusing would stop
 * that file partway on any machine without a screen -- and a build server is
 * exactly that, so the library under test would be a different library from
 * the one that ships.
 *
 * <p>Specified in {@code spec/screen.allium}.
 */
class TopWindowFromTheSourceTest {

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

    @Nested
    @DisplayName("the command is used once and then spent")
    class TheOneShot {

        @Test
        @DisplayName("init-top-window holds a word, not a function, by the time a script runs")
        void theCommandIsSpent() {
            assertThat(answerFrom(RecordingScreen.measuring(1024, 768),
                    "init-top-window")).isEqualTo("done");
        }

        @Test
        @DisplayName("and so does init-view-system, which is what spent it")
        void soIsTheStarter() {
            assertThat(answerFrom(RecordingScreen.measuring(1024, 768),
                    "init-view-system")).isEqualTo("done");
        }

        @Test
        @DisplayName("so a script cannot take the screen over after the fact")
        void aScriptCannotTakeTheScreenOver() {
            RecordingScreen screen = RecordingScreen.measuring(1024, 768);
            Interpreter interpreter = withAScreen(screen);
            interpreter.run("init-top-window make gob! [size: 42x42]");

            assertThat(interpreter.display(interpreter.run(
                    "system/view/screen-gob/size")))
                    .as("the call evaluated to a word and moved nothing")
                    .isEqualTo("1024x768");
        }
    }

    @Nested
    @DisplayName("with a screen behind it")
    class WithAScreen {

        @Test
        @DisplayName("the root gob takes the screen's size")
        void theRootTakesTheScreensSize() {
            assertThat(answerFrom(RecordingScreen.measuring(1024, 768),
                    "system/view/screen-gob/size")).isEqualTo("1024x768");
        }

        @Test
        @DisplayName("which is not the hundred by hundred a fresh gob starts at")
        void itOverwritesTheDefaultSize() {
            assertThat(answerFrom(RecordingScreen.measuring(800, 600), """
                    not equal? 100x100 system/view/screen-gob/size"""))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a different screen gives a different size")
        void adifferentScreenGivesADifferentSize() {
            assertThat(answerFrom(RecordingScreen.measuring(2560, 1440),
                    "system/view/screen-gob/size")).isEqualTo("2560x1440");
        }

        @Test
        @DisplayName("the root has no parent, because it is the root")
        void theRootHasNoParent() {
            assertThat(answerFrom(RecordingScreen.measuring(1024, 768),
                    "none? system/view/screen-gob/parent")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the screen was told which gob is the root")
        void theScreenIsToldWhichGobIsTheRoot() {
            RecordingScreen screen = RecordingScreen.measuring(1024, 768);
            withAScreen(screen);

            assertThat(screen.rootGob())
                    .as("SHOW cannot tell reconciling the screen from refreshing "
                            + "one window without knowing which gob is the root")
                    .isNotNull();
        }
    }

    @Nested
    @DisplayName("when the screen arrives after the library has loaded")
    class TheLateScreen {

        @Test
        @DisplayName("a screen nobody has handed a root to has not got one")
        void aBareScreenHasNoRoot() {
            assertThat(RecordingScreen.measuring(1024, 768).rootGob())
                    .as("the absent half of an optional field")
                    .isNull();
        }

        @Test
        @DisplayName("and the root is re-measured, so it is not left at nothing")
        void theRootIsRemeasured() {
            Interpreter interpreter = Interpreter.withBounds(
                    Bounds.standard().granting(HostService.WINDOWS));

            assertThat(interpreter.display(interpreter.run(
                    "system/view/screen-gob/size")))
                    .as("before a screen arrives, the root was sized against none")
                    .isEqualTo("0x0");

            interpreter.useScreen(RecordingScreen.measuring(1024, 768));

            assertThat(interpreter.display(interpreter.run(
                    "system/view/screen-gob/size")))
                    .as("and it is brought up to date when one does")
                    .isEqualTo("1024x768");
        }

        @Test
        @DisplayName("a second screen moves it again")
        void asecondScreenMovesItAgain() {
            Interpreter interpreter = Interpreter.withBounds(
                    Bounds.standard().granting(HostService.WINDOWS));
            interpreter.useScreen(RecordingScreen.measuring(1024, 768));
            interpreter.useScreen(RecordingScreen.measuring(640, 480));

            assertThat(interpreter.display(interpreter.run(
                    "system/view/screen-gob/size"))).isEqualTo("640x480");
        }
    }

    @Nested
    @DisplayName("with no screen behind it")
    class WithNoScreen {

        @Test
        @DisplayName("the library still loaded, so there is a root gob")
        void thereIsStillARoot() {
            Interpreter walled = Interpreter.create();

            assertThat(walled.display(walled.run("gob? system/view/screen-gob")))
                    .as("view-funcs.reb loading whole is what puts it there")
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and it is sized at nothing")
        void theRootIsSizedAtNothing() {
            assertThat(answerFrom(RecordingScreen.absent(),
                    "system/view/screen-gob/size")).isEqualTo("0x0");
        }

        @Test
        @DisplayName("which is still not the hundred by hundred it started at")
        void nothingIsNotTheDefault() {
            assertThat(answerFrom(RecordingScreen.absent(), """
                    not equal? 100x100 system/view/screen-gob/size"""))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("an interpreter given no screen at all is the same case")
        void theDefaultPortIsTheAbsentOne() {
            Interpreter walled = Interpreter.create();

            assertThat(walled.display(walled.run("system/view/screen-gob/size")))
                    .isEqualTo("0x0");
        }
    }
}
