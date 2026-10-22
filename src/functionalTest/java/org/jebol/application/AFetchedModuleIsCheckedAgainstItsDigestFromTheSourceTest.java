package org.jebol.application;

import org.jebol.adapter.host.FileSystemPort;
import org.jebol.adapter.host.JavaCertificateAuthority;
import org.jebol.adapter.host.JavaSocket;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.jebol.tls.LocalTlsServer;
import org.jebol.tls.LocalTlsServer.Identity;
import org.jebol.tls.TestCertificates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
class AFetchedModuleIsCheckedAgainstItsDigestFromTheSourceTest {

    private static final String TLS_1_3 = "TLSv1.3";
    private static final Duration HANDSHAKE_TIMEOUT = Duration.ofSeconds(60);
    private static final Instant FIXTURE_TIME = Instant.parse("2030-06-01T12:00:00Z");
    private static final String UNREACHABLE_ADDRESS = "https://localhost:1/greeting.reb";

    private static final String GREETING = """
            Rebol [type: module name: greeting]
            export greeting-value: 42
            """;

    private static final String TAMPERED_GREETING = """
            Rebol [type: module name: greeting]
            set 'tampered-ran true
            export greeting-value: 666
            """;

    private static final String NOT_GZIP = "these bytes are not a gzip stream";

    private static final String NO_DIGEST_REASON = "[no SHA-256 digest is recorded for it]";

    private final TestCertificates fixtures = new TestCertificates();

    private Identity localhostIdentity() {
        return new Identity(fixtures.chain("localhost-rsa", "test-intermediate"),
                fixtures.readTheKey("localhost-rsa-key", "RSA"));
    }

    private LocalTlsServer serve(String greetingBody) {
        return new LocalTlsServer(localhostIdentity(), TLS_1_3, path ->
                path.startsWith("/ext/") ? NOT_GZIP : greetingBody);
    }

    private Bounds networkedBounds() {
        return filesOnlyBounds()
                .grant(HostService.NETWORK)
                .grant(HostService.WEB)
                .grant(HostService.CLOCK)
                .grant(HostService.CERTIFICATES);
    }

    private Bounds filesOnlyBounds() {
        return Bounds.standard()
                .withTimeLimit(HANDSHAKE_TIMEOUT)
                .grant(HostService.FILES)
                .grant(HostService.WORKING_DIRECTORY);
    }

