package org.jebol.adapter.web;

import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.ScreenEvent;
import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenEventKind;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.Value;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class WebScreenServerFromTheSourceTest {

    private WebScreenServer serving;
    private BrowserScreen screen;
    private HttpClient client;

    @BeforeEach
    void startServing() throws IOException {
        serving = WebScreenServer.on(0);
        screen = BrowserScreen.seenBy(serving);
        serving.reportTo(screen);
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    private java.io.InputStream pictureStream;

    @AfterEach
    void stopServing() throws IOException {
        if (pictureStream != null) {
            pictureStream.close();
        }
        serving.close();
    }

    private void aBrowserOpensThePage() throws Exception {
        pictureStream = openThePictureStream();
    }

    private void aBrowserOpensThePage(int wide, int high) throws Exception {
        aBrowserOpensThePage();
        post("event", """
                {"kind":"measure","wide":%d,"high":%d}""".formatted(wide, high));
    }

    private String theNextPicture() throws IOException {
        StringBuilder message = new StringBuilder();
        while (true) {
            int startsAt = message.indexOf("event: paint");
            if (startsAt >= 0 && message.indexOf("\n\n", startsAt) >= 0) {
                return message.toString();
            }
            int octet = pictureStream.read();
            if (octet < 0) {
                return message.toString();
            }
            message.append((char) octet);
        }
    }

    private Interpreter anInterpreterOnThisScreen() {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.WINDOWS));
        interpreter.useScreen(screen);
        return interpreter;
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create(serving.address() + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private int post(String path, String body) throws Exception {
        URI where = URI.create(serving.address() + path);
        HttpResponse<String> answer = client.send(
                HttpRequest.newBuilder(where)
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
        lastPostSaid = "POST %s -> %d [%s]".formatted(
                where, answer.statusCode(), answer.body().strip());
        return answer.statusCode();
    }

    private String lastPostSaid = "nothing posted yet";

    private java.io.InputStream openThePictureStream() throws Exception {
        HttpResponse<java.io.InputStream> streaming = client.send(
                HttpRequest.newBuilder(URI.create(serving.address() + "paint")).build(),
                HttpResponse.BodyHandlers.ofInputStream());
        java.io.InputStream stream = streaming.body();
        stream.read();
        return stream;
    }

    @Nested
    @DisplayName("the page it serves")
    class ThePage {

        @Test
        @DisplayName("is HTML, and says so")
        @Timeout(20)
        void itIsHtml() throws Exception {
            HttpResponse<String> page = get("");

            assertThat(page.statusCode()).isEqualTo(200);
            assertThat(page.headers().firstValue("Content-Type").orElseThrow())
                    .contains("text/html");
        }

        @Test
        @DisplayName("holds a canvas and the script that paints on it")
        @Timeout(20)
        void itHoldsACanvasAndTheScript() throws Exception {
            String page = get("").body();

            assertThat(page).contains("<canvas");
            assertThat(page).contains("EventSource('/paint')");
        }

        @Test
        @DisplayName("and nothing it loads comes from anywhere else")
        @Timeout(20)
        void itLoadsNothingFromElsewhere() throws Exception {
            String page = get("").body();

            assertThat(page).doesNotContain("<script src");
            assertThat(page).doesNotContain("<link");
            assertThat(page).doesNotContain("//cdn");
        }
    }

    @Nested
    @DisplayName("with nobody looking")
    class TheEmptyPage {

        @Test
        @DisplayName("the screen has no display, so a script cannot draw on it")
        @Timeout(20)
        void thereIsNoDisplay() {
            assertThat(serving.isConnected()).isFalse();
            assertThat(screen.hasADisplay()).isFalse();
        }
    }

    @Nested
    @DisplayName("once a browser has opened the picture stream")
    class TheAttachedBrowser {

        @Test
        @DisplayName("the screen has a display")
        @Timeout(20)
        void thereIsADisplay() throws Exception {
            aBrowserOpensThePage();

            assertThat(serving.isConnected()).isTrue();
            assertThat(screen.hasADisplay()).isTrue();
        }

        @Test
        @DisplayName("and a paint list crosses whole, clip and opacity included")
        @Timeout(20)
        void thelistCrossesWhole() throws Exception {
            aBrowserOpensThePage(640, 480);
            Interpreter interpreter = anInterpreterOnThisScreen();
            String script = """
                    view/no-wait make gob! [size: 200x100 color: 10.20.30]""";
            interpreter.defineFreshWordsIn(script);
            interpreter.run(script);

            String message = theNextPicture();
            assertThat(message).contains("event: paint");
            assertThat(message).contains("""
                    "kind":"fill",""");
            assertThat(message).contains("""
                    "colour":"#0a141e"}""");
            assertThat(message).contains("""
                    "clip":{""");
        }
    }

    @Nested
    @DisplayName("what a posted event carries")
    class WhatAnEventCarries {

        private GobValue theWindow;

        private void aWindowIsShowingAt(int across, int down) throws Exception {
            aBrowserOpensThePage(640, 480);
            Interpreter interpreter = anInterpreterOnThisScreen();
            String opening = """
                    view/no-wait make gob! [size: 100x100 color: 1.1.1]
                    system/view/screen-gob/1/offset: %dx%d""".formatted(across, down);
            interpreter.defineFreshWordsIn(opening);
            interpreter.run(opening);
            theWindow = theFirstWindowIn(interpreter);
        }

        private GobValue theFirstWindowIn(Interpreter interpreter) {
            Value window = interpreter.run("system/view/screen-gob/1").value();
            assertThat(window).isInstanceOf(GobValue.class);
            return (GobValue) window;
        }

        private Optional<ScreenEvent> whatIsQueuedAfterPosting(String body) throws Exception {
            assertThat(post("event", body)).as("%s", lastPostSaid).isEqualTo(204);
            return screen.takeTheNextEvent();
        }

        private ScreenEvent anEvent(ScreenEventKind kind, ScreenEventDetail detail) {
            return new ScreenEvent(kind, theWindow, detail);
        }

        @ParameterizedTest(name = "{0} at {1}x{2} on the page is {3}x{4} in the window")
        @CsvSource({
                "move, 50,  60,  40,  40",
                "down, 50,  60,  40,  40",
                "up,   50,  60,  40,  40",
                "move, 10,  20,  0,   0",
                "move, 11,  21,  1,   1",
                "move, 108, 118, 98,  98",
                "move, 109, 119, 99,  99",
        })
        @DisplayName("a pointer event counts from the window's top left, the window's place taken off the page's")
        @Timeout(20)
        void aPointerEventCountsFromTheWindow(String kind, int pageAcross, int pageDown,
                int windowAcross, int windowDown) throws Exception {
            aWindowIsShowingAt(10, 20);

            assertThat(whatIsQueuedAfterPosting("""
                    {"kind":"%s","across":%d,"down":%d}""".formatted(kind, pageAcross, pageDown)))
                    .contains(anEvent(ScreenEventKind.valueOf(kind.toUpperCase(Locale.ROOT)),
                            new ScreenEventDetail.At(windowAcross, windowDown)));
        }

        @ParameterizedTest(name = "a {0} at {1}x{2}, on no window, is not delivered")
        @CsvSource({
                "move, 9,   19",
                "move, 9,   50",
                "move, 50,  19",
                "move, 110, 120",
                "move, 110, 50",
                "move, 50,  120",
                "move, 0,   0",
                "down, 9,   19",
                "down, 110, 120",
                "up,   0,   0",
        })
        @DisplayName("a pointer event on no window, with no press held, reaches nothing")
        @Timeout(20)
        void aPointerEventOnNoWindowIsDropped(String kind, int pageAcross, int pageDown) throws Exception {
            aWindowIsShowingAt(10, 20);

            assertThat(whatIsQueuedAfterPosting("""
                    {"kind":"%s","across":%d,"down":%d}""".formatted(kind, pageAcross, pageDown))).isEmpty();
        }

        @Test
        @DisplayName("a drag that leaves the window still belongs to it, counted from its top left")
        @Timeout(20)
        void aDragThatLeavesTheWindowStillBelongsToIt() throws Exception {
            aWindowIsShowingAt(10, 20);

            assertThat(whatIsQueuedAfterPosting("""
                    {"kind":"down","across":50,"down":60}""")).isPresent();
            assertThat(whatIsQueuedAfterPosting("""
                    {"kind":"move","across":0,"down":0}"""))
                    .contains(anEvent(ScreenEventKind.MOVE, new ScreenEventDetail.At(-10, -20)));
            assertThat(whatIsQueuedAfterPosting("""
                    {"kind":"up","across":200,"down":300}"""))
                    .contains(anEvent(ScreenEventKind.UP, new ScreenEventDetail.At(190, 280)));
        }

        @ParameterizedTest(name = "a {0} posted from the page is not delivered")
        @ValueSource(strings = {"resize", "offset"})
        @DisplayName("a page never moves or resizes one window, so a resize or offset posted names none")
        @Timeout(20)
        void aResizeOrOffsetIsDropped(String kind) throws Exception {
            aWindowIsShowingAt(10, 20);

            assertThat(whatIsQueuedAfterPosting("""
                    {"kind":"%s","across":5,"down":5}""".formatted(kind))).isEmpty();
        }

        @ParameterizedTest(name = "a move posted with {0} is not delivered")
        @ValueSource(strings = {
                """
                {"kind":"move"}""",
                """
                {"kind":"move","across":5}""",
                """
                {"kind":"move","down":5}""",
                """
                {"kind":"move","across":"x","down":5}""",
                """
                {"kind":"move","across":5.5,"down":5}""",
                """
                {"kind":"move","across":true,"down":5}""",
                """
                {"kind":"down","across":"","down":5}""",
                """
                {"kind":"up","across":"5","down":5}""",
        })
        @DisplayName("a pointer event without two whole numbers for where it is is not delivered at all")
        @Timeout(20)
        void aPointerEventWithoutAPlaceIsDropped(String body) throws Exception {
            aWindowIsShowingAt(10, 20);

            assertThat(whatIsQueuedAfterPosting(body)).isEmpty();
        }

        @Test
        @DisplayName("a field the page adds that nobody reads is ignored")
        @Timeout(20)
        void anExtraFieldIsIgnored() throws Exception {
            aWindowIsShowingAt(10, 20);

            assertThat(whatIsQueuedAfterPosting("""
                    {"kind":"move","across":15,"down":25,"buttons":1}"""))
                    .contains(anEvent(ScreenEventKind.MOVE, new ScreenEventDetail.At(5, 5)));
        }

        @ParameterizedTest(name = "{0} with code {1} types the character {1}")
        @CsvSource({
                "key,    97",
                "key,    44",
                "key-up, 97",
                "key,    0",
                "key,    13",
                "key,    1114111",
                "key,    128578",
        })
        @DisplayName("a key event carries the character it typed, a comma included")
        @Timeout(20)
        void aKeyCarriesItsCharacter(String kind, int code) throws Exception {
            aWindowIsShowingAt(10, 20);

            assertThat(whatIsQueuedAfterPosting("""
                    {"kind":"%s","code":%d}""".formatted(kind, code)))
                    .contains(anEvent(kind.equals("key") ? ScreenEventKind.KEY : ScreenEventKind.KEY_UP,
                            new ScreenEventDetail.Typed(code)));
        }

        @ParameterizedTest(name = "a key posted with {0} is not delivered")
        @ValueSource(strings = {
                """
                {"kind":"key"}""",
                """
                {"kind":"key","code":-1}""",
                """
                {"kind":"key","code":1114112}""",
                """
                {"kind":"key","code":55296}""",
                """
                {"kind":"key","code":"a"}""",
                """
                {"kind":"key","code":97.5}""",
                """
                {"kind":"key","code":"97"}""",
        })
        @DisplayName("a key whose number is no character is not delivered")
        @Timeout(20)
        void aKeyThatIsNoCharacterIsDropped(String body) throws Exception {
            aWindowIsShowingAt(10, 20);

            assertThat(whatIsQueuedAfterPosting(body)).isEmpty();
        }

        @ParameterizedTest(name = "{0} named {1} is that key")
        @CsvSource({
                "control,    page-up",
                "control,    left",
                "control,    f12",
                "control-up, page-down",
        })
        @DisplayName("a key that types nothing carries its name from the catalogue")
        @Timeout(20)
        void aControlKeyCarriesItsName(String kind, String named) throws Exception {
            aWindowIsShowingAt(10, 20);

            assertThat(whatIsQueuedAfterPosting("""
                    {"kind":"%s","named":"%s"}""".formatted(kind, named)))
                    .contains(anEvent(kind.equals("control") ? ScreenEventKind.CONTROL : ScreenEventKind.CONTROL_UP,
                            new ScreenEventDetail.NamedKey(named)));
        }

        @ParameterizedTest(name = "a control key named {0} is not delivered")
        @ValueSource(strings = {"sideways", "PAGE-UP", "Page-Up", ""})
        @DisplayName("a name the catalogue does not hold, spelt exactly, is not delivered")
        @Timeout(20)
        void anUnknownNameIsDropped(String named) throws Exception {
            aWindowIsShowingAt(10, 20);

            assertThat(whatIsQueuedAfterPosting("""
                    {"kind":"control","named":"%s"}""".formatted(named))).isEmpty();
        }

        @Test
        @DisplayName("a control key with no name at all is not delivered")
        @Timeout(20)
        void aControlKeyWithNoNameIsDropped() throws Exception {
            aWindowIsShowingAt(10, 20);

            assertThat(whatIsQueuedAfterPosting("""
                    {"kind":"control"}""")).isEmpty();
        }

        @Test
        @DisplayName("a close carries nothing more, and is delivered")
        @Timeout(20)
        void aCloseCarriesNothing() throws Exception {
            aWindowIsShowingAt(10, 20);

            assertThat(whatIsQueuedAfterPosting("""
                    {"kind":"close"}"""))
                    .contains(anEvent(ScreenEventKind.CLOSE, new ScreenEventDetail.NothingMore()));
        }
    }

    @Nested
    @DisplayName("with two windows on the page")
    class TwoWindows {

        private GobValue lower;
        private GobValue upper;

        @BeforeEach
        void twoOverlappingWindows() throws Exception {
            aBrowserOpensThePage(640, 480);
            Interpreter interpreter = anInterpreterOnThisScreen();
            String opening = """
                    lower: view/no-wait make gob! [size: 100x100 color: 1.1.1]
                    lower/offset: 10x20
                    upper: view/no-wait make gob! [size: 100x100 color: 2.2.2]
                    upper/offset: 60x70""";
            interpreter.defineFreshWordsIn(opening);
            interpreter.run(opening);
            lower = theGobIn(interpreter, "lower");
            upper = theGobIn(interpreter, "upper");
        }

        private GobValue theGobIn(Interpreter interpreter, String word) {
            Value gob = interpreter.run(word).value();
            assertThat(gob).isInstanceOf(GobValue.class);
            return (GobValue) gob;
        }

        private Optional<ScreenEvent> whatIsQueuedAfterPosting(String body) throws Exception {
            assertThat(post("event", body)).as("%s", lastPostSaid).isEqualTo(204);
            return screen.takeTheNextEvent();
        }

        @Test
        @DisplayName("a click where only the lower window is reaches the lower window")
        @Timeout(20)
        void aClickOnTheLowerReachesIt() throws Exception {
            assertThat(whatIsQueuedAfterPosting("""
                    {"kind":"down","across":20,"down":30}"""))
                    .contains(new ScreenEvent(ScreenEventKind.DOWN, lower, new ScreenEventDetail.At(10, 10)));
        }

        @Test
        @DisplayName("a click where both windows are reaches the upper one, opened last")
        @Timeout(20)
        void aClickOnTheOverlapReachesTheUpper() throws Exception {
            assertThat(whatIsQueuedAfterPosting("""
                    {"kind":"down","across":80,"down":90}"""))
                    .contains(new ScreenEvent(ScreenEventKind.DOWN, upper, new ScreenEventDetail.At(20, 20)));
        }

        @Test
        @DisplayName("a key before any click reaches the upper window, and after a click on the lower, the lower")
        @Timeout(20)
        void aKeyFollowsTheClick() throws Exception {
            assertThat(whatIsQueuedAfterPosting("""
                    {"kind":"key","code":97}"""))
                    .contains(new ScreenEvent(ScreenEventKind.KEY, upper, new ScreenEventDetail.Typed('a')));
            assertThat(whatIsQueuedAfterPosting("""
                    {"kind":"down","across":20,"down":30}""")).isPresent();
            assertThat(whatIsQueuedAfterPosting("""
                    {"kind":"key","code":98}"""))
                    .contains(new ScreenEvent(ScreenEventKind.KEY, lower, new ScreenEventDetail.Typed('b')));
        }

        @Test
        @DisplayName("a close from the page is one close for each window")
        @Timeout(20)
        void aCloseClosesBoth() throws Exception {
            assertThat(whatIsQueuedAfterPosting("""
                    {"kind":"close"}"""))
                    .contains(new ScreenEvent(ScreenEventKind.CLOSE, lower));
            assertThat(screen.takeTheNextEvent())
                    .contains(new ScreenEvent(ScreenEventKind.CLOSE, upper));
            assertThat(screen.takeTheNextEvent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("what the browser posts back")
    class TheEvents {

        @Test
        @DisplayName("a measurement becomes the screen a script reads")
        @Timeout(20)
        void ameasurementBecomesTheScreen() throws Exception {
            aBrowserOpensThePage();

            assertThat(post("event", """
                    {"kind":"measure","wide":1024,"high":768}""")).isEqualTo(204);

            Interpreter interpreter = anInterpreterOnThisScreen();

            assertThat(interpreter.display(interpreter.run("gui-metric 'screen-size")))
                    .isEqualTo("1024x768");
        }

        @Test
        @DisplayName("and a keypress becomes an event on the same queue")
        @Timeout(20)
        void akeypressBecomesAnEvent() throws Exception {
            aBrowserOpensThePage(640, 480);
            Interpreter interpreter = anInterpreterOnThisScreen();
            String setUp = """
                    view/no-wait make gob! [size: 100x100 color: 1.1.1]
                    seen: copy []
                    handle-events [
                        name: 'watcher
                        priority: 90
                        handler: func [event] [append seen event/type  event]
                    ]""";
            interpreter.defineFreshWordsIn(setUp);
            interpreter.run(setUp);

            post("event", """
                    {"kind":"key","code":97}""");
            post("event", """
                    {"kind":"close"}""");
            interpreter.run("do-events");

            assertThat(interpreter.display(interpreter.run("mold seen")))
                    .as("a person pressing a key in a browser reaches the same "
                            + "handler a person pressing one in a window reaches")
                    .isEqualTo("\"[key close]\"");
        }

        @Test
        @DisplayName("a kind nobody serves is ignored rather than passed on")
        @Timeout(20)
        void anunknownKindIsIgnored() throws Exception {
            aBrowserOpensThePage();

            assertThat(post("event", """
                    {"kind":"jump"}"""))
                    .as("%s", lastPostSaid)
                    .isEqualTo(204);
            assertThat(screen.takeTheNextEvent()).isEmpty();
        }

        @Test
        @DisplayName("and nonsense is refused without raising, because a browser is anywhere")
        @Timeout(20)
        void nonsenseIsRefusedQuietly() throws Exception {
            assertThat(post("event", "not json at all")).isEqualTo(204);
            assertThat(post("event", "")).isEqualTo(204);
            assertThat(post("event", """
                    {"kind":"measure","wide":"wide","high":[]}""")).isEqualTo(204);
        }
    }

    @Nested
    @DisplayName("reading what a browser posted")
    class TheReader {

        @Test
        @DisplayName("takes the fields of a flat object, text as text and a whole number as a number")
        void itTakesTheFields() {
            FieldsOfAPostedEvent posted = new FieldsOfAPostedEvent("""
                    {"kind":"measure","wide":1024}""");

            assertThat(posted.text("kind")).contains("measure");
            assertThat(posted.wholeNumber("wide")).contains(1024);
        }

        @ParameterizedTest(name = "{0} holds no whole number")
        @ValueSource(strings = {
                """
                {"wide":"1024"}""",
                """
                {"wide":10.5}""",
                """
                {"wide":true}""",
                """
                {"wide":null}""",
                """
                {"wide":}""",
                """
                {"other":5}""",
        })
        @DisplayName("a whole number is digits as posted: quoted text, a fraction or anything else is not one")
        void aWholeNumberIsOnlyDigits(String body) {
            assertThat(new FieldsOfAPostedEvent(body).wholeNumber("wide")).isEmpty();
        }

        @Test
        @DisplayName("and a number is not text, so an unquoted kind is no kind")
        void aNumberIsNotText() {
            assertThat(new FieldsOfAPostedEvent("""
                    {"kind":5}""").text("kind")).isEmpty();
        }

        @Test
        @DisplayName("and answers nothing for anything that is not one")
        void itAnswersNothingForNonsense() {
            assertThat(new FieldsOfAPostedEvent("").saysNothing()).isTrue();
            assertThat(new FieldsOfAPostedEvent("hello").saysNothing()).isTrue();
            assertThat(new FieldsOfAPostedEvent("[1,2]").saysNothing()).isTrue();
        }
    }
}
