package org.jebol.domain.eval;

import com.sun.net.httpserver.HttpServer;
import org.jebol.adapter.host.JavaSocket;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ReadingHttpFromALocalServerFromTheSourceTest {

    private HttpServer server;
    private int port;
    private final AtomicInteger requestsServed = new AtomicInteger();

    @BeforeEach
    void serveThePagesTheseTestsRead() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestsServed.incrementAndGet();
            byte[] body;
            int status;
            switch (exchange.getRequestURI().getPath()) {
                case "/" -> {
                    status = 200;
                    body = "hello".getBytes(StandardCharsets.UTF_8);
                }
                case "/lines" -> {
                    status = 200;
                    body = "one\ntwo\n".getBytes(StandardCharsets.UTF_8);
                }
                case "/missing" -> {
                    status = 404;
                    body = "gone".getBytes(StandardCharsets.UTF_8);
                }
                default -> {
                    status = 404;
                    body = new byte[0];
                }
            }
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream response = exchange.getResponseBody()) {
                response.write(body);
            }
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stopServing() {
        server.stop(0);
    }

    private static Interpreter reachingTheNetwork() {
        return Interpreter.createOn(
                Interpreter.standardPorts().withNetwork(new JavaSocket()),
                OutputPort.discard(),
                Bounds.standard()
                        .grant(HostService.NETWORK)
                        .grant(HostService.CLOCK));
    }

    private String answerTo(String source) {
        Interpreter interpreter = reachingTheNetwork();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private String urlFor(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    @Test
    @DisplayName("reading an http url answers the body the server sent")
    void answersTheBodyTheServerSent() {
        assertThat(answerTo("read " + urlFor("/"))).isEqualTo("\"hello\"");
        assertThat(requestsServed.get()).isOne();
    }

    @Test
    @DisplayName("/lines answers the body's lines")
    void linesAnswersTheBodysLines() {
        assertThat(answerTo("""
                l: read/lines %s
                reduce [length? l l/1 l/2]""".formatted(urlFor("/lines"))))
                .isEqualTo("[2 \"one\" \"two\"]");
    }

    @Test
    @DisplayName("/part answers the first characters")
    void partAnswersTheFirstCharacters() {
        assertThat(answerTo("read/part " + urlFor("/") + " 3")).isEqualTo("\"hel\"");
    }

    @Test
    @DisplayName("/all answers the status and the body of a page that is not there")
    void allAnswersTheStatusAndBodyOfAPageThatIsNotThere() {
        assertThat(answerTo("""
                res: read/all %s
                reduce [res/1 to string! res/3]""".formatted(urlFor("/missing"))))
                .isEqualTo("[404 \"gone\"]");
    }

    @Test
    @DisplayName("reading a page that is not there raises rather than answering it")
    void readingAPageThatIsNotThereRaises() {
        assertThat(answerTo("error? try [read " + urlFor("/missing") + "]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("with net refused, the read is refused naming the address, and nothing is requested")
    void withNetRefusedTheReadIsRefused() {
        assertThat(answerTo("""
                secure [net throw]
                failure: try [read %s]
                reduce [failure/id failure/arg1]""".formatted(urlFor("/"))))
                .isEqualTo("[security tcp://127.0.0.1:%d]".formatted(port));
        assertThat(requestsServed.get()).isZero();
    }
}
