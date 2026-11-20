package org.jebol.adapter.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(TheSuitesPageBrowsers.class)
class TheExamplesOnAPhoneEndToEndTest {

    private static final int PHONE_WIDE = 390;
    private static final int PHONE_HIGH = 844;
    private static final int CLEAR_OF_THE_EDGE = 48;
    private static final int THE_PANELS_MARGIN = 6;

    private final PageBrowsers browsers;
    private Path theSite;
    private HttpServer serving;
    private AnOperatorAtThePage operator;
    private WebDriver browser;

    TheExamplesOnAPhoneEndToEndTest(PageBrowsers browsers) {
        this.browsers = browsers;
    }

    @BeforeEach
    void serveTheSiteToAPhone() throws IOException {
        theSite = Path.of(System.getProperty("jebol.webSite", "build/site"));
        serving = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serving.createContext("/", this::answer);
        serving.start();
        operator = new AnOperatorAtThePage(browsers, "chrome",
                "http://127.0.0.1:" + serving.getAddress().getPort() + "/").opensThePage();
        browser = operator.browser();
        ChromeDriver chrome = (ChromeDriver) browser;
        chrome.executeCdpCommand("Emulation.setDeviceMetricsOverride", Map.of(
                "width", PHONE_WIDE, "height", PHONE_HIGH, "deviceScaleFactor", 3, "mobile", true));
        chrome.executeCdpCommand("Emulation.setTouchEmulationEnabled", Map.of("enabled", true, "maxTouchPoints", 1));
        browser.navigate().refresh();
        waitUntilThePageSays("the examples opened on the phone", () -> readTheLinesSoFar().contains("open"));
    }

    @AfterEach
    void closeTheBrowser() {
        if (operator != null) {
            operator.close();
        }
        serving.stop(0);
    }

    private void answer(HttpExchange exchange) throws IOException {
        String asked = exchange.getRequestURI().getPath();
        Path file = theSite.resolve(asked.equals("/") ? "index.html" : asked.substring(1)).normalize();
        if (!file.startsWith(theSite) || !Files.isRegularFile(file)) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        byte[] body = Files.readAllBytes(file);
        exchange.getResponseHeaders().add("Content-Type", contentTypeFor(asked));
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static String contentTypeFor(String asked) {
        if (asked.equals("/") || asked.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (asked.endsWith(".wasm")) {
            return "application/wasm";
        }
        if (asked.endsWith(".json")) {
            return "application/json";
        }
        if (asked.endsWith(".ttf")) {
            return "font/ttf";
        }
        if (asked.endsWith(".reb") || asked.endsWith(".r")) {
            return "text/plain; charset=utf-8";
        }
        return "text/javascript";
    }

    private List<String> readTheLinesSoFar() {
        List<String> lines = new ArrayList<>(operator.readOutput());
        lines.addAll(operator.readErrorOutput());
        return lines;
    }

    private void waitUntilThePageSays(String whatWasHoped, Supplier<Boolean> hoped) {
        try {
            new PageWait(browser, Duration.ofSeconds(60)).until(anything -> hoped.get());
        } catch (TimeoutException late) {
            throw new AssertionError("the page never said " + whatWasHoped + "; it said: " + readTheLinesSoFar(), late);
        }
    }

    private String findTheLastLineStartingWith(String prefix) {
        return readTheLinesSoFar().stream()
                .filter(line -> line.startsWith(prefix))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("the page never said " + prefix + "; it said: " + readTheLinesSoFar()));
    }

    private static int[] readPlace(String line, String prefix) {
        String[] halves = line.substring(prefix.length()).trim().split("x");
        return new int[] {(int) Math.round(Double.parseDouble(halves[0])), (int) Math.round(Double.parseDouble(halves[1]))};
    }

    private void fingerDrags(int[] from, int[] to) {
        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence dragging = new Sequence(finger, 0)
                .addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), from[0], from[1]))
                .addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
        for (int step = 1; step <= 8; step++) {
            dragging.addAction(finger.createPointerMove(Duration.ofMillis(40), PointerInput.Origin.viewport(),
                    from[0] + (to[0] - from[0]) * step / 8, from[1] + (to[1] - from[1]) * step / 8));
        }
        dragging.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        ((ChromeDriver) browser).perform(List.of(dragging));
    }

    private void fingerTaps(int[] at) {
        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence tapping = new Sequence(finger, 0)
                .addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), at[0], at[1]))
                .addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
                .addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        ((ChromeDriver) browser).perform(List.of(tapping));
    }

    private int[] bringIntoView(String prefix) {
        for (int tries = 0; tries < 6; tries++) {
            String line = findTheLastLineStartingWith(prefix);
            int[] at = readPlace(line, prefix);
            if (at[1] >= CLEAR_OF_THE_EDGE && at[1] <= PHONE_HIGH - CLEAR_OF_THE_EDGE) {
                return at;
            }
            int from = PHONE_HIGH / 2;
            int by = Math.max(-from + CLEAR_OF_THE_EDGE, Math.min(from - CLEAR_OF_THE_EDGE, PHONE_HIGH / 2 - at[1]));
            fingerDrags(new int[] {THE_PANELS_MARGIN, from}, new int[] {THE_PANELS_MARGIN, from + by});
            waitUntilThePageSays("where " + prefix + "moved to",
                    () -> !findTheLastLineStartingWith(prefix).equals(line));
        }
        throw new AssertionError(prefix + "never came into view; the page said: " + readTheLinesSoFar());
    }

    @Test
    @DisplayName("on a phone the examples are one column, every face inside the screen's width")
    void theExamplesAreOneColumn() {
        for (String place : List.of("slider-at ", "field-at ", "scroller-at ")) {
            assertThat(readPlace(findTheLastLineStartingWith(place), place)[0])
                    .as(place + "lies across the phone's width")
                    .isBetween(0, PHONE_WIDE);
        }
    }

    @Test
    @DisplayName("a finger's drag moves the slider")
    void aFingerDragsTheSlider() {
        int[] slider = bringIntoView("slider-at ");
        String before = findTheLastLineStartingWith("slider ");

        fingerDrags(slider, new int[] {slider[0] + 60, slider[1]});

        waitUntilThePageSays("the slider's value changed", () -> !findTheLastLineStartingWith("slider ").equals(before));
    }

    @Test
    @DisplayName("a finger's drag in the margin scrolls the column, and a tap on the field takes the typed letters")
    void aFingerScrollsToTheFieldAndTypes() {
        int[] field = bringIntoView("field-at ");

        fingerTaps(field);
        new Actions(browser).sendKeys("Ada").perform();

        waitUntilThePageSays("the field held the typed name", () -> findTheLastLineStartingWith("field ").endsWith("Ada"));
    }
}
