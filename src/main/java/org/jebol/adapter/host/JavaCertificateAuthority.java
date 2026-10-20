package org.jebol.adapter.host;

import org.jebol.domain.host.CertificateAuthority;
import org.jebol.domain.host.CertificateMistrust;
import org.jebol.domain.host.ChainVerdict;
import org.jebol.domain.host.VouchedNames;

import javax.security.auth.x500.X500Principal;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
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
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class JavaCertificateAuthority implements CertificateAuthority {

    private static final int DNS_NAME = 2;
    private static final int IP_ADDRESS = 7;

    private final Set<TrustAnchor> trustAnchors;
    private final Clock clock;

    public JavaCertificateAuthority() {
        this.trustAnchors = JavaTrustAnchors.fromThisMachine();
        this.clock = Clock.systemUTC();
    }

    public static JavaCertificateAuthority trustingTheMachineAnd(Path supplied) {
        return new JavaCertificateAuthority(
                JavaTrustAnchors.fromThisMachineAnd(supplied), Clock.systemUTC());
    }

    public JavaCertificateAuthority(Set<TrustAnchor> trustAnchors, Clock clock) {
        if (trustAnchors.isEmpty()) {
            throw new IllegalArgumentException(
                    "an authority that trusts no root trusts nothing; say so with TrustingNoAuthority");
        }
        this.trustAnchors = Set.copyOf(trustAnchors);
        this.clock = clock;
    }

    @Override
    public ChainVerdict judge(List<byte[]> chain) {
        if (chain.isEmpty()) {
            return new ChainVerdict.Mistrusted(CertificateMistrust.NO_CERTIFICATE);
        }
        try {
            List<X509Certificate> certificates = parse(chain);
            if (!isLinksInOrder(certificates)) {
                return new ChainVerdict.Mistrusted(CertificateMistrust.BROKEN_CHAIN);
            }
            return reachAVerdict(trimAtTrustedRoot(certificates));
        } catch (CertificateException unreadable) {
            return new ChainVerdict.Mistrusted(CertificateMistrust.BROKEN_CHAIN);
        }
    }

    private boolean isLinksInOrder(List<X509Certificate> certificates) {
        for (int at = 0; at + 1 < certificates.size(); at++) {
            X500Principal issuer = certificates.get(at).getIssuerX500Principal();
            if (!issuer.equals(certificates.get(at + 1).getSubjectX500Principal())) {
                return false;
            }
        }
        return true;
    }

    private ChainVerdict reachAVerdict(List<X509Certificate> certificates) {
        try {
            validate(certificates);
            return new ChainVerdict.Trusted(readTheVouchedNames(certificates.getFirst()));
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
        return trustAnchors.stream()
                .map(TrustAnchor::getTrustedCert)
                .anyMatch(root -> root.getSubjectX500Principal().equals(certificate.getSubjectX500Principal())
                        && root.getPublicKey().equals(certificate.getPublicKey()));
    }

    private void validate(List<X509Certificate> certificates) throws GeneralSecurityException {
        CertPath path = CertificateFactory.getInstance("X.509").generateCertPath(certificates);
        PKIXParameters parameters = new PKIXParameters(trustAnchors);
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

    private VouchedNames readTheVouchedNames(X509Certificate leaf) throws CertificateParsingException {
        Collection<List<?>> alternatives = leaf.getSubjectAlternativeNames();
        if (alternatives == null) {
            return new VouchedNames(Set.of(), Set.of());
        }
        return new VouchedNames(readTheAlternativeNames(alternatives, DNS_NAME), readTheAlternativeNames(alternatives, IP_ADDRESS));
    }

    private Set<String> readTheAlternativeNames(Collection<List<?>> alternatives, int kind) {
        return alternatives.stream()
                .filter(alternative -> Integer.valueOf(kind).equals(alternative.getFirst()))
                .map(alternative -> String.valueOf(alternative.get(1)))
                .collect(Collectors.toSet());
    }
}
