package org.jebol.devtools;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.jebol.adapter.web.AnOperatorAtThePage;
import org.jebol.adapter.web.PageBrowsers;
import org.jebol.adapter.web.TheSuitesPageBrowsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(TheSuitesPageBrowsers.class)
@Timeout(600)
class TheExampleAppsSiteEndToEndTest {

    private static final Map<String, String> CONTENT_TYPES = Map.of(".html", "text/html", ".wasm", "application/wasm",
            ".ttf", "font/ttf", ".js", "text/javascript", ".png", "image/png", ".webmanifest", "application/manifest+json",
            ".json", "application/json");

    private final PageBrowsers browsers;
    private HttpServer serving;
    private AnOperatorAtThePage operator;
    private Path site;

    @TempDir
    Path scratch;

    TheExampleAppsSiteEndToEndTest(PageBrowsers browsers) {
        this.browsers = browsers;
    }

    @AfterEach
    void stopServing() {
        if (operator != null) {
            operator.close();
        }
        if (serving != null) {
            serving.stop(0);
        }
    }

    private List<String> linesSoFar() {
        List<String> lines = new ArrayList<>(operator.readOutput());
        lines.addAll(operator.readErrorOutput());
        return lines;
    }

    private void waitForLine(String wanted) {
        try {
            new WebDriverWait(operator.browser(), Duration.ofSeconds(90)).until(anything -> linesSoFar().contains(wanted));
        } catch (TimeoutException late) {
            throw new AssertionError("the page never said " + wanted + "; it said: " + linesSoFar(), late);
        }
    }

    private String lineStartingWith(String prefix) {
        return linesSoFar().stream().filter(line -> line.startsWith(prefix)).reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("no line starting " + prefix + " in " + linesSoFar()));
    }

    @Test
    @DisplayName("the example app packaged for the web opens in a page with its title and manifest, and a click on its button is heard")
    void theExampleRunsInAPage() throws IOException {
        TheExampleApp example = TheExampleApp.copiedInto(scratch);
        example.run("packageWeb");
        site = example.directory().resolve("build/jebol/web/site");
        serving = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serving.createContext("/", this::answer);
        serving.start();
        operator = new AnOperatorAtThePage(browsers, "chrome",
                "http://127.0.0.1:" + serving.getAddress().getPort() + "/").opensThePage();

        waitForLine("open");
        assertThat(operator.browser().getTitle()).isEqualTo(TheExampleApp.TITLE);
        String manifest = (String) ((JavascriptExecutor) operator.browser()).executeScript(
                "return document.querySelector('link[rel=manifest]').href");
        assertThat(manifest).endsWith("/manifest.webmanifest");
        String place = lineStartingWith("button-at ").substring("button-at ".length());
        int across = (int) Math.round(Double.parseDouble(place.substring(0, place.indexOf('x'))));
        int down = (int) Math.round(Double.parseDouble(place.substring(place.indexOf('x') + 1)));
        WebElement canvas = operator.browser().findElement(By.id("screen"));
        new Actions(operator.browser())
                .moveToElement(canvas, across - canvas.getRect().getWidth() / 2, down - canvas.getRect().getHeight() / 2)
                .click()
                .perform();

        waitForLine("notes 1");
    }

    private void answer(HttpExchange exchange) throws IOException {
        String asked = exchange.getRequestURI().getPath();
        Path file = site.resolve(asked.equals("/") ? "index.html" : asked.substring(1)).normalize();
        if (!file.startsWith(site) || !Files.isRegularFile(file)) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        String name = file.getFileName().toString();
        String type = CONTENT_TYPES.getOrDefault(name.substring(Math.max(0, name.lastIndexOf('.'))), "text/plain");
        byte[] body = Files.readAllBytes(file);
        exchange.getResponseHeaders().add("Content-Type", type);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
