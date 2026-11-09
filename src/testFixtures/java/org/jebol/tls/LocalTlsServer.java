package org.jebol.tls;

import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class LocalTlsServer implements AutoCloseable {

    public static final String PAGE_BODY = "served over a checked connection";

    private static final String ALIAS = "server";
    private static final int READ_TIMEOUT_MILLISECONDS = 20_000;

    private final SSLServerSocket listening;
    private final Thread acceptor;
    private final Function<String, String> bodyForPath;
    private int connectionsAccepted;
    private int connectionsClosed;
    private int requestsReceived;
    private final List<String> serverNamesSent = new ArrayList<>();
    private final List<String> hostsAskedFor = new ArrayList<>();
    private final List<String> handshakeFailures = new ArrayList<>();

    public record Identity(List<X509Certificate> chain, PrivateKey key) {
    }

    public enum ClientCertificate { NOT_ASKED, WANTED, NEEDED }

    public record Settings(List<String> protocols, List<String> cipherSuites, ClientCertificate clientCertificate) {

        public static Settings speaking(String protocol) {
            return new Settings(List.of(protocol), List.of(), ClientCertificate.NOT_ASKED);
        }

        public Settings offeringOnly(String... suites) {
            return new Settings(protocols, List.of(suites), clientCertificate);
        }

        public Settings asking(ClientCertificate asked) {
            return new Settings(protocols, cipherSuites, asked);
        }
    }

    public LocalTlsServer(Identity identity, String protocol) {
        this(identity, protocol, path -> PAGE_BODY);
    }

    public LocalTlsServer(Identity identity, String protocol, Function<String, String> bodyForPath) {
        this(identity, Settings.speaking(protocol), bodyForPath);
    }

    public LocalTlsServer(Identity identity, Settings settings) {
        this(identity, settings, path -> PAGE_BODY);
    }

    public LocalTlsServer(Identity identity, Settings settings, Function<String, String> bodyForPath) {
        this.bodyForPath = bodyForPath;
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(new KeyManager[] {new FixedKeyManager(identity)}, new TrustManager[] {new TrustingNoClient()}, null);
            listening = (SSLServerSocket) context.getServerSocketFactory()
                    .createServerSocket(0, 50, InetAddress.getLoopbackAddress());
            listening.setEnabledProtocols(settings.protocols().toArray(String[]::new));
            if (!settings.cipherSuites().isEmpty()) {
                listening.setEnabledCipherSuites(settings.cipherSuites().toArray(String[]::new));
            }
            listening.setWantClientAuth(settings.clientCertificate() == ClientCertificate.WANTED);
            if (settings.clientCertificate() == ClientCertificate.NEEDED) {
                listening.setNeedClientAuth(true);
            }
        } catch (IOException | GeneralSecurityException cannotListen) {
            throw new IllegalStateException(cannotListen);
        }
        acceptor = Thread.ofVirtual().start(this::acceptLoop);
    }

    public int port() {
        return listening.getLocalPort();
    }

    public synchronized int connectionsAccepted() {
        return connectionsAccepted;
    }

    public synchronized int requestsReceived() {
        return requestsReceived;
    }

    public synchronized List<String> serverNamesSent() {
        return List.copyOf(serverNamesSent);
    }

    public synchronized List<String> hostsAskedFor() {
        return List.copyOf(hostsAskedFor);
    }

    public synchronized List<String> handshakeFailures() {
        return List.copyOf(handshakeFailures);
    }

    public synchronized void awaitAllConnectionsClosed(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (connectionsClosed < connectionsAccepted) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw new IllegalStateException(
                        (connectionsAccepted - connectionsClosed) + " connections still open after " + timeout);
            }
            wait(Math.max(1, remaining / 1_000_000));
        }
    }

    @Override
    public void close() throws IOException {
        listening.close();
        try {
            acceptor.join();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void acceptLoop() {
        while (!listening.isClosed()) {
            try {
                Socket accepted = listening.accept();
                countAccepted();
                Thread.ofVirtual().start(() -> serve((SSLSocket) accepted));
            } catch (SocketException closed) {
                return;
            } catch (IOException acceptFailed) {
                throw new UncheckedIOException(acceptFailed);
            }
        }
    }

    private void serve(SSLSocket connection) {
        try (connection) {
            connection.setSoTimeout(READ_TIMEOUT_MILLISECONDS);
            try {
                connection.startHandshake();
            } catch (IOException failed) {
                recordHandshakeFailure(failed);
                return;
            }
            recordServerNames(connection);
            String path = readRequest(connection.getInputStream());
            if (path != null) {
                countRequest();
                writePage(connection.getOutputStream(), bodyForPath.apply(path));
            }
        } catch (IOException ignored) {
        } finally {
            countClosed();
        }
    }

    private synchronized void recordHandshakeFailure(IOException failed) {
        handshakeFailures.add(String.valueOf(failed.getMessage()));
    }

    private void recordServerNames(SSLSocket connection) {
        if (connection.getSession() instanceof ExtendedSSLSession session) {
            List<String> named = session.getRequestedServerNames().stream()
                    .filter(SNIHostName.class::isInstance)
                    .map(name -> ((SNIHostName) name).getAsciiName())
                    .toList();
            synchronized (this) {
                serverNamesSent.addAll(named);
            }
        }
    }

    private String readRequest(InputStream in) throws IOException {
        StringBuilder head = new StringBuilder();
        int next;
        while ((next = in.read()) != -1) {
            head.append((char) next);
            if (head.toString().endsWith("\r\n\r\n")) {
                break;
            }
        }
        if (head.isEmpty()) {
            return null;
        }
        recordHostAskedFor(head.toString());
        String requestLine = head.toString().lines().findFirst().orElse("");
        String[] parts = requestLine.split(" ");
        return parts.length > 1 ? parts[1] : "/";
    }

    private void recordHostAskedFor(String head) {
        head.lines()
                .filter(line -> line.regionMatches(true, 0, "Host:", 0, 5))
                .map(line -> line.substring(5).strip())
                .findFirst()
                .ifPresent(host -> {
                    synchronized (this) {
                        hostsAskedFor.add(host);
                    }
                });
    }

    private void writePage(OutputStream out, String page) throws IOException {
        byte[] body = page.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.0 200 OK\r\nContent-Type: text/plain\r\nContent-Length: "
                + body.length + "\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    private synchronized void countAccepted() {
        connectionsAccepted++;
    }

    private synchronized void countRequest() {
        requestsReceived++;
    }

    private synchronized void countClosed() {
        connectionsClosed++;
        notifyAll();
    }

    private static final class TrustingNoClient implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new CertificateException("no client certificate is trusted here");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new CertificateException("this is a server");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    private static final class FixedKeyManager extends X509ExtendedKeyManager {

        private final Identity identity;

        private FixedKeyManager(Identity identity) {
            this.identity = identity;
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            return chooseTheAlias(keyType);
        }

        @Override
        public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
            return chooseTheAlias(keyType);
        }

        private String chooseTheAlias(String keyType) {
            return keyType.equals(identity.key().getAlgorithm()) ? ALIAS : null;
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            String alias = chooseTheAlias(keyType);
            return alias == null ? new String[0] : new String[] {alias};
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return identity.chain().toArray(X509Certificate[]::new);
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return identity.key();
        }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return new String[0];
        }

        @Override
        public String chooseClientAlias(String[] keyTypes, Principal[] issuers, Socket socket) {
            return null;
        }
    }
}
