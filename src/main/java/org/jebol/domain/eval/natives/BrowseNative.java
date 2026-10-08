package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.StringValue;

import java.util.List;
import java.util.Set;

public class BrowseNative extends WindowNative {

    public BrowseNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "browse";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("url", Set.of(Datatype.URL, Datatype.FILE, Datatype.NONE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            granted.require(HostService.WINDOWS);
            return throughTheWindows(() -> {
                if (arguments.getFirst() instanceof StringValue target) {
                    evaluator.windows().browse(target.text());
                }
                return NoneValue.none();
            });
        };
    }
}
