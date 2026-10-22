package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.Navigation;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class CurrentPlaceNative extends NavigationNative {

    private static final String REFUSED = "refused";

    public CurrentPlaceNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "current-place";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.ofRefinement(REFUSED));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of(REFUSED);
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Navigation navigation = requireNavigation(evaluator);
            return refinements.contains(REFUSED)
                    ? navigation.refusedAddress().<Value>map(StringValue::of).orElseGet(NoneValue::none)
                    : navigation.current().asBlock();
        };
    }
}
