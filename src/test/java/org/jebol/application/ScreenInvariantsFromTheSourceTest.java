package org.jebol.application;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
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

/**
 * The two things that must be true however a script drives the screen.
 *
 * <p>Both are invariants rather than rules, which is why they are here rather
 * than beside the command that happens to break them. An invariant is worth
 * writing down when no single rule owns it: any sequence of opens, refreshes
 * and closes has to leave the screen and the gob tree agreeing, and the way
 * to check that is to run sequences rather than cases.
 *
 * <p>The second is the one that could not fail in a single-threaded test, and
 * that is exactly why it needed a second thread. An interpreter is owned by
 * one thread, and that is what lets series share mutable storage with nothing
 * synchronising them. Two threads appending to one block corrupt it without
 * either of them raising, so an event acted on by a toolkit's own thread
 * would do its damage silently and somewhere else.
 *
 * <p>Specified in {@code spec/screen.allium}.
 */
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

    /** A window on the screen, with a watcher that notes and passes events on. */
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

        @Property(tries = 40)
        @DisplayName("after any run of opens and closes, the two still agree")
        void theyAgreeAfterAnySequence(
                @ForAll @IntRange(min = 0, max = 6) int opens,
                @ForAll @IntRange(min = 0, max = 6) int closes) {

            RecordingScreen screen = RecordingScreen.measuring(1024, 768);
            Interpreter interpreter = withAScreen(screen);

            interpreter.run("windows: copy []");
            for (int each = 0; each < opens; each++) {
                interpreter.run(
                        "append windows view/no-wait make gob! [size: 100x100]");
            }
            for (int each = 0; each < Math.min(closes, opens); each++) {
                interpreter.run("unview take windows");
            }

            int stillOpen = opens - Math.min(closes, opens);
            assertThat(screen.whatIsStandingOpen())
                    .as("%d opened, %d closed", opens, closes)
                    .hasSize(stillOpen);
            assertThat(interpreter.display(interpreter.run(
                    "length? system/view/screen-gob")))
                    .as("and the screen gob holds exactly the same number")
                    .isEqualTo(String.valueOf(stillOpen));
        }

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
