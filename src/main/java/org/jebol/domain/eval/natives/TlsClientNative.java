package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.CertificateAuthority;
import org.jebol.domain.host.CertificateMistrust;
import org.jebol.domain.host.ChainVerdict;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.NetworkPort;
import org.jebol.domain.host.SecureSession;
import org.jebol.domain.host.ServerCheck;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.ErrorCategory;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Value;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class TlsClientNative extends HostNative {

    public TlsClientNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "tls-client";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.of("host", Set.of(StringValue.TYPE, TupleValue.TYPE)),
                Parameter.of("port", Set.of(IntegerValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            granted.require(HostService.CERTIFICATES);
            String host = readAsciiHost(arguments.getFirst(), evaluator);
            int port = (int) ((IntegerValue) arguments.get(1)).magnitude();
            ServerCheck check = makeCheckFor(host, evaluator.ports().certificateAuthority());
            try {
                SecureSession session = evaluator.ports().secureTransport().startClient(host, port, check);
                return TlsSessions.wrap(session, host, evaluator);
            } catch (NetworkPort.Refused refused) {
                throw new Raised(ErrorValue.of(ErrorCategory.ACCESS, refused.errorId(), refused.getMessage(),
                        StringValue.of(refused.subject())));
            }
        };
    }

    private String readAsciiHost(Value host, Evaluator evaluator) {
        if (host instanceof TupleValue address) {
            return Arrays.stream(address.segments()).mapToObj(String::valueOf).collect(Collectors.joining("."));
        }
        String written = ((AnyStringValue) host).text();
        Optional<String> ascii = evaluator.ports().network().asciiHostName(written);
        return ascii.orElseThrow(() -> new Raised(ErrorValue.of(ErrorCategory.ACCESS, "no-connect",
                "UTS #46 refuses the host name " + written, StringValue.of(written))));
    }

    private static ServerCheck makeCheckFor(String host, CertificateAuthority authority) {
        return chain -> {
            if (chain.isEmpty()) {
                return Optional.of(CertificateMistrust.NO_CERTIFICATE);
            }
            return switch (authority.judge(chain)) {
                case ChainVerdict.Mistrusted(CertificateMistrust mistrust) -> Optional.of(mistrust);
                case ChainVerdict.Trusted trusted when !trusted.names().cover(host) ->
                        Optional.of(CertificateMistrust.WRONG_HOST);
                case ChainVerdict.Trusted ignored -> Optional.empty();
            };
        };
    }
}
