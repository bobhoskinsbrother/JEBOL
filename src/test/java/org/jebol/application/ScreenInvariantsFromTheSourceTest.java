package org.jebol.application;

import org.jebol.domain.eval.ScreenEventKind;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.GobValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScreenInvariantsFromTheSourceTest {

    private static final String TRUE = "#(true)";

    private static Interpreter withAScreen(RecordingScreen screen) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard()
                        .granting(HostService.WINDOWS)
                        .withWallClockLimit(Duration.ofSeconds(10)));
        interpreter.useScreen(screen);
        return interpreter;
    }

    private static final String A_WINDOW_AND_A_WATCHER = """
            view/no-wait make gob! [size: 100x100]
            seen: copy []
            handle-events [
                name: 'watcher
                priority: 90
                handler: func [event] [append seen event/type  event]
            ]
            """;

    @Nested
    @DisplayName("every open window hangs under the root gob")
    class TheTreeAndTheScreenAgree {

        @Test
        @DisplayName("and the windows the screen holds are the root's own children")
        @Timeout(20)
        void theOpenWindowsAreTheRootsChildren() {
            RecordingScreen screen = RecordingScreen.measuring(1024, 768);
            Interpreter interpreter = withAScreen(screen);
            interpreter.run("view/no-wait make gob! [size: 100x100]");
            interpreter.run("view/no-wait make gob! [size: 200x200]");

            assertThat(interpreter.display(interpreter.run("""
                    not-gobs: copy []
                    foreach g system/view/screen-gob [unless gob? g [append not-gobs g]]
                    mold not-gobs"""))).isEqualTo("\"[]\"");
            assertThat(screen.whatIsStandingOpen()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("nothing the screen reports runs on the screen's own thread")
    class TheOneThreadRule {

        @Test
        @DisplayName("an event queued from another thread runs no handler by itself")
        @Timeout(20)
        void anotherThreadRunsNothing() throws InterruptedException {
            RecordingScreen screen = RecordingScreen.measuring(1024, 768);
            Interpreter interpreter = withAScreen(screen);
            interpreter.defineFreshWordsIn(A_WINDOW_AND_A_WATCHER);
            interpreter.run(A_WINDOW_AND_A_WATCHER);

            screen.reportFromAnotherThread(
                    ScreenEventKind.KEY, screen.whatOpened().getFirst());

            assertThat(interpreter.display(interpreter.run("empty? seen")))
                    .as("the toolkit's thread queued it and did nothing else")
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and it is the interpreter's own thread that acts on it")
        @Timeout(20)
        void theInterpretersThreadActsOnIt() throws InterruptedException {
            RecordingScreen screen = RecordingScreen.measuring(1024, 768);
            Interpreter interpreter = withAScreen(screen);
            interpreter.defineFreshWordsIn(A_WINDOW_AND_A_WATCHER);
            interpreter.run(A_WINDOW_AND_A_WATCHER);

            screen.reportFromAnotherThread(
                    ScreenEventKind.KEY, screen.whatOpened().getFirst());
            screen.theOperatorDoes(
                    ScreenEventKind.CLOSE, screen.whatOpened().getFirst());
            interpreter.run("do-events");

            assertThat(interpreter.display(interpreter.run("mold seen")))
                    .as("and it arrives whole, having crossed a thread boundary")
                    .isEqualTo("\"[key close]\"");
        }

        @Test
        @DisplayName("many events from many threads all arrive, and none is lost")
        @Timeout(30)
        void nothingIsLostAcrossThreads() throws InterruptedException {
            RecordingScreen screen = RecordingScreen.measuring(1024, 768);
            Interpreter interpreter = withAScreen(screen);
            interpreter.defineFreshWordsIn(A_WINDOW_AND_A_WATCHER);
            interpreter.run(A_WINDOW_AND_A_WATCHER);

            GobValue window = screen.whatOpened().getFirst();
            List<Thread> toolkits = new java.util.ArrayList<>();
            for (int each = 0; each < 8; each++) {
                Thread reporting = new Thread(
                        () -> screen.theOperatorDoes(ScreenEventKind.KEY, window));
                toolkits.add(reporting);
                reporting.start();
            }
            for (Thread reporting : toolkits) {
                reporting.join();
            }
            screen.theOperatorDoes(ScreenEventKind.CLOSE, window);
            interpreter.run("do-events");

            assertThat(interpreter.display(interpreter.run("length? seen")))
                    .as("a queue that drops under contention would show up here")
                    .isEqualTo("9");
        }
    }
}
