import java.io.FileInputStream;
import java.nio.file.Path;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertStore;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXCertPathBuilderResult;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class CertPathBuilderReasons {

    static Path fixtures;

    static X509Certificate read(String name) throws Exception {
        try (FileInputStream in = new FileInputStream(fixtures.resolve(name + ".pem").toFile())) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
    }

    static PKIXCertPathBuilderResult buildAt(Instant at, List<X509Certificate> certificates) throws Exception {
        X509CertSelector target = new X509CertSelector();
        target.setCertificate(certificates.getFirst());
        PKIXBuilderParameters parameters = new PKIXBuilderParameters(Set.of(new TrustAnchor(read("test-root"), null)), target);
        parameters.setRevocationEnabled(false);
        parameters.setDate(Date.from(at));
        parameters.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(certificates)));
        return (PKIXCertPathBuilderResult) CertPathBuilder.getInstance("PKIX").build(parameters);
    }

    static void build(String label, Instant at, String... bag) throws Exception {
        List<X509Certificate> certificates = new ArrayList<>();
        for (String name : bag) {
            certificates.add(read(name));
        }
        try {
            PKIXCertPathBuilderResult built = buildAt(at, certificates);
            System.out.println(label + ": built, length " + built.getCertPath().getCertificates().size()
                    + ", anchor " + built.getTrustAnchor().getTrustedCert().getSubjectX500Principal());
        } catch (CertPathBuilderException failed) {
            System.out.println(label + ": " + failed.getClass().getName() + ": " + failed.getMessage());
            for (Throwable cause = failed.getCause(); cause != null; cause = cause.getCause()) {
                System.out.println("    cause " + cause.getClass().getName() + ": " + cause.getMessage());
            }
            System.out.println("    again inside the leaf's validity: " + reasonFromRebuilding(at, certificates));
        }
    }

    static String reasonFromRebuilding(Instant now, List<X509Certificate> certificates) throws Exception {
        X509Certificate leaf = certificates.getFirst();
        Instant from = leaf.getNotBefore().toInstant();
        Instant until = leaf.getNotAfter().toInstant();
        Instant middle = from.plusMillis((until.toEpochMilli() - from.toEpochMilli()) / 2);
        try {
            PKIXCertPathBuilderResult built = buildAt(middle, certificates);
            List<X509Certificate> path = new ArrayList<>();
            built.getCertPath().getCertificates().forEach(each -> path.add((X509Certificate) each));
            path.add(built.getTrustAnchor().getTrustedCert());
            for (X509Certificate each : path) {
                if (each.getNotAfter().toInstant().isBefore(now)) {
                    return "expired (" + each.getSubjectX500Principal() + ")";
                }
                if (each.getNotBefore().toInstant().isAfter(now)) {
                    return "not_yet_valid (" + each.getSubjectX500Principal() + ")";
                }
            }
            return "built, and nothing in it is out of date: some other reason";
        } catch (CertPathBuilderException stillFailed) {
            return "unknown_authority";
        }
    }

    public static void main(String[] arguments) throws Exception {
        fixtures = Path.of(arguments[0]);
        Instant fixtureTime = Instant.parse("2030-06-01T12:00:00Z");
        System.out.println("java " + System.getProperty("java.version"));
        build("in order", fixtureTime, "localhost-rsa", "test-intermediate");
        build("reversed", fixtureTime, "localhost-rsa", "test-root", "test-intermediate");
        build("leaf twice", fixtureTime, "localhost-rsa", "localhost-rsa", "test-intermediate");
        build("unrelated extra", fixtureTime, "localhost-rsa", "stranger-root", "test-intermediate");
        build("cross-signed root after", fixtureTime, "localhost-rsa", "test-intermediate", "test-root-cross-signed");
        build("expired leaf", fixtureTime, "one-day", "test-intermediate");
        build("not yet valid leaf", Instant.parse("2026-06-01T00:00:00Z"), "one-day", "test-intermediate");
        build("intermediate missing", fixtureTime, "localhost-rsa");
        build("self-signed", fixtureTime, "self-signed");
        build("from a stranger", fixtureTime, "from-a-stranger", "stranger-root");
        build("authorities expired", Instant.parse("2050-01-01T00:00:00Z"), "localhost-rsa", "test-intermediate");
    }
}
