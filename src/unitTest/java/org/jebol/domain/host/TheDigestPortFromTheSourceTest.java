package org.jebol.domain.host;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TheDigestPortFromTheSourceTest {

    private static final DigestPort THE_PORT = new JvmDigests();

    private static byte[] readTheBytesOf(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the JVM's port has every digest Rebol's catalogue names")
    void findsTheCatalogueInTheJvm() {
        assertThat(THE_PORT.methods()).contains(
                "md5", "sha1", "sha224", "sha256", "sha384", "sha512",
                "sha3-224", "sha3-256", "sha3-384", "sha3-512");
    }

    @Test
    @DisplayName("a digest is the one the platform takes")
    void digestsAsThePlatformDoes() throws Exception {
        assertThat(THE_PORT.digestOf("sha1", readTheBytesOf("abc")))
                .isEqualTo(MessageDigest.getInstance("SHA-1").digest(readTheBytesOf("abc")));
        assertThat(THE_PORT.digestOf("sha256", readTheBytesOf("abc")))
                .isEqualTo(MessageDigest.getInstance("SHA-256").digest(readTheBytesOf("abc")));
    }

    @Test
    @DisplayName("a keyed digest is the platform's keyed digest")
    void digestsWithAKeyAsThePlatformDoes() throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(readTheBytesOf("key"), "HmacSHA1"));

        assertThat(THE_PORT.digestWithAKey("sha1", readTheBytesOf("abc"), readTheBytesOf("key")))
                .isEqualTo(mac.doFinal(readTheBytesOf("abc")));
    }

    @Test
    @DisplayName("a running digest answers what has arrived so far, and can be asked again")
    void keepsTheRunningDigestsPlace() {
        DigestPort.RunningDigest running = THE_PORT.start("sha1");
        running.add(readTheBytesOf("ab"), 0, 1);
        running.add(readTheBytesOf("ab"), 1, 1);

        byte[] afterAB = running.soFar();
        assertThat(afterAB).isEqualTo(THE_PORT.digestOf("sha1", readTheBytesOf("ab")));
        assertThat(running.soFar()).as("asking twice asks the same thing").isEqualTo(afterAB);

        running.add(readTheBytesOf("c"), 0, 1);
        assertThat(running.soFar()).isEqualTo(THE_PORT.digestOf("sha1", readTheBytesOf("abc")));
    }

    @Test
    @DisplayName("a method the port has not got is refused by name")
    void refusesAMethodItHasNotGot() {
        assertThatThrownBy(() -> THE_PORT.digestOf("bogus", readTheBytesOf("abc")))
                .isInstanceOf(DigestPort.Refused.class)
                .hasMessageContaining("bogus");
        assertThatThrownBy(() -> THE_PORT.start("bogus"))
                .isInstanceOf(DigestPort.Refused.class);
    }
}
