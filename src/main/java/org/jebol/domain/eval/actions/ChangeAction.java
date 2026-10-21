package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.SeriesChange;
import org.jebol.domain.value.ActionValue;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.TypesetValue;

import java.util.List;
import java.util.Set;

public class ChangeAction extends DefaultNative implements ActionValue {

    @Override
    public String nativeName() {
        return "change";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("series"),
                Parameter.of("value", TypesetValue.ANY_TYPE.members()),
                Parameter.belongTo("part", "range", listThePartLimitTypes()),
                Parameter.belongTo("dup", "count", listTheDuplicateCountTypes()));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("part", "only", "dup");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> new SeriesChange(
                arguments.get(1), refinements,
                refinementArgument("part", 0, arguments, refinements),
                refinementArgument("dup", 0, arguments, refinements))
                .change(arguments.getFirst());
    }
}
