package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Actions;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class PokeAction extends DefaultNative implements ActionValue {

    @Override
    public String nativeName() {
        return "poke";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.required("series", Typeset.SERIES.membersAnd(
                        Datatype.PORT, Datatype.MAP, Datatype.GOB, Datatype.BITSET)),
                Parameter.required("index"),
                Parameter.required("value", Typeset.ANY_TYPE.members()));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> evaluator
                .theRebolActorsAnswer("poke", arguments, Set.of())
                .orElseGet(() -> Actions.of(arguments.getFirst())
                        .orElseThrow(() -> Raised.cannotUse(arguments.getFirst(), this))
                        .poked(arguments.get(1), arguments.get(2)));
    }
}
