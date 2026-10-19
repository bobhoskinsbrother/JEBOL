package org.jebol.domain.host;

public final class DigestPorts {

    private DigestPorts() {
    }

    public static DigestPort chooseTheOneForThisBuild() {
        return new JvmDigests();
    }
}
