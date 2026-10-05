package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.EllipticCurveKey;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.WordValue;

import java.util.List;
import java.util.Set;

public class GenerateNative extends DefaultNative {

    @Override
    public String name() {
        return "generate";
    }

    @Override
    public List<Parameter> parameters() {
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
