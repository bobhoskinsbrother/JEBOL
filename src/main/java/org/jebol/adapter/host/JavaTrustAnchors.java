package org.jebol.adapter.host;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class JavaTrustAnchors {

    private static final List<Path> SYSTEM_BUNDLES = List.of(
            Path.of("/etc/ssl/certs/ca-certificates.crt"),
            Path.of("/etc/pki/tls/certs/ca-bundle.crt"),
            Path.of("/etc/ssl/cert.pem"));

    private JavaTrustAnchors() {
    }

    static Set<TrustAnchor> fromThisMachine() {
        Set<TrustAnchor> anchors = new LinkedHashSet<>(theJdksOwn());
        anchors.addAll(thePlatformsOwn());
        return withoutRepeats(anchors);
    }

    static Set<TrustAnchor> fromThisMachineAnd(Path supplied) {
        if (supplied == null || !Files.isRegularFile(supplied)) {
            throw new IllegalArgumentException(
                    supplied + " is not a file of certificate authorities");
        }
        Set<TrustAnchor> suppliedAnchors = in(supplied);
        if (suppliedAnchors.isEmpty()) {
            throw new IllegalArgumentException(
                    supplied + " holds no certificate authority");
        }
        Set<TrustAnchor> anchors = new LinkedHashSet<>(fromThisMachine());
        anchors.addAll(suppliedAnchors);
        return withoutRepeats(anchors);
    }

    private static Set<TrustAnchor> withoutRepeats(Set<TrustAnchor> anchors) {
        java.util.Map<X509Certificate, TrustAnchor> unique = new java.util.LinkedHashMap<>();
        for (TrustAnchor anchor : anchors) {
            if (anchor.getTrustedCert() != null) {
                unique.putIfAbsent(anchor.getTrustedCert(), anchor);
            }
        }
        return Set.copyOf(unique.values());
    }

    static Set<TrustAnchor> theJdksOwn() {
        try {
            TrustManagerFactory factory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            factory.init((KeyStore) null);
            Set<TrustAnchor> anchors = new LinkedHashSet<>();
            for (javax.net.ssl.TrustManager manager : factory.getTrustManagers()) {
                if (manager instanceof X509TrustManager trusting) {
                    for (X509Certificate root : trusting.getAcceptedIssuers()) {
                        anchors.add(new TrustAnchor(root, null));
                    }
                }
            }
            return anchors;
        } catch (GeneralSecurityException noStore) {
            throw new IllegalStateException(
                    "the JDK offers no store of trusted authorities", noStore);
        }
    }

    private static Set<TrustAnchor> thePlatformsOwn() {
        Set<TrustAnchor> anchors = new LinkedHashSet<>();
        for (String type : platformStoreTypes()) {
            anchors.addAll(in(storeOf(type)));
        }
        for (Path bundle : SYSTEM_BUNDLES) {
            anchors.addAll(in(bundle));
        }
        return anchors;
    }

    private static List<String> platformStoreTypes() {
        String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (operatingSystem.contains("mac") || operatingSystem.contains("darwin")) {
            return List.of("KeychainStore");
        }
        if (operatingSystem.contains("win")) {
            return List.of("Windows-ROOT");
        }
        return List.of();
    }

    private static KeyStore storeOf(String type) {
        try {
            KeyStore store = KeyStore.getInstance(type);
            store.load(null, null);
            return store;
        } catch (GeneralSecurityException | IOException unavailable) {
            return null;
        }
    }

    static Set<TrustAnchor> in(KeyStore store) {
        if (store == null) {
            return Set.of();
        }
        Set<TrustAnchor> anchors = new LinkedHashSet<>();
        try {
            for (String alias : Collections.list(store.aliases())) {
                if (store.getCertificate(alias) instanceof X509Certificate certificate
                        && certificate.getBasicConstraints() >= 0) {
                    anchors.add(new TrustAnchor(certificate, null));
                }
            }
        } catch (GeneralSecurityException unreadable) {
            return Set.of();
        }
        return anchors;
    }

    static Set<TrustAnchor> in(Path bundle) {
        if (bundle == null || !Files.isRegularFile(bundle)) {
            return Set.of();
        }
        try (InputStream authorities = Files.newInputStream(bundle)) {
            Set<TrustAnchor> anchors = new LinkedHashSet<>();
            for (Certificate certificate : CertificateFactory.getInstance("X.509")
                    .generateCertificates(authorities)) {
                if (certificate instanceof X509Certificate authority
                        && authority.getBasicConstraints() >= 0) {
                    anchors.add(new TrustAnchor(authority, null));
                }
            }
            return anchors;
        } catch (IOException | CertificateException unreadable) {
            return Set.of();
        }
    }
}
