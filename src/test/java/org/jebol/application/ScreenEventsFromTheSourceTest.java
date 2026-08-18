package org.jebol.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.jebol.domain.eval.ScreenEventKind;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * What the screen tells a script, and which thread it tells it on.
 *
 * <p>The rule this file exists to pin is the threading one, and it is forced
 * rather than chosen. An interpreter is owned by one thread, and that is what
 * lets series share mutable storage with nothing synchronising them. A widget
 * toolkit calls a listener on its own thread. So the screen queues an event
 * and returns, and the interpreter's own thread takes it later inside WAIT.
 *
 * <p>Getting that wrong would not fail loudly. Two threads appending to one
 * block corrupt it without either of them raising, so the damage would show up
 * somewhere else entirely, long afterwards.
 *
 * <p>The other rule here is what ends a wait. REBOL's own words, from
 * {@code init-view-system}: the event port's AWAKE ends with
 * {@code tail? system/view/screen-gob}, which is true exactly when the screen
 * has no children left. That is what makes a script ending in VIEW a program
 * rather than a statement.
 *
 * <p>Specified in {@code spec/screen.allium}.
 */
class ScreenEventsFromTheSourceTest {

    private static final String TRUE = "#(true)";

    private static Interpreter withAScreen(RecordingScreen screen) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard()
                        .granting(HostService.WINDOWS)
                        .withWallClockLimit(Duration.ofSeconds(10)));
        interpreter.useScreen(screen);
        return interpreter;
    }

    private static String answerFrom(RecordingScreen screen, String source) {
        Interpreter interpreter = withAScreen(screen);
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static RecordingScreen aScreen() {
        return RecordingScreen.measuring(1024, 768);
    }

    @Nested
    @DisplayName("the event port")
    class ThePort {

        @Test
        @DisplayName("system/ports/event is a real port, not none")
        void theEventPortExists() {
            assertThat(answerFrom(aScreen(), "port? system/ports/event"))
                    .as("init-view-system reads system/ports/event/extra on its "
                            + "ninth line, and none has no extra")
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and it is the event scheme, which is what sys-ports.reb opens")
        void itIsTheEventScheme() {
            assertThat(answerFrom(aScreen(),
                    "'event = system/ports/event/spec/scheme")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("the scheme is registered, so make-scheme found an actor for it")
        void theSchemeIsRegistered() {
            assertThat(answerFrom(aScreen(),
                    "true? in system/schemes 'event")).isEqualTo(TRUE);
        }
    }

    /**
     * A window on the screen, with a watcher noting every event it sees.
     *
     * <p>The watcher answers the event rather than none, which is how
     * {@code init-view-system} says to carry on: "Handlers should return event
     * in order to continue." A watcher that swallowed events would stop the
     * default handler ever seeing a close, and nothing would shut the window.
     */
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
    @DisplayName("an event the screen reports")
    class TheQueue {

        @Test
        @DisplayName("is queued and not acted on until the interpreter takes it")
        @Timeout(20)
        void itIsQueuedRatherThanDelivered() {
            RecordingScreen screen = aScreen();
            Interpreter interpreter = withAScreen(screen);
            interpreter.defineFreshWordsIn(A_WINDOW_AND_A_WATCHER);
            interpreter.run(A_WINDOW_AND_A_WATCHER);

            screen.theOperatorDoes(ScreenEventKind.KEY, screen.whatOpened().getFirst());

            assertThat(interpreter.display(interpreter.run("empty? seen")))
                    .as("queueing runs no handler; only the interpreter's own thread does")
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and waiting is where it becomes a handler call")
        @Timeout(20)
        void waitingDeliversIt() {
            RecordingScreen screen = aScreen();
            Interpreter interpreter = withAScreen(screen);
            interpreter.defineFreshWordsIn(A_WINDOW_AND_A_WATCHER);
            interpreter.run(A_WINDOW_AND_A_WATCHER);

            screen.theOperatorDoes(ScreenEventKind.KEY, screen.whatOpened().getFirst());
            screen.theOperatorDoes(ScreenEventKind.CLOSE, screen.whatOpened().getFirst());
            interpreter.run("do-events");

            assertThat(interpreter.display(interpreter.run("mold seen")))
                    .as("the handler list is walked on the thread that started the run")
                    .isEqualTo("\"[key close]\"");
        }

        @Test
        @DisplayName("and they arrive in the order the screen reported them")
        @Timeout(20)
        void theyArriveInOrder() {
            RecordingScreen screen = aScreen();
            Interpreter interpreter = withAScreen(screen);
            interpreter.defineFreshWordsIn(A_WINDOW_AND_A_WATCHER);
            interpreter.run(A_WINDOW_AND_A_WATCHER);

            var window = screen.whatOpened().getFirst();
            screen.theOperatorDoes(ScreenEventKind.DOWN, window);
            screen.theOperatorDoes(ScreenEventKind.MOVE, window);
            screen.theOperatorDoes(ScreenEventKind.UP, window);
            screen.theOperatorDoes(ScreenEventKind.CLOSE, window);
            interpreter.run("do-events");

            assertThat(interpreter.display(interpreter.run("mold seen")))
                    .isEqualTo("\"[down move up close]\"");
        }
    }

    @Nested
    @DisplayName("when a wait ends")
    class TheEnding {

        @Test
        @DisplayName("as soon as the screen has no windows left")
        @Timeout(20)
        void itEndsWithAnEmptyScreen() {
            assertThat(answerFrom(
                    aScreen().whereTheOperatorClosesWhateverOpens(), """
                    view/no-wait make gob! [size: 100x100]
                    do-events
                    tail? system/view/screen-gob"""))
                    .as("ep/awake ends with `tail? system/view/screen-gob`")
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a screen that never had a window does not wait at all")
        @Timeout(20)
        void anEmptyScreenNeverWaits() {
            assertThat(answerFrom(aScreen(), """
                    do-events
                    true"""))
                    .as("nothing to wait for is not a reason to wait")
                    .isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("the kinds a window can report")
    class TheKinds {

        @Test
        @DisplayName("all eight arrive with the spelling the catalogue gives them")
        @Timeout(60)
        void everyKindArrivesUnderItsOwnName() {
            for (ScreenEventKind kind : ScreenEventKind.values()) {
                RecordingScreen screen = aScreen();
                Interpreter interpreter = withAScreen(screen);
                interpreter.defineFreshWordsIn(A_WINDOW_AND_A_WATCHER);
                interpreter.run(A_WINDOW_AND_A_WATCHER);

                var window = screen.whatOpened().getFirst();
                screen.theOperatorDoes(kind, window);
                screen.theOperatorDoes(ScreenEventKind.CLOSE, window);
                interpreter.run("do-events");

                // A close is the one kind that ends the wait, because the
                // default handler unviews on it and the screen goes empty. So
                // the second close is never reached, and the trailing close
                // that every other kind needs is that kind's own event.
                String expected = kind == ScreenEventKind.CLOSE
                        ? "\"[close]\""
                        : "\"[" + kind.spelling() + " close]\"";
                assertThat(interpreter.display(interpreter.run("mold seen")))
                        .as("%s should arrive as %s", kind, kind.spelling())
                        .isEqualTo(expected);
            }
        }
    }
}
