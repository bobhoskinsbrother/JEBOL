import java.net.IDN;
import java.net.InetAddress;

public class ResolverLoads {

    public static void main(String[] arguments) throws Exception {
        System.out.println("java " + System.getProperty("java.version"));
        for (String name : new String[] {"bücher.test", IDN.toASCII("bücher.test"), "localhost"}) {
            try {
                System.out.println(name + " -> " + InetAddress.getByName(name).getHostAddress());
            } catch (Exception refused) {
                System.out.println(name + " -> " + refused);
            }
        }
        System.out.println("asked of the provider: " + LoopbackForTestNames.ASKED);
        System.out.println("IDN.toASCII(faß.de) = " + IDN.toASCII("faß.de"));
        System.out.println("IDN.toASCII(BÜCHER.de) = " + IDN.toASCII("BÜCHER.de"));
    }
}
