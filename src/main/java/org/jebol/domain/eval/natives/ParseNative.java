package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.parse.Parser;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class ParseNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "parse";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.required("input", Typeset.SERIES.members()),
                Parameter.required("rules", Set.of(Datatype.BLOCK)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("case");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> switch (arguments.get(1)) {
            case AnyBlockValue rules -> Parser.over(
                            evaluator, context, arguments.getFirst(), refinements.contains("case"))
                    .answerFor(rules);
            case Value other -> refuseTheArgument(other, "rules");
        };
    }
}
