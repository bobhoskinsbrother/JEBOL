package org.jebol.domain.host;

public final class DigestPorts {

    private DigestPorts() {
    }

    public static DigestPort theOneForThisBuild() {
        return new JvmDigests();
    }
}
