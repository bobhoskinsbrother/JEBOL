package org.jebol.adapter.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.jebol.domain.render.DrawingCommand;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;


public final class WebScreenServer implements BrowserScreen.Viewer, AutoCloseable {

    private final HttpServer server;
    private final List<OutputStream> browserConnections = new CopyOnWriteArrayList<>();
    private BrowserScreen screen;
    private volatile int width;
    private volatile int height;

    private static final AtomicInteger CONNECTIONS = new AtomicInteger();

    private WebScreenServer(HttpServer server) {
        this.server = server;
    }

    /** Starts serving on a port, or on any free one when given zero. */
    public static WebScreenServer on(int port) throws IOException {
        HttpServer listening = HttpServer.create(new InetSocketAddress(findLoopbackAddress(), port), 0);
        WebScreenServer serving = new WebScreenServer(listening);
        listening.setExecutor(Executors.newCachedThreadPool(WebScreenServer::startADaemonConnection));
        listening.createContext("/", serving::servePage);
        listening.createContext("/paint", serving::openPaintStreamThatTheBrowserCloses);
        listening.createContext("/event", serving::receiveEvent);
        listening.createContext("/NotoSans-Regular.ttf", serving::serveFont);
        listening.start();
        return serving;
    }

    private static InetAddress findLoopbackAddress() {
        return InetAddress.getLoopbackAddress();
    }

    private static Thread startADaemonConnection(Runnable work) {
        Thread serving = new Thread(work, "browser-connection-" + CONNECTIONS.incrementAndGet());
        serving.setDaemon(true);
        return serving;
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public String address() {
        return "http://" + server.getAddress().getAddress().getHostAddress() + ":" + port() + "/";
    }

    public void reportTo(BrowserScreen given) {
        this.screen = given;
    }

    @Override
    public boolean isConnected() {
        return !browserConnections.isEmpty();
    }

    @Override
    public void paint(List<DrawingCommand> painting) {
        dispatchToRegisteredBrowsers("paint", JsonConverter.write(painting, width, height));
    }

    @Override
    public void close() {
        server.stop(0);
        browserConnections.clear();
    }

    private void servePage(HttpExchange exchange) throws IOException {
        byte[] page = WebScreenPage.HTML.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, page.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(page);
        }
    }

    private void serveFont(HttpExchange exchange) throws IOException {
        byte[] font;
        try (InputStream reading = WebScreenServer.class
                .getResourceAsStream("/org/jebol/fonts/NotoSans-Regular.ttf")) {
            font = reading.readAllBytes();
        }
        exchange.getResponseHeaders().add("Content-Type", "font/ttf");
        exchange.sendResponseHeaders(200, font.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(font);
        }
    }

    private void openPaintStreamThatTheBrowserCloses(HttpExchange exchange)
            throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, 0);
        OutputStream stream = exchange.getResponseBody();
        stream.write(": listening\n\n".getBytes(StandardCharsets.UTF_8));
        stream.flush();
        browserConnections.add(stream);
    }

    private void dispatchToRegisteredBrowsers(String event, String payload) {
        byte[] message = ("event: " + event + "\ndata: " + payload + "\n\n")
                .getBytes(StandardCharsets.UTF_8);
        for (OutputStream stream : browserConnections) {
            try {
                stream.write(message);
                stream.flush();
            } catch (IOException wentAway) {
                browserConnections.remove(stream);
            }
        }
    }

    private void receiveEvent(HttpExchange exchange) throws IOException {
        dispatch(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    private void dispatch(String posted) {
        if (screen == null) {
            return;
        }
        PageEvent pageEvent = new PageEvent(posted);
        if (pageEvent.isMeasurement()) {
            if (pageEvent.isWholeMeasurement()) {
                width = pageEvent.width();
                height = pageEvent.height();
                screen.measure(width, height);
            }
            return;
        }
        pageEvent.asEvent().ifPresent(kind ->
                pageEvent.readDetails(kind).ifPresent(detail -> screen.report(kind, detail)));
    }
}
