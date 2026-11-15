package org.jebol.adapter.web;

import org.jebol.adapter.host.ClasspathFiles;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(30)
class TheCommandLinesPageAnswersTest {

    private WebScreenServer serving;
    private HttpClient client;
    private InputStream pictureStream;

    @BeforeEach
    void startServing() throws IOException {
        serving = WebScreenServer.on(0, new ClasspathFiles());
        serving.reportTo(BrowserScreen.showTo(serving, new ClasspathFiles()));
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterEach
    void stopServing() throws IOException {
        if (pictureStream != null) {
            pictureStream.close();
        }
        serving.close();
    }

    private void openThePage() throws Exception {
        pictureStream = client.send(
                HttpRequest.newBuilder(URI.create(serving.address() + "paint")).build(),
                HttpResponse.BodyHandlers.ofInputStream()).body();
        while (!serving.isConnected()) {
            Thread.onSpinWait();
        }
    }

    private String readNextAsk() throws IOException {
        StringBuilder message = new StringBuilder();
        while (true) {
            int startsAt = message.indexOf("event: ask");
            int endsAt = startsAt < 0 ? -1 : message.indexOf("\n\n", startsAt);
            if (endsAt >= 0) {
                return message.substring(startsAt, endsAt);
            }
            int octet = pictureStream.read();
            if (octet < 0) {
                return message.toString();
            }
            message.append((char) octet);
        }
    }

    private void postAnswer(String answer) throws Exception {
        client.send(HttpRequest.newBuilder(URI.create(serving.address() + "event"))
                        .POST(HttpRequest.BodyPublishers.ofString(answer, StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.discarding());
    }

    @Test
    @DisplayName("an ask goes down the page's stream and the answer posted back is the answer the interpreter gets")
    void anAskRidesTheStreamAndTheAnswerComesBack() throws Exception {
        openThePage();
        CompletableFuture<PageAnswer> waiting =
                CompletableFuture.supplyAsync(() -> serving.ask(new PageAsk("colour", true)));

        assertThat(readNextAsk()).isEqualTo("""
                event: ask
                data: {"type":"ask","ask":1,"kind":"colour","gesture":true}""");
        postAnswer("""
                {"type":"answer","ask":1,"answer":"given","colour":"#ff8000"}""");

        PageAnswer answer = waiting.get(10, TimeUnit.SECONDS);
        assertThat(answer.isGiven()).isTrue();
        assertThat(answer.text("colour")).contains("#ff8000");
    }

    @Test
    @DisplayName("an answer to an ask nobody is waiting for is dropped, and the ask waiting keeps waiting for its own")
    void anAnswerToNoAskIsDropped() throws Exception {
        openThePage();
        CompletableFuture<PageAnswer> waiting =
                CompletableFuture.supplyAsync(() -> serving.ask(new PageAsk("colour", true)));
        readNextAsk();

        postAnswer("""
                {"type":"answer","ask":99,"answer":"given","colour":"#000000"}""");
        postAnswer("""
                {"type":"answer","answer":"given","colour":"#000000"}""");
        assertThat(waiting).isNotDone();

        postAnswer("""
                {"type":"answer","ask":1,"answer":"declined"}""");
        assertThat(waiting.get(10, TimeUnit.SECONDS).isDeclined()).isTrue();
    }

    @Test
    @DisplayName("each ask is numbered one more than the last, so a second ask cannot take the first's answer")
    void asksAreNumberedInTurn() throws Exception {
        openThePage();
        CompletableFuture<PageAnswer> first =
                CompletableFuture.supplyAsync(() -> serving.ask(new PageAsk("colour", true)));
        readNextAsk();
        postAnswer("""
                {"type":"answer","ask":1,"answer":"declined"}""");
        first.get(10, TimeUnit.SECONDS);

        CompletableFuture<PageAnswer> second =
                CompletableFuture.supplyAsync(() -> serving.ask(new PageAsk("open-tab", true)));
        assertThat(readNextAsk()).contains("""
                "ask":2,""");
        postAnswer("""
                {"type":"answer","ask":1,"answer":"given"}""");
        assertThat(second).isNotDone();
        postAnswer("""
                {"type":"answer","ask":2,"answer":"given"}""");
        assertThat(second.get(10, TimeUnit.SECONDS).isGiven()).isTrue();
    }

    @Test
    @DisplayName("with no page open there is nobody to ask, and the ask is refused at once rather than left waiting")
    void anAskWithNoPageIsRefused() {
        PageAnswer answer = serving.ask(new PageAsk("colour", true));

        assertThat(answer.isRefused()).isTrue();
        assertThat(answer.why()).contains("no browser");
    }

    @Test
    @DisplayName("the page's own script for asks is served beside the page")
    void servesTheAsksScript() throws Exception {
        HttpResponse<String> served = client.send(
                HttpRequest.newBuilder(URI.create(serving.address() + "jebol-asks.js")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(served.statusCode()).isEqualTo(200);
        assertThat(served.headers().firstValue("Content-Type")).hasValueSatisfying(type ->
                assertThat(type).startsWith("text/javascript"));
        assertThat(served.body()).contains("JebolAsks");
    }
}
