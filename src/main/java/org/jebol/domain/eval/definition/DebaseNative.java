package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Encodings;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class DebaseNative extends BinaryBaseNative {

    public DebaseNative(Encodings encodings) {
        super(encodings);
    }

    @Override
    public String name() {
        return "debase";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(
                Parameter.required("value", anyStringOr(Datatype.BINARY)),
                Parameter.required("base", Set.of(Datatype.INTEGER)),
                Parameter.belongingTo("part", "limit", anyStringOr(Datatype.BINARY, Datatype.INTEGER)));
    }

    @Override
    public Set<String> refinements() {
        return Set.of("url", "part");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            int base = aKnownBase(arguments);
            Value value = arguments.getFirst();
            try {
                return BinaryValue.ofBytes(encodings.debase(
                        textWithinAnyPart(value, arguments, refinements),
                        base, refinements.contains("url")));
            } catch (IllegalArgumentException malformed) {
                throw Raised.of(EvaluationFailure.INVALID_DATA, value);
            }
        };
    }

    @Override
    Raised refusalOfAnUnknownBase(List<Value> arguments) {
        return Raised.of(EvaluationFailure.INVALID_DATA, arguments.getFirst());
    }
}
