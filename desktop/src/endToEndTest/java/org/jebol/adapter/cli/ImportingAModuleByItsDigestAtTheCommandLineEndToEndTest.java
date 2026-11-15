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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
class ImportingAModuleByItsDigestAtTheCommandLineEndToEndTest {

    private static final String TLS_1_3 = "TLSv1.3";

    private static final String GREETING = """
            Rebol [type: module name: greeting]
            export greeting-value: 42
            """;

    private static final String TAMPERED_GREETING = """
            Rebol [type: module name: greeting]
            print {the tampered module ran}
            export greeting-value: 666
            """;

    private final TestCertificates fixtures = new TestCertificates();

    private record Ran(int exitStatus, String printed, String reported) {

        String everything() {
            return printed + reported;
        }
    }

    private Identity localhostIdentity() {
        return new Identity(fixtures.chain("localhost-rsa", "test-intermediate"),
                fixtures.readTheKey("localhost-rsa-key", "RSA"));
    }

    private void writeAuthority(Path directory) throws Exception {
        String body = Base64.getMimeEncoder(64, new byte[] {'\n'})
                .encodeToString(fixtures.readTheDer("test-root"));
        Files.writeString(directory.resolve("authorities.pem"),
                "-----BEGIN CERTIFICATE-----\n" + body + "\n-----END CERTIFICATE-----\n");
    }

    private String rebolSha256(String body) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(body.getBytes(StandardCharsets.UTF_8));
            return "#{" + HexFormat.of().withUpperCase().formatHex(digest) + "}";
        } catch (NoSuchAlgorithmException absent) {
            throw new IllegalStateException(absent);
        }
    }

    private String greetingAddress(LocalTlsServer server) {
        return "https://localhost:" + server.port() + "/greeting.reb";
    }

    private Path writeScriptRecordingDigest(Path directory, LocalTlsServer server, String digestOf) throws Exception {
        return writeScript(directory, """
                Rebol []
                extend system/modules 'greeting %1$s
                put system/options/module-digests %1$s %2$s
                import 'greeting
                print [{greeting is} greeting-value]
                """.formatted(greetingAddress(server), rebolSha256(digestOf)));
    }

    private Path writeScriptRecordingNoDigest(Path directory, LocalTlsServer server) throws Exception {
        return writeScript(directory, """
                Rebol []
                extend system/modules 'greeting %s
                import 'greeting
                print [{greeting is} greeting-value]
                """.formatted(greetingAddress(server)));
    }

    private Path writeScript(Path directory, String source) throws Exception {
        Path script = directory.resolve("uses-greeting.reb");
        Files.writeString(script, source);
        return script;
    }

    private Ran run(Path directory, Path script) {
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        ByteArrayOutputStream reported = new ByteArrayOutputStream();
        int status = Repl.runCommandLine(new String[] {
                        Repl.CAFILE_SWITCH, directory.resolve("authorities.pem").toString(),
                        Repl.DATA_SWITCH, directory.toString(),
                        script.toString()},
                new PrintStream(printed, true, StandardCharsets.UTF_8),
                new PrintStream(reported, true, StandardCharsets.UTF_8),
                directory.toString());
        return new Ran(status, printed.toString(StandardCharsets.UTF_8),
                reported.toString(StandardCharsets.UTF_8));
    }

    private boolean greetingIsKept(Path directory) {
        return Files.exists(directory.resolve("modules").resolve("greeting.reb"));
    }

    @Test
    @DisplayName("a module whose bytes match the digest the script recorded is fetched once, kept, and loaded from the kept copy on the next run")
    void aMatchingModuleIsFetchedOnceAndKept(@TempDir Path directory) throws Exception {
        writeAuthority(directory);
        try (LocalTlsServer server = new LocalTlsServer(localhostIdentity(), TLS_1_3, path -> GREETING)) {
            Path script = writeScriptRecordingDigest(directory, server, GREETING);

            Ran first = run(directory, script);
            Ran second = run(directory, script);

            assertThat(first.printed()).contains("greeting is 42");
            assertThat(first.exitStatus()).isZero();
            assertThat(greetingIsKept(directory)).isTrue();
            assertThat(second.printed()).contains("greeting is 42");
            assertThat(second.exitStatus()).isZero();
            assertThat(server.requestsReceived()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("a module whose bytes were changed on the server fails the script as tampered, and is neither run nor kept")
    void aTamperedModuleFailsTheScript(@TempDir Path directory) throws Exception {
        writeAuthority(directory);
        try (LocalTlsServer server = new LocalTlsServer(localhostIdentity(), TLS_1_3, path -> TAMPERED_GREETING)) {
            Ran ran = run(directory, writeScriptRecordingDigest(directory, server, GREETING));

            assertThat(ran.exitStatus()).isEqualTo(1);
            assertThat(ran.everything())
                    .contains("invalid checksum (tampered file)")
                    .contains(greetingAddress(server))
                    .doesNotContain("the tampered module ran")
                    .doesNotContain("greeting is");
            assertThat(greetingIsKept(directory)).isFalse();
        }
    }

    @Test
    @DisplayName("a module listed with no digest fails the script without asking the server for anything")
    void aModuleWithNoDigestIsNeverRequested(@TempDir Path directory) throws Exception {
        writeAuthority(directory);
        try (LocalTlsServer server = new LocalTlsServer(localhostIdentity(), TLS_1_3, path -> GREETING)) {
            Ran ran = run(directory, writeScriptRecordingNoDigest(directory, server));

            assertThat(ran.exitStatus()).isEqualTo(1);
            assertThat(ran.everything())
                    .contains("cannot open")
                    .contains(greetingAddress(server))
                    .contains("no SHA-256 digest is recorded for it")
                    .doesNotContain("greeting is");
            assertThat(server.connectionsAccepted()).isZero();
            assertThat(greetingIsKept(directory)).isFalse();
        }
    }
}
