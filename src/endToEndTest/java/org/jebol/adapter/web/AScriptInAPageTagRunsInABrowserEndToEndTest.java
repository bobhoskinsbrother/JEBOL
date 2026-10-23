package org.jebol.adapter.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
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

@ExtendWith(TheSuitesPageBrowsers.class)
class AScriptInAPageTagRunsInABrowserEndToEndTest {

    private final PageBrowsers browsers;
    private Path theWebBuild;
    private String thePage;
    private HttpServer serving;
    private AnOperatorAtThePage operator;
    private WebDriver browser;

    AScriptInAPageTagRunsInABrowserEndToEndTest(PageBrowsers browsers) {
        this.browsers = browsers;
    }

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
        operator = new AnOperatorAtThePage(browsers, "chrome",
                "http://127.0.0.1:" + serving.getAddress().getPort() + "/").opensThePage();
        browser = operator.browser();
    }

    @AfterEach
    void closeTheBrowser() {
        if (operator != null) {
            operator.close();
        }
        serving.stop(0);
    }

    private String readTheWindowsInnerSize() {
        return (String) operator.runsInThePage("return window.innerWidth + 'x' + window.innerHeight;");
    }

    private long readTheWindowsInnerWidth() {
        return (Long) operator.runsInThePage("return window.innerWidth;");
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

    private void waitUntilThePageSaysOrGivesUp(String whatWasHoped, Supplier<Boolean> hoped) {
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

    private void waitUntilTheOutputSays(int atLeastLines, String containing) {
        waitUntilThePageSaysOrGivesUp("saying " + containing, () -> {
            List<String> lines = readTheLinesSoFar();
            return lines.size() >= atLeastLines
                    && lines.stream().anyMatch(line -> line.contains(containing));
        });
    }

    private String readThePixelAt(int across, int down) {
        return ((JavascriptExecutor) browser).executeScript("""
                const d = document.getElementById('screen').getContext('2d')
                    .getImageData(arguments[0], arguments[1], 1, 1).data;
                return [d[0], d[1], d[2], d[3]].join(',');
                """, across, down).toString();
    }

    @Test
    @DisplayName("the script in the page's tag runs in the worker and prints the Brotli answers, waiting included")
    void runsTheScriptInTheTag() {
        assertThat(browser.findElement(By.cssSelector("script[type='text/rebol']")).getDomAttribute("id"))
                .isEqualTo("rebol");
        waitUntilTheOutputSays(8, "waited");
        List<String> lines = readTheLinesSoFar();
        assertThat(lines.subList(0, 8))
                .containsExactly("3", "true", "true", "true", "true", "true", "true", "waited");
    }

    @Test
    @DisplayName("the window the second script opens is painted on the canvas the page owns")
    void paintsTheWindowOnTheCanvas() {
        waitUntilTheOutputSays(9, "open");

        waitUntilThePageSaysOrGivesUp("a painted canvas",
                () -> readThePixelAt(70, 80).equals("250,10,10,255"));
    }

    @Test
    @DisplayName("a real click on the canvas reaches the interpreter in the worker, counted from the window")
    void carriesAClickToTheInterpreterInTheWorker() {
        waitUntilTheOutputSays(9, "open");

        WebElement canvas = browser.findElement(By.id("screen"));
        waitUntilThePageSaysOrGivesUp("a canvas as wide as the window to click",
                () -> canvas.getRect().getWidth() == readTheWindowsInnerWidth());
        Rectangle where = canvas.getRect();

        new Actions(browser).moveToElement(canvas,
                70 - where.getWidth() / 2, 80 - where.getHeight() / 2).click().perform();

        waitUntilTheOutputSays(10, "clicked 50x5");
    }

    @Test
    @DisplayName("a browser resized while the script waits is taken, and the script sees the new size")
    void takesABrowserResizeWhileTheScriptWaits() {
        waitUntilTheOutputSays(9, "open");
        String before = readTheWindowsInnerSize();

        browser.manage().window().setSize(new Dimension(500, 400));

        waitUntilThePageSaysOrGivesUp("the new size",
                () -> readTheLinesSoFar().stream()
                        .anyMatch(line -> line.startsWith("now ")
                                && !line.equals("now " + before)));
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
            contentType = asked.endsWith(".wasm") ? "application/wasm"
                    : asked.endsWith(".ttf") ? "font/ttf" : "text/javascript";
        }
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream response = exchange.getResponseBody()) {
            response.write(body);
        }
    }
}
