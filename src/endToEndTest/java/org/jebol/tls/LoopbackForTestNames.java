package org.jebol.tls;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class LoopbackForTestNames extends InetAddressResolverProvider {

    private static final String TEST_DOMAIN = ".test";

    private static final List<String> NAMES_LOOKED_UP = new ArrayList<>();

    public static List<String> namesLookedUp() {
        synchronized (NAMES_LOOKED_UP) {
            return List.copyOf(NAMES_LOOKED_UP);
        }
    }

    @Override
    public InetAddressResolver get(Configuration configuration) {
        InetAddressResolver builtin = configuration.builtinResolver();
        return new InetAddressResolver() {

            @Override
            public Stream<InetAddress> lookupByName(String host, LookupPolicy policy) throws UnknownHostException {
                if (!host.endsWith(TEST_DOMAIN)) {
                    return builtin.lookupByName(host, policy);
                }
                synchronized (NAMES_LOOKED_UP) {
                    NAMES_LOOKED_UP.add(host);
                }
                return Stream.of(InetAddress.getByAddress(host, new byte[] {127, 0, 0, 1}));
            }

            @Override
            public String lookupByAddress(byte[] address) throws UnknownHostException {
                return builtin.lookupByAddress(address);
            }
        };
    }

    @Override
    public String name() {
        return "loopback for .test names";
    }
}
