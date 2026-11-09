import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;
import java.io.FileInputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

public class JsseChainOrder {

    static Path fixtures;

    static X509Certificate read(String name) throws Exception {
        try (FileInputStream in = new FileInputStream(fixtures.resolve(name + ".pem").toFile())) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
    }

    static PrivateKey readKey(String name) throws Exception {
        String pem = Files.readString(fixtures.resolve(name + ".pem"));
        String body = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
    }

    static String names(X509Certificate[] chain) {
        List<String> each = new ArrayList<>();
        for (X509Certificate certificate : chain) {
            each.add(certificate.getSubjectX500Principal().getName() + " by " + certificate.getIssuerX500Principal().getName());
        }
        return String.join(" | ", each);
    }

    static void serve(String label, String protocol, X509Certificate[] given, PrivateKey key) throws Exception {
        SSLContext serverContext = SSLContext.getInstance("TLS");
        serverContext.init(new KeyManager[] {new Fixed(given, key)}, null, null);
        try (SSLServerSocket listening = (SSLServerSocket) serverContext.getServerSocketFactory()
                .createServerSocket(0, 5, InetAddress.getLoopbackAddress())) {
            listening.setEnabledProtocols(new String[] {protocol});
            Thread server = Thread.ofVirtual().start(() -> {
                try (SSLSocket accepted = (SSLSocket) listening.accept()) {
                    accepted.startHandshake();
                    accepted.getInputStream().read();
                } catch (Exception ignored) {
                }
            });
            X509Certificate[][] received = new X509Certificate[1][];
            SSLContext clientContext = SSLContext.getInstance("TLS");
            clientContext.init(null, new TrustManager[] {new Recording(received)}, null);
            String handshake = "completed";
            try (SSLSocket client = (SSLSocket) clientContext.getSocketFactory()
                    .createSocket(InetAddress.getLoopbackAddress(), listening.getLocalPort())) {
                client.setEnabledProtocols(new String[] {protocol});
                client.startHandshake();
            } catch (javax.net.ssl.SSLException failed) {
                handshake = "failed: " + failed.getMessage();
            }
            server.join();
            System.out.println(label + " over " + protocol);
            System.out.println("    handshake: " + handshake);
            System.out.println("    given:    " + names(given));
            if (received[0] == null) {
                System.out.println("    received: (the trust manager was never asked)");
                return;
            }
            System.out.println("    received: " + names(received[0]));
            System.out.println("    same order: " + java.util.Arrays.equals(given, received[0]));
        }
    }

    public static void main(String[] arguments) throws Exception {
        fixtures = Path.of(arguments[0]);
        System.out.println("java " + System.getProperty("java.version"));
        PrivateKey key = readKey("localhost-rsa-key");
        X509Certificate leaf = read("localhost-rsa");
        X509Certificate intermediate = read("test-intermediate");
        X509Certificate root = read("test-root");
        X509Certificate stranger = read("stranger-root");
        for (String protocol : List.of("TLSv1.2", "TLSv1.3")) {
            serve("in order", protocol, new X509Certificate[] {leaf, intermediate}, key);
            serve("reversed", protocol, new X509Certificate[] {leaf, root, intermediate}, key);
            serve("leaf twice", protocol, new X509Certificate[] {leaf, leaf, intermediate}, key);
            serve("unrelated extra", protocol, new X509Certificate[] {leaf, stranger, intermediate}, key);
            serve("leaf not first", protocol, new X509Certificate[] {intermediate, leaf}, key);
        }
    }

    record Recording(X509Certificate[][] received) implements X509TrustManager {

        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            received[0] = chain.clone();
        }

        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    static final class Fixed extends X509ExtendedKeyManager {

        private final X509Certificate[] chain;
        private final PrivateKey key;

        Fixed(X509Certificate[] chain, PrivateKey key) {
            this.chain = chain;
            this.key = key;
        }

        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            return keyType.equals(key.getAlgorithm()) ? "server" : null;
        }

        public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
            return chooseServerAlias(keyType, issuers, null);
        }

        public String[] getServerAliases(String keyType, Principal[] issuers) {
            return new String[] {"server"};
        }

        public X509Certificate[] getCertificateChain(String alias) {
            return chain;
        }

        public PrivateKey getPrivateKey(String alias) {
            return key;
        }

        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return new String[0];
        }

        public String chooseClientAlias(String[] keyTypes, Principal[] issuers, Socket socket) {
            return null;
        }
    }
}
