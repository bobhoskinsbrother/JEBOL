package org.jebol.application;

import org.jebol.adapter.host.JavaSocket;
import org.jebol.domain.host.HostService;
import org.jebol.tls.Ipv4Loopback;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
class PlainHttpWhereThePlatformRefusesItFromTheSourceTest {

    private static final String REFUSED_HOST = "127.0.0.1";

    private ServerSocket server;
    private final AtomicInteger connectionsTaken = new AtomicInteger();

    @BeforeEach
    void serveOneAnswerForEachConnection() throws IOException {
        server = new ServerSocket(0, 50, Ipv4Loopback.address());
        Thread serving = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket client = server.accept()) {
                    connectionsTaken.incrementAndGet();
                    readRequestHead(client.getInputStream());
                    OutputStream out = client.getOutputStream();
                    out.write("""
                            HTTP/1.1 200 OK\r
                            Content-Type: text/plain\r
                            Content-Length: 5\r
                            Connection: close\r
                            \r
                            hello""".getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                } catch (IOException closed) {
                    return;
                }
            }
        }, "plain-http-server");
        serving.setDaemon(true);
        serving.start();
    }

    @AfterEach
    void stopServing() throws IOException {
        server.close();
    }

    private static void readRequestHead(InputStream in) throws IOException {
        int matched = 0;
        byte[] end = {'\r', '\n', '\r', '\n'};
        while (matched < end.length) {
            int read = in.read();
            if (read < 0) {
                return;
            }
            matched = read == end[matched] ? matched + 1 : (read == end[0] ? 1 : 0);
        }
    }

    private Interpreter interpreterRefusingCleartextTo(String refused) {
        return Interpreter.createOn(
                AnInterpreter.standardPorts().withNetwork(new JavaSocket(PlatformUnderTest.current().hostNames(),
                        host -> !host.equals(refused))),
                text -> {
                },
                AnInterpreter.stretchForThePlatform(Bounds.standard()
                        .withTimeLimit(Duration.ofSeconds(30))
                        .grant(HostService.NETWORK)
                        .grant(HostService.CLOCK)));
    }

    private static String run(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("plain http to a host the policy refuses is an access no-connect error naming the policy, and nothing is sent")
    void refusedHostIsNoConnect() {
        Interpreter interpreter = interpreterRefusingCleartextTo(REFUSED_HOST);
        String url = "http://" + REFUSED_HOST + ":" + server.getLocalPort() + "/";

        assertThat(run(interpreter, "e: try [read " + url + "] e/type = 'access")).isEqualTo("#(true)");
        assertThat(run(interpreter, "e/id = 'no-connect")).isEqualTo("#(true)");
        assertThat(run(interpreter, "e/arg1 = " + url)).isEqualTo("#(true)");
        assertThat(run(interpreter, "to logic! find e/arg2 {network security policy refuses cleartext}"))
                .isEqualTo("#(true)");
        assertThat(connectionsTaken).hasValue(0);
    }

    @Test
    @DisplayName("plain http to a host the policy allows reads as it always has")
    void allowedHostReads() {
        Interpreter interpreter = interpreterRefusingCleartextTo("somewhere.else");

        assertThat(run(interpreter, "{hello} = to string! read http://" + REFUSED_HOST + ":"
                + server.getLocalPort() + "/")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a tcp port a script opens itself is never asked, since the policy is about HTTP and not sockets")
    void rawTcpIsNotAsked() {
        Interpreter interpreter = interpreterRefusingCleartextTo(REFUSED_HOST);

        String answer = run(interpreter, """
                port: open tcp://127.0.0.1:%d
                port/awake: func [event] [
                    switch event/type [lookup [open event/port false] connect [true]]
                ]
                wait [port 10]
                close port
                port? port
                """.formatted(server.getLocalPort()));
        long giveUpAt = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (connectionsTaken.get() == 0 && System.nanoTime() < giveUpAt) {
            Thread.onSpinWait();
        }

        assertThat(answer).isEqualTo("#(true)");
        assertThat(connectionsTaken).hasValue(1);
    }
}
