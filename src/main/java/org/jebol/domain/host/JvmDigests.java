package org.jebol.domain.host;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Set;

final class JvmDigests implements DigestPort {

    private static final Map<String, String> ALGORITHMS = Map.ofEntries(
            Map.entry("md5", "MD5"),
            Map.entry("sha1", "SHA-1"),
            Map.entry("sha224", "SHA-224"),
            Map.entry("sha256", "SHA-256"),
            Map.entry("sha384", "SHA-384"),
            Map.entry("sha512", "SHA-512"),
            Map.entry("sha3-224", "SHA3-224"),
            Map.entry("sha3-256", "SHA3-256"),
            Map.entry("sha3-384", "SHA3-384"),
            Map.entry("sha3-512", "SHA3-512"));

    @Override
    public Set<String> methods() {
        return ALGORITHMS.keySet();
    }

    @Override
    public RunningDigest start(String method) {
        MessageDigest running = findTheDigestNamed(method);
        return new RunningDigest() {

            @Override
            public void add(byte[] octets, int from, int length) {
                running.update(octets, from, length);
            }

            @Override
            public byte[] soFar() {
                try {
                    return ((MessageDigest) running.clone()).digest();
                } catch (CloneNotSupportedException cannotBeSplit) {
                    throw new Refused("feature-na", method + " cannot be split");
                }
            }
        };
    }

    @Override
    public byte[] digestOf(String method, byte[] octets) {
        return findTheDigestNamed(method).digest(octets);
    }

    @Override
    public byte[] digestWithAKey(String method, byte[] octets, byte[] key) {
        try {
            String algorithm = "Hmac" + findTheDigestNamed(method).getAlgorithm().replace("-", "");
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key.length == 0 ? new byte[1] : key, algorithm));
            return mac.doFinal(octets);
        } catch (NoSuchAlgorithmException | InvalidKeyException unavailable) {
            throw new Refused("feature-na", method + " has no keyed form here");
        }
    }

    private static MessageDigest findTheDigestNamed(String method) {
        String algorithm = ALGORITHMS.get(method);
        if (algorithm == null) {
            throw new Refused("feature-na", method + " is not a method this host has");
        }
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new Refused("feature-na", method + " is not a method this host has");
        }
    }
}
