package org.jebol.tls;

import java.net.InetAddress;
import java.net.UnknownHostException;

public final class Ipv4Loopback {

    private static final byte[] ONE_TWENTY_SEVEN_ZERO_ZERO_ONE = {127, 0, 0, 1};

    private Ipv4Loopback() {
    }

    public static InetAddress address() {
        try {
            return InetAddress.getByAddress(ONE_TWENTY_SEVEN_ZERO_ZERO_ONE);
        } catch (UnknownHostException impossibleForFourOctets) {
            throw new IllegalStateException(impossibleForFourOctets);
        }
    }
}
