package org.jebol.adapter.web;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(240)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NavigatingOnAPageEndToEndTest {

    private static final String AN_APP_WITH_THREE_PLACES = """
            Rebol []
            places: [[] [a] [b]]
            print ["at" mold current-place]
            if current-place/refused [print ["refused" current-place/refused]]
            unless find/only places current-place [print "not found"]
            window: view/no-wait/options make gob! [size: 200x100] [title: {Three places}]
            handle-events [
                name: 'moves
                priority: 100
                handler: func [event] [
                    if find [back forward] event/type [print ["moved" event/type mold event/data]]
                    event
                ]
            ]
            """;

    private final PageBrowsers browsers = new PageBrowsers();

    @AfterAll
    void closeTheBrowsers() {
        browsers.close();
    }

    private ShippedRunner runnerFor(String steps) throws IOException {
        return new ShippedRunner()
                .running(AN_APP_WITH_THREE_PLACES + steps + """

                        print "ready"
                        do-events
                        """)
                .servingFile("/elsewhere", "<!doctype html><title>Elsewhere</title><p>elsewhere"
                        .getBytes(StandardCharsets.UTF_8), "text/html");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome"})
    @DisplayName("going somewhere changes the address, and the browser's Back and Forward arrive as events")
    void backAndForwardArriveAsEvents(String browserName) throws IOException {
        try (ShippedRunner runner = runnerFor("go 'a go 'b");
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers, browserName, runner).opensThePage()) {
            operator.waitUntilOutputContains("ready");
            operator.waitUntilTheAddressIs("#b");

            operator.pressesBack();
            operator.waitUntilOutputContains("moved back [a]");
            operator.waitUntilTheAddressIs("#a");

            operator.pressesForward();
            operator.waitUntilOutputContains("moved forward [b]");
            assertThat(operator.readTheAddressHash()).isEqualTo("#b");
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome"})
    @DisplayName("a shared link opens the app at the place it names, data and all")
    void aSharedLinkLandsThere(String browserName) throws IOException {
        try (ShippedRunner runner = runnerFor("");
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers, browserName, runner)
                     .opensThePageAt("#survey%203")) {
            operator.waitUntilOutputContains("ready");

            assertThat(operator.readOutput()).contains("at [survey 3]");
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome"})
    @DisplayName("a link to a place the app does not know gets the app's own not found")
    void anUnknownPlaceIsTheAppsToAnswer(String browserName) throws IOException {
        try (ShippedRunner runner = runnerFor("");
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers, browserName, runner)
                     .opensThePageAt("#nowhere")) {
            operator.waitUntilOutputContains("ready");

            assertThat(operator.readOutput()).contains("at [nowhere]", "not found");
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome"})
    @DisplayName("a link that does not read as a place starts the app at its start, never evaluated")
    void anUnreadableLinkStartsAtTheStart(String browserName) throws IOException {
        try (ShippedRunner runner = runnerFor("");
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers, browserName, runner)
                     .opensThePageAt("#%28print%20%7Bevaluated%7D%29")) {
            operator.waitUntilOutputContains("ready");

            assertThat(operator.readOutput()).contains("at []", "refused %28print%20%7Bevaluated%7D%29");
            assertThat(operator.readOutput()).doesNotContain("evaluated");
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome"})
    @DisplayName("an address typed into the bar is a forward move, and one that does not read arrives at the start")
    void aTypedAddressIsAForwardMove(String browserName) throws IOException {
        try (ShippedRunner runner = runnerFor("go 'a");
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers, browserName, runner).opensThePage()) {
            operator.waitUntilOutputContains("ready");

            operator.typesTheAddress("#c%201");
            operator.waitUntilOutputContains("moved forward [c 1]");

            operator.typesTheAddress("#%5B");
            operator.waitUntilOutputContains("moved forward []");

            operator.pressesBack();
            operator.waitUntilOutputContains("moved back [c 1]");
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome"})
    @DisplayName("a reload lands where the app was, and Back still goes where it went before")
    void aReloadLandsInTheSamePlace(String browserName) throws IOException {
        try (ShippedRunner runner = runnerFor("if current-place = [] [go 'a go 'b]");
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers, browserName, runner).opensThePage()) {
            operator.waitUntilOutputContains("ready");
            operator.waitUntilTheAddressIs("#b");

            operator.reloadsThePage();
            operator.waitUntilOutputContains("ready");
            assertThat(operator.readOutput()).contains("at [b]");

            operator.pressesBack();
            operator.waitUntilOutputContains("moved back [a]");
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome"})
    @DisplayName("the app's own go-back moves the page back, and at the first entry does nothing")
    void theAppsOwnBack(String browserName) throws IOException {
        try (ShippedRunner runner = runnerFor("""
                go-back
                print "still here"
                go 'a
                go-back
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers, browserName, runner).opensThePage()) {
            operator.waitUntilOutputContains("moved back []");

            assertThat(operator.readOutput()).contains("still here");
            assertThat(operator.readTheAddressHash()).isEmpty();
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome", "firefox"})
    @DisplayName("with unsaved work a reload asks first, and staying keeps the app running")
    void stayingKeepsTheAppRunning(String browserName) throws IOException, InterruptedException {
        try (ShippedRunner runner = runnerFor("go 'a go 'b unsaved-work true");
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers, browserName, runner)
                     .whoseLeavingIsWatched().opensThePage()) {
            operator.waitUntilOutputContains("ready");
            operator.clicksTheScreen();

            operator.pressesReload();
            operator.waitForTheLeavePrompt();
            operator.staysOnThePage();

            operator.pressesBack();
            operator.waitUntilOutputContains("moved back [a]");
            assertThat(operator.countOutputLinesSaying("ready")).isEqualTo(1);
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome", "firefox"})
    @DisplayName("with unsaved work, choosing to leave reloads the app where it was")
    void leavingReloads(String browserName) throws IOException, InterruptedException {
        try (ShippedRunner runner = runnerFor("if current-place = [] [go 'a unsaved-work true]");
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers, browserName, runner)
                     .whoseLeavingIsWatched().opensThePage()) {
            operator.waitUntilOutputContains("ready");
            operator.clicksTheScreen();

            operator.pressesReload();
            operator.waitForTheLeavePrompt();
            operator.leavesThePage();

            operator.waitUntilOutputContains("at [a]");
            operator.waitUntilOutputContains("ready");
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome", "firefox"})
    @DisplayName("with unsaved work, Back between the app's own places is not guarded")
    void backBetweenPlacesIsNotGuarded(String browserName) throws IOException {
        try (ShippedRunner runner = runnerFor("go 'a go 'b unsaved-work true");
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers, browserName, runner)
                     .whoseLeavingIsWatched().opensThePage()) {
            operator.waitUntilOutputContains("ready");
            operator.clicksTheScreen();

            operator.pressesBack();

            operator.waitUntilOutputContains("moved back [a]");
            assertThat(operator.isAskedBeforeLeaving()).isFalse();
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome", "firefox"})
    @DisplayName("without unsaved work a reload asks nothing")
    void withoutTheFlagNothingAsks(String browserName) throws IOException {
        try (ShippedRunner runner = runnerFor("go 'a");
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers, browserName, runner)
                     .whoseLeavingIsWatched().opensThePage()) {
            operator.waitUntilOutputContains("ready");
            operator.clicksTheScreen();

            operator.pressesReload();

            operator.waitUntilOutputContains("at [a]");
            operator.waitUntilOutputContains("ready");
            assertThat(operator.isAskedBeforeLeaving()).isFalse();
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome", "firefox"})
    @DisplayName("coming back to the page from another one restarts the app where it was")
    void comingBackRestartsTheApp(String browserName) throws IOException {
        try (ShippedRunner runner = runnerFor("if current-place = [] [go 'a go 'b]");
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers, browserName, runner).opensThePage()) {
            operator.waitUntilOutputContains("ready");
            operator.waitUntilTheAddressIs("#b");

            operator.goesElsewhere("/elsewhere");
            operator.waitUntilTheTitleIs("Elsewhere");
            operator.pressesBack();

            operator.waitUntilOutputContains("ready");
            assertThat(operator.readOutput()).contains("at [b]");
            assertThat(operator.countOutputLinesSaying("ready")).isEqualTo(1);
            operator.pressesBack();
            operator.waitUntilOutputContains("moved back [a]");
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome"})
    @DisplayName("the tab's title is the first window's, and follows it")
    void theTitleFollowsTheFirstWindow(String browserName) throws IOException {
        try (ShippedRunner runner = runnerFor("""
                view/no-wait/options make gob! [size: 50x50] [title: {Second}]
                window/text: {Question 2}
                show window
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers, browserName, runner).opensThePage()) {
            operator.waitUntilOutputContains("ready");

            operator.waitUntilTheTitleIs("Question 2");
        }
    }
}
