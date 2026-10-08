package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.Value;

import java.util.List;

public class IsTailAction extends SeriesOrFileAction {

    public IsTailAction(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "tail?";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("series", Typeset.SERIES.membersAnd(
                Datatype.GOB, Datatype.PORT, Datatype.BITSET, Datatype.TYPESET, Datatype.MAP)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> switch (arguments.getFirst()) {
            case PortValue port when port.isAFile() ->
                    LogicValue.of(theFileBehind(port, evaluator).atItsEnd());
            case Value anythingElse -> LogicValue.of(anythingElse.atTail());
        };
    }
}
