package org.jebol.domain.host;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class JvmDigests implements DigestPort {

    private static final Map<String, String> ALGORITHMS = Map.ofEntries(
            Map.entry("md5", "MD5"),
            Map.entry("sha1", "SHA-1"),
            Map.entry("sha224", "SHA-224"),
            Map.entry("sha256", "SHA-256"),
            Map.entry("sha384", "SHA-384"),
            Map.entry("sha512", "SHA-512"));

    private static final Map<String, Integer> CARRIED_SHA3_OUTPUT_BYTES = Map.of(
            "sha3-224", 28,
            "sha3-256", 32,
            "sha3-384", 48,
            "sha3-512", 64);

    private static final Set<String> METHODS = everyMethod();

    private static Set<String> everyMethod() {
        Set<String> every = new HashSet<>(ALGORITHMS.keySet());
        every.addAll(CARRIED_SHA3_OUTPUT_BYTES.keySet());
        return Set.copyOf(every);
    }

    @Override
    public Set<String> methods() {
        return METHODS;
    }

    @Override
    public RunningDigest start(String method) {
        Integer sha3OutputBytes = CARRIED_SHA3_OUTPUT_BYTES.get(method);
        if (sha3OutputBytes != null) {
            return startCarriedSha3(sha3OutputBytes);
        }
        MessageDigest running = findDigestNamed(method);
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

    private static RunningDigest startCarriedSha3(int outputBytes) {
        Sha3 running = new Sha3(outputBytes);
        return new RunningDigest() {

            @Override
            public void add(byte[] octets, int from, int length) {
                running.add(octets, from, length);
            }

            @Override
            public byte[] soFar() {
                return running.copy().digest();
            }
        };
    }

    @Override
    public byte[] digestOf(String method, byte[] octets) {
        Integer sha3OutputBytes = CARRIED_SHA3_OUTPUT_BYTES.get(method);
        if (sha3OutputBytes != null) {
            Sha3 running = new Sha3(sha3OutputBytes);
            running.add(octets, 0, octets.length);
            return running.digest();
        }
        return findDigestNamed(method).digest(octets);
    }

    @Override
    public byte[] digestWithKey(String method, byte[] octets, byte[] key) {
        Integer sha3OutputBytes = CARRIED_SHA3_OUTPUT_BYTES.get(method);
        if (sha3OutputBytes != null) {
            return Sha3.hmac(sha3OutputBytes, key.length == 0 ? new byte[1] : key, octets);
        }
        try {
            String algorithm = "Hmac" + findDigestNamed(method).getAlgorithm().replace("-", "");
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key.length == 0 ? new byte[1] : key, algorithm));
            return mac.doFinal(octets);
        } catch (NoSuchAlgorithmException | InvalidKeyException unavailable) {
            throw new Refused("feature-na", method + " has no keyed form here");
        }
    }

    private static MessageDigest findDigestNamed(String method) {
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
