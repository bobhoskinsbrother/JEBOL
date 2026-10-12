package org.jebol.adapter.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
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

import static org.assertj.core.api.Assertions.assertThat;

@Tag("browser")
class AScriptInAPageTagRunsInABrowserEndToEndTest {

    private Path theWebBuild;
    private String thePage;
    private HttpServer serving;
    private ChromeDriver browser;

    @BeforeEach
    void serveTheWebBuild() throws IOException {
        theWebBuild = Path.of(System.getProperty("jebol.webBuild", "build/generated/teavm/wasm-gc"));
        try (var page = AScriptInAPageTagRunsInABrowserEndToEndTest.class
                .getResourceAsStream("/web/rebol-on-a-page.html")) {
            thePage = new String(page.readAllBytes(), StandardCharsets.UTF_8);
        }
        serving = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serving.createContext("/", this::answer);
        serving.start();
        ChromeOptions asked = new ChromeOptions();
        asked.addArguments("--headless=new", "--no-sandbox");
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

    @Test
    @DisplayName("the script in the page's tag runs on the web build and prints the Brotli answers")
    void theScriptInTheTag() {
        assertThat(browser.findElement(By.cssSelector("script[type='text/rebol']")).getDomAttribute("id"))
                .isEqualTo("rebol");
        new WebDriverWait(browser, Duration.ofSeconds(120))
                .until(anything -> browser.findElements(By.id("out")).size() == 1);
        assertThat(browser.findElement(By.id("out")).getDomProperty("textContent").lines().toList())
                .containsExactly("3", "true", "true", "true", "true", "true", "true");
    }

    private void answer(HttpExchange exchange) throws IOException {
        String asked = exchange.getRequestURI().getPath();
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
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream response = exchange.getResponseBody()) {
            response.write(body);
        }
    }
}
