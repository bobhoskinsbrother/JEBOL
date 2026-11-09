package org.jebol.adapter.host;

import org.jebol.domain.host.CertificateAuthority;
import org.jebol.domain.host.CertificateMistrust;
import org.jebol.domain.host.ChainVerdict;
import org.jebol.domain.host.VouchedNames;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXCertPathBuilderResult;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class JavaCertificateAuthority implements CertificateAuthority {

    private static final int DNS_NAME = 2;
    private static final int IP_ADDRESS = 7;
    private static final int MOST_AUTHORITIES_BETWEEN_LEAF_AND_ROOT = 5;
    private static final int NOT_AN_AUTHORITY = -1;

    private final Set<TrustAnchor> trustAnchors;
    private final Clock clock;

    public JavaCertificateAuthority() {
        this.trustAnchors = JavaTrustAnchors.fromThisMachine();
        this.clock = Clock.systemUTC();
    }

    public static JavaCertificateAuthority trustingMachineAnd(Path supplied) {
        return new JavaCertificateAuthority(
                JavaTrustAnchors.fromThisMachineAnd(supplied), Clock.systemUTC());
    }

    public JavaCertificateAuthority(Set<TrustAnchor> trustAnchors, Clock clock) {
        if (trustAnchors.isEmpty()) {
            throw new IllegalArgumentException(
                    "an authority that trusts no root trusts nothing; say so with ZeroTrustAuthority");
        }
        this.trustAnchors = Set.copyOf(trustAnchors);
        this.clock = clock;
    }

    @Override
    public ChainVerdict judge(List<byte[]> chain) {
        if (chain.isEmpty()) {
            return new ChainVerdict.Mistrusted(CertificateMistrust.NO_CERTIFICATE);
        }
        Optional<X509Certificate> leaf = parse(chain.getFirst());
        if (leaf.isEmpty() || leaf.get().getBasicConstraints() != NOT_AN_AUTHORITY) {
            return new ChainVerdict.Mistrusted(CertificateMistrust.BROKEN_CHAIN);
        }
        Instant now = clock.instant();
        Optional<CertificateMistrust> outOfDate = findWhyOutOfDate(leaf.get(), now);
        if (outOfDate.isPresent()) {
            return new ChainVerdict.Mistrusted(outOfDate.get());
        }
        Set<X509Certificate> candidates = parseEveryReadable(chain.subList(1, chain.size()));
        if (isTrustAnchor(leaf.get()) || buildPathAt(now, leaf.get(), candidates).isPresent()) {
            return trustNamingWhatLeafVouchesFor(leaf.get());
        }
        if (isSignatureByNamedIssuerBroken(leaf.get(), candidates)) {
            return new ChainVerdict.Mistrusted(CertificateMistrust.BROKEN_CHAIN);
        }
        return new ChainVerdict.Mistrusted(findWhyNoPathIsTrustedNow(leaf.get(), candidates, now));
    }

    private Optional<X509Certificate> parse(byte[] encoded) {
        try {
            return Optional.of((X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(encoded)));
        } catch (CertificateException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    private Set<X509Certificate> parseEveryReadable(List<byte[]> rest) {
        Set<X509Certificate> readable = new LinkedHashSet<>();
        rest.forEach(encoded -> parse(encoded).ifPresent(readable::add));
        return readable;
    }

    private Optional<CertificateMistrust> findWhyOutOfDate(X509Certificate certificate, Instant now) {
        if (certificate.getNotAfter().toInstant().isBefore(now)) {
            return Optional.of(CertificateMistrust.EXPIRED);
        }
        if (certificate.getNotBefore().toInstant().isAfter(now)) {
            return Optional.of(CertificateMistrust.NOT_YET_VALID);
        }
        return Optional.empty();
    }

    private boolean isTrustAnchor(X509Certificate leaf) {
        return trustAnchors.stream().anyMatch(anchor -> leaf.equals(anchor.getTrustedCert()));
    }

    private Optional<List<X509Certificate>> buildPathAt(Instant moment, X509Certificate leaf,
                                                        Set<X509Certificate> candidates) {
        try {
            X509CertSelector target = new X509CertSelector();
            target.setCertificate(leaf);
            PKIXBuilderParameters parameters = new PKIXBuilderParameters(trustAnchors, target);
            parameters.setRevocationEnabled(false);
            parameters.setMaxPathLength(MOST_AUTHORITIES_BETWEEN_LEAF_AND_ROOT);
            parameters.setDate(Date.from(moment));
            List<X509Certificate> store = new ArrayList<>(candidates);
            store.add(leaf);
            parameters.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(store)));
            PKIXCertPathBuilderResult built = (PKIXCertPathBuilderResult) CertPathBuilder.getInstance("PKIX").build(parameters);
            return Optional.of(built.getCertPath().getCertificates().stream()
                    .map(X509Certificate.class::cast)
                    .toList());
        } catch (GeneralSecurityException noPath) {
            return Optional.empty();
        }
    }

    private boolean isSignatureByNamedIssuerBroken(X509Certificate leaf, Set<X509Certificate> candidates) {
        List<X509Certificate> namedIssuers = candidates.stream()
                .filter(candidate -> candidate.getSubjectX500Principal().equals(leaf.getIssuerX500Principal()))
                .toList();
        return !namedIssuers.isEmpty() && namedIssuers.stream().noneMatch(issuer -> isSignedBy(leaf, issuer));
    }

    private boolean isSignedBy(X509Certificate certificate, X509Certificate issuer) {
        try {
            certificate.verify(issuer.getPublicKey());
            return true;
        } catch (GeneralSecurityException | RuntimeException notTheSigner) {
            return false;
        }
    }

    private CertificateMistrust findWhyNoPathIsTrustedNow(X509Certificate leaf, Set<X509Certificate> candidates,
                                                          Instant now) {
        for (Instant moment : findMomentsInsideLeafsValidity(leaf, candidates)) {
            Optional<List<X509Certificate>> path = buildPathAt(moment, leaf, candidates);
            if (path.isPresent()) {
                return path.get().stream()
                        .map(certificate -> findWhyOutOfDate(certificate, now))
                        .flatMap(Optional::stream)
                        .findFirst()
                        .orElse(CertificateMistrust.UNKNOWN_AUTHORITY);
            }
        }
        return CertificateMistrust.UNKNOWN_AUTHORITY;
    }

    private List<Instant> findMomentsInsideLeafsValidity(X509Certificate leaf, Set<X509Certificate> candidates) {
        Instant leafFrom = leaf.getNotBefore().toInstant();
        Instant leafUntil = leaf.getNotAfter().toInstant();
        Set<Instant> moments = new LinkedHashSet<>();
        moments.add(findMiddle(leafFrom, leafUntil));
        for (X509Certificate candidate : candidates) {
            Instant from = later(leafFrom, candidate.getNotBefore().toInstant());
            Instant until = earlier(leafUntil, candidate.getNotAfter().toInstant());
            if (!from.isAfter(until)) {
                moments.add(findMiddle(from, until));
            }
        }
        return List.copyOf(moments);
    }

    private static Instant findMiddle(Instant from, Instant until) {
        return from.plusMillis((until.toEpochMilli() - from.toEpochMilli()) / 2);
    }

    private static Instant later(Instant one, Instant other) {
        return one.isAfter(other) ? one : other;
    }

    private static Instant earlier(Instant one, Instant other) {
        return one.isBefore(other) ? one : other;
    }

    private ChainVerdict trustNamingWhatLeafVouchesFor(X509Certificate leaf) {
        try {
            return new ChainVerdict.Trusted(readVouchedNames(leaf));
        } catch (CertificateParsingException unreadableNames) {
            return new ChainVerdict.Mistrusted(CertificateMistrust.BROKEN_CHAIN);
        }
    }

    private VouchedNames readVouchedNames(X509Certificate leaf) throws CertificateParsingException {
        Collection<List<?>> alternatives = leaf.getSubjectAlternativeNames();
        if (alternatives == null) {
            return new VouchedNames(Set.of(), Set.of());
        }
        return new VouchedNames(readAlternativeNames(alternatives, DNS_NAME), readAlternativeNames(alternatives, IP_ADDRESS));
    }

    private Set<String> readAlternativeNames(Collection<List<?>> alternatives, int kind) {
        return alternatives.stream()
                .filter(alternative -> Integer.valueOf(kind).equals(alternative.getFirst()))
                .map(alternative -> String.valueOf(alternative.get(1)))
                .collect(Collectors.toSet());
    }
}
