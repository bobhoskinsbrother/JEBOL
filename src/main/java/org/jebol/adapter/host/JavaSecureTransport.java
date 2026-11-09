package org.jebol.adapter.host;

import org.jebol.domain.host.CertificateMistrust;
import org.jebol.domain.host.NetworkPort;
import org.jebol.domain.host.RsaServerKey;
import org.jebol.domain.host.SecureSession;
import org.jebol.domain.host.SecureTransport;
import org.jebol.domain.host.ServerCheck;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JavaSecureTransport implements SecureTransport {

    private static final char[] NO_PASSWORD = new char[0];

    @Override
    public SecureSession startClient(String asciiHostName, int portNumber, ServerCheck check) {
        CheckingTrust trust = new CheckingTrust(check);
        SSLContext context = makeContext(new KeyManager[0], new TrustManager[] {trust});
        SSLEngine engine = context.createSSLEngine(asciiHostName, portNumber);
        engine.setUseClientMode(true);
        return new EngineSession(engine, trust);
    }

    @Override
    public SecureSession startServer(List<byte[]> certificateChain, RsaServerKey key) {
        try {
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(null, null);
            store.setKeyEntry("server", makePrivateKey(key), NO_PASSWORD,
                    readCertificates(certificateChain).toArray(Certificate[]::new));
            KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keys.init(store, NO_PASSWORD);
            SSLEngine engine = makeContext(keys.getKeyManagers(), null).createSSLEngine();
            engine.setUseClientMode(false);
            return new EngineSession(engine, null);
        } catch (GeneralSecurityException | java.io.IOException unusable) {
            throw new NetworkPort.Refused("no-connect",
                    "the certificates and key given cannot serve TLS: " + unusable.getMessage(), "a listening server");
        }
    }

    private static SSLContext makeContext(KeyManager[] keys, TrustManager[] trust) {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(keys, trust, null);
            return context;
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("this JVM offers no TLS", unavailable);
        }
    }

    private static PrivateKey makePrivateKey(RsaServerKey key) throws GeneralSecurityException {
        BigInteger n = new BigInteger(1, key.modulus());
        BigInteger e = new BigInteger(1, key.publicExponent());
        BigInteger d = new BigInteger(1, key.privateExponent());
        BigInteger p = new BigInteger(1, key.firstPrime());
        BigInteger q = new BigInteger(1, key.secondPrime());
        return KeyFactory.getInstance("RSA").generatePrivate(new RSAPrivateCrtKeySpec(n, e, d, p, q,
                d.mod(p.subtract(BigInteger.ONE)), d.mod(q.subtract(BigInteger.ONE)), q.modInverse(p)));
    }

    private static List<X509Certificate> readCertificates(List<byte[]> chain) throws CertificateException {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        List<X509Certificate> certificates = new ArrayList<>();
        for (byte[] encoded : chain) {
            certificates.add((X509Certificate) factory.generateCertificate(new ByteArrayInputStream(encoded)));
        }
        return certificates;
    }

    private static final class CheckingTrust extends X509ExtendedTrustManager {

        private final ServerCheck check;
        private CertificateMistrust mistrust;

        private CheckingTrust(ServerCheck check) {
            this.check = check;
        }

        Optional<CertificateMistrust> mistrust() {
            return Optional.ofNullable(mistrust);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
            judge(chain);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
            judge(chain);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            judge(chain);
        }

        private void judge(X509Certificate[] chain) throws CertificateException {
            List<byte[]> encoded = new ArrayList<>();
            for (X509Certificate certificate : chain) {
                encoded.add(encode(certificate));
            }
            Optional<CertificateMistrust> found = check.judge(encoded);
            if (found.isPresent()) {
                mistrust = found.get();
                throw new CertificateException(found.get().name());
            }
        }

        private byte[] encode(X509Certificate certificate) throws CertificateEncodingException {
            return certificate.getEncoded();
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
            throw new CertificateException("a client does not judge clients");
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
            throw new CertificateException("a client does not judge clients");
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new CertificateException("a client does not judge clients");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    private static final class EngineSession implements SecureSession {

        private final SSLEngine engine;
        private final CheckingTrust trust;
        private ByteBuffer fromNetwork = ByteBuffer.allocate(0);
        private final ByteArrayOutputStream waitingToBeSent = new ByteArrayOutputStream();
        private boolean handshakeDone;
        private boolean ended;
        private boolean toNetworkHasClosure;

        private EngineSession(SSLEngine engine, CheckingTrust trust) {
            this.engine = engine;
            this.trust = trust;
        }

        @Override
        public Exchange begin() {
            try {
                engine.beginHandshake();
            } catch (SSLException refused) {
                return endWith(refused, new ByteArrayOutputStream());
            }
            return pump();
        }

        @Override
        public Exchange receive(byte[] bytes) {
            ByteBuffer joined = ByteBuffer.allocate(fromNetwork.remaining() + bytes.length);
            joined.put(fromNetwork).put(bytes).flip();
            fromNetwork = joined;
            return pump();
        }

        @Override
        public Exchange send(byte[] plaintext) {
            waitingToBeSent.writeBytes(plaintext);
            return pump();
        }

        @Override
        public Exchange close() {
            engine.closeOutbound();
            return pump();
        }

        private Exchange pump() {
            ByteArrayOutputStream toNetwork = new ByteArrayOutputStream();
            ByteArrayOutputStream arrived = new ByteArrayOutputStream();
            if (ended) {
                return Exchange.of(new byte[0], new byte[0], State.CLOSED);
            }
            try {
                while (step(toNetwork, arrived)) {
                    Thread.onSpinWait();
                }
            } catch (SSLException refused) {
                return endWith(refused, toNetwork);
            }
            return Exchange.of(toNetwork.toByteArray(), arrived.toByteArray(), currentState());
        }

        private boolean step(ByteArrayOutputStream toNetwork, ByteArrayOutputStream arrived) throws SSLException {
            switch (engine.getHandshakeStatus()) {
                case NEED_TASK -> {
                    Runnable task;
                    while ((task = engine.getDelegatedTask()) != null) {
                        task.run();
                    }
                    return true;
                }
                case NEED_WRAP -> {
                    return wrap(ByteBuffer.allocate(0), toNetwork);
                }
                case NEED_UNWRAP, NEED_UNWRAP_AGAIN -> {
                    return unwrap(arrived);
                }
                default -> {
                    if (engine.isOutboundDone() && engine.isInboundDone()) {
                        return false;
                    }
                    if (waitingToBeSent.size() > 0 && !engine.isOutboundDone()) {
                        ByteBuffer plaintext = ByteBuffer.wrap(waitingToBeSent.toByteArray());
                        waitingToBeSent.reset();
                        wrap(plaintext, toNetwork);
                        if (plaintext.hasRemaining()) {
                            byte[] rest = new byte[plaintext.remaining()];
                            plaintext.get(rest);
                            waitingToBeSent.writeBytes(rest);
                        }
                        return true;
                    }
                    if (engine.isOutboundDone() && !toNetworkHasClosure) {
                        toNetworkHasClosure = true;
                        return wrap(ByteBuffer.allocate(0), toNetwork);
                    }
                    return fromNetwork.hasRemaining() && unwrap(arrived);
                }
            }
        }

        private boolean wrap(ByteBuffer plaintext, ByteArrayOutputStream toNetwork) throws SSLException {
            ByteBuffer room = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
            SSLEngineResult result = engine.wrap(plaintext, room);
            room.flip();
            toNetwork.write(room.array(), 0, room.limit());
            noteHandshake(result);
            return result.getStatus() == SSLEngineResult.Status.OK && (result.bytesProduced() > 0 || result.bytesConsumed() > 0
                    || engine.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NEED_WRAP);
        }

        private boolean unwrap(ByteArrayOutputStream arrived) throws SSLException {
            ByteBuffer room = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
            SSLEngineResult result = engine.unwrap(fromNetwork, room);
            room.flip();
            arrived.write(room.array(), 0, room.limit());
            noteHandshake(result);
            return switch (result.getStatus()) {
                case OK -> result.bytesConsumed() > 0 || result.bytesProduced() > 0;
                case CLOSED -> false;
                case BUFFER_UNDERFLOW, BUFFER_OVERFLOW -> false;
            };
        }

        private void noteHandshake(SSLEngineResult result) {
            if (result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED) {
                handshakeDone = true;
            }
        }

        private State currentState() {
            if (engine.isInboundDone() || engine.isOutboundDone()) {
                return State.CLOSED;
            }
            return handshakeDone ? State.OPEN : State.HANDSHAKING;
        }

        private Exchange endWith(SSLException refused, ByteArrayOutputStream toNetwork) {
            ended = true;
            flushAlert(toNetwork);
            Optional<CertificateMistrust> mistrust = trust == null ? Optional.empty() : trust.mistrust();
            Failure failure = mistrust.<Failure>map(Failure.Mistrusted::new)
                    .orElseGet(() -> new Failure.Refused(refused.getMessage() == null ? "the handshake failed" : refused.getMessage()));
            return Exchange.endedBy(failure, toNetwork.toByteArray());
        }

        private void flushAlert(ByteArrayOutputStream toNetwork) {
            try {
                engine.closeOutbound();
                ByteBuffer room = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
                engine.wrap(ByteBuffer.allocate(0), room);
                room.flip();
                toNetwork.write(room.array(), 0, room.limit());
            } catch (SSLException | RuntimeException nothingMoreToSay) {
                toNetwork.reset();
            }
        }
    }
}
