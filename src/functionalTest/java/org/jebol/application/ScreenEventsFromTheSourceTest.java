package org.jebol.application;

import org.jebol.domain.host.ScreenEventType;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ScreenEventsFromTheSourceTest {

    private static final String TRUE = "#(true)";

    private static Interpreter makeAnInterpreterWithAScreen(RecordingScreen screen) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard()
                        .grant(HostService.WINDOWS)
                        .withTimeLimit(Duration.ofSeconds(10)));
        interpreter.useScreen(screen);
        return interpreter;
    }

    private static String answerFrom(RecordingScreen screen, String source) {
        Interpreter interpreter = makeAnInterpreterWithAScreen(screen);
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static RecordingScreen makeAScreen() {
        return RecordingScreen.measure(1024, 768);
    }

    @Nested
    @DisplayName("the event port")
    class ThePort {

        @Test
        @DisplayName("system/ports/event is a real port, not none")
        void exists() {
            assertThat(answerFrom(makeAScreen(), "port? system/ports/event"))
                    .as("init-view-system reads system/ports/event/extra on its "
                            + "ninth line, and none has no extra")
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and it is the event scheme, which is what sys-ports.reb opens")
        void isTheEventScheme() {
            assertThat(answerFrom(makeAScreen(),
                    "'event = system/ports/event/spec/scheme")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("the scheme is registered, so make-scheme found an actor for it")
        void isRegistered() {
            assertThat(answerFrom(makeAScreen(),
                    "true? in system/schemes 'event")).isEqualTo(TRUE);
        }
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
    @DisplayName("an event the screen reports")
    class TheQueue {

        @Test
        @DisplayName("is queued and not acted on until the interpreter takes it")
        @Timeout(20)
        void queuesRatherThanDelivers() {
            RecordingScreen screen = makeAScreen();
            Interpreter interpreter = makeAnInterpreterWithAScreen(screen);
            interpreter.defineFreshWordsIn(A_WINDOW_AND_A_WATCHER);
            interpreter.run(A_WINDOW_AND_A_WATCHER);

            screen.actAsTheOperator(ScreenEventType.KEY, screen.listWhatOpened().getFirst());

            assertThat(interpreter.display(interpreter.run("empty? seen")))
                    .as("queueing runs no handler; only the interpreter's own thread does")
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and waiting is where it becomes a handler call")
        @Timeout(20)
        void deliversOnWaiting() {
            RecordingScreen screen = makeAScreen();
            Interpreter interpreter = makeAnInterpreterWithAScreen(screen);
            interpreter.defineFreshWordsIn(A_WINDOW_AND_A_WATCHER);
            interpreter.run(A_WINDOW_AND_A_WATCHER);

            screen.actAsTheOperator(ScreenEventType.KEY, screen.listWhatOpened().getFirst());
            screen.actAsTheOperator(ScreenEventType.CLOSE, screen.listWhatOpened().getFirst());
            interpreter.run("do-events");

            assertThat(interpreter.display(interpreter.run("mold seen")))
                    .as("the handler list is walked on the thread that started the run")
                    .isEqualTo("\"[key close]\"");
        }

        @Test
        @DisplayName("and they arrive in the order the screen reported them")
        @Timeout(20)
        void keepsTheOrder() {
            RecordingScreen screen = makeAScreen();
            Interpreter interpreter = makeAnInterpreterWithAScreen(screen);
            interpreter.defineFreshWordsIn(A_WINDOW_AND_A_WATCHER);
            interpreter.run(A_WINDOW_AND_A_WATCHER);

            var window = screen.listWhatOpened().getFirst();
            screen.actAsTheOperator(ScreenEventType.DOWN, window);
            screen.actAsTheOperator(ScreenEventType.MOVE, window);
            screen.actAsTheOperator(ScreenEventType.UP, window);
            screen.actAsTheOperator(ScreenEventType.CLOSE, window);
            interpreter.run("do-events");

            assertThat(interpreter.display(interpreter.run("mold seen")))
                    .isEqualTo("\"[down move up close]\"");
        }
    }

    @Nested
    @DisplayName("waiting on the event port among other things, with a time")
    class AmongOthers {

        private final RecordingScreen screen = makeAScreen();

        private final Interpreter interpreter = makeAnInterpreterWithAScreen(screen);

        private String runAndReport(String source) {
            interpreter.defineFreshWordsIn(source);
            return interpreter.display(interpreter.run(source));
        }

        private void deliversADownAMoveAndALetGo() {
            var window = screen.listWhatOpened().getFirst();
            screen.actAsTheOperator(ScreenEventType.DOWN, window);
            screen.actAsTheOperator(ScreenEventType.MOVE, window);
            screen.actAsTheOperator(ScreenEventType.UP, window);
        }

        @Test
        @DisplayName("hands everything already queued to the handlers, in order, with no time to spare")
        @Timeout(20)
        void deliversWhatIsQueuedOnAWaitOfNoTime() {
            runAndReport(A_WINDOW_AND_A_WATCHER);
            deliversADownAMoveAndALetGo();

            assertThat(runAndReport("""
                    wait [system/ports/event 0]
                    seen""")).isEqualTo("[down move up]");
        }

        @Test
        @DisplayName("and leaves the window open, since only closing it ends a program")
        @Timeout(20)
        void keepsTheWindowOpen() {
            runAndReport(A_WINDOW_AND_A_WATCHER);
            deliversADownAMoveAndALetGo();

            assertThat(runAndReport("""
                    wait [system/ports/event 0]
                    length? system/view/screen-gob""")).isEqualTo("1");
        }

        @Test
        @DisplayName("delivers each event once, so a second wait finds nothing new")
        @Timeout(20)
        void deliversEachEventOnce() {
            runAndReport(A_WINDOW_AND_A_WATCHER);
            deliversADownAMoveAndALetGo();

            assertThat(runAndReport("""
                    wait [system/ports/event 0]
                    wait [system/ports/event 0]
                    seen""")).isEqualTo("[down move up]");
        }

        @Test
        @DisplayName("with nothing queued, answers none once the time is up")
        @Timeout(20)
        void answersNoneWhenNothingIsQueued() {
            runAndReport(A_WINDOW_AND_A_WATCHER);

            assertThat(runAndReport("""
                    answer: wait [system/ports/event 0.3]
                    reduce [answer empty? seen]""")).isEqualTo("[_ #(true)]");
        }

        @Test
        @DisplayName("waits out the time it was given, and no longer, when nothing comes")
        @Timeout(20)
        void waitsOutItsTime() {
            runAndReport(A_WINDOW_AND_A_WATCHER);
            long startedAt = System.nanoTime();

            runAndReport("wait [system/ports/event 0.3]");

            assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
                    .isBetween(Duration.ofMillis(300), Duration.ofSeconds(3));
        }

        @Test
        @DisplayName("reaches a port the script opened on the event scheme itself, as the 2010 GUI does")
        @Timeout(20)
        void reachesAPortTheScriptOpened() {
            runAndReport("""
                    view/no-wait make gob! [size: 100x100]
                    seen: copy []
                    own-port: open [scheme: 'event]
                    own-port/awake: func [event] [append seen event/type false]
                    """);
            deliversADownAMoveAndALetGo();

            assertThat(runAndReport("""
                    wait [own-port 0]
                    seen""")).isEqualTo("[down move up]");
        }

        @Test
        @DisplayName("answers the port when a handler wakes it, and stops handing out events there")
        @Timeout(20)
        void answersTheWokenPort() {
            runAndReport("""
                    view/no-wait make gob! [size: 100x100]
                    seen: copy []
                    own-port: open [scheme: 'event]
                    own-port/awake: func [event] [append seen event/type event/type = 'move]
                    """);
            deliversADownAMoveAndALetGo();

            assertThat(runAndReport("""
                    answer: wait [own-port 5]
                    reduce [same? answer own-port seen]""")).isEqualTo("[#(true) [down move]]");
        }

        @Test
        @DisplayName("and what came after the waking event stays queued for the next wait")
        @Timeout(20)
        void keepsWhatCameAfterQueued() {
            runAndReport("""
                    view/no-wait make gob! [size: 100x100]
                    seen: copy []
                    own-port: open [scheme: 'event]
                    own-port/awake: func [event] [append seen event/type event/type = 'move]
                    """);
            deliversADownAMoveAndALetGo();

            assertThat(runAndReport("""
                    wait [own-port 5]
                    wait [own-port 0]
                    seen""")).isEqualTo("[down move up]");
        }
    }

    @Nested
    @DisplayName("when a wait ends")
    class TheEnding {

        @Test
        @DisplayName("as soon as the screen has no windows left")
        @Timeout(20)
        void endsWithAnEmptyScreen() {
            assertThat(answerFrom(
                    makeAScreen().closeWhateverOpens(), """
                    view/no-wait make gob! [size: 100x100]
                    do-events
                    tail? system/view/screen-gob"""))
                    .as("ep/awake ends with `tail? system/view/screen-gob`")
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a screen that never had a window does not wait at all")
        @Timeout(20)
        void neverWaitsOnAnEmptyScreen() {
            assertThat(answerFrom(makeAScreen(), """
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
        void arriveUnderTheirOwnNames() {
            for (ScreenEventType kind : ScreenEventType.values()) {
                RecordingScreen screen = makeAScreen();
                Interpreter interpreter = makeAnInterpreterWithAScreen(screen);
                interpreter.defineFreshWordsIn(A_WINDOW_AND_A_WATCHER);
                interpreter.run(A_WINDOW_AND_A_WATCHER);

                var window = screen.listWhatOpened().getFirst();
                screen.actAsTheOperator(kind, window);
                screen.actAsTheOperator(ScreenEventType.CLOSE, window);
                interpreter.run("do-events");

                String expected = kind == ScreenEventType.CLOSE
                        ? "\"[close]\""
                        : "\"[" + kind.spelling() + " close]\"";
                assertThat(interpreter.display(interpreter.run("mold seen")))
                        .as("%s should arrive as %s", kind, kind.spelling())
                        .isEqualTo(expected);
            }
        }
    }
}
