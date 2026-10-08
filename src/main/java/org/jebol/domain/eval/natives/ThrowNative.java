package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.ThrownSignal;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class ThrowNative extends DefaultNative {

    private static final int WHERE_THE_NAME_ARRIVES = 1;

    @Override
    public String nativeName() {
        return "throw";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("value", Typeset.ANY_TYPE.members()),
                Parameter.belongingTo("name", "word", Set.of(Datatype.WORD)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("name");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            if (refinements.contains("name") && arguments.size() > WHERE_THE_NAME_ARRIVES) {
                throw new ThrownSignal(arguments.getFirst(),
                        ((AnyWordValue) arguments.get(WHERE_THE_NAME_ARRIVES)).canonical());
            }
            throw new ThrownSignal(arguments.getFirst());
        };
    }
}
