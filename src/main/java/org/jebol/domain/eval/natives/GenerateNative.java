package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.EllipticCurveKey;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class GenerateNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "generate";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("type", Set.of(Datatype.WORD)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            WordValue curveNamed = (WordValue) arguments.getFirst();
            if (!EllipticCurveKey.curveNamesInTheCataloguesOrder()
                    .contains(curveNamed.canonical())) {
                throw Raised.of(EvaluationFailure.INVALID_ARG, curveNamed.spelling());
            }
            return BinaryValue.of(0);
        };
    }
}
