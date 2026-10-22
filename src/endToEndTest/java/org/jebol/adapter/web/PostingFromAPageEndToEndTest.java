package org.jebol.adapter.web;

import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class PostingFromAPageEndToEndTest {

    @TempDir
    Path downloads;

    private final List<String> received = new CopyOnWriteArrayList<>();

    private void echo(ShippedRunner runner, HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        received.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath()
                + " " + exchange.getRequestHeaders().getFirst("Content-Type") + " " + body);
        int status = exchange.getRequestURI().getPath().endsWith("/missing") ? 404 : 200;
        runner.respond(exchange, ("you sent " + exchange.getRequestMethod()).getBytes(StandardCharsets.UTF_8),
                "text/plain; charset=utf-8", status);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome", "firefox"})
    @DisplayName("WRITE to an http url posts from the page through fetch and answers the reply")
    void postsFromThePage(String browserName) throws IOException {
        try (ShippedRunner runner = new ShippedRunner()) {
            runner.serving("/form", exchange -> echo(runner, exchange))
                    .serving("/json", exchange -> echo(runner, exchange))
                    .serving("/missing", exchange -> echo(runner, exchange))
                    .running("""
                            print write to url! rejoin [{THE-ORIGIN} "/form"] "a=b c"
                            print write to url! rejoin [{THE-ORIGIN} "/json"] #[a: 1]
                            e: try [write to url! rejoin [{THE-ORIGIN} "/missing"] "x"]
                            print [e/id e/arg1]
                            print "done"
                            """);
            try (AnOperatorAtThePage operator =
                         new AnOperatorAtThePage(browserName, runner, downloads).opensThePage()) {

                operator.waitUntilOutputContains("done");
                assertThat(operator.readOutput()).containsSubsequence(
                        "you sent POST", "you sent POST", "protocol Not Found");
                assertThat(received).containsExactly(
                        "POST /form application/x-www-form-urlencoded; charset=utf-8 a=b c",
                        """
                                POST /json application/json; charset=utf-8 {"a":1}""",
                        "POST /missing application/x-www-form-urlencoded; charset=utf-8 x");
            }
        }
    }
}
