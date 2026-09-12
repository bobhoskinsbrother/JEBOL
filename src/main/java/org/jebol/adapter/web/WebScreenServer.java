package org.jebol.adapter.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.jebol.domain.eval.ScreenEventKind;
import org.jebol.domain.render.PaintList;
import org.jebol.domain.value.GobValue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A browser reached over the JDK's own HTTP server: one implementation of
 * {@link BrowserScreen.Viewer}, for a host that has not got a web stack of its
 * own. The picture goes down a server-sent event stream and events come back as
 * ordinary posts.
 */
public final class WebScreenServer implements BrowserScreen.Viewer, AutoCloseable {

    private final HttpServer server;
    private final List<OutputStream> watching = new CopyOnWriteArrayList<>();

    private BrowserScreen screen;
    private volatile int wide;
    private volatile int high;

    private WebScreenServer(HttpServer server) {
        this.server = server;
    }

    /** Starts serving on a port, or on any free one when given zero. */
    public static WebScreenServer on(int port) throws IOException {
        HttpServer listening = HttpServer.create(
                new InetSocketAddress(onlyReachableFromThisMachine(), port), 0);
        WebScreenServer serving = new WebScreenServer(listening);
        listening.createContext("/", serving::servePage);
        listening.createContext("/paint",
                serving::openThePaintStreamWhichOnlyTheBrowserEverCloses);
        listening.createContext("/event", serving::takeAnEvent);
        listening.start();
        return serving;
    }

    private static InetAddress onlyReachableFromThisMachine() {
        return InetAddress.getLoopbackAddress();
    }

    /** Which port it ended up on, which matters when it was asked for any. */
    public int port() {
        return server.getAddress().getPort();
    }

    /** Where to reach it, written as the address it actually bound. */
    public String address() {
        return "http://" + server.getAddress().getAddress().getHostAddress()
                + ":" + port() + "/";
    }

    /** Tells the server which screen to report events to. */
    public void reportTo(BrowserScreen given) {
        this.screen = given;
    }

    @Override
    public boolean isConnected() {
        return !watching.isEmpty();
    }

    @Override
    public void paint(PaintList painting) {
        sendToEveryBrowser("paint",
                PaintListAsJson.written(painting, wide, high));
    }

    @Override
    public void close() {
        server.stop(0);
        watching.clear();
    }

    private void servePage(HttpExchange exchange) throws IOException {
        byte[] page = WebScreenPage.HTML.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, page.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(page);
        }
    }

    private void openThePaintStreamWhichOnlyTheBrowserEverCloses(HttpExchange exchange)
            throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, 0);
        OutputStream stream = exchange.getResponseBody();
        stream.write(": listening\n\n".getBytes(StandardCharsets.UTF_8));
        stream.flush();
        watching.add(stream);
    }

    private void sendToEveryBrowser(String named, String payload) {
        byte[] message = ("event: " + named + "\ndata: " + payload + "\n\n")
                .getBytes(StandardCharsets.UTF_8);
        for (OutputStream stream : watching) {
            try {
                stream.write(message);
                stream.flush();
            } catch (IOException wentAway) {
                watching.remove(stream);
            }
        }
    }

    private void takeAnEvent(HttpExchange exchange) throws IOException {
        Map<String, String> said = FieldsOfAPostedEvent.read(
                new String(exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8));
        actOn(said);
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    private void actOn(Map<String, String> said) {
        if (screen == null) {
            return;
        }
        if ("measure".equals(said.get("kind"))) {
            wide = wholeNumberIn(said, "wide");
            high = wholeNumberIn(said, "high");
            screen.theBrowserMeasures(wide, high);
            return;
        }
        kindNamed(said.get("kind")).ifPresent(kind -> screen.theBrowserReports(kind,
                theFirstWindowShowingBecauseAPageCannotSayWhichOneWasClicked()));
    }

    private static int wholeNumberIn(Map<String, String> said, String field) {
        try {
            return Integer.parseInt(said.getOrDefault(field, "0"));
        } catch (NumberFormatException notANumber) {
            return 0;
        }
    }

    private static java.util.Optional<ScreenEventKind> kindNamed(String word) {
        if (word == null) {
            return java.util.Optional.empty();
        }
        for (ScreenEventKind kind : ScreenEventKind.values()) {
            if (kind.spelling().equals(word.toLowerCase(Locale.ROOT))) {
                return java.util.Optional.of(kind);
            }
        }
        return java.util.Optional.empty();
    }

    private GobValue theFirstWindowShowingBecauseAPageCannotSayWhichOneWasClicked() {
        List<GobValue> showing = screen.whatIsShowing();
        return showing.isEmpty() ? null : showing.getFirst();
    }
}
