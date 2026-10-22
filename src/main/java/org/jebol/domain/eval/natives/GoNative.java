package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.Navigation;
import org.jebol.domain.host.Place;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class GoNative extends NavigationNative {

    private static final String REPLACING = "replace";

    public GoNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "go";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("place"), Parameter.ofRefinement(REPLACING));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of(REPLACING);
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Navigation navigation = requireNavigation(evaluator);
            Place place = Place.of(arguments.getFirst());
            Place arrived = refinements.contains(REPLACING)
                    ? navigation.replace(place)
                    : navigation.goTo(place);
            return arrived.asBlock();
        };
    }
}
