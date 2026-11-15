package org.teavm.classlib.javax.crypto;

public class TMac {
    public static TMac getInstance(String algorithm) throws java.security.NoSuchAlgorithmException {
        throw new java.security.NoSuchAlgorithmException("refused in the web build");
    }

    public void init(java.security.Key key) throws java.security.InvalidKeyException {
        throw new java.security.InvalidKeyException("refused in the web build");
    }

    public byte[] doFinal(byte[] input) {
        throw new UnsupportedOperationException("refused in the web build");
    }
}
