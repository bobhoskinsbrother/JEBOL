package org.jebol.adapter.web;

import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.*;

import java.io.IOException;
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
                    {"kind":"key"}""");
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
            assertThat(screen.takeQueuedEvents()).isEmpty();
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
        @DisplayName("takes the fields of a flat object")
        void itTakesTheFields() {
            assertThat(FieldsOfAPostedEvent.read("""
                    {"kind":"measure","wide":1024}"""))
                    .containsEntry("kind", "measure")
                    .containsEntry("wide", "1024");
        }

        @Test
        @DisplayName("and answers nothing for anything that is not one")
        void itAnswersNothingForNonsense() {
            assertThat(FieldsOfAPostedEvent.read("")).isEmpty();
            assertThat(FieldsOfAPostedEvent.read("hello")).isEmpty();
            assertThat(FieldsOfAPostedEvent.read("[1,2]")).isEmpty();
        }
    }
}
