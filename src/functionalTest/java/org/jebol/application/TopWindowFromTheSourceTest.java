package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TopWindowFromTheSourceTest {

    private static final String TRUE = "#(true)";

    private static Interpreter makeAnInterpreterWithAScreen(RecordingScreen screen) {
        return Interpreter.createOn(
                Interpreter.standardPorts().withScreen(screen),
                OutputPort.discard(),
                Bounds.standard().grant(HostService.WINDOWS));
    }

    private static String answerFrom(RecordingScreen screen, String source) {
        Interpreter interpreter = makeAnInterpreterWithAScreen(screen);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("the command is used once and then spent")
    class TheOneShot {

        @Test
        @DisplayName("init-top-window holds a word, not a function, by the time a script runs")
        void isSpent() {
            assertThat(answerFrom(RecordingScreen.measure(1024, 768),
                    "init-top-window")).isEqualTo("done");
        }

        @Test
        @DisplayName("and so does init-view-system, which is what spent it")
        void soIsTheStarter() {
            assertThat(answerFrom(RecordingScreen.measure(1024, 768),
                    "init-view-system")).isEqualTo("done");
        }

        @Test
        @DisplayName("so a script cannot take the screen over after the fact")
        void keepsAScriptFromTakingTheScreenOver() {
            RecordingScreen screen = RecordingScreen.measure(1024, 768);
            Interpreter interpreter = makeAnInterpreterWithAScreen(screen);
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
        void sizesTheRootToTheScreen() {
            assertThat(answerFrom(RecordingScreen.measure(1024, 768),
                    "system/view/screen-gob/size")).isEqualTo("1024x768");
        }

        @Test
        @DisplayName("which is not the hundred by hundred a fresh gob starts at")
        void overwritesTheDefaultSize() {
            assertThat(answerFrom(RecordingScreen.measure(800, 600), """
                    not equal? 100x100 system/view/screen-gob/size"""))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a different screen gives a different size")
        void adifferentScreenGivesADifferentSize() {
            assertThat(answerFrom(RecordingScreen.measure(2560, 1440),
                    "system/view/screen-gob/size")).isEqualTo("2560x1440");
        }

        @Test
        @DisplayName("the root has no parent, because it is the root")
        void givesTheRootNoParent() {
            assertThat(answerFrom(RecordingScreen.measure(1024, 768),
                    "none? system/view/screen-gob/parent")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the screen was told which gob is the root")
        void tellsTheScreenWhichGobIsTheRoot() {
            RecordingScreen screen = RecordingScreen.measure(1024, 768);
            makeAnInterpreterWithAScreen(screen);

            assertThat(screen.rootGob())
                    .as("SHOW cannot tell reconciling the screen from refreshing "
                            + "one window without knowing which gob is the root")
                    .isNotNull();
        }
    }

    @Nested
    @DisplayName("the screen the interpreter was built with")
    class TheScreenItWasBuiltWith {

        @Test
        @DisplayName("a screen nobody has handed a root to has not got one")
        void leavesABareScreenWithoutARoot() {
            assertThat(RecordingScreen.measure(1024, 768).rootGob())
                    .as("the absent half of an optional field")
                    .isNull();
        }

        @Test
        @DisplayName("the root is measured from that screen, not left at nothing")
        void measuresTheRootFromTheScreen() {
            Interpreter interpreter = makeAnInterpreterWithAScreen(
                    RecordingScreen.measure(1024, 768));

            assertThat(interpreter.display(interpreter.run(
                    "system/view/screen-gob/size"))).isEqualTo("1024x768");
        }

        @Test
        @DisplayName("and each interpreter keeps the one it was built with")
        void keepsTheScreenItWasBuiltWith() {
            Interpreter wide = makeAnInterpreterWithAScreen(
                    RecordingScreen.measure(1024, 768));
            Interpreter narrow = makeAnInterpreterWithAScreen(
                    RecordingScreen.measure(640, 480));

            assertThat(wide.display(wide.run("system/view/screen-gob/size")))
                    .isEqualTo("1024x768");
            assertThat(narrow.display(narrow.run("system/view/screen-gob/size")))
                    .isEqualTo("640x480");
        }
    }

    @Nested
    @DisplayName("with no screen behind it")
    class WithNoScreen {

        @Test
        @DisplayName("the library still loaded, so there is a root gob")
        void stillHasARoot() {
            Interpreter walled = Interpreter.create();

            assertThat(walled.display(walled.run("gob? system/view/screen-gob")))
                    .as("view-funcs.reb loading whole is what puts it there")
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and it is sized at nothing")
        void sizesTheRootAtNothing() {
            assertThat(answerFrom(RecordingScreen.absent(),
                    "system/view/screen-gob/size")).isEqualTo("0x0");
        }

        @Test
        @DisplayName("which is still not the hundred by hundred it started at")
        void leavesTheDefaultSizeBehind() {
            assertThat(answerFrom(RecordingScreen.absent(), """
                    not equal? 100x100 system/view/screen-gob/size"""))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("an interpreter given no screen at all is the same case")
        void usesTheAbsentScreenByDefault() {
            Interpreter walled = Interpreter.create();

            assertThat(walled.display(walled.run("system/view/screen-gob/size")))
                    .isEqualTo("0x0");
        }
    }
}
