package org.jebol.adapter.web;

import org.jebol.adapter.host.ClasspathFiles;
import org.jebol.application.AnInterpreter;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HistoryEntry;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.jebol.domain.render.DrawingCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NavigatingAnAppOnAPageTest {

    private static final String TRUE = "#(true)";

    private final class APageWithAHistory implements BrowserScreen.Viewer {

        private final List<String> historyChanges = new ArrayList<>();
        private final List<Boolean> guards = new ArrayList<>();
        private final List<String> titles = new ArrayList<>();
        private boolean looking = true;

        @Override
        public boolean isConnected() {
            return looking;
        }

        @Override
        public void paint(List<DrawingCommand> painting) {
        }

        @Override
        public void changeHistory(String move, HistoryEntry entry) {
            historyChanges.add(move + " " + entry.index() + " " + entry.address());
        }

        @Override
        public void guardLeaving(boolean unsavedWork) {
            guards.add(unsavedWork);
        }

        @Override
        public void showTitle(String title) {
            titles.add(title);
        }
    }

    private final APageWithAHistory page = new APageWithAHistory();

    private long millisecondsNow = 5_000;

    private final BrowserScreen screen = BrowserScreen.showTo(page, new ClasspathFiles(), () -> millisecondsNow);

    private final Interpreter interpreter = interpreterGranting(Bounds.standard()
            .grant(HostService.WINDOWS)
            .grant(HostService.NAVIGATION)
            .withTimeLimit(Duration.ofSeconds(20)));

    private Interpreter interpreterGranting(Bounds bounds) {
        Interpreter made = Interpreter.createOn(
                AnInterpreter.standardPorts().withScreen(screen), OutputPort.discard(), bounds);
        screen.measure(800, 600);
        return made;
    }

    private String answer(String source) {
        return answerFrom(interpreter, source);
    }

    private String answerFrom(Interpreter asked, String source) {
        asked.defineNewWordsIn(source);
        return asked.display(asked.run(source));
    }

    private String errorIdFrom(String source) {
        return answer("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private String messageFrom(Interpreter asked, String source) {
        return answerFrom(asked, "e: try [" + source + "] either error? e [form e] ['no-error]");
    }

    private void openAWindowListeningForMoves() {
        answer("""
                first-window: view/no-wait/options make gob! [size: 40x40] [title: {Survey}]
                heard: copy []
                handle-events [
                    name: 'listening
                    priority: 100
                    handler: func [event] [
                        if find [back forward] event/type [
                            append/only heard reduce [event/type event/data none? event/window]
                        ]
                        event
                    ]
                ]
                """);
    }

    private String waitAndReadWhatWasHeard() {
        return answer("""
                wait [system/ports/event 0.3]
                heard
                """);
    }

    @Nested
    @DisplayName("going somewhere")
    class GoingSomewhere {

        @Test
        @DisplayName("go takes the app to a place, answers it, and pushes it onto the page's history")
        void goPushes() {
            assertThat(answer("go 'survey")).isEqualTo("[survey]");
            assertThat(answer("current-place")).isEqualTo("[survey]");
            assertThat(page.historyChanges).containsExactly("push 1 survey");
        }

        @Test
        @DisplayName("a block is a place, written into the address as data")
        void aBlockIsAPlace() {
            answer("go [survey 3]");

            assertThat(page.historyChanges).containsExactly("push 1 survey%203");
            assertThat(answer("current-place")).isEqualTo("[survey 3]");
        }

        @Test
        @DisplayName("going where the app already is pushes nothing")
        void goingNowherePushesNothing() {
            answer("go 'survey go 'survey go [survey]");

            assertThat(page.historyChanges).containsExactly("push 1 survey");
        }

        @Test
        @DisplayName("go/replace replaces the current entry and pushes nothing")
        void goReplaceReplaces() {
            answer("go 'a");

            assertThat(answer("go/replace 'b")).isEqualTo("[b]");
            assertThat(page.historyChanges).containsExactly("push 1 a", "replace 1 b");
        }

        @Test
        @DisplayName("current-place is a fresh block each time: changing it changes nothing")
        void currentPlaceIsACopy() {
            answer("go [a b]");

            assertThat(answer("""
                    clear current-place
                    current-place
                    """)).isEqualTo("[a b]");
        }

        @Test
        @DisplayName("before the app goes anywhere it is at the start")
        void theStartIsAnEmptyBlock() {
            assertThat(answer("current-place")).isEqualTo("[]");
        }
    }

    @Nested
    @DisplayName("what go refuses")
    class WhatGoRefuses {

        @Test
        @DisplayName("none is not a place")
        void noneIsRefused() {
            assertThat(errorIdFrom("go none")).isEqualTo("invalid-arg");
            assertThat(page.historyChanges).isEmpty();
        }

        @Test
        @DisplayName("a type no place may hold is refused, alone or in a block")
        void otherTypesAreRefused() {
            assertThat(errorIdFrom("go 1x2")).isEqualTo("invalid-arg");
            assertThat(errorIdFrom("go 22-Oct-2026")).isEqualTo("invalid-arg");
            assertThat(errorIdFrom("go [a b: 1]")).isEqualTo("invalid-arg");
            assertThat(errorIdFrom("go [a [(print 1)]]")).isEqualTo("invalid-arg");
            assertThat(errorIdFrom("go/replace make object! []")).isEqualTo("invalid-arg");
            assertThat(page.historyChanges).isEmpty();
        }

        @Test
        @DisplayName("a string, a number and a logic are each a place of one")
        void scalarsArePlaces() {
            assertThat(answer("go {a b}")).isEqualTo("""
                    ["a b"]""");
            assertThat(answer("go 3")).isEqualTo("[3]");
            assertThat(answer("go 1.5")).isEqualTo("[1.5]");
            assertThat(answer("go true")).isEqualTo("[#(true)]");
        }

        @Test
        @DisplayName("a place whose address would be past the longest is too long")
        void tooLongIsRefused() {
            assertThat(errorIdFrom("go to word! append/dup copy {} {a} 2049")).isEqualTo("too-long");
            assertThat(page.historyChanges).isEmpty();
            assertThat(answer("length? form go to word! append/dup copy {} {a} 2048")).isEqualTo("2048");
        }

        @Test
        @DisplayName("the hundred and first change in thirty seconds is refused, and the next window has room")
        void theRateIsKept() {
            assertThat(errorIdFrom("""
                    repeat n 100 [go/replace reduce ['p n]]
                    go 'one-too-many
                    """)).isEqualTo("size-limit");
            assertThat(answer("current-place")).isEqualTo("[p 100]");

            millisecondsNow += 30_000;

            assertThat(answer("go 'later")).isEqualTo("[later]");
        }
    }

    @Nested
    @DisplayName("back and forward")
    class BackAndForward {

        @Test
        @DisplayName("go-back at the first entry does nothing and answers unset")
        void backAtTheStartDoesNothing() {
            assertThat(answer("unset? go-back")).isEqualTo(TRUE);
            assertThat(answer("unset? go-forward")).isEqualTo(TRUE);
            assertThat(page.historyChanges).isEmpty();
        }

        @Test
        @DisplayName("go-back asks the page to go back")
        void goBackAsksThePage() {
            answer("go 'a go-back");

            assertThat(page.historyChanges).containsExactly("push 1 a", "back 0 ");
        }

        @Test
        @DisplayName("go-forward after the page went back asks the page to go forward")
        void goForwardAsksThePage() {
            answer("go 'a");
            screen.navigation().reportArrival(0, "");
            answer("go-forward");

            assertThat(page.historyChanges).containsExactly("push 1 a", "forward 1 a");
        }

        @Test
        @DisplayName("the page going back arrives as a back event carrying the place and naming no window")
        @Timeout(20)
        void aMoveArrivesAsAnEvent() {
            openAWindowListeningForMoves();
            answer("go 'a go [b 2]");

            screen.navigation().reportArrival(1, "a");

            assertThat(waitAndReadWhatWasHeard()).isEqualTo("[[back [a] #(true)]]");
            assertThat(answer("current-place")).isEqualTo("[a]");
        }

        @Test
        @DisplayName("the page going forward arrives as a forward event")
        @Timeout(20)
        void forwardArrivesAsAnEvent() {
            openAWindowListeningForMoves();
            answer("go 'a");
            screen.navigation().reportArrival(0, "");
            waitAndReadWhatWasHeard();
            answer("clear heard");

            screen.navigation().reportArrival(1, "a");

            assertThat(waitAndReadWhatWasHeard()).isEqualTo("[[forward [a] #(true)]]");
        }

        @Test
        @DisplayName("an app that never asked about its history hears no move, but knows where it is when it asks")
        @Timeout(20)
        void anAppThatNeverAskedHearsNothing() {
            openAWindowListeningForMoves();

            screen.navigation().reportArrival(1, "typed");

            assertThat(waitAndReadWhatWasHeard()).isEqualTo("[]");
            assertThat(answer("current-place")).isEqualTo("[typed]");
        }

        @Test
        @DisplayName("an event made by a script may be a back or a forward, and has no data")
        void aScriptMayMakeOne() {
            assertThat(answer("e: make event! [type: 'back] e/type")).isEqualTo("back");
            assertThat(answer("e: make event! [type: 'forward] none? e/data")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("arriving from an address")
    class ArrivingFromAnAddress {

        @Test
        @DisplayName("a fresh load starts the app where its address points, before the script runs")
        void aFreshLoadStartsThere() {
            screen.navigation().reportStart(0, "survey%203");

            assertThat(answer("current-place")).isEqualTo("[survey 3]");
            assertThat(answer("none? current-place/refused")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("an address that does not read starts at the start, and the app can see what it was")
        void anUnreadableAddressIsKept() {
            screen.navigation().reportStart(0, "%28quit%29");

            assertThat(answer("current-place")).isEqualTo("[]");
            assertThat(answer("equal? current-place/refused {%28quit%29}")).isEqualTo(TRUE);
            assertThat(answer("string? current-place/refused")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a reload keeps the browser's index, so go-back still has somewhere to go")
        void aReloadKeepsTheIndex() {
            screen.navigation().reportStart(2, "b");
            answer("go-back");

            assertThat(page.historyChanges).containsExactly("back 1 ");
        }
    }

    @Nested
    @DisplayName("the guard and the title")
    class TheGuardAndTheTitle {

        @Test
        @DisplayName("unsaved-work tells the page to guard leaving, and answers the flag")
        void unsavedWorkGuards() {
            assertThat(answer("unsaved-work true")).isEqualTo(TRUE);
            assertThat(answer("unsaved-work false")).isEqualTo("#(false)");
            assertThat(page.guards).containsExactly(true, false);
        }

        @Test
        @DisplayName("unsaved-work takes a logic and nothing else")
        void unsavedWorkTakesALogic() {
            assertThat(errorIdFrom("unsaved-work {yes}")).isEqualTo("expect-arg");
            assertThat(errorIdFrom("unsaved-work 1")).isEqualTo("expect-arg");
            assertThat(errorIdFrom("unsaved-work none")).isEqualTo("expect-arg");
            assertThat(page.guards).isEmpty();
        }

        @Test
        @DisplayName("the first window's title is the page's, and follows it")
        @Timeout(20)
        void theFirstWindowIsTheTitle() {
            answer("""
                    first-window: view/no-wait/options make gob! [size: 40x40] [title: {Survey}]
                    view/no-wait/options make gob! [size: 20x20] [title: {Other}]
                    """);
            assertThat(page.titles.getLast()).isEqualTo("Survey");

            answer("""
                    first-window/text: {Question 2}
                    show first-window
                    """);

            assertThat(page.titles.getLast()).isEqualTo("Question 2");
            assertThat(page.titles).doesNotContain("Other");
        }

        @Test
        @DisplayName("when the first window closes, the oldest still open names the page")
        @Timeout(20)
        void theOldestStillOpenTakesOver() {
            answer("""
                    first-window: view/no-wait/options make gob! [size: 40x40] [title: {Survey}]
                    view/no-wait/options make gob! [size: 20x20] [title: {Other}]
                    unview first-window
                    """);

            assertThat(page.titles.getLast()).isEqualTo("Other");
        }
    }

    @Nested
    @DisplayName("when there is no history to have")
    class WhenThereIsNoHistory {

        @Test
        @DisplayName("navigation withheld is not granted")
        void withheldIsNotGranted() {
            Interpreter withheld = interpreterGranting(Bounds.standard().grant(HostService.WINDOWS));

            assertThat(messageFrom(withheld, "go 'a")).contains("navigation is not granted");
            assertThat(messageFrom(withheld, "go/replace 'a")).contains("navigation is not granted");
            assertThat(messageFrom(withheld, "go-back")).contains("navigation is not granted");
            assertThat(messageFrom(withheld, "go-forward")).contains("navigation is not granted");
            assertThat(messageFrom(withheld, "current-place")).contains("navigation is not granted");
            assertThat(messageFrom(withheld, "current-place/refused")).contains("navigation is not granted");
            assertThat(messageFrom(withheld, "unsaved-work true")).contains("navigation is not granted");
            assertThat(page.historyChanges).isEmpty();
            assertThat(page.guards).isEmpty();
        }

        @Test
        @DisplayName("a page nobody has opened is no screen, and so no history")
        void nobodyLookingIsNotPresent() {
            page.looking = false;

            assertThat(messageFrom(interpreter, "go 'a")).contains("not present");
            assertThat(messageFrom(interpreter, "go-back")).contains("not present");
        }

        @Test
        @DisplayName("no screen at all is no history, refused as not present")
        void noScreenIsNotPresent() {
            Interpreter screenless = Interpreter.createOn(AnInterpreter.standardPorts(), OutputPort.discard(),
                    Bounds.standard().grant(HostService.NAVIGATION));

            assertThat(messageFrom(screenless, "go 'a")).contains("not present");
            assertThat(messageFrom(screenless, "current-place")).contains("not present");
            assertThat(messageFrom(screenless, "go-forward")).contains("not present");
            assertThat(messageFrom(screenless, "unsaved-work false")).contains("not present");
        }
    }
}
