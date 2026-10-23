package org.jebol.adapter.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.interactions.WheelInput;
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

@ExtendWith(TheSuitesPageBrowsers.class)
class TheExamplesWithNoServerEndToEndTest {

    private final PageBrowsers browsers;
    private Path theSite;
    private HttpServer serving;
    private AnOperatorAtThePage operator;
    private WebDriver browser;

    TheExamplesWithNoServerEndToEndTest(PageBrowsers browsers) {
        this.browsers = browsers;
    }

    @BeforeEach
    void serveTheSite() throws IOException {
        theSite = Path.of(System.getProperty("jebol.webSite", "build/site"));
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

    private WebDriverWait patientBrowser() {
        return new WebDriverWait(browser, Duration.ofSeconds(60));
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

    private String findTheLineStartingWith(String prefix) {
        return readTheLinesSoFar().stream()
                .filter(line -> line.startsWith(prefix))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError(
                        "the page never said a line starting " + prefix + "; it said: " + readTheLinesSoFar()));
    }

    private Actions placeTheOperatorAt(int pageAcross, int pageDown) {
        WebElement canvas = browser.findElement(By.id("screen"));
        return new Actions(browser).moveToElement(canvas,
                pageAcross - canvas.getRect().getWidth() / 2,
                pageDown - canvas.getRect().getHeight() / 2);
    }

    private static int readTheAcrossOf(String pair) {
        return (int) Math.round(Double.parseDouble(pair.substring(0, pair.indexOf('x'))));
    }

    private static int readTheDownOf(String pair) {
        return (int) Math.round(Double.parseDouble(pair.substring(pair.indexOf('x') + 1)));
    }

    @Test
    @DisplayName("the sliders example runs in a page with no server, and a real drag changes its value")
    void dragsTheSliderWithNoServer() {
        waitUntilThePageSays("the two example windows",
                () -> readTheLinesSoFar().contains("open")
                        && readTheLinesSoFar().stream().anyMatch(line -> line.startsWith("booted in "))
                        && readTheLinesSoFar().stream().anyMatch(line -> line.startsWith("worker loaded in "))
                        && readTheLinesSoFar().stream().anyMatch(line -> line.startsWith("slider-at "))
                        && readTheLinesSoFar().stream().anyMatch(line -> line.startsWith("field-at ")));

        String middle = findTheLineStartingWith("slider-at ").substring("slider-at ".length());
        String before = findTheLineStartingWith("slider ").substring("slider ".length());

        placeTheOperatorAt(readTheAcrossOf(middle), readTheDownOf(middle)).clickAndHold()
                .moveByOffset(80, 0).release().perform();

        waitUntilThePageSays("the slider's value changed",
                () -> readTheLinesSoFar().stream()
                        .filter(line -> line.startsWith("slider "))
                        .count() > 1);
        assertThat(findTheLineStartingWith("slider ").substring("slider ".length()))
                .as("the slider the operator dragged is not the one it was")
                .isNotEqualTo(before);
    }

    @Test
    @DisplayName("the scroller is turned by a real wheel in a page with no server, and its value changes")
    void turnsTheScrollerWithNoServer() {
        waitUntilThePageSays("the scroller is found and the windows are open",
                () -> readTheLinesSoFar().contains("open")
                        && readTheLinesSoFar().stream().anyMatch(line -> line.startsWith("scroller-at "))
                        && readTheLinesSoFar().stream().anyMatch(line -> line.startsWith("scroller ")));

        String middle = findTheLineStartingWith("scroller-at ").substring("scroller-at ".length());
        String before = findTheLineStartingWith("scroller ").substring("scroller ".length());
        int across = readTheAcrossOf(middle);
        int down = readTheDownOf(middle);

        placeTheOperatorAt(across, down).perform();
        new Actions(browser)
                .scrollFromOrigin(WheelInput.ScrollOrigin.fromViewport(across, down), 0, 400)
                .perform();

        waitUntilThePageSays("the scroller's value changed",
                () -> readTheLinesSoFar().stream()
                        .filter(line -> line.startsWith("scroller "))
                        .count() > 1);
        assertThat(findTheLineStartingWith("scroller ").substring("scroller ".length()))
                .as("the scroller the operator turned is not the one it was")
                .isNotEqualTo(before);
    }

    @Test
    @DisplayName("and the survey runs there too: a real click and real keys put a name in its field")
    void takesATypedNameInTheSurveyWithNoServer() {
        waitUntilThePageSays("the two example windows",
                () -> readTheLinesSoFar().contains("open")
                        && readTheLinesSoFar().stream().anyMatch(line -> line.startsWith("field-at ")));

        String middle = findTheLineStartingWith("field-at ").substring("field-at ".length());
        placeTheOperatorAt(readTheAcrossOf(middle), readTheDownOf(middle)).click().perform();
        new Actions(browser).sendKeys("Ben").perform();

        waitUntilThePageSays("the field to hold the typed name",
                () -> readTheLinesSoFar().contains("field Ben"));
        assertThat(readTheLinesSoFar()).contains("field Ben");
    }

    private void answer(HttpExchange exchange) throws IOException {
        String asked = exchange.getRequestURI().getPath();
        Path file = theSite.resolve(asked.equals("/") ? "index.html" : asked.substring(1)).normalize();
        if (!file.startsWith(theSite) || !Files.isRegularFile(file)) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        respond(exchange, Files.readAllBytes(file), readTheContentTypeOf(file), 200);
    }

    private static String readTheContentTypeOf(Path file) {
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
