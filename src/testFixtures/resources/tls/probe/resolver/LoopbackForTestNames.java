import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class LoopbackForTestNames extends InetAddressResolverProvider {

    public static final List<String> ASKED = new ArrayList<>();

    @Override
    public InetAddressResolver get(Configuration configuration) {
        InetAddressResolver builtin = configuration.builtinResolver();
        return new InetAddressResolver() {
            @Override
            public Stream<InetAddress> lookupByName(String host, LookupPolicy policy) throws UnknownHostException {
                synchronized (ASKED) {
                    ASKED.add(host);
                }
                if (host.endsWith(".test")) {
                    return Stream.of(InetAddress.getByAddress(host, new byte[] {127, 0, 0, 1}));
                }
                return builtin.lookupByName(host, policy);
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
