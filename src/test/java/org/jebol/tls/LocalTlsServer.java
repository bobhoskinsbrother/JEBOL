package org.jebol.tls;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509ExtendedKeyManager;
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
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;

public final class LocalTlsServer implements AutoCloseable {

    public static final String PAGE_BODY = "served over a checked connection";

    private static final String ALIAS = "server";
    private static final int READ_TIMEOUT_MILLISECONDS = 20_000;

    private final SSLServerSocket listening;
    private final Thread acceptor;
    private int connectionsAccepted;
    private int connectionsClosed;
    private int requestsReceived;

    public record Identity(List<X509Certificate> chain, PrivateKey key) {
    }

    public LocalTlsServer(Identity identity, String protocol) {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(new KeyManager[] {new FixedKeyManager(identity)}, null, null);
            listening = (SSLServerSocket) context.getServerSocketFactory()
                    .createServerSocket(0, 50, InetAddress.getLoopbackAddress());
            listening.setEnabledProtocols(new String[] {protocol});
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
            connection.startHandshake();
            if (readRequest(connection.getInputStream())) {
                countRequest();
                writePage(connection.getOutputStream());
            }
        } catch (IOException ignored) {
        } finally {
            countClosed();
        }
    }

    private boolean readRequest(InputStream in) throws IOException {
        StringBuilder head = new StringBuilder();
        int next;
        while ((next = in.read()) != -1) {
            head.append((char) next);
            if (head.toString().endsWith("\r\n\r\n")) {
                return true;
            }
        }
        return !head.isEmpty();
    }

    private void writePage(OutputStream out) throws IOException {
        byte[] body = PAGE_BODY.getBytes(StandardCharsets.US_ASCII);
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

    private static final class FixedKeyManager extends X509ExtendedKeyManager {

        private final Identity identity;

        private FixedKeyManager(Identity identity) {
            this.identity = identity;
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            return alias(keyType);
        }

        @Override
        public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
            return alias(keyType);
        }

        private String alias(String keyType) {
            return keyType.equals(identity.key().getAlgorithm()) ? ALIAS : null;
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            String alias = alias(keyType);
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
