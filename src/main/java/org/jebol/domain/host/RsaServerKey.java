package org.jebol.domain.host;

public record RsaServerKey(byte[] modulus, byte[] publicExponent, byte[] privateExponent,
                           byte[] firstPrime, byte[] secondPrime) {
}
