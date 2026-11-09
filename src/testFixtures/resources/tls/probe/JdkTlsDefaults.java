import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.security.Security;
import java.util.Arrays;
import java.util.List;

public class JdkTlsDefaults {

    public static void main(String[] arguments) throws Exception {
        System.out.println("java " + System.getProperty("java.version") + " " + System.getProperty("java.vendor"));
        for (String property : List.of("jdk.tls.disabledAlgorithms", "jdk.certpath.disabledAlgorithms",
                "jdk.tls.legacyAlgorithms", "jdk.tls.keyLimits")) {
            System.out.println(property + " = " + Security.getProperty(property));
        }
        SSLContext context = SSLContext.getDefault();
        SSLEngine client = context.createSSLEngine("example.test", 443);
        client.setUseClientMode(true);
        SSLParameters parameters = client.getSSLParameters();
        System.out.println("client protocols: " + Arrays.toString(client.getEnabledProtocols()));
        System.out.println("client suites, in order:");
        for (String suite : client.getEnabledCipherSuites()) {
            System.out.println("    " + suite);
        }
        System.out.println("supported but not enabled:");
        List<String> enabled = Arrays.asList(client.getEnabledCipherSuites());
        for (String suite : client.getSupportedCipherSuites()) {
            if (!enabled.contains(suite)) {
                System.out.println("    " + suite);
            }
        }
        System.out.println("named groups: " + Arrays.toString(parameters.getNamedGroups()));
        System.out.println("signature schemes: " + Arrays.toString(parameters.getSignatureSchemes()));
        System.out.println("server names on a Unicode host: " + parameters.getServerNames());
    }
}
