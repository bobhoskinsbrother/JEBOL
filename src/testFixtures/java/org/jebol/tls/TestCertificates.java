package org.jebol.tls;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class TestCertificates {

    private static final String FIXTURE_DIRECTORY = "/org/jebol/tls/";

    public X509Certificate certificate(String name) {
        try (InputStream pem = open(name)) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(pem);
        } catch (IOException | GeneralSecurityException unreadable) {
            throw new IllegalStateException("the fixture " + name + " is not a certificate", unreadable);
        }
    }

    public byte[] der(String name) {
        try {
            return certificate(name).getEncoded();
        } catch (GeneralSecurityException unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }

    public List<byte[]> ders(String... names) {
        return Arrays.stream(names).map(this::der).toList();
    }

    public List<X509Certificate> chain(String... names) {
        return Arrays.stream(names).map(this::certificate).toList();
    }

    public PrivateKey key(String name, String algorithm) {
        try (InputStream pem = open(name)) {
            byte[] encoded = Base64.getMimeDecoder().decode(base64Body(pem));
            return KeyFactory.getInstance(algorithm).generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (IOException | GeneralSecurityException unreadable) {
            throw new IllegalStateException("the fixture " + name + " is not a key", unreadable);
        }
    }

    public Set<TrustAnchor> trusting(String... roots) {
        return Arrays.stream(roots)
                .map(root -> new TrustAnchor(certificate(root), null))
                .collect(Collectors.toSet());
    }

    private String base64Body(InputStream pem) throws IOException {
        return new String(pem.readAllBytes(), StandardCharsets.US_ASCII)
                .lines()
                .filter(line -> !line.startsWith("-----"))
                .collect(Collectors.joining());
    }

    private InputStream open(String name) {
        InputStream found = TestCertificates.class.getResourceAsStream(FIXTURE_DIRECTORY + name + ".pem");
        if (found == null) {
            throw new UncheckedIOException(new IOException("no fixture named " + name));
        }
        return found;
    }
}
