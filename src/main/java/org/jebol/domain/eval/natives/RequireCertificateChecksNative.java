package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class RequireCertificateChecksNative extends HostNative {

    public RequireCertificateChecksNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "require-certificate-checks";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of();
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            granted.require(HostService.CERTIFICATES);
            return NoneValue.none();
        };
    }
}
