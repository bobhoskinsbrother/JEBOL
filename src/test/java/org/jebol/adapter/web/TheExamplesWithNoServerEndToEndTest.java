package org.jebol.adapter.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("browser")
class TheExamplesWithNoServerEndToEndTest {
    private static final int PAGE_WIDE = 1200;

    private static final int PAGE_HIGH = 900;

    private Path theSite;
    private HttpServer serving;
    private ChromeDriver browser;

    @BeforeEach
    void serveTheSite() throws IOException {
        theSite = Path.of(System.getProperty("jebol.webSite", "build/site"));
        serving = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serving.createContext("/", this::answer);
        serving.start();
        ChromeOptions asked = new ChromeOptions();
        asked.addArguments("--headless=new", "--no-sandbox",
                "--window-size=" + PAGE_WIDE + "," + PAGE_HIGH,
                "--force-device-scale-factor=1",
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
        return new WebDriverWait(browser, Duration.ofSeconds(60));
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

    private String theLineStartingWith(String prefix) {
        return theLinesSoFar().stream()
                .filter(line -> line.startsWith(prefix))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError(
                        "the page never said a line starting " + prefix + "; it said: " + theLinesSoFar()));
    }

    private Actions theOperatorAt(int pageAcross, int pageDown) {
        WebElement canvas = browser.findElement(By.id("screen"));
        return new Actions(browser).moveToElement(canvas,
                pageAcross - canvas.getRect().getWidth() / 2,
                pageDown - canvas.getRect().getHeight() / 2);
    }

    private static int acrossOf(String pair) {
        return (int) Math.round(Double.parseDouble(pair.substring(0, pair.indexOf('x'))));
    }

    private static int downOf(String pair) {
        return (int) Math.round(Double.parseDouble(pair.substring(pair.indexOf('x') + 1)));
    }

    @Test
    @DisplayName("the sliders example runs in a page with no server, and a real drag changes its value")
    void theSliderIsDraggedWithNoServer() {
        untilThePageSays("the two example windows",
                () -> theLinesSoFar().contains("open")
                        && theLinesSoFar().stream().anyMatch(line -> line.startsWith("booted in "))
                        && theLinesSoFar().stream().anyMatch(line -> line.startsWith("slider-at "))
                        && theLinesSoFar().stream().anyMatch(line -> line.startsWith("field-at ")));

        String middle = theLineStartingWith("slider-at ").substring("slider-at ".length());
        String before = theLineStartingWith("slider ").substring("slider ".length());

        theOperatorAt(acrossOf(middle), downOf(middle)).clickAndHold()
                .moveByOffset(80, 0).release().perform();

        untilThePageSays("the slider's value changed",
                () -> theLinesSoFar().stream()
                        .filter(line -> line.startsWith("slider "))
                        .count() > 1);
        assertThat(theLineStartingWith("slider ").substring("slider ".length()))
                .as("the slider the operator dragged is not the one it was")
                .isNotEqualTo(before);
    }

    @Test
    @DisplayName("and the survey runs there too: a real click and real keys put a name in its field")
    void theSurveyTakesATypedNameWithNoServer() {
        untilThePageSays("the two example windows",
                () -> theLinesSoFar().contains("open")
                        && theLinesSoFar().stream().anyMatch(line -> line.startsWith("field-at ")));

        String middle = theLineStartingWith("field-at ").substring("field-at ".length());
        theOperatorAt(acrossOf(middle), downOf(middle)).click().perform();
        new Actions(browser).sendKeys("Ben").perform();

        untilThePageSays("the field to hold the typed name",
                () -> theLinesSoFar().contains("field Ben"));
        assertThat(theLinesSoFar()).contains("field Ben");
    }

    private void answer(HttpExchange exchange) throws IOException {
        String asked = exchange.getRequestURI().getPath();
        Path file = theSite.resolve(asked.equals("/") ? "index.html" : asked.substring(1)).normalize();
        if (!file.startsWith(theSite) || !Files.isRegularFile(file)) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        respond(exchange, Files.readAllBytes(file), theContentTypeOf(file), 200);
    }

    private static String theContentTypeOf(Path file) {
        String name = file.getFileName().toString();
        if (name.endsWith(".html")) {
            return "text/html";
        }
        if (name.endsWith(".wasm")) {
            return "application/wasm";
        }
        if (name.endsWith(".ttf")) {
            return "font/ttf";
        }
        return name.endsWith(".js") ? "text/javascript" : "text/plain";
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
