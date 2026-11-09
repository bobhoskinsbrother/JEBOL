package org.jebol.tls;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class LegacyTlsServer implements AutoCloseable {

    private static final String NOTHING_DISABLED = """
            jdk.tls.disabledAlgorithms=
            jdk.tls.legacyAlgorithms=
            jdk.certpath.disabledAlgorithms=
            """;

    private static final String READY = "listening on ";

    private final Process server;
    private final Path securitySettings;
    private final int port;

    public LegacyTlsServer(String protocol, String cipherSuite, int diffieHellmanBits) {
        try {
            securitySettings = Files.createTempFile("nothing-disabled", ".security");
            Files.writeString(securitySettings, NOTHING_DISABLED);
            List<String> command = new ArrayList<>(List.of(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-Djava.security.properties=" + securitySettings,
                    "-Djdk.tls.ephemeralDHKeySize=" + diffieHellmanBits,
                    "-cp", System.getProperty("java.class.path"),
                    LegacyTlsServer.class.getName(), protocol, cipherSuite));
            server = new ProcessBuilder(command).redirectErrorStream(true).start();
            port = readPortAnnounced();
        } catch (IOException cannotStart) {
            throw new UncheckedIOException(cannotStart);
        }
    }

    public int port() {
        return port;
    }

    private int readPortAnnounced() throws IOException {
        BufferedReader printed = new BufferedReader(new InputStreamReader(server.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder seen = new StringBuilder();
        String line;
        while ((line = printed.readLine()) != null) {
            if (line.startsWith(READY)) {
                return Integer.parseInt(line.substring(READY.length()).strip());
            }
            seen.append(line).append('\n');
        }
        throw new IllegalStateException("the legacy server never listened:\n" + seen);
    }

    @Override
    public void close() throws IOException {
        server.getOutputStream().close();
        try {
            if (!server.waitFor(10, TimeUnit.SECONDS)) {
                server.destroyForcibly();
            }
        } catch (InterruptedException interrupted) {
            server.destroyForcibly();
            Thread.currentThread().interrupt();
        }
        Files.deleteIfExists(securitySettings);
    }

    public static void main(String[] arguments) throws IOException {
        TestCertificates fixtures = new TestCertificates();
        LocalTlsServer.Identity identity = new LocalTlsServer.Identity(
                fixtures.chain("localhost-rsa", "test-intermediate"),
                fixtures.readTheKey("localhost-rsa-key", "RSA"));
        LocalTlsServer.Settings settings = LocalTlsServer.Settings.speaking(arguments[0]).offeringOnly(arguments[1]);
        try (LocalTlsServer server = new LocalTlsServer(identity, settings)) {
            System.out.println(READY + server.port());
            System.out.flush();
            while (System.in.read() != -1) {
                Thread.onSpinWait();
            }
        }
    }
}