    private Interpreter interpreterIn(Path root, Bounds bounds) {
        Interpreter interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts()
                        .withFiles(FileSystemPort.rootFileSystem(root))
                        .withNetwork(new JavaSocket())
                        .withCertificateAuthority(new JavaCertificateAuthority(
                                fixtures.trustOnly("test-root"),
                                Clock.fixed(FIXTURE_TIME, ZoneOffset.UTC))),
                OutputPort.discard(),
                bounds);
        ran(interpreter, """
                system/options/data: %/data/
                make-dir/deep system/options/data""");
        interpreter.followApplicationDataDirectory();
        ran(interpreter, "make-dir/deep system/options/modules");
        return interpreter;
    }

    private Interpreter networkedInterpreterIn(Path root) {
        return interpreterIn(root, networkedBounds());
    }

    private void ran(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
        ScriptOutcome outcome = interpreter.run(source);
        if (!outcome.succeeded()) {
            throw new IllegalStateException(source + " raised: " + interpreter.display(outcome));
        }
    }

    private String answerTo(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private String greetingAddress(LocalTlsServer server) {
        return "https://localhost:" + server.port() + "/greeting.reb";
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

    private void listGreetingAt(Interpreter interpreter, String address) {
        ran(interpreter, "extend system/modules 'greeting " + address);
    }

    private void recordDigest(Interpreter interpreter, String address, String digestSource) {
        ran(interpreter, "put system/options/module-digests " + address + " " + digestSource);
    }

    private String importFailure(Interpreter interpreter, String module) {
        return answerTo(interpreter, """
                failure: try [import '%s]
                reason: either string? failure/arg2 [load failure/arg2] [failure/arg2]
                either error? failure [reduce [failure/type failure/id failure/arg1 reason]] [{imported}]"""
                .formatted(module));
    }

    private String greetingIsKept(Interpreter interpreter) {
        return answerTo(interpreter, "exists? system/options/modules/greeting.reb");
    }

    @Nested
    @DisplayName("the table of digests")
    class TheTable {

        @Test
        @DisplayName("starts as an empty map")
        void startsEmpty(@TempDir Path root) {
            assertThat(answerTo(networkedInterpreterIn(root), """
                    table: system/options/module-digests
                    reduce [map? table empty? table]"""))
                    .isEqualTo("[#(true) #(true)]");
        }

        @Test
        @DisplayName("is each interpreter's own, so a digest one records is not in another's")
        void isEachInterpretersOwn(@TempDir Path first, @TempDir Path second) {
            Interpreter recording = networkedInterpreterIn(first);
            recordDigest(recording, UNREACHABLE_ADDRESS, rebolSha256(GREETING));

            assertThat(answerTo(recording, "length? system/options/module-digests")).isEqualTo("1");
            assertThat(answerTo(networkedInterpreterIn(second), "length? system/options/module-digests"))
                    .isEqualTo("0");
        }

        @ParameterizedTest(name = "replaced by {0}")
        @ValueSource(strings = {"none", "[]", "42", "{a string}"})
        @DisplayName("that is not a map has recorded nothing, and an address outside the build is refused")
        void thatIsNotAMapFailsClosed(String replacement, @TempDir Path root) throws Exception {
            try (LocalTlsServer server = serve(GREETING)) {
                Interpreter interpreter = networkedInterpreterIn(root);
                listGreetingAt(interpreter, greetingAddress(server));
                ran(interpreter, "system/options/module-digests: " + replacement);

                assertThat(importFailure(interpreter, "greeting"))
                        .isEqualTo("[access cannot-open " + greetingAddress(server) + " " + NO_DIGEST_REASON + "]");
                assertThat(server.connectionsAccepted()).isZero();
            }
        }

        @Test
        @DisplayName("that is a block holding the right digest is still not a map, and is refused")
        void aBlockHoldingTheRightDigestIsRefused(@TempDir Path root) throws Exception {
            try (LocalTlsServer server = serve(GREETING)) {
                Interpreter interpreter = networkedInterpreterIn(root);
                listGreetingAt(interpreter, greetingAddress(server));
                ran(interpreter, "system/options/module-digests: reduce ["
                        + greetingAddress(server) + " " + rebolSha256(GREETING) + "]");

                assertThat(importFailure(interpreter, "greeting"))
                        .isEqualTo("[access cannot-open " + greetingAddress(server) + " " + NO_DIGEST_REASON + "]");
                assertThat(server.connectionsAccepted()).isZero();
            }
        }
    }

    @Nested
    @DisplayName("an address outside the build with no digest")
    class NoDigest {

        @Test
        @DisplayName("is refused as cannot-open, naming the address and why")
        void isRefused(@TempDir Path root) throws Exception {
            try (LocalTlsServer server = serve(GREETING)) {
                Interpreter interpreter = networkedInterpreterIn(root);
                listGreetingAt(interpreter, greetingAddress(server));

                assertThat(importFailure(interpreter, "greeting"))
                        .isEqualTo("[access cannot-open " + greetingAddress(server) + " " + NO_DIGEST_REASON + "]");
            }
        }

        @Test
        @DisplayName("asks nothing of the host it names, and keeps nothing")
        void asksNothing(@TempDir Path root) throws Exception {
            try (LocalTlsServer server = serve(GREETING)) {
                Interpreter interpreter = networkedInterpreterIn(root);
                listGreetingAt(interpreter, greetingAddress(server));
                importFailure(interpreter, "greeting");

                assertThat(server.connectionsAccepted()).isZero();
                assertThat(greetingIsKept(interpreter)).isEqualTo("_");
            }
        }

        @Test
        @DisplayName("is refused when the digest was recorded for a different address")
        void aDigestForAnotherAddressDoesNotCount(@TempDir Path root) throws Exception {
            try (LocalTlsServer server = serve(GREETING)) {
                Interpreter interpreter = networkedInterpreterIn(root);
                listGreetingAt(interpreter, greetingAddress(server));
                recordDigest(interpreter, "https://localhost:" + server.port() + "/other.reb", rebolSha256(GREETING));

                assertThat(importFailure(interpreter, "greeting"))
                        .isEqualTo("[access cannot-open " + greetingAddress(server) + " " + NO_DIGEST_REASON + "]");
                assertThat(server.connectionsAccepted()).isZero();
            }
        }

        @Test
        @DisplayName("is refused when the module is asked for by a script's needs header")
        void isRefusedThroughNeeds(@TempDir Path root) throws Exception {
            try (LocalTlsServer server = serve(GREETING)) {
                Interpreter interpreter = networkedInterpreterIn(root);
                listGreetingAt(interpreter, greetingAddress(server));

                ran(interpreter, """
                        write %/data/needs-greeting.reb {Rebol [needs: [greeting]] greeting-value}""");

                assertThat(answerTo(interpreter, """
                        failure: try [do %/data/needs-greeting.reb]
                        reduce [failure/type failure/id failure/arg1]"""))
                        .isEqualTo("[access cannot-open " + greetingAddress(server) + "]");
                assertThat(server.connectionsAccepted()).isZero();
            }
        }
    }

    @Nested
    @DisplayName("a recorded digest that is not thirty-two bytes is no digest at all")
    class DigestShape {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "#{}",
                "copy/part DIGEST 31",
                "join DIGEST #{00}",
                "copy/part DIGEST 20",
                "enbase DIGEST 16",
                "form DIGEST",
                "none",
                "reduce [DIGEST]",
                "42",
                "true",
                "0.5"})
        @DisplayName("and the address is refused before it is read")
        void isRefused(String recorded, @TempDir Path root) throws Exception {
            try (LocalTlsServer server = serve(GREETING)) {
                Interpreter interpreter = networkedInterpreterIn(root);
                listGreetingAt(interpreter, greetingAddress(server));
                recordDigest(interpreter, greetingAddress(server),
                        "(" + recorded.replace("DIGEST", rebolSha256(GREETING)) + ")");

                assertThat(importFailure(interpreter, "greeting"))
                        .isEqualTo("[access cannot-open " + greetingAddress(server) + " " + NO_DIGEST_REASON + "]");
                assertThat(server.connectionsAccepted()).isZero();
                assertThat(greetingIsKept(interpreter)).isEqualTo("_");
            }
        }

        @Test
        @DisplayName("thirty-two bytes that are not the digest is a digest, and fails as a mismatch")
        void thirtyTwoWrongBytesAreADigest(@TempDir Path root) throws Exception {
            try (LocalTlsServer server = serve(GREETING)) {
                Interpreter interpreter = networkedInterpreterIn(root);
                listGreetingAt(interpreter, greetingAddress(server));
                recordDigest(interpreter, greetingAddress(server), "(head change copy "
                        + rebolSha256(GREETING) + " #{00})");

                assertThat(importFailure(interpreter, "greeting"))
                        .isEqualTo("[access invalid-check " + greetingAddress(server) + " _]");
            }
        }
    }

    @Nested
    @DisplayName("bytes whose SHA-256 is the recorded digest")
    class Matching {

        @Test
        @DisplayName("are imported, and the module's exports are there")
        void areImported(@TempDir Path root) throws Exception {
            try (LocalTlsServer server = serve(GREETING)) {
                Interpreter interpreter = networkedInterpreterIn(root);
                listGreetingAt(interpreter, greetingAddress(server));
                recordDigest(interpreter, greetingAddress(server), rebolSha256(GREETING));

                assertThat(answerTo(interpreter, """
                        m: import 'greeting
                        reduce [module? m greeting-value]"""))
                        .isEqualTo("[#(true) 42]");
            }
        }

        @Test
        @DisplayName("are kept in the modules directory exactly as they arrived")
        void areKept(@TempDir Path root) throws Exception {
            try (LocalTlsServer server = serve(GREETING)) {
                Interpreter interpreter = networkedInterpreterIn(root);
                listGreetingAt(interpreter, greetingAddress(server));
                recordDigest(interpreter, greetingAddress(server), rebolSha256(GREETING));
                ran(interpreter, "import 'greeting");

                assertThat(answerTo(interpreter,
                        "checksum read system/options/modules/greeting.reb 'sha256"))
                        .isEqualTo(rebolSha256(GREETING));
                assertThat(server.requestsReceived()).isEqualTo(1);
            }
        }
    }

    @Nested
    @DisplayName("bytes whose SHA-256 is not the recorded digest")
    class Mismatching {

        @Test
        @DisplayName("fail the import with Rebol's error for a tampered file, naming the address")
        void failTheImport(@TempDir Path root) throws Exception {
            try (LocalTlsServer server = serve(TAMPERED_GREETING)) {
                Interpreter interpreter = networkedInterpreterIn(root);
                listGreetingAt(interpreter, greetingAddress(server));
                recordDigest(interpreter, greetingAddress(server), rebolSha256(GREETING));

                assertThat(importFailure(interpreter, "greeting"))
                        .isEqualTo("[access invalid-check " + greetingAddress(server) + " _]");
            }
        }

        @Test
        @DisplayName("are neither kept nor run")
        void areNeitherKeptNorRun(@TempDir Path root) throws Exception {
            try (LocalTlsServer server = serve(TAMPERED_GREETING)) {
                Interpreter interpreter = networkedInterpreterIn(root);
                listGreetingAt(interpreter, greetingAddress(server));
                recordDigest(interpreter, greetingAddress(server), rebolSha256(GREETING));
                importFailure(interpreter, "greeting");

                assertThat(greetingIsKept(interpreter)).isEqualTo("_");
                assertThat(answerTo(interpreter, "reduce [value? 'tampered-ran value? 'greeting-value]"))
                        .isEqualTo("[#(false) #(false)]");
                assertThat(answerTo(interpreter, "url? select system/modules 'greeting"))
                        .isEqualTo("#(true)");
            }
        }

        @Test
        @DisplayName("are refused again on the next import, since nothing was kept to load instead")
        void areRefusedAgain(@TempDir Path root) throws Exception {
            try (LocalTlsServer server = serve(TAMPERED_GREETING)) {
                Interpreter interpreter = networkedInterpreterIn(root);
                listGreetingAt(interpreter, greetingAddress(server));
                recordDigest(interpreter, greetingAddress(server), rebolSha256(GREETING));
                importFailure(interpreter, "greeting");

                assertThat(importFailure(interpreter, "greeting"))
                        .isEqualTo("[access invalid-check " + greetingAddress(server) + " _]");
                assertThat(server.requestsReceived()).isEqualTo(2);
            }
        }
    }

    @Nested
    @DisplayName("a compiled extension, which is served gzipped")
    class Gzipped {

        private String extensionAddress(LocalTlsServer server) {
            return "https://localhost:" + server.port() + "/ext/";
        }

        private String readAddress(Interpreter interpreter, LocalTlsServer server) {
            return answerTo(interpreter, """
                    as url! ajoin [%s 'fake-ext #"-" system/platform #"-" system/build/arch %%.rebx %%.gz]"""
                    .formatted(extensionAddress(server)));
        }

        private Interpreter listedIn(Path root, LocalTlsServer server) {
            Interpreter interpreter = networkedInterpreterIn(root);
            ran(interpreter, "extend system/modules 'fake-ext " + extensionAddress(server));
            return interpreter;
        }

        @Test
        @DisplayName("is checked before it is decompressed, so a mismatch is invalid-check and not a decompression error")
        void isCheckedBeforeDecompressing(@TempDir Path root) throws Exception {
            try (LocalTlsServer server = serve(GREETING)) {
                Interpreter interpreter = listedIn(root, server);
                String readAddress = readAddress(interpreter, server);
                recordDigest(interpreter, readAddress, rebolSha256("some other bytes"));

                assertThat(importFailure(interpreter, "fake-ext"))
                        .isEqualTo("[access invalid-check " + readAddress + " _]");
            }
        }

        @Test
        @DisplayName("whose digest matches goes on to be decompressed, and bytes that are not gzip find no module")
        void matchingBytesAreDecompressed(@TempDir Path root) throws Exception {
            try (LocalTlsServer server = serve(GREETING)) {
                Interpreter interpreter = listedIn(root, server);
                String readAddress = readAddress(interpreter, server);
                recordDigest(interpreter, readAddress, rebolSha256(NOT_GZIP));

                assertThat(importFailure(interpreter, "fake-ext"))
                        .isEqualTo("[access cannot-open fake-ext [module not found]]");
                assertThat(server.requestsReceived()).isEqualTo(1);
            }
        }

        @Test
        @DisplayName("needs a digest for the file it reads, not for the directory the table names")
        void needsADigestForTheFileItReads(@TempDir Path root) throws Exception {
            try (LocalTlsServer server = serve(GREETING)) {
                Interpreter interpreter = listedIn(root, server);
                String readAddress = readAddress(interpreter, server);
                recordDigest(interpreter, extensionAddress(server), rebolSha256(NOT_GZIP));

                assertThat(importFailure(interpreter, "fake-ext"))
                        .isEqualTo("[access cannot-open " + readAddress + " " + NO_DIGEST_REASON + "]");
                assertThat(server.connectionsAccepted()).isZero();
            }
        }
    }

    @Nested
    @DisplayName("an address in the build")
    class Bundled {

        @Test
        @DisplayName("needs no digest")
        void needsNoDigest(@TempDir Path root) {
            assertThat(answerTo(networkedInterpreterIn(root), """
                    m: import 'soundex
                    reduce [module? m function? :soundex]"""))
                    .isEqualTo("[#(true) #(true)]");
        }

        @Test
        @DisplayName("needs no digest on a host that grants no network")
        void needsNoNetwork(@TempDir Path root) {
            assertThat(answerTo(interpreterIn(root, filesOnlyBounds()), "module? import 'soundex"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("with a digest recorded that is wrong fails as a mismatch, because a recorded digest is always checked")
        void aWrongRecordedDigestIsChecked(@TempDir Path root) {
            Interpreter interpreter = networkedInterpreterIn(root);
            recordDigest(interpreter, "bundled://soundex.reb", rebolSha256("not soundex"));

            assertThat(importFailure(interpreter, "soundex"))
                    .isEqualTo("[access invalid-check bundled://soundex.reb _]");
            assertThat(answerTo(interpreter, "exists? system/options/modules/soundex.reb"))
                    .isEqualTo("_");
        }

        @Test
        @DisplayName("with the right digest recorded is imported")
        void theRightRecordedDigestImports(@TempDir Path root) {
            Interpreter interpreter = networkedInterpreterIn(root);
            ran(interpreter, """
                    put system/options/module-digests bundled://soundex.reb checksum read bundled://soundex.reb 'sha256""");

            assertThat(answerTo(interpreter, "module? import 'soundex")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("a host that grants no network")
    class NoNetwork {

        @Test
        @DisplayName("refuses the fetch of an address with a digest as no-service, and keeps nothing")
        void refusesTheFetch(@TempDir Path root) {
            Interpreter interpreter = interpreterIn(root, filesOnlyBounds());
            listGreetingAt(interpreter, UNREACHABLE_ADDRESS);
            recordDigest(interpreter, UNREACHABLE_ADDRESS, rebolSha256(GREETING));

            assertThat(answerTo(interpreter, """
                    failure: try [import 'greeting]
                    reduce [failure/type failure/id]"""))
                    .isEqualTo("[Access no-service]");
            assertThat(greetingIsKept(interpreter)).isEqualTo("_");
        }

        @Test
        @DisplayName("refuses an address with no digest for having none, before the network is asked")
        void refusesForTheMissingDigestFirst(@TempDir Path root) {
            Interpreter interpreter = interpreterIn(root, filesOnlyBounds());
            listGreetingAt(interpreter, UNREACHABLE_ADDRESS);

            assertThat(importFailure(interpreter, "greeting"))
                    .isEqualTo("[access cannot-open " + UNREACHABLE_ADDRESS + " " + NO_DIGEST_REASON + "]");
        }
    }

    @Nested
    @DisplayName("a copy already kept in the modules directory")
    class KeptCopy {

        @Test
        @DisplayName("is loaded as it is, with no digest and no request")
        void isLoadedWithoutFetching(@TempDir Path root) throws Exception {
            try (LocalTlsServer server = serve(TAMPERED_GREETING)) {
                Interpreter interpreter = networkedInterpreterIn(root);
                listGreetingAt(interpreter, greetingAddress(server));
                ran(interpreter, """
                        write system/options/modules/greeting.reb {Rebol [type: module name: greeting] export greeting-value: 7}""");

                assertThat(answerTo(interpreter, "import 'greeting greeting-value")).isEqualTo("7");
                assertThat(server.connectionsAccepted()).isZero();
            }
        }
    }
}
