package org.jebol.adapter.host;

import org.jebol.domain.host.CertificateAuthority;
import org.jebol.domain.host.CertificateMistrust;
import org.jebol.domain.host.ChainVerdict;
import org.jebol.domain.host.VouchedNames;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;
import java.io.ByteArrayInputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.PKIXParameters;
import java.security.cert.PKIXReason;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class JavaCertificateAuthority implements CertificateAuthority {

    private static final int DNS_NAME = 2;
    private static final int IP_ADDRESS = 7;

    private final Set<TrustAnchor> trusted;
    private final Clock clock;

    public JavaCertificateAuthority() {
        this.trusted = jdkTrustAnchors();
        this.clock = Clock.systemUTC();
    }

    public JavaCertificateAuthority(Set<TrustAnchor> trusted, Clock clock) {
        if (trusted.isEmpty()) {
            throw new IllegalArgumentException(
                    "an authority that trusts no root trusts nothing; say so with TrustingNoAuthority");
        }
        this.trusted = Set.copyOf(trusted);
        this.clock = clock;
    }

    @Override
    public ChainVerdict judge(List<byte[]> chain) {
        if (chain.isEmpty()) {
            return new ChainVerdict.Mistrusted(CertificateMistrust.NO_CERTIFICATE);
        }
        try {
            List<X509Certificate> certificates = parse(chain);
            if (!linksInOrder(certificates)) {
                return new ChainVerdict.Mistrusted(CertificateMistrust.BROKEN_CHAIN);
            }
            return verdictOn(trimAtTrustedRoot(certificates));
        } catch (CertificateException unreadable) {
            return new ChainVerdict.Mistrusted(CertificateMistrust.BROKEN_CHAIN);
        }
    }

    private boolean linksInOrder(List<X509Certificate> certificates) {
        for (int at = 0; at + 1 < certificates.size(); at++) {
            X500Principal issuer = certificates.get(at).getIssuerX500Principal();
            if (!issuer.equals(certificates.get(at + 1).getSubjectX500Principal())) {
                return false;
            }
        }
        return true;
    }

    private ChainVerdict verdictOn(List<X509Certificate> certificates) {
        try {
            validate(certificates);
            return new ChainVerdict.Trusted(vouchedNames(certificates.getFirst()));
        } catch (CertPathValidatorException refused) {
            return new ChainVerdict.Mistrusted(mistrustFor(refused));
        } catch (GeneralSecurityException unusable) {
            return new ChainVerdict.Mistrusted(CertificateMistrust.BROKEN_CHAIN);
        }
    }

    private List<X509Certificate> parse(List<byte[]> chain) throws CertificateException {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        List<X509Certificate> certificates = new ArrayList<>();
        for (byte[] encoded : chain) {
            certificates.add((X509Certificate) factory.generateCertificate(new ByteArrayInputStream(encoded)));
        }
        return certificates;
    }

    private List<X509Certificate> trimAtTrustedRoot(List<X509Certificate> certificates) {
        for (int at = 1; at < certificates.size(); at++) {
            if (isTrustedRoot(certificates.get(at))) {
                return certificates.subList(0, at);
            }
        }
        return certificates;
    }

    private boolean isTrustedRoot(X509Certificate certificate) {
        return trusted.stream()
                .map(TrustAnchor::getTrustedCert)
                .anyMatch(root -> root.getSubjectX500Principal().equals(certificate.getSubjectX500Principal())
                        && root.getPublicKey().equals(certificate.getPublicKey()));
    }

    private void validate(List<X509Certificate> certificates) throws GeneralSecurityException {
        CertPath path = CertificateFactory.getInstance("X.509").generateCertPath(certificates);
        PKIXParameters parameters = new PKIXParameters(trusted);
        parameters.setRevocationEnabled(false);
        parameters.setDate(Date.from(clock.instant()));
        CertPathValidator.getInstance("PKIX").validate(path, parameters);
    }

    private CertificateMistrust mistrustFor(CertPathValidatorException refused) {
        if (refused.getReason() == CertPathValidatorException.BasicReason.EXPIRED) {
            return CertificateMistrust.EXPIRED;
        }
        if (refused.getReason() == CertPathValidatorException.BasicReason.NOT_YET_VALID) {
            return CertificateMistrust.NOT_YET_VALID;
        }
        if (refused.getReason() == PKIXReason.NO_TRUST_ANCHOR) {
            return CertificateMistrust.UNKNOWN_AUTHORITY;
        }
        return CertificateMistrust.BROKEN_CHAIN;
    }

    private VouchedNames vouchedNames(X509Certificate leaf) throws CertificateParsingException {
        Collection<List<?>> alternatives = leaf.getSubjectAlternativeNames();
        if (alternatives == null) {
            return new VouchedNames(Set.of(), Set.of());
        }
        return new VouchedNames(alternativeNames(alternatives, DNS_NAME), alternativeNames(alternatives, IP_ADDRESS));
    }

    private Set<String> alternativeNames(Collection<List<?>> alternatives, int kind) {
        return alternatives.stream()
                .filter(alternative -> Integer.valueOf(kind).equals(alternative.getFirst()))
                .map(alternative -> String.valueOf(alternative.get(1)))
                .collect(Collectors.toSet());
    }

    private Set<TrustAnchor> jdkTrustAnchors() {
        try {
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init((KeyStore) null);
            return Arrays.stream(factory.getTrustManagers())
                    .filter(X509TrustManager.class::isInstance)
                    .flatMap(manager -> Arrays.stream(((X509TrustManager) manager).getAcceptedIssuers()))
                    .map(root -> new TrustAnchor(root, null))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (GeneralSecurityException noStore) {
            throw new IllegalStateException("the JDK offers no store of trusted authorities", noStore);
        }
    }
}
