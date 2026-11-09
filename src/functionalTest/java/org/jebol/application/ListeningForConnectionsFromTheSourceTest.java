package org.jebol.application;

import org.jebol.adapter.host.FileSystemPort;
import org.jebol.adapter.host.JavaCertificateAuthority;
import org.jebol.adapter.host.JavaSocket;
import org.jebol.domain.host.HostService;
import org.jebol.tls.TestCertificates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
class ListeningForConnectionsFromTheSourceTest {

    private static final Instant FIXTURE_TIME = Instant.parse("2030-06-01T12:00:00Z");
    private static final Duration TIME_LIMIT = Duration.ofSeconds(60);
    private static final String RS_DEMO_ANSWER = "Hello from Rebol using TLS v1.3";

    private final TestCertificates fixtures = new TestCertificates();

    private final StringBuilder printed = new StringBuilder();

    private Interpreter makeAnInterpreterTrustingTheTestRoot(Path root) {
        return Interpreter.createOn(
                AnInterpreter.standardPorts()
                        .withFiles(FileSystemPort.rootFileSystem(root))
                        .withNetwork(new JavaSocket())
                        .withCertificateAuthority(new JavaCertificateAuthority(
                                fixtures.trustOnly("test-root"), Clock.fixed(FIXTURE_TIME, ZoneOffset.UTC))),
                printed::append,
                Bounds.standard()
                        .withTimeLimit(TIME_LIMIT)
                        .grant(HostService.NETWORK)
                        .grant(HostService.CLOCK)
                        .grant(HostService.CERTIFICATES)
                        .grant(HostService.FILES)
                        .grant(HostService.WORKING_DIRECTORY));
    }

    private String run(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in = ListeningForConnectionsFromTheSourceTest.class.getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void copyTheServersFixtures(Path root) throws IOException {
        for (String name : new String[] {"localhost-rsa.pem", "test-intermediate.pem", "localhost-rsa.key"}) {
            Files.write(root.resolve(name), readResource("/org/jebol/tls/" + name).getBytes(StandardCharsets.UTF_8));
        }
    }

    @Nested
    @DisplayName("a tcp server")
    class ATcpServer {

        @Test
        @DisplayName("listening and served by a client in the same script answers what a real 3.22.5 answers, event for event")
        void answersAsRDoes(@TempDir Path root) throws IOException {
            String script = readResource("/tls/listening/echo.r3");
            String whatRPrinted = readResource("/tls/listening/echo.txt");

            run(makeAnInterpreterTrustingTheTestRoot(root), script);

            assertThat(printed.toString().strip()).isEqualTo(whatRPrinted.strip());
        }

        @Test
        @DisplayName("listening on port 0 is given a free one, which query reads back")
        void isGivenAFreePort(@TempDir Path root) {
            assertThat(run(makeAnInterpreterTrustingTheTestRoot(root), """
                    server: open tcp://:0
                    also (query server 'local-port) > 0 close server"""))
                    .isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("a tls server listening with certificates and a key given as R3 gives them")
    class ATlsServer {

        private String openTheServer(Interpreter interpreter) {
            return run(interpreter, """
                    server: open [
                        scheme: 'tls
                        port: 0
                        config: [
                            certificates: [%/localhost-rsa.pem %/test-intermediate.pem]
                            private-key: %/localhost-rsa.key
                        ]
                    ]
                    query server/state/tcp-port 'local-port""");
        }

        @Test
        @DisplayName("answers a client of the JVM's own with R3's demo answer, over the chain it was given")
        void answersAJvmClient(@TempDir Path root) throws Exception {
            copyTheServersFixtures(root);
            Interpreter interpreter = makeAnInterpreterTrustingTheTestRoot(root);
            int port = Integer.parseInt(openTheServer(interpreter));

            CompletableFuture<String> answered = CompletableFuture.supplyAsync(() -> askOverTls(port));
            while (!answered.isDone()) {
                run(interpreter, "wait [server 0.2]");
            }

            assertThat(answered.get(10, TimeUnit.SECONDS)).contains(RS_DEMO_ANSWER);
        }

        @Test
        @DisplayName("answers JEBOL's own tls client in the same script, where a real 3.22.5 answers neither its own nor OpenSSL's")
        void answersItsOwnClient(@TempDir Path root) throws IOException {
            copyTheServersFixtures(root);
            String script = readResource("/tls/listening/secure-hello.r3")
                    .replace("fixtures: %../../org/jebol/tls/", "fixtures: %/");

            run(makeAnInterpreterTrustingTheTestRoot(root), script);

            assertThat(printed.toString())
                    .contains("[listening #(true) #(true) client connect client wrote client read ")
                    .contains(RS_DEMO_ANSWER);
        }

        private String readUntilTheDemoAnswer(InputStream in) throws IOException {
            StringBuilder answered = new StringBuilder();
            byte[] room = new byte[4096];
            int arrived;
            while (!answered.toString().contains(RS_DEMO_ANSWER) && (arrived = in.read(room)) > 0) {
                answered.append(new String(room, 0, arrived, StandardCharsets.UTF_8));
            }
            return answered.toString();
        }

        private String askOverTls(int port) {
            try {
                KeyStore roots = KeyStore.getInstance("PKCS12");
                roots.load(null, null);
                roots.setCertificateEntry("test-root", fixtures.readTheCertificate("test-root"));
                TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trust.init(roots);
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, trust.getTrustManagers(), null);
                try (SSLSocket client = (SSLSocket) context.getSocketFactory()
                        .createSocket(InetAddress.getLoopbackAddress(), port)) {
                    client.setSoTimeout(20_000);
                    OutputStream out = client.getOutputStream();
                    out.write("GET / HTTP/1.0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                    return readUntilTheDemoAnswer(client.getInputStream());
                }
            } catch (IOException | java.security.GeneralSecurityException failed) {
                return "failed: " + failed;
            }
        }
    }
}
