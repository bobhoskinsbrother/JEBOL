package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Actions;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.TupleActions;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.TypesetActions;
import org.jebol.domain.value.TypesetValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class ComplementFunction extends DefaultFunction {

    @Override
    public String name() {
        return "complement";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("value", Set.of(
                Datatype.LOGIC, Datatype.INTEGER, Datatype.TUPLE,
                Datatype.BINARY, Datatype.BITSET, Datatype.TYPESET,
                Datatype.IMAGE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                switch (arguments.getFirst()) {
                    case LogicValue truth -> LogicValue.of(!truth.truth());
                    case IntegerValue whole -> IntegerValue.of(~whole.magnitude());
                    case TypesetValue kinds -> new TypesetActions(kinds).complemented();
                    case TupleValue tuple -> new TupleActions().complemented(tuple);
                    case Value subject when Actions.of(subject).isPresent() ->
                            Actions.of(subject).orElseThrow().complemented();
                    default -> refuseTheArgument(
                            arguments.getFirst(), "logic or integer");
                };
    }
}
