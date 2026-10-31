package org.jebol.adapter.web;

import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(TheSuitesPageBrowsers.class)
class TheLoadingScreenEndToEndTest {

    private static final Path THE_MODULE = Path.of(
            System.getProperty("jebol.webBuild", "build/generated/teavm/wasm-gc"), "jebol-web.wasm");

    private static final int PIECES = 40;

    private static final long PAUSE_BETWEEN_PIECES_MILLISECONDS = 60;

    private final PageBrowsers browsers;

    TheLoadingScreenEndToEndTest(PageBrowsers browsers) {
        this.browsers = browsers;
    }

    private static void sendSlowly(HttpExchange exchange) throws IOException {
        byte[] module = Files.readAllBytes(THE_MODULE);
        exchange.getResponseHeaders().add("Content-Type", "application/wasm");
        exchange.getResponseHeaders().add("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, module.length);
        try (OutputStream out = exchange.getResponseBody()) {
            int piece = module.length / PIECES + 1;
            for (int from = 0; from < module.length; from += piece) {
                out.write(module, from, Math.min(piece, module.length - from));
                out.flush();
                try {
                    Thread.sleep(PAUSE_BETWEEN_PIECES_MILLISECONDS);
                } catch (InterruptedException stopped) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static Optional<String> readWhatTheLoadingScreenSays(AnOperatorAtThePage operator) {
        List<WebElement> loading = operator.browser().findElements(By.id("loading"));
        return loading.isEmpty() ? Optional.empty() : Optional.of(loading.getFirst().getText());
    }

    @Test
    @DisplayName("while the module downloads the page shows a spinner and how much of it has come, then clears once the app paints")
    void showsTheDownloadThenClears() throws IOException {
        long megabytesTenths = Math.round(Files.size(THE_MODULE) * 10.0 / (1024 * 1024));
        String ofTheWhole = " of " + (megabytesTenths / 10) + "." + (megabytesTenths % 10) + " MB";
        try (ShippedRunner runner = new ShippedRunner()
                .serving("/jebol-web.wasm", TheLoadingScreenEndToEndTest::sendSlowly)
                .running("""
                        view/no-wait make gob! [size: 100x100 color: 200.0.0]
                        print "painted"
                        """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers, "chrome", runner).opensThePage()) {

            List<String> seen = new ArrayList<>();
            new PageWait(operator.browser(), Duration.ofSeconds(60)).until(anything -> {
                Optional<String> says = readWhatTheLoadingScreenSays(operator);
                says.filter(text -> seen.isEmpty() || !seen.getLast().equals(text)).ifPresent(seen::add);
                return says.isEmpty();
            });

            assertThat(seen).anyMatch(text -> text.startsWith("Downloading") && text.endsWith(ofTheWhole));
            assertThat(operator.browser().findElements(By.className("jebol-loading-spinner"))).isEmpty();
            operator.waitUntilOutputContains("painted");
        }
    }

    @Test
    @DisplayName("an app that prints and never paints clears the loading screen when it finishes")
    void clearsWhenAnAppOnlyPrints() throws IOException {
        try (ShippedRunner runner = new ShippedRunner().running("""
                print "only words"
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers, "chrome", runner).opensThePage()) {

            operator.waitUntilOutputContains("only words");
            new PageWait(operator.browser(), Duration.ofSeconds(20))
                    .until(anything -> readWhatTheLoadingScreenSays(operator).isEmpty());
        }
    }

    @Test
    @DisplayName("a module that cannot be had leaves the loading screen up, saying JEBOL could not start, without its spinner")
    void saysWhenTheModuleCannotBeHad() throws IOException {
        try (ShippedRunner runner = new ShippedRunner()
                .serving("/jebol-web.wasm", exchange -> runnerRefuses(exchange))
                .running("print {never}");
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers, "chrome", runner).opensThePage()) {

            new PageWait(operator.browser(), Duration.ofSeconds(30)).until(anything ->
                    readWhatTheLoadingScreenSays(operator).filter(text -> text.startsWith("JEBOL could not start")).isPresent());
            assertThat(operator.browser().findElement(By.className("jebol-loading-spinner")).isDisplayed()).isFalse();
        }
    }

    private static void runnerRefuses(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(404, -1);
        exchange.close();
    }
}
