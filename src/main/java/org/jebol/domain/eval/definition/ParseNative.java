package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.parse.Parser;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class ParseNative extends DefaultNative {

    @Override
    public String name() {
        return "parse";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(
                Parameter.required("input", Typeset.SERIES.members()),
                Parameter.required("rules", Set.of(Datatype.BLOCK)));
    }

    @Override
    public Set<String> refinements() {
        return Set.of("case");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> switch (arguments.get(1)) {
            case BlockValue rules -> Parser.over(
                            evaluator, context, arguments.getFirst(), refinements.contains("case"))
                    .answerFor(rules);
            case Value other -> refuseTheArgument(other, "rules");
        };
    }
}
