package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Asked;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public abstract class AddingAction extends SeriesOrFileAction {

    protected AddingAction(GrantedServices granted) {
        super(granted);
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("series", Typeset.SERIES.membersAnd(
                        Datatype.PORT, Datatype.MAP, Datatype.GOB, Datatype.OBJECT, Datatype.BITSET)),
                Parameter.required("value", Typeset.ANY_TYPE.members()),
                Parameter.belongingTo("part", "range", aPartLimit()),
                Parameter.belongingTo("dup", "count", aDuplicateCount()));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("part", "only", "dup");
    }

    protected Asked askedOf(Value subject, List<Value> arguments, Set<String> refinements,
            Evaluator evaluator, Context context) {
        return Asked.reading(subject, arguments.get(1), refinements,
                argumentOf("dup", 0, arguments, refinements),
                argumentOf("part", 0, arguments, refinements),
                evaluator, context);
    }
}
