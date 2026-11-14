package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.jebol.domain.host.WindowArrangement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class GuiMetricSetFromTheSourceTest {

    private static final String TRUE = "#(true)";

    private static final String OPEN_A_WINDOW = """
            win: make gob! [size: 200x100 offset: 10x20]
            append system/view/screen-gob win
            show win
            """;

    private final RecordingScreen screen = RecordingScreen.measure(1024, 768);
    private final Interpreter interpreter = Interpreter.createOn(
            AnInterpreter.standardPorts().withScreen(screen),
            OutputPort.discard(),
            Bounds.standard().grant(HostService.WINDOWS));

    private String answerTo(String source) {
        return answerFrom(interpreter, source);
    }

    private static String answerFrom(Interpreter asked, String source) {
        asked.defineNewWordsIn(source);
        return asked.display(asked.run(source));
    }

    @Nested
    @DisplayName("on an open window")
    class OnAnOpenWindow {

        @ParameterizedTest(name = "{0}")
        @EnumSource(WindowArrangement.class)
        @DisplayName("each of the four asks the screen for that arrangement of that window")
        void asksTheScreen(WindowArrangement arrangement) {
            answerTo(OPEN_A_WINDOW + "gui-metric/set '" + arrangement.spelling() + " win");

            assertThat(screen.listArrangementsAskedFor()).containsExactly(arrangement);
            assertThat(screen.listWindowsArranged()).hasSize(1);
            assertThat(screen.listWindowsArranged().getFirst()
                    .sharesStorageWith(screen.listWhatOpened().getFirst())).isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"restore", "minimize", "maximize", "activate"})
        @DisplayName("and answers the window, as SHOW answers its argument")
        void answersTheWindow(String word) {
            assertThat(answerTo(OPEN_A_WINDOW + "same? win gui-metric/set '" + word + " win"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("one after another, in the order asked")
        void arrangesInOrder() {
            answerTo(OPEN_A_WINDOW + """
                    gui-metric/set 'minimize win
                    gui-metric/set 'restore win
                    gui-metric/set 'maximize win
                    gui-metric/set 'activate win""");

            assertThat(screen.listArrangementsAskedFor()).containsExactly(
                    WindowArrangement.MINIMIZE, WindowArrangement.RESTORE,
                    WindowArrangement.MAXIMIZE, WindowArrangement.ACTIVATE);
        }

        @Test
        @DisplayName("/display is ignored by the four")
        void ignoresDisplay() {
            assertThat(answerTo(OPEN_A_WINDOW + "same? win gui-metric/set/display 'maximize win 5"))
                    .isEqualTo(TRUE);
            assertThat(screen.listArrangementsAskedFor()).containsExactly(WindowArrangement.MAXIMIZE);
        }
    }

    @Nested
    @DisplayName("anything but an open window is invalid-arg naming it")
    class NotAnOpenWindow {

        @Test
        @DisplayName("a gob with no window")
        void refusesAGobWithNoWindow() {
            assertThat(answerTo("""
                    stray: make gob! [size: 10x10]
                    failed: try [gui-metric/set 'maximize stray]
                    reduce [failed/id same? stray failed/arg1]""")).isEqualTo("[invalid-arg #(true)]");
            assertThat(screen.listArrangementsAskedFor()).isEmpty();
        }

        @Test
        @DisplayName("a window that has been closed")
        void refusesAClosedWindow() {
            assertThat(answerTo(OPEN_A_WINDOW + """
                    remove find system/view/screen-gob win
                    show win
                    failed: try [gui-metric/set 'restore win]
                    reduce [failed/id same? win failed/arg1]""")).isEqualTo("[invalid-arg #(true)]");
        }

        @Test
        @DisplayName("an integer")
        void refusesAnInteger() {
            assertThat(answerTo("""
                    failed: try [gui-metric/set 'minimize 7]
                    reduce [failed/id failed/arg1]""")).isEqualTo("[invalid-arg 7]");
        }

        @Test
        @DisplayName("none")
        void refusesNone() {
            assertThat(answerTo("""
                    failed: try [gui-metric/set 'activate none]
                    reduce [failed/id none? failed/arg1]""")).isEqualTo("[invalid-arg #(true)]");
        }
    }

    @Nested
    @DisplayName("what does not change")
    class Unchanged {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"restore", "minimize", "maximize", "activate"})
        @DisplayName("without /set each is command-fail with arg1 none, as in the C")
        void needsSet(String word) {
            assertThat(answerTo(OPEN_A_WINDOW + """
                    failed: try [gui-metric '%s]
                    reduce [failed/id none? failed/arg1]""".formatted(word)))
                    .isEqualTo("[command-fail #(true)]");
            assertThat(screen.listArrangementsAskedFor()).isEmpty();
        }

        @Test
        @DisplayName("a metric under /set is still the metric, and the value is not read")
        void metricsIgnoreSet() {
            assertThat(answerTo("gui-metric/set 'screen-size 1x1")).isEqualTo("1024x768");
        }
    }

    @Nested
    @DisplayName("with no display")
    class WithNoDisplay {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"make gob! [size: 10x10]", "7", "none"})
        @DisplayName("is no-service, as SHOW's is, whatever was given")
        void isNoService(String given) {
            Interpreter walled = AnInterpreter.withBounds(Bounds.standard().grant(HostService.WINDOWS));

            assertThat(answerFrom(walled,
                    "failed: try [gui-metric/set 'maximize " + given + "] failed/id"))
                    .isEqualTo("no-service");
        }

        @Test
        @DisplayName("and so is a screen that says it has none")
        void isNoServiceOnAnAbsentScreen() {
            Interpreter onNothing = Interpreter.createOn(
                    AnInterpreter.standardPorts().withScreen(RecordingScreen.absent()),
                    OutputPort.discard(),
                    Bounds.standard().grant(HostService.WINDOWS));

            assertThat(answerFrom(onNothing,
                    "failed: try [gui-metric/set 'minimize make gob! []] failed/id"))
                    .isEqualTo("no-service");
        }
    }
}
