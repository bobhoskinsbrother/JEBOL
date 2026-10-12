package org.jebol.adapter.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("browser")
class TheBrowserAdaptersEndToEndTest {

    private Path theWebBuild;
    private String thePage;
    private HttpServer serving;
    private ChromeDriver browser;

    @BeforeEach
    void serveTheWebBuild() throws IOException {
        theWebBuild = Path.of(System.getProperty("jebol.webBuild", "build/generated/teavm/wasm-gc"));
        try (var page = TheBrowserAdaptersEndToEndTest.class
                .getResourceAsStream("/web/rebol-adapters-on-a-page.html")) {
            thePage = new String(page.readAllBytes(), StandardCharsets.UTF_8);
        }
        serving = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serving.createContext("/", this::answer);
        serving.start();
        ChromeOptions asked = new ChromeOptions();
        asked.addArguments("--headless=new", "--no-sandbox",
                "--window-size=800,600", "--force-device-scale-factor=1",
                "--hide-scrollbars");
        browser = new ChromeDriver(asked);
        browser.get("http://127.0.0.1:" + serving.getAddress().getPort() + "/");
    }

    @AfterEach
    void closeTheBrowser() {
        if (browser != null) {
            browser.quit();
        }
        serving.stop(0);
    }

    private WebDriverWait patientBrowser() {
        return new WebDriverWait(browser, Duration.ofSeconds(30));
    }

    private List<String> theLinesSoFar() {
        return browser.findElement(By.id("out")).getDomProperty("textContent").lines().toList();
    }

    private boolean theWorkerGaveUp(List<String> lines) {
        return lines.stream().anyMatch(line ->
                line.startsWith("failed ") || line.startsWith("worker error"));
    }

    private void untilThePageSays(String whatWasHoped, Supplier<Boolean> hoped) {
        try {
            patientBrowser().until(anything -> theWorkerGaveUp(theLinesSoFar()) || hoped.get());
        } catch (TimeoutException late) {
            throw new AssertionError("the page never " + whatWasHoped
                    + "; the page said: " + theLinesSoFar(), late);
        }
        List<String> lines = theLinesSoFar();
        if (theWorkerGaveUp(lines)) {
            throw new AssertionError("the worker gave up before " + whatWasHoped
                    + "; the page said: " + lines);
        }
    }

    private void untilTheOutputContains(String line) {
        untilThePageSays("saying " + line, () -> theLinesSoFar().contains(line));
    }

    @Test
    @DisplayName("a script fetches, keeps a file, reads the page's own line, and is refused the rest")
    void theScriptReachesTheOutside() {
        untilTheOutputContains("kept");

        WebElement consoleInput = browser.findElement(By.id("console"));
        consoleInput.sendKeys("typed-here" + Keys.ENTER);

        untilThePageSays("the refusals",
                () -> theLinesSoFar().contains("feature-na"));

        assertThat(theLinesSoFar()).containsSubsequence(
                "hello",
                "true",
                "kept",
                "typed-here",
                "true",
                "protocol");
        assertThat(theLinesSoFar()).contains("feature-na");
    }

    private void answer(HttpExchange exchange) throws IOException {
        String asked = exchange.getRequestURI().getPath();
        if (asked.equals("/hello")) {
            respond(exchange, "hello".getBytes(StandardCharsets.UTF_8), "text/plain", 200);
            return;
        }
        if (asked.equals("/missing")) {
            respond(exchange, "gone".getBytes(StandardCharsets.UTF_8), "text/plain", 404);
            return;
        }
        byte[] body;
        String contentType;
        if (asked.equals("/")) {
            body = thePage.getBytes(StandardCharsets.UTF_8);
            contentType = "text/html";
        } else {
            Path file = theWebBuild.resolve(asked.substring(1)).normalize();
            if (!file.startsWith(theWebBuild) || !Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            body = Files.readAllBytes(file);
            contentType = asked.endsWith(".wasm") ? "application/wasm" : "text/javascript";
        }
        respond(exchange, body, contentType, 200);
    }

    private static void respond(
            HttpExchange exchange, byte[] body, String contentType, int status) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream response = exchange.getResponseBody()) {
            response.write(body);
        }
    }
}
