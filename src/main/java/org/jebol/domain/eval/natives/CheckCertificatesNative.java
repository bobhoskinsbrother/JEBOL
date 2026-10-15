package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.CertificateAuthority;
import org.jebol.domain.host.CertificateMistrust;
import org.jebol.domain.host.ChainVerdict;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.ErrorCategory;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Value;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CheckCertificatesNative extends HostNative {

    public CheckCertificatesNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "check-certificates";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.required("chain", Set.of(BlockValue.TYPE)),
                Parameter.required("host", Set.of(StringValue.TYPE, TupleValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            granted.require(HostService.CERTIFICATES);
            List<byte[]> chain = certificates((AnyBlockValue) arguments.get(0));
            String host = hostName(arguments.get(1));
            if (chain.isEmpty()) {
                throw mistrusted(CertificateMistrust.NO_CERTIFICATE, host);
            }
            requireTrust(evaluator.certificateAuthority(), chain, host);
            return NoneValue.none();
        };
    }

    private void requireTrust(CertificateAuthority authority, List<byte[]> chain, String host) {
        switch (authority.judge(chain)) {
            case ChainVerdict.Mistrusted(CertificateMistrust mistrust) -> throw mistrusted(mistrust, host);
            case ChainVerdict.Trusted trusted when !trusted.names().cover(host) ->
                    throw mistrusted(CertificateMistrust.WRONG_HOST, host);
            case ChainVerdict.Trusted ignored -> {
            }
        }
    }

    private List<byte[]> certificates(AnyBlockValue chain) {
        return chain.remaining().stream().map(this::bytes).toList();
    }

    private byte[] bytes(Value certificate) {
        if (!(certificate instanceof BinaryValue binary)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, certificate);
        }
        return binary.bytesFromHere();
    }

    private String hostName(Value host) {
        return host instanceof TupleValue address
                ? Arrays.stream(address.segments()).mapToObj(String::valueOf).collect(Collectors.joining("."))
                : ((AnyStringValue) host).text();
    }

    private Raised mistrusted(CertificateMistrust mistrust, String host) {
        String message = mistrust.messageFor(host);
        return new Raised(ErrorValue.about(ErrorCategory.ACCESS, "protocol", message, StringValue.of(message)));
    }
}
