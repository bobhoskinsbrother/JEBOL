package org.jebol.adapter.host;

import org.jebol.tls.TestCertificates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.TrustAnchor;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavaTrustAnchorsTest {

    private final TestCertificates fixtures = new TestCertificates();

    private Set<String> subjectsOf(Set<TrustAnchor> anchors) {
        return anchors.stream()
                .map(TrustAnchor::getTrustedCert)
                .map(certificate -> certificate.getSubjectX500Principal().getName())
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("a store contributes the authorities and not the leaves")
    void keepsTheAuthoritiesAndDropsTheLeaves() throws Exception {
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null, null);
        store.setCertificateEntry("root", fixtures.readTheCertificate("test-root"));
        store.setCertificateEntry("leaf", fixtures.readTheCertificate("localhost-rsa"));

        assertThat(subjectsOf(JavaTrustAnchors.in(store))).containsExactly(
                fixtures.readTheCertificate("test-root")
                        .getSubjectX500Principal().getName());
    }

    @Test
    @DisplayName("a bundle contributes the authorities it holds and not the leaves")
    void readsABundleOfAuthorities(@TempDir Path directory) throws Exception {
        Path bundle = directory.resolve("authorities.pem");
        Files.write(bundle, concatenated(
                fixtures.readTheDer("test-root"),
                fixtures.readTheDer("localhost-rsa")));

        assertThat(subjectsOf(JavaTrustAnchors.in(bundle))).containsExactly(
                fixtures.readTheCertificate("test-root")
                        .getSubjectX500Principal().getName());
    }

    private static byte[] concatenated(byte[] first, byte[] second) {
        byte[] both = new byte[first.length + second.length];
        System.arraycopy(first, 0, both, 0, first.length);
        System.arraycopy(second, 0, both, first.length, second.length);
        return both;
    }

    @Test
    @DisplayName("a bundle that is not there contributes nothing")
    void missingBundleContributesNothing(@TempDir Path directory) {
        assertThat(JavaTrustAnchors.in(directory.resolve("none.pem"))).isEmpty();
    }

    @Test
    @DisplayName("the machine's authorities include the runtime's own")
    void includesTheJdksOwn() {
        assertThat(subjectsOf(JavaTrustAnchors.fromThisMachine()))
                .containsAll(subjectsOf(JavaTrustAnchors.jdksOwn()));
    }

    @Test
    @DisplayName("a supplied bundle is added to the machine's rather than replacing them")
    void addsToTheMachines(@TempDir Path directory) throws Exception {
        Path root = directory.resolve("root.pem");
        Files.write(root, fixtures.readTheDer("test-root"));

        assertThat(subjectsOf(JavaTrustAnchors.fromThisMachineAnd(root)))
                .containsAll(subjectsOf(JavaTrustAnchors.fromThisMachine()))
                .contains(fixtures.readTheCertificate("test-root")
                        .getSubjectX500Principal().getName());
    }

    @Test
    @DisplayName("a supplied bundle naming no authority is refused rather than trusted silently")
    void refusesABundleWithNoAuthority(@TempDir Path directory) throws Exception {
        Path leafOnly = directory.resolve("leaf.pem");
        Files.write(leafOnly, fixtures.readTheDer("localhost-rsa"));

        assertThatThrownBy(() -> JavaTrustAnchors.fromThisMachineAnd(leafOnly))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("holds no certificate authority");
    }

    @Test
    @DisplayName("a supplied file that is not there is refused, naming it")
    void refusesAMissingSuppliedFile(@TempDir Path directory) {
        assertThatThrownBy(() -> JavaTrustAnchors.fromThisMachineAnd(
                directory.resolve("none.pem")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("none.pem");
    }
}
