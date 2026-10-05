package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Delect;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class DelectNative extends DefaultNative {

    @Override
    public String name() {
        return "delect";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("dialect", Set.of(Datatype.OBJECT)),
                Parameter.required("input", Set.of(Datatype.BLOCK)),
                Parameter.required("output", Set.of(Datatype.BLOCK)),
                Parameter.belongingTo("in", "where", Set.of(Datatype.BLOCK)));
    }

    @Override
    public Set<String> refinements() {
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
