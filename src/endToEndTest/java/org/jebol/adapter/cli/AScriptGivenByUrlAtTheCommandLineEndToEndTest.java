package org.jebol.adapter.cli;

import org.jebol.tls.LocalTlsServer;
import org.jebol.tls.LocalTlsServer.Identity;
import org.jebol.tls.TestCertificates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
class AScriptGivenByUrlAtTheCommandLineEndToEndTest {

    private static final String TLS_1_3 = "TLSv1.3";

    private final TestCertificates fixtures = new TestCertificates();

    private record Ran(int exitStatus, String printed, String reported) {
    }

    private Identity theLocalhostIdentity() {
        return new Identity(fixtures.chain("localhost-rsa", "test-intermediate"),
                fixtures.readTheKey("localhost-rsa-key", "RSA"));
    }

    private Identity theStrangerIdentity() {
        return new Identity(fixtures.chain("from-a-stranger", "stranger-root"),
                fixtures.readTheKey("localhost-rsa-key", "RSA"));
    }

    private Path writeTheAuthority(Path directory) throws Exception {
        Path pem = directory.resolve("authorities.pem");
        String body = Base64.getMimeEncoder(64, new byte[] {'\n'})
                .encodeToString(fixtures.readTheDer("test-root"));
        Files.writeString(pem, "-----BEGIN CERTIFICATE-----\n" + body
                + "\n-----END CERTIFICATE-----\n");
        return pem;
    }

    private Ran run(Path directory, String scriptArgument, String... arguments) {
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        ByteArrayOutputStream reported = new ByteArrayOutputStream();
        String[] given = new String[5 + arguments.length];
        given[0] = Repl.CAFILE_SWITCH;
        given[1] = directory.resolve("authorities.pem").toString();
        given[2] = Repl.DATA_SWITCH;
        given[3] = directory.toString();
        given[4] = scriptArgument;
        System.arraycopy(arguments, 0, given, 5, arguments.length);
        int status = Repl.runCommandLine(given,
                new PrintStream(printed, true, StandardCharsets.UTF_8),
                new PrintStream(reported, true, StandardCharsets.UTF_8),
                directory.toString());
        return new Ran(status, printed.toString(StandardCharsets.UTF_8),
                reported.toString(StandardCharsets.UTF_8));
    }

    private LocalTlsServer serveTheScripts(AtomicInteger port) {
        return new LocalTlsServer(theLocalhostIdentity(), TLS_1_3, path -> switch (path) {
            case "/main.reb" -> """
                    Rebol [title: "from a url"]
                    print system/options/script
                    probe system/options/args
                    print system/script/title
                    """;
            case "/plain.reb" -> "Rebol []\nprobe system/options/args\n";
            case "/with-needs.reb" -> """
                    Rebol [needs: https://localhost:%d/needed.reb]
                    print needed-value
                    """.formatted(port.get());
            case "/needed.reb" -> """
                    Rebol [type: module name: needed]
                    export needed-value: 42
                    """;
            case "/quitting.reb" -> "Rebol []\nquit/return 7\n";
            default -> "Rebol []\n";
        });
    }

    @Test
    @DisplayName("a script given by url runs, and sees its url, its args and its header")
    void runsAScriptFromAUrl(@TempDir Path directory) throws Exception {
        writeTheAuthority(directory);
        AtomicInteger port = new AtomicInteger();
        try (LocalTlsServer server = serveTheScripts(port)) {
            port.set(server.port());

            Ran ran = run(directory,
                    "https://localhost:" + port.get() + "/main.reb", "one", "two");

            assertThat(ran.printed())
                    .contains("https://localhost:" + port.get() + "/main.reb")
                    .contains("[\"one\" \"two\"]")
                    .contains("from a url");
            assertThat(ran.reported()).isEmpty();
            assertThat(ran.exitStatus()).isZero();
        }
    }

    @Test
    @DisplayName("a script runs the same whether it came from a file or a url")
    void runsTheSameFromAFileAndAUrl(@TempDir Path directory) throws Exception {
        writeTheAuthority(directory);
        Path local = directory.resolve("plain.reb");
        Files.writeString(local, "Rebol []\nprobe system/options/args\n");
        AtomicInteger port = new AtomicInteger();
        try (LocalTlsServer server = serveTheScripts(port)) {
            port.set(server.port());

            Ran fromFile = run(directory, local.toString(), "a", "b");
            Ran fromUrl = run(directory,
                    "https://localhost:" + port.get() + "/plain.reb", "a", "b");

            assertThat(fromFile.printed()).isEqualTo("[\"a\" \"b\"]\n");
            assertThat(fromUrl.printed()).isEqualTo(fromFile.printed());
            assertThat(fromUrl.exitStatus()).isEqualTo(fromFile.exitStatus()).isZero();
        }
    }

    @Test
    @DisplayName("the needs of a url script are imported from their urls")
    void importsTheNeedsOfAUrlScript(@TempDir Path directory) throws Exception {
        writeTheAuthority(directory);
        AtomicInteger port = new AtomicInteger();
        try (LocalTlsServer server = serveTheScripts(port)) {
            port.set(server.port());

            Ran ran = run(directory,
                    "https://localhost:" + port.get() + "/with-needs.reb");

            assertThat(ran.printed()).isEqualTo("42\n");
            assertThat(ran.reported()).isEmpty();
            assertThat(ran.exitStatus()).isZero();
        }
    }

    @Test
    @DisplayName("a url script leaves with what quit carried")
    void leavesWithWhatQuitCarried(@TempDir Path directory) throws Exception {
        writeTheAuthority(directory);
        AtomicInteger port = new AtomicInteger();
        try (LocalTlsServer server = serveTheScripts(port)) {
            port.set(server.port());

            Ran ran = run(directory,
                    "https://localhost:" + port.get() + "/quitting.reb");

            assertThat(ran.exitStatus()).isEqualTo(7);
        }
    }

    @Test
    @DisplayName("a url the host does not trust fails the run, and nothing is requested")
    void refusesAnUntrustedUrl(@TempDir Path directory) throws Exception {
        writeTheAuthority(directory);
        try (LocalTlsServer server = new LocalTlsServer(theStrangerIdentity(), TLS_1_3)) {
            Ran ran = run(directory, "https://localhost:" + server.port() + "/main.reb");

            assertThat(ran.exitStatus()).isEqualTo(1);
            assertThat(ran.reported()).isNotEmpty();
            assertThat(server.requestsReceived()).isZero();
        }
    }
}
