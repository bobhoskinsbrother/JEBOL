package org.jebol.tls;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;

public final class LocalSmtpServer implements AutoCloseable {

    private static final int READ_TIMEOUT_MILLISECONDS = 20_000;
    private static final char[] NO_PASSWORD = new char[0];

    private final ServerSocket listening;
    private final SSLContext upgrade;
    private final Thread acceptor;
    private final List<String> commandsInTheClear = new ArrayList<>();
    private final List<String> commandsInsideTls = new ArrayList<>();
    private final StringBuilder message = new StringBuilder();

    public LocalSmtpServer(LocalTlsServer.Identity identity) {
        try {
            upgrade = makeContextServing(identity);
            listening = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
        } catch (IOException | GeneralSecurityException cannotListen) {
            throw new IllegalStateException(cannotListen);
        }
        acceptor = Thread.ofVirtual().start(this::acceptOne);
    }

    public int port() {
        return listening.getLocalPort();
    }

    public void awaitConversationEnd() throws InterruptedException {
        acceptor.join(READ_TIMEOUT_MILLISECONDS);
    }

    public synchronized List<String> commandsInTheClear() {
        return List.copyOf(commandsInTheClear);
    }

    public synchronized List<String> commandsInsideTls() {
        return List.copyOf(commandsInsideTls);
    }

    public synchronized String message() {
        return message.toString();
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

    private static SSLContext makeContextServing(LocalTlsServer.Identity identity) throws IOException, GeneralSecurityException {
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setKeyEntry("server", identity.key(), NO_PASSWORD, identity.chain().toArray(Certificate[]::new));
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(store, NO_PASSWORD);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keys.getKeyManagers(), null, null);
        return context;
    }

    private void acceptOne() {
        try (Socket plain = listening.accept()) {
            plain.setSoTimeout(READ_TIMEOUT_MILLISECONDS);
            converseInTheClear(plain);
        } catch (SocketException closed) {
            return;
        } catch (IOException ignored) {
        }
    }

    private void converseInTheClear(Socket plain) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(plain.getInputStream(), StandardCharsets.US_ASCII));
        OutputStream out = plain.getOutputStream();
        say(out, "220 local ESMTP");
        String line;
        while ((line = in.readLine()) != null) {
            record(commandsInTheClear, line);
            if (line.startsWith("EHLO")) {
                say(out, "250-local\r\n250-STARTTLS\r\n250 OK");
            } else if (line.equals("STARTTLS")) {
                say(out, "220 go ahead");
                converseInsideTls(plain);
                return;
            } else {
                say(out, "530 must issue STARTTLS first");
            }
        }
    }

    private void converseInsideTls(Socket plain) throws IOException {
        try (SSLSocket secure = (SSLSocket) upgrade.getSocketFactory()
                .createSocket(plain, plain.getInetAddress().getHostAddress(), plain.getPort(), false)) {
            secure.setUseClientMode(false);
            secure.startHandshake();
            BufferedReader in = new BufferedReader(new InputStreamReader(secure.getInputStream(), StandardCharsets.US_ASCII));
            OutputStream out = secure.getOutputStream();
            String line;
            while ((line = in.readLine()) != null) {
                record(commandsInsideTls, line);
                if (line.startsWith("EHLO")) {
                    say(out, "250-local\r\n250-AUTH PLAIN\r\n250 OK");
                } else if (line.startsWith("AUTH PLAIN")) {
                    say(out, "235 accepted");
                } else if (line.startsWith("MAIL FROM") || line.startsWith("RCPT TO")) {
                    say(out, "250 OK");
                } else if (line.equals("DATA")) {
                    say(out, "354 go ahead");
                    readMessage(in);
                    say(out, "250 queued");
                } else if (line.equals("QUIT")) {
                    say(out, "221 bye");
                    return;
                }
            }
        }
    }

    private void readMessage(BufferedReader in) throws IOException {
        String line;
        while ((line = in.readLine()) != null && !line.equals(".")) {
            synchronized (this) {
                message.append(line).append('\n');
            }
        }
    }

    private synchronized void record(List<String> into, String command) {
        into.add(command);
    }

    private void say(OutputStream out, String reply) throws IOException {
        out.write((reply + "\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }
}
