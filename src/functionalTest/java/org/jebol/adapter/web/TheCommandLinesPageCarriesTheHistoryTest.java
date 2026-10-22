package org.jebol.adapter.web;

import org.jebol.adapter.host.ClasspathFiles;
import org.jebol.application.AnInterpreter;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.jebol.domain.host.Place;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(30)
class TheCommandLinesPageCarriesTheHistoryTest {

    private WebScreenServer serving;
    private BrowserScreen screen;
    private InputStream messages;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach
    void startServing() throws IOException {
        serving = WebScreenServer.on(0, new ClasspathFiles());
        screen = BrowserScreen.showTo(serving, new ClasspathFiles());
        serving.reportTo(screen);
    }

    @AfterEach
    void stopServing() throws IOException {
        if (messages != null) {
            messages.close();
        }
        serving.close();
    }

    private void openThePage() throws Exception {
        messages = client.send(HttpRequest.newBuilder(URI.create(serving.address() + "paint")).build(),
                HttpResponse.BodyHandlers.ofInputStream()).body();
        messages.read();
    }

    private int post(String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(serving.address() + "event"))
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private String waitForMessage(String kind) throws IOException {
        StringBuilder read = new StringBuilder();
        while (true) {
            int startsAt = read.indexOf("event: " + kind + "\n");
            if (startsAt >= 0 && read.indexOf("\n\n", startsAt) >= 0) {
                return read.substring(startsAt, read.indexOf("\n\n", startsAt));
            }
            int octet = messages.read();
            if (octet < 0) {
                return read.toString();
            }
            read.append((char) octet);
        }
    }

    private Interpreter makeAnInterpreter() {
        return Interpreter.createOn(AnInterpreter.standardPorts().withScreen(screen), OutputPort.discard(),
                Bounds.standard().grant(HostService.WINDOWS).grant(HostService.NAVIGATION));
    }

    private String run(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("the page's address at load is where the app starts")
    void theAddressAtLoadIsWhereTheAppStarts() throws Exception {
        openThePage();

        assertThat(post("""
                {"type":"arrive","index":0,"address":"survey%203"}""")).isEqualTo(204);

        assertThat(screen.navigation().current()).isEqualTo(Place.readAddress("survey%203").orElseThrow());
    }

    @Test
    @DisplayName("a popstate the page posts moves the app's history")
    void aPopstateMovesTheHistory() throws Exception {
        openThePage();
        Interpreter interpreter = makeAnInterpreter();
        run(interpreter, "go 'a");

        post("""
                {"type":"moved","index":0,"address":""}""");

        assertThat(run(interpreter, "current-place")).isEqualTo("[]");
    }

    @Test
    @DisplayName("a posting with the wrong types is dropped, never coerced")
    void theWrongTypesAreDropped() throws Exception {
        openThePage();
        Interpreter interpreter = makeAnInterpreter();
        run(interpreter, "go 'a");

        assertThat(post("""
                {"type":"moved","index":"0","address":""}""")).isEqualTo(204);
        assertThat(post("""
                {"type":"moved","index":-1,"address":""}""")).isEqualTo(204);
        assertThat(post("""
                {"type":"moved","index":0,"address":3}""")).isEqualTo(204);
        assertThat(post("""
                {"type":"arrive","address":"b"}""")).isEqualTo(204);

        assertThat(run(interpreter, "current-place")).isEqualTo("[a]");
    }

    @Test
    @DisplayName("the app going somewhere is sent down to the page as a push")
    void goingIsSentDown() throws Exception {
        openThePage();

        run(makeAnInterpreter(), "go [survey 3]");

        assertThat(waitForMessage("navigate")).contains("""
                "move":"push","index":1,"address":"survey%203""");
    }

    @Test
    @DisplayName("the guard and the title are sent down to the page")
    void theGuardAndTheTitleAreSentDown() throws Exception {
        openThePage();
        post("""
                {"type":"measure","wide":400,"high":300}""");

        run(makeAnInterpreter(), """
                unsaved-work true
                view/no-wait/options make gob! [size: 40x40] [title: {Survey}]
                """);

        assertThat(waitForMessage("guard")).contains("""
                "unsaved":true""");
        assertThat(waitForMessage("title")).contains("""
                "text":"Survey""");
    }

    @Test
    @DisplayName("the page's navigation script is served beside the asks")
    void theScriptIsServed() throws Exception {
        HttpResponse<String> served = client.send(
                HttpRequest.newBuilder(URI.create(serving.address() + "jebol-navigation.js")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(served.statusCode()).isEqualTo(200);
        assertThat(served.headers().firstValue("Content-Type")).hasValueSatisfying(type ->
                assertThat(type).startsWith("text/javascript"));
        assertThat(served.body()).contains("class JebolNavigation");
    }
}
