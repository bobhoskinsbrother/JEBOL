package org.jebol.application;

import org.jebol.domain.host.ScreenEventType;
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

    private static Interpreter makeAnInterpreterWithAScreen(RecordingScreen screen) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard()
                        .grant(HostService.WINDOWS)
                        .withTimeLimit(Duration.ofSeconds(10)));
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
        void listTheOpenWindowsAsTheRootsChildren() {
            RecordingScreen screen = RecordingScreen.measure(1024, 768);
            Interpreter interpreter = makeAnInterpreterWithAScreen(screen);
            interpreter.run("view/no-wait make gob! [size: 100x100]");
            interpreter.run("view/no-wait make gob! [size: 200x200]");

            assertThat(interpreter.display(interpreter.run("""
                    not-gobs: copy []
                    foreach g system/view/screen-gob/pane [unless gob? g [append not-gobs g]]
                    mold not-gobs"""))).isEqualTo("\"[]\"");
            assertThat(screen.listWhatIsStandingOpen()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("nothing the screen reports runs on the screen's own thread")
    class TheOneThreadRule {

        @Test
        @DisplayName("an event queued from another thread runs no handler by itself")
        @Timeout(20)
        void letsAnotherThreadRunNothing() throws InterruptedException {
            RecordingScreen screen = RecordingScreen.measure(1024, 768);
            Interpreter interpreter = makeAnInterpreterWithAScreen(screen);
            interpreter.defineFreshWordsIn(A_WINDOW_AND_A_WATCHER);
            interpreter.run(A_WINDOW_AND_A_WATCHER);

            screen.reportFromAnotherThread(
                    ScreenEventType.KEY, screen.listWhatOpened().getFirst());

            assertThat(interpreter.display(interpreter.run("empty? seen")))
                    .as("the toolkit's thread queued it and did nothing else")
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and it is the interpreter's own thread that acts on it")
        @Timeout(20)
        void actsOnTheInterpretersThread() throws InterruptedException {
            RecordingScreen screen = RecordingScreen.measure(1024, 768);
            Interpreter interpreter = makeAnInterpreterWithAScreen(screen);
            interpreter.defineFreshWordsIn(A_WINDOW_AND_A_WATCHER);
            interpreter.run(A_WINDOW_AND_A_WATCHER);

            screen.reportFromAnotherThread(
                    ScreenEventType.KEY, screen.listWhatOpened().getFirst());
            screen.actAsTheOperator(
                    ScreenEventType.CLOSE, screen.listWhatOpened().getFirst());
            interpreter.run("do-events");

            assertThat(interpreter.display(interpreter.run("mold seen")))
                    .as("and it arrives whole, having crossed a thread boundary")
                    .isEqualTo("\"[key close]\"");
        }

        @Test
        @DisplayName("many events from many threads all arrive, and none is lost")
        @Timeout(30)
        void losesNothingAcrossThreads() throws InterruptedException {
            RecordingScreen screen = RecordingScreen.measure(1024, 768);
            Interpreter interpreter = makeAnInterpreterWithAScreen(screen);
            interpreter.defineFreshWordsIn(A_WINDOW_AND_A_WATCHER);
            interpreter.run(A_WINDOW_AND_A_WATCHER);

            GobValue window = screen.listWhatOpened().getFirst();
            List<Thread> toolkits = new java.util.ArrayList<>();
            for (int each = 0; each < 8; each++) {
                Thread reporting = new Thread(
                        () -> screen.actAsTheOperator(ScreenEventType.KEY, window));
                toolkits.add(reporting);
                reporting.start();
            }
            for (Thread reporting : toolkits) {
                reporting.join();
            }
            screen.actAsTheOperator(ScreenEventType.CLOSE, window);
            interpreter.run("do-events");

            assertThat(interpreter.display(interpreter.run("length? seen")))
                    .as("a queue that drops under contention would show up here")
                    .isEqualTo("9");
        }

        @Test
        @DisplayName("an event posted while the interpreter is inside the wait is taken by it")
        @Timeout(20)
        void takesAnEventPostedDuringTheWait() throws InterruptedException {
            RecordingScreen screen = RecordingScreen.measure(1024, 768);
            Interpreter interpreter = makeAnInterpreterWithAScreen(screen);
            interpreter.defineFreshWordsIn(A_WINDOW_AND_A_WATCHER);
            interpreter.run(A_WINDOW_AND_A_WATCHER);
            GobValue window = screen.listWhatOpened().getFirst();

            Thread toolkit = new Thread(() -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException stopped) {
                    Thread.currentThread().interrupt();
                    return;
                }
                screen.actAsTheOperator(ScreenEventType.CLOSE, window);
            }, "toolkit");
            toolkit.start();
            interpreter.run("do-events");
            toolkit.join();

            assertThat(interpreter.display(interpreter.run("mold seen")))
                    .as("the crossing happens while the interpreter waits, not before it")
                    .isEqualTo("\"[close]\"");
        }
    }
}
