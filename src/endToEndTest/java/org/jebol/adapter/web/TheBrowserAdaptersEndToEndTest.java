package org.jebol.adapter.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.jebol.application.DefaultFont;
import org.jebol.domain.host.DigestPorts;
import org.jebol.domain.render.Colour;
import org.jebol.domain.render.TextExtent;
import org.jebol.domain.render.TextRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

    private List<String> readTheLinesSoFar() {
        return browser.findElement(By.id("out")).getDomProperty("textContent").lines().toList();
    }

    private boolean hasTheWorkerGivenUp(List<String> lines) {
        return lines.stream().anyMatch(line ->
                line.startsWith("failed ") || line.startsWith("worker error"));
    }

    private void waitUntilThePageSays(String whatWasHoped, Supplier<Boolean> hoped) {
        try {
            patientBrowser().until(anything -> hasTheWorkerGivenUp(readTheLinesSoFar()) || hoped.get());
        } catch (TimeoutException late) {
            throw new AssertionError("the page never " + whatWasHoped
                    + "; the page said: " + readTheLinesSoFar(), late);
        }
        List<String> lines = readTheLinesSoFar();
        if (hasTheWorkerGivenUp(lines)) {
            throw new AssertionError("the worker gave up before " + whatWasHoped
                    + "; the page said: " + lines);
        }
    }

    private void waitUntilTheOutputContains(String line) {
        waitUntilThePageSays("saying " + line, () -> readTheLinesSoFar().contains(line));
    }

    @Test
    @DisplayName("a script fetches, keeps a file, reads the page's own line, measures text, and is refused the rest")
    void letsTheScriptReachTheOutside() {
        waitUntilTheOutputContains("kept");

        WebElement consoleInput = browser.findElement(By.id("console"));
        consoleInput.sendKeys("typed-here" + Keys.ENTER);

        waitUntilThePageSays("the refusals",
                () -> readTheLinesSoFar().contains(measureTheLabelsText()));

        assertThat(readTheLinesSoFar()).containsSubsequence(
                "hello",
                "true",
                "kept",
                "typed-here",
                "true",
                "protocol");
        assertThat(readTheLinesSoFar())
                .as("the worker's SHA-1 is the same digest the JVM takes")
                .contains("#{" + writeTheHexOf("abc") + "}");
        assertThat(readTheLinesSoFar())
                .as("and its HMAC is the same keyed digest the JVM takes")
                .contains("#{" + writeTheKeyedHexOf("abc") + "}");
        assertThat(readTheLinesSoFar())
                .as("the worker measured with the font this build carries")
                .contains(measureTheLabelsText());

        browser.navigate().refresh();
        waitUntilThePageSays("the file it wrote before the reload",
                () -> readTheLinesSoFar().contains("again kept"));
        assertThat(readTheLinesSoFar())
                .as("a file a script wrote is a file a reload finds")
                .contains("again kept");
    }

    private static String measureTheLabelsText() {
        TextExtent extent = DefaultFont.measurement().measureTheExtentOf(
                new TextRun("MMMM", Colour.BLACK, 12, false, false));
        return Math.round(extent.wide()) + "x" + Math.round(extent.high());
    }

    private static String writeTheHexOf(String text) {
        return encodeAsHex(DigestPorts.chooseTheOneForThisBuild()
                .digestOf("sha1", text.getBytes(StandardCharsets.UTF_8)));
    }

    private static String writeTheKeyedHexOf(String text) {
        return encodeAsHex(DigestPorts.chooseTheOneForThisBuild().digestWithAKey(
                "sha1", text.getBytes(StandardCharsets.UTF_8),
                "key".getBytes(StandardCharsets.UTF_8)));
    }

    private static String encodeAsHex(byte[] octets) {
        StringBuilder written = new StringBuilder();
        for (byte octet : octets) {
            written.append(String.format("%02X", octet));
        }
        return written.toString();
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
            contentType = asked.endsWith(".wasm") ? "application/wasm"
                    : asked.endsWith(".ttf") ? "font/ttf" : "text/javascript";
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
