package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.ContinueSignal;
import org.jebol.domain.eval.LoopSignal;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.ReturnSignal;
import org.jebol.domain.eval.ThrownSignal;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class AttemptNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "attempt";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("block", Set.of(Datatype.BLOCK, Datatype.PAREN)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("safer");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            try {
                return evaluator.evaluateOrRaise((BlockValue) arguments.getFirst(), context);
            } catch (Raised raised) {
                return NoneValue.none();
            } catch (ThrownSignal | LoopSignal | ContinueSignal | ReturnSignal escaping) {
                if (!refinements.contains("safer")) {
                    throw escaping;
                }
                return NoneValue.none();
            }
        };
    }
}
