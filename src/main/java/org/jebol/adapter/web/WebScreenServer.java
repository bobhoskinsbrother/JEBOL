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
 * A browser reached over the JDK's own HTTP server.
 *
 * <p>One implementation of {@link BrowserScreen.Viewer} and not the only one
 * anybody should write. A host that already serves pages implements that
 * interface over what it has; this exists so that a host which has not got one
 * can still open a page, and so that the browser renderer can be run and
 * looked at.
 *
 * <p>{@code com.sun.net.httpserver} is in the JDK, which is why it is this and
 * not a web framework. The project has no runtime dependencies and a renderer
 * is a poor reason to acquire the first one.
 *
 * <p>Two directions and both are plain HTTP. The picture goes down a
 * server-sent event stream, which is a long-lived GET and needs nothing on
 * either side that a browser has not had for a decade. Events come back as
 * ordinary posts. No socket upgrade, no library.
 *
 * <p>Nothing about any of that reaches {@link BrowserScreen}, which is the
 * point: the port hands over a paint list and takes events back, and how they
 * travel is this file's business alone.
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

    /**
     * Starts serving on a port, or on any free one when given zero.
     *
     * <p>On the loopback address only. The page is for whoever is sitting at
     * this machine -- it is a window, drawn somewhere else -- and a window
     * does not need to be reachable from the network. Binding every address
     * offered one anyway.
     */
    public static WebScreenServer on(int port) throws IOException {
        HttpServer listening = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        WebScreenServer serving = new WebScreenServer(listening);
        listening.createContext("/", serving::servePage);
        listening.createContext("/paint", serving::openThePaintStream);
        listening.createContext("/event", serving::takeAnEvent);
        listening.start();
        return serving;
    }

    /** Which port it ended up on, which matters when it was asked for any. */
    public int port() {
        return server.getAddress().getPort();
    }

    /**
     * Where to reach it, written as the address it actually bound.
     *
     * <p>Not as the name {@code localhost}, which resolves to two addresses on
     * a dual-stack machine and leaves the caller to pick. Naming the one that
     * was bound means the answer cannot depend on which one gets picked.
     */
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

    /**
     * A stream the page keeps open, down which every picture goes.
     *
     * <p>It never closes from this end. A browser that goes away closes it,
     * the next write fails, and that is how {@link #isConnected} learns there
     * is nobody looking -- which is what makes an empty page a screen that is
     * not there.
     */
    private void openThePaintStream(HttpExchange exchange) throws IOException {
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

    /**
     * Something the person looking at the page did.
     *
     * <p>It is put on the screen's queue and nothing else happens here. The
     * interpreter's own thread takes it inside WAIT, which is what keeps a
     * handler block running where every other block runs.
     */
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
        kindNamed(said.get("kind")).ifPresent(kind ->
                screen.theBrowserReports(kind, whichWindow()));
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

    /**
     * Which window an event belongs to.
     *
     * <p>The first one showing, and that is a gap rather than a decision. A
     * page paints every window onto one surface, so a browser reporting a
     * click cannot say which window it was in without being told where the
     * windows are. Naming the gap here rather than guessing quietly: with one
     * window open this is right, and with two it is a coin toss.
     */
    private GobValue whichWindow() {
        List<GobValue> showing = screen.whatIsShowing();
        return showing.isEmpty() ? null : showing.getFirst();
    }
}
