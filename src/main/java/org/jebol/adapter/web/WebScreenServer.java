package org.jebol.adapter.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenEventKind;
import org.jebol.domain.render.PaintList;
import org.jebol.domain.value.EventCatalogue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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

    private void sendToEveryBrowser(String event, String payload) {
        byte[] message = ("event: " + event + "\ndata: " + payload + "\n\n")
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
        actOn(new FieldsOfAPostedEvent(
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    private void actOn(FieldsOfAPostedEvent said) {
        if (screen == null) {
            return;
        }
        if (said.text("kind").filter("measure"::equals).isPresent()) {
            wide = said.wholeNumber("wide").orElse(0);
            high = said.wholeNumber("high").orElse(0);
            screen.theBrowserMeasures(wide, high);
            return;
        }
        said.text("kind").flatMap(this::kindNamed).ifPresent(kind ->
                whatTheEventCarries(kind, said).ifPresent(detail -> screen.theBrowserReports(kind, detail)));
    }

    private Optional<ScreenEventDetail> whatTheEventCarries(ScreenEventKind kind, FieldsOfAPostedEvent said) {
        return switch (kind) {
            case DOWN, UP, MOVE -> wherethePointerIs(said);
            case KEY, KEY_UP -> said.wholeNumber("code")
                    .filter(this::isACharacter)
                    .map(ScreenEventDetail.Typed::new);
            case CONTROL, CONTROL_UP -> said.text("named")
                    .filter(named -> EventCatalogue.keyIndexOf(named).isPresent())
                    .map(ScreenEventDetail.NamedKey::new);
            case CLOSE, RESIZE, OFFSET -> Optional.of(new ScreenEventDetail.NothingMore());
        };
    }

    private boolean isACharacter(int code) {
        return Character.isValidCodePoint(code)
                && (code < Character.MIN_SURROGATE || code > Character.MAX_SURROGATE);
    }

    private Optional<ScreenEventDetail> wherethePointerIs(FieldsOfAPostedEvent said) {
        Optional<Integer> across = said.wholeNumber("across");
        Optional<Integer> down = said.wholeNumber("down");
        return across.isPresent() && down.isPresent()
                ? Optional.of(new ScreenEventDetail.At(across.get(), down.get()))
                : Optional.empty();
    }

    private Optional<ScreenEventKind> kindNamed(String word) {
        for (ScreenEventKind kind : ScreenEventKind.values()) {
            if (kind.spelling().equals(word.toLowerCase(Locale.ROOT))) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
