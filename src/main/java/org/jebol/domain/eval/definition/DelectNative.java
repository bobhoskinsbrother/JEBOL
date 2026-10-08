package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Delect;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class DelectNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "delect";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("dialect", Set.of(Datatype.OBJECT)),
                Parameter.required("input", Set.of(Datatype.BLOCK)),
                Parameter.required("output", Set.of(Datatype.BLOCK)),
                Parameter.belongingTo("in", "where", Set.of(Datatype.BLOCK)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("in", "all");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            arguments.get(2).requireChangeable();
            return Delect.read(
                    (ObjectValue) arguments.getFirst(),
                    (BlockValue) arguments.get(1),
                    (BlockValue) arguments.get(2),
                    refinements.contains("all"),
                    evaluator, context);
        };
    }
}
