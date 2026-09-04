package org.jebol.adapter.host;

import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.eval.WindowPort;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.awt.*;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * The window adapter, tested on the one path a server actually takes.
 *
 * <p>Four of the five requests put a dialog on a screen and wait for a person,
 * so nothing here drives them: a test that opened a file chooser would hang
 * the build until someone closed it. What is testable is the refusal, and it
 * is the more important half -- a headless JVM is what production is, and
 * Swing throws an {@code AWTError} there rather than an exception. An error is
 * exactly what must never escape a script, because the promise is that every
 * failure arrives as a catchable {@code error!}.
 *
 * <p>So this asserts that the adapter refuses before Swing is touched, and
 * that the refusal reaches a script as an ordinary error rather than as a
 * throwable out of the interpreter.
 */
class DesktopWindowsTest {

    /**
     * Runs only where there is no display.
     *
     * <p>EnabledIf and not DisabledIf, and getting that the wrong way round
     * cost a killed build: DisabledIf on the same condition turns these off
     * exactly where they are meant to run, and then the two tests that use a
     * real adapter opened a colour chooser on a developer's screen and waited
     * for someone to close it.
     */
    private static final String ONLY_HEADLESS =
            "java.awt.GraphicsEnvironment#isHeadless";

    @Nested
    @DisplayName("with no screen, which is what a server is")
    @EnabledIf(value = ONLY_HEADLESS, disabledReason = "this machine has a display")
    class Headless {

        @Test
        @DisplayName("every request refuses rather than throwing an AWTError")
        void everyRequestRefuses() {
            WindowPort screen = DesktopWindows.onThisMachine();

            assertThatExceptionOfType(WindowPort.Denied.class)
                    .isThrownBy(() -> screen.browse("http://example.com"));
            assertThatExceptionOfType(WindowPort.Denied.class)
                    .isThrownBy(() -> screen.chooseFiles(
                            false, false, Optional.empty(), Optional.empty(), List.of()));
            assertThatExceptionOfType(WindowPort.Denied.class)
                    .isThrownBy(() -> screen.chooseDirectory(
                            Optional.empty(), Optional.empty()));
            assertThatExceptionOfType(WindowPort.Denied.class)
                    .isThrownBy(() -> screen.chooseColour(Optional.empty()));
            assertThatExceptionOfType(WindowPort.Denied.class)
                    .isThrownBy(screen::askForPassword);
        }

        @Test
        @DisplayName("the refusal says there is no screen, not that one was withheld")
        void theRefusalSaysWhy() {
            WindowPort screen = DesktopWindows.onThisMachine();

            assertThatExceptionOfType(WindowPort.Denied.class)
                    .isThrownBy(() -> screen.chooseColour(Optional.empty()))
                    .withMessageContaining("no screen");
        }

        @Test
        @DisplayName("and reaches a script as a catchable error, not a throwable")
        void theRefusalReachesTheScriptAsAnError() {
            Interpreter interpreter = Interpreter.withBounds(
                    Bounds.standard().granting(HostService.WINDOWS));
            interpreter.useWindows(DesktopWindows.onThisMachine());

            String source = "e: try [request-color] either error? e [e/id] ['no-error]";
            interpreter.defineFreshWordsIn(source);

            assertThat(interpreter.display(interpreter.run(source)))
                    .isEqualTo("no-service");
        }
    }

    @Nested
    @DisplayName("whatever this machine is")
    class Always {

        @Test
        @DisplayName("the adapter is a WindowPort, so a host can hand it straight over")
        void itFitsThePort() {
            assertThat(DesktopWindows.onThisMachine()).isInstanceOf(WindowPort.class);
        }

        @Test
        @DisplayName("granting the service without handing over a screen still refuses")
        void theGrantAloneIsNotEnough() {
            Interpreter granted = Interpreter.withBounds(
                    Bounds.standard().granting(HostService.WINDOWS));

            String source = "e: try [request-dir] either error? e [e/id] ['no-error]";
            granted.defineFreshWordsIn(source);

            assertThat(granted.display(granted.run(source))).isEqualTo("no-service");
        }

        @Test
        @DisplayName("a declined dialog is an empty answer, which the port models as empty")
        void decliningIsModelledAsEmpty() {
            WindowPort declining = new WindowPort() {
                @Override
                public void browse(String target) {
                }

                @Override
                public List<String> chooseFiles(
                        boolean forSaving, boolean allowingMany,
                        Optional<String> suggestedName, Optional<String> title,
                        List<String> filterPairs) {
                    return List.of();
                }

                @Override
                public Optional<String> chooseDirectory(
                        Optional<String> startingAt, Optional<String> title) {
                    return Optional.empty();
                }

                @Override
                public Optional<int[]> chooseColour(Optional<int[]> suggested) {
                    return Optional.empty();
                }

                @Override
                public Optional<String> askForPassword() {
                    return Optional.empty();
                }
            };

            Interpreter interpreter = Interpreter.withBounds(
                    Bounds.standard().granting(HostService.WINDOWS));
            interpreter.useWindows(declining);

            String source = "none? request-color";
            interpreter.defineFreshWordsIn(source);
            assertThat(interpreter.display(interpreter.run(source))).isEqualTo("#(true)");
        }
    }

    @Test
    @DisplayName("this build knows whether it has a display, so the skips are honest")
    void theHeadlessCheckIsMeaningful() {
        assertThat(GraphicsEnvironment.isHeadless())
                .as("headless here: %s", GraphicsEnvironment.isHeadless())
                .isIn(true, false);
    }
}
