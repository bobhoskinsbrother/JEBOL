package org.jebol.adapter.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class ShippedRunner implements AutoCloseable {

    private static final String ORIGIN_IN_A_SCRIPT = "{THE-ORIGIN}";

    private final Path webBuild = Path.of(System.getProperty("jebol.webBuild", "build/generated/teavm/wasm-gc"));
    private final Map<String, HttpHandler> routes = new ConcurrentHashMap<>();
    private final HttpServer serving;
    private final String runnerPage;
    private volatile String entryScript = "";

    ShippedRunner() throws IOException {
        runnerPage = Files.readString(Path.of("site/runner.html"))
                .replace("__APP_FILES__", "[]")
                .replace("__APP_ENTRY__", "main.reb");
        serving = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serving.createContext("/", this::answer);
        serving.start();
    }

    String origin() {
        return "http://127.0.0.1:" + serving.getAddress().getPort();
    }

    ShippedRunner running(String script) {
        entryScript = script.replace(ORIGIN_IN_A_SCRIPT, "{" + origin() + "}");
        return this;
    }

    ShippedRunner serving(String path, HttpHandler handler) {
        routes.put(path, handler);
        return this;
    }

    ShippedRunner servingFile(String path, byte[] contents, String contentType) {
        return serving(path, exchange -> respond(exchange, contents, contentType, 200));
    }

    @Override
    public void close() {
        serving.stop(0);
    }

    void respond(HttpExchange exchange, byte[] body, String contentType, int status) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.getResponseHeaders().add("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream response = exchange.getResponseBody()) {
            response.write(body);
        }
    }

    private void answer(HttpExchange exchange) throws IOException {
        String asked = exchange.getRequestURI().getPath();
        HttpHandler route = routes.get(asked);
        if (route != null) {
            route.handle(exchange);
            return;
        }
        if (asked.equals("/") || asked.equals("/index.html")) {
            respond(exchange, runnerPage.getBytes(StandardCharsets.UTF_8), "text/html; charset=utf-8", 200);
            return;
        }
        if (asked.equals("/main.reb")) {
            respond(exchange, entryScript.getBytes(StandardCharsets.UTF_8), "text/plain; charset=utf-8", 200);
            return;
        }
        Path file = webBuild.resolve(asked.substring(1)).normalize();
        if (!file.startsWith(webBuild) || !Files.isRegularFile(file)) {
            respond(exchange, new byte[0], "text/plain", 404);
            return;
        }
        respond(exchange, Files.readAllBytes(file), contentTypeFor(asked), 200);
    }

    private String contentTypeFor(String asked) {
        if (asked.endsWith(".wasm")) {
            return "application/wasm";
        }
        if (asked.endsWith(".ttf")) {
            return "font/ttf";
        }
        return "text/javascript";
    }
}
